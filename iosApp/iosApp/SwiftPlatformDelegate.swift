import Foundation
import CryptoKit
import JOSESwift
import WidgetKit
import UIKit

import FirebaseCore
import FirebaseMessaging

import class shared.Platform_iosKt
import protocol shared.PlatformDelegate
import class shared.FirebaseIOSOptionsBridge

class Throttler {
  private var workItem: DispatchWorkItem?
  private let block: (() -> Void)
  private let queue: DispatchQueue
  private let delay: TimeInterval
  
  init(delay: TimeInterval, queue: DispatchQueue = .main, block: @escaping () -> Void) {
    self.delay = delay
    self.queue = queue
    self.block = block
  }
  
  func throttle() {
    workItem?.cancel()
    
    workItem = DispatchWorkItem { [weak self] in
      guard let self = self else { return }
      self.block()
      self.workItem = nil
    }

    if let workItem = workItem {
      queue.asyncAfter(deadline: .now() + delay, execute: workItem)
    }
  }
  
  func consumeNow() {
    guard let workItem = workItem else {
      return
    }
    
    workItem.cancel()
    block()

    self.workItem = nil
  }
}


class SwiftPlatformDelegate : PlatformDelegate {
  static let shared = SwiftPlatformDelegate()
  private let groupDefaults = UserDefaults(suiteName: "group.app.interfold.Interfold")!
  
  let widgetThrottler = Throttler(delay: 5.0) {
    WidgetCenter.shared.reloadAllTimelines()
  }
  
  func decryptData(key: Data, iv: Data, cipherText: Data, tag: Data) -> Optional<String> {
    do {
      let box = try AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: iv), ciphertext: cipherText, tag: tag)
      let data = try AES.GCM.open(box, using: SymmetricKey(data: key))

      return String(data: data, encoding: .utf8)!
    } catch {
      return nil
    }
  }
  
  func encryptData(key: Data, iv: Data, plainText: String) -> Data {
    let box = try! AES.GCM.seal(plainText.data(using: .utf8)!, using: SymmetricKey(data: key), nonce: AES.GCM.Nonce(data: iv))
    
    let cipherText = box.ciphertext
    let tag = box.tag
    
    let combinedData = NSMutableData()
    
    combinedData.append(cipherText)
    combinedData.append(tag)
    return combinedData as Data
  }
  
  func recoveryCodeToJWE(recoveryCode: String, endpoint: String) -> String {
    let header = JWEHeader(keyManagementAlgorithm: .RSAOAEP256, contentEncryptionAlgorithm: .A256GCM)
    let payload = Payload(recoveryCode.data(using: .utf8)!)
    
    // Pass the endpoint down to the Kotlin helper to fetch the correct public key
    let publicKeyString = Platform_iosKt.getInterfoldPublicKey(endpoint: endpoint)
    let publicKey = publicKeyFromString(publicKeyString)
    
    let encrypter = Encrypter(keyManagementAlgorithm: .RSAOAEP256, contentEncryptionAlgorithm: .A256GCM, encryptionKey: publicKey)!
    let jwe = try! JWE(header: header, payload: payload, encrypter: encrypter)
    
    return jwe.compactSerializedString
  }
  
  func updateWidgets(sessionInvalidated: Bool) {
    if(sessionInvalidated) {
      Task {
        groupDefaults.set(true, forKey: "invalidate")
        WidgetCenter.shared.reloadAllTimelines()
      }
    } else {
      widgetThrottler.throttle()
    }
  }
  
  func reconfigureFirebase(bridge: FirebaseIOSOptionsBridge) {
    // Kotlin side has already persisted the fresh config to KVault; our job here is
    // to swap out the live FirebaseApp singleton so this session's `Messaging.messaging()`
    // is bound to the correct project. Runs on the main thread because FirebaseCore
    // touches UIApplication under the hood.
    let options = FirebaseOptions(googleAppID: bridge.googleAppId, gcmSenderID: bridge.gcmSenderId)
    options.apiKey = bridge.apiKey
    options.projectID = bridge.projectId
    options.bundleID = bridge.bundleId
    if let storageBucket = bridge.storageBucket {
      options.storageBucket = storageBucket
    }
    if let clientId = bridge.clientId {
      options.clientID = clientId
    }

    Task { @MainActor in
      // Async delete has to complete before we configure a new instance, otherwise
      // FirebaseApp.configure(options:) may race with the teardown and end up with
      // Messaging.messaging() referencing a dangling app.
      if let currentApp = FirebaseApp.app() {
        let succeeded = await withCheckedContinuation { (continuation: CheckedContinuation<Bool, Never>) in
          currentApp.delete { success in
            continuation.resume(returning: success)
          }
        }
        if !succeeded {
          NSLog("reconfigureFirebase: FirebaseApp.delete returned false (continuing anyway)")
        }
      }

      FirebaseApp.configure(options: options)

      // FirebaseApp.delete() clears Messaging.messaging()'s delegate; re-attach so
      // MessagingDelegate.messaging(_:didReceiveRegistrationToken:) fires with the
      // freshly-minted token from the new project and flows back into Kotlin via
      // providePushNotificationToken.
      if let appDelegate = UIApplication.shared.delegate as? MessagingDelegate {
        Messaging.messaging().delegate = appDelegate
      } else {
        NSLog("reconfigureFirebase: AppDelegate is not a MessagingDelegate; token won't flow back")
      }

      // Force iOS to hand APNs back to Firebase, which kicks off a fresh FCM token
      // acquisition against the newly-configured project.
      UIApplication.shared.registerForRemoteNotifications()
    }
  }

  private func publicKeyFromString(_ keyString: String) -> SecKey {
    // Robustly strip any PEM headers/footers using regex
    let pattern = "-----BEGIN.*?-----|-----END.*?-----"
    let regex = try! NSRegularExpression(pattern: pattern, options: [])
    let range = NSRange(location: 0, length: keyString.utf16.count)
    let strippedHeaderFooter = regex.stringByReplacingMatches(in: keyString, options: [], range: range, withTemplate: "")
    
    let publicKeyStringWithoutHeaders = strippedHeaderFooter
        .replacingOccurrences(of: "\n", with: "")
        .replacingOccurrences(of: "\r", with: "")
        .trimmingCharacters(in: .whitespacesAndNewlines)
    
    let publicKeyData = Data(base64Encoded: publicKeyStringWithoutHeaders, options: .ignoreUnknownCharacters)!
    
    let keyDict: [NSString: AnyObject] = [
      kSecAttrKeyType: kSecAttrKeyTypeRSA,
      kSecAttrKeyClass: kSecAttrKeyClassPublic,
      kSecAttrKeySizeInBits: 2048 as AnyObject
    ]
    var error: Unmanaged<CFError>?
    
    return SecKeyCreateWithData(publicKeyData as CFData, keyDict as CFDictionary, &error)!
  }
}

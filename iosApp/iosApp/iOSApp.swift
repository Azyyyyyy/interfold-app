import SwiftUI
import WidgetKit

import FirebaseMessaging
import FirebaseCore

import shared

let delegate = SwiftPlatformDelegate()

class AppDelegate: NSObject, UIApplicationDelegate {
  private var stateKeeper = StateKeeperDispatcherKt.StateKeeperDispatcher(savedState: nil)
  var backDispatcher: Back_handlerBackDispatcher = DecomposeUtilsKt.createBackDispatcher()

  lazy var root: RootComponent = DecomposeUtilsKt.createRootComponent(
      componentContext: DefaultComponentContext(
        lifecycle: ApplicationLifecycle(),
        stateKeeper: stateKeeper,
        instanceKeeper: nil,
        backHandler: backDispatcher
        )
      )
  
  func application(_ application: UIApplication, shouldSaveSecureApplicationState coder: NSCoder) -> Bool {
    DecomposeUtilsKt.save(coder: coder, state: stateKeeper.save())
    return true
  }
      
  func application(_ application: UIApplication, shouldRestoreSecureApplicationState coder: NSCoder) -> Bool {
    // stateKeeper = StateKeeperDispatcherKt.StateKeeperDispatcher(savedState: StateKeeperUtilsKt.restore(coder: coder))
    return true
  }
  
  func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
    let defaults = UserDefaults.standard
    
    if !defaults.bool(forKey: "hasPreviouslyLaunched") {
      Settings_iosKt.clearKeychain()
      defaults.set(true, forKey: "hasPreviouslyLaunched")
    }
    
    // Firebase config now lives on the API server (see FirebaseConfigProvider). Kick
    // off the async bridge fetch; when it resolves we configure Firebase and wire the
    // messaging delegate. If the fetch fails and there's no cache, push simply won't
    // work until the next launch that reaches the server.
    Task {
      guard let bridge = try? await Main_iosKt.awaitFirebaseOptionsForIOS() else {
        NSLog("Firebase options unavailable; skipping FirebaseApp.configure")
        return
      }
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
      await MainActor.run {
        if FirebaseApp.app() == nil {
          FirebaseApp.configure(options: options)
        }
        Messaging.messaging().delegate = self
      }
    }
    
    Task { WidgetCenter.shared.reloadAllTimelines() }
    
    return true
  }
  
//  func applicationDidEnterBackground(_ application: UIApplication) {
//    NSLog("Entered background")
//    
//    SwiftPlatformDelegate.shared.widgetThrottler.consumeNow()
//  }
//  
//  func applicationWillResignActive(_ application: UIApplication) {
//    NSLog("Resigning")
//    
//    SwiftPlatformDelegate.shared.widgetThrottler.consumeNow()
//  }
  
  func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
    NSLog("didRegisterForRemoteNotificationsWithDeviceToken")
    Messaging.messaging().apnsToken = deviceToken
  }
}

extension AppDelegate: MessagingDelegate {
  func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
    Main_iosKt.providePushNotificationToken(token: fcmToken)
  }
}

@main
struct iOSApp: App {
  @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
  
  var body: some Scene {
    WindowGroup {
      ContentView(root: delegate.root, backDispatcher: delegate.backDispatcher)
    }
  }
}

@file:OptIn(ExperimentalWasmJsInterop::class)

package app.interfold.app.utils

import kotlinx.coroutines.await
import kotlin.js.Promise
import kotlin.js.JsAny

// JS implementations (Promise-based) for IndexedDB + Web Crypto operations.
// Keep heavy JS inside @JsFun externals so Kotlin/Wasm doesn't use `dynamic` or `js()` in function bodies.

// Read the existing JWK (or detect its absence) in a short-lived readonly transaction, do
// ALL crypto work outside of any IDB transaction (because awaiting non-IDB promises lets
// IDB transactions auto-commit and become inactive), then perform every IDB write in a
// single readwrite transaction spanning [KEYS, META]. This avoids the
// "A request was placed against a transaction which is currently not active, or which
// is finished" error that fired on the very first setup when the JWK had to be generated.
@JsFun("(val) => new Promise((resolve,reject) => { try { const DB_NAME='interfold_keystore'; const KEYS='keys'; const META='meta'; const request = indexedDB.open(DB_NAME,1); request.onupgradeneeded = (e) => { const db = e.target.result; if (!db.objectStoreNames.contains(KEYS)) db.createObjectStore(KEYS); if (!db.objectStoreNames.contains(META)) db.createObjectStore(META); }; request.onerror = (ev) => reject(ev); request.onsuccess = (e) => { const db = e.target.result; try { const txRead = db.transaction(META,'readonly'); const getJwkReq = txRead.objectStore(META).get('wrapping_key_jwk'); getJwkReq.onerror = (ev) => { db.close(); reject(ev); }; getJwkReq.onsuccess = (ev) => { const existingJwk = ev.target.result; const wrappingKeyPromise = existingJwk ? crypto.subtle.importKey('jwk', existingJwk, { name: 'AES-GCM' }, true, ['encrypt','decrypt']).then((k) => ({ key: k, jwkToPersist: null })) : crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, true, ['encrypt','decrypt']).then((k) => crypto.subtle.exportKey('jwk', k).then((exp) => ({ key: k, jwkToPersist: exp }))); wrappingKeyPromise.then((res) => { const enc = new TextEncoder(); const data = enc.encode(val); const iv = crypto.getRandomValues(new Uint8Array(12)); return crypto.subtle.encrypt({ name: 'AES-GCM', iv: iv }, res.key, data).then((ct) => ({ jwkToPersist: res.jwkToPersist, iv: iv, ct: ct })); }).then((res) => { try { const txWrite = db.transaction([KEYS, META],'readwrite'); if (res.jwkToPersist) txWrite.objectStore(META).put(res.jwkToPersist, 'wrapping_key_jwk'); txWrite.objectStore(KEYS).put({ iv: res.iv.buffer, ct: res.ct }, 'encryption_key'); txWrite.oncomplete = () => { db.close(); resolve(true); }; txWrite.onerror = (ev) => { db.close(); reject(ev); }; txWrite.onabort = (ev) => { db.close(); reject(ev); }; } catch(err) { db.close(); reject(err); } }).catch((err) => { db.close(); reject(err); }); }; } catch(err) { db.close(); reject(err); } }; } catch(err) { reject(err); } })")
private external fun idbStoreEncryptionKeyAsync(value: String): Promise<JsAny?>

@JsFun("() => new Promise((resolve,reject) => { try { const DB_NAME='interfold_keystore'; const KEYS='keys'; const META='meta'; const request = indexedDB.open(DB_NAME,1); request.onupgradeneeded = (e) => { const db = e.target.result; if (!db.objectStoreNames.contains(KEYS)) db.createObjectStore(KEYS); if (!db.objectStoreNames.contains(META)) db.createObjectStore(META); }; request.onsuccess = (e) => { const db = e.target.result; try { const txKeys = db.transaction(KEYS,'readonly'); const storeKeys = txKeys.objectStore(KEYS); const getReq = storeKeys.get('encryption_key'); getReq.onsuccess = (ev) => { const entry = ev.target.result; if (!entry) { db.close(); resolve(null); return; } const txMeta = db.transaction(META,'readonly'); const storeMeta = txMeta.objectStore(META); const getJwkReq = storeMeta.get('wrapping_key_jwk'); getJwkReq.onsuccess = (ev2) => { const jwk = ev2.target.result; if (!jwk) { db.close(); reject(new Error('Wrapping key missing')); return; } crypto.subtle.importKey('jwk', jwk, { name: 'AES-GCM' }, true, ['encrypt','decrypt']).then((wk)=>{ try { const iv = new Uint8Array(entry.iv); crypto.subtle.decrypt({ name: 'AES-GCM', iv: iv }, wk, entry.ct).then((decrypted)=>{ const dec = new TextDecoder(); db.close(); resolve(dec.decode(decrypted)); }).catch((err)=>{ db.close(); reject(err); }); } catch(err) { db.close(); reject(err); } }).catch((err)=>{ db.close(); reject(err); }); }; getJwkReq.onerror = (ev2) => { db.close(); reject(ev2); }; }; getReq.onerror = (ev) => { db.close(); reject(ev); }; } catch(err) { db.close(); reject(err); } }; request.onerror = (ev) => reject(ev); } catch(err) { reject(err); } })")
private external fun idbGetEncryptionKeyAsync(): Promise<JsAny?>

@JsFun("() => new Promise((resolve,reject) => { try { const DB_NAME='interfold_keystore'; const KEYS='keys'; const request = indexedDB.open(DB_NAME,1); request.onupgradeneeded = (e) => { const db = e.target.result; if (!db.objectStoreNames.contains(KEYS)) db.createObjectStore(KEYS); }; request.onsuccess = (e) => { const db = e.target.result; try { const tx = db.transaction(KEYS,'readwrite'); const store = tx.objectStore(KEYS); store.delete('encryption_key'); tx.oncomplete = () => { db.close(); resolve(true); }; tx.onerror = (ev) => { db.close(); reject(ev); }; } catch(err) { db.close(); reject(err); } }; request.onerror = (ev) => reject(ev); } catch(err) { reject(err); } })")
private external fun idbDeleteEncryptionKeyAsync(): Promise<JsAny?>

@JsFun("() => new Promise((resolve) => { try { const OLD_NAME='octocon_keystore'; const NEW_NAME='interfold_keystore'; const KEYS='keys'; const META='meta'; const openNew=indexedDB.open(NEW_NAME,1); openNew.onupgradeneeded=(e)=>{ const db=e.target.result; if(!db.objectStoreNames.contains(KEYS)) db.createObjectStore(KEYS); if(!db.objectStoreNames.contains(META)) db.createObjectStore(META); }; openNew.onerror=()=>resolve(false); openNew.onsuccess=(e)=>{ const newDb=e.target.result; try { const tx=newDb.transaction(KEYS,'readonly'); const getExisting=tx.objectStore(KEYS).get('encryption_key'); getExisting.onerror=()=>{ newDb.close(); resolve(false); }; getExisting.onsuccess=()=>{ if(getExisting.result){ newDb.close(); resolve(true); return; } const openOld=indexedDB.open(OLD_NAME); openOld.onupgradeneeded=(ev)=>{ try { ev.target.transaction.abort(); } catch(err) {} }; openOld.onerror=()=>{ newDb.close(); resolve(false); }; openOld.onsuccess=(oe)=>{ const oldDb=oe.target.result; if(!oldDb.objectStoreNames.contains(KEYS)){ oldDb.close(); newDb.close(); resolve(false); return; } const stores=[KEYS]; if(oldDb.objectStoreNames.contains(META)) stores.push(META); const ot=oldDb.transaction(stores,'readonly'); const entryReq=ot.objectStore(KEYS).get('encryption_key'); entryReq.onerror=()=>{ oldDb.close(); newDb.close(); resolve(false); }; entryReq.onsuccess=()=>{ const entry=entryReq.result; if(!entry){ oldDb.close(); newDb.close(); resolve(false); return; } const finish=(jwk)=>{ try { const names=jwk?[KEYS,META]:[KEYS]; const wt=newDb.transaction(names,'readwrite'); wt.objectStore(KEYS).put(entry,'encryption_key'); if(jwk) wt.objectStore(META).put(jwk,'wrapping_key_jwk'); wt.onerror=()=>{ oldDb.close(); newDb.close(); resolve(false); }; wt.oncomplete=()=>{ oldDb.close(); newDb.close(); const del=indexedDB.deleteDatabase(OLD_NAME); del.onsuccess=()=>resolve(true); del.onerror=()=>resolve(true); del.onblocked=()=>resolve(true); }; } catch(err) { oldDb.close(); newDb.close(); resolve(false); } }; if(oldDb.objectStoreNames.contains(META)){ const jwkReq=ot.objectStore(META).get('wrapping_key_jwk'); jwkReq.onerror=()=>finish(null); jwkReq.onsuccess=()=>finish(jwkReq.result); } else { finish(null); } }; }; }; } catch(err) { newDb.close(); resolve(false); } }; } catch(err) { resolve(false); } })")
private external fun idbMigrateLegacyKeystoreAsync(): Promise<JsAny?>

private var legacyKeystoreMigrated = false

private suspend fun migrateLegacyKeystoreIfNeeded() {
  if (legacyKeystoreMigrated) return
  try {
    idbMigrateLegacyKeystoreAsync().await<JsAny?>()
  } catch (_: Throwable) {
  }
  legacyKeystoreMigrated = true
}

// Returns true if the key was successfully persisted, false otherwise. Errors are logged
// but not rethrown so the caller can decide how to recover (e.g. clear stale state in
// localStorage so the user isn't stuck claiming to have a key that isn't actually there).
suspend fun webStoreEncryptionKey(keyBase64: String): Boolean {
  return try {
    migrateLegacyKeystoreIfNeeded()
    // Await as JsAny? to avoid illegal-cast when JS resolves null/undefined/other types
    idbStoreEncryptionKeyAsync(keyBase64).await<JsAny?>()
    true
  } catch (e: Throwable) {
    platformLog("SETTINGS", "Failed to store encryption key in IndexedDB: $e")
    false
  }
}

suspend fun webRetrieveEncryptionKey(): String? {
  return try {
    migrateLegacyKeystoreIfNeeded()
    val res = idbGetEncryptionKeyAsync().await<JsAny?>()
    res?.toString()
  } catch (e: Throwable) {
    platformLog("SETTINGS", "Failed to read encryption key from IndexedDB: $e")
    null
  }
}

suspend fun webDeleteEncryptionKey() {
  try {
    migrateLegacyKeystoreIfNeeded()
    idbDeleteEncryptionKeyAsync().await<JsAny?>()
  } catch (e: Throwable) {
    platformLog("SETTINGS", "Failed to delete encryption key from IndexedDB: $e")
  }
}

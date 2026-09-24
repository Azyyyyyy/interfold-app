// APP_VERSION and PRECACHE_URLS are stamped at build time by
// :webApp:generateServiceWorkerPrecache. Handlers live in the Kotlin/JS
// bundle imported below (`:pwa-service-worker-js`).
const APP_VERSION = '__APP_VERSION__';
const PRECACHE_URLS = [];
//^^ This is populated at build time with the actual list of files to precache;
// based on the output of the bundler. See build.gradle.kts for details.

importScripts('/interfold-sw.js');
InterfoldServiceWorker.start(APP_VERSION, PRECACHE_URLS);

package app.interfold.sw

@JsExport
object InterfoldServiceWorker {
  fun start(appVersion: String, precacheUrls: Array<String>) {
    ServiceWorkerRuntime(appVersion, precacheUrls.toList()).attach()
  }
}

fun main() {
  val exported = InterfoldServiceWorker
  js("self.InterfoldServiceWorker = exported")
}

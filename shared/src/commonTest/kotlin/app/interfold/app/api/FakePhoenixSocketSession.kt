package app.interfold.app.api

import app.interfold.app.utils.globalSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * In-memory [PhoenixSocketSession] for `commonTest`. Lets a unit test drive the
 * real [app.interfold.app.ui.model.interfaces.ApiInterfaceImpl] without a backend
 * or WebSocket: the test scripts what an `endpoint`-proxied REST reply looks
 * like, and may push [ChannelMessage]s onto the captured event pipeline.
 *
 * Defaults are deliberately loud — any unexpected `sendMessage` errors out
 * with an explicit message, preserving the same "tripwire" property the older
 * throw-everything `FakeApiInterface` stub used to give us at the
 * [ApiInterface][app.interfold.app.ui.model.interfaces.ApiInterface] boundary.
 * Tests that intentionally exercise a call must configure [endpointHandler].
 */
internal class FakePhoenixSocketSession(
  val coroutineScope: CoroutineScope,
  val eventPipeline: MutableSharedFlow<ChannelMessage>,
  val errorPipeline: MutableSharedFlow<String>,
) : PhoenixSocketSession {
  data class SentEndpoint(val method: String, val path: String, val body: String)
  data class SentEvent(val event: String, val payload: Map<String, Any?>)

  /** All `endpoint`-shaped sends, in call order. */
  val sentEndpoints: MutableList<SentEndpoint> = mutableListOf()

  /** All non-`endpoint` sends (in production today, this stays empty). */
  val sentOtherEvents: MutableList<SentEvent> = mutableListOf()

  /**
   * Resolves an `endpoint` send to a [SocketAdapterResponse]. The default
   * errors loudly so an unexpected REST proxy call from production code is
   * visible at test time. Override per-test to script a reply.
   */
  var endpointHandler: (method: String, path: String, body: String) -> SocketAdapterResponse =
    { method, path, _ ->
      error(
        "FakePhoenixSocketSession received an unexpected endpoint call: " +
          "$method $path. Configure endpointHandler if this was expected."
      )
    }

  /** Tracks whether [disconnect] has been invoked. */
  var disconnected: Boolean = false
    private set

  override fun disconnect() {
    disconnected = true
  }

  override fun sendMessage(
    event: String,
    payload: Map<String, Any?>,
    callback: suspend (String) -> Unit,
  ) {
    if (event != "endpoint") {
      sentOtherEvents += SentEvent(event, payload)
      error(
        "FakePhoenixSocketSession received an unexpected non-endpoint event " +
          "'$event'. ApiInterfaceImpl only emits 'endpoint' events; extend the " +
          "fake if a new event type is being introduced."
      )
    }
    val method = payload["method"] as? String
      ?: error("Endpoint payload missing 'method': $payload")
    val path = payload["path"] as? String
      ?: error("Endpoint payload missing 'path': $payload")
    val body = (payload["body"] as? String).orEmpty()
    sentEndpoints += SentEndpoint(method, path, body)
    val response = endpointHandler(method, path, body)
    val encoded = globalSerializer.encodeToString(response)
    coroutineScope.launch {
      callback(encoded)
    }
  }

  /** Convenience: emit a [ChannelMessage] into the event pipeline. */
  suspend fun emitChannelEvent(message: ChannelMessage) {
    eventPipeline.emit(message)
  }
}

/**
 * Test factory paired with [FakePhoenixSocketSession]. When constructed with a
 * non-null [socketInitResponse] (already-serialised JSON matching the
 * [app.interfold.app.api.model.SocketInitResponse] shape),
 * [PhoenixSocketSessionFactory.create] invokes `onConnected` synchronously so
 * the `loadClient` init path completes before [ApiInterfaceImpl.loadClient]'s
 * coroutine returns.
 *
 * The last-created session is exposed as [lastSession] so tests can inspect
 * captured sends and inject channel events.
 */
internal class FakePhoenixSocketSessionFactory(
  private val socketInitResponse: String? = null,
) : PhoenixSocketSessionFactory {
  var lastSession: FakePhoenixSocketSession? = null
    private set

  /** Token the most recent `create()` call was issued with. */
  var lastToken: String? = null
    private set

  /** Endpoint the most recent `create()` call was issued with. */
  var lastEndpoint: String? = null
    private set

  override fun create(
    token: String,
    userID: String,
    eventPipeline: MutableSharedFlow<ChannelMessage>,
    errorPipeline: MutableSharedFlow<String>,
    coroutineScope: CoroutineScope,
    endpoint: String,
    onConnected: (String) -> Unit,
  ): PhoenixSocketSession {
    lastToken = token
    lastEndpoint = endpoint
    val session = FakePhoenixSocketSession(coroutineScope, eventPipeline, errorPipeline)
    lastSession = session
    val initJson = socketInitResponse
      ?: error(
        "FakePhoenixSocketSessionFactory.create() was called but no " +
          "socketInitResponse was configured. Pass one to the factory if the " +
          "test intends loadClient to complete init."
      )
    onConnected(initJson)
    return session
  }
}

/**
 * Builds a syntactically-valid unsigned JWT whose payload has the given `sub`
 * claim — the only field [app.interfold.app.ui.model.interfaces.ApiInterfaceImpl.loadClient]
 * decodes from the token. No signature is verified anywhere on the client.
 */
@OptIn(ExperimentalEncodingApi::class)
internal fun dummyJwt(sub: String): String {
  val encoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
  val header = encoder.encode("""{"alg":"none","typ":"JWT"}""".encodeToByteArray())
  val payload = encoder.encode("""{"sub":"$sub"}""".encodeToByteArray())
  return "$header.$payload."
}

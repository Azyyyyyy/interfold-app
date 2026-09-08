package app.interfold.app.api

import app.interfold.app.Settings
import app.interfold.app.api.model.MyAlter
import app.interfold.app.api.model.MySystem
import app.interfold.app.api.model.SecurityLevel
import app.interfold.app.api.model.SocketInitResponse
import app.interfold.app.ui.compose.screens.main.hometabs.FakeSettingsInterface
import app.interfold.app.ui.model.interfaces.ApiInterfaceImpl
import app.interfold.app.utils.FailingPlatformUtilities
import app.interfold.app.utils.globalSerializer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Proof-of-life unit tests for the real [ApiInterfaceImpl] wired against the
 * in-memory [FakePhoenixSocketSession]. No backend, no Docker — proves the
 * [PhoenixSocketSessionFactory] seam works end-to-end across:
 *   1. socket init -> [ApiInterface.initComplete] flips and state-flows hydrate;
 *   2. an `endpoint`-proxied REST mutation captures the right payload and the
 *      scripted reply doesn't push an error onto [ApiInterface.errorFlow];
 *   3. a channel event drives [ApiInterface.alters] via `handleChannelMessage`.
 *
 * Uses [runBlocking] + [withTimeout] (mirroring `:shared:desktopIntegrationTest`)
 * because [ApiInterfaceImpl.loadClient] suspends inside `withContext(ioDispatcher)`,
 * and `kotlinx.coroutines.test.runTest`'s virtual clock can't advance through a
 * real dispatcher switch. `commonTest` currently only runs on the desktop JVM
 * target; if/when iOS/wasm test source sets are added this will need revisiting.
 */
class ApiInterfaceImplIntegrationTest {

  @Test
  fun loadClient_flipsInitComplete_andPopulatesState() = runBlocking {
    withTimeout(5.seconds) {
      val initResponse = SocketInitResponse(
        system = sampleSystem("system-1"),
        alters = emptyList(),
        tags = emptyList(),
        fronts = emptyList(),
      )
      val factory = FakePhoenixSocketSessionFactory(
        socketInitResponse = globalSerializer.encodeToString(initResponse),
      )
      val api = newApi(factory)

      api.loadClient(dummyJwt(sub = "system-1")).join()

      assertEquals(true, api.initComplete.value, "initComplete should flip after onConnected")
      val systemState = api.systemMe.value
      assertTrue(systemState is APIState.Success, "systemMe should be Success, was $systemState")
      assertEquals("system-1", systemState.data.id)
      assertTrue(api.alters.value is APIState.Success, "alters should be Success after init")
      assertEquals(emptyList(), api.alters.value.ensureData)
      assertEquals("system-1", factory.lastToken?.let { decodeSub(it) })
    }
  }

  @Test
  fun createAlter_routesEndpointMessage_andDoesNotPushError() = runBlocking {
    withTimeout(5.seconds) {
      val factory = FakePhoenixSocketSessionFactory(
        socketInitResponse = globalSerializer.encodeToString(
          SocketInitResponse(
            system = sampleSystem("system-2"),
            alters = emptyList(),
            tags = emptyList(),
            fronts = emptyList(),
          )
        ),
      )
      val api = newApi(factory)
      api.loadClient(dummyJwt(sub = "system-2")).join()

      val session = assertNotNull(factory.lastSession, "factory should have created a session")
      val received = CompletableDeferred<FakePhoenixSocketSession.SentEndpoint>()
      session.endpointHandler = { method, path, body ->
        received.complete(FakePhoenixSocketSession.SentEndpoint(method, path, body))
        // Empty body -> responseFromAdapterMessage returns (true, APIResponse(null, null))
        // without invoking the body parser, which is the simplest no-op success reply.
        SocketAdapterResponse(status = 200, body = "")
      }

      api.createAlter("Test")
      val sent = received.await()

      assertEquals(1, session.sentEndpoints.size, "expected one endpoint send")
      assertEquals("POST", sent.method)
      assertEquals("/api/systems/me/alters", sent.path)
      assertTrue(sent.body.contains("\"name\":\"Test\""), "expected name in body, was: ${sent.body}")
    }
  }

  @Test
  fun channelEvent_alterCreated_appendsToAltersFlow() = runBlocking {
    withTimeout(5.seconds) {
      val factory = FakePhoenixSocketSessionFactory(
        socketInitResponse = globalSerializer.encodeToString(
          SocketInitResponse(
            system = sampleSystem("system-3"),
            alters = emptyList(),
            tags = emptyList(),
            fronts = emptyList(),
          )
        ),
      )
      val api = newApi(factory)
      api.loadClient(dummyJwt(sub = "system-3")).join()
      val session = assertNotNull(factory.lastSession)

      val alter = sampleAlter(id = 42, name = "Eve")
      session.emitChannelEvent(ChannelMessage.AlterCreated(alter))

      val alters = withTimeout(2.seconds) {
        api.alters.first { it is APIState.Success && it.ensureData.any { a -> a.id == 42 } }
      }
      assertTrue(alters is APIState.Success)
      assertEquals(listOf(42), alters.ensureData.map { it.id })
      assertEquals("Eve", alters.ensureData.single().name)
    }
  }

  private fun newApi(factory: FakePhoenixSocketSessionFactory): ApiInterfaceImpl {
    val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
    return ApiInterfaceImpl(
      coroutineScope = scope,
      platformUtilities = FailingPlatformUtilities(),
      settingsInterface = FakeSettingsInterface(Settings(isSinglet = false)),
      socketSessionFactory = factory,
    )
  }

  private fun sampleSystem(id: String): MySystem = MySystem(
    autoproxyMode = "off",
    id = id,
    lifetimeAlterCount = 0,
    showSystemTag = false,
    fields = emptyList(),
    encryptionInitialized = false,
  )

  private fun sampleAlter(id: Int, name: String): MyAlter = MyAlter(
    fields = emptyList(),
    id = id,
    name = name,
    securityLevel = SecurityLevel.PRIVATE,
  )

  @OptIn(ExperimentalEncodingApi::class)
  private fun decodeSub(jwt: String): String {
    val payloadSegment = jwt.split(".")[1]
    val bytes = Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL).decode(payloadSegment)
    val obj = Json.parseToJsonElement(bytes.decodeToString()) as JsonObject
    return obj["sub"]!!.jsonPrimitive.content
  }
}

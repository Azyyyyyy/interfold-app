package app.interfold.app.api

import app.interfold.app.Settings
import app.interfold.app.api.model.APIResponse
import app.interfold.app.api.model.MyAlter
import app.interfold.app.api.model.MyFront
import app.interfold.app.api.model.MySystem
import app.interfold.app.api.model.SecurityLevel
import app.interfold.app.api.model.SocketInitResponse
import app.interfold.app.ui.compose.screens.main.hometabs.FakeSettingsInterface
import app.interfold.app.ui.model.interfaces.ApiInterfaceImpl
import app.interfold.app.utils.MonthYearPair
import app.interfold.app.utils.failingPlatformUtilities
import app.interfold.app.utils.globalSerializer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
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
 * Uses [runTest] with a real-time [timeout] so this file compiles on Kotlin/Wasm
 * (no `runBlocking` there) as well as desktop and iOS. [ApiInterfaceImpl] is
 * constructed on [Dispatchers.Unconfined]; `loadClient().join()` waits on that
 * job without needing virtual-time `delay`.
 */
class ApiInterfaceImplIntegrationTest {

  @Test
  fun loadClient_flipsInitComplete_andPopulatesState() = runTest(timeout = 10.seconds) {
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

  @Test
  fun createAlter_routesEndpointMessage_andDoesNotPushError() = runTest(timeout = 10.seconds) {
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

  @Test
  fun channelEvent_alterCreated_appendsToAltersFlow() = runTest(timeout = 10.seconds) {
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

      val alters = api.alters.first { it is APIState.Success && it.ensureData.any { a -> a.id == 42 } }
      assertTrue(alters is APIState.Success)
      assertEquals(listOf(42), alters.ensureData.map { it.id })
      assertEquals("Eve", alters.ensureData.single().name)
  }

  @Test
  fun updateDescription_postsSettingsDescription_andPersistsOnSystemMe() = runTest(timeout = 10.seconds) {
      val factory = FakePhoenixSocketSessionFactory(
        socketInitResponse = globalSerializer.encodeToString(
          SocketInitResponse(
            system = sampleSystem("system-desc", description = "old bio"),
            alters = emptyList(),
            tags = emptyList(),
            fronts = emptyList(),
          )
        ),
      )
      val api = newApi(factory)
      api.loadClient(dummyJwt(sub = "system-desc")).join()

      val session = assertNotNull(factory.lastSession, "factory should have created a session")
      val received = CompletableDeferred<FakePhoenixSocketSession.SentEndpoint>()
      session.endpointHandler = { method, path, body ->
        received.complete(FakePhoenixSocketSession.SentEndpoint(method, path, body))
        SocketAdapterResponse(status = 200, body = "")
      }

      api.updateDescription("new bio")
      val sent = received.await()

      assertEquals("POST", sent.method)
      assertEquals("/api/settings/description", sent.path)
      assertTrue(
        sent.body.contains("\"description\":\"new bio\""),
        "expected description in body, was: ${sent.body}"
      )

      val system = api.systemMe.first { it is APIState.Success && it.ensureData.description == "new bio" }
      assertTrue(system is APIState.Success)
      assertEquals("new bio", system.ensureData.description)
  }

  @Test
  fun channelEvent_selfUpdated_appliesNewDescription() = runTest(timeout = 10.seconds) {
      val factory = FakePhoenixSocketSessionFactory(
        socketInitResponse = globalSerializer.encodeToString(
          SocketInitResponse(
            system = sampleSystem("system-self", description = "old bio"),
            alters = emptyList(),
            tags = emptyList(),
            fronts = emptyList(),
          )
        ),
      )
      val api = newApi(factory)
      api.loadClient(dummyJwt(sub = "system-self")).join()
      val session = assertNotNull(factory.lastSession)

      session.emitChannelEvent(
        ChannelMessage.SelfUpdated(sampleSystem("system-self", description = "socket bio"))
      )

      val system = api.systemMe.first { it is APIState.Success && it.ensureData.description == "socket bio" }
      assertTrue(system is APIState.Success)
      assertEquals("socket bio", system.ensureData.description)
  }

  @Test
  fun channelEvent_frontDeleted_lastEntryForMonth_leavesEmptyHistory() = runTest(timeout = 10.seconds) {
      val month = sampleMonth()
      val front = sampleFront(id = "front-only", month = month)
      val (api, session) = apiWithLoadedFrontHistory(listOf(front), month)

      session.emitChannelEvent(ChannelMessage.FrontDeleted(frontID = "front-only"))

      val history = api.frontHistory.first { monthState ->
        val state = monthState[month]
        state is APIState.Success && state.ensureData.isEmpty()
      }
      val monthState = history[month]
      assertTrue(monthState is APIState.Success, "month should stay Success so the empty-state UI can render")
      assertEquals(
        emptyList(),
        monthState.ensureData,
        "deleting the last front of the month must drop empty day groups, otherwise FrontHistoryScreen keeps headers and skips 'No front history for this month.'"
      )
  }

  @Test
  fun channelEvent_frontDeleted_keepsOtherEntryOnSameDay() = runTest(timeout = 10.seconds) {
      val month = sampleMonth()
      val kept = sampleFront(id = "front-kept", month = month, startHour = 10)
      val removed = sampleFront(id = "front-removed", month = month, startHour = 14)
      val (api, session) = apiWithLoadedFrontHistory(listOf(kept, removed), month)

      session.emitChannelEvent(ChannelMessage.FrontDeleted(frontID = "front-removed"))

      val history = api.frontHistory.first { monthState ->
        val state = monthState[month]
        state is APIState.Success &&
          state.ensureData.any { group -> group.second.any { it.frontID == "front-kept" } } &&
          state.ensureData.none { group -> group.second.any { it.frontID == "front-removed" } }
      }
      val remainingIds = history[month]!!.ensureData.flatMap { it.second }.map { it.frontID }.distinct()
      assertEquals(listOf("front-kept"), remainingIds)
  }

  private fun newApi(factory: FakePhoenixSocketSessionFactory): ApiInterfaceImpl {
    val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
    return ApiInterfaceImpl(
      coroutineScope = scope,
      platformUtilities = failingPlatformUtilities(),
      settingsInterface = FakeSettingsInterface(Settings(isSinglet = false)),
      socketSessionFactory = factory,
    )
  }

  private suspend fun apiWithLoadedFrontHistory(
    fronts: List<MyFront>,
    month: MonthYearPair,
  ): Pair<ApiInterfaceImpl, FakePhoenixSocketSession> {
    val factory = FakePhoenixSocketSessionFactory(
      socketInitResponse = globalSerializer.encodeToString(
        SocketInitResponse(
          system = sampleSystem("system-fronts"),
          alters = emptyList(),
          tags = emptyList(),
          fronts = emptyList(),
        )
      ),
    )
    val api = newApi(factory)
    api.loadClient(dummyJwt(sub = "system-fronts")).join()
    val session = assertNotNull(factory.lastSession, "factory should have created a session")
    session.endpointHandler = { _, path, _ ->
      require(path.contains("systems/me/front/between")) { "unexpected path $path" }
      SocketAdapterResponse(
        status = 200,
        body = globalSerializer.encodeToString(APIResponse.success(fronts)),
      )
    }
    api.loadFrontHistory(month, force = true)
    api.frontHistory.first { it[month] is APIState.Success }
    return api to session
  }

  private fun sampleMonth(): MonthYearPair = 2024 to 3

  private fun sampleFront(
    id: String,
    month: MonthYearPair,
    startHour: Int = 10,
  ): MyFront {
    val zone = TimeZone.currentSystemDefault()
    val start = LocalDateTime(month.first, month.second, 15, startHour, 0).toInstant(zone)
    val end = LocalDateTime(month.first, month.second, 15, startHour + 2, 0).toInstant(zone)
    return MyFront(
      id = id,
      alterID = 1,
      timeStart = start,
      timeEnd = end,
      userID = "system-fronts",
    )
  }

  private fun sampleSystem(id: String, description: String? = null): MySystem = MySystem(
    autoproxyMode = "off",
    id = id,
    description = description,
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

package app.interfold.app.telemetry

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClientActivityStoreTest {
  @BeforeTest
  fun setUp() {
    ClientActivityStore.clear()
  }

  @AfterTest
  fun tearDown() {
    ClientActivityStore.clear()
  }

  @Test
  fun recordsInOrderAndClears() {
    ClientActivityStore.record(
      name = "first",
      scope = ActivityScope.APP,
      status = ActivityStatus.OK,
      durationMillis = 10,
    )
    ClientActivityStore.record(
      name = "second",
      scope = ActivityScope.APP,
      status = ActivityStatus.OK,
      durationMillis = 20,
    )

    val snapshot = ClientActivityStore.snapshot()
    assertEquals(listOf("first", "second"), snapshot.map { it.name })

    ClientActivityStore.clear()
    assertTrue(ClientActivityStore.snapshot().isEmpty())
  }

  @Test
  fun dropsOldestWhenOverCapacity() {
    repeat(ClientActivityStore.CAPACITY + 3) { index ->
      ClientActivityStore.record(
        name = "event-$index",
        scope = ActivityScope.APP,
        status = ActivityStatus.OK,
        durationMillis = 0,
      )
    }

    val snapshot = ClientActivityStore.snapshot()
    assertEquals(ClientActivityStore.CAPACITY, snapshot.size)
    assertEquals("event-3", snapshot.first().name)
    assertEquals("event-${ClientActivityStore.CAPACITY + 2}", snapshot.last().name)
  }

  @Test
  fun filtersByScopeAndStatus() {
    ClientActivityStore.record(
      name = "http.get",
      scope = ActivityScope.APP,
      status = ActivityStatus.OK,
      durationMillis = 5,
    )
    ClientActivityStore.record(
      name = "platform.log",
      scope = ActivityScope.PLATFORM_LOG,
      status = ActivityStatus.OK,
      durationMillis = 0,
      message = "debug",
    )
    ClientActivityStore.record(
      name = "uncaught.exception",
      scope = ActivityScope.APP,
      status = ActivityStatus.ERROR,
      durationMillis = 0,
    )

    val events = ClientActivityStore.snapshot()
    assertEquals(listOf("http.get", "uncaught.exception"), events.filtered(ActivityFilter.NETWORK).map { it.name })
    assertEquals(listOf("platform.log"), events.filtered(ActivityFilter.PLATFORM_LOG).map { it.name })
    assertEquals(listOf("uncaught.exception"), events.filtered(ActivityFilter.ERRORS).map { it.name })
    assertEquals(3, events.filtered(ActivityFilter.ALL).size)
  }
}

class OtlpDiscoveryStateTest {
  @Test
  fun treatsMissingDisallowedAndEmptyAsUnavailable() {
    assertEquals(OtlpDiscoveryState.Unavailable, otlpDiscoveryState(404, null))
    assertEquals(OtlpDiscoveryState.Unavailable, otlpDiscoveryState(403, null))
    assertEquals(OtlpDiscoveryState.Unavailable, otlpDiscoveryState(200, null))
    assertEquals(OtlpDiscoveryState.Unavailable, otlpDiscoveryState(200, "  "))
    assertEquals(OtlpDiscoveryState.Unavailable, otlpDiscoveryState(500, "https://collector.example:4318"))
  }

  @Test
  fun usesAdvertisedUrlWhenPresent() {
    assertEquals(
      OtlpDiscoveryState.Available("https://collector.example:4318"),
      otlpDiscoveryState(200, " https://collector.example:4318 "),
    )
  }
}

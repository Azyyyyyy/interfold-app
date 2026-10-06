package app.interfold.app.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalNetworkAddressTest {
  @Test
  fun privateAndLinkLocalIpv4NeedLocalNetworkAccess() {
    assertEquals(true, isLocalNetworkAddress("10.0.2.2"))
    assertEquals(true, isLocalNetworkAddress("10.1.2.3"))
    assertEquals(true, isLocalNetworkAddress("172.16.0.1"))
    assertEquals(true, isLocalNetworkAddress("172.31.255.255"))
    assertEquals(true, isLocalNetworkAddress("192.168.1.20"))
    assertEquals(true, isLocalNetworkAddress("169.254.1.1"))
    assertEquals(true, isLocalNetworkAddress("100.64.0.1"))
    assertEquals(true, isLocalNetworkAddress("100.127.255.255"))
    assertEquals(true, isLocalNetworkAddress("224.0.0.1"))
    assertEquals(true, isLocalNetworkAddress("255.255.255.255"))
  }

  @Test
  fun publicAndLoopbackIpv4DoNotNeedLocalNetworkAccess() {
    assertEquals(false, isLocalNetworkAddress("127.0.0.1"))
    assertEquals(false, isLocalNetworkAddress("127.1.2.3"))
    assertEquals(false, isLocalNetworkAddress("8.8.8.8"))
    assertEquals(false, isLocalNetworkAddress("172.15.255.255"))
    assertEquals(false, isLocalNetworkAddress("172.32.0.1"))
    assertEquals(false, isLocalNetworkAddress("100.63.255.255"))
    assertEquals(false, isLocalNetworkAddress("100.128.0.1"))
    assertEquals(false, isLocalNetworkAddress("192.169.0.1"))
    assertEquals(false, isLocalNetworkAddress("localhost"))
    assertEquals(false, isLocalNetworkAddress("LOCALHOST"))
  }

  @Test
  fun ipv6LocalRangesAreRecognized() {
    assertEquals(true, isLocalNetworkAddress("fe80::1"))
    assertEquals(true, isLocalNetworkAddress("FE80::abcd"))
    assertEquals(true, isLocalNetworkAddress("fc00::1"))
    assertEquals(true, isLocalNetworkAddress("fd12:3456::1"))
    assertEquals(true, isLocalNetworkAddress("ff02::1"))
    assertEquals(true, isLocalNetworkAddress("::ffff:192.168.1.20"))
    assertEquals(true, isLocalNetworkAddress("::ffff:10.0.0.1"))
    assertEquals(false, isLocalNetworkAddress("::1"))
    assertEquals(false, isLocalNetworkAddress("::"))
    assertEquals(false, isLocalNetworkAddress("::ffff:8.8.8.8"))
    assertEquals(false, isLocalNetworkAddress("2001:db8::1"))
  }

  @Test
  fun mdnsNamesNeedLocalNetworkAccessAndOtherNamesNeedResolution() {
    assertEquals(true, isLocalNetworkAddress("api.local"))
    assertEquals(true, isLocalNetworkAddress("Printer.LOCAL"))
    assertNull(isLocalNetworkAddress("api.interfold.co.uk"))
    assertNull(isLocalNetworkAddress("server.home"))
  }

  @Test
  fun bracketedAndZoneIdsAreStripped() {
    assertEquals(true, isLocalNetworkAddress("[fe80::1%wlan0]"))
    assertEquals(false, isLocalNetworkAddress("[::1]"))
  }
}

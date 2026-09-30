package app.interfold.app.utils

/**
 * Android 17 treats these ranges as local-network destinations. Loopback is not
 * one of them: traffic to localhost stays on the device and does not need
 * [android.permission.ACCESS_LOCAL_NETWORK].
 *
 * Returns true or false for a literal address, `localhost`, or a `.local` name.
 * Returns null when [host] is a DNS name that has to be resolved before the
 * decision can be made.
 */
internal fun isLocalNetworkAddress(host: String): Boolean? {
  val normalized = host.trim().removePrefix("[").removeSuffix("]").substringBefore('%')
  if (normalized.equals("localhost", ignoreCase = true)) return false
  if (normalized.endsWith(".local", ignoreCase = true)) return true
  val bytes = parseIpBytes(normalized) ?: return null
  return isLocalNetworkIp(bytes)
}

internal fun isLocalNetworkIp(bytes: ByteArray): Boolean {
  if (bytes.size == 16 && isIpv4Mapped(bytes)) {
    return isLocalNetworkIp(bytes.copyOfRange(12, 16))
  }
  return when (bytes.size) {
    4 -> isLocalIpv4(bytes)
    16 -> isLocalIpv6(bytes)
    else -> false
  }
}

private fun isLocalIpv4(bytes: ByteArray): Boolean {
  val a = bytes[0].toInt() and 0xff
  val b = bytes[1].toInt() and 0xff
  val c = bytes[2].toInt() and 0xff
  val d = bytes[3].toInt() and 0xff
  if (a == 127) return false
  if (a == 10) return true
  if (a == 172 && b in 16..31) return true
  if (a == 192 && b == 168) return true
  if (a == 169 && b == 254) return true
  if (a == 100 && b in 64..127) return true
  if (a in 224..239) return true
  if (a == 255 && b == 255 && c == 255 && d == 255) return true
  return false
}

private fun isLocalIpv6(bytes: ByteArray): Boolean {
  if (isUnspecifiedOrLoopbackV6(bytes)) return false
  val a = bytes[0].toInt() and 0xff
  val b = bytes[1].toInt() and 0xff
  if (a == 0xfe && (b and 0xc0) == 0x80) return true
  if ((a and 0xfe) == 0xfc) return true
  if (a == 0xff) return true
  return false
}

private fun isUnspecifiedOrLoopbackV6(bytes: ByteArray): Boolean {
  for (i in 0 until 15) {
    if (bytes[i] != 0.toByte()) return false
  }
  val last = bytes[15].toInt() and 0xff
  return last == 0 || last == 1
}

private fun isIpv4Mapped(bytes: ByteArray): Boolean {
  for (i in 0 until 10) {
    if (bytes[i] != 0.toByte()) return false
  }
  return bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte()
}

private fun parseIpBytes(host: String): ByteArray? {
  parseIpv4(host)?.let { return it }
  return parseIpv6(host)
}

private fun parseIpv4(host: String): ByteArray? {
  val parts = host.split('.')
  if (parts.size != 4) return null
  val bytes = ByteArray(4)
  for (i in parts.indices) {
    val part = parts[i]
    if (part.isEmpty() || part.length > 3 || part.any { it !in '0'..'9' }) return null
    val value = part.toIntOrNull() ?: return null
    if (value !in 0..255) return null
    bytes[i] = value.toByte()
  }
  return bytes
}

private fun parseIpv6(input: String): ByteArray? {
  if (input.isEmpty() || !input.contains(':')) return null
  val firstCompression = input.indexOf("::")
  val lastCompression = input.lastIndexOf("::")
  if (firstCompression != lastCompression) return null

  val headTail = if (firstCompression >= 0) {
    listOf(input.substring(0, firstCompression), input.substring(firstCompression + 2))
  } else {
    listOf(input)
  }

  fun parseGroups(part: String): List<Int>? {
    if (part.isEmpty()) return emptyList()
    val pieces = part.split(':')
    val groups = ArrayList<Int>(pieces.size + 1)
    pieces.forEachIndexed { index, piece ->
      if (piece.isEmpty()) return null
      if (piece.contains('.')) {
        if (index != pieces.lastIndex) return null
        val v4 = parseIpv4(piece) ?: return null
        groups += ((v4[0].toInt() and 0xff) shl 8) or (v4[1].toInt() and 0xff)
        groups += ((v4[2].toInt() and 0xff) shl 8) or (v4[3].toInt() and 0xff)
      } else {
        if (piece.length > 4) return null
        groups += piece.toIntOrNull(16) ?: return null
      }
    }
    return groups
  }

  val head = parseGroups(headTail[0]) ?: return null
  val tail = if (headTail.size == 2) parseGroups(headTail[1]) ?: return null else emptyList()
  val missing = 8 - head.size - tail.size
  if (headTail.size == 1) {
    if (missing != 0) return null
  } else if (missing < 1) {
    return null
  }

  val groups = ArrayList<Int>(8)
  groups += head
  repeat(missing.coerceAtLeast(0)) { groups += 0 }
  groups += tail
  if (groups.size != 8) return null

  val bytes = ByteArray(16)
  groups.forEachIndexed { index, group ->
    bytes[index * 2] = (group shr 8).toByte()
    bytes[index * 2 + 1] = (group and 0xff).toByte()
  }
  return bytes
}

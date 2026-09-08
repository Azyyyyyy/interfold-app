package app.interfold.app.utils

import java.util.UUID

actual fun generateUUID(): String {
  return UUID.randomUUID().toString()
}
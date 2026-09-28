// Fixtures for BrowserErrorEventTest. Imported by Kotlin as @interfold/sample-browser-event.

function withName(name) {
  const ctor = function () {};
  Object.defineProperty(ctor, "name", { value: name });
  return ctor;
}

export function sampleBrowserEvent(kind) {
  if (kind === "image") {
    const img = {};
    Object.defineProperty(img, "constructor", { value: withName("HTMLImageElement") });
    img.src = "https://cdn.example/a.png?sig=secret";
    return { type: "error", message: "", target: img };
  }
  if (kind === "type-error") {
    return {
      type: "error",
      message: "Uncaught TypeError: boom",
      filename: "https://app.example/main.js?token=secret#L1",
      lineno: 12,
      colno: 4,
      error: {
        name: "TypeError",
        message: "boom",
        stack: "TypeError: boom\n    at boom (https://app.example/main.js?token=secret:12:4)",
      },
    };
  }
  if (kind === "wrapped-message") {
    return {
      type: "error",
      error: {
        name: "RuntimeError",
        message: { toString: function () { return "memory access out of bounds"; } },
        stack: "RuntimeError: memory access out of bounds\n    at wasm",
      },
    };
  }
  if (kind === "socket") {
    const ws = new (withName("WebSocket"))();
    ws.url = "wss://api.example/socket?access=secret";
    ws.readyState = 3;
    return { type: "unhandledrejection", reason: { type: "error", target: ws } };
  }
  if (kind === "idb") {
    const request = {};
    Object.defineProperty(request, "constructor", { value: withName("IDBRequest") });
    request.error = { name: "NotFoundError", message: "missing key" };
    return { type: "unhandledrejection", reason: { type: "error", target: request } };
  }
  return { type: "error", message: "Script error." };
}

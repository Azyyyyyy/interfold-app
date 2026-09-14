# Interfold API — client telemetry contracts

Client already ships both of these. Until the API implements them:

- Endpoint W3C fields are ignored (or must not break deserialize).
- “Share activity with server” always shows “no OTLP” and exports nowhere from discovery.

The API **must not ingest OTLP**. Discovery only — return a collector URL the client exports to.

---

## 1. Phoenix `endpoint` — W3C propagation fields

### Where

`WebSocketHandler.HandleEndpointProxyAsync` (or whatever handles Phoenix event `"endpoint"`).

### Payload (today + new optional fields)

```json
{
  "method": "GET",
  "path": "/api/alters",
  "body": "",
  "traceparent": "00-<trace-id>-<span-id>-01",
  "tracestate": "vendor=value"
}
```

| Field | Required | Notes |
|--------|----------|--------|
| `method` | yes | HTTP method string |
| `path` | yes | Must start with `/api` (unchanged) |
| `body` | yes | JSON **string**, not nested object (unchanged) |
| `traceparent` | no | W3C Trace Context |
| `tracestate` | no | W3C Trace Context; often omitted when empty |

Client merges `traceparent` / `tracestate` as **top-level** keys next to `method` / `path` / `body`. Empty `tracestate` is omitted.

### Required server behavior

1. **Deserialize must tolerate extra properties**  
   Either ignore unknowns, or add optional `string? TraceParent` / `string? TraceState` on `PhxEndpointPayload`.  
   Older clients send only `method`/`path`/`body`. New clients may send the two extras. **Do not 400 on unknown keys.**

2. **If `traceparent` is present**, before the internal REST dispatch:
   - `TextMapPropagator.extract` into the current OTel context (carrier = payload map / header-like getter for those two keys).
   - Run the proxied REST under that context so API spans are **children** of the client `sendAPIRequest` span.

3. **Do not**:
   - Persist `traceparent` / `tracestate`
   - Copy them onto the loopback/proxied HTTP request unless that hop is itself traced and you intentionally continue the context there
   - Require auth changes for these fields

4. **Without extract**: client spans still exist on-device / at the user’s collector; they just won’t parent server spans.

### Example extract sketch (.NET)

```csharp
// Pseudocode — adapt to your OTel wiring
if (!string.IsNullOrEmpty(payload.Traceparent))
{
    var carrier = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
    carrier["traceparent"] = payload.Traceparent;
    if (!string.IsNullOrEmpty(payload.Tracestate))
        carrier["tracestate"] = payload.Tracestate;

    var parent = Propagators.DefaultTextMapPropagator.Extract(
        default,
        carrier,
        static (c, key) =>
            c.TryGetValue(key, out var v) ? new[] { v } : Enumerable.Empty<string>());

    using var scope = parent.Activate();
    // dispatch proxied REST inside this scope
}
```

---

## 2. OTLP discovery — `GET /api/telemetry/otlp`

### Purpose

When the user enables **Share activity with server**, the client calls:

```http
GET {apiEndpoint}/api/telemetry/otlp
```

If the response advertises a collector URL, the client exports OTLP/HTTP **spans** (and logs when export is on) to that URL.  
**The Interfold API does not receive or store those traces.** It only tells the client where a collector lives (e.g. Aspire collector, Grafana Alloy, your own OTel collector).

Custom OTLP URL in settings is independent; both can be set (client fans out to both).

### Request

- Method: `GET`
- Path: `/api/telemetry/otlp` (not `/otlp`, not `/v1/traces`)
- Auth: match whatever you use for authenticated app APIs (client uses the same `HttpClient` / session as other REST). Returning **403** or **404** is treated as “no OTLP” (not a hard failure).

### Success response

**Status:** `2xx`

**Body (JSON):** camelCase field name the client already deserializes:

```json
{
  "otlpHttpEndpoint": "http://otel-collector:4318"
}
```

| Field | Type | Meaning |
|--------|------|---------|
| `otlpHttpEndpoint` | string | Base OTLP/HTTP endpoint the OpenTelemetry Kotlin exporters use. Typically the collector root that serves `/v1/traces` (and `/v1/logs`), e.g. `http://host:4318` — **not** the Interfold API URL. |

Whitespace is trimmed. Empty / missing → client treats as unavailable.

### Unavailable / not configured

Any of these → client shows “This server does not share an OTLP address” and does not export via discovery:

- `404` / `403`
- Non-`2xx`
- `2xx` with null, missing, or blank `otlpHttpEndpoint`
- Network failure

Suggested when no collector is configured:

- `404` with empty body, **or**
- `200` with `{ "otlpHttpEndpoint": null }` / omit the field

### Hard rules

| Do | Don’t |
|----|--------|
| Advertise a real OTLP/HTTP collector URL | Accept OTLP on the Interfold API |
| Config-drive the URL (env / Aspire) | Hard-code a SaaS crash vendor |
| Keep discovery cheap and read-only | Require the client to POST traces to `/api/...` |

### Client acceptance (already implemented)

```text
403, 404, or status outside 200–299  → Unavailable
200 + empty/null otlpHttpEndpoint   → Unavailable
200 + non-empty otlpHttpEndpoint    → Available(url.trim())
```

---

## 3. Suggested API checklist

**Endpoint proxy**

- [ ] `PhxEndpointPayload` ignores unknowns **or** adds optional `traceparent` / `tracestate`
- [ ] Extract W3C context before proxied REST when `traceparent` present
- [ ] Existing clients without those fields still work
- [ ] Fields not logged/persisted as PII

**Discovery**

- [ ] `GET /api/telemetry/otlp` exists
- [ ] Returns `{ "otlpHttpEndpoint": "<collector>" }` when configured
- [ ] Returns 404/403/empty when not configured
- [ ] API never hosts OTLP ingest

---

## 4. How the client uses this

```text
Settings: Share with server ON
  → GET {api}/api/telemetry/otlp
  → if Available, export OTLP/HTTP to that URL (+ optional custom URL)

Phoenix "endpoint" send
  → payload: method, path, body, [traceparent], [tracestate]
  → API extract → proxied REST spans under client span
```

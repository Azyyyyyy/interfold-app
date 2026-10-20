// Describes a browser error or unhandled rejection for the WASM activity log.
// Imported by Kotlin as @interfold/describe-js-error-event.

function textOf(value) {
  try {
    if (value == null) return "";
    const type = typeof value;
    if (type === "string") return value;
    if (type === "number" || type === "boolean") return String(value);
    if (type === "object" && typeof value.toString === "function") {
      const viaToString = value.toString();
      if (typeof viaToString === "string" && viaToString && viaToString.indexOf("[object ") !== 0) {
        return viaToString;
      }
      const viaString = String(value);
      if (typeof viaString === "string" && viaString && viaString.indexOf("[object ") !== 0) {
        return viaString;
      }
    }
  } catch (_ex) {
    // Cross-origin objects can throw on property access.
  }
  return "";
}

function useful(value) {
  const text = textOf(value).trim();
  if (!text || text === "Script error." || text.indexOf("[object ") === 0) return "";
  return text;
}

function clip(value, max) {
  const text = typeof value === "string" ? value : textOf(value);
  if (!text) return "";
  return text.length > max ? text.slice(0, max) + "..." : text;
}

function clipUrl(value) {
  const text = textOf(value).trim();
  if (!text) return "";
  if (text.indexOf("data:") === 0) {
    const semi = text.indexOf(";");
    const comma = text.indexOf(",");
    const end = semi > 0 ? semi : (comma > 0 ? comma : Math.min(text.length, 40));
    return text.slice(0, Math.min(end, 40));
  }
  return clip(text.split("#")[0].split("?")[0], 180);
}

function ctorName(value) {
  try {
    if (!value || (typeof value !== "object" && typeof value !== "function")) return "";
    const name = value.constructor && value.constructor.name;
    if (typeof name === "string" && name && name !== "Object") return name;
    const tag = Object.prototype.toString.call(value);
    if (tag.indexOf("[object ") === 0 && tag.charAt(tag.length - 1) === "]") {
      const kind = tag.slice(8, -1);
      if (kind && kind !== "Object") return kind;
    }
  } catch (_ex) {
    // Ignore hosts that hide constructor.
  }
  return "";
}

function isEventLike(value) {
  if (!value || typeof value !== "object") return false;
  if (typeof value.preventDefault === "function") return true;
  const type = textOf(value.type);
  return !!type && (
    value.target != null ||
    type === "error" ||
    type === "unhandledrejection" ||
    type === "abort" ||
    type === "close" ||
    type === "timeout"
  );
}

function targetOf(value) {
  try {
    if (!value || (typeof value !== "object" && typeof value !== "function")) return null;
    if (typeof window !== "undefined" && (value === window || value === window.document)) return null;
    return value;
  } catch (_ex) {
    return null;
  }
}

function describeTarget(target) {
  const kind = ctorName(target) || textOf(target.tagName);
  const bits = [];
  const src = clipUrl(target.currentSrc || target.src || target.href || target.url || "");
  if (src) bits.push(src);
  if (typeof target.readyState === "number") bits.push("readyState=" + target.readyState);
  if (typeof target.status === "number" && target.status) bits.push("status=" + target.status);
  let errorName = "";
  let errorMessage = "";
  try {
    const dbError = target.error;
    if (dbError && typeof dbError === "object" && !isEventLike(dbError)) {
      errorName = useful(dbError.name);
      errorMessage = useful(dbError.message);
    }
  } catch (_ex) {
    // IndexedDB request.error can throw.
  }
  return { kind: kind, detail: bits.join(" "), errorName: errorName, errorMessage: errorMessage };
}

function payload(fields) {
  return JSON.stringify({
    eventType: fields.eventType || "",
    message: fields.message || "",
    filename: fields.filename || "",
    line: fields.line || 0,
    column: fields.column || 0,
    errorName: fields.errorName || "",
    errorMessage: fields.errorMessage || "",
    errorStack: fields.errorStack || "",
    errorKind: fields.errorKind || "",
    nestedType: fields.nestedType || "",
    targetKind: fields.targetKind || "",
    targetDetail: fields.targetDetail || "",
    describeFailure: fields.describeFailure || "",
  });
}

export function describeJsErrorEvent(event) {
  try {
    if (typeof event === "string" || typeof event === "number" || typeof event === "boolean") {
      return payload({ errorMessage: clip(useful(event), 300) });
    }
    const e = (event && typeof event === "object") ? event : {};
    const nested = isEventLike(e.reason) ? e.reason : (isEventLike(e.error) ? e.error : null);
    let thrown = null;
    if (isEventLike(e)) {
      const candidate = e.error != null ? e.error : e.reason;
      if (typeof candidate === "string" || typeof candidate === "number" || typeof candidate === "boolean") {
        thrown = candidate;
      } else if (candidate && typeof candidate === "object" && !isEventLike(candidate)) {
        thrown = candidate;
      }
    } else if (event && typeof event === "object") {
      thrown = event;
    }
    let errorName = "";
    let errorMessage = "";
    let errorStack = "";
    let errorKind = "";
    if (typeof thrown === "string" || typeof thrown === "number" || typeof thrown === "boolean") {
      errorMessage = useful(thrown);
    } else if (thrown && typeof thrown === "object") {
      errorKind = ctorName(thrown);
      errorName = textOf(thrown.name).trim();
      errorMessage = useful(thrown.message);
      errorStack = textOf(thrown.stack);
      if (!errorMessage) {
        const asString = textOf(thrown);
        const lowered = asString.toLowerCase();
        if (
          asString &&
          lowered !== errorName.toLowerCase() &&
          lowered !== "event" &&
          lowered !== "object" &&
          lowered !== "error" &&
          lowered !== "errorevent"
        ) {
          errorMessage = asString;
        }
      }
    }
    const carrier = nested || e;
    const rawTarget = targetOf(carrier.target);
    let targetKind = "";
    let targetDetail = "";
    if (rawTarget) {
      const described = describeTarget(rawTarget);
      targetKind = described.kind;
      targetDetail = described.detail;
      if (!errorMessage && described.errorMessage) {
        errorName = described.errorName || errorName;
        errorMessage = described.errorMessage;
      } else if (!errorName && described.errorName) {
        errorName = described.errorName;
      }
    }
    const nestedType = nested ? textOf(nested.type).trim() : "";
    return payload({
      eventType: textOf(e.type).trim(),
      message: textOf(e.message).trim(),
      filename: clipUrl(e.filename),
      line: typeof e.lineno === "number" ? e.lineno : 0,
      column: typeof e.colno === "number" ? e.colno : 0,
      errorName: errorName,
      errorMessage: clip(errorMessage, 300),
      errorStack: clip(errorStack, 800),
      errorKind: errorKind,
      nestedType: nestedType,
      targetKind: targetKind,
      targetDetail: targetDetail,
    });
  } catch (ex) {
    const failureName = textOf(ex && ex.name);
    const failureMessage = textOf(ex && ex.message);
    const failure = clip((failureName ? failureName + ": " : "") + failureMessage, 200) || "describe failed";
    return payload({ describeFailure: failure });
  }
}

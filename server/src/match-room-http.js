// Keep MatchRoom JSON parsing contract distinct from Friends' stricter parser.
import { httpError } from "./crypto.js";

export async function readJson(request) {
  try {
    const value = await request.json();
    if (!value || typeof value !== "object" || Array.isArray(value)) {
      throw new Error("object required");
    }
    return value;
  } catch {
    throw httpError(400, "INVALID_JSON", "request body must be a JSON object");
  }
}

export function json(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "x-content-type-options": "nosniff",
    },
  });
}

export function errorResponse(error) {
  const status =
    Number.isInteger(error?.status) && error.status >= 400
      ? error.status
      : Number.isInteger(error?.status) && error.status < 400
        ? 500
        : (error?.status ?? 500);
  return json(
    status >= 400 && status <= 599 ? status : 500,
    {
      error: error?.code ?? "INTERNAL_ERROR",
      message:
        status >= 500 && !error?.code
          ? "internal server error"
          : String(error?.message ?? "internal server error"),
    },
  );
}

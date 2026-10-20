// Preserve Friends' strict 16KB, JSON content-type and public error behavior.
import { httpError } from "./crypto.js";

export async function readJson(
  request,
) {
  const type =
    request.headers.get(
      "content-type",
    ) ??
    "";
  if (
    !type
      .toLowerCase()
      .startsWith(
        "application/json",
      )
  ) {
    throw httpError(
      415,
      "UNSUPPORTED_MEDIA_TYPE",
      "content-type must be application/json",
    );
  }

  const text =
    await request.text();
  if (
    new TextEncoder()
      .encode(
        text,
      )
      .byteLength >
    16 * 1024
  ) {
    throw httpError(
      413,
      "REQUEST_TOO_LARGE",
      "request body is too large",
    );
  }

  try {
    const value =
      text.length ===
      0
        ? {}
        : JSON.parse(
            text,
          );
    if (
      !value ||
      typeof value !==
        "object" ||
      Array.isArray(
        value,
      )
    ) {
      throw new Error(
        "object required",
      );
    }
    return value;
  } catch {
    throw httpError(
      400,
      "INVALID_JSON",
      "request body must be valid JSON",
    );
  }
}

export function json(
  status,
  body,
) {
  return new Response(
    JSON.stringify(
      body,
    ),
    {
      status,
      headers: {
        "content-type":
          "application/json; charset=utf-8",
        "cache-control":
          "no-store",
        "x-content-type-options":
          "nosniff",
      },
    },
  );
}

export function errorResponse(
  error,
) {
  const status =
    Number.isInteger(
      error
        ?.status,
    )
      ? error.status
      : 500;

  return json(
    status,
    {
      error:
        error
          ?.code ??
        "INTERNAL_ERROR",
      message:
        status ===
          500 &&
        !error
          ?.code
          ? "internal server error"
          : String(
              error
                ?.message ??
                "internal server error",
            ),
    },
  );
}

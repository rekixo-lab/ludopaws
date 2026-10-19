package com.rekixo.ludopaws

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/** REST, realtime and exit requests reuse the same sockets and HTTP/2 connections. */
internal object GameHttpTransport {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    val exitClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(3_500, TimeUnit.MILLISECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .callTimeout(9, TimeUnit.SECONDS)
            .build()
    }

    fun executeJson(
        client: OkHttpClient,
        request: Request,
        maxResponseBytes: Int,
    ): JSONObject = client.newCall(request).execute().use { response ->
        val body = response.body
        if (body != null && body.contentLength() > maxResponseBytes) {
            throw responseTooLarge()
        }
        val output = ByteArrayOutputStream()
        body?.byteStream()?.use { stream ->
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > maxResponseBytes) throw responseTooLarge()
                output.write(buffer, 0, count)
            }
        }
        val text = output.toByteArray().toString(Charsets.UTF_8)
        val json = runCatching {
            if (text.isBlank()) JSONObject() else JSONObject(text)
        }.getOrElse { error ->
            if (response.isSuccessful) throw error
            JSONObject()
        }
        if (!response.isSuccessful) {
            throw GameApiException(
                code = json.optString("error", "HTTP_${response.code}"),
                message = json.optString("message", "Request failed with HTTP ${response.code}"),
            )
        }
        json
    }

    private fun responseTooLarge() = GameApiException(
        code = "RESPONSE_TOO_LARGE",
        message = "Server response exceeded the safety limit.",
    )
}

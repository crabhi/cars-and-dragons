package cz.crabhi.dragons.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Minimal JSON-over-HTTP client on HttpURLConnection, available on both the JVM and Android. */
open class HttpClient(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 60_000,
) {
    class HttpException(
        val status: Int,
        val body: String,
    ) : IOException("HTTP $status: ${body.take(300)}")

    open suspend fun postJson(
        url: String,
        headers: Map<String, String>,
        body: String,
    ): String =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = connectTimeoutMs
                conn.readTimeout = readTimeoutMs
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val status = conn.responseCode
                val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (status !in 200..299) throw HttpException(status, text)
                text
            } finally {
                conn.disconnect()
            }
        }
}

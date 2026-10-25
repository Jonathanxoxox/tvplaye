package io.github.jonathanxoxox.tvkanaele

import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

class HttpException(val code: Int, url: String) : IOException("HTTP $code bei ${redact(url)}")

/** Entfernt Zugangsdaten aus Adressen, bevor sie in Fehlermeldungen landen. */
fun redact(url: String): String = url
    .replace(Regex("(username|password)=[^&]*"), "$1=***")
    .replace(Regex("/live/[^/]+/[^/]+/"), "/live/***/***/")

internal object Http {
    const val DEFAULT_USER_AGENT = "TVKanaele/1.0 (Android TV)"

    private fun open(url: String, userAgent: String?, timeoutMs: Int = 30_000): HttpURLConnection {
        var current = url
        repeat(5) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", userAgent?.takeIf { it.isNotBlank() } ?: DEFAULT_USER_AGENT)
            conn.setRequestProperty("Accept-Encoding", "gzip")
            val code = conn.responseCode
            if (code in 300..399) {
                // Auch Wechsel zwischen http und https folgen, das macht HttpURLConnection nicht selbst.
                val location = conn.getHeaderField("Location") ?: throw HttpException(code, current)
                conn.disconnect()
                current = URL(URL(current), location).toString()
                return@repeat
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw HttpException(code, current)
            }
            return conn
        }
        throw IOException("Zu viele Weiterleitungen bei ${redact(url)}")
    }

    /** Lädt [url] in [target]. Gzip wird ausgepackt, egal ob vom Server oder als .gz-Datei. */
    fun download(url: String, target: File, userAgent: String?) {
        val conn = open(url, userAgent, timeoutMs = 120_000)
        try {
            val tmp = File(target.parentFile, target.name + ".part")
            unpack(conn.inputStream).use { input -> tmp.outputStream().use { input.copyTo(it, 64 * 1024) } }
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        } finally {
            conn.disconnect()
        }
    }

    fun getText(url: String, userAgent: String?): String {
        val conn = open(url, userAgent)
        try {
            return unpack(conn.inputStream).bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun getBytes(url: String, maxBytes: Int, timeoutMs: Int = 10_000): ByteArray? = try {
        val conn = open(url, null, timeoutMs)
        try {
            if (conn.contentLengthLong > maxBytes) null
            else unpack(conn.inputStream).use { it.readNBytesCompat(maxBytes) }
        } finally {
            conn.disconnect()
        }
    } catch (_: IOException) {
        null
    }

    /** Erkennt Gzip an den ersten zwei Bytes, unabhängig von Header oder Dateiendung. */
    private fun unpack(stream: InputStream): InputStream {
        val buffered = BufferedInputStream(stream, 64 * 1024)
        buffered.mark(2)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()
        return if (b1 == 0x1f && b2 == 0x8b) BufferedInputStream(GZIPInputStream(buffered, 64 * 1024)) else buffered
    }

    private fun InputStream.readNBytesCompat(max: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) return null
        }
        return out.toByteArray()
    }
}

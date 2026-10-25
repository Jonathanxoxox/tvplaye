package io.github.jonathanxoxox.tvkanaele

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import io.github.jonathanxoxox.tvkanaele.core.IptvChannel
import io.github.jonathanxoxox.tvkanaele.core.Programme
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Zeichnet Kacheln im Format 16:9 mit Senderlogo, Sendung, Uhrzeit und Fortschrittsbalken,
 * damit man schon im Launcher sieht, was läuft. Die Bilder liefert [TileProvider] an den Launcher.
 */
class TileRenderer(private val context: Context, private val enabled: Boolean) {
    private val tilesDir = File(context.filesDir, "tiles").apply { mkdirs() }
    private val logoDir = File(context.cacheDir, "logos").apply { mkdirs() }
    private val logoCache = mutableMapOf<String, Bitmap?>()
    private val stamp = System.currentTimeMillis()

    /**
     * Liefert die Bildadresse für eine Kachel. Ohne eigene Kacheln (oder wenn das Zeichnen scheitert)
     * das Senderlogo bzw. Sendungsbild aus Playlist und EPG.
     */
    fun tile(id: String, channel: IptvChannel, programme: Programme?, badge: String?, now: Long): Uri? {
        val fallback = (programme?.icon ?: channel.logo)?.let { Uri.parse(it) }
        if (!enabled) return fallback
        return try {
            val name = "${hash(id)}_$stamp.png"
            val file = File(tilesDir, name)
            val bitmap = draw(channel, programme, badge, now)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            Uri.Builder().scheme("content").authority(TileProvider.authority(context)).appendPath(name).build()
        } catch (e: Exception) {
            android.util.Log.w("TileRenderer", "Kachel $id nicht gezeichnet", e)
            fallback
        }
    }

    /** Löscht Kacheln älterer Durchläufe (mit Puffer, falls der Launcher sie gerade lädt). */
    fun cleanup() {
        val cutoff = stamp - 60 * 60_000L
        tilesDir.listFiles()?.forEach { f ->
            val t = f.name.substringAfter('_').substringBefore('.').toLongOrNull() ?: 0L
            if (t < cutoff) f.delete()
        }
        logoDir.listFiles()?.filter { stamp - it.lastModified() > 7 * 24 * 3_600_000L }?.forEach { it.delete() }
    }

    private fun draw(channel: IptvChannel, programme: Programme?, badge: String?, now: Long): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), Color.rgb(0x1B, 0x23, 0x38), Color.rgb(0x0B, 0x0E, 0x16), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)

        // Logo oben links auf hellem Feld, damit auch dunkle Logos lesbar sind.
        val box = RectF(PAD, PAD, PAD + LOGO_W, PAD + LOGO_H)
        c.drawRoundRect(box, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 245, 246, 250) })
        val logo = channel.logo?.let { loadLogo(it) }
        if (logo != null) {
            val scale = minOf((LOGO_W - 16) / logo.width, (LOGO_H - 16) / logo.height)
            val w = logo.width * scale
            val h = logo.height * scale
            val dst = RectF(box.centerX() - w / 2, box.centerY() - h / 2, box.centerX() + w / 2, box.centerY() + h / 2)
            c.drawBitmap(logo, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        } else {
            val initials = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(0x1B, 0x23, 0x38); textSize = 44f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
            }
            val text = channel.name.filter { it.isLetterOrDigit() || it == ' ' }.split(' ')
                .filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }.uppercase()
            c.drawText(text, box.centerX(), box.centerY() + 16f, initials)
        }

        // Sendername rechts neben dem Logo
        val nameX = box.right + 20f
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xC9, 0xD1, 0xE6); textSize = 26f }
        val name = TextUtils.ellipsize(channel.name, namePaint, W - nameX - PAD, TextUtils.TruncateAt.END)
        c.drawText(name, 0, name.length, nameX, PAD + 34f, namePaint)

        // Badge (LIVE / Uhrzeit) unter dem Namen
        val running = programme?.isRunning(now) == true
        val badgeText = badge ?: programme?.let { if (running) "LIVE" else startLabel(it.start, now) }
        if (badgeText != null) {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 24f; typeface = Typeface.DEFAULT_BOLD }
            val tw = p.measureText(badgeText)
            val r = RectF(nameX, PAD + 54f, nameX + tw + 24f, PAD + 90f)
            c.drawRoundRect(r, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (running) Color.rgb(0xE5, 0x39, 0x35) else Color.rgb(0x3D, 0x5A, 0xFE)
            })
            c.drawText(badgeText, r.left + 12f, r.bottom - 10f, p)
        }

        // Titel der Sendung, bis zu zwei Zeilen
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 38f; typeface = Typeface.DEFAULT_BOLD }
        val title = programme?.title ?: context.getString(R.string.no_epg)
        val layout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, (W - 2 * PAD).toInt())
            .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.0f).build()
        c.save()
        c.translate(PAD, box.bottom + 22f)
        layout.draw(c)
        c.restore()

        if (programme != null) {
            val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x9A, 0xA4, 0xBF); textSize = 24f }
            c.drawText("${TIME.format(programme.start)}–${TIME.format(programme.end)}", PAD, H - 30f, timePaint)
            if (running) {
                val total = (programme.end - programme.start).coerceAtLeast(1)
                val frac = ((now - programme.start).toFloat() / total).coerceIn(0f, 1f)
                val track = Paint().apply { color = Color.argb(70, 255, 255, 255) }
                c.drawRect(0f, H - 8f, W.toFloat(), H.toFloat(), track)
                c.drawRect(0f, H - 8f, W * frac, H.toFloat(), Paint().apply { color = Color.rgb(0xE5, 0x39, 0x35) })
            }
        }
        return bmp
    }

    private fun startLabel(start: Long, now: Long): String {
        val minutes = (start - now) / 60_000
        if (minutes in 0..59) return context.getString(R.string.badge_in_minutes, minutes.coerceAtLeast(1))
        val day = Calendar.getInstance().apply { timeInMillis = start }.get(Calendar.DAY_OF_YEAR)
        val today = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_YEAR)
        return if (day == today) TIME.format(start) else context.getString(R.string.badge_tomorrow, TIME.format(start))
    }

    private fun loadLogo(url: String): Bitmap? = logoCache.getOrPut(url) {
        val file = File(logoDir, hash(url))
        val bytes = if (file.exists()) file.readBytes() else Http.getBytes(url, 2 * 1024 * 1024)?.also { file.writeBytes(it) }
        bytes?.let { decode(it) }
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= LOGO_W && bounds.outHeight / (sample * 2) >= LOGO_H) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    companion object {
        private const val W = 640
        private const val H = 360
        private const val PAD = 28f
        private const val LOGO_W = 200f
        private const val LOGO_H = 112f
        private val TIME = SimpleDateFormat("HH:mm", Locale.GERMANY)

        fun hash(s: String): String =
            MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)
    }
}

/** Gibt die gezeichneten Kacheln an den Launcher heraus (nur lesend, nur PNGs aus files/tiles). */
class TileProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        if (!name.matches(Regex("[0-9a-f]+_[0-9]+\\.png"))) throw FileNotFoundException(name)
        val file = File(File(context!!.filesDir, "tiles"), name)
        if (!file.exists()) throw FileNotFoundException(name)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "image/png"
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    companion object {
        fun authority(context: Context) = "${context.packageName}.tiles"
    }
}

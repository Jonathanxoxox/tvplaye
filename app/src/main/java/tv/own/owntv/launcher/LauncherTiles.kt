package tv.own.owntv.launcher

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
import tv.own.owntv.R
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Draws the 16:9 launcher tiles: channel logo, the programme on air, its times and progress, so the
 * home screen already shows what is on. [LauncherTileProvider] hands the files to the launcher.
 */
internal class LauncherTiles(private val context: Context) {
    private val tilesDir = File(context.filesDir, DIR).apply { mkdirs() }
    private val logoDir = File(context.cacheDir, LOGO_DIR).apply { mkdirs() }
    private val logoCache = HashMap<String, Bitmap?>()
    private val stamp = System.currentTimeMillis()

    class Content(
        val channelName: String,
        val logoUrl: String?,
        val title: String?,
        val badge: String?,
        val live: Boolean,
        val timeRange: String?,
        val progress: Float?,
    )

    /** The tile's address, or the channel logo when drawing fails. */
    fun tile(id: String, content: Content): Uri? = runCatching {
        val name = "${hash(id)}_$stamp.png"
        val bitmap = draw(content)
        File(tilesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        Uri.Builder().scheme("content").authority(LauncherTileProvider.authority(context)).appendPath(name).build()
    }.getOrElse { content.logoUrl?.let(Uri::parse) }

    /** Drops tiles of earlier runs, with an hour's slack in case a launcher is still loading one. */
    fun cleanup() {
        val cutoff = stamp - 60 * 60_000L
        tilesDir.listFiles()?.forEach { f ->
            val t = f.name.substringAfter('_').substringBefore('.').toLongOrNull() ?: 0L
            if (t < cutoff) f.delete()
        }
        logoDir.listFiles()?.filter { stamp - it.lastModified() > 7 * 24 * 3_600_000L }?.forEach { it.delete() }
    }

    private fun draw(c: Content): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), Color.rgb(0x26, 0x29, 0x30), Color.rgb(0x12, 0x13, 0x17), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)

        // The logo on a light plate, so dark logos stay readable.
        val box = RectF(PAD, PAD, PAD + LOGO_W, PAD + LOGO_H)
        canvas.drawRoundRect(box, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 245, 246, 250) })
        val logo = c.logoUrl?.let { loadLogo(it) }
        if (logo != null) {
            val scale = minOf((LOGO_W - 16) / logo.width, (LOGO_H - 16) / logo.height)
            val w = logo.width * scale
            val h = logo.height * scale
            val dst = RectF(box.centerX() - w / 2, box.centerY() - h / 2, box.centerX() + w / 2, box.centerY() + h / 2)
            canvas.drawBitmap(logo, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        } else {
            val initials = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(0x26, 0x29, 0x30); textSize = 44f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
            }
            val text = c.channelName.split(' ').filter { w -> w.any { it.isLetterOrDigit() } }
                .take(2).joinToString("") { it.take(1) }.uppercase()
            canvas.drawText(text, box.centerX(), box.centerY() + 16f, initials)
        }

        val nameX = box.right + 20f
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xC8, 0xCC, 0xD3); textSize = 26f }
        val name = TextUtils.ellipsize(c.channelName, namePaint, W - nameX - PAD, TextUtils.TruncateAt.END)
        canvas.drawText(name, 0, name.length, nameX, PAD + 34f, namePaint)

        if (c.badge != null) {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 24f; typeface = Typeface.DEFAULT_BOLD }
            val r = RectF(nameX, PAD + 54f, nameX + p.measureText(c.badge) + 24f, PAD + 90f)
            canvas.drawRoundRect(r, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (c.live) Color.rgb(0xE5, 0x39, 0x35) else Color.rgb(0x2F, 0x6F, 0xE4)
            })
            canvas.drawText(c.badge, r.left + 12f, r.bottom - 10f, p)
        }

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 38f; typeface = Typeface.DEFAULT_BOLD }
        val title = c.title ?: context.getString(R.string.tvplayer_no_guide)
        val layout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, (W - 2 * PAD).toInt())
            .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
        canvas.save()
        canvas.translate(PAD, box.bottom + 22f)
        layout.draw(canvas)
        canvas.restore()

        c.timeRange?.let {
            val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xA4, 0xA9, 0xB1); textSize = 24f }
            canvas.drawText(it, PAD, H - 30f, timePaint)
        }
        c.progress?.let { frac ->
            canvas.drawRect(0f, H - 8f, W.toFloat(), H.toFloat(), Paint().apply { color = Color.argb(70, 255, 255, 255) })
            canvas.drawRect(0f, H - 8f, W * frac.coerceIn(0f, 1f), H.toFloat(), Paint().apply { color = Color.rgb(0x2F, 0x6F, 0xE4) })
        }
        return bmp
    }

    private fun loadLogo(url: String): Bitmap? = logoCache.getOrPut(url) {
        runCatching {
            val file = File(logoDir, hash(url))
            val bytes = if (file.exists()) file.readBytes() else download(url)?.also { file.writeBytes(it) }
            bytes?.let { decode(it) }
        }.getOrNull()
    }

    private fun download(url: String): ByteArray? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.instanceFollowRedirects = true
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_LOGO_BYTES) return null
                }
                out.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
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
        const val DIR = "launcher_tiles"
        private const val LOGO_DIR = "launcher_logos"
        private const val W = 640
        private const val H = 360
        private const val PAD = 28f
        private const val LOGO_W = 200f
        private const val LOGO_H = 112f
        private const val MAX_LOGO_BYTES = 2 * 1024 * 1024

        fun hash(s: String): String =
            MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)
    }
}

/** Serves the drawn tiles to the launcher: read only, and only PNGs from the tiles folder. */
class LauncherTileProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        if (!name.matches(NAME)) throw FileNotFoundException(name)
        val file = File(File(context!!.filesDir, LauncherTiles.DIR), name)
        if (!file.exists()) throw FileNotFoundException(name)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "image/png"
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    companion object {
        private val NAME = Regex("[0-9a-f]+_[0-9]+\\.png")
        fun authority(context: Context) = "${context.packageName}.tiles"
    }
}

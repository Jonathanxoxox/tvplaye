package tv.own.owntv.launcher

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
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
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import tv.own.owntv.R
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

/**
 * Draws the 16:9 launcher tiles, one look for every row: the channel logo large at the top, and below
 * it what is on (or coming up), its badge, times and progress. [LauncherTileProvider] hands the files
 * to the launcher.
 */
internal class LauncherTiles(private val context: Context) {
    private val tilesDir = File(context.filesDir, DIR).apply { mkdirs() }
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

    /**
     * The tile's address, or the channel logo when drawing fails. With [reuse], a tile drawn for the
     * same [id] in the last minutes is handed out again instead of drawing it once more.
     */
    suspend fun tile(id: String, content: Content, reuse: Boolean = false): Uri? = runCatching {
        if (reuse) recent(id)?.let { return@runCatching uriOf(it.name) }
        val name = "${hash(id)}_$stamp.png"
        val bitmap = draw(content, content.logoUrl?.let { loadLogo(it) })
        File(tilesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        uriOf(name)
    }.getOrElse { content.logoUrl?.let(Uri::parse) }

    private fun uriOf(name: String): Uri =
        Uri.Builder().scheme("content").authority(LauncherTileProvider.authority(context)).appendPath(name).build()

    /** The newest tile drawn for [id] within [REUSE_MS], if any. */
    private fun recent(id: String): File? {
        val prefix = hash(id) + "_"
        return tilesDir.listFiles { f -> f.name.startsWith(prefix) }
            ?.maxByOrNull { it.name.substringAfter('_').substringBefore('.').toLongOrNull() ?: 0L }
            ?.takeIf { stamp - (it.name.substringAfter('_').substringBefore('.').toLongOrNull() ?: 0L) < REUSE_MS }
    }

    /** Drops tiles of earlier runs, with an hour's slack in case a launcher is still loading one. */
    fun cleanup() {
        val cutoff = stamp - 60 * 60_000L
        tilesDir.listFiles()?.forEach { f ->
            val t = f.name.substringAfter('_').substringBefore('.').toLongOrNull() ?: 0L
            if (t < cutoff) f.delete()
        }
        // Logos used to be downloaded into a folder of their own; they now come from the app's image cache.
        File(context.cacheDir, OLD_LOGO_DIR).takeIf { it.exists() }?.deleteRecursively()
    }

    private fun draw(c: Content, logo: Bitmap?): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), Color.rgb(0x26, 0x29, 0x30), Color.rgb(0x12, 0x13, 0x17), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)

        // The logo large on a light plate, so dark logos stay readable.
        val box = RectF((W - LOGO_W) / 2, PAD, (W + LOGO_W) / 2, PAD + LOGO_H)
        canvas.drawRoundRect(box, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(240, 245, 246, 250) })
        if (logo != null) {
            val scale = minOf((LOGO_W - 2 * LOGO_INSET) / logo.width, (LOGO_H - 2 * LOGO_INSET) / logo.height)
            val w = logo.width * scale
            val h = logo.height * scale
            val dst = RectF(box.centerX() - w / 2, box.centerY() - h / 2, box.centerX() + w / 2, box.centerY() + h / 2)
            canvas.drawBitmap(logo, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        } else {
            // No logo: the channel's name in its place, never a pair of letters.
            val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(0x26, 0x29, 0x30); textSize = 40f; typeface = Typeface.DEFAULT_BOLD
            }
            val width = (LOGO_W - 2 * LOGO_INSET).toInt()
            val layout = StaticLayout.Builder.obtain(c.channelName, 0, c.channelName.length, namePaint, width)
                .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()
            canvas.save()
            canvas.translate(box.left + LOGO_INSET, box.centerY() - layout.height / 2f)
            layout.draw(canvas)
            canvas.restore()
        }

        // What is on, centred under the logo; without a guide the channel's name, dimmed.
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (c.title != null) Color.WHITE else Color.rgb(0xA4, 0xA9, 0xB1); textSize = 32f; typeface = Typeface.DEFAULT_BOLD
        }
        val title = c.title ?: if (logo != null) c.channelName else context.getString(R.string.tvplayer_no_guide)
        val layout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, (W - 2 * PAD).toInt())
            .setMaxLines(if (c.timeRange != null || c.badge != null) 1 else 2)
            .setEllipsize(TextUtils.TruncateAt.END).setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
        canvas.save()
        canvas.translate(PAD, box.bottom + 14f)
        layout.draw(canvas)
        canvas.restore()

        // Badge (LIVE, "In 20 Min.") and times on one centred line.
        val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 22f; typeface = Typeface.DEFAULT_BOLD }
        val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xC8, 0xCC, 0xD3); textSize = 24f }
        val badgeW = c.badge?.let { badgePaint.measureText(it) + 20f } ?: 0f
        val gap = if (c.badge != null && c.timeRange != null) 14f else 0f
        val timeW = c.timeRange?.let { timePaint.measureText(it) } ?: 0f
        var x = (W - badgeW - gap - timeW) / 2
        val baseline = H - 26f
        if (c.badge != null) {
            val r = RectF(x, baseline - 25f, x + badgeW, baseline + 8f)
            canvas.drawRoundRect(r, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (c.live) Color.rgb(0xE5, 0x39, 0x35) else Color.rgb(0x2F, 0x6F, 0xE4)
            })
            canvas.drawText(c.badge, r.left + 10f, baseline, badgePaint)
            x = r.right + gap
        }
        c.timeRange?.let { canvas.drawText(it, x, baseline, timePaint) }

        c.progress?.let { frac ->
            canvas.drawRect(0f, H - 6f, W.toFloat(), H.toFloat(), Paint().apply { color = Color.argb(70, 255, 255, 255) })
            canvas.drawRect(0f, H - 6f, W * frac.coerceIn(0f, 1f), H.toFloat(), Paint().apply { color = Color.rgb(0x2F, 0x6F, 0xE4) })
        }
        return bmp
    }

    /**
     * The logo through the app's image loader: its disk cache already holds the logos the guide has
     * shown, and its client sends the player User-Agent and follows redirects that some logo hosts need.
     */
    private suspend fun loadLogo(url: String): Bitmap? {
        if (logoCache.containsKey(url)) return logoCache[url]
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(LOGO_W.toInt(), LOGO_H.toInt())
            .allowHardware(false)
            .build()
        val bitmap = runCatching {
            val image = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
            // A software canvas cannot draw a hardware bitmap (one the guide left in the memory cache).
            if (image?.config == Bitmap.Config.HARDWARE) image.copy(Bitmap.Config.ARGB_8888, false) else image
        }.getOrNull()
        logoCache[url] = bitmap
        return bitmap
    }

    companion object {
        const val DIR = "launcher_tiles"
        private const val OLD_LOGO_DIR = "launcher_logos"
        private const val W = 640
        private const val H = 360
        private const val PAD = 24f
        private const val LOGO_W = 400f
        private const val LOGO_H = 225f
        private const val LOGO_INSET = 18f
        /** Well inside [cleanup]'s hour, so a reused tile is never deleted while a card shows it. */
        private const val REUSE_MS = 20 * 60_000L

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

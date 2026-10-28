package tv.own.owntv.features.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import tv.own.owntv.BuildConfig
import tv.own.owntv.R
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * ElevenLabs Scribe: the recorded sentence goes to ElevenLabs and comes back as text, the way
 * TizenTube's speech goes to YouTube. Much better at German and at names than the small model on the
 * box, which then only decides when the sentence is over (and stands in without a key or network).
 * The names it should expect (teams, channels) are in res/raw/voice_keyterms.json.
 * Blank key (GitHub secret ELEVENLABS_API_KEY) = off.
 */
class ScribeClient(private val context: Context, okHttp: OkHttpClient) {

    private val http = okHttp.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
    private val apiKey = BuildConfig.ELEVENLABS_API_KEY

    val available: Boolean get() = apiKey.isNotBlank()

    private val keyterms: List<String> by lazy {
        runCatching {
            val list = JSONObject(context.resources.openRawResource(R.raw.voice_keyterms).bufferedReader().use { it.readText() })
                .getJSONArray(KEY_KEYTERMS)
            (0 until list.length()).map { list.getString(it) }
        }.getOrDefault(emptyList())
    }

    /** [pcm]: 16 kHz mono 16-bit little-endian. Null when the call failed (logged). */
    suspend fun transcribe(pcm: ByteArray): String? = withContext(Dispatchers.IO) {
        if (!available || pcm.size < MIN_BYTES) return@withContext null
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart(F_MODEL, MODEL)
            .addFormDataPart(F_FORMAT, PCM_FORMAT)
            .addFormDataPart(F_LANGUAGE, language())
            .addFormDataPart(F_EVENTS, false.toString())
            .addFormDataPart(F_TIMESTAMPS, NO_TIMESTAMPS)
            .addFormDataPart(F_FILE, AUDIO_NAME, pcm.toRequestBody(OCTETS.toMediaType()))
        keyterms.forEach { form.addFormDataPart(F_KEYTERMS, it) }
        val request = Request.Builder().url(URL).header(H_KEY, apiKey).post(form.build()).build()
        runCatching {
            http.newCall(request).execute().use { r ->
                val body = r.body.string()
                if (!r.isSuccessful) { Log.w(TAG, "HTTP ${r.code}: ${body.take(300)}"); return@use null }
                JSONObject(body).optString(F_TEXT).trim().trimEnd('.', '!', '?').takeIf { it.isNotEmpty() }
            }
        }.onFailure { Log.w(TAG, "request failed: ${it.message}") }.getOrNull()
    }

    private fun language(): String = if (Locale.getDefault().language == Locale.ENGLISH.language) Locale.ENGLISH.language else Locale.GERMAN.language

    private companion object {
        const val TAG = "Scribe"
        const val URL = "https://api.elevenlabs.io/v1/speech-to-text"
        const val MODEL = "scribe_v2"
        const val PCM_FORMAT = "pcm_s16le_16"
        const val NO_TIMESTAMPS = "none"
        const val OCTETS = "application/octet-stream"
        const val AUDIO_NAME = "search.pcm"
        const val H_KEY = "xi-api-key"
        const val F_MODEL = "model_id"
        const val F_FORMAT = "file_format"
        const val F_LANGUAGE = "language_code"
        const val F_EVENTS = "tag_audio_events"
        const val F_TIMESTAMPS = "timestamps_granularity"
        const val F_FILE = "file"
        const val F_KEYTERMS = "keyterms"
        const val F_TEXT = "text"
        const val KEY_KEYTERMS = "keyterms"
        /** A quarter second: anything shorter is a click, not a search. */
        const val MIN_BYTES = 16_000 / 2
    }
}

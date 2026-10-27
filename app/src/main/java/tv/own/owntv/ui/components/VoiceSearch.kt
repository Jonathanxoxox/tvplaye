package tv.own.owntv.ui.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import tv.own.owntv.R
import java.util.Locale

/**
 * The microphone button beside a search field, for remotes whose keyboard has its own mic switched off.
 * First the box's voice-search screen (RecognizerIntent: Google TV's, the remote's mic); a box without one
 * runs the speech recogniser here, asking for the microphone the first time. [note] is what the field
 * says meanwhile ("Speak now…", or why nothing happened), null when it shows its own placeholder.
 */
@Stable
class VoiceSearch internal constructor() {
    var listening by mutableStateOf(false)
        internal set
    @get:StringRes
    var note by mutableStateOf<Int?>(null)
        internal set
    internal var launch: () -> Unit = {}
    fun start() = launch()
}

@Composable
fun rememberVoiceSearch(onResult: (String) -> Unit): VoiceSearch {
    val context = LocalContext.current
    val result by rememberUpdatedState(onResult)
    val voice = remember { VoiceSearch() }
    val recognizer = remember { mutableStateOf<SpeechRecognizer?>(null) }
    DisposableEffect(Unit) { onDispose { recognizer.value?.destroy() } }
    // A "why not" note goes after a few seconds; "Speak now…" stays while it listens.
    LaunchedEffect(voice.note, voice.listening) {
        if (voice.note != null && !voice.listening) { kotlinx.coroutines.delay(4_000); voice.note = null }
    }

    fun deliver(texts: List<String>?) {
        texts?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { result(it) }
    }

    fun listenHere() {
        val sr = recognizer.value ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer.value = it }
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                voice.listening = false
                voice.note = null
                deliver(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
            }
            override fun onError(error: Int) {
                voice.listening = false
                voice.note = R.string.voice_search_not_understood
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        voice.listening = true
        voice.note = R.string.voice_search_listening
        sr.startListening(recognizeIntent())
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listenHere() else { voice.note = R.string.voice_search_no_microphone }
    }
    val screen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) deliver(r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS))
    }

    voice.launch = launch@{
        if (voice.listening) {
            recognizer.value?.stopListening()
            return@launch
        }
        voice.note = null
        // No voice-search screen on the box (ActivityNotFoundException): listen here instead.
        val opened = runCatching {
            screen.launch(recognizeIntent().putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.voice_search_listening)))
        }.isSuccess
        if (opened) return@launch
        when {
            !SpeechRecognizer.isRecognitionAvailable(context) -> { voice.note = R.string.voice_search_unavailable }
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> listenHere()
            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    return voice
}

private fun recognizeIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

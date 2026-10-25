package io.github.jonathanxoxox.tvkanaele

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.tvprovider.media.tv.TvContractCompat
import io.github.jonathanxoxox.tvkanaele.core.Suggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/** Einstellungen: Playlist, EPG, Interessen, Player; dazu eine Vorschau der aktuellen Vorschläge. */
class MainActivity : Activity() {
    private val scope = MainScope()
    private lateinit var settings: Settings

    private lateinit var status: TextView
    private lateinit var message: TextView
    private lateinit var lastSync: TextView
    private lateinit var preview: LinearLayout
    private val pendingBrowsable = ArrayDeque<Long>()

    private val fields = mutableListOf<Pair<EditText, (String) -> Unit>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = Settings(this)

        status = findViewById(R.id.status)
        message = findViewById(R.id.message)
        lastSync = findViewById(R.id.last_sync)
        preview = findViewById(R.id.preview)

        val sourceGroup = findViewById<RadioGroup>(R.id.source)
        sourceGroup.check(if (settings.source == Settings.Source.XTREAM) R.id.source_xtream else R.id.source_m3u)
        updateSourceFields(settings.source)
        sourceGroup.setOnCheckedChangeListener { _, id ->
            val source = if (id == R.id.source_xtream) Settings.Source.XTREAM else Settings.Source.M3U
            updateSourceFields(source)
        }

        bind(R.id.m3u_url, settings.m3uUrl) { settings.m3uUrl = it }
        bind(R.id.xtream_server, settings.xtreamServer) { settings.xtreamServer = it }
        bind(R.id.xtream_user, settings.xtreamUser) { settings.xtreamUser = it }
        bind(R.id.xtream_password, settings.xtreamPassword) { settings.xtreamPassword = it }
        bind(R.id.epg_url, settings.epgUrl) { settings.epgUrl = it }
        bind(R.id.user_agent, settings.userAgent) { settings.userAgent = it }
        bind(R.id.teams, settings.teams) { settings.teams = it }
        bind(R.id.leagues, settings.leagues) { settings.leagues = it }
        bind(R.id.keywords, settings.keywords) { settings.keywords = it }
        bind(R.id.excludes, settings.excludes) { settings.excludes = it }
        bind(R.id.preferred, settings.preferredChannels) { settings.preferredChannels = it }
        bind(R.id.lookahead, settings.lookaheadHours.toString()) { settings.lookaheadHours = it.toIntOrNull() ?: 3 }

        val allFootball = findViewById<CheckBox>(R.id.cb_all_football).apply { isChecked = settings.includeAllFootball }
        val drawTiles = findViewById<CheckBox>(R.id.cb_draw_tiles).apply { isChecked = settings.drawTiles }
        val watchNext = findViewById<CheckBox>(R.id.cb_watch_next).apply { isChecked = settings.watchNextEnabled }

        val playerGroup = findViewById<RadioGroup>(R.id.player)
        playerGroup.check(
            when (settings.player) {
                Settings.Player.TIVIMATE_LINK -> R.id.player_tivimate_link
                Settings.Player.TIVIMATE_APP -> R.id.player_tivimate_app
                Settings.Player.BUILTIN -> R.id.player_builtin
                Settings.Player.EXTERNAL -> R.id.player_external
            }
        )

        findViewById<Button>(R.id.save).setOnClickListener {
            val sourceBefore = sourceKey()
            settings.source = if (sourceGroup.checkedRadioButtonId == R.id.source_xtream) Settings.Source.XTREAM else Settings.Source.M3U
            fields.forEach { (field, apply) -> apply(field.text.toString()) }
            settings.includeAllFootball = allFootball.isChecked
            settings.drawTiles = drawTiles.isChecked
            settings.watchNextEnabled = watchNext.isChecked
            settings.player = when (playerGroup.checkedRadioButtonId) {
                R.id.player_tivimate_app -> Settings.Player.TIVIMATE_APP
                R.id.player_builtin -> Settings.Player.BUILTIN
                R.id.player_external -> Settings.Player.EXTERNAL
                else -> Settings.Player.TIVIMATE_LINK
            }
            settings.needsReparse = true
            refreshNow(force = sourceKey() != sourceBefore)
        }
        findViewById<Button>(R.id.reload).setOnClickListener { refreshNow(force = true) }
        findViewById<Button>(R.id.pick_favorites).setOnClickListener {
            startActivity(Intent(this, ChannelPickerActivity::class.java))
        }
        findViewById<Button>(R.id.show_channels).setOnClickListener { requestAllChannelsBrowsable() }
        findViewById<Button>(R.id.open_tivimate).setOnClickListener {
            if (!Launcher.openTiviMate(this)) message.text = getString(R.string.tivimate_missing)
        }

        SyncWorker.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        loadPreview()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun sourceKey() = listOf(settings.source.name, settings.m3uUrl, settings.xtreamServer, settings.xtreamUser,
        settings.xtreamPassword, settings.epgUrl, settings.userAgent).joinToString("|")

    private fun bind(id: Int, initial: String, apply: (String) -> Unit) {
        val field = findViewById<EditText>(id)
        field.setText(initial)
        fields += field to apply
    }

    private fun updateSourceFields(source: Settings.Source) {
        findViewById<View>(R.id.m3u_fields).visibility = if (source == Settings.Source.M3U) View.VISIBLE else View.GONE
        findViewById<View>(R.id.xtream_fields).visibility = if (source == Settings.Source.XTREAM) View.VISIBLE else View.GONE
    }

    private fun updateStatus() {
        val lines = mutableListOf<String>()
        lines += when {
            !Launcher.tiviMateInstalled(this) -> getString(R.string.tivimate_missing)
            Launcher.tiviMateAcceptsLinks(this) -> getString(R.string.tivimate_links_ok)
            else -> getString(R.string.tivimate_links_no)
        }
        if (!settings.isConfigured) lines += getString(R.string.not_configured)
        status.text = lines.joinToString("\n")
        lastSync.text = settings.lastSyncMessage?.let { getString(R.string.last_sync, it) } ?: ""
    }

    private fun refreshNow(force: Boolean) {
        message.setText(if (force) R.string.syncing_full else R.string.syncing)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    Sync.run(applicationContext, force)
                } catch (e: Exception) {
                    "Fehler – ${e.message}"
                }
            }
            settings.lastSyncMessage = "${SyncWorker.now()}: $result"
            message.text = ""
            updateStatus()
            loadPreview()
        }
    }

    /** Zeigt die Vorschläge auch hier, so lässt sich prüfen, ob die Interessen greifen. */
    private fun loadPreview() {
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                runCatching { Sync.suggestions(settings, Sync.store(applicationContext).load()) }.getOrDefault(emptyList())
            }
            preview.removeAllViews()
            if (list.isEmpty()) {
                preview.addView(TextView(this@MainActivity).apply {
                    setText(R.string.preview_empty)
                    setTextColor(0xFFAAAAAA.toInt())
                })
            }
            list.forEach { preview.addView(previewButton(it)) }
        }
    }

    private fun previewButton(s: Suggestion): Button {
        val now = System.currentTimeMillis()
        val p = s.programme
        val time = if (p.isRunning(now)) "LIVE" else TIME.format(p.start)
        return Button(this).apply {
            isAllCaps = false
            text = "$time  ${p.title}\n${s.channel.name} · ${s.reasons.joinToString(", ")} · ${s.score} Punkte"
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setOnClickListener { Launcher.play(this@MainActivity, s.channel) }
        }
    }

    /** Fragt beim System nach, die Kanäle dieser App auf dem Startbildschirm anzuzeigen. */
    private fun requestAllChannelsBrowsable() {
        scope.launch {
            val ids = withContext(Dispatchers.IO) { ChannelPublisher(applicationContext).channelIds().values }
            if (ids.isEmpty()) {
                message.setText(R.string.no_channels_yet)
                return@launch
            }
            pendingBrowsable.clear()
            pendingBrowsable.addAll(ids)
            requestNextBrowsable()
        }
    }

    private fun requestNextBrowsable() {
        val id = pendingBrowsable.removeFirstOrNull() ?: return
        val intent = Intent(TvContractCompat.ACTION_REQUEST_CHANNEL_BROWSABLE)
            .putExtra(TvContractCompat.EXTRA_CHANNEL_ID, id)
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_BROWSABLE)
        } catch (e: ActivityNotFoundException) {
            // Launcher ohne diese Abfrage (z. B. Projectivity): Kanäle dort in den Einstellungen hinzufügen.
            message.setText(R.string.browsable_unsupported)
            pendingBrowsable.clear()
        }
    }

    @Deprecated("Activity-API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_BROWSABLE) requestNextBrowsable()
    }

    companion object {
        private const val REQUEST_BROWSABLE = 1
        private val TIME = SimpleDateFormat("HH:mm", Locale.GERMANY)
    }
}

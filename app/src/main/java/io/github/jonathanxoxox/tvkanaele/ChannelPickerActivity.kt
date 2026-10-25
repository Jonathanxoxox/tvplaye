package io.github.jonathanxoxox.tvkanaele

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import io.github.jonathanxoxox.tvkanaele.core.IptvChannel
import io.github.jonathanxoxox.tvkanaele.core.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Auswahl der Lieblingssender. Gewählte stehen oben; die Reihenfolge der Auswahl bleibt erhalten. */
class ChannelPickerActivity : Activity() {
    private val scope = MainScope()
    private lateinit var settings: Settings
    private lateinit var list: ListView
    private lateinit var count: TextView

    private var all: List<IptvChannel> = emptyList()
    private var shown: List<IptvChannel> = emptyList()
    private val selected = mutableListOf<String>()
    private var changed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_picker)
        settings = Settings(this)
        selected += settings.favorites
        list = findViewById(R.id.list)
        count = findViewById(R.id.count)
        list.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        findViewById<EditText>(R.id.filter).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) = show(s?.toString().orEmpty())
        })

        list.setOnItemClickListener { _, _, position, _ ->
            val key = shown[position].key
            if (list.isItemChecked(position)) {
                if (key !in selected) selected += key
            } else {
                selected -= key
            }
            changed = true
            updateCount()
        }

        scope.launch {
            all = withContext(Dispatchers.IO) { Sync.store(applicationContext).loadChannels() }
            if (all.isEmpty()) count.setText(R.string.picker_empty)
            show("")
        }
    }

    private fun show(filter: String) {
        val words = Text.fold(filter).split(' ').filter { it.isNotEmpty() }
        val byKey = all.associateBy { it.key }
        val chosen = selected.mapNotNull { byKey[it] }
        val rest = all.filter { it.key !in selected }
        shown = (chosen + rest).filter { c ->
            val text = Text.fold("${c.group} ${c.name}")
            words.all { text.contains(it) }
        }.take(MAX_SHOWN)
        list.adapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_multiple_choice,
            shown.map { if (it.group.isNotEmpty()) "${it.name}   ·   ${it.group}" else it.name },
        )
        shown.forEachIndexed { i, c -> list.setItemChecked(i, c.key in selected) }
        updateCount()
    }

    private fun updateCount() {
        if (all.isNotEmpty()) count.text = getString(R.string.picker_count, selected.size, all.size)
    }

    override fun onPause() {
        super.onPause()
        if (changed) {
            settings.favorites = selected.toList()
            settings.needsReparse = true
            SyncWorker.runNow(this)
            changed = false
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val MAX_SHOWN = 500
    }
}

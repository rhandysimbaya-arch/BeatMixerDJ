package com.beatmixer.dj

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

class MixerViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app.applicationContext
    private val prefs = ctx.getSharedPreferences("beatmixer", Context.MODE_PRIVATE)

    val engine = MixEngine()

    // ---------- UI state
    var screen by mutableStateOf(Screen.MIX)
    val deckUi = mutableStateListOf(DeckUi(), DeckUi())
    val tempo = mutableStateListOf(0f, 0f)
    val filter = mutableStateListOf(0f, 0f)
    val echo = mutableStateListOf(false, false)
    val flanger = mutableStateListOf(false, false)
    val phaser = mutableStateListOf(false, false)
    val loopSize = mutableStateListOf(4f, 4f)
    val playlists = mutableStateListOf<Playlist>()
    var playlistSel by mutableStateOf(-1)
    var miniWide by mutableStateOf(false)
    var miniPortrait by mutableStateOf(true)

    fun updateMini(wide: Boolean, v: Boolean) {
        if (wide) miniWide = v else miniPortrait = v
        prefs.edit().putBoolean(if (wide) "miniWide" else "miniPortrait", v).apply()
    }
    val eq = mutableStateListOf(0f, 0f, 0f, 0f, 0f, 0f) // deck * 3 + band (low, mid, high), dB
    val hotCues = listOf(
        mutableStateListOf<Float?>(null, null, null, null),
        mutableStateListOf<Float?>(null, null, null, null),
    )
    val loopOn = mutableStateListOf(false, false)

    var crossfader by mutableFloatStateOf(0.5f)
        private set
    var master by mutableFloatStateOf(0.85f)
        private set
    var muted by mutableStateOf(false)
        private set
    var micOn by mutableStateOf(false)
        private set
    var mcTalk by mutableStateOf(false)
        private set
    var autoMix by mutableStateOf(false)
        private set
    var recording by mutableStateOf(false)
        private set
    var recSeconds by mutableFloatStateOf(0f)
        private set
    var nowPlaying by mutableStateOf("Nothing playing")
    var nowDeck by mutableStateOf(-1)
    var themeName by mutableStateOf("PIONEER")
        private set
    var outputKind by mutableStateOf("SPEAKER")
        private set
    var djName by mutableStateOf("DJ DJ")
        private set
    var crossfadeSec by mutableFloatStateOf(12f)
        private set
    var beatMatch by mutableStateOf(true)
        private set
    var micGain by mutableFloatStateOf(1f)
        private set
    var duckAmt by mutableFloatStateOf(0.3f)
        private set

    var folderMode by mutableStateOf(false)
        private set
    var folderName by mutableStateOf("")
        private set
    var scanning by mutableStateOf(false)
        private set

    val library = mutableStateListOf<Song>()
    val queue = mutableStateListOf<Song>()
    val recFiles = mutableStateListOf<File>()
    val drops = mutableStateListOf<DropItem>().apply {
        addAll(DropSynth.all.mapIndexed { i, d -> DropItem(i, d.name).also { it.category = BUILTIN_DROP_CATEGORY.getOrElse(i) { "FX" } } })
    }
    val categories = mutableStateListOf<String>()

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    // ---------- internals
    private var folderUri: Uri? = null
    private val folderSongs = ArrayList<Song>()
    private val mediaSongs = ArrayList<Song>()
    private val extras = ArrayList<Song>()
    private val preview = BooleanArray(2)
    private val stale = BooleanArray(2)
    private var transitionJob: Job? = null
    private var forceNext = false
    private var mcWasMicOn = false

    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val deviceCb = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            updateOutput()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            updateOutput()
        }
    }

    init {
        engine.start()
        loadPrefs()
        am.registerAudioDeviceCallback(deviceCb, null)
        updateOutput()
        refreshLibrary()
        refreshRecordings()
        initDrops()
        initSounds()
        viewModelScope.launch {
            while (true) {
                tick()
                delay(100)
            }
        }
    }

    override fun onCleared() {
        try {
            am.unregisterAudioDeviceCallback(deviceCb)
        } catch (_: Exception) {
        }
        engine.recorder?.let { r ->
            engine.recorder = null
            Thread { r.stop() }.start()
        }
        for (dk in engine.decks) dk.track?.cancelled = true
        engine.release()
        super.onCleared()
    }

    fun toast(s: String) {
        messages.tryEmit(s)
    }

    // ---------- prefs
    fun updateTheme(name: String) {
        val t = THEMES.firstOrNull { it.name == name } ?: return
        themeName = t.name
        C.apply(t)
        prefs.edit().putString("theme", t.name).apply()
    }

    private fun loadPrefs() {
        miniWide = prefs.getBoolean("miniWide", false)
        miniPortrait = prefs.getBoolean("miniPortrait", true)
        loadPlaylists()
        updateTheme(prefs.getString("theme", "PIONEER") ?: "PIONEER")
        djName = prefs.getString("dj", "DJ DJ") ?: "DJ DJ"
        master = prefs.getFloat("master", 0.85f)
        engine.master = master
        crossfadeSec = prefs.getFloat("xfade", 12f)
        micGain = prefs.getFloat("micGain", 1f)
        engine.micGain = micGain
        duckAmt = prefs.getFloat("duck", 0.3f)
        engine.duck = duckAmt
        beatMatch = prefs.getBoolean("beatmatch", true)
        val fu = prefs.getString("folderUri", null)
        if (fu != null) {
            val u = Uri.parse(fu)
            folderUri = u
            folderName = folderLabel(u)
            folderMode = prefs.getBoolean("folderMode", false)
        }
        val s = prefs.getString("extras", null)
        if (s != null) {
            try {
                val arr = JSONArray(s)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    extras.add(Song(Uri.parse(o.getString("u")), o.getString("t"), o.getString("a"), o.getLong("d")))
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun saveExtras() {
        val arr = JSONArray()
        for (s in extras) {
            arr.put(
                JSONObject()
                    .put("u", s.uri.toString())
                    .put("t", s.title)
                    .put("a", s.artist)
                    .put("d", s.durationMs),
            )
        }
        prefs.edit().putString("extras", arr.toString()).apply()
    }

    fun updateDjName(n: String) {
        djName = n.trim().ifEmpty { "DJ DJ" }
        prefs.edit().putString("dj", djName).apply()
    }

    // ---------- drops
    private fun drop(id: Int): DropItem? = drops.firstOrNull { it.index == id }

    private fun saveCategories() {
        prefs.edit().putString("dropCats", categories.joinToString("\n")).apply()
    }

    fun addCategory(name: String) {
        val n = name.trim().uppercase()
        if (n.isEmpty() || n == "OTHER" || categories.any { it == n }) return
        categories.add(n)
        saveCategories()
    }

    fun removeCategory(name: String) {
        categories.remove(name)
        saveCategories()
    }

    fun setDropCategory(id: Int, cat: String) {
        val d = drop(id) ?: return
        d.category = cat
        prefs.edit().putString("dropCat_$id", cat).apply()
    }

    private fun initDrops() {
        val cs = prefs.getString("dropCats", null)
        categories.clear()
        if (cs == null) categories.addAll(DEFAULT_DROP_CATEGORIES) else categories.addAll(cs.split("\n").filter { it.isNotBlank() })
        for (d in drops.toList()) {
            val sc = prefs.getString("dropCat_" + d.index, null)
            if (sc != null) d.category = sc
        }
        val ids = prefs.getString("userDropIds", null)?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
        for (id in ids) {
            val it = DropItem(id, prefs.getString("udName_$id", "DROP") ?: "DROP", false)
            it.name = it.defaultName
            it.category = prefs.getString("dropCat_$id", "") ?: ""
            it.custom = true
            drops.add(it)
        }
        viewModelScope.launch {
            val defs = DropSynth.all
            val samples = withContext(Dispatchers.Default) { defs.map { it.gen() } }
            val favStr = prefs.getString("favs", null)
            val favSet = favStr?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet()
            for (item in drops.toList()) {
                if (item.builtin) item.sample = samples.getOrNull(item.index)
                if (favSet != null) item.fav = item.index in favSet
                val nm = prefs.getString("dropName_" + item.index, null)
                if (nm != null) item.name = nm
            }
            for (item in drops.toList()) {
                val u = (if (item.builtin) prefs.getString("dropUri_" + item.index, null) else prefs.getString("udUri_" + item.index, null)) ?: continue
                val custom = withContext(Dispatchers.Default) { decodeDrop(Uri.parse(u)) }
                if (custom != null) {
                    item.sample = custom
                    item.custom = true
                }
            }
        }
    }

    private fun decodeDrop(uri: Uri): ShortArray? {
        val st = Decoder.decodeStereo(ctx, uri, 8) ?: return null
        val frames = st.size / 2
        val mono = FloatArray(frames)
        var peak = 1e-6f
        for (i in 0 until frames) {
            val v = (st[i * 2] + st[i * 2 + 1]) * 0.5f
            mono[i] = v
            if (abs(v) > peak) peak = abs(v)
        }
        val g = 0.85f * 32767f / peak
        return ShortArray(frames) { (mono[it] * g).toInt().coerceIn(-32767, 32767).toShort() }
    }

    fun fireDrop(idx: Int) {
        val s = drop(idx)?.sample ?: return
        engine.triggerDrop(s, 1f)
    }

    fun toggleFav(idx: Int) {
        val d = drop(idx) ?: return
        d.fav = !d.fav
        prefs.edit().putString("favs", drops.filter { it.fav }.joinToString(",") { it.index.toString() }).apply()
    }

    fun renameDrop(idx: Int, name: String) {
        val d = drop(idx) ?: return
        val n = name.trim().ifEmpty { d.defaultName }
        d.name = n
        prefs.edit().putString(if (d.builtin) "dropName_$idx" else "udName_$idx", n).apply()
    }

    fun setCustomDrop(idx: Int, uri: Uri) {
        viewModelScope.launch {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            val s = withContext(Dispatchers.Default) { decodeDrop(uri) }
            val d = drop(idx)
            if (s == null || d == null) {
                toast("Could not read that audio file")
            } else {
                d.sample = s
                d.custom = true
                prefs.edit().putString(if (d.builtin) "dropUri_$idx" else "udUri_$idx", uri.toString()).apply()
                toast("Drop replaced (max 8 seconds used)")
            }
        }
    }

    fun addDrop(name: String, category: String, uri: Uri) {
        viewModelScope.launch {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            val s = withContext(Dispatchers.Default) { decodeDrop(uri) }
            if (s == null) {
                toast("Could not read that audio file")
                return@launch
            }
            val id = prefs.getInt("nextDropId", 1000)
            val item = DropItem(id, name.trim().ifEmpty { "DROP" }, false)
            item.name = item.defaultName
            item.category = category
            item.custom = true
            item.sample = s
            drops.add(item)
            val ids = (prefs.getString("userDropIds", null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()) + id.toString()
            prefs.edit()
                .putInt("nextDropId", id + 1)
                .putString("userDropIds", ids.joinToString(","))
                .putString("udName_$id", item.name)
                .putString("udUri_$id", uri.toString())
                .putString("dropCat_$id", category)
                .apply()
            toast("Drop added (max 8 seconds used)")
        }
    }

    fun deleteDrop(idx: Int) {
        val d = drop(idx) ?: return
        if (d.builtin) return
        drops.remove(d)
        val ids = (prefs.getString("userDropIds", null)?.split(",")?.filter { it.isNotBlank() && it != idx.toString() } ?: emptyList())
        prefs.edit().putString("userDropIds", ids.joinToString(",")).remove("udName_$idx").remove("udUri_$idx").remove("dropCat_$idx").apply()
    }

    fun resetDrop(idx: Int) {
        val d = drop(idx) ?: return
        if (!d.builtin) return
        viewModelScope.launch {
            d.sample = withContext(Dispatchers.Default) { DropSynth.all[idx].gen() }
            d.custom = false
            d.name = d.defaultName
            prefs.edit().remove("dropUri_$idx").remove("dropName_$idx").apply()
        }
    }

    private val noteSamples = arrayOfNulls<ShortArray>(88)
    private val drumSamples = arrayOfNulls<ShortArray>(DropSynth.drums.size)

    private fun midiFreq(m: Int): Float = 440f * 2f.pow((m - 69) / 12f)

    private fun initSounds() {
        viewModelScope.launch(Dispatchers.Default) {
            DropSynth.drums.forEachIndexed { i, d -> drumSamples[i] = d.gen() }
            for (m in 21..108) noteSamples[m - 21] = DropSynth.note(midiFreq(m))
        }
    }

    fun playNote(midi: Int) {
        val i = midi - 21
        if (i !in 0..87) return
        val s = noteSamples[i] ?: DropSynth.note(midiFreq(midi)).also { noteSamples[i] = it }
        engine.triggerDrop(s, 0.9f)
    }

    fun playDrum(i: Int) {
        if (i !in drumSamples.indices) return
        val s = drumSamples[i] ?: DropSynth.drums[i].gen().also { drumSamples[i] = it }
        engine.triggerDrop(s, 1f)
    }

    // ---------- library
    fun refreshLibrary() {
        viewModelScope.launch {
            scanning = true
            val fu = folderUri
            if (folderMode && fu != null) {
                val list = withContext(Dispatchers.IO) { scanFolder(fu) }
                folderSongs.clear()
                folderSongs.addAll(list)
                if (list.isEmpty()) toast("No songs found in this folder (or access was lost)")
            } else {
                val list = withContext(Dispatchers.IO) { queryMediaStore() }
                mediaSongs.clear()
                mediaSongs.addAll(list)
            }
            rebuildLibrary()
            scanning = false
        }
    }

    private fun rebuildLibrary() {
        val all = ArrayList<Song>()
        if (folderMode) {
            all.addAll(folderSongs)
        } else {
            all.addAll(mediaSongs)
            for (e in extras) if (all.none { it.uri == e.uri }) all.add(e)
        }
        all.sortBy { it.title.lowercase(Locale.getDefault()) }
        library.clear()
        library.addAll(all)
    }

    private fun folderLabel(tree: Uri): String = try {
        DocumentsContract.getTreeDocumentId(tree).substringAfterLast('/').substringAfter(':').ifEmpty { "Storage" }
    } catch (e: Exception) {
        "Folder"
    }

    /** Use only the songs inside [tree] (including sub-folders) as the library. */
    fun applyFolder(tree: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        folderUri = tree
        folderName = folderLabel(tree)
        folderMode = true
        prefs.edit().putString("folderUri", tree.toString()).putBoolean("folderMode", true).apply()
        refreshLibrary()
    }

    fun showFolder() {
        if (folderUri == null) return
        folderMode = true
        prefs.edit().putBoolean("folderMode", true).apply()
        refreshLibrary()
    }

    fun showAllMusic() {
        folderMode = false
        prefs.edit().putBoolean("folderMode", false).apply()
        refreshLibrary()
    }

    private fun isAudioFile(name: String, mime: String): Boolean {
        if (mime.startsWith("audio/")) return true
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        return ext in setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr", "mid")
    }

    private fun scanFolder(tree: Uri): List<Song> {
        val out = ArrayList<Song>()
        val rootId = try {
            DocumentsContract.getTreeDocumentId(tree)
        } catch (e: Exception) {
            return out
        }
        fun walk(docId: String, depth: Int) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            try {
                ctx.contentResolver.query(
                    children,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                    ),
                    null, null, null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        val mime = c.getString(2) ?: ""
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (depth < 5) walk(id, depth + 1)
                        } else if (isAudioFile(name, mime)) {
                            val base = name.substringBeforeLast('.')
                            val title: String
                            val artist: String
                            if (base.contains(" - ")) {
                                artist = base.substringBefore(" - ").trim()
                                title = base.substringAfter(" - ").trim()
                            } else {
                                artist = "Unknown artist"
                                title = base
                            }
                            out.add(Song(DocumentsContract.buildDocumentUriUsingTree(tree, id), title, artist, 0L))
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        walk(rootId, 0)
        return out
    }

    private fun queryMediaStore(): List<Song> {
        val out = ArrayList<Song>()
        try {
            val proj = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
            )
            val sel = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 30000"
            ctx.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, sel, null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { c ->
                val idI = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val tI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val aI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val dI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (c.moveToNext()) {
                    val id = c.getLong(idI)
                    val artist = c.getString(aI)
                    out.add(
                        Song(
                            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                            c.getString(tI) ?: "Unknown",
                            if (artist == null || artist == "<unknown>") "Unknown artist" else artist,
                            c.getLong(dI),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun displayName(u: Uri): String? = try {
        ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    private fun songFromUri(u: Uri): Song {
        try {
            ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        val meta = Decoder.readMeta(ctx, u)
        val t = meta.first
        val name: String = if (!t.isNullOrBlank()) t else (displayName(u)?.substringBeforeLast('.') ?: "Track")
        return Song(u, name, meta.second ?: "Unknown artist", 0L)
    }

    fun addFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) { uris.map { songFromUri(it) } }
            for (s in added) if (extras.none { it.uri == s.uri }) extras.add(s)
            saveExtras()
            rebuildLibrary()
            toast("Added ${added.size} song(s)")
        }
    }

    fun loadFromUri(deck: Int, uri: Uri) {
        viewModelScope.launch {
            val s = withContext(Dispatchers.IO) { songFromUri(uri) }
            loadSong(deck, s)
        }
    }

    fun enqueue(s: Song) {
        queue.add(s)
        toast("Added to queue")
    }

    // ---------- recordings
    private fun recDir(): File {
        val base = ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: ctx.filesDir
        return File(base, "recordings").also { it.mkdirs() }
    }

    fun refreshRecordings() {
        val files = recDir().listFiles { f -> f.name.endsWith(".wav") }?.sortedByDescending { it.lastModified() } ?: emptyList()
        recFiles.clear()
        recFiles.addAll(files)
    }

    fun deleteRecording(f: File) {
        f.delete()
        refreshRecordings()
    }

    fun loadRecording(deck: Int, f: File) {
        loadSong(deck, Song(Uri.fromFile(f), f.nameWithoutExtension, "Recording", 0L))
    }

    fun toggleRec() {
        if (recording) {
            val r = engine.recorder
            engine.recorder = null
            recording = false
            viewModelScope.launch {
                withContext(Dispatchers.IO) { r?.stop() }
                refreshRecordings()
                toast("Recording saved in Tools")
            }
        } else {
            val name = "mix_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".wav"
            val r = WavRecorder(File(recDir(), name))
            r.start()
            engine.recorder = r
            recSeconds = 0f
            recording = true
        }
    }

    // ---------- output route
    private fun updateOutput() {
        val devs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        outputKind = when {
            devs.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            } -> "BLUETOOTH"
            devs.any {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_LINE_ANALOG ||
                    it.type == AudioDeviceInfo.TYPE_LINE_DIGITAL ||
                    it.type == AudioDeviceInfo.TYPE_HDMI
            } -> "WIRED"
            else -> "SPEAKER"
        }
    }

    // ---------- deck loading / transport
    fun loadSong(deck: Int, song: Song, thenPlay: Boolean = false) {
        if (deckUi[deck].loading) return
        val d = engine.decks[deck]
        d.playing = false
        d.braking = false
        deckUi[deck] = deckUi[deck].copy(loading = true, title = song.title, artist = "Loading...")
        viewModelScope.launch {
            val t = withContext(Dispatchers.IO) {
                try {
                    Decoder.startTrack(ctx, song) { tr ->
                        if (tr.bpm <= 0f) messages.tryEmit("BPM not detected for ${tr.title}")
                    }
                } catch (e: Throwable) {
                    null
                }
            }
            if (t == null) {
                deckUi[deck] = DeckUi(title = "No track", artist = "Could not load that file")
                toast("Could not load ${song.title}")
                return@launch
            }
            d.track?.cancelled = true
            d.track = null
            d.posFrames = 0.0
            d.cuePoint = 0.0
            d.loopStart = -1.0
            d.loopEnd = -1.0
            d.seekReq = Double.NaN
            setTempo(deck, 0f)
            d.track = t
            stale[deck] = false
            loopOn[deck] = false
            for (i in 0..3) hotCues[deck][i] = null
            refreshDeckUi(deck)
            if (thenPlay) d.playing = true
        }
    }

    private fun refreshDeckUi(i: Int) {
        val d = engine.decks[i]
        val t = d.track
        val old = deckUi[i]
        if (old.loading && t == null) return
        val new = if (t == null) {
            DeckUi()
        } else {
            val rem = max(0f, ((t.frames - d.posFrames) / (SR * d.rate)).toFloat())
            DeckUi(
                title = t.title,
                artist = t.artist,
                bpm = (t.bpm * d.rate.toFloat() * 10f).roundToInt() / 10f,
                remaining = rem.toInt().toFloat(),
                playing = d.playing,
                loaded = true,
                loading = false,
                uri = t.uri.toString(),
            )
        }
        if (new != old) deckUi[i] = new
    }

    fun brake(i: Int) {
        val d = engine.decks[i]
        if (d.track == null || !d.playing) return
        d.brakeMul = 1f
        d.braking = true
    }

    fun togglePlay(i: Int) {
        val d = engine.decks[i]
        d.braking = false
        d.brakeMul = 1f
        val t = d.track
        if (t == null) {
            toast("Load a song on deck ${'A' + i} first")
            return
        }
        if (!d.playing && d.posFrames >= t.frames - 4) d.seek(d.cuePoint)
        d.playing = !d.playing
        refreshDeckUi(i)
    }

    fun cueDown(i: Int) {
        val d = engine.decks[i]
        if (d.track == null) return
        if (d.playing) {
            d.playing = false
            d.seek(d.cuePoint)
            preview[i] = false
        } else {
            if (abs(d.posFrames - d.cuePoint) > SR / 50) d.cuePoint = d.posFrames
            d.playing = true
            preview[i] = true
        }
        refreshDeckUi(i)
    }

    fun cueUp(i: Int) {
        if (!preview[i]) return
        preview[i] = false
        val d = engine.decks[i]
        d.playing = false
        d.seek(d.cuePoint)
        refreshDeckUi(i)
    }

    fun seekFraction(i: Int, f: Float) {
        val d = engine.decks[i]
        val t = d.track ?: return
        d.seek(f.coerceIn(0f, 1f) * (t.frames - 2.0))
    }

    fun setTempo(i: Int, pct: Float) {
        val v = pct.coerceIn(-TEMPO_RANGE, TEMPO_RANGE)
        tempo[i] = v
        engine.decks[i].tempoPct = v
    }

    private fun tempoToMatch(trackBpm: Float, targetBpm: Float): Float {
        var best = Float.MAX_VALUE
        var bestPct = 0f
        for (m in floatArrayOf(0.5f, 1f, 2f)) {
            val pct = (targetBpm * m / trackBpm - 1f) * 100f
            if (abs(pct) < abs(best)) {
                best = pct
                bestPct = pct
            }
        }
        return bestPct
    }

    fun sync(i: Int) {
        val d = engine.decks[i]
        val o = engine.decks[1 - i]
        val tb = d.track?.bpm ?: 0f
        val ob = o.track?.bpm ?: 0f
        if (tb <= 0f || ob <= 0f) {
            toast("SYNC needs both decks loaded with a detected BPM")
            return
        }
        val pct = tempoToMatch(tb, ob * o.rate.toFloat())
        if (abs(pct) > TEMPO_RANGE) toast("BPMs are far apart - tempo limited to +/-${TEMPO_RANGE.toInt()}%")
        setTempo(i, (pct * 10f).roundToInt() / 10f)
    }

    fun setFilter(i: Int, v: Float) {
        filter[i] = v
        engine.decks[i].filter = v
    }

    fun toggleEcho(i: Int) {
        echo[i] = !echo[i]
        engine.decks[i].echoOn = echo[i]
    }

    /** Picks the deck a double-tapped song should go to: the one that is not playing. */
    fun loadToActive(s: Song) {
        val a = engine.decks[0]
        val b = engine.decks[1]
        val d = when {
            !a.playing && b.playing -> 0
            !b.playing && a.playing -> 1
            !a.playing && !b.playing -> if (a.track == null) 0 else if (b.track == null) 1 else 0
            else -> if (crossfader < 0.5f) 1 else 0
        }
        loadSong(d, s)
        toast("Loaded to Deck " + (if (d == 0) "A" else "B"))
    }

    fun toggleFlanger(i: Int) {
        flanger[i] = !flanger[i]
        engine.decks[i].flangerOn = flanger[i]
    }

    fun togglePhaser(i: Int) {
        phaser[i] = !phaser[i]
        engine.decks[i].phaserOn = phaser[i]
    }

    private val loopSizes = listOf(0.5f, 1f, 2f, 4f, 8f, 16f)

    fun stepLoopSize(i: Int, dir: Int) {
        val cur = loopSizes.indexOf(loopSize[i]).let { if (it < 0) 3 else it }
        loopSize[i] = loopSizes[(cur + dir).coerceIn(0, loopSizes.size - 1)]
        if (loopOn[i]) loopBeats(i, loopSize[i])
    }

    fun toggleLoop(i: Int) {
        if (loopOn[i]) exitLoop(i) else loopBeats(i, loopSize[i])
    }

    fun applyEqPreset(deck: Int, p: EqPreset) {
        val decks = if (deck < 0) listOf(0, 1) else listOf(deck)
        for (dk in decks) {
            setEq(dk, 0, p.low)
            setEq(dk, 1, p.mid)
            setEq(dk, 2, p.high)
        }
    }

    // ---------- playlists
    fun visibleSongs(): List<Song> = playlists.firstOrNull { it.id == playlistSel }?.songs ?: library

    private fun savePlaylists() {
        val arr = JSONArray()
        for (p in playlists) {
            val o = JSONObject()
            o.put("id", p.id)
            o.put("name", p.name)
            val sa = JSONArray()
            for (s in p.songs) {
                val so = JSONObject()
                so.put("u", s.uri.toString())
                so.put("t", s.title)
                so.put("a", s.artist)
                so.put("d", s.durationMs)
                sa.put(so)
            }
            o.put("songs", sa)
            arr.put(o)
        }
        prefs.edit().putString("playlists", arr.toString()).apply()
    }

    private fun loadPlaylists() {
        val s = prefs.getString("playlists", null) ?: return
        try {
            val arr = JSONArray(s)
            playlists.clear()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val p = Playlist(o.getInt("id"), o.getString("name"))
                val sa = o.getJSONArray("songs")
                for (k in 0 until sa.length()) {
                    val so = sa.getJSONObject(k)
                    p.songs.add(Song(Uri.parse(so.getString("u")), so.getString("t"), so.getString("a"), so.optLong("d", 0L)))
                }
                playlists.add(p)
            }
        } catch (_: Exception) {
        }
    }

    fun createPlaylist(name: String): Int {
        val n = name.trim()
        if (n.isEmpty()) return -1
        val id = (playlists.maxOfOrNull { it.id } ?: 0) + 1
        playlists.add(Playlist(id, n))
        savePlaylists()
        return id
    }

    fun renamePlaylist(id: Int, name: String) {
        val p = playlists.firstOrNull { it.id == id } ?: return
        val n = name.trim()
        if (n.isEmpty()) return
        p.name = n
        savePlaylists()
    }

    fun deletePlaylist(id: Int) {
        playlists.removeAll { it.id == id }
        if (playlistSel == id) playlistSel = -1
        savePlaylists()
    }

    fun addToPlaylist(id: Int, s: Song) {
        val p = playlists.firstOrNull { it.id == id } ?: return
        if (p.songs.any { it.uri == s.uri }) {
            toast("Already in " + p.name)
        } else {
            p.songs.add(s)
            savePlaylists()
            toast("Added to " + p.name)
        }
    }

    fun removeFromPlaylist(id: Int, s: Song) {
        val p = playlists.firstOrNull { it.id == id } ?: return
        p.songs.removeAll { it.uri == s.uri }
        savePlaylists()
    }

    fun setEq(deck: Int, band: Int, db: Float) {
        val v = if (abs(db) < 0.4f) 0f else db
        eq[deck * 3 + band] = v
        val d = engine.decks[deck]
        when (band) {
            0 -> d.eqLow = v
            1 -> d.eqMid = v
            else -> d.eqHigh = v
        }
    }

    fun resetEq() {
        for (dk in 0..1) for (b in 0..2) setEq(dk, b, 0f)
    }

    // loops & hot cues
    fun loopBeats(i: Int, beats: Float) {
        val d = engine.decks[i]
        val t = d.track ?: return
        if (t.bpm <= 0f) {
            toast("Loops need a detected BPM")
            return
        }
        val len = beats * 60.0 / t.bpm * SR
        d.loopStart = d.posFrames
        d.loopEnd = d.posFrames + len
        loopOn[i] = true
    }

    fun exitLoop(i: Int) {
        val d = engine.decks[i]
        d.loopEnd = -1.0
        d.loopStart = -1.0
        loopOn[i] = false
    }

    fun hotCue(i: Int, n: Int) {
        val d = engine.decks[i]
        if (d.track == null) return
        val saved = hotCues[i][n]
        if (saved == null) {
            hotCues[i][n] = (d.posFrames / SR).toFloat()
        } else {
            d.seek(saved * SR.toDouble())
        }
    }

    fun clearHotCue(i: Int, n: Int) {
        hotCues[i][n] = null
    }

    // ---------- mixer
    fun updateCrossfader(v: Float) {
        crossfader = v.coerceIn(0f, 1f)
        engine.crossfader = crossfader
    }

    fun updateMaster(v: Float) {
        master = v.coerceIn(0f, 1f)
        engine.master = master
        prefs.edit().putFloat("master", master).apply()
    }

    fun toggleMute() {
        muted = !muted
        engine.muted = muted
    }

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun setMic(on: Boolean) {
        if (on) {
            if (engine.startMic()) {
                engine.micOn = true
                micOn = true
            } else {
                toast("Could not open the microphone")
            }
        } else {
            engine.micOn = false
            engine.stopMic()
            micOn = false
        }
    }

    fun toggleMic() = setMic(!micOn)

    fun mcDown() {
        mcWasMicOn = micOn
        if (!micOn) setMic(true)
        if (!micOn) return
        engine.mcTalk = true
        mcTalk = true
    }

    fun mcUp() {
        if (!mcTalk) return
        engine.mcTalk = false
        mcTalk = false
        if (!mcWasMicOn) setMic(false)
    }

    fun updateMicGain(v: Float) {
        micGain = v
        engine.micGain = v
        prefs.edit().putFloat("micGain", v).apply()
    }

    fun setDuck(v: Float) {
        duckAmt = v
        engine.duck = v
        prefs.edit().putFloat("duck", v).apply()
    }

    fun updateCrossfadeSec(v: Float) {
        crossfadeSec = v
        prefs.edit().putFloat("xfade", v).apply()
    }

    fun updateBeatMatch(v: Boolean) {
        beatMatch = v
        prefs.edit().putBoolean("beatmatch", v).apply()
    }

    fun stopAll() {
        transitionJob?.cancel()
        forceNext = false
        autoMix = false
        for (i in 0..1) {
            engine.decks[i].playing = false
            exitLoop(i)
        }
        for (v in engine.voices) v.data = null
        for (i in 0..1) refreshDeckUi(i)
    }

    // ---------- auto-mix
    fun updateAutoMix(on: Boolean) {
        autoMix = on
        if (on) {
            val a = engine.decks[0]
            val b = engine.decks[1]
            if (!a.playing && !b.playing) {
                when {
                    a.track != null && !stale[0] -> {
                        a.playing = true
                        updateCrossfader(0f)
                    }
                    b.track != null && !stale[1] -> {
                        b.playing = true
                        updateCrossfader(1f)
                    }
                    else -> {
                        val s = nextSong(emptySet())
                        if (s == null) {
                            toast("Add songs to your library or queue first")
                            autoMix = false
                        } else {
                            updateCrossfader(0f)
                            loadSong(0, s, thenPlay = true)
                        }
                    }
                }
            }
        }
    }

    fun skipNext() {
        if (liveDeck() < 0) {
            toast("Nothing is playing")
            return
        }
        forceNext = true
    }

    private fun nextSong(exclude: Set<Uri>): Song? {
        if (queue.isNotEmpty()) return queue.removeAt(0)
        val pool = visibleSongs().filter { it.uri !in exclude && (it.durationMs == 0L || it.durationMs > 30_000L) }
        return if (pool.isEmpty()) null else pool.random()
    }

    private fun liveDeck(): Int {
        val a = engine.decks[0]
        val b = engine.decks[1]
        return when {
            a.playing && b.playing -> if (crossfader < 0.5f) 0 else 1
            a.playing -> 0
            b.playing -> 1
            else -> -1
        }
    }

    private fun autoMixTick() {
        if (transitionJob?.isActive == true) return
        if (!autoMix && !forceNext) return
        val live = liveDeck()
        if (live < 0) {
            forceNext = false
            return
        }
        val other = 1 - live
        val d = engine.decks[live]
        val t = d.track ?: return
        val od = engine.decks[other]
        val remaining = ((t.frames - d.posFrames) / (SR * d.rate)).toFloat()
        val otherReady = od.track != null && !od.playing && !stale[other] && od.posFrames < SR * 2
        val otherBusy = deckUi[other].loading

        if (!otherReady && !otherBusy && (forceNext || remaining < crossfadeSec + 25f)) {
            val s = nextSong(setOf(t.uri))
            if (s != null) {
                loadSong(other, s)
            } else if (forceNext) {
                forceNext = false
                toast("No next song - add songs to the queue")
            }
            return
        }
        if (otherReady && (forceNext || remaining <= crossfadeSec + 0.5f)) {
            val forced = forceNext
            val secs = if (forced) min(crossfadeSec, 6f) else crossfadeSec
            forceNext = false
            startTransition(live, min(secs, max(remaining - 0.3f, 1.5f)), forced)
        }
    }

    private fun startTransition(from: Int, seconds: Float, isSkip: Boolean = false) {
        val to = 1 - from
        val dFrom = engine.decks[from]
        val dTo = engine.decks[to]
        dTo.seek(0.0)
        if (beatMatch && !isSkip) {
            val lt = dFrom.track
            val inT = dTo.track
            val fb = (lt?.bpm ?: 0f) * dFrom.rate.toFloat()
            val tb = inT?.bpm ?: 0f
            if (lt != null && inT != null && fb > 0f && tb > 0f) {
                val pct = tempoToMatch(tb, fb)
                if (abs(pct) <= TEMPO_RANGE) {
                    setTempo(to, (pct * 10f).roundToInt() / 10f)
                    // line the incoming beat up with the live beat
                    val liveBeatLen = 60.0 / lt.bpm
                    val liveSec = dFrom.posFrames / SR
                    val frac = (((liveSec - lt.beatOffset) / liveBeatLen) % 1.0 + 1.0) % 1.0
                    val startSec = inT.beatOffset + frac * 60.0 / inT.bpm
                    dTo.seek(startSec * SR)
                } else {
                    toast("BPMs too far apart to beat-match - fading without sync")
                }
            }
        }
        dTo.playing = true
        transitionJob = viewModelScope.launch {
            val startCf = crossfader
            val endCf = if (to == 1) 1f else 0f
            val t0 = System.nanoTime()
            while (true) {
                val p = ((System.nanoTime() - t0) / 1e9f / seconds).coerceIn(0f, 1f)
                updateCrossfader(startCf + (endCf - startCf) * p)
                if (p >= 1f) break
                delay(30)
            }
            dFrom.playing = false
            dFrom.seek(0.0)
            exitLoop(from)
            setTempo(from, 0f)
            stale[from] = true
        }
    }

    // ---------- periodic tick (10 Hz)
    private fun tick() {
        for (i in 0..1) refreshDeckUi(i)
        val a = engine.decks[0]
        val b = engine.decks[1]
        val live = when {
            liveDeck() >= 0 -> liveDeck()
            a.track != null -> 0
            b.track != null -> 1
            else -> -1
        }
        nowDeck = if (live >= 0 && engine.decks[live].track != null) live else -1
        nowPlaying = if (live < 0) {
            "Nothing playing"
        } else {
            val t = engine.decks[live].track
            if (t == null) {
                "Nothing playing"
            } else if (t.artist.startsWith("Unknown") || t.artist.isBlank()) {
                t.title
            } else {
                t.artist + " - " + t.title
            }
        }
        if (recording) recSeconds = (engine.recorder?.bytes ?: 0L) / (SR * 4f)
        autoMixTick()
    }
}

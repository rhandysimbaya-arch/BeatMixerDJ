package com.beatmixer.dj

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class Song(
    val uri: Uri,
    val title: String,
    val artist: String,
    val durationMs: Long,
)

data class DeckUi(
    val title: String = "No track",
    val artist: String = "Tap LOAD",
    val bpm: Float = 0f,
    val remaining: Float = 0f,
    val playing: Boolean = false,
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val uri: String = "",
)

enum class Screen(val label: String) {
    DROPS("DROPS"),
    MIX("MIX"),
    MUSIC("MUSIC"),
    PLAY("PLAY"),
    TOOLS("TOOLS"),
    HELP("HELP"),
}

class DropItem(val index: Int, val defaultName: String, val builtin: Boolean = true) {
    var name by mutableStateOf(defaultName)
    var fav by mutableStateOf(true)
    var custom by mutableStateOf(false)
    var category by mutableStateOf("")
    var long by mutableStateOf(false)
    var uri by mutableStateOf("")

    @Volatile
    var sample: ShortArray? = null
}

/** Drops in this category play the whole file (no 8 second limit). Tap again to stop. */
const val LONG_CATEGORY = "FULL LENGTH"

val DEFAULT_DROP_CATEGORIES = listOf("HORNS", "FX", "BASS", "PERCUSSION", "VOICE")

/** Category of each built-in drop, by its position in DropSynth.all. */
val BUILTIN_DROP_CATEGORY = listOf(
    "HORNS", "HORNS", "FX", "BASS", "FX", "FX", "PERCUSSION", "PERCUSSION", "PERCUSSION",
    "FX", "FX", "BASS", "VOICE", "VOICE", "FX", "VOICE", "VOICE", "VOICE",
)

class Playlist(val id: Int, initialName: String) {
    var name by mutableStateOf(initialName)
    val songs = mutableStateListOf<Song>()
}

class EqPreset(val name: String, val low: Float, val mid: Float, val high: Float)

val EQ_PRESETS = listOf(
    EqPreset("FLAT", 0f, 0f, 0f),
    EqPreset("PUNCHY BASS", 6f, -3f, 0f),
    EqPreset("BASS BOOST", 6f, 0f, -3f),
    EqPreset("POP", 0f, 3f, 3f),
    EqPreset("ROCK", 3f, -3f, 3f),
    EqPreset("HIP-HOP", 6f, 0f, 3f),
    EqPreset("VOCAL", -3f, 6f, 3f),
    EqPreset("TREBLE BOOST", -3f, 0f, 6f),
    EqPreset("LOUDNESS", 6f, -3f, 6f),
)

/** Allowed tempo slider range, in percent (+/-). */
const val TEMPO_RANGE = 30f

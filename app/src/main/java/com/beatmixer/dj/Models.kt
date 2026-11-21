package com.beatmixer.dj

import android.net.Uri
import androidx.compose.runtime.getValue
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

    @Volatile
    var sample: ShortArray? = null
}

val DEFAULT_DROP_CATEGORIES = listOf("HORNS", "FX", "BASS", "PERCUSSION", "VOICE")

/** Category of each built-in drop, by its position in DropSynth.all. */
val BUILTIN_DROP_CATEGORY = listOf(
    "HORNS", "HORNS", "FX", "BASS", "FX", "FX", "PERCUSSION", "PERCUSSION", "PERCUSSION",
    "FX", "FX", "BASS", "VOICE", "VOICE", "FX", "VOICE", "VOICE", "VOICE",
)

/** Allowed tempo slider range, in percent (+/-). */
const val TEMPO_RANGE = 30f

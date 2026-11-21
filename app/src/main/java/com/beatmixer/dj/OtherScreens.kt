package com.beatmixer.dj

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.Icon
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.math.roundToInt

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(C.Panel).border(1.dp, C.Border, shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
fun Heading(text: String, color: Color = C.Orange) {
    Label(text, color = color, size = 14.sp)
}

// ------------------------------------------------------------------ shared settings

@Composable
fun SettingRow(title: String, valueText: String, content: @Composable () -> Unit) {
    Column {
        Row {
            Label(title, color = C.Text, size = 13.sp)
            Spacer(Modifier.weight(1f))
            Label(valueText, color = C.Dim, size = 12.sp)
        }
        content()
    }
}

@Composable
fun MixerSettings(vm: MixerViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label("THEME", color = C.Dim, size = 11.sp)
        for (row in THEMES.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                for (t in row) {
                    val sel = vm.themeName == t.name
                    PadButton(
                        Modifier.weight(1f).height(44.dp),
                        accent = Color(t.a),
                        active = sel,
                        activeFill = Color(t.panel2),
                        onClick = { vm.updateTheme(t.name) },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(Color(t.a)))
                            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(Color(t.b)))
                            Label(t.name, color = if (sel) Color.White else C.Dim, size = 11.sp)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Label("DROP CATEGORIES", color = C.Dim, size = 11.sp)
        var newCat by remember { mutableStateOf("") }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (t in vm.categories.toList()) {
                PadButton(Modifier.height(34.dp), accent = C.Blue, onClick = { vm.removeCategory(t) }) {
                    Label(t + "  \u2715", color = C.Blue, size = 11.sp, modifier = Modifier.padding(horizontal = 10.dp))
                }
            }
        }
        Label("Tap a category to remove it (its drops move to OTHER).", color = C.Dim, size = 10.sp, weight = FontWeight.Normal, maxLines = 3)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = newCat,
                onValueChange = { newCat = it },
                singleLine = true,
                label = { Text("New category") },
                modifier = Modifier.weight(1f),
            )
            PadButton(Modifier.height(40.dp), accent = C.Green, onClick = {
                vm.addCategory(newCat)
                newCat = ""
            }) { Label("ADD", color = C.Green, size = 12.sp, modifier = Modifier.padding(horizontal = 12.dp)) }
        }
        SettingRow("Microphone volume", "${(vm.micGain * 100).roundToInt()}%") {
            FlatSlider(vm.micGain, { vm.updateMicGain(it) }, Modifier.fillMaxWidth(), range = 0f..2f, accent = C.Green)
        }
        SettingRow("Music level while MC TALK is held", "${(vm.duckAmt * 100).roundToInt()}%") {
            FlatSlider(vm.duckAmt, { vm.setDuck(it) }, Modifier.fillMaxWidth(), accent = C.Red)
        }
        SettingRow("Auto-mix crossfade", "${vm.crossfadeSec.roundToInt()} s") {
            FlatSlider(
                vm.crossfadeSec,
                { vm.updateCrossfadeSec(it.roundToInt().toFloat()) },
                Modifier.fillMaxWidth(),
                range = 3f..30f,
                accent = C.Yellow,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Beat-match next song in auto-mix", color = C.Text, size = 13.sp)
            Spacer(Modifier.weight(1f))
            PadButton(
                Modifier.width(64.dp).height(32.dp),
                accent = C.Yellow,
                active = vm.beatMatch,
                activeFill = C.Yellow,
                onClick = { vm.updateBeatMatch(!vm.beatMatch) },
            ) { Label(if (vm.beatMatch) "ON" else "OFF", color = if (vm.beatMatch) Color.Black else C.Yellow, size = 12.sp) }
        }
        Label("Use headphones with the mic to avoid feedback.", color = C.Dim, size = 11.sp, weight = FontWeight.Normal, maxLines = 8)
    }
}

// ------------------------------------------------------------------ DROPS

@Composable
private fun CatChip(label: String, accent: Color, selected: Boolean, onClick: () -> Unit) {
    PadButton(
        Modifier.height(34.dp),
        accent = accent,
        active = selected,
        activeFill = accent.copy(alpha = 0.28f),
        onClick = onClick,
    ) { Label(label, color = if (selected) C.Text else accent, size = 12.sp, modifier = Modifier.padding(horizontal = 12.dp)) }
}

private fun effCat(vm: MixerViewModel, d: DropItem): String = if (d.category in vm.categories) d.category else "OTHER"

@Composable
fun DropsScreen(vm: MixerViewModel, modifier: Modifier = Modifier) {
    var onlyFav by remember { mutableStateOf(false) }
    var cat by remember { mutableStateOf("ALL") }
    var editing by remember { mutableStateOf<DropItem?>(null) }
    var adding by remember { mutableStateOf<Uri?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        val e = editing
        if (u != null && e != null) vm.setCustomDrop(e.index, u)
    }
    val pickNew = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) adding = u
    }
    val hasOther = vm.drops.any { effCat(vm, it) == "OTHER" }
    val tabs = listOf("ALL") + vm.categories.toList() + (if (hasOther) listOf("OTHER") else emptyList())
    if (cat !in tabs) cat = "ALL"
    Column(modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Heading("DROPS")
            Label("Tap to fire. Long-press to edit.", color = C.Dim, size = 12.sp, weight = FontWeight.Normal, maxLines = 2, modifier = Modifier.weight(1f))
            PadButton(Modifier.height(34.dp), accent = C.Green, onClick = { pickNew.launch(arrayOf("audio/*")) }) {
                Label("+ ADD DROP", color = C.Green, size = 12.sp, modifier = Modifier.padding(horizontal = 10.dp))
            }
            PadButton(Modifier.height(34.dp), accent = C.Yellow, active = onlyFav, activeFill = Color(0xFF3A3A3A), onClick = { onlyFav = !onlyFav }) {
                Label("FAVORITES", color = C.Yellow, size = 12.sp, modifier = Modifier.padding(horizontal = 10.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (t in tabs) CatChip(t, if (t == "ALL") C.Orange else C.Blue, cat == t) { cat = t }
        }
        val list = vm.drops.filter { (!onlyFav || it.fav) && (cat == "ALL" || effCat(vm, it) == cat) }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(130.dp),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(list, key = { it.index }) { item ->
                DropPad(item, vm, Modifier.fillMaxWidth().height(78.dp), onLongClick = { editing = item })
            }
        }
    }
    val e = editing
    if (e != null) {
        var text by remember(e) { mutableStateOf(e.name) }
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = C.Panel,
            title = { Text("Edit drop", color = C.Text) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Name") })
                    Label("CATEGORY", color = C.Dim, size = 11.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (t in vm.categories.toList()) CatChip(t, C.Blue, e.category == t) { vm.setDropCategory(e.index, t) }
                    }
                    TextButton(onClick = { pick.launch(arrayOf("audio/*")) }) { Text("REPLACE SOUND WITH A FILE") }
                    if (e.builtin) {
                        TextButton(onClick = {
                            vm.resetDrop(e.index)
                            editing = null
                        }) { Text("RESET TO DEFAULT") }
                    } else {
                        TextButton(onClick = {
                            vm.deleteDrop(e.index)
                            editing = null
                        }) { Text("DELETE THIS DROP") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.renameDrop(e.index, text)
                    editing = null
                }) { Text("SAVE") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("CANCEL") } },
        )
    }
    val nu = adding
    if (nu != null) {
        var nm by remember(nu) { mutableStateOf("") }
        var nc by remember(nu) { mutableStateOf(if (cat in vm.categories) cat else (vm.categories.firstOrNull() ?: "")) }
        AlertDialog(
            onDismissRequest = { adding = null },
            containerColor = C.Panel,
            title = { Text("Add drop", color = C.Text) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = nm, onValueChange = { nm = it }, singleLine = true, label = { Text("Drop name") })
                    Label("CATEGORY", color = C.Dim, size = 11.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (t in vm.categories.toList()) CatChip(t, C.Blue, nc == t) { nc = t }
                    }
                    Label("Add or remove categories in Mixer settings (Tools).", color = C.Dim, size = 11.sp, weight = FontWeight.Normal, maxLines = 3)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addDrop(nm, nc, nu)
                    adding = null
                }) { Text("ADD") }
            },
            dismissButton = { TextButton(onClick = { adding = null }) { Text("CANCEL") } },
        )
    }
}

// ------------------------------------------------------------------ MUSIC

@Composable
fun MusicScreen(
    vm: MixerViewModel,
    hasPermission: Boolean,
    requestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableIntStateOf(0) }
    var q by remember { mutableStateOf("") }
    val add = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.addFiles(uris)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u ->
        if (u != null) vm.applyFolder(u)
    }
    Column(modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PadButton(Modifier.height(36.dp), accent = C.Orange, active = tab == 0, activeFill = Color(0xFF3A0000), onClick = { tab = 0 }) {
                Label("LIBRARY (${vm.library.size})", color = C.Orange, size = 12.sp, modifier = Modifier.padding(horizontal = 10.dp))
            }
            PadButton(Modifier.height(36.dp), accent = C.Yellow, active = tab == 1, activeFill = Color(0xFF3A3A3A), onClick = { tab = 1 }) {
                Label("QUEUE (${vm.queue.size})", color = C.Yellow, size = 12.sp, modifier = Modifier.padding(horizontal = 10.dp))
            }
            Spacer(Modifier.weight(1f))
            PadButton(Modifier.height(36.dp), accent = C.Blue, onClick = { add.launch(arrayOf("audio/*")) }) {
                Label("ADD FILES", color = C.Blue, size = 12.sp, modifier = Modifier.padding(horizontal = 10.dp))
            }
            PadButton(Modifier.height(36.dp), accent = C.Dim, onClick = { vm.refreshLibrary() }) {
                Label("REFRESH", color = C.Dim, size = 12.sp, modifier = Modifier.padding(horizontal = 10.dp))
            }
        }
        if (tab == 0) {
            PlaylistBar(vm)
            Label("Long-press a playlist to rename or delete it. Tap PL on a song to add it. Double-tap a song to load it.", color = C.Dim, size = 10.sp, weight = FontWeight.Normal, maxLines = 3)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                PadButton(
                    Modifier.weight(1f).height(36.dp),
                    accent = C.Orange,
                    active = !vm.folderMode,
                    activeFill = Color(0xFF3A0000),
                    onClick = { vm.showAllMusic() },
                ) { Label("DEVICE", color = C.Orange, size = 12.sp) }
                PadButton(
                    Modifier.weight(1.4f).height(36.dp),
                    accent = C.Blue,
                    active = vm.folderMode,
                    activeFill = Color(0xFF3A3A3A),
                    onClick = { if (vm.folderName.isEmpty()) pickFolder.launch(null) else vm.showFolder() },
                ) {
                    Label(
                        if (vm.folderName.isEmpty()) "FOLDER" else "FOLDER: " + vm.folderName,
                        color = C.Blue,
                        size = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                PadButton(Modifier.weight(1f).height(36.dp), accent = C.Dim, onClick = { pickFolder.launch(null) }) {
                    Label(if (vm.folderName.isEmpty()) "CHOOSE" else "CHANGE", color = C.Dim, size = 12.sp)
                }
            }
            if (vm.folderMode) {
                Label(
                    "Showing only songs in the \"${vm.folderName}\" folder (and its sub-folders).",
                    color = C.Dim,
                    size = 11.sp,
                    weight = FontWeight.Normal,
                    maxLines = 3,
                )
            }
            if (vm.scanning) {
                Label("Scanning...", color = C.Dim, size = 11.sp, weight = FontWeight.Normal)
            }
            if (!hasPermission && !vm.folderMode) {
                Card {
                    Label("Allow access to your music to see your library here.", color = C.Text, size = 13.sp, weight = FontWeight.Normal, maxLines = 8)
                    PadButton(Modifier.height(38.dp), accent = C.Green, onClick = requestPermission) {
                        Label("ALLOW MUSIC ACCESS", color = C.Green, size = 13.sp, modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                singleLine = true,
                placeholder = { Text("Search") },
                modifier = Modifier.fillMaxWidth(),
            )
            val list = vm.visibleSongs().filter { q.isBlank() || it.title.contains(q, true) || it.artist.contains(q, true) }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(list, key = { it.uri.toString() }) { s ->
                    SongRow(vm, s)
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PadButton(Modifier.height(34.dp), accent = C.Dim, onClick = { vm.queue.shuffle() }) {
                    Label("SHUFFLE", color = C.Dim, size = 12.sp, modifier = Modifier.padding(horizontal = 10.dp))
                }
                PadButton(Modifier.height(34.dp), accent = C.Red, onClick = { vm.queue.clear() }) {
                    Label("CLEAR", color = C.Red, size = 12.sp, modifier = Modifier.padding(horizontal = 10.dp))
                }
            }
            if (vm.queue.isEmpty()) {
                Label(
                    "Queue is empty. Add songs from the library with +Q. AUTO-MIX plays the queue first, then random library songs.",
                    color = C.Dim,
                    size = 12.sp,
                    weight = FontWeight.Normal, maxLines = 8,
                )
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(vm.queue.toList()) { s ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.Panel).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(s.title, color = C.Text, fontSize = 14.sp, maxLines = 1)
                            Text(s.artist, color = C.Dim, fontSize = 11.sp, maxLines = 1)
                        }
                        PadButton(Modifier.size(34.dp), accent = C.Red, onClick = { vm.queue.remove(s) }) {
                            Label("X", color = C.Red, size = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckTag(vm: MixerViewModel, i: Int, s: Song, modifier: Modifier) {
    val accent = if (i == 0) C.Orange else C.Blue
    val ui = vm.deckUi[i]
    val deckPlaying = ui.playing
    val hasSong = ui.loaded && ui.uri == s.uri.toString()
    val p = if (deckPlaying) pulseValue() else 1f
    PadButton(
        modifier,
        accent = accent.copy(alpha = if (deckPlaying) p else 1f),
        active = hasSong,
        activeFill = if (deckPlaying) accent else Color(0xFF3A3A3A),
        onClick = { vm.loadSong(i, s) },
    ) {
        val letter = if (i == 0) "A" else "B"
        Label(
            if (hasSong && deckPlaying) "\u25B6" + letter else letter,
            color = if (hasSong && deckPlaying) Color.Black else accent,
            size = 14.sp,
        )
    }
}

@Composable
fun SongRow(vm: MixerViewModel, s: Song, compact: Boolean = false) {
    val drag = LocalDrag.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var pickPl by remember { mutableStateOf(false) }
    val onA = vm.deckUi[0].loaded && vm.deckUi[0].uri == s.uri.toString()
    val onB = vm.deckUi[1].loaded && vm.deckUi[1].uri == s.uri.toString()
    val bh = if (compact) 32.dp else 36.dp
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.Panel2).padding(horizontal = 8.dp, vertical = if (compact) 3.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .onGloballyPositioned { origin = it.positionInRoot() }
                .pointerInput(s) { detectTapGestures(onDoubleTap = { vm.loadToActive(s) }) }
                .pointerInput(s) {
                    if (drag != null) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { o ->
                                drag.song = s
                                drag.pos = origin + o
                            },
                            onDrag = { ch, amt ->
                                ch.consume()
                                drag.pos = drag.pos + amt
                            },
                            onDragEnd = {
                                val d = drag.deckAt(drag.pos)
                                val sg = drag.song
                                drag.song = null
                                if (d >= 0 && sg != null) vm.loadSong(d, sg)
                            },
                            onDragCancel = { drag.song = null },
                        )
                    }
                },
        ) {
            Text(s.title, color = C.Text, fontSize = if (compact) 13.sp else 14.sp, maxLines = 1)
            if (onA || onB) {
                val d = if (onA && vm.deckUi[0].playing) 0 else if (onB && vm.deckUi[1].playing) 1 else if (onA) 0 else 1
                val playingNow = vm.deckUi[d].playing
                Label(
                    (if (playingNow) "\u25B6 PLAYING ON " else "LOADED ON ") + (if (d == 0) "A" else "B"),
                    color = if (d == 0) C.Orange else C.Blue,
                    size = 10.sp,
                )
            } else {
                Text(
                    if (s.durationMs > 0) s.artist + "  \u2022  " + fmtTime(s.durationMs / 1000f) else s.artist,
                    color = C.Dim,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
        DeckTag(vm, 0, s, Modifier.size(width = if (compact) 44.dp else 50.dp, height = bh))
        DeckTag(vm, 1, s, Modifier.size(width = if (compact) 44.dp else 50.dp, height = bh))
        PadButton(Modifier.size(width = 40.dp, height = bh), accent = C.Yellow, onClick = { vm.enqueue(s) }) {
            Label("+Q", color = C.Yellow, size = 12.sp)
        }
        PadButton(Modifier.size(width = 40.dp, height = bh), accent = C.Green, onClick = { pickPl = true }) {
            Label("PL", color = C.Green, size = 12.sp)
        }
    }
    if (pickPl) {
        var nn by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pickPl = false },
            containerColor = C.Panel,
            title = { Text("Playlist", color = C.Text, fontSize = 18.sp) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Label(s.title, color = C.Dim, size = 12.sp, weight = FontWeight.Normal)
                    for (p in vm.playlists.toList()) {
                        TextButton(onClick = {
                            vm.addToPlaylist(p.id, s)
                            pickPl = false
                        }) { Text("ADD TO: " + p.name) }
                    }
                    OutlinedTextField(value = nn, onValueChange = { nn = it }, singleLine = true, label = { Text("New playlist name") })
                    TextButton(onClick = {
                        val id = vm.createPlaylist(nn)
                        if (id >= 0) vm.addToPlaylist(id, s)
                        pickPl = false
                    }) { Text("CREATE PLAYLIST AND ADD") }
                    val cur = vm.playlists.firstOrNull { it.id == vm.playlistSel }
                    if (cur != null) {
                        TextButton(onClick = {
                            vm.removeFromPlaylist(cur.id, s)
                            pickPl = false
                        }) { Text("REMOVE FROM " + cur.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickPl = false }) { Text("CLOSE") } },
        )
    }
}

@Composable
fun PlaylistBar(vm: MixerViewModel, compact: Boolean = false) {
    var creating by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf<Playlist?>(null) }
    val h = if (compact) 28.dp else 34.dp
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PadButton(
            Modifier.height(h),
            accent = C.Orange,
            active = vm.playlistSel < 0,
            activeFill = C.Orange.copy(alpha = 0.28f),
            onClick = { vm.playlistSel = -1 },
        ) { Label("ALL MUSIC", color = C.Orange, size = 11.sp, modifier = Modifier.padding(horizontal = 10.dp)) }
        for (p in vm.playlists.toList()) {
            PadButton(
                Modifier.height(h),
                accent = C.Blue,
                active = vm.playlistSel == p.id,
                activeFill = C.Blue.copy(alpha = 0.28f),
                onClick = { vm.playlistSel = p.id },
                onLongClick = { managing = p },
            ) { Label(p.name + " (" + p.songs.size + ")", color = C.Blue, size = 11.sp, modifier = Modifier.padding(horizontal = 10.dp)) }
        }
        PadButton(Modifier.height(h), accent = C.Green, onClick = { creating = true }) {
            Label("+ PLAYLIST", color = C.Green, size = 11.sp, modifier = Modifier.padding(horizontal = 10.dp))
        }
    }
    if (creating) {
        var nm by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            containerColor = C.Panel,
            title = { Text("New playlist", color = C.Text, fontSize = 18.sp) },
            text = { OutlinedTextField(value = nm, onValueChange = { nm = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = {
                TextButton(onClick = {
                    val id = vm.createPlaylist(nm)
                    if (id >= 0) vm.playlistSel = id
                    creating = false
                }) { Text("CREATE") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("CANCEL") } },
        )
    }
    val mg = managing
    if (mg != null) {
        var nm by remember(mg) { mutableStateOf(mg.name) }
        AlertDialog(
            onDismissRequest = { managing = null },
            containerColor = C.Panel,
            title = { Text("Playlist", color = C.Text, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = nm, onValueChange = { nm = it }, singleLine = true, label = { Text("Name") })
                    TextButton(onClick = {
                        vm.deletePlaylist(mg.id)
                        managing = null
                    }) { Text("DELETE THIS PLAYLIST") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.renamePlaylist(mg.id, nm)
                    managing = null
                }) { Text("SAVE") }
            },
            dismissButton = { TextButton(onClick = { managing = null }) { Text("CANCEL") } },
        )
    }
}

/** Music library shown at the bottom of the MIX screen. */
@Composable
fun MixLibrary(vm: MixerViewModel, modifier: Modifier = Modifier) {
    var q by remember { mutableStateOf("") }
    val shape = RoundedCornerShape(14.dp)
    BoxWithConstraints(modifier.clip(shape).background(C.Panel).border(1.dp, C.Border, shape).padding(6.dp)) {
        val tall = maxHeight > 150.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PlaylistBar(vm, true)
            if (tall) {
                BasicTextField(
                    value = q,
                    onValueChange = { q = it },
                    singleLine = true,
                    textStyle = TextStyle(color = C.Text, fontSize = 13.sp),
                    cursorBrush = SolidColor(C.Orange),
                    modifier = Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(6.dp)).background(C.Panel2).padding(horizontal = 8.dp, vertical = 6.dp),
                    decorationBox = { inner ->
                        Box {
                            if (q.isEmpty()) Text("Search  -  hold a song and drag it to a deck, or double-tap to load", color = C.Dim, fontSize = 12.sp, maxLines = 1)
                            inner()
                        }
                    },
                )
            }
            val list = vm.visibleSongs().filter { q.isBlank() || it.title.contains(q, true) || it.artist.contains(q, true) }
            if (list.isEmpty()) {
                Label(
                    "No songs here. Open the MUSIC tab to allow access, choose a folder or add files.",
                    color = C.Dim,
                    size = 11.sp,
                    weight = FontWeight.Normal,
                    maxLines = 3,
                )
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(list, key = { it.uri.toString() }) { s -> SongRow(vm, s, true) }
            }
        }
    }
}

/** Favourite drops next to the library on wide screens. */
@Composable
fun FavDrops(vm: MixerViewModel, dense: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.clip(shape).background(C.Panel).border(1.dp, C.Border, shape).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label("DROPS", color = C.Dim, size = 11.sp)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(78.dp),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(vm.drops.filter { it.fav }, key = { it.index }) { item ->
                DropPad(item, vm, Modifier.fillMaxWidth().height(if (dense) 34.dp else 42.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ PLAY (hot cues, loops, keyboard)

@Composable
private fun DeckPerformance(vm: MixerViewModel, i: Int, modifier: Modifier) {
    val accent = if (i == 0) C.Orange else C.Blue
    Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Heading(if (i == 0) "DECK A" else "DECK B", accent)
            Spacer(Modifier.weight(1f))
            Label(vm.deckUi[i].title, color = C.Dim, size = 11.sp, weight = FontWeight.Normal, maxLines = 8)
        }
        Label("HOT CUES  (tap to set / jump, long-press to clear)", color = C.Dim, size = 11.sp, weight = FontWeight.Normal, maxLines = 8)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (n in 0..3) {
                val saved = vm.hotCues[i][n]
                PadButton(
                    Modifier.weight(1f).height(46.dp),
                    accent = accent,
                    active = saved != null,
                    activeFill = accent,
                    onClick = { vm.hotCue(i, n) },
                    onLongClick = { vm.clearHotCue(i, n) },
                ) {
                    Label(
                        if (saved == null) "${n + 1}" else fmtTime(saved),
                        color = if (saved == null) accent else Color.Black,
                        size = 13.sp,
                    )
                }
            }
        }
        Label("LOOP (beats)", color = C.Dim, size = 11.sp, weight = FontWeight.Normal, maxLines = 8)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val sizes = listOf(0.5f to "1/2", 1f to "1", 2f to "2", 4f to "4", 8f to "8")
            for ((beats, name) in sizes) {
                PadButton(Modifier.weight(1f).height(40.dp), accent = accent, onClick = { vm.loopBeats(i, beats) }) {
                    Label(name, color = accent, size = 13.sp)
                }
            }
            PadButton(
                Modifier.weight(1.3f).height(40.dp),
                accent = C.Red,
                active = vm.loopOn[i],
                activeFill = C.Red,
                onClick = { vm.exitLoop(i) },
            ) { Label("EXIT", color = if (vm.loopOn[i]) Color.White else C.Red, size = 13.sp) }
        }
    }
}

@Composable
private fun Piano(vm: MixerViewModel, octave: Int, modifier: Modifier) {
    val whites = listOf(0, 2, 4, 5, 7, 9, 11, 12)
    val blacks = listOf(0 to 1, 1 to 3, 3 to 6, 4 to 8, 5 to 10)
    BoxWithConstraints(modifier) {
        val w = maxWidth / 8
        Row(Modifier.fillMaxSize()) {
            for (semi in whites) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(1.dp)
                        .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                        .background(Color(0xFFEDEDED))
                        .pointerInput(semi, octave) {
                            detectTapGestures(onPress = { vm.playNote(semi, octave) })
                        },
                )
            }
        }
        for ((idx, semi) in blacks) {
            Box(
                Modifier
                    .offset(x = w * (idx + 1) - w * 0.3f)
                    .width(w * 0.6f)
                    .fillMaxHeight(0.6f)
                    .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                    .background(Color(0xFF14151A))
                    .border(1.dp, Color(0xFF3A3D48), RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                    .pointerInput(semi, octave) {
                        detectTapGestures(onPress = { vm.playNote(semi, octave) })
                    },
            )
        }
    }
}

@Composable
fun PlayScreen(vm: MixerViewModel, wide: Boolean, modifier: Modifier = Modifier) {
    var octave by remember { mutableIntStateOf(1) }
    Column(modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (wide) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeckPerformance(vm, 0, Modifier.weight(1f))
                DeckPerformance(vm, 1, Modifier.weight(1f))
            }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState()).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DeckPerformance(vm, 0, Modifier)
                DeckPerformance(vm, 1, Modifier)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Heading("KEYBOARD", C.Green)
            Spacer(Modifier.weight(1f))
            Label("OCTAVE", color = C.Dim, size = 11.sp)
            PadButton(Modifier.size(34.dp), accent = C.Dim, onClick = { if (octave > 0) octave-- }) { Label("-", size = 16.sp) }
            Label("${octave + 3}", color = C.Text, size = 14.sp)
            PadButton(Modifier.size(34.dp), accent = C.Dim, onClick = { if (octave < 2) octave++ }) { Label("+", size = 16.sp) }
        }
        Piano(vm, octave, Modifier.weight(1f).fillMaxWidth().heightIn(min = 90.dp))
    }
}

// ------------------------------------------------------------------ TOOLS

@Composable
fun ToolsScreen(vm: MixerViewModel, micGate: MicGate, modifier: Modifier = Modifier) {
    var nameDialog by remember { mutableStateOf(false) }
    Column(
        modifier.padding(8.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card {
            Heading("LIVE MIC AND RECORDING")
            Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PadButton(
                    Modifier.weight(1f).fillMaxHeight(),
                    accent = C.Green,
                    active = vm.micOn,
                    activeFill = Color(0xFF3A3A3A),
                    onClick = {
                        if (vm.micOn) vm.setMic(false) else if (micGate { vm.setMic(true) }) vm.setMic(true)
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.Mic, null, tint = C.Green, modifier = Modifier.size(16.dp))
                        Label("MIC", color = C.Green, size = 13.sp)
                    }
                }
                PadButton(
                    Modifier.weight(1.3f).fillMaxHeight(),
                    accent = C.Red,
                    active = vm.mcTalk,
                    activeFill = Color(0xFF4A0000),
                    onPress = { if (micGate(null)) vm.mcDown() },
                    onRelease = { vm.mcUp() },
                ) { Label("MC TALK (hold)", color = C.Red, size = 13.sp) }
                PadButton(
                    Modifier.weight(1f).fillMaxHeight(),
                    accent = C.Red,
                    active = vm.recording,
                    activeFill = Color(0xFF4A0000),
                    onClick = { vm.toggleRec() },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.FiberManualRecord, null, tint = C.Red, modifier = Modifier.size(14.dp))
                        Label(if (vm.recording) fmtTime(vm.recSeconds) else "REC", color = C.Red, size = 13.sp)
                    }
                }
            }
        }
        Card {
            Heading("OUTPUT")
            val msg = when (vm.outputKind) {
                "BLUETOOTH" -> "Bluetooth output detected. Bluetooth adds delay, so beat-matching by ear and the mic will feel late. A cable or USB audio is best."
                "WIRED" -> "Wired / USB output detected. Lowest delay."
                else -> "Playing through the device speaker."
            }
            Label(msg, color = C.Text, size = 13.sp, weight = FontWeight.Normal, maxLines = 8)
        }
        Card {
            Heading("DJ PROFILE")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(vm.djName, color = C.Text, size = 16.sp, modifier = Modifier.weight(1f))
                PadButton(Modifier.height(34.dp), accent = C.Orange, onClick = { nameDialog = true }) {
                    Label("EDIT", color = C.Orange, size = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
                }
            }
        }
        Card {
            Heading("MIXER SETTINGS")
            MixerSettings(vm)
        }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Heading("RECORDINGS")
                Spacer(Modifier.weight(1f))
                PadButton(Modifier.height(32.dp), accent = C.Dim, onClick = { vm.refreshRecordings() }) {
                    Label("REFRESH", color = C.Dim, size = 11.sp, modifier = Modifier.padding(horizontal = 10.dp))
                }
            }
            if (vm.recFiles.isEmpty()) {
                Label("Nothing yet. Press REC on the MIX screen to record your set as a WAV file.", color = C.Dim, size = 12.sp, weight = FontWeight.Normal, maxLines = 8)
            }
            for (f in vm.recFiles.toList()) {
                RecordingRow(vm, f)
            }
            Label("Files are saved in the app's Music folder: Android/data/com.beatmixer.dj/files/Music/recordings", color = C.Dim, size = 10.sp, weight = FontWeight.Normal, maxLines = 8)
        }
    }
    if (nameDialog) NameDialog(vm) { nameDialog = false }
}

@Composable
private fun RecordingRow(vm: MixerViewModel, f: File) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.Panel2).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(f.name, color = C.Text, fontSize = 13.sp, maxLines = 1)
            Text("%.1f MB".format(f.length() / 1048576f), color = C.Dim, fontSize = 11.sp)
        }
        PadButton(Modifier.size(width = 38.dp, height = 34.dp), accent = C.Orange, onClick = { vm.loadRecording(0, f) }) {
            Label("A", color = C.Orange, size = 14.sp)
        }
        PadButton(Modifier.size(width = 38.dp, height = 34.dp), accent = C.Blue, onClick = { vm.loadRecording(1, f) }) {
            Label("B", color = C.Blue, size = 14.sp)
        }
        PadButton(Modifier.size(34.dp), accent = C.Red, onClick = { vm.deleteRecording(f) }) {
            Label("X", color = C.Red, size = 13.sp)
        }
    }
}

// ------------------------------------------------------------------ HELP

@Composable
fun HelpScreen(modifier: Modifier = Modifier) {
    val items = listOf(
        "Music folders" to "In MUSIC, tap CHOOSE to pick a folder. The library then shows only the songs inside that folder (and sub-folders), and AUTO-MIX picks from it too. Tap CHANGE to pick another folder, or ALL MUSIC to go back to everything on your phone.",
        "Loading music" to "Tap LOAD on a deck, or open MUSIC and tap A / B on any song. Use ADD FILES to pick songs from anywhere on your device.",
        "Transport" to "PLAY starts / pauses. CUE: while playing it returns to the cue point; while paused it sets the cue point here, and holding it previews the sound. Tap or drag the small waveform to jump around.",
        "Tempo and SYNC" to "The TEMPO slider changes speed (and pitch, like a record) by up to 30%. SYNC matches this deck's BPM to the other deck. BPM is detected automatically when a song loads.",
        "Mixer" to "The crossfader blends A and B. FILTER knobs sweep a low-pass (left) or high-pass (right) filter - double-tap a knob to reset. ECHO adds a beat-synced echo. EQ in the top bar has LOW / MID / HIGH for each deck.",
        "MIC and MC TALK" to "MIC switches the microphone on. Hold MC TALK to talk over the music: the music ducks while you hold it. Use headphones to avoid feedback.",
        "AUTO-MIX" to "Plays your queue (then random library songs) and crossfades between decks automatically. The next-track button fades straight to the next song right now (no tempo or beat sync). Set the fade length and beat-matching in the mixer settings (sliders button).",
        "DROPS" to "The pads along the bottom fire sound effects instantly. Star the ones you use most. Open the DROPS screen to rename a drop or replace it with your own audio file (long-press).",
        "PLAY" to "Hot cues, beat loops and a small synth keyboard.",
        "REC" to "Records everything you hear (decks, drops and mic) to a WAV file. Find it under TOOLS.",
        "Bluetooth" to "Bluetooth speakers and headphones add delay. For the tightest mixing use a cable or USB audio.",
    )
    Column(modifier.padding(8.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((t, body) in items) {
            Card {
                Heading(t)
                Label(body, color = C.Text, size = 13.sp, weight = FontWeight.Normal, maxLines = 8)
            }
        }
    }
}

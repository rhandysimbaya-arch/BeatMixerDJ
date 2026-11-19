@file:OptIn(ExperimentalFoundationApi::class)

package com.beatmixer.dj

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs
import kotlin.math.roundToInt

/** Returns true when the mic permission is already granted, otherwise asks for it. */
typealias MicGate = (afterGrant: (() -> Unit)?) -> Boolean

fun fmtTime(sec: Float): String {
    val s = sec.toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

// ---------------------------------------------------------------- top bar

@Composable
private fun NowPlayingBox(vm: MixerViewModel, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Label("NOW PLAYING", color = C.Orange, size = 11.sp)
        Text(
            vm.nowPlaying,
            color = C.Text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.basicMarquee(),
        )
    }
}

@Composable
private fun OutputChip(kind: String, modifier: Modifier = Modifier) {
    val bt = kind == "BLUETOOTH"
    val text = when (kind) {
        "BLUETOOTH" -> "BLUETOOTH — may delay. Cable is best"
        "WIRED" -> "WIRED OUTPUT"
        else -> "PHONE SPEAKER"
    }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (bt) C.Yellow else C.Panel2)
            .border(1.dp, if (bt) C.Yellow else C.Border, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (bt) Color(0xFF000000) else C.Dim,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 14.sp,
            maxLines = 2,
        )
    }
}

@Composable
private fun StopAllButton(vm: MixerViewModel, modifier: Modifier = Modifier) {
    PadButton(modifier = modifier, accent = C.Red, active = true, activeFill = C.Red, onClick = { vm.stopAll() }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(14.dp).background(Color.White))
            Label("STOP ALL", color = Color.White, size = 14.sp)
        }
    }
}

@Composable
private fun DjChip(vm: MixerViewModel, onName: () -> Unit, modifier: Modifier = Modifier) {
    PadButton(modifier = modifier, accent = C.Orange, onClick = onName) {
        Row(
            Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.Person, null, tint = C.Orange, modifier = Modifier.size(16.dp))
            Label(vm.djName, color = C.Orange, size = 13.sp)
        }
    }
}

@Composable
private fun MasterBox(vm: MixerViewModel, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Label("MASTER", color = C.Dim, size = 11.sp)
            Spacer(Modifier.weight(1f))
            Label("${(vm.master * 100).roundToInt()}%", color = C.Dim, size = 11.sp)
        }
        FlatSlider(
            value = vm.master,
            onValueChange = { vm.updateMaster(it) },
            modifier = Modifier.fillMaxWidth(),
            accent = C.Yellow,
        )
    }
}

@Composable
private fun MuteButton(vm: MixerViewModel, modifier: Modifier = Modifier) {
    PadButton(modifier = modifier, accent = C.Orange, onClick = { vm.toggleMute() }) {
        Icon(
            if (vm.muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
            null,
            tint = C.Orange,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun TopBar(
    vm: MixerViewModel,
    clock: UiClock,
    compact: Boolean,
    onEq: () -> Unit,
    onName: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!compact) {
        Row(
            modifier.fillMaxWidth().height(60.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NowPlayingBox(vm, Modifier.weight(1f))
            OutputChip(vm.outputKind, Modifier.widthIn(max = 210.dp))
            DjChip(vm, onName, Modifier.height(40.dp))
            PadButton(Modifier.width(52.dp).height(40.dp), accent = C.Orange, onClick = onEq) {
                Label("EQ", color = C.Orange, size = 15.sp)
            }
            VuMeter(vm.engine, clock, Modifier.width(16.dp).height(44.dp))
            MasterBox(vm, Modifier.width(150.dp))
            MuteButton(vm, Modifier.size(44.dp))
            StopAllButton(vm, Modifier.height(44.dp).width(130.dp))
        }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogoMark(Modifier.size(width = 66.dp, height = 36.dp))
                NowPlayingBox(vm, Modifier.weight(1f))
                StopAllButton(vm, Modifier.height(38.dp).width(116.dp))
            }
            Row(
                Modifier.fillMaxWidth().height(38.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutputChip(vm.outputKind, Modifier.weight(1f))
                DjChip(vm, onName, Modifier.height(34.dp))
                PadButton(Modifier.width(46.dp).height(34.dp), accent = C.Orange, onClick = onEq) {
                    Label("EQ", color = C.Orange, size = 14.sp)
                }
            }
            Row(
                Modifier.fillMaxWidth().height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VuMeter(vm.engine, clock, Modifier.width(14.dp).height(34.dp))
                MasterBox(vm, Modifier.weight(1f))
                MuteButton(vm, Modifier.size(38.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- deck

@Composable
fun DeckPanel(
    vm: MixerViewModel,
    clock: UiClock,
    idx: Int,
    compact: Boolean,
    dense: Boolean,
    onLoad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (idx == 0) C.Orange else C.Blue
    val ui = vm.deckUi[idx]
    val d = vm.engine.decks[idx]
    val shape = RoundedCornerShape(14.dp)
    val btnH = if (dense) 38.dp else 46.dp

    @Composable
    fun BpmTime() {
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (ui.bpm > 0f) "%.1f".format(ui.bpm) else "---",
                color = C.Text,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
            Text("-" + fmtTime(ui.remaining), color = C.Dim, fontSize = 12.sp)
        }
    }

    @Composable
    fun LoadButton() {
        PadButton(Modifier.height(36.dp).widthIn(min = 64.dp), accent = accent, onClick = onLoad) {
            Label(if (ui.loading) "..." else "LOAD", color = accent, size = 14.sp)
        }
    }

    Column(
        modifier.clip(shape).background(C.Panel).border(1.dp, C.Border, shape).padding(if (dense) 6.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (dense) 4.dp else 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeckBadge(if (idx == 0) "A" else "B", accent, ui.playing, if (compact) 26.dp else 30.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    ui.title,
                    color = C.Text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.basicMarquee(),
                )
                Text(ui.artist, color = C.Dim, fontSize = 11.sp, maxLines = 1)
            }
            if (!compact) {
                BpmTime()
                LoadButton()
            }
        }
        if (compact) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BpmTime()
                Spacer(Modifier.weight(1f))
                LoadButton()
            }
        }

        OverviewWave(
            deck = d,
            track = d.track,
            accent = accent,
            clock = clock,
            onSeek = { vm.seekFraction(idx, it) },
            modifier = Modifier.fillMaxWidth().height(if (dense) 22.dp else 30.dp),
        )

        if (compact) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Vinyl(d, accent, clock, Modifier)
            }
        } else {
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Vinyl(d, accent, clock, Modifier.fillMaxHeight())
                ScrollWave(d, d.track, accent, clock, Modifier.weight(1f).fillMaxHeight())
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PadButton(
                Modifier.weight(1f).height(btnH),
                accent = accent,
                onPress = { vm.cueDown(idx) },
                onRelease = { vm.cueUp(idx) },
            ) { Label("CUE", color = accent, size = 15.sp) }
            PadButton(
                Modifier.weight(1.3f).height(btnH),
                accent = C.Green,
                active = ui.playing,
                activeFill = C.Green,
                onClick = { vm.togglePlay(idx) },
            ) {
                Icon(
                    if (ui.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    null,
                    tint = if (ui.playing) Color(0xFF000000) else C.Green,
                    modifier = Modifier.size(26.dp),
                )
            }
            PadButton(Modifier.weight(1f).height(btnH), accent = accent, onClick = { vm.sync(idx) }) {
                Label("SYNC", color = accent, size = 15.sp)
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label("TEMPO", color = C.Dim, size = 11.sp)
            FlatSlider(
                value = vm.tempo[idx],
                onValueChange = { v -> vm.setTempo(idx, if (abs(v) < 0.4f) 0f else (v * 10f).roundToInt() / 10f) },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                range = -TEMPO_RANGE..TEMPO_RANGE,
                accent = accent,
                fromCenter = true,
                onReset = { vm.setTempo(idx, 0f) },
            )
            Label("%+.1f%%".format(vm.tempo[idx]), color = C.Text, size = 12.sp, modifier = Modifier.width(54.dp))
        }
    }
}

// ---------------------------------------------------------------- center

@Composable
private fun KnobColumn(vm: MixerViewModel, i: Int, modifier: Modifier) {
    val accent = if (i == 0) C.Orange else C.Blue
    BoxWithConstraints(modifier) {
        val s = maxOf(28.dp, minOf(maxWidth, maxHeight - 18.dp))
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Knob(vm.filter[i], { vm.setFilter(i, it) }, accent, Modifier.size(s))
            Label(if (i == 0) "FILTER A" else "FILTER B", color = C.Dim, size = 11.sp)
        }
    }
}

@Composable
private fun ControlRows(vm: MixerViewModel, onSettings: () -> Unit, micGate: MicGate, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            PadButton(
                Modifier.weight(1f).fillMaxHeight(),
                accent = C.Orange,
                active = vm.echo[0],
                activeFill = Color(0xFF3A0000),
                onClick = { vm.toggleEcho(0) },
            ) { Label("ECHO A", color = C.Orange, size = 13.sp) }
            PadButton(
                Modifier.weight(1f).fillMaxHeight(),
                accent = C.Blue,
                active = vm.echo[1],
                activeFill = Color(0xFF3A3A3A),
                onClick = { vm.toggleEcho(1) },
            ) { Label("ECHO B", color = C.Blue, size = 13.sp) }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
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
                Modifier.weight(1.2f).fillMaxHeight(),
                accent = C.Red,
                active = vm.mcTalk,
                activeFill = Color(0xFF4A0000),
                onPress = { if (micGate(null)) vm.mcDown() },
                onRelease = { vm.mcUp() },
            ) { Label("MC TALK", color = C.Red, size = 13.sp) }
            PadButton(Modifier.width(40.dp).fillMaxHeight(), accent = C.Dim, onClick = onSettings) {
                Icon(Icons.Filled.Tune, null, tint = C.Dim, modifier = Modifier.size(18.dp))
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            PadButton(
                Modifier.weight(1.4f).fillMaxHeight(),
                accent = C.Yellow,
                active = vm.autoMix,
                activeFill = C.Yellow,
                onClick = { vm.updateAutoMix(!vm.autoMix) },
            ) { Label("AUTO-MIX", color = if (vm.autoMix) Color.Black else C.Yellow, size = 13.sp) }
            PadButton(Modifier.width(40.dp).fillMaxHeight(), accent = C.Dim, onClick = { vm.skipNext() }) {
                Icon(Icons.Filled.SkipNext, null, tint = C.Text, modifier = Modifier.size(20.dp))
            }
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
        Row(
            Modifier.weight(0.9f).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Label("A", color = C.Orange, size = 15.sp)
            FlatSlider(
                value = vm.crossfader,
                onValueChange = { vm.updateCrossfader(it) },
                modifier = Modifier.weight(1f),
                accent = C.Yellow,
                onReset = { vm.updateCrossfader(0.5f) },
            )
            Label("B", color = C.Blue, size = 15.sp)
        }
    }
}

@Composable
fun CenterPanel(
    vm: MixerViewModel,
    wide: Boolean,
    onSettings: () -> Unit,
    micGate: MicGate,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(modifier.clip(shape).background(C.Panel).border(1.dp, C.Border, shape).padding(8.dp)) {
        if (wide) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.weight(1.7f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KnobColumn(vm, 0, Modifier.weight(1f).fillMaxHeight())
                    KnobColumn(vm, 1, Modifier.weight(1f).fillMaxHeight())
                }
                ControlRows(vm, onSettings, micGate, Modifier.weight(3.6f).fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KnobColumn(vm, 0, Modifier.width(62.dp).fillMaxHeight())
                ControlRows(vm, onSettings, micGate, Modifier.weight(1f).fillMaxHeight())
                KnobColumn(vm, 1, Modifier.width(62.dp).fillMaxHeight())
            }
        }
    }
}

// ---------------------------------------------------------------- drops

private val dropBg = listOf(Color(0xFF2A0505), Color(0xFF1E1E1E), Color(0xFF141414))
private val dropAccent = listOf(C.Orange, C.Green, C.Blue)

@Composable
fun DropPad(item: DropItem, vm: MixerViewModel, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null) {
    val k = item.index % 3
    PadButton(
        modifier,
        accent = dropAccent[k],
        fill = dropBg[k],
        onPress = { vm.fireDrop(item.index) },
        onLongClick = onLongClick,
    ) {
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(3.dp).background(dropAccent[k]),
        )
        Label(item.name, color = C.Text, size = 14.sp)
        Icon(
            if (item.fav) Icons.Filled.Star else Icons.Filled.StarBorder,
            null,
            tint = C.Yellow,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(22.dp)
                .clickable { vm.toggleFav(item.index) }
                .padding(3.dp),
        )
    }
}

@Composable
fun DropsBar(vm: MixerViewModel, perPage: Int, modifier: Modifier = Modifier) {
    val pages = maxOf(1, (vm.drops.size + perPage - 1) / perPage)
    var page by remember { mutableIntStateOf(0) }
    if (page >= pages) page = 0
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (k in 0 until perPage) {
            val item = vm.drops.getOrNull(page * perPage + k)
            if (item != null) {
                DropPad(item, vm, Modifier.weight(1f).fillMaxHeight())
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
        Column(
            Modifier.width(38.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                null,
                tint = C.Dim,
                modifier = Modifier.size(20.dp).clickable { page = (page + pages - 1) % pages },
            )
            Label("${page + 1}/$pages", color = C.Text, size = 12.sp)
            Icon(
                Icons.Filled.KeyboardArrowDown,
                null,
                tint = C.Dim,
                modifier = Modifier.size(20.dp).clickable { page = (page + 1) % pages },
            )
        }
    }
}

// ---------------------------------------------------------------- mix content

@Composable
fun MixContent(
    vm: MixerViewModel,
    clock: UiClock,
    wide: Boolean,
    dense: Boolean,
    perPage: Int,
    openLoad: (Int) -> Unit,
    openSettings: () -> Unit,
    micGate: MicGate,
    modifier: Modifier = Modifier,
) {
    if (wide) {
        Column(modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeckPanel(vm, clock, 0, false, dense, { openLoad(0) }, Modifier.weight(1.35f).fillMaxHeight())
                CenterPanel(vm, true, openSettings, micGate, Modifier.weight(1f).fillMaxHeight())
                DeckPanel(vm, clock, 1, false, dense, { openLoad(1) }, Modifier.weight(1.35f).fillMaxHeight())
            }
            DropsBar(vm, perPage, Modifier.fillMaxWidth().height(if (dense) 44.dp else 54.dp))
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DeckPanel(vm, clock, 0, true, dense, { openLoad(0) }, Modifier.weight(1f).fillMaxHeight())
                DeckPanel(vm, clock, 1, true, dense, { openLoad(1) }, Modifier.weight(1f).fillMaxHeight())
            }
            CenterPanel(vm, false, openSettings, micGate, Modifier.fillMaxWidth().height(172.dp))
            DropsBar(vm, perPage, Modifier.fillMaxWidth().height(50.dp))
        }
    }
}

// ---------------------------------------------------------------- dialogs

@Composable
fun EqDialog(vm: MixerViewModel, onDismiss: () -> Unit) {
    val names = listOf("LOW", "MID", "HIGH")
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Panel,
        confirmButton = { TextButton(onClick = onDismiss) { Text("DONE") } },
        dismissButton = { TextButton(onClick = { vm.resetEq() }) { Text("RESET") } },
        title = { Text("Equalizer (tap + / - , tap the value to reset)", color = C.Text, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (dk in 0..1) {
                    val accent = if (dk == 0) C.Orange else C.Blue
                    Label(if (dk == 0) "DECK A" else "DECK B", color = accent, size = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    for (b in 0..2) {
                        val cur = vm.eq[dk * 3 + b]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(vertical = 3.dp),
                        ) {
                            Label(names[b], color = C.Dim, size = 12.sp, modifier = Modifier.width(40.dp))
                            PadButton(
                                Modifier.size(44.dp),
                                accent = accent,
                                onClick = { vm.setEq(dk, b, (cur - 3f).coerceAtLeast(-24f)) },
                            ) { Label("\u2212", color = accent, size = 24.sp) }
                            PadButton(
                                Modifier.width(66.dp).height(44.dp),
                                accent = C.Dim,
                                active = cur != 0f,
                                activeFill = Color(0xFF2A2A2A),
                                onClick = { vm.setEq(dk, b, 0f) },
                            ) {
                                Label(
                                    if (cur <= -24f) "KILL" else "%+.0f dB".format(cur),
                                    color = C.Text,
                                    size = 13.sp,
                                )
                            }
                            PadButton(
                                Modifier.size(44.dp),
                                accent = accent,
                                onClick = { vm.setEq(dk, b, (cur + 3f).coerceAtMost(6f)) },
                            ) { Label("+", color = accent, size = 24.sp) }
                        }
                    }
                }
            }
        },
    )
}

@Composable
fun NameDialog(vm: MixerViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(vm.djName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Panel,
        confirmButton = {
            TextButton(onClick = {
                vm.updateDjName(text)
                onDismiss()
            }) { Text("SAVE") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
        title = { Text("DJ name", color = C.Text) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
    )
}

@Composable
fun MixerSettingsDialog(vm: MixerViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Panel,
        confirmButton = { TextButton(onClick = onDismiss) { Text("DONE") } },
        title = { Text("Mixer settings", color = C.Text) },
        text = { MixerSettings(vm) },
    )
}

@Composable
fun LoadDialog(vm: MixerViewModel, deck: Int, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            vm.loadFromUri(deck, u)
            onDismiss()
        }
    }
    val accent = if (deck == 0) C.Orange else C.Blue
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = C.Panel,
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.88f),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("Load to deck ${if (deck == 0) "A" else "B"}", color = accent, size = 17.sp)
                    Spacer(Modifier.weight(1f))
                    PadButton(Modifier.height(36.dp), accent = accent, onClick = { pick.launch(arrayOf("audio/*")) }) {
                        Label("BROWSE FILES", color = accent, size = 12.sp, modifier = Modifier.padding(horizontal = 10.dp))
                    }
                    TextButton(onClick = onDismiss) { Text("CLOSE") }
                }
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    singleLine = true,
                    placeholder = { Text("Search your library") },
                    modifier = Modifier.fillMaxWidth(),
                )
                val list = vm.library.filter {
                    q.isBlank() || it.title.contains(q, true) || it.artist.contains(q, true)
                }
                if (list.isEmpty()) {
                    Text(
                        "No songs found. Allow music access, or tap BROWSE FILES.",
                        color = C.Dim,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(list, key = { it.uri.toString() }) { s ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.loadSong(deck, s)
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                        ) {
                            Text(s.title, color = C.Text, fontSize = 15.sp, maxLines = 1)
                            Text(s.artist, color = C.Dim, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

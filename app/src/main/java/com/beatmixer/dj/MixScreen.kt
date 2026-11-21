@file:OptIn(ExperimentalFoundationApi::class)

package com.beatmixer.dj

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Label("NOW PLAYING", color = C.Orange, size = 11.sp)
            if (vm.nowDeck >= 0) {
                val dc = if (vm.nowDeck == 0) C.Orange else C.Blue
                Box(
                    Modifier.clip(RoundedCornerShape(4.dp)).background(dc).padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(
                        "DECK " + (if (vm.nowDeck == 0) "A" else "B"),
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
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
        "BLUETOOTH" -> "BT (delay)"
        "WIRED" -> "WIRED"
        else -> "SPEAKER"
    }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (bt) C.Yellow else C.Panel2)
            .border(1.dp, if (bt) C.Yellow else C.Border, shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (bt) Color(0xFF000000) else C.Dim,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun StopAllButton(vm: MixerViewModel, modifier: Modifier = Modifier) {
    PadButton(modifier = modifier, accent = C.Red, active = true, activeFill = C.Red, onClick = { vm.stopAll() }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(11.dp).background(Color.White))
            Label("STOP", color = Color.White, size = 13.sp)
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
    onLogo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!compact) {
        Row(
            modifier.fillMaxWidth().height(50.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LogoMark(Modifier.size(width = 62.dp, height = 36.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onLogo))
            NowPlayingBox(vm, Modifier.weight(1f))
            PadButton(Modifier.width(52.dp).height(40.dp), accent = C.Orange, onClick = onEq) {
                Label("EQ", color = C.Orange, size = 15.sp)
            }
            VuMeter(vm.engine, clock, Modifier.width(96.dp).height(26.dp))
            MasterBox(vm, Modifier.width(150.dp))
            MuteButton(vm, Modifier.size(44.dp))
            StopAllButton(vm, Modifier.height(40.dp).width(92.dp))
        }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogoMark(Modifier.size(width = 66.dp, height = 36.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onLogo))
                NowPlayingBox(vm, Modifier.weight(1f))
                StopAllButton(vm, Modifier.height(36.dp).width(88.dp))
            }
            Row(
                Modifier.fillMaxWidth().height(38.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                VuMeter(vm.engine, clock, Modifier.weight(1f).height(26.dp))
                PadButton(Modifier.width(46.dp).height(34.dp), accent = C.Orange, onClick = onEq) {
                    Label("EQ", color = C.Orange, size = 14.sp)
                }
            }
            Row(
                Modifier.fillMaxWidth().height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
    val btnH = if (dense) 34.dp else 40.dp

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
            modifier = Modifier.fillMaxWidth().height(if (dense) 20.dp else 30.dp),
        )

        if (compact) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Vinyl(d, accent, clock, Modifier.heightIn(max = 90.dp).fillMaxHeight())
            }
        } else {
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Vinyl(d, accent, clock, Modifier.heightIn(max = 170.dp).fillMaxHeight())
                ScrollWave(d, d.track, accent, clock, Modifier.weight(1f).fillMaxHeight())
                TempoV(vm, idx, accent, Modifier.width(34.dp).fillMaxHeight())
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
            PadButton(Modifier.weight(0.9f).height(btnH), accent = C.Red, onClick = { vm.brake(idx) }) {
                Box(Modifier.size(13.dp).background(C.Red))
            }
            PadButton(Modifier.weight(1f).height(btnH), accent = accent, onClick = { vm.sync(idx) }) {
                Label("SYNC", color = accent, size = 15.sp)
            }
        }

        val rh = if (dense) 26.dp else 30.dp
        if (compact) {
            HotCueRow(vm, idx, rh, Modifier.fillMaxWidth())
            LoopRow(vm, idx, accent, rh, Modifier.fillMaxWidth())
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HotCueRow(vm, idx, rh, Modifier.weight(1f))
                LoopRow(vm, idx, accent, rh, Modifier.weight(1f))
            }
        }

        if (compact) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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

@Composable
private fun TempoV(vm: MixerViewModel, idx: Int, accent: Color, modifier: Modifier) {
    val v = vm.tempo[idx]
    val setT by rememberUpdatedState { f: Float ->
        val t = TEMPO_RANGE - f * 2f * TEMPO_RANGE
        vm.setTempo(idx, if (abs(t) < 0.4f) 0f else (t * 10f).roundToInt() / 10f)
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { vm.setTempo(idx, 0f) },
                        onTap = { o -> setT((o.y / size.height).coerceIn(0f, 1f)) },
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { ch, _ ->
                        ch.consume()
                        setT((ch.position.y / size.height).coerceIn(0f, 1f))
                    }
                },
        ) {
            val cx = size.width / 2f
            val th = 10.dp.toPx()
            drawRect(C.Border, Offset(cx - 2.dp.toPx(), 0f), Size(4.dp.toPx(), size.height))
            drawRect(C.Dim, Offset(cx - 8.dp.toPx(), size.height / 2f - 1f), Size(16.dp.toPx(), 2f))
            val f = 1f - (v + TEMPO_RANGE) / (2f * TEMPO_RANGE)
            val ty = th / 2f + f * (size.height - th)
            drawRoundRect(accent, Offset(cx - 12.dp.toPx(), ty - th / 2f), Size(24.dp.toPx(), th), CornerRadius(3.dp.toPx()))
        }
        Text("%+.0f".format(v), color = C.Dim, fontSize = 9.sp, maxLines = 1)
    }
}

private val hcColors = listOf(Color(0xFFFF5252), Color(0xFFFFC107), Color(0xFF4CAF50), Color(0xFF42A5F5))

@Composable
private fun HotCueRow(vm: MixerViewModel, idx: Int, h: Dp, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (n in 0..3) {
            val saved = vm.hotCues[idx][n]
            PadButton(
                Modifier.weight(1f).height(h),
                accent = hcColors[n],
                active = saved != null,
                activeFill = hcColors[n],
                onClick = { vm.hotCue(idx, n) },
                onLongClick = { vm.clearHotCue(idx, n) },
            ) { Label("" + (n + 1), color = if (saved != null) Color.Black else hcColors[n], size = 12.sp) }
        }
    }
}

@Composable
private fun LoopRow(vm: MixerViewModel, idx: Int, accent: Color, h: Dp, modifier: Modifier) {
    val sz = vm.loopSize[idx]
    val txt = if (sz < 1f) "1/2" else sz.toInt().toString()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        PadButton(Modifier.width(28.dp).height(h), accent = accent, onClick = { vm.stepLoopSize(idx, -1) }) {
            Label("\u2039", color = accent, size = 16.sp)
        }
        Label(txt, color = C.Text, size = 12.sp, modifier = Modifier.width(22.dp), maxLines = 1)
        PadButton(Modifier.width(28.dp).height(h), accent = accent, onClick = { vm.stepLoopSize(idx, 1) }) {
            Label("\u203A", color = accent, size = 16.sp)
        }
        PadButton(
            Modifier.weight(1f).height(h),
            accent = accent,
            active = vm.loopOn[idx],
            activeFill = accent,
            onClick = { vm.toggleLoop(idx) },
        ) { Label("LOOP", color = if (vm.loopOn[idx]) Color.Black else accent, size = 12.sp) }
    }
}

// ---------------------------------------------------------------- center

@Composable
private fun FxPanel(vm: MixerViewModel, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Label("DROPS", color = C.Dim, size = 11.sp, modifier = Modifier.weight(1f))
            PadButton(Modifier.height(26.dp), accent = C.Green, onClick = { vm.screen = Screen.DROPS }) {
                Label("ALL DROPS \u203A", color = C.Green, size = 11.sp, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
        val list = vm.drops.filter { it.fav }.ifEmpty { vm.drops.toList() }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(66.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(list, key = { it.index }) { item ->
                DropPad(item, vm, Modifier.fillMaxWidth().height(34.dp))
            }
        }
        Row(Modifier.fillMaxWidth().height(38.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            PadButton(
                Modifier.weight(1.4f).fillMaxHeight(),
                accent = C.Yellow,
                active = vm.autoMix,
                activeFill = C.Yellow,
                onClick = { vm.updateAutoMix(!vm.autoMix) },
            ) { Label("AUTO-MIX", color = if (vm.autoMix) Color.Black else C.Yellow, size = 13.sp) }
            PadButton(Modifier.width(44.dp).fillMaxHeight(), accent = C.Dim, onClick = { vm.skipNext() }) {
                Icon(Icons.Filled.SkipNext, null, tint = C.Text, modifier = Modifier.size(20.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().height(28.dp),
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
        FxPanel(vm, Modifier.fillMaxSize())
    }
}

// ---------------------------------------------------------------- drops

private val dropBg = listOf(Color(0xFF2A0505), Color(0xFF1E1E1E), Color(0xFF141414))
private fun dropAccent(k: Int): Color = when (k) { 0 -> C.Orange; 1 -> C.Green; else -> C.Blue }

@Composable
fun DropPad(item: DropItem, vm: MixerViewModel, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null) {
    val k = item.index % 3
    PadButton(
        modifier,
        accent = dropAccent(k),
        fill = dropBg[k],
        onPress = { vm.fireDrop(item.index) },
        onLongClick = onLongClick,
    ) {
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(3.dp).background(dropAccent(k)),
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
    val drag = remember { DragCtl() }
    val hot = if (drag.song != null) drag.deckAt(drag.pos) else -1
    fun deckMod(i: Int, m: Modifier): Modifier = m
        .onGloballyPositioned { c ->
            val r = c.boundsInRoot()
            if (i == 0) drag.b0 = r else drag.b1 = r
        }
        .then(if (hot == i) Modifier.border(3.dp, if (i == 0) C.Orange else C.Blue, RoundedCornerShape(14.dp)) else Modifier)

    CompositionLocalProvider(LocalDrag provides drag) {
        Box(modifier.onGloballyPositioned { drag.root = it.positionInRoot() }) {
            if (wide) {
                Column(Modifier.fillMaxSize().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.weight(if (dense) 1f else 1.5f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DeckPanel(vm, clock, 0, false, dense, { openLoad(0) }, deckMod(0, Modifier.weight(1.35f).fillMaxHeight()))
                        CenterPanel(vm, true, openSettings, micGate, Modifier.weight(1f).fillMaxHeight())
                        DeckPanel(vm, clock, 1, false, dense, { openLoad(1) }, deckMod(1, Modifier.weight(1.35f).fillMaxHeight()))
                    }
                    if (dense) {
                        MixLibrary(vm, Modifier.fillMaxWidth().height(104.dp))
                    } else {
                        MixLibrary(vm, Modifier.fillMaxWidth().weight(1f))
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(Modifier.fillMaxWidth().height(350.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DeckPanel(vm, clock, 0, true, dense, { openLoad(0) }, deckMod(0, Modifier.weight(1f).fillMaxHeight()))
                        DeckPanel(vm, clock, 1, true, dense, { openLoad(1) }, deckMod(1, Modifier.weight(1f).fillMaxHeight()))
                    }
                    CenterPanel(vm, false, openSettings, micGate, Modifier.fillMaxWidth().height(230.dp))
                    MixLibrary(vm, Modifier.fillMaxWidth().height(270.dp))
                }
            }
            val g = drag.song
            if (g != null) {
                Box(
                    Modifier
                        .offset { IntOffset((drag.pos.x - drag.root.x).roundToInt() - 70.dp.roundToPx(), (drag.pos.y - drag.root.y).roundToInt() - 44.dp.roundToPx()) }
                        .width(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(C.Orange.copy(alpha = 0.92f))
                        .padding(8.dp),
                ) {
                    Text(g.title, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- dialogs

@Composable
private fun PresetChips(accent: Color, onPick: (EqPreset) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (p in EQ_PRESETS) {
            PadButton(Modifier.height(32.dp), accent = accent, onClick = { onPick(p) }) {
                Label(p.name, color = accent, size = 11.sp, modifier = Modifier.padding(horizontal = 10.dp))
            }
        }
    }
}

@Composable
fun EqDialog(vm: MixerViewModel, onDismiss: () -> Unit) {
    val names = listOf("LOW", "MID", "HIGH")
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Panel,
        confirmButton = { TextButton(onClick = onDismiss) { Text("DONE") } },
        dismissButton = { TextButton(onClick = { vm.resetEq() }) { Text("RESET") } },
        title = { Text("Equalizer", color = C.Text, fontSize = 18.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label("PRESETS - BOTH DECKS", color = C.Green, size = 12.sp)
                PresetChips(C.Green) { vm.applyEqPreset(-1, it) }
                Label("Tap + / - to adjust, tap the value to reset.", color = C.Dim, size = 10.sp, weight = FontWeight.Normal, maxLines = 2)
                for (dk in 0..1) {
                    val accent = if (dk == 0) C.Orange else C.Blue
                    Label(if (dk == 0) "DECK A" else "DECK B", color = accent, size = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    PresetChips(accent) { vm.applyEqPreset(dk, it) }
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
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { MixerSettings(vm) } },
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

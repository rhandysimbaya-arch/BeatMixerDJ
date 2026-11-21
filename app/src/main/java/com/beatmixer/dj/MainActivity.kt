package com.beatmixer.dj

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideBars()
        setContent {
            BeatTheme { BeatApp() }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    private fun hideBars() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
private fun NavIcon(screen: Screen, tint: Color) {
    val mod = Modifier.size(24.dp)
    when (screen) {
        Screen.DROPS -> Icon(Icons.Filled.GraphicEq, null, tint = tint, modifier = mod)
        Screen.MIX -> Icon(Icons.Filled.Tune, null, tint = tint, modifier = mod)
        Screen.MUSIC -> Icon(Icons.Filled.LibraryMusic, null, tint = tint, modifier = mod)
        Screen.PLAY -> Icon(Icons.Filled.Piano, null, tint = tint, modifier = mod)
        Screen.TOOLS -> Icon(Icons.Filled.Build, null, tint = tint, modifier = mod)
        Screen.HELP -> Box(
            Modifier.size(22.dp).clip(CircleShape).border(2.dp, tint, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("?", color = tint, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold) }
    }
}

@Composable
private fun NavItem(screen: Screen, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val tint = if (selected) C.Orange else C.Dim
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF2A0A0A) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NavIcon(screen, tint)
        Label(screen.label, color = tint, size = 11.sp)
    }
}

@Composable
fun BeatApp(vm: MixerViewModel = viewModel()) {
    val ctx = LocalContext.current
    val clock = remember { UiClock() }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (t - last > 28_000_000L) {
                    last = t
                    clock.tick = t
                }
            }
        }
    }

    // ---- permissions
    val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var hasLib by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, audioPerm) == PackageManager.PERMISSION_GRANTED)
    }
    val libLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasLib = ok
        if (ok) vm.refreshLibrary()
    }
    var pendingMic by remember { mutableStateOf<(() -> Unit)?>(null) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            pendingMic?.invoke()
            if (pendingMic == null) vm.toast("Microphone allowed - press MIC or MC TALK again")
        } else {
            vm.toast("Microphone permission denied")
        }
        pendingMic = null
    }
    val micGate: MicGate = { after ->
        if (vm.hasMicPermission()) {
            true
        } else {
            pendingMic = after
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            false
        }
    }

    LaunchedEffect(Unit) {
        if (!hasLib) libLauncher.launch(audioPerm)
    }
    LaunchedEffect(Unit) {
        vm.messages.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(vm.screen) {
        if (vm.screen == Screen.TOOLS) vm.refreshRecordings()
    }
    var exitOpen by remember { mutableStateOf(false) }
    var navOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = true) {
        if (navOpen) navOpen = false else if (vm.screen != Screen.MIX) vm.screen = Screen.MIX else exitOpen = true
    }

    // ---- dialogs
    var eqOpen by remember { mutableStateOf(false) }
    var nameOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var loadDeck by remember { mutableStateOf<Int?>(null) }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(C.Bg).windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val wide = maxWidth >= 600.dp
        val dense = maxHeight < 480.dp
        val perPage = if (!wide) 3 else if (maxWidth < 760.dp) 4 else 6

        val content: @Composable (Modifier) -> Unit = { m ->
            when (vm.screen) {
                Screen.MIX -> MixContent(
                    vm, clock, wide, dense, perPage,
                    { loadDeck = it }, { settingsOpen = true }, micGate, m,
                )
                Screen.DROPS -> DropsScreen(vm, m)
                Screen.MUSIC -> MusicScreen(vm, hasLib, { libLauncher.launch(audioPerm) }, m)
                Screen.PLAY -> PlayScreen(vm, wide, m)
                Screen.TOOLS -> ToolsScreen(vm, micGate, m)
                Screen.HELP -> HelpScreen(m)
            }
        }

        val navT by animateFloatAsState(if (navOpen) 1f else 0f, tween(420), label = "nav")
        val toggleNav = { navOpen = !navOpen }
        Box(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)) {
            Column(Modifier.fillMaxSize()) {
                TopBar(vm, clock, !wide, { eqOpen = true }, { nameOpen = true }, toggleNav)
                if (!wide) Spacer(Modifier.height(6.dp))
                Spacer(Modifier.height(if (wide) 4.dp else 0.dp))
                content(Modifier.weight(1f).fillMaxWidth())
            }
            if (navOpen) {
                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { navOpen = false } })
            }
            if (navT > 0.001f) {
                val screens = Screen.entries
                val n = screens.size
                val itemH = if (dense) 50.dp else 58.dp
                Column(
                    Modifier
                        .padding(top = if (wide) 54.dp else 50.dp)
                        .width(84.dp)
                        .graphicsLayer { alpha = navT.coerceIn(0f, 1f) }
                        .clip(RoundedCornerShape(14.dp))
                        .background(C.Panel)
                        .border(1.dp, C.Border, RoundedCornerShape(14.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for ((i, s) in screens.withIndex()) {
                        val p = (navT * 1.6f - i * 0.12f).coerceIn(0f, 1f)
                        NavItem(
                            s,
                            vm.screen == s,
                            {
                                vm.screen = s
                                navOpen = false
                            },
                            Modifier
                                .fillMaxWidth()
                                .height(itemH)
                                .graphicsLayer {
                                    translationY = -(1f - p) * (i * (itemH.toPx() + 2.dp.toPx()) + 40.dp.toPx())
                                    alpha = p
                                    scaleX = 0.4f + 0.6f * p
                                    scaleY = 0.4f + 0.6f * p
                                },
                        )
                    }
                }
            }
        }

        if (exitOpen) {
            AlertDialog(
                onDismissRequest = { exitOpen = false },
                containerColor = C.Panel,
                title = { Text("Exit RB Pro Audio?", color = C.Text) },
                text = { Text("Playback will stop.", color = C.Dim) },
                confirmButton = { TextButton(onClick = { (ctx as? Activity)?.finish() }) { Text("EXIT") } },
                dismissButton = { TextButton(onClick = { exitOpen = false }) { Text("STAY") } },
            )
        }
        if (eqOpen) EqDialog(vm) { eqOpen = false }
        if (nameOpen) NameDialog(vm) { nameOpen = false }
        if (settingsOpen) MixerSettingsDialog(vm) { settingsOpen = false }
        val ld = loadDeck
        if (ld != null) LoadDialog(vm, ld) { loadDeck = null }
    }
}

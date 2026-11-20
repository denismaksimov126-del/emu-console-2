package com.denismaksimov.emuconsole

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sin

data class AppItem(
    val name: String,
    val packageName: String,
    val icon: ImageBitmap
)

enum class WallpaperStyle(val title: String) {
    SINGLE_WAVE("Волна PlayStation"),
    XMB_DOUBLE("Двойная волна XMB"),
    PARTICLES("Звёздная пыль PS3"),
    CYBER_GRID("Неоновая сетка"),
    STATIC("Только фото / чистый фон")
}

enum class ConsoleTheme(val title: String, val accent: Color, val bgTop: Color, val bgMid: Color) {
    PS_BLUE("PlayStation Blue", Color(0xFF38BDF8), Color(0xFF0A0E17), Color(0xFF131B2E)),
    PSP_GOLD("PSP Gold", Color(0xFFFBBF24), Color(0xFF171207), Color(0xFF2E230D)),
    EMERALD("Xbox Emerald", Color(0xFF34D399), Color(0xFF061510), Color(0xFF0D2B21)),
    CRIMSON("Crimson Red", Color(0xFFF87171), Color(0xFF180808), Color(0xFF2E1010)),
    CYBER_PURPLE("Cyberpunk Neon", Color(0xFFC084FC), Color(0xFF12081C), Color(0xFF241038)),
    NOTHING_WHITE("Nothing Monochrome", Color(0xFFF8FAFC), Color(0xFF09090B), Color(0xFF18181B))
}

enum class SoundScheme(val title: String) {
    PSP_CLASSIC("PSP Классика"),
    RETRO_8BIT("Ретро 8-Бит"),
    SOFT_CLICK("Мягкий клик"),
    OFF("Без звука")
}

enum class CardShapeStyle(val title: String, val radiusDp: Int) {
    ROUNDED("Скруглённые", 22),
    SHARP("Острые углы", 6),
    PILL("Мягкий овал", 34)
}

enum class LayoutStyle(val title: String) {
    CAROUSEL("Лента (PS5 / Switch)"),
    GRID("Сетка (Steam Deck / Vita)")
}

class ConsoleSoundPlayer {
    private var toneGen: ToneGenerator? = null

    init {
        runCatching {
            toneGen = ToneGenerator(AudioManager.STREAM_SYSTEM, 70)
        }
    }

    fun playMove(scheme: SoundScheme) {
        if (scheme == SoundScheme.OFF) return
        val tone = when (scheme) {
            SoundScheme.PSP_CLASSIC -> ToneGenerator.TONE_PROP_BEEP
            SoundScheme.RETRO_8BIT -> ToneGenerator.TONE_DTMF_1
            SoundScheme.SOFT_CLICK -> ToneGenerator.TONE_PROP_NACK
            SoundScheme.OFF -> return
        }
        runCatching { toneGen?.startTone(tone, 35) }
    }

    fun playSelect(scheme: SoundScheme) {
        if (scheme == SoundScheme.OFF) return
        val tone = when (scheme) {
            SoundScheme.PSP_CLASSIC -> ToneGenerator.TONE_PROP_ACK
            SoundScheme.RETRO_8BIT -> ToneGenerator.TONE_DTMF_D
            SoundScheme.SOFT_CLICK -> ToneGenerator.TONE_PROP_BEEP2
            SoundScheme.OFF -> return
        }
        runCatching { toneGen?.startTone(tone, 95) }
    }

    fun playOpenMenu(scheme: SoundScheme) {
        if (scheme == SoundScheme.OFF) return
        val tone = when (scheme) {
            SoundScheme.PSP_CLASSIC -> ToneGenerator.TONE_PROP_BEEP2
            SoundScheme.RETRO_8BIT -> ToneGenerator.TONE_DTMF_5
            SoundScheme.SOFT_CLICK -> ToneGenerator.TONE_PROP_BEEP
            SoundScheme.OFF -> return
        }
        runCatching { toneGen?.startTone(tone, 65) }
    }

    fun release() {
        runCatching { toneGen?.release() }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                EmuConsoleScreen()
            }
        }
    }
}

@Composable
fun EmuConsoleScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("emu_console_prefs", Context.MODE_PRIVATE) }
    val soundPlayer = remember { ConsoleSoundPlayer() }

    DisposableEffect(Unit) {
        onDispose { soundPlayer.release() }
    }

    var allApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var selectedPackages by remember {
        mutableStateOf(prefs.getStringSet("saved_emulators", emptySet()) ?: emptySet())
    }

    var consoleTitle by remember { mutableStateOf(prefs.getString("console_title", "EMU CONSOLE") ?: "EMU CONSOLE") }
    var vibrationEnabled by remember { mutableStateOf(prefs.getBoolean("vibration_enabled", true)) }
    var largeCards by remember { mutableStateOf(prefs.getBoolean("large_cards", false)) }
    var showLabels by remember { mutableStateOf(prefs.getBoolean("show_labels", true)) }
    var showBottomHints by remember { mutableStateOf(prefs.getBoolean("show_bottom_hints", true)) }
    var glowPulse by remember { mutableStateOf(prefs.getBoolean("glow_pulse", true)) }
    var wallpaperDim by remember { mutableFloatStateOf(prefs.getFloat("wallpaper_dim", 0.45f)) }

    var soundScheme by remember {
        mutableStateOf(SoundScheme.entries.getOrElse(prefs.getInt("sound_scheme", 0)) { SoundScheme.PSP_CLASSIC })
    }
    var wallpaperStyle by remember {
        mutableStateOf(WallpaperStyle.entries.getOrElse(prefs.getInt("wallpaper_style", 0)) { WallpaperStyle.SINGLE_WAVE })
    }
    var consoleTheme by remember {
        mutableStateOf(ConsoleTheme.entries.getOrElse(prefs.getInt("console_theme", 0)) { ConsoleTheme.PS_BLUE })
    }
    var cardShape by remember {
        mutableStateOf(CardShapeStyle.entries.getOrElse(prefs.getInt("card_shape", 0)) { CardShapeStyle.ROUNDED })
    }
    var layoutStyle by remember {
        mutableStateOf(LayoutStyle.entries.getOrElse(prefs.getInt("layout_style", 0)) { LayoutStyle.CAROUSEL })
    }

    var customWallpaper by remember { mutableStateOf<ImageBitmap?>(null) }
    var showSelectorDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedBitmap = saveWallpaperToInternalStorage(context, uri)
            if (savedBitmap != null) {
                customWallpaper = savedBitmap.asImageBitmap()
                soundPlayer.playSelect(soundScheme)
            }
        }
    }

    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.IO) { fetchInstalledApps(context) }
        customWallpaper = withContext(Dispatchers.IO) { loadSavedWallpaper(context)?.asImageBitmap() }
    }

    val myEmulators = remember(allApps, selectedPackages) {
        allApps.filter { it.packageName in selectedPackages }
    }

    val animBgTop by animateColorAsState(targetValue = consoleTheme.bgTop, animationSpec = tween(500), label = "bgTop")
    val animBgMid by animateColorAsState(targetValue = consoleTheme.bgMid, animationSpec = tween(500), label = "bgMid")
    val animAccent by animateColorAsState(targetValue = consoleTheme.accent, animationSpec = tween(500), label = "accent")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(animBgTop, animBgMid, Color(0xFF06080D))
                )
            )
    ) {
        if (customWallpaper != null) {
            Image(
                bitmap = customWallpaper!!,
                contentDescription = "Фон из галереи",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = wallpaperDim))
            )
        }

        LiveWallpaperLayer(style = wallpaperStyle, accentColor = animAccent)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            ConsoleTopBar(
                title = consoleTitle,
                accentColor = animAccent,
                onOpenSettings = {
                    soundPlayer.playOpenMenu(soundScheme)
                    showSettingsDialog = true
                }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (layoutStyle == LayoutStyle.CAROUSEL) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        contentPadding = PaddingValues(horizontal = 48.dp)
                    ) {
                        itemsIndexed(myEmulators) { index, app ->
                            EmulatorTile(
                                app = app,
                                isFirst = index == 0,
                                accentColor = animAccent,
                                largeMode = largeCards,
                                showLabel = showLabels,
                                shapeRadius = cardShape.radiusDp,
                                glowPulse = glowPulse,
                                onFocusMove = {
                                    soundPlayer.playMove(soundScheme)
                                    if (vibrationEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                onLaunch = {
                                    soundPlayer.playSelect(soundScheme)
                                    if (vibrationEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                                        context.startActivity(it)
                                    }
                                }
                            )
                        }

                        item {
                            ActionTile(
                                title = "Добавить",
                                icon = Icons.Default.Add,
                                isFirst = myEmulators.isEmpty(),
                                accentColor = animAccent,
                                largeMode = largeCards,
                                showLabel = showLabels,
                                shapeRadius = cardShape.radiusDp,
                                glowPulse = glowPulse,
                                onFocusMove = { soundPlayer.playMove(soundScheme) },
                                onClick = {
                                    soundPlayer.playOpenMenu(soundScheme)
                                    showSelectorDialog = true
                                }
                            )
                        }

                        item {
                            ActionTile(
                                title = "Настройки",
                                icon = Icons.Default.Settings,
                                isFirst = false,
                                accentColor = animAccent,
                                largeMode = largeCards,
                                showLabel = showLabels,
                                shapeRadius = cardShape.radiusDp,
                                glowPulse = glowPulse,
                                onFocusMove = { soundPlayer.playMove(soundScheme) },
                                onClick = {
                                    soundPlayer.playOpenMenu(soundScheme)
                                    showSettingsDialog = true
                                }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = if (largeCards) 185.dp else 150.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        gridItemsIndexed(myEmulators) { index, app ->
                            EmulatorTile(
                                app = app,
                                isFirst = index == 0,
                                accentColor = animAccent,
                                largeMode = largeCards,
                                showLabel = showLabels,
                                shapeRadius = cardShape.radiusDp,
                                glowPulse = glowPulse,
                                onFocusMove = {
                                    soundPlayer.playMove(soundScheme)
                                    if (vibrationEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                onLaunch = {
                                    soundPlayer.playSelect(soundScheme)
                                    if (vibrationEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                                        context.startActivity(it)
                                    }
                                }
                            )
                        }

                        item {
                            ActionTile(
                                title = "Добавить",
                                icon = Icons.Default.Add,
                                isFirst = myEmulators.isEmpty(),
                                accentColor = animAccent,
                                largeMode = largeCards,
                                showLabel = showLabels,
                                shapeRadius = cardShape.radiusDp,
                                glowPulse = glowPulse,
                                onFocusMove = { soundPlayer.playMove(soundScheme) },
                                onClick = {
                                    soundPlayer.playOpenMenu(soundScheme)
                                    showSelectorDialog = true
                                }
                            )
                        }

                        item {
                            ActionTile(
                                title = "Настройки",
                                icon = Icons.Default.Settings,
                                isFirst = false,
                                accentColor = animAccent,
                                largeMode = largeCards,
                                showLabel = showLabels,
                                shapeRadius = cardShape.radiusDp,
                                glowPulse = glowPulse,
                                onFocusMove = { soundPlayer.playMove(soundScheme) },
                                onClick = {
                                    soundPlayer.playOpenMenu(soundScheme)
                                    showSettingsDialog = true
                                }
                            )
                        }
                    }
                }
            }

            if (showBottomHints) {
                ConsoleBottomHints()
            }
        }

        if (showSelectorDialog) {
            AppPickerDialog(
                apps = allApps,
                selected = selectedPackages,
                accentColor = animAccent,
                onToggle = { pkg ->
                    soundPlayer.playMove(soundScheme)
                    val newSet = if (pkg in selectedPackages) selectedPackages - pkg else selectedPackages + pkg
                    selectedPackages = newSet
                    prefs.edit().putStringSet("saved_emulators", newSet).apply()
                },
                onClose = {
                    soundPlayer.playSelect(soundScheme)
                    showSelectorDialog = false
                }
            )
        }

        if (showSettingsDialog) {
            ConsoleSettingsDialog(
                consoleTitle = consoleTitle,
                onTitleChange = {
                    consoleTitle = it
                    prefs.edit().putString("console_title", it).apply()
                },
                hasCustomWallpaper = customWallpaper != null,
                onPickGalleryWallpaper = {
                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onClearWallpaper = {
                    deleteSavedWallpaper(context)
                    customWallpaper = null
                    soundPlayer.playMove(soundScheme)
                },
                wallpaperDim = wallpaperDim,
                onWallpaperDimChange = {
                    wallpaperDim = it
                    prefs.edit().putFloat("wallpaper_dim", it).apply()
                },
                wallpaperStyle = wallpaperStyle,
                onWallpaperStyleChange = {
                    wallpaperStyle = it
                    prefs.edit().putInt("wallpaper_style", it.ordinal).apply()
                    soundPlayer.playMove(soundScheme)
                },
                consoleTheme = consoleTheme,
                onThemeChange = {
                    consoleTheme = it
                    prefs.edit().putInt("console_theme", it.ordinal).apply()
                    soundPlayer.playMove(soundScheme)
                },
                layoutStyle = layoutStyle,
                onLayoutStyleChange = {
                    layoutStyle = it
                    prefs.edit().putInt("layout_style", it.ordinal).apply()
                    soundPlayer.playMove(soundScheme)
                },
                cardShape = cardShape,
                onCardShapeChange = {
                    cardShape = it
                    prefs.edit().putInt("card_shape", it.ordinal).apply()
                    soundPlayer.playMove(soundScheme)
                },
                soundScheme = soundScheme,
                onSoundSchemeChange = {
                    soundScheme = it
                    prefs.edit().putInt("sound_scheme", it.ordinal).apply()
                    soundPlayer.playSelect(it)
                },
                vibrationEnabled = vibrationEnabled,
                onVibrationChange = {
                    vibrationEnabled = it
                    prefs.edit().putBoolean("vibration_enabled", it).apply()
                },
                largeCards = largeCards,
                onLargeCardsChange = {
                    largeCards = it
                    prefs.edit().putBoolean("large_cards", it).apply()
                },
                showLabels = showLabels,
                onShowLabelsChange = {
                    showLabels = it
                    prefs.edit().putBoolean("show_labels", it).apply()
                },
                showBottomHints = showBottomHints,
                onShowBottomHintsChange = {
                    showBottomHints = it
                    prefs.edit().putBoolean("show_bottom_hints", it).apply()
                },
                glowPulse = glowPulse,
                onGlowPulseChange = {
                    glowPulse = it
                    prefs.edit().putBoolean("glow_pulse", it).apply()
                },
                onSetDefaultLauncher = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                },
                onClose = {
                    soundPlayer.playSelect(soundScheme)
                    showSettingsDialog = false
                }
            )
        }
    }
}

@Composable
fun LiveWallpaperLayer(style: WallpaperStyle, accentColor: Color) {
    if (style == WallpaperStyle.STATIC) return

    val infiniteTransition = rememberInfiniteTransition(label = "bgAnim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        when (style) {
            WallpaperStyle.SINGLE_WAVE -> {
                val path = Path().apply {
                    moveTo(0f, h * 0.65f)
                    for (x in 0..w.toInt() step 16) {
                        val y = h * 0.65f + sin((x / w) * 4f + phase) * 42f
                        lineTo(x.toFloat(), y)
                    }
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(path = path, color = accentColor.copy(alpha = 0.14f))
            }

            WallpaperStyle.XMB_DOUBLE -> {
                for (layer in 0..1) {
                    val offsetPhase = phase + layer * 1.5f
                    val path = Path().apply {
                        moveTo(0f, h * (0.55f + layer * 0.1f))
                        for (x in 0..w.toInt() step 16) {
                            val y = h * (0.55f + layer * 0.1f) + sin((x / w) * 3.5f + offsetPhase) * (50f - layer * 12f)
                            lineTo(x.toFloat(), y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = accentColor.copy(alpha = 0.24f - layer * 0.08f),
                        style = Stroke(width = (4 - layer).dp.toPx())
                    )
                }
            }

            WallpaperStyle.PARTICLES -> {
                for (i in 1..32) {
                    val px = ((i * 137f + phase * 45f) % w)
                    val py = ((i * 89f + sin(phase + i) * 35f + h) % h)
                    val radius = (2f + (i % 4) * 1.5f).dp.toPx()
                    drawCircle(
                        color = accentColor.copy(alpha = 0.18f + (i % 3) * 0.07f),
                        radius = radius,
                        center = Offset(px, py)
                    )
                }
            }

            WallpaperStyle.CYBER_GRID -> {
                val horizon = h * 0.55f
                val step = 60.dp.toPx()
                val shift = (phase / (2 * Math.PI).toFloat()) * step
                var y = horizon + shift
                while (y < h) {
                    drawLine(
                        color = accentColor.copy(alpha = 0.18f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    y += step * 0.6f
                }
                for (i in -6..6) {
                    drawLine(
                        color = accentColor.copy(alpha = 0.15f),
                        start = Offset(w / 2f + i * 40f, horizon),
                        end = Offset(w / 2f + i * 190f, h),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            }

            WallpaperStyle.STATIC -> {}
        }
    }
}

@Composable
fun ConsoleTopBar(title: String, accentColor: Color, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    var time by remember { mutableStateOf("") }
    var battery by remember { mutableIntStateOf(100) }

    LaunchedEffect(Unit) {
        while (true) {
            time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            delay(5000)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(accentColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title.uppercase().ifBlank { "EMU CONSOLE" },
                color = accentColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.5.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$battery%   •   $time",
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(16.dp))
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Настройки",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun EmulatorTile(
    app: AppItem,
    isFirst: Boolean,
    accentColor: Color,
    largeMode: Boolean,
    showLabel: Boolean,
    shapeRadius: Int,
    glowPulse: Boolean,
    onFocusMove: () -> Unit,
    onLaunch: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val scale by animateFloatAsState(targetValue = if (focused) 1.08f else 1.0f, label = "tileScale")

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    val cardW = if (largeMode) 190.dp else 156.dp
    val cardH = if (largeMode) 215.dp else 182.dp
    val iconSize = if (largeMode) 100.dp else 80.dp
    val borderColor = if (focused) accentColor.copy(alpha = if (glowPulse) pulseAlpha else 1f) else Color.White.copy(alpha = 0.12f)

    LaunchedEffect(isFirst) {
        if (isFirst) runCatching { focusRequester.requestFocus() }
    }

    Column(
        modifier = Modifier
            .scale(scale)
            .width(cardW)
            .height(cardH)
            .clip(RoundedCornerShape(shapeRadius.dp))
            .background(if (focused) Color(0xFF1A2336).copy(alpha = 0.92f) else Color(0xFF101624).copy(alpha = 0.78f))
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(shapeRadius.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged {
                if (it.isFocused && !focused) onFocusMove()
                focused = it.isFocused
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.ButtonA || event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onLaunch()
                    true
                } else false
            }
            .clickable { onLaunch() }
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.name,
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape((shapeRadius * 0.85f).toInt().dp))
        )
        if (showLabel) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = app.name,
                color = if (focused) Color.White else Color.LightGray,
                fontSize = 14.sp,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ActionTile(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isFirst: Boolean,
    accentColor: Color,
    largeMode: Boolean,
    showLabel: Boolean,
    shapeRadius: Int,
    glowPulse: Boolean,
    onFocusMove: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val scale by animateFloatAsState(targetValue = if (focused) 1.08f else 1.0f, label = "actionScale")

    val pulseTransition = rememberInfiniteTransition(label = "pulseAction")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseActionAlpha"
    )

    val cardW = if (largeMode) 190.dp else 156.dp
    val cardH = if (largeMode) 215.dp else 182.dp
    val borderColor = if (focused) accentColor.copy(alpha = if (glowPulse) pulseAlpha else 1f) else Color.White.copy(alpha = 0.1f)

    LaunchedEffect(isFirst) {
        if (isFirst) runCatching { focusRequester.requestFocus() }
    }

    Column(
        modifier = Modifier
            .scale(scale)
            .width(cardW)
            .height(cardH)
            .clip(RoundedCornerShape(shapeRadius.dp))
            .background(if (focused) Color(0xFF1A2336).copy(alpha = 0.9f) else Color(0xFF101624).copy(alpha = 0.62f))
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(shapeRadius.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged {
                if (it.isFocused && !focused) onFocusMove()
                focused = it.isFocused
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.ButtonA || event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else false
            }
            .clickable { onClick() }
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (focused) accentColor else Color.White.copy(alpha = 0.75f),
            modifier = Modifier.size(40.dp)
        )
        if (showLabel) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.78f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ConsoleBottomHints() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "D-Pad: Выбор   •   A / Нажатие: Запуск   •   [+]: Добавить игры   •   ⚙: Настройки и обои",
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun AppPickerDialog(
    apps: List<AppItem>,
    selected: Set<String>,
    accentColor: Color,
    onToggle: (String) -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Color(0xFF151C2C),
        title = {
            Text("Выбор эмуляторов и игр", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            LazyColumn(modifier = Modifier.height(230.dp)) {
                items(apps) { app ->
                    val checked = app.packageName in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(app.packageName) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = accentColor)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Image(
                            bitmap = app.icon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(app.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text("Готово", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun ConsoleSettingsDialog(
    consoleTitle: String,
    onTitleChange: (String) -> Unit,
    hasCustomWallpaper: Boolean,
    onPickGalleryWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    wallpaperDim: Float,
    onWallpaperDimChange: (Float) -> Unit,
    wallpaperStyle: WallpaperStyle,
    onWallpaperStyleChange: (WallpaperStyle) -> Unit,
    consoleTheme: ConsoleTheme,
    onThemeChange: (ConsoleTheme) -> Unit,
    layoutStyle: LayoutStyle,
    onLayoutStyleChange: (LayoutStyle) -> Unit,
    cardShape: CardShapeStyle,
    onCardShapeChange: (CardShapeStyle) -> Unit,
    soundScheme: SoundScheme,
    onSoundSchemeChange: (SoundScheme) -> Unit,
    vibrationEnabled: Boolean,
    onVibrationChange: (Boolean) -> Unit,
    largeCards: Boolean,
    onLargeCardsChange: (Boolean) -> Unit,
    showLabels: Boolean,
    onShowLabelsChange: (Boolean) -> Unit,
    showBottomHints: Boolean,
    onShowBottomHintsChange: (Boolean) -> Unit,
    glowPulse: Boolean,
    onGlowPulseChange: (Boolean) -> Unit,
    onSetDefaultLauncher: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Color(0xFF151C2C),
        title = {
            Text("Настройки и кастомизация", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(265.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text("Обои из галереи / скриншоты", color = consoleTheme.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onPickGalleryWallpaper,
                            colors = ButtonDefaults.buttonColors(containerColor = consoleTheme.accent)
                        ) {
                            Text("Выбрать фото", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        if (hasCustomWallpaper) {
                            OutlinedButton(
                                onClick = onClearWallpaper,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Сбросить", color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                    if (hasCustomWallpaper) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Затемнение фона: ${(wallpaperDim * 100).toInt()}%", color = Color.LightGray, fontSize = 12.sp)
                        Slider(
                            value = wallpaperDim,
                            onValueChange = onWallpaperDimChange,
                            valueRange = 0f..0.85f,
                            colors = SliderDefaults.colors(thumbColor = consoleTheme.accent, activeTrackColor = consoleTheme.accent)
                        )
                    }
                }

                item {
                    Text("Анимация поверх фона", color = consoleTheme.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    WallpaperStyle.entries.forEach { style ->
                        RadioOptionRow(style.title, wallpaperStyle == style, consoleTheme.accent) { onWallpaperStyleChange(style) }
                    }
                }

                item {
                    Text("Цветовая подсветка темы", color = consoleTheme.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ConsoleTheme.entries.forEach { theme ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(theme.accent)
                                    .border(if (consoleTheme == theme) 3.dp else 0.dp, Color.White, CircleShape)
                                    .clickable { onThemeChange(theme) }
                            )
                        }
                    }
                }

                item {
                    Text("Вид главного экрана", color = consoleTheme.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    LayoutStyle.entries.forEach { style ->
                        RadioOptionRow(style.title, layoutStyle == style, consoleTheme.accent) { onLayoutStyleChange(style) }
                    }
                }

                item {
                    Text("Форма карточек", color = consoleTheme.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    CardShapeStyle.entries.forEach { shape ->
                        RadioOptionRow(shape.title, cardShape == shape, consoleTheme.accent) { onCardShapeChange(shape) }
                    }
                }

                item {
                    Text("Звуковая тема меню", color = consoleTheme.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    SoundScheme.entries.forEach { scheme ->
                        RadioOptionRow(scheme.title, soundScheme == scheme, consoleTheme.accent) { onSoundSchemeChange(scheme) }
                    }
                }

                item {
                    Text("Название консоли в углу", color = consoleTheme.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = consoleTitle,
                        onValueChange = onTitleChange,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = consoleTheme.accent,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                }

                item {
                    Text("Дополнительные функции", color = consoleTheme.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    SettingSwitchRow("Пульсация активной рамки", glowPulse, consoleTheme.accent, onGlowPulseChange)
                    SettingSwitchRow("Крупные карточки", largeCards, consoleTheme.accent, onLargeCardsChange)
                    SettingSwitchRow("Показывать названия игр", showLabels, consoleTheme.accent, onShowLabelsChange)
                    SettingSwitchRow("Нижняя панель подсказок", showBottomHints, consoleTheme.accent, onShowBottomHintsChange)
                    SettingSwitchRow("Виброотклик геймпада", vibrationEnabled, consoleTheme.accent, onVibrationChange)
                }

                item {
                    OutlinedButton(
                        onClick = onSetDefaultLauncher,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, consoleTheme.accent)
                    ) {
                        Text("Назначить рабочим столом (Кнопка Домой)", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = consoleTheme.accent)
            ) {
                Text("Готово", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun RadioOptionRow(title: String, selected: Boolean, accentColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = accentColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
fun SettingSwitchRow(title: String, checked: Boolean, accentColor: Color, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
        )
    }
}

fun saveWallpaperToInternalStorage(context: Context, uri: Uri): Bitmap? {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bitmap = BitmapFactory.decodeStream(input) ?: return null
            val outFile = File(context.filesDir, "custom_wallpaper.jpg")
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            bitmap
        }
    }.getOrNull()
}

fun loadSavedWallpaper(context: Context): Bitmap? {
    return runCatching {
        val file = File(context.filesDir, "custom_wallpaper.jpg")
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }.getOrNull()
}

fun deleteSavedWallpaper(context: Context) {
    runCatching {
        val file = File(context.filesDir, "custom_wallpaper.jpg")
        if (file.exists()) file.delete()
    }
}

fun fetchInstalledApps(context: Context): List<AppItem> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    return pm.queryIntentActivities(intent, 0).mapNotNull { info ->
        val pkg = info.activityInfo.packageName
        if (pkg == context.packageName) return@mapNotNull null
        val name = info.loadLabel(pm).toString()
        val icon = drawableToBitmap(info.loadIcon(pm)).asImageBitmap()
        AppItem(name, pkg, icon)
    }.distinctBy { it.packageName }.sortedBy { it.name.lowercase() }
}

fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 120
    val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 120
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

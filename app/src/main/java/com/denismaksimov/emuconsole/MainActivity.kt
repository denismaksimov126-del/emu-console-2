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
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
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
import androidx.compose.ui.graphics.vector.ImageVector
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
    STATIC("Чистый фон Switch (без анимации)"),
    SINGLE_WAVE("Волна PlayStation"),
    XMB_DOUBLE("Двойная волна XMB"),
    PARTICLES("Звёздная пыль PS3"),
    CYBER_GRID("Неоновая сетка")
}

enum class ConsoleTheme(
    val title: String,
    val accent: Color,
    val bgTop: Color,
    val bgMid: Color,
    val isLight: Boolean
) {
    SWITCH_DARK("Switch Базовая чёрная", Color(0xFF00E5D3), Color(0xFF2D2D2D), Color(0xFF222222), false),
    SWITCH_LIGHT("Switch Базовая белая", Color(0xFF0055FF), Color(0xFFEBEBEB), Color(0xFFDFDFDF), true),
    PS_BLUE("PlayStation Blue", Color(0xFF38BDF8), Color(0xFF0A0E17), Color(0xFF131B2E), false),
    PSP_GOLD("PSP Gold", Color(0xFFFBBF24), Color(0xFF171207), Color(0xFF2E230D), false),
    EMERALD("Xbox Emerald", Color(0xFF34D399), Color(0xFF061510), Color(0xFF0D2B21), false),
    CRIMSON("Crimson Red", Color(0xFFF87171), Color(0xFF180808), Color(0xFF2E1010), false),
    CYBER_PURPLE("Cyberpunk Neon", Color(0xFFC084FC), Color(0xFF12081C), Color(0xFF241038), false)
}

enum class SoundScheme(val title: String) {
    SWITCH_CLICK("Nintendo Switch Клик"),
    PSP_CLASSIC("PSP Классика"),
    RETRO_8BIT("Ретро 8-Бит"),
    SOFT_CLICK("Мягкий клик"),
    OFF("Без звука")
}

enum class CardShapeStyle(val title: String, val radiusDp: Int) {
    SWITCH_SQUARE("Switch Квадрат", 10),
    ROUNDED("Скруглённые", 22),
    SHARP("Острые углы", 4)
}

enum class LayoutStyle(val title: String) {
    CAROUSEL("Лента Nintendo Switch"),
    GRID("Сетка (Steam Deck / Vita)")
}

class ConsoleSoundPlayer {
    private var toneGen: ToneGenerator? = null

    init {
        runCatching {
            toneGen = ToneGenerator(AudioManager.STREAM_SYSTEM, 75)
        }
    }

    fun playMove(scheme: SoundScheme) {
        if (scheme == SoundScheme.OFF) return
        val (tone, duration) = when (scheme) {
            SoundScheme.SWITCH_CLICK -> ToneGenerator.TONE_PROP_NACK to 22
            SoundScheme.PSP_CLASSIC -> ToneGenerator.TONE_PROP_BEEP to 35
            SoundScheme.RETRO_8BIT -> ToneGenerator.TONE_DTMF_1 to 35
            SoundScheme.SOFT_CLICK -> ToneGenerator.TONE_PROP_NACK to 30
            SoundScheme.OFF -> return
        }
        runCatching { toneGen?.startTone(tone, duration) }
    }

    fun playSelect(scheme: SoundScheme) {
        if (scheme == SoundScheme.OFF) return
        val (tone, duration) = when (scheme) {
            SoundScheme.SWITCH_CLICK -> ToneGenerator.TONE_PROP_BEEP2 to 55
            SoundScheme.PSP_CLASSIC -> ToneGenerator.TONE_PROP_ACK to 95
            SoundScheme.RETRO_8BIT -> ToneGenerator.TONE_DTMF_D to 95
            SoundScheme.SOFT_CLICK -> ToneGenerator.TONE_PROP_BEEP2 to 75
            SoundScheme.OFF -> return
        }
        runCatching { toneGen?.startTone(tone, duration) }
    }

    fun playOpenMenu(scheme: SoundScheme) {
        if (scheme == SoundScheme.OFF) return
        val (tone, duration) = when (scheme) {
            SoundScheme.SWITCH_CLICK -> ToneGenerator.TONE_PROP_BEEP to 40
            SoundScheme.PSP_CLASSIC -> ToneGenerator.TONE_PROP_BEEP2 to 65
            SoundScheme.RETRO_8BIT -> ToneGenerator.TONE_DTMF_5 to 65
            SoundScheme.SOFT_CLICK -> ToneGenerator.TONE_PROP_BEEP to 55
            SoundScheme.OFF -> return
        }
        runCatching { toneGen?.startTone(tone, duration) }
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

    var consoleTitle by remember { mutableStateOf(prefs.getString("console_title", "EMO") ?: "EMO") }
    var vibrationEnabled by remember { mutableStateOf(prefs.getBoolean("vibration_enabled", true)) }
    var largeCards by remember { mutableStateOf(prefs.getBoolean("large_cards", false)) }
    var showLabels by remember { mutableStateOf(prefs.getBoolean("show_labels", true)) }
    var glowPulse by remember { mutableStateOf(prefs.getBoolean("glow_pulse", true)) }
    var wallpaperDim by remember { mutableFloatStateOf(prefs.getFloat("wallpaper_dim", 0.35f)) }

    var soundScheme by remember {
        mutableStateOf(SoundScheme.entries.getOrElse(prefs.getInt("sound_scheme", 0)) { SoundScheme.SWITCH_CLICK })
    }
    var wallpaperStyle by remember {
        mutableStateOf(WallpaperStyle.entries.getOrElse(prefs.getInt("wallpaper_style", 0)) { WallpaperStyle.STATIC })
    }
    var consoleTheme by remember {
        mutableStateOf(ConsoleTheme.entries.getOrElse(prefs.getInt("console_theme", 0)) { ConsoleTheme.SWITCH_DARK })
    }
    var cardShape by remember {
        mutableStateOf(CardShapeStyle.entries.getOrElse(prefs.getInt("card_shape", 0)) { CardShapeStyle.SWITCH_SQUARE })
    }
    var layoutStyle by remember {
        mutableStateOf(LayoutStyle.entries.getOrElse(prefs.getInt("layout_style", 0)) { LayoutStyle.CAROUSEL })
    }

    var customWallpaper by remember { mutableStateOf<ImageBitmap?>(null) }
    var showSelectorDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isSleepMode by remember { mutableStateOf(false) }

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

    val isLight = consoleTheme.isLight
    val textColor = if (isLight) Color(0xFF1F1F1F) else Color.White
    val subTextColor = if (isLight) Color(0xFF555555) else Color.White.copy(alpha = 0.72f)
    val tileSurfaceColor = if (isLight) Color(0xFFFFFFFF) else Color(0xFF383838)
    val circleButtonBg = if (isLight) Color(0xFFFFFFFF) else Color(0xFF3A3A3A)
    val dividerColor = if (isLight) Color(0xFFCCCCCC) else Color.White.copy(alpha = 0.22f)

    val animBgTop by animateColorAsState(targetValue = consoleTheme.bgTop, animationSpec = tween(400), label = "bgTop")
    val animBgMid by animateColorAsState(targetValue = consoleTheme.bgMid, animationSpec = tween(400), label = "bgMid")
    val animAccent by animateColorAsState(targetValue = consoleTheme.accent, animationSpec = tween(400), label = "accent")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(animBgTop, animBgMid)
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
                    .background((if (isLight) Color.White else Color.Black).copy(alpha = wallpaperDim))
            )
        }

        LiveWallpaperLayer(style = wallpaperStyle, accentColor = animAccent)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            ConsoleTopBar(
                title = consoleTitle,
                accentColor = animAccent,
                textColor = textColor,
                isLight = isLight,
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
                        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        contentPadding = PaddingValues(horizontal = 48.dp)
                    ) {
                        itemsIndexed(myEmulators) { index, app ->
                            EmulatorTile(
                                app = app,
                                isFirst = index == 0,
                                accentColor = animAccent,
                                tileBg = tileSurfaceColor,
                                textColor = textColor,
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
                                tileBg = tileSurfaceColor,
                                textColor = textColor,
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
                                tileBg = tileSurfaceColor,
                                textColor = textColor,
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
                        columns = GridCells.Adaptive(minSize = if (largeCards) 168.dp else 138.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        gridItemsIndexed(myEmulators) { index, app ->
                            EmulatorTile(
                                app = app,
                                isFirst = index == 0,
                                accentColor = animAccent,
                                tileBg = tileSurfaceColor,
                                textColor = textColor,
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
                                tileBg = tileSurfaceColor,
                                textColor = textColor,
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
                                tileBg = tileSurfaceColor,
                                textColor = textColor,
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

            SwitchBottomDock(
                accentColor = animAccent,
                buttonBg = circleButtonBg,
                iconTint = textColor,
                labelColor = subTextColor,
                dividerColor = dividerColor,
                glowPulse = glowPulse,
                onFocusMove = {
                    soundPlayer.playMove(soundScheme)
                    if (vibrationEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                onAddApps = {
                    soundPlayer.playOpenMenu(soundScheme)
                    showSelectorDialog = true
                },
                onOpenAlbum = {
                    soundPlayer.playOpenMenu(soundScheme)
                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onOpenControllers = {
                    soundPlayer.playOpenMenu(soundScheme)
                    runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                },
                onOpenSettings = {
                    soundPlayer.playOpenMenu(soundScheme)
                    showSettingsDialog = true
                },
                onToggleTheme = {
                    val nextTheme = if (consoleTheme == ConsoleTheme.SWITCH_DARK) ConsoleTheme.SWITCH_LIGHT else ConsoleTheme.SWITCH_DARK
                    consoleTheme = nextTheme
                    prefs.edit().putInt("console_theme", nextTheme.ordinal).apply()
                    soundPlayer.playSelect(soundScheme)
                },
                onSleep = {
                    soundPlayer.playSelect(soundScheme)
                    isSleepMode = true
                }
            )
        }

        if (isSleepMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .focusable()
                    .onKeyEvent {
                        if (it.type == KeyEventType.KeyUp) {
                            soundPlayer.playMove(soundScheme)
                            isSleepMode = false
                            true
                        } else false
                    }
                    .clickable {
                        soundPlayer.playMove(soundScheme)
                        isSleepMode = false
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.DarkMode,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Режим ожидания • Нажмите, чтобы продолжить",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 14.sp
                    )
                }
            }
        }

        if (showSelectorDialog) {
            AppPickerDialog(
                apps = allApps,
                selected = selectedPackages,
                accentColor = animAccent,
                isLight = isLight,
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
                    soundPlayer.playSelect(soundScheme)
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
fun SwitchBottomDock(
    accentColor: Color,
    buttonBg: Color,
    iconTint: Color,
    labelColor: Color,
    dividerColor: Color,
    glowPulse: Boolean,
    onFocusMove: () -> Unit,
    onAddApps: () -> Unit,
    onOpenAlbum: () -> Unit,
    onOpenControllers: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTheme: () -> Unit,
    onSleep: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SwitchCircleAction(
                label = "Добавить игры",
                icon = Icons.Default.Add,
                accentColor = accentColor,
                buttonBg = buttonBg,
                iconTint = iconTint,
                labelColor = labelColor,
                glowPulse = glowPulse,
                onFocusMove = onFocusMove,
                onClick = onAddApps
            )
            SwitchCircleAction(
                label = "Альбом",
                icon = Icons.Default.PhotoLibrary,
                accentColor = accentColor,
                buttonBg = buttonBg,
                iconTint = iconTint,
                labelColor = labelColor,
                glowPulse = glowPulse,
                onFocusMove = onFocusMove,
                onClick = onOpenAlbum
            )
            SwitchCircleAction(
                label = "Контроллеры",
                icon = Icons.Default.SportsEsports,
                accentColor = accentColor,
                buttonBg = buttonBg,
                iconTint = iconTint,
                labelColor = labelColor,
                glowPulse = glowPulse,
                onFocusMove = onFocusMove,
                onClick = onOpenControllers
            )
            SwitchCircleAction(
                label = "Системные настройки",
                icon = Icons.Default.Settings,
                accentColor = accentColor,
                buttonBg = buttonBg,
                iconTint = iconTint,
                labelColor = labelColor,
                glowPulse = glowPulse,
                onFocusMove = onFocusMove,
                onClick = onOpenSettings
            )
            SwitchCircleAction(
                label = "Чёрная / Белая тема",
                icon = Icons.Default.Contrast,
                accentColor = accentColor,
                buttonBg = buttonBg,
                iconTint = iconTint,
                labelColor = labelColor,
                glowPulse = glowPulse,
                onFocusMove = onFocusMove,
                onClick = onToggleTheme
            )
            SwitchCircleAction(
                label = "Режим ожидания",
                icon = Icons.Default.DarkMode,
                accentColor = accentColor,
                buttonBg = buttonBg,
                iconTint = iconTint,
                labelColor = labelColor,
                glowPulse = glowPulse,
                onFocusMove = onFocusMove,
                onClick = onSleep
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = dividerColor, thickness = 1.dp)
    }
}

@Composable
fun SwitchCircleAction(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    buttonBg: Color,
    iconTint: Color,
    labelColor: Color,
    glowPulse: Boolean,
    onFocusMove: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.12f else 1.0f, label = "circleScale")

    val pulseTransition = rememberInfiniteTransition(label = "circlePulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "circlePulseAlpha"
    )

    val borderColor = if (focused) accentColor.copy(alpha = if (glowPulse) pulseAlpha else 1f) else Color.Transparent

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(86.dp)
    ) {
        Box(
            modifier = Modifier
                .scale(scale)
                .size(54.dp)
                .clip(CircleShape)
                .background(buttonBg)
                .border(
                    width = if (focused) 3.dp else 1.dp,
                    color = if (focused) borderColor else iconTint.copy(alpha = 0.14f),
                    shape = CircleShape
                )
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
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (focused) accentColor else iconTint.copy(alpha = 0.85f),
                modifier = Modifier.size(25.dp)
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = if (focused) label else "",
            color = if (focused) accentColor else labelColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
fun ConsoleTopBar(
    title: String,
    accentColor: Color,
    textColor: Color,
    isLight: Boolean,
    onOpenSettings: () -> Unit
) {
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
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE60012))
                    .border(2.dp, if (isLight) Color.White else Color.White.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title.trim().firstOrNull()?.uppercase() ?: "E",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title.uppercase().ifBlank { "EMO" },
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = time,
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .border(1.5.dp, textColor.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "$battery%",
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(36.dp)
                    .background(textColor.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Настройки",
                    tint = textColor,
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
    tileBg: Color,
    textColor: Color,
    largeMode: Boolean,
    showLabel: Boolean,
    shapeRadius: Int,
    glowPulse: Boolean,
    onFocusMove: () -> Unit,
    onLaunch: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val scale by animateFloatAsState(targetValue = if (focused) 1.06f else 1.0f, label = "tileScale")

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    val tileSize = if (largeMode) 172.dp else 146.dp
    val borderColor = if (focused) accentColor.copy(alpha = if (glowPulse) pulseAlpha else 1f) else Color.Transparent

    LaunchedEffect(isFirst) {
        if (isFirst) runCatching { focusRequester.requestFocus() }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(tileSize + 14.dp)
    ) {
        Box(modifier = Modifier.height(22.dp), contentAlignment = Alignment.Center) {
            if (showLabel && focused) {
                Text(
                    text = app.name,
                    color = accentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .scale(scale)
                .size(tileSize + 10.dp)
                .border(
                    width = if (focused) 3.5.dp else 0.dp,
                    color = borderColor,
                    shape = RoundedCornerShape((shapeRadius + 4).dp)
                )
                .padding(5.dp)
                .clip(RoundedCornerShape(shapeRadius.dp))
                .background(tileBg)
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
                .clickable { onLaunch() },
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = app.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun ActionTile(
    title: String,
    icon: ImageVector,
    isFirst: Boolean,
    accentColor: Color,
    tileBg: Color,
    textColor: Color,
    largeMode: Boolean,
    showLabel: Boolean,
    shapeRadius: Int,
    glowPulse: Boolean,
    onFocusMove: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val scale by animateFloatAsState(targetValue = if (focused) 1.06f else 1.0f, label = "actionScale")

    val pulseTransition = rememberInfiniteTransition(label = "pulseAction")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseActionAlpha"
    )

    val tileSize = if (largeMode) 172.dp else 146.dp
    val borderColor = if (focused) accentColor.copy(alpha = if (glowPulse) pulseAlpha else 1f) else Color.Transparent

    LaunchedEffect(isFirst) {
        if (isFirst) runCatching { focusRequester.requestFocus() }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(tileSize + 14.dp)
    ) {
        Box(modifier = Modifier.height(22.dp), contentAlignment = Alignment.Center) {
            if (showLabel && focused) {
                Text(
                    text = title,
                    color = accentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .scale(scale)
                .size(tileSize + 10.dp)
                .border(
                    width = if (focused) 3.5.dp else 1.dp,
                    color = if (focused) borderColor else textColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape((shapeRadius + 4).dp)
                )
                .padding(5.dp)
                .clip(RoundedCornerShape(shapeRadius.dp))
                .background(tileBg)
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
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (focused) accentColor else textColor.copy(alpha = 0.72f),
                modifier = Modifier.size(42.dp)
            )
        }
    }
}

@Composable
fun AppPickerDialog(
    apps: List<AppItem>,
    selected: Set<String>,
    accentColor: Color,
    isLight: Boolean,
    onToggle: (String) -> Unit,
    onClose: () -> Unit
) {
    val dialogBg = if (isLight) Color(0xFFF5F5F5) else Color(0xFF262626)
    val itemTextColor = if (isLight) Color(0xFF1A1A1A) else Color.White

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = dialogBg,
        title = {
            Text("Выбор программ и эмуляторов", color = itemTextColor, fontWeight = FontWeight.Bold)
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
                        Text(app.name, color = itemTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text("Готово", color = if (isLight) Color.White else Color.Black, fontWeight = FontWeight.Bold)
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
    glowPulse: Boolean,
    onGlowPulseChange: (Boolean) -> Unit,
    onSetDefaultLauncher: () -> Unit,
    onClose: () -> Unit
) {
    val isLight = consoleTheme.isLight
    val dialogBg = if (isLight) Color(0xFFF5F5F5) else Color(0xFF262626)
    val itemTextColor = if (isLight) Color(0xFF1A1A1A) else Color.White

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = dialogBg,
        title = {
            Text("Настройки системы", color = itemTextColor, fontWeight = FontWeight.Bold)
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(275.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text("Тема Nintendo Switch", color = consoleTheme.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { onThemeChange(ConsoleTheme.SWITCH_DARK) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (consoleTheme == ConsoleTheme.SWITCH_DARK) Color(0xFF2D2D2D) else Color.Transparent
                            ),
                            border = BorderStroke(
                                width = if (consoleTheme == ConsoleTheme.SWITCH_DARK) 2.5.dp else 1.dp,
                                color = if (consoleTheme == ConsoleTheme.SWITCH_DARK) ConsoleTheme.SWITCH_DARK.accent else itemTextColor.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                "Базовая чёрная",
                                color = if (consoleTheme == ConsoleTheme.SWITCH_DARK) Color.White else itemTextColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = { onThemeChange(ConsoleTheme.SWITCH_LIGHT) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (consoleTheme == ConsoleTheme.SWITCH_LIGHT) Color(0xFFEBEBEB) else Color.Transparent
                            ),
                            border = BorderStroke(
                                width = if (consoleTheme == ConsoleTheme.SWITCH_LIGHT) 2.5.dp else 1.dp,
                                color = if (consoleTheme == ConsoleTheme.SWITCH_LIGHT) ConsoleTheme.SWITCH_LIGHT.accent else itemTextColor.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                "Базовая белая",
                                color = if (consoleTheme == ConsoleTheme.SWITCH_LIGHT) Color(0xFF1A1A1A) else itemTextColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                item {
                    Text("Другие цветовые темы", color = consoleTheme.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ConsoleTheme.entries.forEach { theme ->
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(if (theme == ConsoleTheme.SWITCH_LIGHT) Color.White else theme.accent)
                                    .border(
                                        width = if (consoleTheme == theme) 3.dp else 1.dp,
                                        color = if (consoleTheme == theme) consoleTheme.accent else itemTextColor.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    )
                                    .clickable { onThemeChange(theme) }
                            )
                        }
                    }
                }

                item {
                    Text("Обои из галереи / скриншоты", color = consoleTheme.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onPickGalleryWallpaper,
                            colors = ButtonDefaults.buttonColors(containerColor = consoleTheme.accent)
                        ) {
                            Text("Выбрать фото", color = if (isLight) Color.White else Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        if (hasCustomWallpaper) {
                            OutlinedButton(
                                onClick = onClearWallpaper,
                                border = BorderStroke(1.dp, itemTextColor.copy(alpha = 0.4f))
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = itemTextColor, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Сбросить", color = itemTextColor, fontSize = 13.sp)
                            }
                        }
                    }
                    if (hasCustomWallpaper) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Затемнение фона: ${(wallpaperDim * 100).toInt()}%", color = itemTextColor.copy(alpha = 0.75f), fontSize = 12.sp)
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
                        RadioOptionRow(style.title, wallpaperStyle == style, consoleTheme.accent, itemTextColor) { onWallpaperStyleChange(style) }
                    }
                }

                item {
                    Text("Вид главного экрана", color = consoleTheme.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    LayoutStyle.entries.forEach { style ->
                        RadioOptionRow(style.title, layoutStyle == style, consoleTheme.accent, itemTextColor) { onLayoutStyleChange(style) }
                    }
                }

                item {
                    Text("Форма карточек", color = consoleTheme.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    CardShapeStyle.entries.forEach { shape ->
                        RadioOptionRow(shape.title, cardShape == shape, consoleTheme.accent, itemTextColor) { onCardShapeChange(shape) }
                    }
                }

                item {
                    Text("Звуковая тема меню", color = consoleTheme.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    SoundScheme.entries.forEach { scheme ->
                        RadioOptionRow(scheme.title, soundScheme == scheme, consoleTheme.accent, itemTextColor) { onSoundSchemeChange(scheme) }
                    }
                }

                item {
                    Text("Имя пользователя / консоли", color = consoleTheme.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = consoleTitle,
                        onValueChange = onTitleChange,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = itemTextColor,
                            unfocusedTextColor = itemTextColor,
                            focusedBorderColor = consoleTheme.accent,
                            unfocusedBorderColor = itemTextColor.copy(alpha = 0.3f)
                        )
                    )
                }

                item {
                    Text("Дополнительные функции", color = consoleTheme.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    SettingSwitchRow("Пульсация активной рамки", glowPulse, consoleTheme.accent, itemTextColor, onGlowPulseChange)
                    SettingSwitchRow("Крупные карточки", largeCards, consoleTheme.accent, itemTextColor, onLargeCardsChange)
                    SettingSwitchRow("Показывать название над игрой", showLabels, consoleTheme.accent, itemTextColor, onShowLabelsChange)
                    SettingSwitchRow("Виброотклик геймпада", vibrationEnabled, consoleTheme.accent, itemTextColor, onVibrationChange)
                }

                item {
                    OutlinedButton(
                        onClick = onSetDefaultLauncher,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, consoleTheme.accent)
                    ) {
                        Text("Назначить рабочим столом (Кнопка Домой)", color = itemTextColor, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = consoleTheme.accent)
            ) {
                Text("Готово", color = if (isLight) Color.White else Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun RadioOptionRow(title: String, selected: Boolean, accentColor: Color, textColor: Color, onClick: () -> Unit) {
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
            colors = RadioButtonDefaults.colors(selectedColor = accentColor, unselectedColor = textColor.copy(alpha = 0.6f))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, color = textColor, fontSize = 14.sp)
    }
}

@Composable
fun SettingSwitchRow(title: String, checked: Boolean, accentColor: Color, textColor: Color, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = textColor, fontSize = 14.sp)
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

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
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.onKeyEvent
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
import kotlin.math.cos
import kotlin.math.sin

data class AppItem(
    val name: String,
    val packageName: String,
    val icon: ImageBitmap
)

enum class WallpaperStyle(val title: String) {
    STATIC("Только фото / чистый фон Switch"),
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
    val isLight: Boolean = false
) {
    SWITCH_DARK("Switch: Базовая чёрная", Color(0xFF00E5FF), Color(0xFF2D2D2D), Color(0xFF262626), false),
    SWITCH_LIGHT("Switch: Базовая белая", Color(0xFF0084FF), Color(0xFFEBEBEB), Color(0xFFE0E0E0), true),
    PS_BLUE("PlayStation Blue", Color(0xFF38BDF8), Color(0xFF0A0E17), Color(0xFF131B2E), false),
    PSP_GOLD("PSP Gold", Color(0xFFFBBF24), Color(0xFF171207), Color(0xFF2E230D), false),
    EMERALD("Xbox Emerald", Color(0xFF34D399), Color(0xFF061510), Color(0xFF0D2B21), false),
    CRIMSON("Crimson Red", Color(0xFFF87171), Color(0xFF180808), Color(0xFF2E1010), false),
    CYBER_PURPLE("Cyberpunk Neon", Color(0xFFC084FC), Color(0xFF12081C), Color(0xFF241038), false),
    NOTHING_WHITE("Nothing Monochrome", Color(0xFFF8FAFC), Color(0xFF09090B), Color(0xFF18181B), false)
}

enum class SoundScheme(val title: String) {
    SWITCH_CLICK("Nintendo Switch Клик"),
    PSP_CLASSIC("PSP Классика"),
    RETRO_8BIT("Ретро 8-Бит"),
    SOFT_CLICK("Мягкий клик"),
    OFF("Без звука")
}

enum class CardShapeStyle(val title: String, val radiusDp: Int) {
    SWITCH_SQUARE("Switch Квадрат", 6),
    ROUNDED("Скруглённые", 22),
    PILL("Мягкий овал", 34)
}

enum class LayoutStyle(val title: String) {
    CAROUSEL("Лента (Nintendo Switch)"),
    GRID("Сетка (Steam Deck / Vita)")
}

enum class ProfileAvatarIcon(val label: String) {
    JOYCON("Джойкон"),
    STAR("Звезда"),
    HEART("Сердце"),
    BOLT("Молния"),
    CROWN("Корона"),
    SHIELD("Щит")
}

class ConsoleSoundPlayer {
    private var toneGen: ToneGenerator? = null

    init {
        runCatching { toneGen = ToneGenerator(AudioManager.STREAM_SYSTEM, 75) }
    }

    fun playMove(scheme: SoundScheme) {
        if (scheme == SoundScheme.OFF) return
        val (tone, duration) = when (scheme) {
            SoundScheme.SWITCH_CLICK -> ToneGenerator.TONE_PROP_NACK to 25
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
            SoundScheme.SOFT_CLICK -> ToneGenerator.TONE_PROP_BEEP2 to 80
            SoundScheme.OFF -> return
        }
        runCatching { toneGen?.startTone(tone, duration) }
    }

    fun playOpenMenu(scheme: SoundScheme) {
        if (scheme == SoundScheme.OFF) return
        val (tone, duration) = when (scheme) {
            SoundScheme.SWITCH_CLICK -> ToneGenerator.TONE_PROP_BEEP to 45
            SoundScheme.PSP_CLASSIC -> ToneGenerator.TONE_PROP_BEEP2 to 65
            SoundScheme.RETRO_8BIT -> ToneGenerator.TONE_DTMF_5 to 65
            SoundScheme.SOFT_CLICK -> ToneGenerator.TONE_PROP_BEEP to 60
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
                SwitchLauncherScreen()
            }
        }
    }
}

@Composable
fun SwitchLauncherScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("emu_console_prefs", Context.MODE_PRIVATE) }
    val soundPlayer = remember { ConsoleSoundPlayer() }

    DisposableEffect(Unit) { onDispose { soundPlayer.release() } }

    var allInstalledApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var pinnedPackages by remember {
        val saved = prefs.getString("pinned_apps", "") ?: ""
        val legacySet = prefs.getStringSet("saved_emulators", emptySet()) ?: emptySet()
        val list = if (saved.isNotBlank()) {
            saved.split(",").filter { it.isNotBlank() }
        } else {
            legacySet.toList()
        }
        mutableStateOf(list)
    }

    var userName by remember { mutableStateOf(prefs.getString("profile_name", "EMO Player") ?: "EMO Player") }
    var avatarColorHex by remember { mutableIntStateOf(prefs.getInt("profile_color", Color(0xFFE60012).toArgb())) }
    var avatarIcon by remember {
        val savedIcon = prefs.getString("profile_icon", ProfileAvatarIcon.JOYCON.name)
        mutableStateOf(runCatching { ProfileAvatarIcon.valueOf(savedIcon ?: ProfileAvatarIcon.JOYCON.name) }.getOrDefault(ProfileAvatarIcon.JOYCON))
    }
    var useCustomAvatar by remember { mutableStateOf(prefs.getBoolean("use_custom_avatar", false)) }
    var customAvatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

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

    var wallpaperBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    var focusRow by remember { mutableIntStateOf(0) }
    var selectedGameIndex by remember { mutableIntStateOf(0) }
    var selectedSysBtnIndex by remember { mutableIntStateOf(0) }

    var showAddAppsDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showSleepOverlay by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    fun savePinned(list: List<String>) {
        pinnedPackages = list
        prefs.edit()
            .putString("pinned_apps", list.joinToString(","))
            .putStringSet("saved_emulators", list.toSet())
            .apply()
    }

    fun feedbackMove() {
        soundPlayer.playMove(soundScheme)
        if (vibrationEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun feedbackSelect() {
        soundPlayer.playSelect(soundScheme)
        if (vibrationEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let {
            val bmp = saveUriToInternalFile(context, it, "custom_wallpaper.png", 1280)
            if (bmp != null) {
                wallpaperBitmap = bmp
                feedbackSelect()
            }
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let {
            val bmp = saveUriToInternalFile(context, it, "profile_avatar.png", 256)
            if (bmp != null) {
                customAvatarBitmap = bmp
                useCustomAvatar = true
                prefs.edit().putBoolean("use_custom_avatar", true).apply()
                feedbackSelect()
            }
        }
    }

    LaunchedEffect(Unit) {
        allInstalledApps = withContext(Dispatchers.IO) { fetchInstalledApps(context) }
        wallpaperBitmap = withContext(Dispatchers.IO) { loadSavedBitmap(context, "custom_wallpaper.png") }
        customAvatarBitmap = withContext(Dispatchers.IO) { loadSavedBitmap(context, "profile_avatar.png") }
        runCatching { focusRequester.requestFocus() }
    }

    val displayedApps = remember(allInstalledApps, pinnedPackages) {
        val map = allInstalledApps.associateBy { it.packageName }
        pinnedPackages.mapNotNull { map[it] }
    }

    LaunchedEffect(selectedGameIndex, displayedApps.size, layoutStyle) {
        if (layoutStyle == LayoutStyle.CAROUSEL && displayedApps.isNotEmpty() && selectedGameIndex in displayedApps.indices) {
            listState.animateScrollToItem(selectedGameIndex)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "switch_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(750, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse_alpha"
    )

    val animBgTop by animateColorAsState(consoleTheme.bgTop, tween(300), label = "bgTop")
    val animBgMid by animateColorAsState(consoleTheme.bgMid, tween(300), label = "bgMid")
    val animAccent by animateColorAsState(consoleTheme.accent, tween(300), label = "accent")
    val isLight = consoleTheme.isLight && wallpaperBitmap == null

    val primaryText = if (isLight) Color(0xFF1F1F1F) else Color.White
    val cardBg = if (isLight) Color.White else Color(0xFF3A3A3A)
    val idleBorder = if (isLight) Color.Black.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.14f)
    val dividerColor = if (isLight) Color.Black.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.22f)
    val dialogBg = if (isLight) Color(0xFFF5F5F5) else Color(0xFF262626)

    var currentTime by remember { mutableStateOf("") }
    var batteryLevel by remember { mutableIntStateOf(100) }

    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        while (true) {
            currentTime = sdf.format(Date())
            batteryLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
            delay(5000)
        }
    }

    fun triggerSystemAction(index: Int) {
        soundPlayer.playOpenMenu(soundScheme)
        when (index) {
            0 -> showAddAppsDialog = true
            1 -> wallpaperPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            2 -> runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            3 -> showSettingsDialog = true
            4 -> {
                val next = if (consoleTheme == ConsoleTheme.SWITCH_LIGHT) ConsoleTheme.SWITCH_DARK else ConsoleTheme.SWITCH_LIGHT
                consoleTheme = next
                prefs.edit().putInt("console_theme", next.ordinal).apply()
            }
            5 -> showSleepOverlay = true
        }
    }

    BackHandler(enabled = showSleepOverlay || showAddAppsDialog || showSettingsDialog || showProfileDialog) {
        when {
            showSleepOverlay -> showSleepOverlay = false
            showProfileDialog -> showProfileDialog = false
            showAddAppsDialog -> showAddAppsDialog = false
            showSettingsDialog -> showSettingsDialog = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(animBgTop, animBgMid, animBgMid)))
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                if (showSleepOverlay) {
                    showSleepOverlay = false
                    return@onKeyEvent true
                }
                if (showAddAppsDialog || showSettingsDialog || showProfileDialog) return@onKeyEvent false

                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (focusRow == 0) {
                            if (selectedGameIndex < displayedApps.size) {
                                selectedGameIndex++
                                feedbackMove()
                            }
                        } else if (selectedSysBtnIndex < 5) {
                            selectedSysBtnIndex++
                            feedbackMove()
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (focusRow == 0) {
                            if (selectedGameIndex > 0) {
                                selectedGameIndex--
                                feedbackMove()
                            }
                        } else if (selectedSysBtnIndex > 0) {
                            selectedSysBtnIndex--
                            feedbackMove()
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (focusRow == 0) {
                            focusRow = 1
                            feedbackMove()
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (focusRow == 1) {
                            focusRow = 0
                            feedbackMove()
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                        if (focusRow == 0) {
                            if (selectedGameIndex < displayedApps.size) {
                                feedbackSelect()
                                val app = displayedApps[selectedGameIndex]
                                context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { context.startActivity(it) }
                            } else {
                                soundPlayer.playOpenMenu(soundScheme)
                                showAddAppsDialog = true
                            }
                        } else {
                            triggerSystemAction(selectedSysBtnIndex)
                        }
                        true
                    }
                    KeyEvent.KEYCODE_BUTTON_START -> {
                        soundPlayer.playOpenMenu(soundScheme)
                        showSettingsDialog = true
                        true
                    }
                    else -> false
                }
            }
    ) {
        wallpaperBitmap?.let { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = null,
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
                .padding(horizontal = 32.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            soundPlayer.playOpenMenu(soundScheme)
                            showProfileDialog = true
                        }
                        .padding(end = 10.dp)
                ) {
                    ProfileAvatarCircle(
                        size = 42.dp,
                        bgColor = Color(avatarColorHex),
                        icon = avatarIcon,
                        customBitmap = if (useCustomAvatar) customAvatarBitmap else null,
                        borderColor = if (isLight) Color.White else Color.White.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = userName,
                        color = primaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentTime,
                        color = primaryText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "$batteryLevel%",
                        color = primaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    SwitchBatteryIcon(level = batteryLevel, color = primaryText)
                }
            }

            val tileSize = if (largeCards) 186.dp else 156.dp
            val effectiveAlpha = if (glowPulse) pulseAlpha else 1f

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (layoutStyle == LayoutStyle.CAROUSEL) {
                    LazyRow(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(displayedApps) { index, app ->
                            val isSelected = focusRow == 0 && selectedGameIndex == index
                            SwitchGameCard(
                                title = app.name,
                                icon = app.icon,
                                isSelected = isSelected,
                                tileSize = tileSize,
                                radiusDp = cardShape.radiusDp,
                                showLabel = showLabels,
                                accentColor = animAccent,
                                pulseAlpha = effectiveAlpha,
                                cardBg = cardBg,
                                idleBorder = idleBorder,
                                onClick = {
                                    focusRow = 0
                                    if (selectedGameIndex == index) {
                                        feedbackSelect()
                                        context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { context.startActivity(it) }
                                    } else {
                                        selectedGameIndex = index
                                        feedbackMove()
                                    }
                                }
                            )
                        }

                        item {
                            val addIndex = displayedApps.size
                            val isSelected = focusRow == 0 && selectedGameIndex == addIndex
                            SwitchAddCard(
                                isSelected = isSelected,
                                tileSize = tileSize,
                                radiusDp = cardShape.radiusDp,
                                showLabel = showLabels,
                                accentColor = animAccent,
                                pulseAlpha = effectiveAlpha,
                                cardBg = cardBg,
                                idleBorder = idleBorder,
                                iconTint = primaryText,
                                onClick = {
                                    focusRow = 0
                                    selectedGameIndex = addIndex
                                    soundPlayer.playOpenMenu(soundScheme)
                                    showAddAppsDialog = true
                                }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = tileSize + 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        gridItemsIndexed(displayedApps) { index, app ->
                            val isSelected = focusRow == 0 && selectedGameIndex == index
                            SwitchGameCard(
                                title = app.name,
                                icon = app.icon,
                                isSelected = isSelected,
                                tileSize = tileSize,
                                radiusDp = cardShape.radiusDp,
                                showLabel = showLabels,
                                accentColor = animAccent,
                                pulseAlpha = effectiveAlpha,
                                cardBg = cardBg,
                                idleBorder = idleBorder,
                                onClick = {
                                    focusRow = 0
                                    if (selectedGameIndex == index) {
                                        feedbackSelect()
                                        context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { context.startActivity(it) }
                                    } else {
                                        selectedGameIndex = index
                                        feedbackMove()
                                    }
                                }
                            )
                        }

                        item {
                            val addIndex = displayedApps.size
                            val isSelected = focusRow == 0 && selectedGameIndex == addIndex
                            SwitchAddCard(
                                isSelected = isSelected,
                                tileSize = tileSize,
                                radiusDp = cardShape.radiusDp,
                                showLabel = showLabels,
                                accentColor = animAccent,
                                pulseAlpha = effectiveAlpha,
                                cardBg = cardBg,
                                idleBorder = idleBorder,
                                iconTint = primaryText,
                                onClick = {
                                    focusRow = 0
                                    selectedGameIndex = addIndex
                                    soundPlayer.playOpenMenu(soundScheme)
                                    showAddAppsDialog = true
                                }
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val sysItems = listOf(
                    Triple("Добавить игры", Color(0xFF2ED573), 0),
                    Triple("Альбом / Обои", Color(0xFF1E90FF), 1),
                    Triple("Контроллеры", Color(0xFFFF6B81), 2),
                    Triple("Системные настройки", if (isLight) Color(0xFF4A4A4A) else Color(0xFFDCDDE1), 3),
                    Triple(if (isLight) "Чёрная тема" else "Белая тема", Color(0xFFFFA502), 4),
                    Triple("Режим ожидания", if (isLight) Color(0xFF57606F) else Color(0xFFA4B0BE), 5)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    sysItems.forEach { (label, tint, idx) ->
                        val isSelected = focusRow == 1 && selectedSysBtnIndex == idx
                        val btnScale by animateFloatAsState(if (isSelected) 1.12f else 1.0f, tween(140), label = "sysScale")
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .scale(btnScale)
                                    .size(54.dp)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) animAccent.copy(alpha = effectiveAlpha) else idleBorder,
                                        shape = CircleShape
                                    )
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(cardBg)
                                    .clickable {
                                        focusRow = 1
                                        selectedSysBtnIndex = idx
                                        triggerSystemAction(idx)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                SwitchSystemIcon(index = idx, tint = tint)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isSelected) label else "",
                                color = animAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.height(15.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(thickness = 1.dp, color = dividerColor, modifier = Modifier.fillMaxWidth())
            }
        }

        if (showSleepOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.96f))
                    .clickable {
                        feedbackSelect()
                        showSleepOverlay = false
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = currentTime,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Light
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Нажмите любую кнопку или коснитесь экрана",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        }

        if (showProfileDialog) {
            val presetColors = listOf(
                Color(0xFFE60012), Color(0xFF00A651), Color(0xFF0084FF),
                Color(0xFFFFB900), Color(0xFFFF5599), Color(0xFF7B3FE4)
            )
            AlertDialog(
                onDismissRequest = { showProfileDialog = false },
                containerColor = dialogBg,
                title = { Text("Профиль игрока", color = primaryText, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProfileAvatarCircle(
                                size = 58.dp,
                                bgColor = Color(avatarColorHex),
                                icon = avatarIcon,
                                customBitmap = if (useCustomAvatar) customAvatarBitmap else null,
                                borderColor = animAccent
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            OutlinedTextField(
                                value = userName,
                                onValueChange = {
                                    if (it.length <= 16) {
                                        userName = it
                                        prefs.edit().putString("profile_name", it).apply()
                                    }
                                },
                                label = { Text("Никнейм", color = primaryText.copy(alpha = 0.7f)) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = primaryText,
                                    unfocusedTextColor = primaryText,
                                    focusedBorderColor = animAccent,
                                    unfocusedBorderColor = idleBorder
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                colors = ButtonDefaults.buttonColors(containerColor = animAccent)
                            ) {
                                Text("Фото из галереи", color = if (isLight) Color.White else Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            if (useCustomAvatar) {
                                OutlinedButton(onClick = {
                                    useCustomAvatar = false
                                    prefs.edit().putBoolean("use_custom_avatar", false).apply()
                                }) {
                                    Text("Значок", color = primaryText, fontSize = 12.sp)
                                }
                            }
                        }

                        Text("Цвет фона значка:", color = primaryText, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            presetColors.forEach { col ->
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(col)
                                        .border(if (avatarColorHex == col.toArgb() && !useCustomAvatar) 3.dp else 0.dp, animAccent, CircleShape)
                                        .clickable {
                                            useCustomAvatar = false
                                            avatarColorHex = col.toArgb()
                                            prefs.edit().putBoolean("use_custom_avatar", false).putInt("profile_color", col.toArgb()).apply()
                                        }
                                )
                            }
                        }

                        Text("Игровой значок:", color = primaryText, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProfileAvatarIcon.entries.forEach { ic ->
                                val active = !useCustomAvatar && avatarIcon == ic
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (active) Color(avatarColorHex) else cardBg)
                                        .border(if (active) 2.dp else 1.dp, if (active) animAccent else idleBorder, CircleShape)
                                        .clickable {
                                            useCustomAvatar = false
                                            avatarIcon = ic
                                            prefs.edit().putBoolean("use_custom_avatar", false).putString("profile_icon", ic.name).apply()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AvatarVectorSymbol(icon = ic, tint = if (active || !isLight) Color.White else primaryText, size = 18.dp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        feedbackSelect()
                        showProfileDialog = false
                    }) {
                        Text("Готово", color = animAccent, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        if (showAddAppsDialog) {
            AlertDialog(
                onDismissRequest = { showAddAppsDialog = false },
                containerColor = dialogBg,
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Игры и эмуляторы", color = primaryText, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showAddAppsDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = primaryText)
                        }
                    }
                },
                text = {
                    LazyColumn(modifier = Modifier.height(250.dp)) {
                        items(allInstalledApps) { app ->
                            val isPinned = pinnedPackages.contains(app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        feedbackMove()
                                        val updated = if (isPinned) pinnedPackages - app.packageName else pinnedPackages + app.packageName
                                        savePinned(updated)
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isPinned,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = animAccent)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(app.name, color = primaryText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            feedbackSelect()
                            showAddAppsDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = animAccent)
                    ) {
                        Text("Готово", color = if (isLight) Color.White else Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        if (showSettingsDialog) {
            val subText = if (isLight) Color(0xFF555555) else Color.LightGray
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                containerColor = dialogBg,
                title = { Text("Системные настройки EMO", color = primaryText, fontWeight = FontWeight.Bold) },
                text = {
                    LazyColumn(
                        modifier = Modifier.height(275.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Text("Тема оформления (8 тем)", color = animAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            ConsoleTheme.entries.forEach { theme ->
                                RadioOptionRow(theme.title, consoleTheme == theme, animAccent, primaryText) {
                                    consoleTheme = theme
                                    prefs.edit().putInt("console_theme", theme.ordinal).apply()
                                    feedbackMove()
                                }
                            }
                        }

                        item {
                            Text("Обои из галереи / скриншоты", color = animAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { wallpaperPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    colors = ButtonDefaults.buttonColors(containerColor = animAccent)
                                ) {
                                    Text("Выбрать фото", color = if (isLight) Color.White else Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                if (wallpaperBitmap != null) {
                                    OutlinedButton(onClick = {
                                        File(context.filesDir, "custom_wallpaper.png").delete()
                                        wallpaperBitmap = null
                                        feedbackMove()
                                    }) {
                                        Text("Сбросить", color = primaryText, fontSize = 13.sp)
                                    }
                                }
                            }
                            if (wallpaperBitmap != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Затемнение фона: ${(wallpaperDim * 100).toInt()}%", color = subText, fontSize = 12.sp)
                                Slider(
                                    value = wallpaperDim,
                                    onValueChange = {
                                        wallpaperDim = it
                                        prefs.edit().putFloat("wallpaper_dim", it).apply()
                                    },
                                    valueRange = 0f..0.85f,
                                    colors = SliderDefaults.colors(thumbColor = animAccent, activeTrackColor = animAccent)
                                )
                            }
                        }

                        item {
                            Text("Живые обои поверх фона", color = animAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            WallpaperStyle.entries.forEach { style ->
                                RadioOptionRow(style.title, wallpaperStyle == style, animAccent, primaryText) {
                                    wallpaperStyle = style
                                    prefs.edit().putInt("wallpaper_style", style.ordinal).apply()
                                    feedbackMove()
                                }
                            }
                        }

                        item {
                            Text("Вид главного экрана", color = animAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            LayoutStyle.entries.forEach { style ->
                                RadioOptionRow(style.title, layoutStyle == style, animAccent, primaryText) {
                                    layoutStyle = style
                                    prefs.edit().putInt("layout_style", style.ordinal).apply()
                                    feedbackMove()
                                }
                            }
                        }

                        item {
                            Text("Форма карточек", color = animAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            CardShapeStyle.entries.forEach { shape ->
                                RadioOptionRow(shape.title, cardShape == shape, animAccent, primaryText) {
                                    cardShape = shape
                                    prefs.edit().putInt("card_shape", shape.ordinal).apply()
                                    feedbackMove()
                                }
                            }
                        }

                        item {
                            Text("Звуковая схема (5 режимов)", color = animAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            SoundScheme.entries.forEach { scheme ->
                                RadioOptionRow(scheme.title, soundScheme == scheme, animAccent, primaryText) {
                                    soundScheme = scheme
                                    prefs.edit().putInt("sound_scheme", scheme.ordinal).apply()
                                    soundPlayer.playSelect(scheme)
                                }
                            }
                        }

                        item {
                            Text("Дополнительные параметры", color = animAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            SettingSwitchRow("Пульсация активной рамки", glowPulse, animAccent, primaryText) {
                                glowPulse = it
                                prefs.edit().putBoolean("glow_pulse", it).apply()
                            }
                            SettingSwitchRow("Крупные карточки", largeCards, animAccent, primaryText) {
                                largeCards = it
                                prefs.edit().putBoolean("large_cards", it).apply()
                            }
                            SettingSwitchRow("Показывать название выбранной игры", showLabels, animAccent, primaryText) {
                                showLabels = it
                                prefs.edit().putBoolean("show_labels", it).apply()
                            }
                            SettingSwitchRow("Виброотклик геймпада", vibrationEnabled, animAccent, primaryText) {
                                vibrationEnabled = it
                                prefs.edit().putBoolean("vibration_enabled", it).apply()
                            }
                        }

                        item {
                            OutlinedButton(
                                onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } },
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, animAccent)
                            ) {
                                Text("Назначить рабочим столом (Кнопка Домой)", color = primaryText, fontSize = 13.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            feedbackSelect()
                            showSettingsDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = animAccent)
                    ) {
                        Text("Готово", color = if (isLight) Color.White else Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
fun SwitchGameCard(
    title: String,
    icon: ImageBitmap,
    isSelected: Boolean,
    tileSize: androidx.compose.ui.unit.Dp,
    radiusDp: Int,
    showLabel: Boolean,
    accentColor: Color,
    pulseAlpha: Float,
    cardBg: Color,
    idleBorder: Color,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(if (isSelected) 1.05f else 1.0f, tween(150), label = "tileScale")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(scale)) {
        if (showLabel) {
            Box(modifier = Modifier.height(26.dp), contentAlignment = Alignment.Center) {
                if (isSelected) {
                    Text(
                        text = title,
                        color = accentColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = tileSize + 18.dp)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(tileSize)
                .border(
                    width = if (isSelected) 4.dp else 1.dp,
                    color = if (isSelected) accentColor.copy(alpha = pulseAlpha) else idleBorder,
                    shape = RoundedCornerShape((radiusDp + 4).dp)
                )
                .padding(if (isSelected) 6.dp else 2.dp)
                .clip(RoundedCornerShape(radiusDp.dp))
                .background(cardBg)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = icon,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun SwitchAddCard(
    isSelected: Boolean,
    tileSize: androidx.compose.ui.unit.Dp,
    radiusDp: Int,
    showLabel: Boolean,
    accentColor: Color,
    pulseAlpha: Float,
    cardBg: Color,
    idleBorder: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(if (isSelected) 1.05f else 1.0f, tween(150), label = "addScale")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(scale)) {
        if (showLabel) {
            Box(modifier = Modifier.height(26.dp), contentAlignment = Alignment.Center) {
                if (isSelected) {
                    Text(
                        text = "Добавить игру",
                        color = accentColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(tileSize)
                .border(
                    width = if (isSelected) 4.dp else 1.dp,
                    color = if (isSelected) accentColor.copy(alpha = pulseAlpha) else idleBorder,
                    shape = RoundedCornerShape((radiusDp + 4).dp)
                )
                .padding(if (isSelected) 6.dp else 2.dp)
                .clip(RoundedCornerShape(radiusDp.dp))
                .background(cardBg)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Добавить",
                tint = if (isSelected) accentColor else iconTint.copy(alpha = 0.72f),
                modifier = Modifier.size(46.dp)
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
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
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
                        lineTo(x.toFloat(), h * 0.65f + sin((x / w) * 4f + phase) * 42f)
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
                            lineTo(x.toFloat(), h * (0.55f + layer * 0.1f) + sin((x / w) * 3.5f + offsetPhase) * (50f - layer * 12f))
                        }
                    }
                    drawPath(path = path, color = accentColor.copy(alpha = 0.24f - layer * 0.08f), style = Stroke(width = (4 - layer).dp.toPx()))
                }
            }
            WallpaperStyle.PARTICLES -> {
                for (i in 1..32) {
                    val px = ((i * 137f + phase * 45f) % w)
                    val py = ((i * 89f + sin(phase + i) * 35f + h) % h)
                    drawCircle(color = accentColor.copy(alpha = 0.18f + (i % 3) * 0.07f), radius = (2f + (i % 4) * 1.5f).dp.toPx(), center = Offset(px, py))
                }
            }
            WallpaperStyle.CYBER_GRID -> {
                val horizon = h * 0.55f
                val step = 60.dp.toPx()
                var y = horizon + (phase / (2 * Math.PI).toFloat()) * step
                while (y < h) {
                    drawLine(accentColor.copy(alpha = 0.18f), Offset(0f, y), Offset(w, y), 1.5.dp.toPx())
                    y += step * 0.6f
                }
                for (i in -6..6) {
                    drawLine(accentColor.copy(alpha = 0.15f), Offset(w / 2f + i * 40f, horizon), Offset(w / 2f + i * 190f, h), 1.5.dp.toPx())
                }
            }
            WallpaperStyle.STATIC -> {}
        }
    }
}

@Composable
fun RadioOptionRow(title: String, selected: Boolean, accentColor: Color, textColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = accentColor))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, color = textColor, fontSize = 14.sp)
    }
}

@Composable
fun SettingSwitchRow(title: String, checked: Boolean, accentColor: Color, textColor: Color, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = textColor, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor))
    }
}

@Composable
fun ProfileAvatarCircle(
    size: androidx.compose.ui.unit.Dp,
    bgColor: Color,
    icon: ProfileAvatarIcon,
    customBitmap: ImageBitmap?,
    borderColor: Color
) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(bgColor).border(2.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (customBitmap != null) {
            Image(bitmap = customBitmap, contentDescription = "Avatar", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            AvatarVectorSymbol(icon = icon, tint = Color.White, size = size * 0.55f)
        }
    }
}

@Composable
fun AvatarVectorSymbol(icon: ProfileAvatarIcon, tint: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        when (icon) {
            ProfileAvatarIcon.JOYCON -> {
                drawRoundRect(tint, Offset(w * 0.14f, h * 0.1f), Size(w * 0.3f, h * 0.8f), CornerRadius(w * 0.15f), style = Stroke(w * 0.09f))
                drawRoundRect(tint, Offset(w * 0.56f, h * 0.1f), Size(w * 0.3f, h * 0.8f), CornerRadius(w * 0.15f))
                drawCircle(tint, w * 0.06f, Offset(w * 0.29f, h * 0.34f))
            }
            ProfileAvatarIcon.STAR -> {
                val p = Path().apply {
                    moveTo(w * 0.5f, h * 0.08f); lineTo(w * 0.63f, h * 0.36f); lineTo(w * 0.94f, h * 0.40f)
                    lineTo(w * 0.71f, h * 0.61f); lineTo(w * 0.77f, h * 0.91f); lineTo(w * 0.5f, h * 0.76f)
                    lineTo(w * 0.23f, h * 0.91f); lineTo(w * 0.29f, h * 0.61f); lineTo(w * 0.06f, h * 0.40f)
                    lineTo(w * 0.37f, h * 0.36f); close()
                }
                drawPath(p, tint)
            }
            ProfileAvatarIcon.HEART -> {
                val p = Path().apply {
                    moveTo(w * 0.5f, h * 0.86f)
                    cubicTo(w * 0.1f, h * 0.58f, w * 0.08f, h * 0.22f, w * 0.32f, h * 0.22f)
                    cubicTo(w * 0.43f, h * 0.22f, w * 0.5f, h * 0.32f, w * 0.5f, h * 0.36f)
                    cubicTo(w * 0.5f, h * 0.32f, w * 0.57f, h * 0.22f, w * 0.68f, h * 0.22f)
                    cubicTo(w * 0.92f, h * 0.22f, w * 0.9f, h * 0.58f, w * 0.5f, h * 0.86f)
                    close()
                }
                drawPath(p, tint)
            }
            ProfileAvatarIcon.BOLT -> {
                val p = Path().apply {
                    moveTo(w * 0.58f, h * 0.08f); lineTo(w * 0.22f, h * 0.54f); lineTo(w * 0.48f, h * 0.54f)
                    lineTo(w * 0.40f, h * 0.92f); lineTo(w * 0.78f, h * 0.44f); lineTo(w * 0.52f, h * 0.44f); close()
                }
                drawPath(p, tint)
            }
            ProfileAvatarIcon.CROWN -> {
                val p = Path().apply {
                    moveTo(w * 0.12f, h * 0.78f); lineTo(w * 0.12f, h * 0.30f); lineTo(w * 0.34f, h * 0.52f)
                    lineTo(w * 0.50f, h * 0.22f); lineTo(w * 0.66f, h * 0.52f); lineTo(w * 0.88f, h * 0.30f)
                    lineTo(w * 0.88f, h * 0.78f); close()
                }
                drawPath(p, tint)
            }
            ProfileAvatarIcon.SHIELD -> {
                val p = Path().apply {
                    moveTo(w * 0.50f, h * 0.10f); lineTo(w * 0.84f, h * 0.24f); lineTo(w * 0.84f, h * 0.52f)
                    cubicTo(w * 0.84f, h * 0.74f, w * 0.50f, h * 0.90f, w * 0.50f, h * 0.90f)
                    cubicTo(w * 0.50f, h * 0.90f, w * 0.16f, h * 0.74f, w * 0.16f, h * 0.52f)
                    lineTo(w * 0.16f, h * 0.24f); close()
                }
                drawPath(p, tint)
            }
        }
    }
}

@Composable
fun SwitchSystemIcon(index: Int, tint: Color) {
    when (index) {
        0 -> Icon(Icons.Default.Add, contentDescription = null, tint = tint, modifier = Modifier.size(25.dp))
        3 -> Icon(Icons.Default.Settings, contentDescription = null, tint = tint, modifier = Modifier.size(23.dp))
        else -> {
            Canvas(modifier = Modifier.size(23.dp)) {
                val w = size.width
                val h = size.height
                when (index) {
                    1 -> {
                        drawRoundRect(tint, Offset(w * 0.1f, h * 0.18f), Size(w * 0.8f, h * 0.64f), CornerRadius(3f), style = Stroke(2.2f))
                        val m = Path().apply {
                            moveTo(w * 0.18f, h * 0.74f); lineTo(w * 0.42f, h * 0.44f); lineTo(w * 0.60f, h * 0.64f)
                            lineTo(w * 0.72f, h * 0.50f); lineTo(w * 0.84f, h * 0.74f); close()
                        }
                        drawPath(m, tint)
                    }
                    2 -> {
                        drawRoundRect(tint, Offset(w * 0.14f, h * 0.12f), Size(w * 0.3f, h * 0.76f), CornerRadius(6f), style = Stroke(2.2f))
                        drawRoundRect(tint, Offset(w * 0.56f, h * 0.12f), Size(w * 0.3f, h * 0.76f), CornerRadius(6f))
                    }
                    4 -> {
                        drawCircle(tint, w * 0.22f, Offset(w * 0.5f, h * 0.5f))
                        for (i in 0 until 8) {
                            val a = Math.toRadians(i * 45.0)
                            drawLine(
                                tint,
                                Offset((w * 0.5f + cos(a) * w * 0.32f).toFloat(), (h * 0.5f + sin(a) * h * 0.32f).toFloat()),
                                Offset((w * 0.5f + cos(a) * w * 0.44f).toFloat(), (h * 0.5f + sin(a) * h * 0.44f).toFloat()),
                                2.2f
                            )
                        }
                    }
                    5 -> {
                        drawArc(tint, -55f, 290f, false, Offset(w * 0.18f, h * 0.18f), Size(w * 0.64f, h * 0.64f), style = Stroke(2.4f))
                        drawLine(tint, Offset(w * 0.5f, h * 0.1f), Offset(w * 0.5f, h * 0.48f), 2.4f)
                    }
                }
            }
        }
    }
}

@Composable
fun SwitchBatteryIcon(level: Int, color: Color) {
    Canvas(modifier = Modifier.size(width = 26.dp, height = 13.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(color, Offset.Zero, Size(w * 0.88f, h), CornerRadius(2f), style = Stroke(1.8f))
        drawRect(color, Offset(w * 0.9f, h * 0.28f), Size(w * 0.1f, h * 0.44f))
        val fillW = (w * 0.80f) * (level / 100f)
        if (fillW > 0f) {
            drawRect(if (level <= 15) Color(0xFFFF3B30) else color, Offset(w * 0.04f, h * 0.16f), Size(fillW, h * 0.68f))
        }
    }
}

private fun fetchInstalledApps(context: Context): List<AppItem> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
    return pm.queryIntentActivities(intent, 0).mapNotNull { info ->
        val pkg = info.activityInfo.packageName
        if (pkg == context.packageName) return@mapNotNull null
        AppItem(info.loadLabel(pm).toString(), pkg, drawableToBitmap(info.loadIcon(pm)).asImageBitmap())
    }.distinctBy { it.packageName }.sortedBy { it.name.lowercase(Locale.getDefault()) }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
    val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 128
    val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 128
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bmp
}

private fun saveUriToInternalFile(context: Context, uri: Uri, filename: String, maxDim: Int): ImageBitmap? {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val original = BitmapFactory.decodeStream(input) ?: return null
            val scale = minOf(1f, maxDim.toFloat() / maxOf(original.width, original.height))
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(original, (original.width * scale).toInt().coerceAtLeast(1), (original.height * scale).toInt().coerceAtLeast(1), true)
            } else original
            FileOutputStream(File(context.filesDir, filename)).use { fos -> scaled.compress(Bitmap.CompressFormat.PNG, 92, fos) }
            scaled.asImageBitmap()
        }
    }.getOrNull()
}

private fun loadSavedBitmap(context: Context, filename: String): ImageBitmap? {
    return runCatching {
        val file = File(context.filesDir, filename)
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() else null
    }.getOrNull()
}

package com.denismaksimov.emuconsole

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class AppItem(
    val name: String,
    val packageName: String,
    val icon: ImageBitmap
)

enum class SwitchTheme(
    val title: String,
    val bgColor: Color,
    val textColor: Color,
    val accentColor: Color,
    val cardBgColor: Color,
    val dividerColor: Color,
    val dialogBgColor: Color
) {
    SWITCH_DARK(
        title = "Базовая чёрная",
        bgColor = Color(0xFF2D2D2D),
        textColor = Color.White,
        accentColor = Color(0xFF00E5FF),
        cardBgColor = Color(0xFF3A3A3A),
        dividerColor = Color(0xFF555555),
        dialogBgColor = Color(0xFF222222)
    ),
    SWITCH_LIGHT(
        title = "Базовая белая",
        bgColor = Color(0xFFEBEBEB),
        textColor = Color(0xFF2D2D2D),
        accentColor = Color(0xFF0084FF),
        cardBgColor = Color(0xFFFFFFFF),
        dividerColor = Color(0xFFCCCCCC),
        dialogBgColor = Color(0xFFF7F7F7)
    )
}

enum class ProfileAvatarIcon(val label: String) {
    JOYCON("Джойкон"),
    STAR("Звезда"),
    HEART("Сердце"),
    BOLT("Молния"),
    CROWN("Корона"),
    SHIELD("Щит")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

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
    val prefs = remember { context.getSharedPreferences("emu_console_prefs", Context.MODE_PRIVATE) }

    var currentTheme by remember {
        val saved = prefs.getString("switch_theme", SwitchTheme.SWITCH_DARK.name)
        mutableStateOf(
            try { SwitchTheme.valueOf(saved ?: SwitchTheme.SWITCH_DARK.name) }
            catch (e: Exception) { SwitchTheme.SWITCH_DARK }
        )
    }

    var soundEnabled by remember { mutableStateOf(prefs.getBoolean("sound_enabled", true)) }
    var userName by remember { mutableStateOf(prefs.getString("profile_name", "Player 1") ?: "Player 1") }
    var avatarColorHex by remember { mutableIntStateOf(prefs.getInt("profile_color", Color(0xFFE60012).toArgb())) }
    var avatarIcon by remember {
        val savedIcon = prefs.getString("profile_icon", ProfileAvatarIcon.JOYCON.name)
        mutableStateOf(
            try { ProfileAvatarIcon.valueOf(savedIcon ?: ProfileAvatarIcon.JOYCON.name) }
            catch (e: Exception) { ProfileAvatarIcon.JOYCON }
        )
    }
    var useCustomAvatar by remember { mutableStateOf(prefs.getBoolean("use_custom_avatar", false)) }
    var customAvatarBitmap by remember { mutableStateOf<ImageBitmap?>(loadSavedBitmap(context, "profile_avatar.png")) }
    var wallpaperBitmap by remember { mutableStateOf<ImageBitmap?>(loadSavedBitmap(context, "custom_wallpaper.png")) }

    var allInstalledApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var pinnedPackages by remember {
        val saved = prefs.getString("pinned_apps", "") ?: ""
        val list = if (saved.isBlank()) emptyList() else saved.split(",").filter { it.isNotBlank() }
        mutableStateOf(list)
    }

    var focusRow by remember { mutableIntStateOf(0) } // 0 = games, 1 = bottom buttons
    var selectedGameIndex by remember { mutableIntStateOf(0) }
    var selectedSysBtnIndex by remember { mutableIntStateOf(0) }

    var showAddAppsDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showSleepOverlay by remember { mutableStateOf(false) }
    var showProfileNameBadge by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val toneGen = remember {
        try { ToneGenerator(AudioManager.STREAM_SYSTEM, 45) } catch (e: Exception) { null }
    }

    DisposableEffect(Unit) {
        onDispose { toneGen?.release() }
    }

    fun playSwitchClick(launch: Boolean = false) {
        if (!soundEnabled) return
        try {
            if (launch) {
                toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 70)
            } else {
                toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 28)
            }
        } catch (_: Exception) {}
    }

    fun savePinned(list: List<String>) {
        pinnedPackages = list
        prefs.edit().putString("pinned_apps", list.joinToString(",")).apply()
    }

    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bmp = saveUriToInternalFile(context, it, "custom_wallpaper.png", 1280)
            if (bmp != null) {
                wallpaperBitmap = bmp
            }
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bmp = saveUriToInternalFile(context, it, "profile_avatar.png", 256)
            if (bmp != null) {
                customAvatarBitmap = bmp
                useCustomAvatar = true
                prefs.edit().putBoolean("use_custom_avatar", true).apply()
            }
        }
    }

    LaunchedEffect(Unit) {
        allInstalledApps = loadLaunchableApps(context)
        focusRequester.requestFocus()
    }

    val displayedApps = remember(allInstalledApps, pinnedPackages) {
        val map = allInstalledApps.associateBy { it.packageName }
        pinnedPackages.mapNotNull { map[it] }
    }

    LaunchedEffect(selectedGameIndex, displayedApps.size) {
        if (displayedApps.isNotEmpty() && selectedGameIndex in displayedApps.indices) {
            listState.animateScrollToItem(selectedGameIndex)
        }
    }

    LaunchedEffect(showProfileNameBadge) {
        if (showProfileNameBadge) {
            delay(2500)
            showProfileNameBadge = false
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "switch_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val animatedBg by animateColorAsState(currentTheme.bgColor, tween(250), label = "bg")
    val animatedText by animateColorAsState(currentTheme.textColor, tween(250), label = "text")

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
        playSwitchClick(false)
        when (index) {
            0 -> showAddAppsDialog = true
            1 -> wallpaperPicker.launch("image/*")
            2 -> {
                try {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {}
            }
            3 -> showSettingsDialog = true
            4 -> {
                val next = if (currentTheme == SwitchTheme.SWITCH_DARK) SwitchTheme.SWITCH_LIGHT else SwitchTheme.SWITCH_DARK
                currentTheme = next
                prefs.edit().putString("switch_theme", next.name).apply()
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
            .background(animatedBg)
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
                            val maxIdx = displayedApps.size
                            if (selectedGameIndex < maxIdx) {
                                selectedGameIndex++
                                playSwitchClick()
                            }
                        } else {
                            if (selectedSysBtnIndex < 5) {
                                selectedSysBtnIndex++
                                playSwitchClick()
                            }
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (focusRow == 0) {
                            if (selectedGameIndex > 0) {
                                selectedGameIndex--
                                playSwitchClick()
                            }
                        } else {
                            if (selectedSysBtnIndex > 0) {
                                selectedSysBtnIndex--
                                playSwitchClick()
                            }
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (focusRow == 0) {
                            focusRow = 1
                            playSwitchClick()
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (focusRow == 1) {
                            focusRow = 0
                            playSwitchClick()
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                        if (focusRow == 0) {
                            if (selectedGameIndex < displayedApps.size) {
                                playSwitchClick(true)
                                val app = displayedApps[selectedGameIndex]
                                context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                                    context.startActivity(it)
                                }
                            } else {
                                playSwitchClick()
                                showAddAppsDialog = true
                            }
                        } else {
                            triggerSystemAction(selectedSysBtnIndex)
                        }
                        true
                    }
                    KeyEvent.KEYCODE_BUTTON_START -> {
                        playSwitchClick()
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
                contentScale = ContentScale.Crop,
                alpha = if (currentTheme == SwitchTheme.SWITCH_DARK) 0.42f else 0.25f
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Profile + Status
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
                            playSwitchClick()
                            showProfileNameBadge = true
                            showProfileDialog = true
                        }
                        .padding(end = 10.dp)
                ) {
                    ProfileAvatarCircle(
                        size = 44.dp,
                        bgColor = Color(avatarColorHex),
                        icon = avatarIcon,
                        customBitmap = if (useCustomAvatar) customAvatarBitmap else null,
                        borderColor = currentTheme.cardBgColor
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = userName,
                        color = animatedText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentTime,
                        color = animatedText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "$batteryLevel%",
                        color = animatedText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    SwitchBatteryIcon(level = batteryLevel, color = animatedText)
                }
            }

            // Middle: Game Tiles Row
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(displayedApps) { index, app ->
                    val isSelected = focusRow == 0 && selectedGameIndex == index
                    val scale by animateFloatAsState(if (isSelected) 1.05f else 1.0f, tween(150), label = "tile_scale")

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.scale(scale)
                    ) {
                        Box(
                            modifier = Modifier.height(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Text(
                                    text = app.name,
                                    color = currentTheme.accentColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 175.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(156.dp)
                                .border(
                                    width = if (isSelected) 4.dp else 0.dp,
                                    color = if (isSelected) currentTheme.accentColor.copy(alpha = pulseAlpha) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(currentTheme.cardBgColor)
                                .clickable {
                                    focusRow = 0
                                    if (selectedGameIndex == index) {
                                        playSwitchClick(true)
                                        context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                                            context.startActivity(it)
                                        }
                                    } else {
                                        selectedGameIndex = index
                                        playSwitchClick()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = app.icon,
                                contentDescription = app.name,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(18.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                // Add App Tile at end of row
                item {
                    val addIndex = displayedApps.size
                    val isSelected = focusRow == 0 && selectedGameIndex == addIndex
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.height(28.dp), contentAlignment = Alignment.Center) {
                            if (isSelected) {
                                Text(
                                    text = "Добавить игру",
                                    color = currentTheme.accentColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(156.dp)
                                .border(
                                    width = if (isSelected) 4.dp else 0.dp,
                                    color = if (isSelected) currentTheme.accentColor.copy(alpha = pulseAlpha) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(currentTheme.cardBgColor)
                                .clickable {
                                    focusRow = 0
                                    selectedGameIndex = addIndex
                                    playSwitchClick()
                                    showAddAppsDialog = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Добавить",
                                tint = animatedText.copy(alpha = 0.75f),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }
            }

            // Bottom: Switch Circular System Buttons + Divider
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val sysLabels = listOf(
                    "Добавить игры",
                    "Альбом / Обои",
                    "Контроллеры",
                    "Системные настройки",
                    if (currentTheme == SwitchTheme.SWITCH_DARK) "Белая тема" else "Чёрная тема",
                    "Режим ожидания"
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    sysLabels.forEachIndexed { idx, label ->
                        val isSelected = focusRow == 1 && selectedSysBtnIndex == idx
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) currentTheme.accentColor.copy(alpha = pulseAlpha) else currentTheme.dividerColor,
                                        shape = CircleShape
                                    )
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(currentTheme.cardBgColor)
                                    .clickable {
                                        focusRow = 1
                                        selectedSysBtnIndex = idx
                                        triggerSystemAction(idx)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                SwitchSystemIcon(
                                    index = idx,
                                    tint = if (isSelected) currentTheme.accentColor else animatedText
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isSelected) label else "",
                                color = currentTheme.accentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(
                    thickness = 1.dp,
                    color = currentTheme.dividerColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Sleep Overlay
        if (showSleepOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.96f))
                    .clickable { showSleepOverlay = false },
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

        // Profile Dialog
        if (showProfileDialog) {
            val presetColors = listOf(
                Color(0xFFE60012), // Mario Red
                Color(0xFF00A651), // Link Green
                Color(0xFF0084FF), // Blue
                Color(0xFFFFB900), // Pikachu Yellow
                Color(0xFFFF5599), // Kirby Pink
                Color(0xFF7B3FE4)  // Purple
            )
            AlertDialog(
                onDismissRequest = { showProfileDialog = false },
                containerColor = currentTheme.dialogBgColor,
                title = {
                    Text("Профиль игрока", color = animatedText, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProfileAvatarCircle(
                                size = 60.dp,
                                bgColor = Color(avatarColorHex),
                                icon = avatarIcon,
                                customBitmap = if (useCustomAvatar) customAvatarBitmap else null,
                                borderColor = currentTheme.accentColor
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
                                label = { Text("Никнейм", color = animatedText.copy(alpha = 0.7f)) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = animatedText,
                                    unfocusedTextColor = animatedText,
                                    focusedBorderColor = currentTheme.accentColor,
                                    unfocusedBorderColor = currentTheme.dividerColor
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { avatarPicker.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = currentTheme.accentColor)
                            ) {
                                Text("Фото из галереи", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            if (useCustomAvatar) {
                                OutlinedButton(
                                    onClick = {
                                        useCustomAvatar = false
                                        prefs.edit().putBoolean("use_custom_avatar", false).apply()
                                    }
                                ) {
                                    Text("Значок", color = animatedText, fontSize = 12.sp)
                                }
                            }
                        }

                        Text("Цвет фона значка:", color = animatedText, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            presetColors.forEach { col ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(col)
                                        .border(
                                            width = if (avatarColorHex == col.toArgb() && !useCustomAvatar) 3.dp else 0.dp,
                                            color = currentTheme.accentColor,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            useCustomAvatar = false
                                            avatarColorHex = col.toArgb()
                                            prefs.edit()
                                                .putBoolean("use_custom_avatar", false)
                                                .putInt("profile_color", col.toArgb())
                                                .apply()
                                        }
                                )
                            }
                        }

                        Text("Игровой значок:", color = animatedText, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProfileAvatarIcon.entries.forEach { ic ->
                                val active = !useCustomAvatar && avatarIcon == ic
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (active) Color(avatarColorHex) else currentTheme.cardBgColor)
                                        .border(
                                            width = if (active) 2.dp else 1.dp,
                                            color = if (active) currentTheme.accentColor else currentTheme.dividerColor,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            useCustomAvatar = false
                                            avatarIcon = ic
                                            prefs.edit()
                                                .putBoolean("use_custom_avatar", false)
                                                .putString("profile_icon", ic.name)
                                                .apply()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AvatarVectorSymbol(icon = ic, tint = Color.White, size = 20.dp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showProfileDialog = false }) {
                        Text("Готово", color = currentTheme.accentColor, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Settings Dialog
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                containerColor = currentTheme.dialogBgColor,
                title = {
                    Text("Системные настройки", color = animatedText, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Тема оформления:", color = animatedText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SwitchTheme.entries.forEach { theme ->
                                val selected = currentTheme == theme
                                OutlinedButton(
                                    onClick = {
                                        currentTheme = theme
                                        prefs.edit().putString("switch_theme", theme.name).apply()
                                    },
                                    border = BorderStroke(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) currentTheme.accentColor else currentTheme.dividerColor
                                    ),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = theme.bgColor
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(theme.title, color = theme.textColor, fontSize = 12.sp)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Звуки интерфейса Switch", color = animatedText, fontSize = 14.sp)
                            Switch(
                                checked = soundEnabled,
                                onCheckedChange = {
                                    soundEnabled = it
                                    prefs.edit().putBoolean("sound_enabled", it).apply()
                                }
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { wallpaperPicker.launch("image/*") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Выбрать фон", color = animatedText, fontSize = 12.sp)
                            }
                            if (wallpaperBitmap != null) {
                                OutlinedButton(
                                    onClick = {
                                        File(context.filesDir, "custom_wallpaper.png").delete()
                                        wallpaperBitmap = null
                                    }
                                ) {
                                    Text("Сброс фона", color = animatedText, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text("Закрыть", color = currentTheme.accentColor, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Add/Remove Apps Dialog
        if (showAddAppsDialog) {
            AlertDialog(
                onDismissRequest = { showAddAppsDialog = false },
                containerColor = currentTheme.dialogBgColor,
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Игры и программы", color = animatedText, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showAddAppsDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = animatedText)
                        }
                    }
                },
                text = {
                    LazyColumn(modifier = Modifier.height(260.dp)) {
                        items(allInstalledApps) { app ->
                            val isPinned = pinnedPackages.contains(app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val updated = if (isPinned) {
                                            pinnedPackages - app.packageName
                                        } else {
                                            pinnedPackages + app.packageName
                                        }
                                        savePinned(updated)
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    bitmap = app.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(34.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = app.name,
                                    color = animatedText,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Checkbox(
                                    checked = isPinned,
                                    onCheckedChange = { checked ->
                                        val updated = if (checked) {
                                            pinnedPackages + app.packageName
                                        } else {
                                            pinnedPackages - app.packageName
                                        }
                                        savePinned(updated)
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAddAppsDialog = false }) {
                        Text("Готово", color = currentTheme.accentColor, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
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
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor)
            .border(2.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (customBitmap != null) {
            Image(
                bitmap = customBitmap,
                contentDescription = "Avatar",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            AvatarVectorSymbol(icon = icon, tint = Color.White, size = size * 0.55f)
        }
    }
}

@Composable
fun AvatarVectorSymbol(
    icon: ProfileAvatarIcon,
    tint: Color,
    size: androidx.compose.ui.unit.Dp
) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        when (icon) {
            ProfileAvatarIcon.JOYCON -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.14f, h * 0.1f),
                    size = Size(w * 0.3f, h * 0.8f),
                    cornerRadius = CornerRadius(w * 0.15f, w * 0.15f),
                    style = Stroke(width = w * 0.09f)
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.56f, h * 0.1f),
                    size = Size(w * 0.3f, h * 0.8f),
                    cornerRadius = CornerRadius(w * 0.15f, w * 0.15f)
                )
                drawCircle(color = tint, radius = w * 0.06f, center = Offset(w * 0.29f, h * 0.34f))
            }
            ProfileAvatarIcon.STAR -> {
                val path = Path().apply {
                    moveTo(w * 0.5f, h * 0.08f)
                    lineTo(w * 0.63f, h * 0.36f)
                    lineTo(w * 0.94f, h * 0.40f)
                    lineTo(w * 0.71f, h * 0.61f)
                    lineTo(w * 0.77f, h * 0.91f)
                    lineTo(w * 0.5f, h * 0.76f)
                    lineTo(w * 0.23f, h * 0.91f)
                    lineTo(w * 0.29f, h * 0.61f)
                    lineTo(w * 0.06f, h * 0.40f)
                    lineTo(w * 0.37f, h * 0.36f)
                    close()
                }
                drawPath(path, color = tint)
            }
            ProfileAvatarIcon.HEART -> {
                val path = Path().apply {
                    moveTo(w * 0.5f, h * 0.86f)
                    cubicTo(w * 0.1f, h * 0.58f, w * 0.08f, h * 0.22f, w * 0.32f, h * 0.22f)
                    cubicTo(w * 0.43f, h * 0.22f, w * 0.5f, h * 0.32f, w * 0.5f, h * 0.36f)
                    cubicTo(w * 0.5f, h * 0.32f, w * 0.57f, h * 0.22f, w * 0.68f, h * 0.22f)
                    cubicTo(w * 0.92f, h * 0.22f, w * 0.9f, h * 0.58f, w * 0.5f, h * 0.86f)
                    close()
                }
                drawPath(path, color = tint)
            }
            ProfileAvatarIcon.BOLT -> {
                val path = Path().apply {
                    moveTo(w * 0.58f, h * 0.08f)
                    lineTo(w * 0.22f, h * 0.54f)
                    lineTo(w * 0.48f, h * 0.54f)
                    lineTo(w * 0.40f, h * 0.92f)
                    lineTo(w * 0.78f, h * 0.44f)
                    lineTo(w * 0.52f, h * 0.44f)
                    close()
                }
                drawPath(path, color = tint)
            }
            ProfileAvatarIcon.CROWN -> {
                val path = Path().apply {
                    moveTo(w * 0.12f, h * 0.78f)
                    lineTo(w * 0.12f, h * 0.30f)
                    lineTo(w * 0.34f, h * 0.52f)
                    lineTo(w * 0.50f, h * 0.22f)
                    lineTo(w * 0.66f, h * 0.52f)
                    lineTo(w * 0.88f, h * 0.30f)
                    lineTo(w * 0.88f, h * 0.78f)
                    close()
                }
                drawPath(path, color = tint)
            }
            ProfileAvatarIcon.SHIELD -> {
                val path = Path().apply {
                    moveTo(w * 0.50f, h * 0.10f)
                    lineTo(w * 0.84f, h * 0.24f)
                    lineTo(w * 0.84f, h * 0.52f)
                    cubicTo(w * 0.84f, h * 0.74f, w * 0.50f, h * 0.90f, w * 0.50f, h * 0.90f)
                    cubicTo(w * 0.50f, h * 0.90f, w * 0.16f, h * 0.74f, w * 0.16f, h * 0.52f)
                    lineTo(w * 0.16f, h * 0.24f)
                    close()
                }
                drawPath(path, color = tint)
            }
        }
    }
}

@Composable
fun SwitchSystemIcon(index: Int, tint: Color) {
    when (index) {
        0 -> Icon(Icons.Default.Add, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        3 -> Icon(Icons.Default.Settings, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        else -> {
            Canvas(modifier = Modifier.size(22.dp)) {
                val w = size.width
                val h = size.height
                when (index) {
                    1 -> { // Album / Gallery
                        drawRoundRect(
                            color = tint,
                            topLeft = Offset(w * 0.1f, h * 0.18f),
                            size = Size(w * 0.8f, h * 0.64f),
                            cornerRadius = CornerRadius(3f, 3f),
                            style = Stroke(width = 2.2f)
                        )
                        val mountain = Path().apply {
                            moveTo(w * 0.18f, h * 0.74f)
                            lineTo(w * 0.42f, h * 0.44f)
                            lineTo(w * 0.60f, h * 0.64f)
                            lineTo(w * 0.72f, h * 0.50f)
                            lineTo(w * 0.84f, h * 0.74f)
                            close()
                        }
                        drawPath(mountain, color = tint)
                    }
                    2 -> { // Controllers (Joy-Cons)
                        drawRoundRect(
                            color = tint,
                            topLeft = Offset(w * 0.14f, h * 0.12f),
                            size = Size(w * 0.3f, h * 0.76f),
                            cornerRadius = CornerRadius(6f, 6f),
                            style = Stroke(width = 2.2f)
                        )
                        drawRoundRect(
                            color = tint,
                            topLeft = Offset(w * 0.56f, h * 0.12f),
                            size = Size(w * 0.3f, h * 0.76f),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                    }
                    4 -> { // Theme Sun
                        drawCircle(color = tint, radius = w * 0.22f, center = Offset(w * 0.5f, h * 0.5f))
                        for (i in 0 until 8) {
                            val angle = Math.toRadians(i * 45.0)
                            val sx = (w * 0.5f + Math.cos(angle) * w * 0.32f).toFloat()
                            val sy = (h * 0.5f + Math.sin(angle) * h * 0.32f).toFloat()
                            val ex = (w * 0.5f + Math.cos(angle) * w * 0.44f).toFloat()
                            val ey = (h * 0.5f + Math.sin(angle) * h * 0.44f).toFloat()
                            drawLine(tint, Offset(sx, sy), Offset(ex, ey), strokeWidth = 2.2f)
                        }
                    }
                    5 -> { // Sleep Power
                        drawArc(
                            color = tint,
                            startAngle = -55f,
                            sweepAngle = 290f,
                            useCenter = false,
                            topLeft = Offset(w * 0.18f, h * 0.18f),
                            size = Size(w * 0.64f, h * 0.64f),
                            style = Stroke(width = 2.4f)
                        )
                        drawLine(
                            color = tint,
                            start = Offset(w * 0.5f, h * 0.1f),
                            end = Offset(w * 0.5f, h * 0.48f),
                            strokeWidth = 2.4f
                        )
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
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, 0f),
            size = Size(w * 0.88f, h),
            cornerRadius = CornerRadius(2f, 2f),
            style = Stroke(width = 1.8f)
        )
        drawRect(
            color = color,
            topLeft = Offset(w * 0.9f, h * 0.28f),
            size = Size(w * 0.1f, h * 0.44f)
        )
        val fillW = (w * 0.80f) * (level / 100f)
        if (fillW > 0f) {
            drawRect(
                color = if (level <= 15) Color(0xFFFF3B30) else color,
                topLeft = Offset(w * 0.04f, h * 0.16f),
                size = Size(fillW, h * 0.68f)
            )
        }
    }
}

private fun loadLaunchableApps(context: Context): List<AppItem> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .map { ri ->
            val label = ri.loadLabel(pm).toString()
            val pkg = ri.activityInfo.packageName
            val bmp = ri.loadIcon(pm).toBitmap(144, 144).asImageBitmap()
            AppItem(label, pkg, bmp)
        }
        .sortedBy { it.name.lowercase(Locale.getDefault()) }
}

private fun saveUriToInternalFile(context: Context, uri: Uri, filename: String, maxDim: Int): ImageBitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val original = BitmapFactory.decodeStream(input) ?: return null
            val scale = minOf(1f, maxDim.toFloat() / maxOf(original.width, original.height))
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * scale).toInt().coerceAtLeast(1),
                    (original.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else original
            val outFile = File(context.filesDir, filename)
            FileOutputStream(outFile).use { fos ->
                scaled.compress(Bitmap.CompressFormat.PNG, 92, fos)
            }
            scaled.asImageBitmap()
        }
    } catch (e: Exception) {
        null
    }
}

private fun loadSavedBitmap(context: Context, filename: String): ImageBitmap? {
    return try {
        val file = File(context.filesDir, filename)
        if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        } else null
    } catch (e: Exception) {
        null
    }
}

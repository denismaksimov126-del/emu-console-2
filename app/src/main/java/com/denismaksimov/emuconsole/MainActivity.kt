package com.denismaksimov.emuconsole

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class AppItem(
    val label: String,
    val packageName: String,
    val icon: Bitmap
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EmuConsoleScreen()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Блокируем выход по кнопке "Назад", так как это главный экран (лаунчер)
    }
}

@Composable
fun EmuConsoleScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("emu_prefs", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()

    // Загружаем все установленные приложения
    val allApps = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { info ->
                AppItem(
                    label = info.loadLabel(pm).toString(),
                    packageName = info.activityInfo.packageName,
                    icon = drawableToBitmap(info.loadIcon(pm))
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    // Список выбранных пользователем пакетов
    var pinnedPackages by remember {
        val saved = prefs.getStringSet("pinned", emptySet()) ?: emptySet()
        mutableStateOf(saved.toList())
    }

    val pinnedApps = remember(pinnedPackages, allApps) {
        pinnedPackages.mapNotNull { pkg -> allApps.find { it.packageName == pkg } }
    }

    // Навигация с геймпада (индекс выбранной карточки; последний индекс = кнопка "+")
    var selectedIndex by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    val totalItems = pinnedApps.size + 1

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in 0 until totalItems) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    fun toggleApp(pkg: String) {
        val updated = if (pinnedPackages.contains(pkg)) {
            pinnedPackages - pkg
        } else {
            pinnedPackages + pkg
        }
        pinnedPackages = updated
        prefs.edit().putStringSet("pinned", updated.toSet()).apply()
        if (selectedIndex >= updated.size + 1) {
            selectedIndex = updated.size
        }
    }

    fun launchApp(pkg: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E17))
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && !showAddDialog) {
                    when (event.key) {
                        Key.DirectionRight -> {
                            selectedIndex = (selectedIndex + 1) % totalItems
                            true
                        }
                        Key.DirectionLeft -> {
                            selectedIndex = (selectedIndex - 1 + totalItems) % totalItems
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.ButtonA -> {
                            if (selectedIndex < pinnedApps.size) {
                                launchApp(pinnedApps[selectedIndex].packageName)
                            } else {
                                showAddDialog = true
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Синяя волна на фоне
        BlueWaveBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Верхняя панель: название, часы, батарея
            TopStatusBar()

            // Центральная карусель игр + кнопка "+"
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(pinnedApps) { index, app ->
                    val isSelected = index == selectedIndex
                    AppCard(
                        app = app,
                        isSelected = isSelected,
                        onClick = {
                            selectedIndex = index
                            launchApp(app.packageName)
                        }
                    )
                }

                // Кнопка "+" в конце списка
                item {
                    val isSelected = selectedIndex == pinnedApps.size
                    AddButtonCard(
                        isSelected = isSelected,
                        onClick = {
                            selectedIndex = pinnedApps.size
                            showAddDialog = true
                        }
                    )
                }
            }

            // Подсказка внизу экрана
            Text(
                text = "D-Pad: Выбор   •   A / Нажатие: Запуск   •   [+]: Добавить или убрать игры",
                color = Color(0xFF7A8FA6),
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        // Диалог выбора приложений
        if (showAddDialog) {
            Dialog(onDismissRequest = {
                showAddDialog = false
                coroutineScope.launch { focusRequester.requestFocus() }
            }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF131B2E),
                    border = BorderStroke(1.dp, Color(0xFF2B6CB0)),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .fillMaxHeight(0.85f)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Выберите игры и эмуляторы",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = {
                                showAddDialog = false
                                coroutineScope.launch { focusRequester.requestFocus() }
                            }) {
                                Text("Готово", color = Color(0xFF4DA6FF), fontSize = 16.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(allApps) { _, app ->
                                val isPinned = pinnedPackages.contains(app.packageName)
                                Row(
modifier = Modifier
.fillMaxWidth()
.background(
if (isPinned) Color(0xFF1D3557) else Color(0xFF0E1422),
RoundedCornerShape(10.dp)
)
.clickable { toggleApp(app.packageName) }
.padding(10.dp),
verticalAlignment = Alignment.CenterVertically
) {
Image(
bitmap = app.icon.asImageBitmap(),
contentDescription = null,
modifier = Modifier.size(36.dp)
)
Spacer(modifier = Modifier.width(12.dp))
Text(
text = app.label,
color = Color.White,
fontSize = 16.sp,
modifier = Modifier.weight(1f)
)
Text(
text = if (isPinned) "✓ Добавлено" else "+ Добавить",
color = if (isPinned) Color(0xFF4ADE80) else Color(0xFF60A5FA),
fontSize = 14.sp,
fontWeight = FontWeight.Medium
)
}
}
}
}
}
}
}
}
}
@Composable
fun BlueWaveBackground() {
Canvas(modifier = Modifier.fillMaxSize()) {
val width = size.width
val height = size.height
val wavePath1 = Path().apply {
moveTo(0f, height * 0.65f)
cubicTo(
width * 0.3f, height * 0.45f,
width * 0.7f, height * 0.85f,
width, height * 0.6f
)
lineTo(width, height)
lineTo(0f, height)
close()
}
val wavePath2 = Path().apply {
moveTo(0f, height * 0.78f)
cubicTo(
width * 0.35f, height * 0.92f,
width * 0.65f, height * 0.55f,
width, height * 0.75f
)
lineTo(width, height)
lineTo(0f, height)
close()
}
drawPath(
path = wavePath1,
brush = Brush.verticalGradient(
colors = listOf(Color(0x331E6091), Color(0x050A0E17))
)
)
drawPath(
path = wavePath2,
brush = Brush.verticalGradient(
colors = listOf(Color(0x4D2563EB), Color(0x110A0E17))
)
)
}
}
@Composable
fun TopStatusBar() {
val context = LocalContext.current
var timeText by remember { mutableStateOf("") }
var batteryLevel by remember { mutableIntStateOf(100) }
// Обновление часов каждую секунду
LaunchedEffect(Unit) {
val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
while (true) {
timeText = formatter.format(Date())
delay(1000)
}
}
// Отслеживание уровня заряда батареи
DisposableEffect(context) {
val receiver = object : BroadcastReceiver() {
override fun onReceive(ctx: Context?, intent: Intent?) {
val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
if (level >= 0 && scale > 0) {
batteryLevel = (level * 100) / scale
}
}
}
context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
onDispose { context.unregisterReceiver(receiver) }
}
Row(
modifier = Modifier.fillMaxWidth(),
horizontalArrangement = Arrangement.SpaceBetween,
verticalAlignment = Alignment.CenterVertically
) {
Text(
text = "EMU CONSOLE",
color = Color(0xFF60A5FA),
fontSize = 18.sp,
fontWeight = FontWeight.Bold,
letterSpacing = 2.sp
)
Row(verticalAlignment = Alignment.CenterVertically) {
Text(
text = "🔋 $batteryLevel%",
color = Color(0xFFE2E8F0),
fontSize = 16.sp,
fontWeight = FontWeight.Medium
)
Spacer(modifier = Modifier.width(20.dp))
Text(
text = timeText,
color = Color.White,
fontSize = 18.sp,
fontWeight = FontWeight.Bold
)
}
}
}
@Composable
fun AppCard(app: AppItem, isSelected: Boolean, onClick: () -> Unit) {
val borderColor = if (isSelected) Color(0xFF3B82F6) else Color(0x3364748B)
val bgColor = if (isSelected) Color(0xFF1E293B) else Color(0xCC111827)
Surface(
shape = RoundedCornerShape(18.dp),
color = bgColor,
border = BorderStroke(if (isSelected) 3.dp else 1.dp, borderColor),
modifier = Modifier
.size(width = 150.dp, height = 170.dp)
.clickable { onClick() }
) {
Column(
modifier = Modifier
.fillMaxSize()
.padding(14.dp),
horizontalAlignment = Alignment.CenterHorizontally,
verticalArrangement = Arrangement.Center
) {
Image(
bitmap = app.icon.asImageBitmap(),
contentDescription = app.label,
modifier = Modifier.size(72.dp)
)
Spacer(modifier = Modifier.height(14.dp))
Text(
text = app.label,
color = Color.White,
fontSize = 14.sp,
fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
maxLines = 1,
overflow = TextOverflow.Ellipsis,
textAlign = TextAlign.Center
)
}
}
}
@Composable
fun AddButtonCard(isSelected: Boolean, onClick: () -> Unit) {
val borderColor = if (isSelected) Color(0xFF3B82F6) else Color(0x4D60A5FA)
val bgColor = if (isSelected) Color(0xFF1E3A5F) else Color(0x66111827)
Surface(
shape = RoundedCornerShape(18.dp),
color = bgColor,
border = BorderStroke(if (isSelected) 3.dp else 1.dp, borderColor),
modifier = Modifier
.size(width = 150.dp, height = 170.dp)
.clickable { onClick() }
) {
Column(
modifier = Modifier.fillMaxSize(),
horizontalAlignment = Alignment.CenterHorizontally,
verticalArrangement = Arrangement.Center
) {
Text(
text = "+",
color = Color(0xFF60A5FA),
fontSize = 48.sp,
fontWeight = FontWeight.Light
)
Spacer(modifier = Modifier.height(6.dp))
Text(
text = "Добавить",
color = Color(0xFF93C5FD),
fontSize = 14.sp
)
}
}
}
fun drawableToBitmap(drawable: Drawable): Bitmap {
if (drawable is BitmapDrawable && drawable.bitmap != null) {
return drawable.bitmap
}
val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 128
val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 128
val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
val canvas = Canvas(bitmap)
drawable.setBounds(0, 0, canvas.width, canvas.height)
drawable.draw(canvas)
return bitmap
}

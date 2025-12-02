package com.web.webide.ui.welcome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    // 核心状态
    val initialHsv = colorToHsv(initialColor)
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var alpha by remember { mutableFloatStateOf(initialColor.alpha) }

    val currentColor = Color.hsv(hue, saturation, value, alpha)

    // Hex 输入框状态 (透明度为1时只显示6位)
    var hexInput by remember(currentColor) {
        val isOpaque = currentColor.alpha >= 0.999f
        mutableStateOf(colorToHex(currentColor, !isOpaque).removePrefix("#"))
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // --- 1. 顶部：HEX 输入 + 预览 ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // HEX 输入
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "#",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        BasicTextField(
                            value = hexInput,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isLetterOrDigit() }.take(8).uppercase()
                                hexInput = filtered

                                // 解析逻辑
                                if (filtered.length == 6) {
                                    try {
                                        val color = hexToColor(filtered)
                                        val hsv = colorToHsv(color)
                                        hue = hsv[0]
                                        saturation = hsv[1]
                                        value = hsv[2]
                                        alpha = 1f
                                    } catch (e: Exception) { }
                                } else if (filtered.length == 8) {
                                    try {
                                        val color = hexToColor(filtered)
                                        val hsv = colorToHsv(color)
                                        hue = hsv[0]
                                        saturation = hsv[1]
                                        value = hsv[2]
                                        alpha = color.alpha
                                    } catch (e: Exception) { }
                                }
                            },
                            textStyle = MaterialTheme.typography.headlineMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                keyboardType = KeyboardType.Ascii
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.width(160.dp)
                        )
                    }

                    // 预览方块 (修改为方形圆角 8.dp)
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(4.dp)) // 方的
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                    ) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            drawCheckerboard()
                        }
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(currentColor)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // --- 2. 颜色选择核心区：左侧方块 + 右侧竖条 ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    // 左侧：饱和度/明度 (方块)
                    SatValPanel(
                        hue = hue,
                        saturation = saturation,
                        value = value,
                        onValChange = { s, v ->
                            saturation = s
                            value = v
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // 右侧：色相 (竖条)
                    VerticalHueSlider(
                        hue = hue,
                        onHueChange = { hue = it },
                        modifier = Modifier
                            .width(24.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // --- 3. 透明度滑块 (横向) ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                ) {
                    // 背景棋盘格
                    Canvas(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp))) {
                        drawCheckerboard()
                    }
                    // 渐变滑块 (竖条指示器)
                    HorizontalAlphaSlider(
                        alpha = alpha,
                        baseColor = Color.hsv(hue, saturation, value),
                        onAlphaChange = { alpha = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- 4. 详细数值区域 ---

                val r = (currentColor.red * 255).roundToInt()
                val g = (currentColor.green * 255).roundToInt()
                val b = (currentColor.blue * 255).roundToInt()
                val aInt = (currentColor.alpha * 255).roundToInt()

                // RGB Inputs
                ColorInputRow(label = "RGB") {
                    NumberInput("R", r, 255) { onColorUpdate(Color(it, g, b, aInt), { h, s, v, a -> hue=h; saturation=s; value=v; alpha=a }) }
                    NumberInput("G", g, 255) { onColorUpdate(Color(r, it, b, aInt), { h, s, v, a -> hue=h; saturation=s; value=v; alpha=a }) }
                    NumberInput("B", b, 255) { onColorUpdate(Color(r, g, it, aInt), { h, s, v, a -> hue=h; saturation=s; value=v; alpha=a }) }
                    NumberInput("A", aInt, 255) { onColorUpdate(Color(r, g, b, it), { h, s, v, a -> hue=h; saturation=s; value=v; alpha=a }) }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // HSV Inputs
                ColorInputRow(label = "HSV") {
                    NumberInput("H", hue.roundToInt(), 360) { hue = it.toFloat() }
                    NumberInput("S", (saturation * 100).roundToInt(), 100) { saturation = it / 100f }
                    NumberInput("V", (value * 100).roundToInt(), 100) { value = it / 100f }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // HSL Inputs
                val hsl = rgbToHsl(r, g, b)
                ColorInputRow(label = "HSL") {
                    NumberInput("H", hsl[0].roundToInt(), 360) {
                        val newColor = hslToColor(it.toFloat(), hsl[1], hsl[2], alpha)
                        onColorUpdate(newColor, { h, s, v, a -> hue=h; saturation=s; value=v; alpha=a })
                    }
                    NumberInput("S", (hsl[1] * 100).roundToInt(), 100) {
                        val newColor = hslToColor(hsl[0], it / 100f, hsl[2], alpha)
                        onColorUpdate(newColor, { h, s, v, a -> hue=h; saturation=s; value=v; alpha=a })
                    }
                    NumberInput("L", (hsl[2] * 100).roundToInt(), 100) {
                        val newColor = hslToColor(hsl[0], hsl[1], it / 100f, alpha)
                        onColorUpdate(newColor, { h, s, v, a -> hue=h; saturation=s; value=v; alpha=a })
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- 底部按钮 ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onColorSelected(currentColor) }) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

private fun onColorUpdate(newColor: Color, updateState: (Float, Float, Float, Float) -> Unit) {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(newColor.toArgb(), hsv)
    updateState(hsv[0], hsv[1], hsv[2], newColor.alpha)
}

// --- 组件：数值输入行 ---
@Composable
private fun ColorInputRow(
    label: String,
    content: @Composable RowScope.() -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .width(36.dp)
                // ▼▼▼ 修改处：添加顶部内边距，避开右侧的小标题高度 ▼▼▼
                .padding(top = 14.dp)
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

// --- 组件：大方块 (Sat/Val) ---
@Composable
private fun SatValPanel(
    hue: Float,
    saturation: Float,
    value: Float,
    onValChange: (Float, Float) -> Unit,
    modifier: Modifier
) {
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onValChange(
                            (offset.x / size.width).coerceIn(0f, 1f),
                            1f - (offset.y / size.height).coerceIn(0f, 1f)
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        onValChange(
                            (change.position.x / size.width).coerceIn(0f, 1f),
                            1f - (change.position.y / size.height).coerceIn(0f, 1f)
                        )
                    }
                }
        ) {
            drawRect(color = Color.hsv(hue, 1f, 1f))
            drawRect(brush = Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
            drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))

            // 指示器：改为方形
            val x = saturation * size.width
            val y = (1f - value) * size.height
            val cursorSize = 14f

            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(x - cursorSize/2, y - cursorSize/2),
                size = Size(cursorSize, cursorSize),
                style = Stroke(3f)
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(x - cursorSize/2, y - cursorSize/2),
                size = Size(cursorSize, cursorSize),
                style = Stroke(1.5f)
            )
        }
    }
}

// --- 组件：竖向色相滑块 ---
@Composable
private fun VerticalHueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier
) {
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onHueChange((offset.y / size.height * 360f).coerceIn(0f, 360f))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        onHueChange((change.position.y / size.height * 360f).coerceIn(0f, 360f))
                    }
                }
        ) {
            val colors = (0..360 step 10).map { Color.hsv(it.toFloat(), 1f, 1f) }
            drawRect(brush = Brush.verticalGradient(colors = colors))

            // 指示器：方形矩形条
            val y = (hue / 360f) * size.height
            val barHeight = 6f

            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(0f, y - barHeight/2),
                size = Size(size.width, barHeight),
                style = Stroke(2f)
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(0f, y - barHeight/2),
                size = Size(size.width, barHeight),
                style = Stroke(1f)
            )
        }
    }
}

// --- 组件：横向透明度滑块 ---
@Composable
private fun HorizontalAlphaSlider(
    alpha: Float,
    baseColor: Color,
    onAlphaChange: (Float) -> Unit,
    modifier: Modifier
) {
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onAlphaChange((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        onAlphaChange((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                }
        ) {
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, baseColor.copy(alpha = 1f))
                )
            )

            // 指示器：改为竖条 (Vertical Bar)
            val x = alpha * size.width
            val barWidth = 6f

            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(x - barWidth/2, 0f),
                size = Size(barWidth, size.height),
                style = Stroke(2f)
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(x - barWidth/2, 0f),
                size = Size(barWidth, size.height),
                style = Stroke(1f)
            )
        }
    }
}

@Composable
private fun RowScope.NumberInput(
    label: String,
    value: Int,
    max: Int,
    onValueChange: (Int) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var text by remember(value) { mutableStateOf(value.toString()) }

    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 10.sp
        )
        BasicTextField(
            value = text,
            onValueChange = {
                if (it.all { char -> char.isDigit() }) {
                    text = it
                    val num = it.toIntOrNull()
                    if (num != null) {
                        onValueChange(num.coerceIn(0, max))
                    }
                }
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                .padding(vertical = 4.dp)
        )
    }
}

// --- 辅助函数 ---

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCheckerboard() {
    val size = 20f
    val rows = (this.size.height / size).toInt() + 1
    val cols = (this.size.width / size).toInt() + 1

    for (i in 0 until rows) {
        for (j in 0 until cols) {
            val color = if ((i + j) % 2 == 0) Color.LightGray else Color.White
            drawRect(
                color = color,
                topLeft = Offset(j * size, i * size),
                size = Size(size, size)
            )
        }
    }
}

fun colorToHex(color: Color, includeAlpha: Boolean = false): String {
    val alpha = (color.alpha * 255).toInt()
    val red = (color.red * 255).toInt()
    val green = (color.green * 255).toInt()
    val blue = (color.blue * 255).toInt()

    return if (includeAlpha) {
        "#%02X%02X%02X%02X".format(alpha, red, green, blue)
    } else {
        "#%02X%02X%02X".format(red, green, blue)
    }
}

fun hexToColor(hex: String): Color {
    val cleanHex = hex.removePrefix("#")
    return if (cleanHex.length == 8) {
        val alpha = cleanHex.substring(0, 2).toInt(16)
        val red = cleanHex.substring(2, 4).toInt(16)
        val green = cleanHex.substring(4, 6).toInt(16)
        val blue = cleanHex.substring(6, 8).toInt(16)
        Color(red, green, blue, alpha)
    } else {
        Color(android.graphics.Color.parseColor("#$cleanHex"))
    }
}

fun colorToHsv(color: Color): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return hsv
}

fun rgbToHsl(r: Int, g: Int, b: Int): FloatArray {
    val rf = r / 255f
    val gf = g / 255f
    val bf = b / 255f
    val max = max(rf, max(gf, bf))
    val min = min(rf, min(gf, bf))
    var h: Float
    val s: Float
    val l = (max + min) / 2f

    if (max == min) {
        h = 0f
        s = 0f
    } else {
        val d = max - min
        s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        h = when (max) {
            rf -> (gf - bf) / d + (if (gf < bf) 6f else 0f)
            gf -> (bf - rf) / d + 2f
            bf -> (rf - gf) / d + 4f
            else -> 0f
        }
        h *= 60f
    }
    return floatArrayOf(h, s, l)
}

fun hslToColor(h: Float, s: Float, l: Float, a: Float): Color {
    val c = (1f - abs(2 * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f

    var r = 0f
    var g = 0f
    var b = 0f

    when {
        h < 60f -> { r = c; g = x; b = 0f }
        h < 120f -> { r = x; g = c; b = 0f }
        h < 180f -> { r = 0f; g = c; b = x }
        h < 240f -> { r = 0f; g = x; b = c }
        h < 300f -> { r = x; g = 0f; b = c }
        else -> { r = c; g = 0f; b = x }
    }

    return Color(r + m, g + m, b + m, a)
}
package com.web.webide.ui.editor

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlin.math.pow

/**
 * 编辑器配色方案管理器
 * 只同步基础颜色（背景、文本、选择等），保留 TextMate 语法高亮
 */
object EditorColorSchemeManager {

    /**
     * 将 Material 主题色应用到现有的 EditorColorScheme
     * 不替换整个配色方案，只更新基础颜色
     */
    fun applyThemeColors(scheme: EditorColorScheme, seedColor: Color, isDark: Boolean) {
        // 提取 HCT 色彩空间参数
        val (hue, chroma, _) = seedColor.toHct()
        val adjustedChroma = chroma.coerceAtLeast(48f)
        
        if (isDark) {
            applyDarkThemeColors(scheme, hue, adjustedChroma)
        } else {
            applyLightThemeColors(scheme, hue, adjustedChroma)
        }
    }

    /**
     * 应用深色主题的基础颜色
     */
    private fun applyDarkThemeColors(scheme: EditorColorScheme, hue: Float, chroma: Float) {
        // 背景色系
        val backgroundColor = hctToAndroidColor(hue, chroma * 0.05f, 6f)
        val surfaceColor = hctToAndroidColor(hue, chroma * 0.05f, 12f)
        val surfaceVariant = hctToAndroidColor(hue, chroma * 0.1f, 30f)
        
        // 文本色系
        val textSecondary = hctToAndroidColor(hue, chroma * 0.1f, 80f)
        
        // 主题色
        val primary = hctToAndroidColor(hue, chroma, 80f)
        
        scheme.apply {
            // 只更新基础背景和文本颜色
            setColor(EditorColorScheme.WHOLE_BACKGROUND, backgroundColor)
            setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, surfaceColor)
            setColor(EditorColorScheme.LINE_DIVIDER, surfaceVariant)
            setColor(EditorColorScheme.LINE_NUMBER, textSecondary)
            setColor(EditorColorScheme.LINE_NUMBER_CURRENT, primary)
            
            // 当前行高亮
            setColor(EditorColorScheme.CURRENT_LINE, adjustAlpha(surfaceVariant, 0.15f))
            
            // 选择相关
            setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, adjustAlpha(primary, 0.25f))
            setColor(EditorColorScheme.SELECTION_INSERT, primary)
            setColor(EditorColorScheme.SELECTION_HANDLE, primary)
            
            // 滚动条
            setColor(EditorColorScheme.SCROLL_BAR_THUMB, adjustAlpha(textSecondary, 0.3f))
            setColor(EditorColorScheme.SCROLL_BAR_THUMB_PRESSED, adjustAlpha(primary, 0.5f))
            
            // 自动完成窗口
            setColor(EditorColorScheme.COMPLETION_WND_BACKGROUND, surfaceColor)
            setColor(EditorColorScheme.COMPLETION_WND_CORNER, surfaceVariant)
            setColor(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT, adjustAlpha(primary, 0.2f))
            
            // 括号匹配
            setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND, primary)
            setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BACKGROUND, adjustAlpha(primary, 0.15f))
            setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_UNDERLINE, primary)
            
            // 下划线
            setColor(EditorColorScheme.UNDERLINE, primary)
            
            // 代码块线条
            setColor(EditorColorScheme.BLOCK_LINE, surfaceVariant)
            setColor(EditorColorScheme.BLOCK_LINE_CURRENT, primary)
            setColor(EditorColorScheme.SIDE_BLOCK_LINE, surfaceVariant)
        }
    }

    /**
     * 应用浅色主题的基础颜色
     */
    private fun applyLightThemeColors(scheme: EditorColorScheme, hue: Float, chroma: Float) {
        // 背景色系
        val backgroundColor = hctToAndroidColor(hue, chroma * 0.05f, 98f)
        val surfaceColor = hctToAndroidColor(hue, chroma * 0.05f, 94f)
        val surfaceVariant = hctToAndroidColor(hue, chroma * 0.1f, 90f)
        
        // 文本色系
        val textSecondary = hctToAndroidColor(hue, chroma * 0.1f, 30f)
        
        // 主题色
        val primary = hctToAndroidColor(hue, chroma, 40f)
        
        scheme.apply {
            // 只更新基础背景和文本颜色
            setColor(EditorColorScheme.WHOLE_BACKGROUND, backgroundColor)
            setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, surfaceColor)
            setColor(EditorColorScheme.LINE_DIVIDER, surfaceVariant)
            setColor(EditorColorScheme.LINE_NUMBER, textSecondary)
            setColor(EditorColorScheme.LINE_NUMBER_CURRENT, primary)
            
            // 当前行高亮
            setColor(EditorColorScheme.CURRENT_LINE, adjustAlpha(surfaceVariant, 0.3f))
            
            // 选择相关
            setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, adjustAlpha(primary, 0.25f))
            setColor(EditorColorScheme.SELECTION_INSERT, primary)
            setColor(EditorColorScheme.SELECTION_HANDLE, primary)
            
            // 滚动条
            setColor(EditorColorScheme.SCROLL_BAR_THUMB, adjustAlpha(textSecondary, 0.3f))
            setColor(EditorColorScheme.SCROLL_BAR_THUMB_PRESSED, adjustAlpha(primary, 0.5f))
            
            // 自动完成窗口
            setColor(EditorColorScheme.COMPLETION_WND_BACKGROUND, surfaceColor)
            setColor(EditorColorScheme.COMPLETION_WND_CORNER, surfaceVariant)
            setColor(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT, adjustAlpha(primary, 0.15f))
            
            // 括号匹配
            setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND, primary)
            setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BACKGROUND, adjustAlpha(primary, 0.1f))
            setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_UNDERLINE, primary)
            
            // 下划线
            setColor(EditorColorScheme.UNDERLINE, primary)
            
            // 代码块线条
            setColor(EditorColorScheme.BLOCK_LINE, surfaceVariant)
            setColor(EditorColorScheme.BLOCK_LINE_CURRENT, primary)
            setColor(EditorColorScheme.SIDE_BLOCK_LINE, surfaceVariant)
        }
    }

    // HCT 色彩空间转换
    private fun Color.toHct(): Triple<Float, Float, Float> {
        val r = red.toLinear()
        val g = green.toLinear()
        val b = blue.toLinear()
        
        val x = r * 0.4124564f + g * 0.3575761f + b * 0.1804375f
        val y = r * 0.2126729f + g * 0.7151522f + b * 0.0721750f
        val z = r * 0.0193339f + g * 0.1191920f + b * 0.9503041f
        
        val l = 116f * labF(y / 100f) - 16f
        val a = 500f * (labF(x / 95.047f) - labF(y / 100f))
        val bLab = 200f * (labF(y / 100f) - labF(z / 108.883f))
        
        val hue = Math.toDegrees(kotlin.math.atan2(bLab.toDouble(), a.toDouble())).toFloat()
        val hueNormalized = if (hue < 0) hue + 360f else hue
        val chroma = kotlin.math.sqrt(a * a + bLab * bLab)
        
        return Triple(hueNormalized, chroma, l)
    }

    private fun Float.toLinear(): Float {
        return if (this <= 0.04045f) this / 12.92f
        else ((this + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun labF(t: Float): Float {
        val delta = 6f / 29f
        return if (t > delta * delta * delta) t.pow(1f / 3f)
        else t / (3f * delta * delta) + 4f / 29f
    }

    private fun hctToAndroidColor(h: Float, c: Float, t: Float): Int {
        val hRad = Math.toRadians(h.toDouble())
        val a = (c * kotlin.math.cos(hRad)).toFloat()
        val b = (c * kotlin.math.sin(hRad)).toFloat()
        
        val fy = (t + 16f) / 116f
        val fx = a / 500f + fy
        val fz = fy - b / 200f
        
        val x = 95.047f * labFInv(fx)
        val y = 100f * labFInv(fy)
        val z = 108.883f * labFInv(fz)
        
        val r = (x * 3.2404542f - y * 1.5371385f - z * 0.4985314f) / 100f
        val g = (-x * 0.9692660f + y * 1.8760108f + z * 0.0415560f) / 100f
        val bColor = (x * 0.0556434f - y * 0.2040259f + z * 1.0572252f) / 100f
        
        val red = (r.fromLinear().coerceIn(0f, 1f) * 255).toInt()
        val green = (g.fromLinear().coerceIn(0f, 1f) * 255).toInt()
        val blue = (bColor.fromLinear().coerceIn(0f, 1f) * 255).toInt()
        
        return AndroidColor.rgb(red, green, blue)
    }

    private fun labFInv(t: Float): Float {
        val delta = 6f / 29f
        return if (t > delta) t * t * t
        else 3f * delta * delta * (t - 4f / 29f)
    }

    private fun Float.fromLinear(): Float {
        return if (this <= 0.0031308f) this * 12.92f
        else 1.055f * this.pow(1f / 2.4f) - 0.055f
    }

    private fun adjustAlpha(color: Int, alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        val r = AndroidColor.red(color)
        val g = AndroidColor.green(color)
        val b = AndroidColor.blue(color)
        return AndroidColor.argb(a, r, g, b)
    }
}
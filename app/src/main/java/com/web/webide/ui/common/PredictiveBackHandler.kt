package com.web.webide.ui.common

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.launch

/**
 * 一个处理预测性返回手势的 Composable 封装。
 * @param onBack 当返回手势完成时调用的回调。
 * @param content 需要应用预测性返回动画的内容 Composable。
 *                它接收一个 Modifier，必须将此 Modifier 应用于其根布局。
 */
@Composable
fun PredictiveBackHandler(
    onBack: () -> Unit,
    content: @Composable (modifier: Modifier) -> Unit
) {
    val scope = rememberCoroutineScope()
    val backPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val lifecycleOwner = LocalLifecycleOwner.current

    // 用于动画的状态
    val scale = remember { Animatable(1f) }
    val xOffset = remember { Animatable(0f) }

    val backCallback = remember {
        object : OnBackPressedCallback(true) {
            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                scope.launch {
                    // 手势开始时，立即给用户一个视觉反馈
                    scale.snapTo(0.95f)
                }
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                scope.launch {
                    // 根据手势进度更新动画
                    val progress = backEvent.progress
                    scale.snapTo(1f - (progress * 0.1f).coerceAtMost(0.1f)) // 最多缩小到0.9
                    xOffset.snapTo(backEvent.touchX.coerceAtLeast(0f) / 2) // 页面跟随手指轻微移动
                }
            }

            override fun handleOnBackPressed() {
                // 手势完成，执行真正的返回操作
                isEnabled = false // 防止重复调用
                onBack()
            }

            override fun handleOnBackCancelled() {
                // 手势取消，平滑地恢复到原始状态
                scope.launch {
                    scale.animateTo(1f, tween(150))
                    xOffset.animateTo(0f, tween(150))
                }
            }
        }
    }

    // 将回调与 Composable 的生命周期绑定
    DisposableEffect(lifecycleOwner, backPressedDispatcher) {
        backPressedDispatcher?.addCallback(lifecycleOwner, backCallback)
        onDispose {
            backCallback.remove()
        }
    }

    // 将动画效果通过 Modifier 应用到内容上
    content(
        Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            translationX = xOffset.value
        }
    )
}
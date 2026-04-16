/*
 * WebIDE - A powerful IDE for Android web development.
 * Copyright (C) 2025  如日中天  <3382198490@qq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.web.webide.ui.editor.aicoding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Job

import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.shape.CircleShape
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay

import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ListItem
import androidx.compose.material3.HorizontalDivider

import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import com.web.webide.R
import java.text.DateFormat

@Composable
private fun resolveAICodingMessageContent(
    rawContent: String,
    initialAssistantMessageText: String
): String {
    LocalConfiguration.current
    val context = LocalContext.current
    return when {
        rawContent == AICodingViewModel.INITIAL_ASSISTANT_MESSAGE_MARKER ||
            rawContent == AICodingViewModel.LEGACY_INITIAL_ASSISTANT_MESSAGE -> initialAssistantMessageText
        else -> AICodingLocalizedText.resolve(context, rawContent) ?: rawContent
    }
}

@Composable
fun AICodingPanel(
    state: AICodingState = rememberAICodingState(),
    viewModel: AICodingViewModel = viewModel()
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    val defaultChatTitleText = stringResource(R.string.ai_default_chat_title)
    val initialAssistantMessageText = stringResource(R.string.ai_initial_assistant_message)

    if (showSettings) {
        var newApiKey by remember { mutableStateOf(viewModel.apiKey) }
        var newBaseUrl by remember { mutableStateOf(viewModel.baseUrl) }
        var newModel by remember { mutableStateOf(viewModel.model) }
        var selectedProvider by remember { mutableStateOf(AICodingViewModel.ApiProvider.fromUrl(viewModel.baseUrl)) }
        val aiSettingsTitleText = stringResource(R.string.ai_settings_title)
        val providerPresetText = stringResource(R.string.ai_provider_preset)
        val apiKeyText = stringResource(R.string.ai_api_key)
        val baseUrlText = stringResource(R.string.ai_base_url)
        val baseUrlPlaceholderText = stringResource(R.string.ai_base_url_placeholder)
        val modelText = stringResource(R.string.ai_model)
        val modelPlaceholderText = stringResource(R.string.ai_model_placeholder)
        val noModelsText = stringResource(R.string.ai_no_models)
        val clearChatHistoryText = stringResource(R.string.ai_clear_chat_history)
        val saveText = stringResource(R.string.action_save)
        val cancelText = stringResource(R.string.action_cancel)
        val selectProviderContentDescription = stringResource(R.string.content_desc_select_provider)
        val selectModelContentDescription = stringResource(R.string.content_desc_select_model)
        val fetchModelsContentDescription = stringResource(R.string.content_desc_fetch_models)
        val providerOptions = listOf(
            AICodingViewModel.ApiProvider.OPENAI to stringResource(R.string.ai_provider_openai),
            AICodingViewModel.ApiProvider.DEEPSEEK to stringResource(R.string.ai_provider_deepseek),
            AICodingViewModel.ApiProvider.ANTHROPIC to stringResource(R.string.ai_provider_anthropic),
            AICodingViewModel.ApiProvider.GOOGLE to stringResource(R.string.ai_provider_google),
            AICodingViewModel.ApiProvider.ZHIPU to stringResource(R.string.ai_provider_zhipu),
            AICodingViewModel.ApiProvider.MOONSHOT to stringResource(R.string.ai_provider_moonshot),
            AICodingViewModel.ApiProvider.ALIYUN to stringResource(R.string.ai_provider_aliyun),
            AICodingViewModel.ApiProvider.BAIDU to stringResource(R.string.ai_provider_baidu),
            AICodingViewModel.ApiProvider.DOUBAO to stringResource(R.string.ai_provider_doubao),
            AICodingViewModel.ApiProvider.MISTRAL to stringResource(R.string.ai_provider_mistral),
            AICodingViewModel.ApiProvider.SILICONFLOW to stringResource(R.string.ai_provider_siliconflow),
            AICodingViewModel.ApiProvider.OPENROUTER to stringResource(R.string.ai_provider_openrouter),
            AICodingViewModel.ApiProvider.LMSTUDIO to stringResource(R.string.ai_provider_lmstudio),
            AICodingViewModel.ApiProvider.CUSTOM to stringResource(R.string.ai_provider_custom)
        )
        val providerLabels = providerOptions.toMap()
        val selectedProviderLabel = providerLabels[selectedProvider].orEmpty()

        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text(aiSettingsTitleText) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Provider Selection
                    Box(modifier = Modifier.fillMaxWidth()) {
                        var expanded by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = selectedProviderLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(providerPresetText) },
                            trailingIcon = {
                                IconButton(onClick = { expanded = !expanded }) {
                                    Icon(Icons.Default.ArrowDropDown, selectProviderContentDescription)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        // Invisible clickable overlay to ensure the whole field triggers dropdown
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .alpha(0f)
                                .clickable { expanded = true }
                        )
                        
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.heightIn(max = 300.dp)
                        ) {
                            providerOptions.forEach { (provider, providerLabel) ->
                                DropdownMenuItem(
                                    text = { Text(providerLabel) },
                                    onClick = {
                                        selectedProvider = provider
                                        expanded = false
                                        if (provider != AICodingViewModel.ApiProvider.CUSTOM) {
                                            newBaseUrl = provider.defaultBaseUrl
                                            newModel = provider.defaultModel
                                        }
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = newApiKey,
                        onValueChange = { newApiKey = it },
                        label = { Text(apiKeyText) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newBaseUrl,
                        onValueChange = { 
                            newBaseUrl = it 
                            // Check if custom or matches a provider
                            val matched = AICodingViewModel.ApiProvider.fromUrl(it)
                            selectedProvider = if (matched != AICodingViewModel.ApiProvider.CUSTOM) {
                                matched
                            } else {
                                AICodingViewModel.ApiProvider.CUSTOM
                            }
                        },
                        label = { Text(baseUrlText) },
                        placeholder = { Text(baseUrlPlaceholderText) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    // Model Selection with Dropdown and Fetch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            var expanded by remember { mutableStateOf(false) }
                            
                            OutlinedTextField(
                                value = newModel,
                                onValueChange = { newModel = it },
                                label = { Text(modelText) },
                                placeholder = { Text(modelPlaceholderText) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { expanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = selectModelContentDescription)
                                    }
                                }
                            )
                            
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.heightIn(max = 200.dp)
                            ) {
                                if (viewModel.availableModels.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text(noModelsText) },
                                        onClick = { expanded = false }
                                    )
                                } else {
                                    viewModel.availableModels.forEach { modelName ->
                                        DropdownMenuItem(
                                            text = { Text(modelName) },
                                            onClick = {
                                                newModel = modelName
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        IconButton(
                            onClick = { viewModel.fetchModels() },
                            enabled = !viewModel.isFetchingModels
                        ) {
                            if (viewModel.isFetchingModels) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = fetchModelsContentDescription)
                            }
                        }
                    }
                    
                    Button(
                        onClick = { viewModel.clearChat() },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                         Text(clearChatHistoryText)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateSettings(newApiKey, newBaseUrl, newModel)
                        showSettings = false
                    }
                ) {
                    Text(saveText)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text(cancelText)
                }
            }
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
    ) {
        val layoutMaxWidth = maxWidth
        val layoutMaxHeight = maxHeight
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        
        // Helper to convert Dp to Px
        val dpToPx = { dp: Dp -> with(density) { dp.toPx() } }
        val pxToDp = { px: Float -> with(density) { px.toDp() } }

        // Glow trigger state
        var isHoveringBorder by remember { mutableStateOf(false) }
        var isResizingLeft by remember { mutableStateOf(false) }
        var isResizingRight by remember { mutableStateOf(false) }
        
        // Combined visibility state
        val isInteractingWithBorder = state.isExpanded && (isHoveringBorder || isResizingLeft || isResizingRight)
        
        // Is actively resizing? Used to disable animations for instant feedback
        val isResizing = isResizingLeft || isResizingRight

        // Initialize position to right side center if not set
        LaunchedEffect(Unit) {
            if (state.windowOffset == Offset.Zero) {
                state.dockSide = DockSide.Right
                state.windowOffset = Offset(
                    x = maxWidthPx - dpToPx(32.dp), // Docked width
                    y = maxHeightPx / 2
                )
            }
        }

        val transition = updateTransition(targetState = state.isExpanded, label = "WindowTransition")

        // Animation values
        val expandProgress by transition.animateFloat(
            transitionSpec = { spring(stiffness = Spring.StiffnessLow) },
            label = "expandProgress"
        ) { expanded -> if (expanded) 1f else 0f }

        // Calculate Target Size based on Maximized State
        // We use animateDpAsState here to handle the Maximize/Restore animation
        // BUT we switch to snap() spec when dragging to ensure instant feedback and avoid drift
        val targetWidth = if (state.isMaximized) layoutMaxWidth else state.windowWidth
        val targetHeight = if (state.isMaximized) layoutMaxHeight else state.windowHeight

        val animatedTargetWidth by animateDpAsState(
            targetValue = targetWidth,
            animationSpec = if (isResizing) snap() else spring(stiffness = Spring.StiffnessLow),
            label = "animatedWidth"
        )

        val animatedTargetHeight by animateDpAsState(
            targetValue = targetHeight,
            animationSpec = if (isResizing) snap() else spring(stiffness = Spring.StiffnessLow),
            label = "animatedHeight"
        )

        val width = androidx.compose.ui.unit.lerp(
            32.dp,
            animatedTargetWidth,
            expandProgress
        )

        val height = androidx.compose.ui.unit.lerp(
            64.dp,
            animatedTargetHeight,
            expandProgress
        )
        
        val alpha by transition.animateFloat(
            transitionSpec = { tween(300) },
            label = "alpha"
        ) { expanded ->
             if (expanded) 1f else 0.9f
        }

        // Animated Offset - Split into X and Y for independent animations (X=Spring, Y=Decay)
        val animX = remember { Animatable(state.windowOffset.x) }
        val animY = remember { Animatable(state.windowOffset.y) }
        
        // Derived offset for calculations
        val currentOffset = Offset(animX.value, animY.value)

        // Dynamic Corner Radius Calculation based on Distance to Edge
        val maxRadius = 16.dp
        val maxRadiusPx = dpToPx(maxRadius)
        val thresholdRadius = 32.dp
        val thresholdRadiusPx = dpToPx(thresholdRadius)

        // Calculate distances to edges based on current animated offset
        val distanceToLeft = currentOffset.x
        val distanceToRight = maxWidthPx - (currentOffset.x + dpToPx(width))
        
        // Calculate radii: 0 when touching edge, maxRadius when distance > threshold
        val currentTopStartRadius = pxToDp(
            (distanceToLeft / thresholdRadiusPx).coerceIn(0f, 1f) * maxRadiusPx
        )
        val currentBottomStartRadius = pxToDp(
            (distanceToLeft / thresholdRadiusPx).coerceIn(0f, 1f) * maxRadiusPx
        )
        
        val currentTopEndRadius = pxToDp(
            (distanceToRight / thresholdRadiusPx).coerceIn(0f, 1f) * maxRadiusPx
        )
        val currentBottomEndRadius = pxToDp(
            (distanceToRight / thresholdRadiusPx).coerceIn(0f, 1f) * maxRadiusPx
        )

        // Arrow rotation animation
        // 0 degrees for Right dock (pointing Left)
        // 180 degrees for Left dock (pointing Right)
        val arrowRotation by animateFloatAsState(
            targetValue = if (state.dockSide == DockSide.Right) 0f else 180f,
            animationSpec = spring(stiffness = Spring.StiffnessLow)
        )

        // Glow trigger state - Defined at top of scope
        // var isHoveringBorder by remember { mutableStateOf(false) }
        // var isResizingLeft by remember { mutableStateOf(false) }
        // var isResizingRight by remember { mutableStateOf(false) }
        
        // Combined visibility state
        // val isInteractingWithBorder = isHoveringBorder || isResizingLeft || isResizingRight

        // Trigger glow on expand
        LaunchedEffect(state.isExpanded) {
            if (state.isExpanded) {
                isHoveringBorder = true
                delay(1000) 
                isHoveringBorder = false
            }
        }
        
        LaunchedEffect(state.isExpanded, state.dockSide, state.isMaximized) {
             if (state.isExpanded) {
                 if (state.isMaximized) {
                     launch { animX.animateTo(0f, spring(stiffness = Spring.StiffnessLow)) }
                     launch { animY.animateTo(0f, spring(stiffness = Spring.StiffnessLow)) }
                 } else {
                     // Calculate target position: Center or Last Known
                     val targetW = dpToPx(state.windowWidth)
                     val targetH = dpToPx(state.windowHeight)

                     // Always Default to Horizontal Center, Vertical follows Tab (Inertia friendly)
                     // We intentionally ignore lastFloatingPosition for X to ensure it always centers nicely on open
                     // unless the user is dragging it RIGHT NOW (which is handled by drag logic, not here)
                     val targetX: Float = (maxWidthPx - targetW) / 2f
                     val targetY: Float = animY.value
                     
                     // Ensure within bounds
                     val constrainedX = targetX.coerceIn(0f, (maxWidthPx - targetW).coerceAtLeast(0f))
                     val constrainedY = targetY.coerceIn(0f, (maxHeightPx - targetH).coerceAtLeast(0f))
                     
                     launch { animX.animateTo(constrainedX, spring(stiffness = Spring.StiffnessLow)) }
                     launch { animY.animateTo(constrainedY, spring(stiffness = Spring.StiffnessLow)) }
                 }
             } else {
                 // Slide to dock
                 // Only animate if NOT dragging (dragging controls offset directly)
                 if (!state.isDragging) {
                     val targetX = if (state.dockSide == DockSide.Right) maxWidthPx - dpToPx(32.dp) else 0f
                     val targetY = state.windowOffset.y.coerceIn(0f, (maxHeightPx - dpToPx(64.dp)).coerceAtLeast(0f))
                     launch { animX.animateTo(targetX, spring(stiffness = Spring.StiffnessLow)) }
                     launch { animY.animateTo(targetY, spring(stiffness = Spring.StiffnessLow)) }
                 }
             }
        }
        
        // Sync state offset for restoration
        if (!state.isDragging && !state.isMaximized) {
            state.windowOffset = Offset(animX.value, animY.value)
        }

        // The Window/Tab
        // We use a Box as the container to handle positioning and the outer glow effect
        // The Surface inside handles the content and clipping
        Box(
            modifier = Modifier
                .offset { IntOffset(animX.value.roundToInt(), animY.value.roundToInt()) }
                .size(width, height)
                .alpha(alpha)
                .borderGlow(
                    show = isInteractingWithBorder,
                    topStart = currentTopStartRadius,
                    topEnd = currentTopEndRadius,
                    bottomEnd = currentBottomEndRadius,
                    bottomStart = currentBottomStartRadius
                )
                // Border Touch Detector for Glow and Resize Hints
                .pointerInput(state.isExpanded) {
                    if (state.isExpanded && !state.isMaximized) {
                        awaitPointerEventScope {
                            var hideJob: Job? = null
                            while (true) {
                                val event = awaitPointerEvent()
                                val position = event.changes.first().position
                                val widthPx = size.width
                                val heightPx = size.height
                                val borderWidth = 24.dp.toPx()
                                
                                val isNearBorder = position.x < borderWidth || 
                                                   position.x > widthPx - borderWidth ||
                                                   position.y < borderWidth || 
                                                   position.y > heightPx - borderWidth
                                
                                if (isNearBorder) {
                                    hideJob?.cancel()
                                    isHoveringBorder = true
                                } else {
                                    // Delay hiding to prevent flickering when moving slightly out
                                    if (hideJob == null || !hideJob.isActive) {
                                        hideJob = scope.launch {
                                            delay(1000)
                                            isHoveringBorder = false
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (!state.isExpanded) {
                                state.isExpanded = true
                            }
                        }
                    )
                }
                .pointerInput(state.isExpanded) {
                    if (!state.isExpanded) {
                        // Custom drag handler to capture velocity for inertia
                        awaitPointerEventScope {
                            val velocityTracker = VelocityTracker()
                            
                            while (true) {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                state.isDragging = true
                                velocityTracker.resetTracking()
                                
                                var dragChanges = Offset.Zero
                                
                                drag(down.id) { change ->
                                    change.consume()
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    
                                    val dragAmount = change.position - change.previousPosition
                                    dragChanges += dragAmount
                                    
                                    scope.launch {
                                        val currentW = dpToPx(width)
                                        val currentH = dpToPx(height)
                                        
                                        val newX = (animX.value + dragAmount.x).coerceIn(0f, maxWidthPx - currentW)
                                        val newY = (animY.value + dragAmount.y).coerceIn(0f, maxHeightPx - currentH)
                                        
                                        animX.snapTo(newX)
                                        animY.snapTo(newY)
                                        
                                        val centerX = maxWidthPx / 2
                                        val newSide = if (newX + (currentW / 2) > centerX) DockSide.Right else DockSide.Left
                                        if (state.dockSide != newSide) {
                                            state.dockSide = newSide
                                        }
                                    }
                                }
                                
                                state.isDragging = false
                                val velocity = velocityTracker.calculateVelocity()
                                val velocityY = velocity.y
                                
                                // Final snap logic
                                val centerX = maxWidthPx / 2
                                val newSide = if (animX.value > centerX) DockSide.Right else DockSide.Left
                                state.dockSide = newSide
                                val targetX = if (newSide == DockSide.Right) maxWidthPx - dpToPx(32.dp) else 0f
                                
                                scope.launch {
                                    launch {
                                        animX.animateTo(
                                            targetValue = targetX, 
                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                        )
                                    }
                                    
                                    launch {
                                        // Inertia decay for Y
                                        val decay = exponentialDecay<Float>(frictionMultiplier = 2f)
                                        val currentH = dpToPx(height)
                                        val maxY = maxHeightPx - currentH
                                        
                                        animY.updateBounds(lowerBound = 0f, upperBound = maxY)
                                        try {
                                            animY.animateDecay(velocityY, decay)
                                        } finally {
                                            animY.updateBounds(lowerBound = null, upperBound = null)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(
                    topStart = currentTopStartRadius,
                    topEnd = currentTopEndRadius,
                    bottomEnd = currentBottomEndRadius,
                    bottomStart = currentBottomStartRadius
                ),
                color = if (state.isExpanded) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = if (state.isExpanded) 8.dp else 0.dp,
                shadowElevation = if (state.isExpanded) 8.dp else 0.dp
            ) {
                if (state.isExpanded) {
                    // Expanded Content
                    Box(modifier = Modifier.fillMaxSize()) {
                         Column(modifier = Modifier.fillMaxSize()) {
                            // Title Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp) // Taller title bar
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.surfaceContainerHigh,
                                            MaterialTheme.colorScheme.surfaceContainer
                                        )
                                    )
                                )
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { state.isDragging = true },
                                        onDragEnd = { state.isDragging = false },
                                        onDragCancel = { state.isDragging = false }
                                    ) { change, dragAmount ->
                                        if (state.isMaximized) return@detectDragGestures
                                        change.consume()
                                        scope.launch {
                                            val currentW = dpToPx(width)
                                            val currentH = dpToPx(height)
                                            
                                            val newX = (animX.value + dragAmount.x).coerceIn(0f, (maxWidthPx - currentW).coerceAtLeast(0f))
                                            val newY = (animY.value + dragAmount.y).coerceIn(0f, (maxHeightPx - currentH).coerceAtLeast(0f))
                                            
                                            animX.snapTo(newX)
                                            animY.snapTo(newY)
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.ai_panel_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Spacer(modifier = Modifier.weight(1f))
                            
                            // History/Session Button
                            IconButton(
                                onClick = { showHistory = !showHistory },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.List,
                                    contentDescription = stringResource(R.string.content_desc_history),
                                    tint = if (showHistory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Settings Button
                            IconButton(
                                onClick = { showSettings = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.action_settings),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            // Maximize/Restore Button
                            IconButton(
                                onClick = {
                                    if (state.isMaximized) {
                                        // Restore
                                        state.isMaximized = false
                                        state.windowWidth = state.restoreWidth
                                        state.windowHeight = state.restoreHeight
                                    } else {
                                        // Maximize
                                        state.restoreWidth = state.windowWidth
                                        state.restoreHeight = state.windowHeight
                                        state.isMaximized = true
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CropSquare,
                                    contentDescription = stringResource(R.string.content_desc_maximize),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            // Minimize/Dock Button
                            IconButton(onClick = { state.isExpanded = false }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.content_desc_minimize),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        
                        // Content Area
                        Column(modifier = Modifier.weight(1f)) {
                            // Chat History
                            val listState = rememberLazyListState()
                            
                            // Auto-scroll to bottom when new messages arrive
                            LaunchedEffect(viewModel.messages.size) {
                                if (viewModel.messages.isNotEmpty()) {
                                    listState.animateScrollToItem(viewModel.messages.size - 1)
                                }
                            }

                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 16.dp),
                                reverseLayout = false
                            ) {
                                items(
                                    items = viewModel.messages,
                                    key = { it.id }
                                ) { message ->
                                    val isUser = message.role == "user"
                                    val isError = message.role == "error"
                                    val messageContent = resolveAICodingMessageContent(
                                        rawContent = message.content,
                                        initialAssistantMessageText = initialAssistantMessageText
                                    )
                                    
                                    // Animation for new messages
                                    val alphaAnim = remember { Animatable(0f) }
                                    val offsetAnim = remember { Animatable(50f) }
                                    
                                    LaunchedEffect(Unit) {
                                        launch { alphaAnim.animateTo(1f, spring(stiffness = Spring.StiffnessLow)) }
                                        launch { offsetAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                    }
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .alpha(alphaAnim.value)
                                            .offset(y = offsetAnim.value.dp),
                                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(
                                                topStart = 16.dp,
                                                topEnd = 16.dp,
                                                bottomStart = if (isUser) 16.dp else 4.dp,
                                                bottomEnd = if (isUser) 4.dp else 16.dp
                                            ),
                                            color = when {
                                                isError -> MaterialTheme.colorScheme.errorContainer
                                                isUser -> MaterialTheme.colorScheme.primaryContainer
                                                else -> MaterialTheme.colorScheme.secondaryContainer
                                            },
                                            modifier = Modifier.widthIn(max = 280.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                // Reasoning Content Section
                                                    if (!message.reasoningContent.isNullOrEmpty()) {
                                                        var isReasoningExpanded by remember { mutableStateOf(false) }
                                                        
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(bottom = 8.dp)
                                                                .background(
                                                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                                                    shape = RoundedCornerShape(8.dp)
                                                                )
                                                                .padding(8.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .clickable { isReasoningExpanded = !isReasoningExpanded },
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(
                                                                    text = stringResource(R.string.ai_thinking_process),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.secondary,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                                Spacer(modifier = Modifier.weight(1f))
                                                                Icon(
                                                                    imageVector = if (isReasoningExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                                    contentDescription = stringResource(R.string.content_desc_toggle_reasoning),
                                                                    modifier = Modifier.size(16.dp),
                                                                    tint = MaterialTheme.colorScheme.secondary
                                                                )
                                                            }
                                                            
                                                            if (isReasoningExpanded) {
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                Text(
                                                                    text = message.reasoningContent,
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    fontFamily = FontFamily.Monospace,
                                                                    fontSize = 11.sp
                                                                )
                                                            }
                                                        }
                                                    }
                                                    
                                                    // Main Content
                                                    MarkdownText(
                                                        markdown = messageContent,
                                                        color = when {
                                                            isError -> MaterialTheme.colorScheme.onErrorContainer
                                                            isUser -> MaterialTheme.colorScheme.onPrimaryContainer
                                                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                }
                            }
                            
                            // Input Area
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // Add extra bottom padding to prevent overlap with the gesture bar
                                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 36.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text(stringResource(R.string.ai_ask_placeholder)) },
                                    singleLine = false,
                                    maxLines = 3,
                                    shape = RoundedCornerShape(24.dp)
                                )
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                val isSending = viewModel.isLoading
                                val sendScale by animateFloatAsState(if (isSending) 0.8f else 1f)
                                
                                IconButton(
                                    onClick = {
                                        if (inputText.isNotBlank()) {
                                            viewModel.sendMessage(inputText)
                                            inputText = ""
                                        }
                                    },
                                    enabled = !viewModel.isLoading && inputText.isNotBlank(),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .scale(sendScale) // Scale animation on click
                                        .background(
                                            color = if (isSending) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary, 
                                            shape = CircleShape
                                        )
                                ) {
                                    if (isSending) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = stringResource(R.string.content_desc_send),
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // History Overlay
                    if (showHistory) {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 48.dp) // Below Title Bar
                                .zIndex(10f),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 4.dp
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Button(
                                    onClick = { 
                                        viewModel.createNewSession()
                                        showHistory = false 
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.ai_new_chat))
                                }
                                
                                HorizontalDivider()
                                
                                LazyColumn(modifier = Modifier.weight(1f)) {
                                    items(
                                        items = viewModel.sessions,
                                        key = { it.id }
                                    ) { session ->
                                        val isSelected = session.id == viewModel.currentSessionId
                                        val sessionTitle = if (
                                            session.title == AICodingViewModel.DEFAULT_CHAT_TITLE_MARKER ||
                                            session.title == AICodingViewModel.LEGACY_DEFAULT_CHAT_TITLE
                                        ) {
                                            defaultChatTitleText
                                        } else {
                                            session.title
                                        }
                                        ListItem(
                                            headlineContent = { 
                                                Text(
                                                    sessionTitle,
                                                    maxLines = 1, 
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                ) 
                                            },
                                            supportingContent = { 
                                                Text(
                                                    DateFormat.getDateTimeInstance(
                                                        DateFormat.SHORT,
                                                        DateFormat.SHORT,
                                                        java.util.Locale.getDefault()
                                                    ).format(java.util.Date(session.timestamp)),
                                                    style = MaterialTheme.typography.bodySmall
                                                ) 
                                            },
                                            modifier = Modifier
                                                .clickable { 
                                                    viewModel.loadSession(session.id)
                                                    showHistory = false 
                                                }
                                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent),
                                            trailingContent = {
                                                IconButton(onClick = { viewModel.deleteSession(session.id) }) {
                                                    Icon(
                                                        Icons.Default.Delete, 
                                                        contentDescription = stringResource(R.string.content_desc_delete),
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                                    }
                                }
                            }
                        }
                    }

                    // Gesture Navigation Indicator (Interactive)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            // Maximize touch width to 80% of window and decent height
                            .fillMaxWidth(0.8f)
                            .height(32.dp) 
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    if (state.isMaximized) return@detectDragGestures
                                    change.consume()
                                    scope.launch {
                                        val currentW = dpToPx(width)
                                        val currentH = dpToPx(height)
                                        val constrainedX = (animX.value + dragAmount.x).coerceIn(0f, (maxWidthPx - currentW).coerceAtLeast(0f))
                                        val constrainedY = (animY.value + dragAmount.y).coerceIn(0f, (maxHeightPx - currentH).coerceAtLeast(0f))
                                        
                                        animX.snapTo(constrainedX)
                                        animY.snapTo(constrainedY)
                                        
                                        // Update last known floating position
                                        state.lastFloatingPosition = Offset(constrainedX, constrainedY)
                                    }
                                }
                            },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        // The visible indicator bar
                        Box(
                            modifier = Modifier
                                .padding(bottom = 8.dp) // Visual offset
                                .width(80.dp) // Slightly wider visual bar
                                .height(5.dp) // Slightly thicker
                                .background(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    shape = CircleShape
                                )
                        )
                    }
                    }
                } else {
                    // Collapsed Content
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.content_desc_expand),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.rotate(arrowRotation)
                        )
                    }
                }
            }
        }

        // Dynamic Resize Handles (Bottom Left & Right) - MOVED OUTSIDE WINDOW BOX TO PREVENT CLIPPING
        if (state.isExpanded) {
            val handleSize = 64.dp
            val handleSizePx = dpToPx(handleSize)
            val windowX = animX.value
            val windowY = animY.value
            val windowW = dpToPx(width)
            val windowH = dpToPx(height)

            // Bottom Right Handle
            Box(
                modifier = Modifier
                    .offset { 
                        IntOffset(
                            x = (windowX + windowW - handleSizePx / 2).roundToInt(),
                            y = (windowY + windowH - handleSizePx / 2).roundToInt()
                        )
                    }
                    .size(handleSize)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { isResizingRight = true },
                            onDragEnd = { isResizingRight = false },
                            onDragCancel = { isResizingRight = false }
                        ) { change, dragAmount ->
                            if (state.isMaximized) return@detectDragGestures
                            change.consume()
                            val newW = (state.windowWidth + pxToDp(dragAmount.x)).coerceAtLeast(200.dp)
                            val newH = (state.windowHeight + pxToDp(dragAmount.y)).coerceAtLeast(200.dp)
                            state.windowWidth = newW
                            state.windowHeight = newH
                        }
                    }
                    .drawBehind {
                        // Show if hovering OR resizing this side, BUT hide if resizing the OTHER side
                        val shouldShow = (isHoveringBorder || isResizingRight) && !isResizingLeft
                        
                        if (shouldShow) {
                            val strokeWidth = 5.dp.toPx()
                            val color = Color.White
                            
                            // Visual parameters
                            val gap = 4.dp.toPx() // Distance from window corner
                            val handleLength = 24.dp.toPx()
                            // Dynamic corner radius following the window
                            val cornerRadius = currentBottomEndRadius.toPx()
                            // Calculate effective outer radius for concentric wrapping
                            // If window has radius R, outer curve at distance G should have radius R + G
                            val effectiveRadius = if (cornerRadius > 0) cornerRadius + gap else 0f
                            
                            // Center of the box is the Window Corner
                            val cx = size.width / 2
                            val cy = size.height / 2
                            
                            // Draw L-shape wrapping Bottom-Right corner
                            // Vertical line from top
                            val startY = cy - handleLength
                            val verticalX = cx + gap
                            
                            val path = Path().apply {
                                moveTo(verticalX, startY)
                                lineTo(verticalX, cy + gap - effectiveRadius)
                                // Arc around the corner
                                if (effectiveRadius > 0) {
                                    quadraticTo(
                                        verticalX,
                                        cy + gap,
                                        verticalX - effectiveRadius,
                                        cy + gap
                                    )
                                } else {
                                    lineTo(verticalX, cy + gap)
                                    lineTo(verticalX, cy + gap) // Sharp corner
                                }
                                // Horizontal line to left
                                lineTo(cx - handleLength, cy + gap)
                            }
                            
                            drawPath(
                                path = path,
                                color = color,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    }
            )

            // Bottom Left Handle
            Box(
                modifier = Modifier
                    .offset { 
                        IntOffset(
                            x = (windowX - handleSizePx / 2).roundToInt(),
                            y = (windowY + windowH - handleSizePx / 2).roundToInt()
                        )
                    }
                    .size(handleSize)
                    .pointerInput(Unit) {
                        var initialX = 0f
                        var initialWidthPx = 0f
                        var totalDragX = 0f
                        
                        detectDragGestures(
                            onDragStart = { 
                                isResizingLeft = true
                                initialX = animX.value
                                initialWidthPx = dpToPx(state.windowWidth)
                                totalDragX = 0f
                            },
                            onDragEnd = { isResizingLeft = false },
                            onDragCancel = { isResizingLeft = false }
                        ) { change, dragAmount ->
                            if (state.isMaximized) return@detectDragGestures
                            change.consume()
                            
                            totalDragX += dragAmount.x
                            val minWidthPx = dpToPx(200.dp)
                            
                            // Calculate target state based on initial state + accumulation
                            // This prevents rounding errors from accumulating frame-by-frame
                            var targetWidthPx = initialWidthPx - totalDragX
                            var targetX = initialX + totalDragX
                            
                            if (targetWidthPx < minWidthPx) {
                                // Constrain logic
                                targetWidthPx = minWidthPx
                                // If width is constrained, X must be anchored to the Right Edge
                                // Right Edge = InitialX + InitialWidth
                                targetX = (initialX + initialWidthPx) - minWidthPx
                            }
                            
                            // Apply updates
                            state.windowWidth = pxToDp(targetWidthPx)
                            // Height logic remains simple delta-based for now as it doesn't affect anchoring
                            state.windowHeight = (state.windowHeight + pxToDp(dragAmount.y)).coerceAtLeast(200.dp)
                            
                            scope.launch {
                                animX.snapTo(targetX)
                            }
                        }
                    }
                    .drawBehind {
                        // Show if hovering OR resizing this side, BUT hide if resizing the OTHER side
                        val shouldShow = (isHoveringBorder || isResizingLeft) && !isResizingRight

                        if (shouldShow) {
                            val strokeWidth = 5.dp.toPx()
                            val color = Color.White
                            
                            // Visual parameters
                            val gap = 4.dp.toPx()
                            val handleLength = 24.dp.toPx()
                            // Dynamic corner radius following the window
                            val cornerRadius = currentBottomStartRadius.toPx()
                            // Calculate effective outer radius for concentric wrapping
                            val effectiveRadius = if (cornerRadius > 0) cornerRadius + gap else 0f
                            
                            // Center of the box is the Window Corner
                            val cx = size.width / 2
                            val cy = size.height / 2
                            
                            // Draw L-shape wrapping Bottom-Left corner
                            // Vertical line from top
                            val startY = cy - handleLength
                            val verticalX = cx - gap
                            
                            val path = Path().apply {
                                moveTo(verticalX, startY)
                                lineTo(verticalX, cy + gap - effectiveRadius)
                                // Arc around the corner (curving right)
                                if (effectiveRadius > 0) {
                                    quadraticTo(
                                        verticalX,
                                        cy + gap,
                                        verticalX + effectiveRadius,
                                        cy + gap
                                    )
                                } else {
                                    lineTo(verticalX, cy + gap)
                                    lineTo(verticalX, cy + gap) // Sharp corner
                                }
                                // Horizontal line to right
                                lineTo(cx + handleLength, cy + gap)
                            }
                            
                            drawPath(
                                path = path,
                                color = color,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    }
            )
        }
    }
}

// Continuous Glow Effect
fun Modifier.borderGlow(
    show: Boolean,
    topStart: Dp,
    topEnd: Dp,
    bottomEnd: Dp,
    bottomStart: Dp
): Modifier = composed {
    val density = LocalDensity.current
    val alphaAnim = remember { Animatable(0f) }
    val rotateAnim = remember { Animatable(0f) }
    
    // Manage visibility and rotation
    LaunchedEffect(show) {
        if (show) {
            // Fade in
            launch { alphaAnim.animateTo(1f, tween(300)) }
            // Continuous rotation
            launch {
                while (true) {
                    rotateAnim.animateTo(
                        targetValue = rotateAnim.value + 360f,
                        animationSpec = tween(2000, easing = LinearEasing)
                    )
                }
            }
        } else {
            // Fade out
            alphaAnim.animateTo(0f, tween(500))
        }
    }
    
    val colors = listOf(
        Color(0xFF00FFFF), // Cyan
        Color(0xFF0000FF), // Blue
        Color(0xFFFF00FF), // Magenta
        Color(0xFF00FFFF)  // Cyan
    )
    
    val colorInts = remember(colors) { colors.map { it.toArgb() }.toIntArray() }
    
    drawBehind {
        if (alphaAnim.value > 0f) {
            val topStartPx = with(density) { topStart.toPx() }
            val topEndPx = with(density) { topEnd.toPx() }
            val bottomEndPx = with(density) { bottomEnd.toPx() }
            val bottomStartPx = with(density) { bottomStart.toPx() }

            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(offset = Offset.Zero, size = size),
                        topLeft = CornerRadius(topStartPx),
                        topRight = CornerRadius(topEndPx),
                        bottomRight = CornerRadius(bottomEndPx),
                        bottomLeft = CornerRadius(bottomStartPx)
                    )
                )
            }
            
            val shader = android.graphics.SweepGradient(
                center.x,
                center.y,
                colorInts,
                null
            )
            val matrix = android.graphics.Matrix()
            matrix.setRotate(rotateAnim.value, center.x, center.y)
            shader.setLocalMatrix(matrix)
            
            drawPath(
                path = path,
                brush = ShaderBrush(shader),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
                alpha = alphaAnim.value
            )
        }
    }
}

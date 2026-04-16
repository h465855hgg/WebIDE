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
package com.web.webide.ui.projects

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.navigation.NavController
import com.web.webide.ui.components.ColorPickerDialog
import com.web.webide.ui.editor.viewmodel.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import androidx.core.net.toUri
import com.web.webide.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectConfigScreen(
    navController: NavController,
    filePath: String,
    viewModel: EditorViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = File(filePath)
    val projectDir = file.parentFile
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    // States
    var appName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var versionName by remember { mutableStateOf("") }
    var versionCode by remember { mutableStateOf("1") }
    var encryptionEnabled by remember { mutableStateOf(true) }
    var targetUrl by remember { mutableStateOf("") }
    var iconPath by remember { mutableStateOf("icon.png") }
    
    // Display
    var orientation by remember { mutableStateOf("portrait") }
    var isFullscreen by remember { mutableStateOf(false) }
    var statusBarColor by remember { mutableStateOf("#FFFFFF") }
    var isDarkStatusText by remember { mutableStateOf(true) }
    
    // Webview
    var zoomEnabled by remember { mutableStateOf(false) }
    
    // Signing
    var enableSigning by remember { mutableStateOf(false) }
    var keystorePath by remember { mutableStateOf("") }
    var keystoreAlias by remember { mutableStateOf("") }
    var storePassword by remember { mutableStateOf("") }
    var keyPassword by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(true) }
    var showColorPicker by remember { mutableStateOf(false) }

    // File Pickers
    val iconPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { iconPath = it.toString() }
    }
    
    val keystorePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { keystorePath = it.toString() }
    }

    // Load Data
    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                if (file.exists()) {
                    val content = file.readText()
                    val json = JSONObject(content)
                    
                    appName = json.optString("name", "MyApp")
                    packageName = json.optString("package", "com.example.app")
                    versionName = json.optString("versionName", "1.0.0")
                    versionCode = json.optInt("versionCode", 1).toString()
                    encryptionEnabled = json.optBoolean("encryption", true)
                    targetUrl = json.optString("targetUrl", "index.html")
                    iconPath = json.optString("icon", "icon.png")
                    
                    orientation = json.optString("orientation", "portrait")
                    isFullscreen = json.optBoolean("fullscreen", false)
                    
                    val sb = json.optJSONObject("statusBar")
                    if (sb != null) {
                        statusBarColor = sb.optString("backgroundColor", "#FFFFFF")
                        val style = sb.optString("style", "dark")
                        isDarkStatusText = style == "dark" || style == "default"
                    }
                    
                    val wv = json.optJSONObject("webview")
                    if (wv != null) {
                        zoomEnabled = wv.optBoolean("zoomEnabled", false)
                    }
                    
                    val sign = json.optJSONObject("signing")
                    if (sign != null) {
                        enableSigning = true
                        keystorePath = sign.optString("keystore", "")
                        keystoreAlias = sign.optString("alias", "")
                        storePassword = sign.optString("storePassword", "")
                        keyPassword = sign.optString("keyPassword", "")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        isLoading = false
    }

    // Auto-scroll when signing is enabled
    LaunchedEffect(enableSigning) {
        if (enableSigning) {
            kotlinx.coroutines.delay(100) // Wait for layout to start expanding
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // Helper to get file name from URI
    fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            result = cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result.substring(cut + 1)
            }
        }
        return result
    }

    // Helper to copy file
    fun copyFileToProject(uri: Uri, destName: String): String {
        try {
            val destFile = File(projectDir, destName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            return destName
        } catch (e: Exception) {
            e.printStackTrace()
            return destName // Fail gracefully
        }
    }

    // Save Logic
    fun handleSave() {
        scope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                try {
                    val root = JSONObject()
                    root.put("name", appName)
                    root.put("package", packageName)
                    root.put("versionName", versionName)
                    root.put("versionCode", versionCode.toIntOrNull() ?: 1)
                    root.put("encryption", encryptionEnabled)
                    root.put("targetUrl", targetUrl)
                    
                    // Handle Icon Copy if it's a URI
                    var finalIconPath = iconPath
                    if (iconPath.startsWith("content://")) {
                        finalIconPath = copyFileToProject(iconPath.toUri(), "icon.png")
                    }
                    root.put("icon", finalIconPath)
                    
                    root.put("orientation", orientation)
                    root.put("fullscreen", isFullscreen)
                    
                    val sb = JSONObject()
                    sb.put("backgroundColor", statusBarColor)
                    sb.put("style", if (isDarkStatusText) "dark" else "light")
                    root.put("statusBar", sb)
                    
                    val wv = JSONObject()
                    wv.put("zoomEnabled", zoomEnabled)
                    root.put("webview", wv)
                    
                    if (enableSigning) {
                        val sign = JSONObject()
                        var finalKeystorePath = keystorePath
                        if (keystorePath.startsWith("content://")) {
                             val uri = keystorePath.toUri()
                             // Try to preserve original filename, default to keystore.jks if not found
                             val originalName = getFileName(uri) ?: "keystore.jks"
                             // Ensure we don't accidentally overwrite if user didn't mean to (though usually they do)
                             // For now, we overwrite or create new.
                             finalKeystorePath = copyFileToProject(uri, originalName)
                        }
                        sign.put("keystore", finalKeystorePath)
                        sign.put("alias", keystoreAlias)
                        sign.put("storePassword", storePassword)
                        sign.put("keyPassword", keyPassword)
                        root.put("signing", sign)
                    }
                    
                    root.put("permissions", org.json.JSONArray().put("android.permission.INTERNET"))

                    val jsonString = root.toString(2)
                    
                    // Write to file
                    file.writeText(jsonString)
                    
                    // Update Editor with Undo
                    withContext(Dispatchers.Main) {
                        viewModel.updateCodeWithUndo(jsonString)
                        isLoading = false
                        navController.popBackStack()
                    }
                } catch (e: Exception) {
                        e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        snackbarHostState.showSnackbar(context.getString(R.string.project_config_save_failed, e.message))
                    }
                }
            }
        }
    }

    if (showColorPicker) {
        val initialColorObj = try {
            Color(statusBarColor.toColorInt())
        } catch (_: Exception) {
            Color.White
        }
        
        ColorPickerDialog(
            initialColor = initialColorObj,
            onDismiss = { showColorPicker = false },
            onColorSelected = { color ->
                statusBarColor = String.format("#%06X", (0xFFFFFF and color.toArgb()))
                showColorPicker = false
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.project_config_title), fontSize = 18.sp, fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { handleSave() }, enabled = !isLoading) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, stringResource(R.string.action_save))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            
            // --- Basic Info ---
            ConfigSectionTitle(stringResource(R.string.new_project_app_info))
            CleanTextField(appName, { appName = it }, stringResource(R.string.project_config_app_name),
                Icons.AutoMirrored.Outlined.Label
            )
            Spacer(Modifier.height(16.dp))
            CleanTextField(packageName, { packageName = it }, stringResource(R.string.new_project_package_name), Icons.Outlined.Info, keyboardType = KeyboardType.Ascii)
            Spacer(Modifier.height(16.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CleanTextField(versionName, { versionName = it }, stringResource(R.string.new_project_version_name), null, isSmall = true, modifier = Modifier.weight(1f))
                CleanTextField(versionCode, { versionCode = it }, stringResource(R.string.new_project_version_code), null, isSmall = true, modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number)
            }
            
            Spacer(Modifier.height(16.dp))
            CleanTextField(targetUrl, { targetUrl = it }, stringResource(R.string.project_config_target_url), Icons.Outlined.Link, keyboardType = KeyboardType.Uri)
            
            Spacer(Modifier.height(16.dp))
            FileSelectorRow(stringResource(R.string.new_project_app_icon), iconPath, { iconPickerLauncher.launch("image/*") }, icon = Icons.Outlined.Image)
            
            Spacer(Modifier.height(8.dp))
            SwitchRow(stringResource(R.string.new_project_resource_encryption), encryptionEnabled) { encryptionEnabled = it }

            // --- Display ---
            ConfigSectionTitle(stringResource(R.string.new_project_display_theme))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Text(stringResource(R.string.new_project_screen_orientation), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(16.dp))
                Box(Modifier.weight(1f)) {
                    AnimatedOrientationSelector(
                        currentValue = orientation,
                        onValueChange = { orientation = it }
                    )
                }
            }
            
            Spacer(Modifier.height(4.dp))
            SwitchRow(stringResource(R.string.new_project_fullscreen), isFullscreen) { isFullscreen = it }
            SwitchRow(stringResource(R.string.new_project_webview_zoom), zoomEnabled) { zoomEnabled = it }
            
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CleanTextField(
                    value = statusBarColor,
                    onValueChange = { statusBarColor = it },
                    placeholder = stringResource(R.string.new_project_status_bar_color),
                    icon = Icons.Outlined.Palette,
                    isSmall = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(try { Color(statusBarColor.toColorInt()) } catch (_: Exception) { Color.Transparent })
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .clickable { showColorPicker = true }
                )
            }
            
            Spacer(Modifier.height(8.dp))
            SwitchRow(stringResource(R.string.new_project_dark_status_bar_text), isDarkStatusText) { isDarkStatusText = it }

            // --- Signing ---
            ConfigSectionTitle(stringResource(R.string.new_project_signing))
            SwitchRow(stringResource(R.string.new_project_enable_signing), enableSigning) { enableSigning = it }

            AnimatedVisibility(
                visible = enableSigning,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    FileSelectorRow(stringResource(R.string.new_project_keystore_file), keystorePath, { keystorePickerLauncher.launch("*/*") })
                    Spacer(Modifier.height(16.dp))
                    CleanTextField(keystoreAlias, { keystoreAlias = it }, stringResource(R.string.new_project_alias), Icons.Outlined.Badge, isSmall = true)
                    Spacer(Modifier.height(16.dp))
                    CleanTextField(storePassword, { storePassword = it }, stringResource(R.string.new_project_store_password), Icons.Outlined.Lock, isSmall = true, isPassword = true)
                    Spacer(Modifier.height(16.dp))
                    CleanTextField(keyPassword, { keyPassword = it }, stringResource(R.string.new_project_key_password), Icons.Outlined.VpnKey, isSmall = true, isPassword = true)
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

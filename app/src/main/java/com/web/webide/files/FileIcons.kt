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

package com.web.webide.files

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Css
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import compose.icons.FontAwesomeIcons
import compose.icons.SimpleIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Database
import compose.icons.simpleicons.*

object FileIcons {

    // Define colors for file types
    private val ColorFolder = Color(0xFFFFCA28) // Amber 400
    private val ColorAndroid = Color(0xFF3DDC84) // Android Green
    private val ColorKotlin = Color(0xFF7F52FF) // Kotlin Purple
    private val ColorJava = Color(0xFFF89820) // Java Orange
    private val ColorPython = Color(0xFF3776AB) // Python Blue
    private val ColorJavaScript = Color(0xFFF1E05A) // JS Yellow
    private val ColorTypeScript = Color(0xFF3178C6) // TS Blue
    private val ColorHtml = Color(0xFFE34F26) // HTML Orange
    private val ColorCss = Color(0xFF563D7C) // CSS Purple
    private val ColorJson = Color(0xFFCBCB41) // JSON Yellow-Green
    private val ColorXml = Color(0xFFE37933) // XML Orange
    private val ColorShell = Color(0xFF4EAA25) // Shell Green
    private val ColorImage = Color(0xFFB07219) // Image Brown
    private val ColorVideo = Color(0xFFFA5C5C) // Video Red
    private val ColorAudio = Color(0xFF1DB954) // Spotify Green (Audio)
    private val ColorArchive = Color(0xFF9E9E9E) // Archive Grey
    private val ColorConfig = Color(0xFF607D8B) // Config Grey-Blue
    private val ColorDatabase = Color(0xFFDA5B0B) // DB Orange
    private val ColorC = Color(0xFF555555) // C Grey
    private val ColorCpp = Color(0xFFf34b7d) // C++ Pinkish
    private val ColorGo = Color(0xFF00ADD8) // Go Blue
    private val ColorDart = Color(0xFF00B4AB) // Dart Cyan
    private val ColorLua = Color(0xFF000080) // Lua Blue
    private val ColorRuby = Color(0xFFCC342D) // Ruby Red
    private val ColorSwift = Color(0xFFF05138) // Swift Orange

    fun getFileIcon(name: String, isDirectory: Boolean, isExpanded: Boolean = false): Pair<ImageVector, Color> {
        if (isDirectory) {
            return if (isExpanded) {
                Icons.Filled.FolderOpen to ColorFolder
            } else {
                Icons.Filled.Folder to ColorFolder
            }
        }

        val extension = name.substringAfterLast('.', "").lowercase()
        val fileName = name.lowercase()

        // Specific file names check
        if (fileName == "androidmanifest.xml") return Icons.Filled.Android to ColorAndroid
        if (fileName == "build.gradle" || fileName == "build.gradle.kts" || fileName == "settings.gradle" || fileName == "settings.gradle.kts") return SimpleIcons.Gradle to Color(0xFF02303A)
        if (fileName == "package.json") return SimpleIcons.Json to Color(0xFF339933)
        if (fileName.startsWith(".git")) return SimpleIcons.Git to Color(0xFFF05032)
        if (fileName == "dockerfile") return SimpleIcons.Docker to Color(0xFF2496ED)

        return when (extension) {
            // JVM Languages
            "kt", "kts" -> SimpleIcons.Kotlin to ColorKotlin
            "java" -> SimpleIcons.Java to ColorJava
            "class", "jar" -> Icons.Filled.FolderZip to ColorJava
            
            // Web
            "html", "htm" -> SimpleIcons.Html5 to ColorHtml
            "css" -> SimpleIcons.Css3 to ColorCss
            "scss", "sass" -> SimpleIcons.Sass to Color(0xFFCC6699)
            "less" -> Icons.Filled.Css to ColorCss
            "js", "jsx", "mjs", "cjs" -> SimpleIcons.Javascript to ColorJavaScript
            "ts", "tsx" -> SimpleIcons.Typescript to ColorTypeScript
            "vue" -> Icons.Filled.Code to Color(0xFF4FC08D)
            "php" -> SimpleIcons.Php to Color(0xFF777BB4)
            "svelte" -> SimpleIcons.Svelte to Color(0xFFFF3E00)
            "ico" -> Icons.Filled.Image to ColorImage

            // Scripting / Other Languages
            "py", "pyc", "pyd" -> SimpleIcons.Python to ColorPython
            "rb" -> SimpleIcons.Ruby to ColorRuby
            "go" -> SimpleIcons.Go to ColorGo
            "rs" -> SimpleIcons.Rust to Color.Unspecified // Rust icon usually has its own color or black/white
            "c", "h" -> SimpleIcons.C to ColorC
            "cpp", "hpp", "cc" -> SimpleIcons.Cplusplus to ColorCpp
            "cs" -> SimpleIcons.Csharp to Color(0xFF239120)
            "swift" -> SimpleIcons.Swift to ColorSwift
            "dart" -> SimpleIcons.Dart to ColorDart
            "lua" -> SimpleIcons.Lua to ColorLua
            "sh", "bash", "zsh", "fish" -> SimpleIcons.Gnubash to ColorShell
            "bat", "cmd", "ps1" -> SimpleIcons.Powershell to ColorShell

            // Data & Config
            "json" -> SimpleIcons.Json to ColorJson
            "xml" -> Icons.Filled.Code to ColorXml
            "yaml", "yml" -> Icons.Filled.Settings to ColorConfig
            "toml", "ini", "conf", "properties" -> Icons.Filled.Settings to ColorConfig
            "sql", "db", "sqlite" -> FontAwesomeIcons.Solid.Database to ColorDatabase
            "csv", "tsv" -> Icons.Filled.TableChart to Color(0xFF217346)

            // Media
            "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "tiff" -> Icons.Filled.Image to ColorImage
            "mp3", "wav", "ogg", "m4a", "flac", "aac" -> Icons.Filled.AudioFile to ColorAudio
            "mp4", "mkv", "avi", "mov", "webm", "flv", "wmv" -> Icons.Filled.VideoFile to ColorVideo
            
            // Documents
            "md", "markdown" -> SimpleIcons.Markdown to Color.Unspecified // Markdown usually black/white
            "txt", "log" -> Icons.AutoMirrored.Filled.Article to Color(0xFF757575)
            "pdf" -> Icons.AutoMirrored.Filled.InsertDriveFile to Color(0xFFD32F2F)
            
            // Archives
            "zip", "tar", "gz", "7z", "rar", "apk", "aab" -> Icons.Filled.FolderZip to ColorArchive
            
            // Fonts
            "ttf", "otf", "woff", "woff2" -> Icons.Filled.FontDownload to Color(0xFF000000)

            // Default
            else -> Icons.AutoMirrored.Filled.InsertDriveFile to Color.Unspecified
        }
    }
}
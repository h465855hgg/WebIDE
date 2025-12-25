/*
 * TreeCompose - A tree-structured file viewer built with Jetpack Compose
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

package com.mcal.apksigner

import com.android.apksig.ApkSigner
import com.android.apksigner.ApkSignerTool
import com.mcal.apksigner.utils.KeyStoreHelper
import java.io.File
import java.io.InputStream
import java.security.PrivateKey
import java.security.cert.X509Certificate

class ApkSigner(
    private val unsignedApkFile: File,
    private val signedApkFile: File,
) {
    var useDefaultSignatureVersion = true
    var v1SigningEnabled = true
    var v2SigningEnabled = true
    var v3SigningEnabled = true
    var v4SigningEnabled = false

    fun signRelease(
        pk8File: File,
        x509File: File
    ): Boolean {
        val args = mutableListOf(
            "sign",
            "--in",
            unsignedApkFile.path,
            "--out",
            signedApkFile.path,
            "--key",
            pk8File.path,
            "--cert",
            x509File.path,
        )
        if (!useDefaultSignatureVersion) {
            args.add("--v1-signing-enabled")
            args.add(v1SigningEnabled.toString())
            args.add("--v2-signing-enabled")
            args.add(v2SigningEnabled.toString())
            args.add("--v3-signing-enabled")
            args.add(v3SigningEnabled.toString())
            args.add("--v4-signing-enabled")
            args.add(v4SigningEnabled.toString())
        }
        return try {
            ApkSignerTool.main(args.toTypedArray())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun signRelease(
        keyFile: File,
        password: String,
        alias: String,
        aliasPassword: String,
    ): Boolean {
        return try {
            val keystore = KeyStoreHelper.loadKeyStore(keyFile, password.toCharArray())
            ApkSigner.Builder(
                listOf(
                    ApkSigner.SignerConfig.Builder(
                        "CERT",
                        keystore.getKey(alias, aliasPassword.toCharArray()) as PrivateKey,
                        listOf(keystore.getCertificate(alias) as X509Certificate)
                    ).build()
                )
            ).apply {
                setInputApk(unsignedApkFile)
                setOutputApk(signedApkFile)
                if (!useDefaultSignatureVersion) {
                    setV1SigningEnabled(v1SigningEnabled)
                    setV2SigningEnabled(v2SigningEnabled)
                    setV3SigningEnabled(v3SigningEnabled)
                    setV4SigningEnabled(v4SigningEnabled)
                }
            }.build().sign()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun signDebug(): Boolean {
        val pk8File = File.createTempFile("testkey", "pk8").also {
            it.writeBytes((com.mcal.apksigner.ApkSigner::class.java.getResourceAsStream("/keystore/testkey.pk8") as InputStream).readBytes())
        }

        val x509File = File.createTempFile("testkey", "x509.pem").also {
            it.writeBytes((com.mcal.apksigner.ApkSigner::class.java.getResourceAsStream("/keystore/testkey.x509.pem") as InputStream).readBytes())
        }

        val args = mutableListOf(
            "sign",
            "--in",
            unsignedApkFile.path,
            "--out",
            signedApkFile.path,
            "--key",
            pk8File.path,
            "--cert",
            x509File.path,
        )
        if (!useDefaultSignatureVersion) {
            args.add("--v1-signing-enabled")
            args.add(v1SigningEnabled.toString())
            args.add("--v2-signing-enabled")
            args.add(v2SigningEnabled.toString())
            args.add("--v3-signing-enabled")
            args.add(v3SigningEnabled.toString())
            args.add("--v4-signing-enabled")
            args.add(v4SigningEnabled.toString())
        }
        return try {
            ApkSignerTool.main(args.toTypedArray())
            pk8File.delete()
            x509File.delete()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            pk8File.delete()
            x509File.delete()
            false
        }
    }

    fun validateKeystorePassword(keyFile: File, password: String): Boolean {
        return KeyStoreHelper.validateKeystorePassword(keyFile, password)
    }
}

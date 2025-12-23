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

package com.mcal.apksigner.utils

import org.spongycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.Security
import java.util.Locale

object KeyStoreHelper {
    val provider by lazy(LazyThreadSafetyMode.NONE) {
        BouncyCastleProvider().also {
            Security.addProvider(it)
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun loadJks(jksFile: File?, password: CharArray): KeyStore {
        val keyStore: KeyStore
        try {
            keyStore = JksKeyStore()
            keyStore.load(jksFile?.let { FileInputStream(it) }, password)
        } catch (e: Exception) {
            throw RuntimeException("Failed to load keystore: " + e.message)
        }
        return keyStore
    }

    @JvmStatic
    @Throws(Exception::class)
    fun loadBks(bksFile: File?, password: CharArray): KeyStore {
        val keyStore: KeyStore
        try {
            keyStore = KeyStore.getInstance("BKS", "BC")
            keyStore.load(bksFile?.let { FileInputStream(it) }, password)
        } catch (e: Exception) {
            throw RuntimeException("Failed to load keystore: " + e.message)
        }
        return keyStore
    }

    @JvmStatic
    @Throws(java.lang.Exception::class)
    fun loadKeyStore(keystoreFile: File, password: CharArray): KeyStore {
        return if (keystoreFile.path.lowercase(Locale.getDefault()).endsWith(".bks")) {
            loadBks(keystoreFile, password)
        } else {
            loadJks(keystoreFile, password)
        }
    }

    @JvmStatic
    @Throws(java.lang.Exception::class)
    fun createKeyStore(keystoreFile: File, password: CharArray): KeyStore {
        return if (keystoreFile.path.lowercase(Locale.getDefault()).endsWith(".bks")) {
            loadBks(null, password)
        } else {
            loadJks(null, password)
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun writeKeyStore(ks: KeyStore, keystorePath: File, password: CharArray) {
        FileOutputStream(keystorePath).use { fos ->
            ks.store(fos, password)
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun validateKeystorePassword(keystoreFile: File, password: String): Boolean {
        return try {
            loadKeyStore(keystoreFile, password.toCharArray())
            true
        } catch (e: Exception) {
            false
        }
    }
}
package com.web.webide.core.utils

import android.content.Context

object WorkspaceManager {

    private const val PREFS_NAME = "webide_prefs"
    private const val KEY_WORKSPACE_PATH = "workspace_path"
    private const val DEFAULT_PATH = "/storage/emulated/0"

    fun saveWorkspacePath(context: Context, path: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_WORKSPACE_PATH, path).apply()
    }

    fun getWorkspacePath(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_WORKSPACE_PATH, DEFAULT_PATH) ?: DEFAULT_PATH
    }
}
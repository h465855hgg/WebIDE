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
package com.web.webide.ui.editor.git

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.web.webide.R
import kotlinx.coroutines.launch
import org.eclipse.jgit.revwalk.RevCommit
import androidx.core.content.edit

class GitViewModel(application: Application) : AndroidViewModel(application) {

    var isGitProject by mutableStateOf(false)
    var changedFiles by mutableStateOf<List<GitFileChange>>(emptyList())
    var currentBranch by mutableStateOf("")
    var commitLog by mutableStateOf<List<GitCommitUI>>(emptyList())
    var isLoading by mutableStateOf(false)
    var statusMessage by mutableStateOf<String?>(null)

    // 🔥 配置相关
    var remoteUrl by mutableStateOf("")
    var userEmail by mutableStateOf("")
    var savedAuth by mutableStateOf<GitAuth?>(null)

    private var gitManager: GitManager? = null
    var testConnectionResult by mutableStateOf<String?>(null)
    var testConnectionSuccess by mutableStateOf<Boolean?>(null)
    var isTestingConnection by mutableStateOf(false)
    private val laneColors = listOf(
        Color(0xFFFF5252), Color(0xFF40C4FF), Color(0xFFE040FB),
        Color(0xFF69F0AE), Color(0xFFFFAB40), Color(0xFFFFD740),
        Color(0xFF9E9E9E), Color(0xFF795548)
    )

    fun initialize(projectPath: String) {
        gitManager = GitManager(projectPath)
        loadConfig()
        refreshAll()
    }
    fun testRemoteConnection(
        url: String,
        authType: AuthType,
        username: String,
        token: String,
        privateKey: String,
        passphrase: String
    ) {
        viewModelScope.launch {
            isTestingConnection = true
            testConnectionSuccess = null
            testConnectionResult = getApplication<Application>().getString(R.string.status_connecting)

            val tempAuth = GitAuth(authType, username, token, privateKey, passphrase)
            // 如果 manager 为空，临时创建一个（针对还没 init 的情况）
            val manager = gitManager ?: GitManager(getApplication<Application>().filesDir.absolutePath)

            val result = manager.testConnectivity(url, tempAuth)
            testConnectionSuccess = result.isSuccess
            testConnectionResult = if (result.isSuccess) {
                getApplication<Application>().getString(R.string.git_test_success_refs, result.refsCount)
            } else {
                when (result.error) {
                    GitConnectivityError.AUTH_FAILED -> getApplication<Application>().getString(R.string.git_auth_failed_token)
                    GitConnectivityError.REPO_NOT_FOUND -> getApplication<Application>().getString(R.string.git_repo_not_found)
                    GitConnectivityError.TIMEOUT -> getApplication<Application>().getString(R.string.git_connection_timeout)
                    GitConnectivityError.UNKNOWN_HOST -> getApplication<Application>().getString(R.string.git_connection_unknown_host)
                    GitConnectivityError.SSH_ENV_FAILED -> getApplication<Application>().getString(R.string.git_connection_ssh_env_failed)
                    else -> getApplication<Application>().getString(
                        R.string.git_connection_failed,
                        result.rawMessage.orEmpty()
                    )
                }
            }
            isTestingConnection = false
        }
    }
    private fun loadConfig() {
        val context = getApplication<Application>()
        val prefs = context.getSharedPreferences("git_config", Context.MODE_PRIVATE)

        remoteUrl = prefs.getString("remote_url", "") ?: ""
        userEmail = prefs.getString("user_email", "") ?: ""

        // 读取认证信息
        val authTypeStr = prefs.getString("auth_type", "HTTPS") ?: "HTTPS"
        val username = prefs.getString("username", "") ?: ""
        val token = prefs.getString("token", "") ?: ""
        val privateKey = prefs.getString("private_key", "") ?: ""
        val passphrase = prefs.getString("passphrase", "") ?: ""

        val authType = if (authTypeStr == "SSH") AuthType.SSH else AuthType.HTTPS

        savedAuth = GitAuth(
            type = authType,
            username = username,
            token = token,
            privateKey = privateKey,
            passphrase = passphrase
        )
    }

    fun saveConfig(
        remote: String,
        email: String,
        authType: AuthType,
        username: String,
        token: String,
        privateKey: String,
        passphrase: String
    ) {
        viewModelScope.launch {
            gitManager?.addRemote("origin", remote)
            remoteUrl = remote
            userEmail = email

            savedAuth = GitAuth(authType, username, token, privateKey, passphrase)

            val prefs = getApplication<Application>().getSharedPreferences("git_config", Context.MODE_PRIVATE)
            prefs.edit {
                putString("remote_url", remote)
                putString("user_email", email)
                putString("auth_type", authType.name)
                putString("username", username)
                putString("token", token)
                putString("private_key", privateKey)
                putString("passphrase", passphrase)
            }
            statusMessage = getApplication<Application>().getString(R.string.git_status_config_saved)
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            isLoading = true
            val manager = gitManager ?: return@launch
            isGitProject = manager.isGitRepo()
            if (isGitProject) {
                try {
                    changedFiles = manager.getStatus()
                    currentBranch = manager.getCurrentBranch()
                    val (rawCommits, refMap) = manager.getCommitLog()
                    commitLog = calculateGraph(rawCommits, refMap)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            isLoading = false
        }
    }

    private fun calculateGraph(
        commits: List<RevCommit>,
        refMap: Map<String, List<GitRefUI>>
    ): List<GitCommitUI> {
        val result = mutableListOf<GitCommitUI>()
        val slots = ArrayList<String?>()
        val colorMap = HashMap<String, Int>()
        var nextColorIndex = 0

        commits.forEach { commit ->
            val hash = commit.name
            val parents = commit.parents.map { it.name }

            var myLane = slots.indexOf(hash)
            if (myLane == -1) {
                myLane = slots.indexOf(null)
                if (myLane == -1) {
                    myLane = slots.size
                    slots.add(hash)
                } else {
                    slots[myLane] = hash
                }
            }

            var myColorIdx = colorMap[hash]
            if (myColorIdx == null) {
                myColorIdx = nextColorIndex++
                colorMap[hash] = myColorIdx
            }
            val myColor = laneColors[myColorIdx % laneColors.size]

            val parentLanes = mutableListOf<Int>()
            if (parents.isNotEmpty()) {
                val firstParent = parents[0]
                slots[myLane] = firstParent
                parentLanes.add(myLane)
                colorMap[firstParent] = myColorIdx

                for (i in 1 until parents.size) {
                    val otherParent = parents[i]
                    var otherLane = slots.indexOf(otherParent)

                    if (otherLane == -1) {
                        otherLane = slots.indexOf(null)
                        if (otherLane == -1) {
                            otherLane = slots.size
                            slots.add(otherParent)
                        } else {
                            slots[otherLane] = otherParent
                        }
                        colorMap[otherParent] = (nextColorIndex++ % laneColors.size)
                    }
                    parentLanes.add(otherLane)
                }
            } else {
                slots[myLane] = null
            }

            while (slots.isNotEmpty() && slots.last() == null) slots.removeAt(slots.lastIndex)

            result.add(GitCommitUI(
                hash = hash,
                shortHash = hash.substring(0, 7),
                message = commit.shortMessage.trim(),
                fullMessage = commit.fullMessage.trim(),
                author = commit.authorIdent.name,
                email = commit.authorIdent.emailAddress ?: "",
                time = commit.commitTime * 1000L,
                parents = parents,
                refs = refMap[hash] ?: emptyList(),
                lane = myLane,
                totalLanes = slots.size,
                childLanes = emptyList(),
                parentLanes = parentLanes,
                color = myColor
            ))
        }
        return result
    }

    // --- Git 操作 ---
    fun initRepo() { viewModelScope.launch { gitManager?.initRepo(); refreshAll() } }

    fun commit(msg: String, pushAfter: Boolean) {
        viewModelScope.launch {
            isLoading = true
            try {
                val author = savedAuth?.username?.ifEmpty { "AndroidUser" } ?: "AndroidUser"
                val email = userEmail.ifEmpty { "user@ide.com" }
                gitManager?.commitAll(msg, author, email)
                if (pushAfter) push() else statusMessage = getApplication<Application>().getString(R.string.git_status_commit_success)
                refreshAll()
            } catch (e: Exception) {
                statusMessage = getApplication<Application>().getString(R.string.git_status_operation_failed, e.message)
            } finally { isLoading = false }
        }
    }

    fun push() {
        viewModelScope.launch {
            isLoading = true
            try {
                val auth = savedAuth ?: throw Exception(getApplication<Application>().getString(R.string.git_error_account_not_configured))
                gitManager?.push(auth)
                statusMessage = getApplication<Application>().getString(R.string.git_status_push_success)
            } catch (e: Exception) {
                statusMessage = getApplication<Application>().getString(R.string.git_status_push_failed, e.message)
                e.printStackTrace()
            } finally { isLoading = false }
        }
    }

    fun pull() { updateProject(false) }

    fun updateProject(rebase: Boolean) {
        viewModelScope.launch {
            isLoading = true
            try {
                val auth = savedAuth ?: throw Exception(getApplication<Application>().getString(R.string.git_error_account_not_configured))
                if (rebase) gitManager?.pullRebase(auth) else gitManager?.pull(auth)
                statusMessage = getApplication<Application>().getString(R.string.git_status_update_success)
                refreshAll()
            } catch (e: Exception) {
                statusMessage = getApplication<Application>().getString(R.string.git_status_update_failed, e.message)
            } finally { isLoading = false }
        }
    }

    fun createBranch(name: String) {
        viewModelScope.launch {
            isLoading = true
            try {
                gitManager?.createBranch(name)
                statusMessage = getApplication<Application>().getString(R.string.git_status_branch_created, name)
                refreshAll()
            } catch (e: Exception) { statusMessage = getApplication<Application>().getString(R.string.git_status_branch_create_failed, e.message) }
            finally { isLoading = false }
        }
    }

    fun createTag(name: String, msg: String) {
        viewModelScope.launch {
            isLoading = true
            try {
                gitManager?.createTag(name, msg)
                statusMessage = getApplication<Application>().getString(R.string.git_status_tag_created, name)
                refreshAll()
            } catch (e: Exception) { statusMessage = getApplication<Application>().getString(R.string.git_status_tag_create_failed, e.message) }
            finally { isLoading = false }
        }
    }

    fun checkout(name: String) {
        viewModelScope.launch {
            isLoading = true
            try {
                gitManager?.checkout(name)
                statusMessage = getApplication<Application>().getString(R.string.git_status_checked_out, name)
                refreshAll()
            } catch (e: Exception) { statusMessage = getApplication<Application>().getString(R.string.git_status_checkout_failed, e.message) }
            finally { isLoading = false }
        }
    }

    suspend fun getBranches() = gitManager?.getBranches() ?: emptyList()
    fun clearMessage() { statusMessage = null }
}

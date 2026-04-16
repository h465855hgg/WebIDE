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

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase
import org.eclipse.jgit.transport.sshd.SshdSessionFactory
import java.io.File
import java.net.InetSocketAddress
import java.security.PublicKey

// 🔥 导入 Apache SSHD 工具类，用于修复 Android 上的 Home 目录崩溃问题
import org.apache.sshd.common.util.io.PathUtils
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.PathFilter

// 日志标签，Logcat 搜索 "WebIDE_Git"
private const val TAG = "WebIDE_Git"
enum class GitConnectivityError {
    AUTH_FAILED,
    REPO_NOT_FOUND,
    TIMEOUT,
    UNKNOWN_HOST,
    SSH_ENV_FAILED,
    UNKNOWN,
}

data class GitConnectivityResult(
    val isSuccess: Boolean,
    val refsCount: Int = 0,
    val error: GitConnectivityError? = null,
    val rawMessage: String? = null,
)

private val DEFAULT_GITIGNORE = """
    # --- WebIDE Security (绝对不能上传) ---
    .git_ssh_config/
    id_rsa
    id_rsa.pub
    
    # --- Android Build ---
    build/
    .gradle/
    app/build/
    *.apk
    *.ap_
    *.dex
    
    # --- IDE Settings ---
    .idea/
    .vscode/
    *.iml
    *.ipr
    *.iws
    local.properties
    
    # --- System ---
    .DS_Store
    Thumbs.db
""".trimIndent()
class GitManager(projectPath: String) {
    private val rootDir = File(projectPath)

    // SSH 密钥存储目录 (隐藏文件夹)
    private val sshConfigDir = File(rootDir, ".git_ssh_config")

    // ========================================================================
    // 🔥🔥 核心修复：初始化 SSH 环境 🔥🔥
    // ========================================================================
    init {
        try {
            // Android 系统没有标准的 user.home 目录，Apache SSHD 默认初始化会崩溃。
            // 这里强制指定 App 的私有目录作为 "用户主目录"。
            PathUtils.setUserHomeFolderResolver {
                if (!sshConfigDir.exists()) sshConfigDir.mkdirs()
                sshConfigDir.parentFile.toPath()
            }
            Log.i(TAG, "SSH环境修复: UserHome 已重定向至 -> ${sshConfigDir.parentFile}")
        } catch (e: Throwable) {
            Log.e(TAG, "SSH环境修复失败 (可能导致 SSH 连接崩溃)", e)
        }
    }

    fun isGitRepo(): Boolean = File(rootDir, ".git").exists()

    // ========================================================================
    // 🔍 诊断工具
    // ========================================================================
    fun debugGitConfig() {
        try {
            val configFile = File(rootDir, ".git/config")
            if (configFile.exists()) {
                Log.d(TAG, "⬇️⬇️⬇️ [.git/config] ⬇️⬇️⬇️")
                Log.d(TAG, configFile.readText())
                Log.d(TAG, "⬆️⬆️⬆️ [End Config] ⬆️⬆️⬆️")
            } else {
                Log.e(TAG, "❌ 错误: .git/config 不存在")
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取 config 失败", e)
        }
    }

    // ========================================================================
    // 基础操作
    // ========================================================================

    suspend fun initRepo() = withContext(Dispatchers.IO) {
        Log.i(TAG, "正在初始化仓库: $rootDir")

        // 1. 执行 Git Init
        val git = Git.init().setDirectory(rootDir).call()

        // 2. 🔥 自动化：如果不存在，立即创建 .gitignore
        val ignoreFile = File(rootDir, ".gitignore")
        if (!ignoreFile.exists()) {
            try {
                ignoreFile.writeText(DEFAULT_GITIGNORE)
                Log.i(TAG, "自动化：已创建默认 .gitignore 规则")
            } catch (e: Exception) {
                Log.e(TAG, "写入 .gitignore 失败", e)
            }
        } else {
            // 3. 🔥 增强：如果文件已存在，检查是否遗漏了关键配置
            // 防止用户自己建了文件但忘了加 .git_ssh_config
            val currentContent = ignoreFile.readText()
            if (!currentContent.contains(".git_ssh_config/")) {
                ignoreFile.appendText("\n# Safety check by WebIDE\n.git_ssh_config/\n")
                Log.i(TAG, "自动化：已追加安全忽略规则")
            }
        }

        // 4. (可选) 自动执行一次 Initial Commit?
        // 通常 IDE 不会自动 commit，但会把文件变红/变绿显示出来。
        // 这里我们只负责把环境配好。

        git.close()
        Log.i(TAG, "仓库初始化流程完成 (Init + Security Rules)")
    }
    suspend fun getBranches(): List<GitBranch> = withContext(Dispatchers.IO) {
        if (!isGitRepo()) return@withContext emptyList()
        val git = Git.open(rootDir)
        val repo = git.repository
        val currentBranchRef = repo.fullBranch

        val branchList = mutableListOf<GitBranch>()
        val refs = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call()

        refs.forEach { ref ->
            val fullName = ref.name
            var displayName = fullName
            var type = BranchType.LOCAL

            if (fullName.startsWith(Constants.R_HEADS)) {
                displayName = fullName.substring(Constants.R_HEADS.length)
                type = BranchType.LOCAL
            } else if (fullName.startsWith(Constants.R_REMOTES)) {
                displayName = fullName.substring(Constants.R_REMOTES.length)
                type = BranchType.REMOTE
            }
            branchList.add(GitBranch(displayName, fullName, type, fullName == currentBranchRef))
        }

        git.close()
        branchList.sortedWith(compareByDescending<GitBranch> { it.isCurrent }
            .thenBy { it.type }
            .thenBy { it.name }
        )
    }

    suspend fun getStatus(): List<GitFileChange> = withContext(Dispatchers.IO) {
        if (!isGitRepo()) return@withContext emptyList()
        val git = Git.open(rootDir)
        val status = git.status().call()
        val changes = mutableListOf<GitFileChange>()

        status.added.forEach { changes.add(GitFileChange(it, GitFileStatus.ADDED)) }
        status.changed.forEach { changes.add(GitFileChange(it, GitFileStatus.MODIFIED)) }
        status.modified.forEach { changes.add(GitFileChange(it, GitFileStatus.MODIFIED)) }
        status.untracked.forEach { changes.add(GitFileChange(it, GitFileStatus.UNTRACKED)) }
        status.missing.forEach { changes.add(GitFileChange(it, GitFileStatus.MISSING)) }
        status.removed.forEach { changes.add(GitFileChange(it, GitFileStatus.REMOVED)) }
        status.conflicting.forEach { changes.add(GitFileChange(it, GitFileStatus.CONFLICTING)) }

        git.close()
        if (changes.isNotEmpty()) Log.d(TAG, "检测到 ${changes.size} 个文件变更")
        changes.sortedBy { it.filePath }
    }

    suspend fun commitAll(message: String, author: String, email: String) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Commit: '$message'")
        val git = Git.open(rootDir)

        // --- 🔥 硬核保险：Add 之前，再次确认敏感目录没被追踪 ---
        // 这一步是为了防止用户手贱删了 .gitignore，然后又把私钥 commit 上去了
        val sshDir = File(rootDir, ".git_ssh_config")
        if (sshDir.exists()) {
            // 确保 Git 不会追踪这个目录 (即使没有 .gitignore)
            // 这是一个比较底层的防御，通常 .gitignore 够用了，但这能体现 IDE 的健壮性
            // 不过 JGit 的 add() 主要还是看 .gitignore，所以这里我们确保 .gitignore 存在
            val ignoreFile = File(rootDir, ".gitignore")
            if (!ignoreFile.exists()) {
                ignoreFile.writeText(DEFAULT_GITIGNORE)
            }
        }
        // ----------------------------------------------------

        git.add().addFilepattern(".").call()

        // ... 后面的代码保持不变 ...
        val status = git.status().call()
        if (status.missing.isNotEmpty() || status.removed.isNotEmpty()) {
            val rm = git.rm()
            status.missing.forEach { rm.addFilepattern(it) }
            status.removed.forEach { rm.addFilepattern(it) }
            rm.call()
        }

        val person = PersonIdent(author, email)
        git.commit()
            .setMessage(message)
            .setAuthor(person)
            .setCommitter(person)
            .call()

        git.close()
    }

    // ========================================================================
    // 远程操作 (Connect, Add, Push, Pull)
    // ========================================================================

    /**
     * 测试连接 (ls-remote)
     */
    suspend fun testConnectivity(url: String, auth: GitAuth): GitConnectivityResult = withContext(Dispatchers.IO) {
        Log.i(TAG, ">>> 开始测试连接: $url")
        try {
            val cmd = Git.lsRemoteRepository()
                .setRemote(url)
                .setHeads(true)
                .setTags(false)

            if (auth.type == AuthType.HTTPS) {
                cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(auth.username, auth.token))
            } else {
                cmd.setTransportConfigCallback(prepareSshEnvironment(auth))
            }

            val result = cmd.callAsMap()
            Log.i(TAG, "✅ 连接成功! 发现 ${result.size} 个引用")
            return@withContext GitConnectivityResult(
                isSuccess = true,
                refsCount = result.size
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ 连接测试失败", e)
            val msg = e.message ?: e.toString()
            return@withContext when {
                msg.contains("401") -> GitConnectivityResult(false, error = GitConnectivityError.AUTH_FAILED)
                msg.contains("not found") -> GitConnectivityResult(false, error = GitConnectivityError.REPO_NOT_FOUND)
                msg.contains("timeout") || msg.contains("abort") -> GitConnectivityResult(false, error = GitConnectivityError.TIMEOUT)
                msg.contains("UnknownHost") -> GitConnectivityResult(false, error = GitConnectivityError.UNKNOWN_HOST)
                msg.contains("No user home") -> GitConnectivityResult(false, error = GitConnectivityError.SSH_ENV_FAILED)
                else -> GitConnectivityResult(false, error = GitConnectivityError.UNKNOWN, rawMessage = msg)
            }
        }
    }

    /**
     * 添加远程仓库 (包含 Fetch 规则修复)
     */
    suspend fun addRemote(name: String, url: String) = withContext(Dispatchers.IO) {
        Log.i(TAG, "设置 Remote: $name -> $url")
        val git = Git.open(rootDir)
        val config = git.repository.config

        config.setString("remote", name, "url", url)
        // 关键：写入 fetch 规则，否则 pull 会失败
        val fetchSpec = "+refs/heads/*:refs/remotes/$name/*"
        config.setString("remote", name, "fetch", fetchSpec)

        config.save()
        git.close()
        debugGitConfig()
    }

    /**
     * 推送 (强制推送 Force Push)
     */
    /**
     * 推送 (Push) - 包含 "硬强制" (+RefSpec) 修复
     */
    suspend fun push(auth: GitAuth, remote: String = "origin") = withContext(Dispatchers.IO) {
        Log.i(TAG, ">>> PUSH (FORCE+) Start <<<")
        val git = Git.open(rootDir)
        val currentBranch = git.repository.branch ?: throw Exception("未处于任何分支")

        // 🔥🔥🔥 核心修改：手动加 "+" 号 🔥🔥🔥
        // 加了 + 号，等于告诉 Git："不管远程有什么，直接用我的覆盖它！"
        // 这种写法比 setForce(true) 更底层、更有效。
        val refSpecStr = "+refs/heads/$currentBranch:refs/heads/$currentBranch"

        Log.i(TAG, "使用强力推送规则: $refSpecStr")
        val spec = RefSpec(refSpecStr)

        val cmd = git.push()
            .setRemote(remote)
            .setRefSpecs(spec)
        // .setForce(true) // 有了上面的 + 号，这一行其实可以省略，但留着也没坏处

        if (auth.type == AuthType.HTTPS) {
            cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(auth.username, auth.token))
        } else {
            cmd.setTransportConfigCallback(prepareSshEnvironment(auth))
        }

        try {
            val results = cmd.call()
            var errorMsg = ""
            var isSuccess = false

            for (result in results) {
                for (update in result.remoteUpdates) {
                    Log.i(TAG, "分支 [${update.remoteName}] -> ${update.status}")

                    // 只要状态是 OK, UP_TO_DATE 或者 FORCED_UPDATE 都算成功
                    if (update.status == RemoteRefUpdate.Status.OK ||
                        update.status == RemoteRefUpdate.Status.UP_TO_DATE ||
                        update.status == RemoteRefUpdate.Status.OK) {
                        isSuccess = true
                    } else {
                        errorMsg += "Push失败: ${update.status} - ${update.message}\n"
                    }
                }
            }

            if (!isSuccess && errorMsg.isNotEmpty()) throw Exception(errorMsg)
            Log.i(TAG, "✅ Push 成功")

        } catch (e: Exception) {
            // 过滤掉偶尔的网络断开错误，因为你之前的日志显示网络不稳定
            if (e.message?.contains("Software caused connection abort") == true) {
                Log.w(TAG, "网络波动，请重试...")
                throw Exception("网络连接中断，请重试")
            }
            Log.e(TAG, "❌ Push 异常", e)
            throw e
        } finally {
            git.close()
        }
    }
    // 🔥🔥🔥 新增核心方法：获取文件在 HEAD (最新提交) 中的内容 🔥🔥🔥
    suspend fun getFileContentAtHead(filePath: String): String = withContext(Dispatchers.IO) {
        if (!isGitRepo()) return@withContext ""
        val git = Git.open(rootDir)
        val repo = git.repository

        try {
            val headId = repo.resolve(Constants.HEAD) ?: return@withContext ""

            // 1. 解析 HEAD Commit 的 Tree
            val revWalk = RevWalk(repo)
            val commit = revWalk.parseCommit(headId)
            val tree = commit.tree

            // 2. 计算仓库相对路径
            // 如果 filePath 是绝对路径，转换为相对路径
            val relativePath = if (filePath.startsWith(rootDir.absolutePath)) {
                filePath.substring(rootDir.absolutePath.length + 1).replace("\\", "/")
            } else {
                filePath
            }

            // 3. 遍历 Tree 查找文件
            val treeWalk = TreeWalk(repo)
            treeWalk.addTree(tree)
            treeWalk.isRecursive = true
            treeWalk.filter = PathFilter.create(relativePath)

            if (!treeWalk.next()) {
                // 文件在 HEAD 中不存在（可能是新添加的文件）
                return@withContext ""
            }

            val objectId = treeWalk.getObjectId(0)
            val loader = repo.open(objectId)

            // 读取字节并转为 String (UTF-8)
            return@withContext String(loader.bytes, Charsets.UTF_8)

        } catch (e: Exception) {
            Log.e(TAG, "获取 HEAD 内容失败: $filePath", e)
            return@withContext "" // 出错返回空字符串
        } finally {
            git.close()
        }
    }
    suspend fun pull(auth: GitAuth, remote: String = "origin") = withContext(Dispatchers.IO) {
        Log.i(TAG, ">>> PULL Start <<<")
        val git = Git.open(rootDir)
        val cmd = git.pull().setRemote(remote)

        if (auth.type == AuthType.HTTPS) {
            cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(auth.username, auth.token))
        } else {
            cmd.setTransportConfigCallback(prepareSshEnvironment(auth))
        }

        try {
            val result = cmd.call()
            if (!result.isSuccessful) {
                throw Exception("Pull 失败: ${result.mergeResult?.mergeStatus}")
            }
            Log.i(TAG, "✅ Pull 成功")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Pull 异常", e)
            throw e
        } finally {
            git.close()
        }
    }

    suspend fun pullRebase(auth: GitAuth, remote: String = "origin") = withContext(Dispatchers.IO) {
        Log.i(TAG, ">>> PULL (Rebase) Start <<<")
        val git = Git.open(rootDir)
        val cmd = git.pull().setRemote(remote).setRebase(true)

        if (auth.type == AuthType.HTTPS) {
            cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(auth.username, auth.token))
        } else {
            cmd.setTransportConfigCallback(prepareSshEnvironment(auth))
        }

        try {
            cmd.call()
            Log.i(TAG, "✅ Rebase 成功")
        } finally {
            git.close()
        }
    }

    // ========================================================================
    // SSH 配置 (TrustAll + KeyInjection)
    // ========================================================================

    class CustomSshSessionFactory(private val sshDir: File) : SshdSessionFactory() {
        override fun getSshDirectory(): File = sshDir
        override fun getHomeDirectory(): File = sshDir.parentFile
        override fun getServerKeyDatabase(homeDir: File, sshDir: File): ServerKeyDatabase {
            return object : ServerKeyDatabase {
                override fun lookup(c: String, r: InetSocketAddress, conf: ServerKeyDatabase.Configuration): List<PublicKey> = emptyList()
                override fun accept(c: String, r: InetSocketAddress, k: PublicKey, conf: ServerKeyDatabase.Configuration, p: CredentialsProvider?): Boolean {
                    Log.d(TAG, "SSH: 信任 Host Key -> $c")
                    return true
                }
            }
        }
    }

    private fun prepareSshEnvironment(auth: GitAuth): TransportConfigCallback {
        return TransportConfigCallback { transport ->
            if (transport is SshTransport) {
                if (!sshConfigDir.exists()) sshConfigDir.mkdirs()

                if (auth.privateKey.isNotBlank()) {
                    val keyFile = File(sshConfigDir, "id_rsa")
                    // 只有内容变动时才重写，减少IO
                    if (!keyFile.exists() || keyFile.readText() != auth.privateKey) {
                        keyFile.writeText(auth.privateKey)
                        Log.d(TAG, "SSH: 注入新私钥")
                    }
                }
                transport.sshSessionFactory = CustomSshSessionFactory(sshConfigDir)
            }
        }
    }

    // ========================================================================
    // 其他功能
    // ========================================================================

    suspend fun createBranch(name: String, checkout: Boolean = true) = withContext(Dispatchers.IO) {
        val git = Git.open(rootDir)
        git.branchCreate().setName(name).call()
        if (checkout) git.checkout().setName(name).call()
        git.close()
    }

    suspend fun createTag(name: String, message: String) = withContext(Dispatchers.IO) {
        val git = Git.open(rootDir)
        git.tag().setName(name).setMessage(message).call()
        git.close()
    }

    suspend fun checkout(name: String) = withContext(Dispatchers.IO) {
        val git = Git.open(rootDir)
        git.checkout().setName(name).call()
        git.close()
    }

    suspend fun getCurrentBranch(): String = withContext(Dispatchers.IO) {
        if (!isGitRepo()) return@withContext ""
        val git = Git.open(rootDir)
        val b = git.repository.branch
        git.close()
        b ?: "HEAD"
    }

    suspend fun getCommitLog(): Pair<List<RevCommit>, Map<String, List<GitRefUI>>> = withContext(Dispatchers.IO) {
        if (!isGitRepo()) return@withContext Pair(emptyList(), emptyMap())
        val git = Git.open(rootDir)
        val repo = git.repository

        val refMap = mutableMapOf<String, MutableList<GitRefUI>>()

        val head = repo.resolve(Constants.HEAD)
        if (head != null) {
            refMap.getOrPut(head.name) { mutableListOf() }.add(GitRefUI("HEAD", RefType.HEAD))
        }

        repo.refDatabase.refs.forEach { ref ->
            val id = ref.objectId.name
            val name = ref.name
            val simpleName = RepositoryUtils.shortenRefName(name)

            val type = when {
                name.startsWith(Constants.R_HEADS) -> RefType.LOCAL_BRANCH
                name.startsWith(Constants.R_REMOTES) -> RefType.REMOTE_BRANCH
                name.startsWith(Constants.R_TAGS) -> RefType.TAG
                else -> RefType.LOCAL_BRANCH
            }

            if (name != Constants.HEAD) {
                refMap.getOrPut(id) { mutableListOf() }.add(GitRefUI(simpleName, type))
            }
        }

        val walk = RevWalk(repo)
        repo.refDatabase.refs.forEach { ref ->
            if (ref.objectId != null) {
                try {
                    walk.markStart(walk.parseCommit(ref.objectId))
                } catch (_: Exception) {}
            }
        }
        walk.sort(org.eclipse.jgit.revwalk.RevSort.COMMIT_TIME_DESC)
        walk.sort(org.eclipse.jgit.revwalk.RevSort.TOPO)

        val commits = mutableListOf<RevCommit>()
        for (commit in walk) {
            commits.add(commit)
        }

        walk.dispose()
        git.close()

        Pair(commits, refMap)
    }
}

// 辅助工具单例
object RepositoryUtils {
    fun shortenRefName(refName: String): String {
        if (refName.startsWith(Constants.R_HEADS)) return refName.substring(Constants.R_HEADS.length)
        if (refName.startsWith(Constants.R_TAGS)) return refName.substring(Constants.R_TAGS.length)
        if (refName.startsWith(Constants.R_REMOTES)) return refName.substring(Constants.R_REMOTES.length)
        return refName
    }
}

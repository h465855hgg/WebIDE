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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.web.webide.R
import kotlinx.coroutines.launch
import com.web.webide.ui.editor.viewmodel.EditorViewModel
import java.io.File

// 定义侧边栏 Tab 枚举，供外部使用
enum class SidebarTab { FILES, GIT }

@Composable
fun GitPanel(
    projectPath: String,
    modifier: Modifier = Modifier,
    viewModel: GitViewModel,
    editorViewModel: EditorViewModel
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(stringResource(R.string.git_tab_changes), stringResource(R.string.git_tab_history))

    var showConfigDialog by remember { mutableStateOf(false) }
    var showBranchDialog by remember { mutableStateOf(false) }

    LaunchedEffect(projectPath) { viewModel.initialize(projectPath) }
    LaunchedEffect(viewModel.statusMessage) {
        viewModel.statusMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                GitToolbarCompact(
                    viewModel = viewModel,
                    onBranchClick = {
                        scope.launch {
                            viewModel.refreshAll()
                            showBranchDialog = true
                        }
                    },
                    onSettingsClick = { showConfigDialog = true },
                    onRefreshClick = { viewModel.refreshAll() }
                )

                if (viewModel.isGitProject) {
                    SecondaryTabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        divider = { HorizontalDivider() }
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = { Text(title, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (viewModel.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }

            if (!viewModel.isGitProject) {
                EmptyGitState { viewModel.initRepo() }
            } else {
                when (selectedTabIndex) {
                    0 -> GitChangesPageCompact(viewModel, editorViewModel, projectPath)
                    1 -> GitGraphListCompact(viewModel.commitLog)
                }
            }
        }
    }

    if (showConfigDialog) ConfigDialog(viewModel) { showConfigDialog = false }
    if (showBranchDialog) BranchListDialog(viewModel) { showBranchDialog = false }
}

@Composable
fun GitToolbarCompact(
    viewModel: GitViewModel,
    onBranchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val pushContentDescription = stringResource(R.string.content_desc_push)
    val pullContentDescription = stringResource(R.string.content_desc_pull)
    val moreOptionsContentDescription = stringResource(R.string.action_more_options)
    val refreshText = stringResource(R.string.action_refresh)
    val settingsText = stringResource(R.string.action_settings)
    val rebaseUpdateText = stringResource(R.string.git_rebase_update)
    val headText = stringResource(R.string.git_head)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        AssistChip(
            onClick = onBranchClick,
            label = {
                Text(
                    text = viewModel.currentBranch.ifEmpty { headText },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium
                )
            },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallSplit, null, Modifier.size(14.dp)) },
            modifier = Modifier.weight(1f).height(32.dp),
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(Modifier.width(4.dp))

        if (viewModel.isGitProject) {
            IconButton(onClick = { viewModel.push() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.CloudUpload, pushContentDescription, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { viewModel.pull() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.CloudDownload, pullContentDescription, modifier = Modifier.size(18.dp))
            }
        }

        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.MoreVert, moreOptionsContentDescription, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(refreshText) },
                    leadingIcon = { Icon(Icons.Default.Refresh, null) },
                    onClick = { onRefreshClick(); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text(settingsText) },
                    leadingIcon = { Icon(Icons.Default.Settings, null) },
                    onClick = { onSettingsClick(); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text(rebaseUpdateText) },
                    onClick = { viewModel.updateProject(true); showMenu = false }
                )
            }
        }
    }
}

@Composable
fun GitChangesPageCompact(
    viewModel: GitViewModel,
    editorViewModel: EditorViewModel,
    projectPath: String
) {
    var message by remember { mutableStateOf("") }
    var pushAfter by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        if (viewModel.remoteUrl.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.padding(8.dp).fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.git_no_remote),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(8.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            if (viewModel.changedFiles.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize().height(200.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.git_no_changes), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                items(viewModel.changedFiles) { file ->
                    GitFileItemCompact(
                        file = file,
                        onClick = {
                            val targetFile = File(projectPath, file.filePath)
                            editorViewModel.openDiff(projectPath, targetFile)
                        }
                    )
                }
            }
        }
        Surface(
            shadowElevation = 8.dp,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth().imePadding()
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    placeholder = { Text(stringResource(R.string.git_commit_message_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),//.height(80.dp),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Checkbox(checked = pushAfter, onCheckedChange = { pushAfter = it }, modifier = Modifier.scale(0.8f))
                    Text(stringResource(R.string.action_push), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { viewModel.commit(message, pushAfter); message = "" },
                        enabled = viewModel.changedFiles.isNotEmpty() && message.isNotBlank(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) { Text(stringResource(R.string.action_commit)) }
                }
            }
        }
    }
}

@Composable
fun GitFileItemCompact(file: GitFileChange, onClick: () -> Unit) {
    val fileName = file.filePath.substringAfterLast("/")
    val fileDir = file.filePath.substringBeforeLast("/", "")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = when (file.status) {
            GitFileStatus.ADDED -> Color(0xFF4CAF50)
            GitFileStatus.MODIFIED -> Color(0xFF2196F3)
            else -> Color(0xFFF44336)
        }
        Surface(
            color = color.copy(alpha = 0.1f),
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.size(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = file.status.name.first().toString(), color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(text = fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (fileDir.isNotEmpty()) {
                Text(text = fileDir, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun EmptyGitState(onInit: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Source, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.git_not_repo), color = MaterialTheme.colorScheme.outline)
        Button(onClick = onInit, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.git_init_repo)) }
    }
}

@Composable
fun ConfigDialog(viewModel: GitViewModel, onDismiss: () -> Unit) {
    var remote by remember { mutableStateOf(viewModel.remoteUrl) }
    var email by remember { mutableStateOf(viewModel.userEmail) }
    val currentAuth = viewModel.savedAuth
    var authType by remember { mutableStateOf(currentAuth?.type ?: AuthType.HTTPS) }
    var username by remember { mutableStateOf(currentAuth?.username ?: "") }
    var token by remember { mutableStateOf(currentAuth?.token ?: "") }
    var privateKey by remember { mutableStateOf(currentAuth?.privateKey ?: "") }
    var passphrase by remember { mutableStateOf(currentAuth?.passphrase ?: "") }

    val testResult = viewModel.testConnectionResult
    val isTesting = viewModel.isTestingConnection
    val repoConfigTitleText = stringResource(R.string.git_repo_config)
    val remoteUrlText = stringResource(R.string.git_remote_url)
    val remotePlaceholderText = stringResource(
        if (authType == AuthType.SSH) R.string.git_remote_placeholder_ssh
        else R.string.git_remote_placeholder_https
    )
    val committerEmailText = stringResource(R.string.git_committer_email)
    val authMethodText = stringResource(R.string.git_auth_method)
    val httpsText = stringResource(R.string.git_https)
    val sshKeyText = stringResource(R.string.git_ssh_key)
    val usernameText = stringResource(R.string.git_username)
    val tokenLabelText = stringResource(R.string.git_token_label)
    val tokenPlaceholderText = stringResource(R.string.git_token_placeholder)
    val patNoteText = stringResource(R.string.git_pat_note)
    val privateKeyText = stringResource(R.string.git_private_key)
    val privateKeyPlaceholderText = stringResource(R.string.git_private_key_placeholder)
    val passphraseOptionalText = stringResource(R.string.git_passphrase_optional)
    val connectingText = stringResource(R.string.status_connecting)
    val testConnectionText = stringResource(R.string.git_test_connection)
    val saveConfigText = stringResource(R.string.git_save_config)
    val cancelText = stringResource(R.string.action_cancel)

    AlertDialog(
        onDismissRequest = {
            viewModel.testConnectionResult = null
            viewModel.testConnectionSuccess = null
            onDismiss()
        },
        title = { Text(repoConfigTitleText) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = remote,
                    onValueChange = { remote = it },
                    label = { Text(remoteUrlText) },
                    placeholder = { Text(remotePlaceholderText) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(committerEmailText) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                HorizontalDivider()

                Text(authMethodText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = authType == AuthType.HTTPS,
                        onClick = { authType = AuthType.HTTPS },
                        label = { Text(httpsText) },
                        leadingIcon = { if (authType == AuthType.HTTPS) Icon(Icons.Default.Check, null) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = authType == AuthType.SSH,
                        onClick = { authType = AuthType.SSH },
                        label = { Text(sshKeyText) },
                        leadingIcon = { if (authType == AuthType.SSH) Icon(Icons.Default.Check, null) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (authType == AuthType.HTTPS) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(usernameText) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text(tokenLabelText) },
                        placeholder = { Text(tokenPlaceholderText) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text(
                        patNoteText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    OutlinedTextField(
                        value = privateKey,
                        onValueChange = { privateKey = it },
                        label = { Text(privateKeyText) },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        placeholder = { Text(privateKeyPlaceholderText) }
                    )
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text(passphraseOptionalText) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                if (testResult != null || isTesting) {
                    val isSuccess = viewModel.testConnectionSuccess == true
                    val containerColor = if (isTesting) MaterialTheme.colorScheme.surfaceVariant else if (isSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                    val contentColor = if (isTesting) MaterialTheme.colorScheme.onSurfaceVariant else if (isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer

                    Surface(
                        color = containerColor,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = contentColor)
                                Spacer(Modifier.width(8.dp))
                                Text(connectingText, style = MaterialTheme.typography.bodySmall, color = contentColor)
                            } else {
                                Icon(
                                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = testResult ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = contentColor,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Row {
                    TextButton(
                        onClick = { viewModel.testRemoteConnection(remote, authType, username, token, privateKey, passphrase) },
                        enabled = !isTesting && remote.isNotBlank()
                    ) {
                        Text(testConnectionText)
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = {
                            viewModel.saveConfig(remote, email, authType, username, token, privateKey, passphrase)
                            viewModel.testConnectionResult = null
                            viewModel.testConnectionSuccess = null
                            onDismiss()
                        },
                        enabled = !isTesting
                    ) {
                        Text(saveConfigText)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                viewModel.testConnectionResult = null
                viewModel.testConnectionSuccess = null
                onDismiss()
            }) { Text(cancelText) }
        }
    )
}

@Composable
fun BranchListDialog(viewModel: GitViewModel, onDismiss: () -> Unit) {
    var branches by remember { mutableStateOf(emptyList<GitBranch>()) }
    var showNewBranchInput by remember { mutableStateOf(false) }
    var showNewTagInput by remember { mutableStateOf(false) }
    val branchManagementText = stringResource(R.string.git_branch_management)
    val newBranchText = stringResource(R.string.git_new_branch)
    val newTagText = stringResource(R.string.git_new_tag)
    val localBranchesText = stringResource(R.string.git_local_branches)
    val remoteBranchesText = stringResource(R.string.git_remote_branches)
    val noBranchesText = stringResource(R.string.git_no_branches)
    val closeText = stringResource(R.string.action_close)

    LaunchedEffect(Unit) { branches = viewModel.getBranches() }

    if (showNewBranchInput) {
        NewBranchDialog(viewModel) {
            showNewBranchInput = false
            onDismiss()
        }
        return
    }

    if (showNewTagInput) {
        NewTagDialog(viewModel) {
            showNewTagInput = false
            onDismiss()
        }
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.CallSplit, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(branchManagementText)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { showNewBranchInput = true },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(newBranchText)
                    }

                    OutlinedButton(
                        onClick = { showNewTagInput = true },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Label, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(newTagText)
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()

                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    val localBranches = branches.filter { it.type == BranchType.LOCAL }
                    if (localBranches.isNotEmpty()) {
                        item {
                            Text(localBranchesText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                        }
                        items(localBranches) { b -> BranchItem(b, viewModel, onDismiss) }
                    }

                    val remoteBranches = branches.filter { it.type == BranchType.REMOTE }
                    if (remoteBranches.isNotEmpty()) {
                        item {
                            Text(remoteBranchesText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                        }
                        items(remoteBranches) { b -> BranchItem(b, viewModel, onDismiss) }
                    }

                    if (localBranches.isEmpty() && remoteBranches.isEmpty()) {
                        item { Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { Text(noBranchesText, color = Color.Gray) } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(closeText) } }
    )
}

@Composable
fun BranchItem(branch: GitBranch, viewModel: GitViewModel, onDismiss: () -> Unit) {
    val isCurrent = branch.isCurrent
    val bgColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable {
                if (!isCurrent) {
                    viewModel.checkout(branch.name)
                    onDismiss()
                }
            }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (branch.type == BranchType.LOCAL) Icons.Default.Computer else Icons.Default.Cloud,
            contentDescription = null,
            tint = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Gray,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = branch.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (isCurrent) {
            Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun NewBranchDialog(viewModel: GitViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val newBranchText = stringResource(R.string.git_new_branch)
    val createFromHeadText = stringResource(R.string.git_create_from_head)
    val branchNameText = stringResource(R.string.git_branch_name)
    val createAndSwitchText = stringResource(R.string.git_create_and_switch)
    val cancelText = stringResource(R.string.action_cancel)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(newBranchText) },
        text = {
            Column {
                Text(createFromHeadText, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(branchNameText) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { viewModel.createBranch(name); onDismiss() }, enabled = name.isNotBlank()) { Text(createAndSwitchText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancelText) } }
    )
}

@Composable
fun NewTagDialog(viewModel: GitViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    val newTagText = stringResource(R.string.git_new_tag)
    val tagNameText = stringResource(R.string.git_tag_name)
    val descriptionText = stringResource(R.string.git_description)
    val createText = stringResource(R.string.action_create)
    val cancelText = stringResource(R.string.action_cancel)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(newTagText) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text(tagNameText) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(msg, { msg = it }, label = { Text(descriptionText) }, modifier = Modifier.fillMaxWidth())
        }},
        confirmButton = {
            Button(onClick = { viewModel.createTag(name, msg); onDismiss() }, enabled = name.isNotBlank()) { Text(createText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancelText) } }
    )
}

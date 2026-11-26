package com.ngalite.app.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions as KeyboardOpts
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ngalite.app.data.CookieStore
import com.ngalite.app.data.NgaApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class AccountType(val label: String, val value: String)

private val ACCOUNT_TYPES = listOf(
    AccountType("用户名/昵称/手机号", ""),
    AccountType("邮箱", "mail"),
    AccountType("用户ID", "id"),
    AccountType("手机号", "phone"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginDialog(onDismiss: () -> Unit) {
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var typeIndex by remember { mutableIntStateOf(0) }
    var showPwd by remember { mutableStateOf(false) }

    var captchaSession by remember { mutableStateOf<NgaApi.CaptchaSession?>(null) }
    var captchaText by remember { mutableStateOf("") }
    var captchaBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var captchaLoading by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var successMsg by remember { mutableStateOf<String?>(null) }

    var showCookieFallback by remember { mutableStateOf(false) }
    var cookieInput by remember { mutableStateOf(CookieStore.get()) }

    val scope = rememberCoroutineScope()

    fun loadCaptchaImage(session: NgaApi.CaptchaSession) {
        captchaLoading = true
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { session.fetchImageBytes() }
                val bmp = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                captchaBitmap = bmp?.asImageBitmap()
            } catch (_: Exception) {
                captchaBitmap = null
            } finally {
                captchaLoading = false
            }
        }
    }

    val fieldShape = RoundedCornerShape(12.dp)

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        shape = RoundedCornerShape(22.dp),
        containerColor = ForumColors.Surface,
        title = {
            Column {
                Text(
                    text = "登录 NGA",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ForumColors.TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "登录凭证仅保存在本地设备",
                    style = MaterialTheme.typography.labelSmall,
                    color = ForumColors.Meta
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = account,
                    onValueChange = { account = it },
                    label = { Text("账号") },
                    singleLine = true,
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                var accountTypeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = accountTypeExpanded,
                    onExpandedChange = { accountTypeExpanded = !accountTypeExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = ACCOUNT_TYPES[typeIndex].label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("账号类型") },
                        shape = fieldShape,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountTypeExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = accountTypeExpanded,
                        onDismissRequest = { accountTypeExpanded = false }
                    ) {
                        ACCOUNT_TYPES.forEachIndexed { i, t ->
                            DropdownMenuItem(
                                text = { Text(t.label) },
                                onClick = {
                                    typeIndex = i
                                    accountTypeExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    shape = fieldShape,
                    visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOpts(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPwd = !showPwd }) {
                            Icon(
                                if (showPwd) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPwd) "隐藏密码" else "显示密码"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                AnimatedVisibility(
                    visible = captchaSession != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "图形验证码",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = ForumColors.TextPrimary
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ForumColors.SurfaceMuted)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(44.dp)
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                if (captchaBitmap != null) {
                                    Image(
                                        bitmap = captchaBitmap!!,
                                        contentDescription = "验证码",
                                        modifier = Modifier.height(44.dp)
                                    )
                                } else if (captchaLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                            IconButton(onClick = {
                                captchaSession?.let {
                                    it.refresh()
                                    loadCaptchaImage(it)
                                }
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = "换一张")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = captchaText,
                            onValueChange = { captchaText = it },
                            label = { Text("请输入验证码（6个字符）") },
                            singleLine = true,
                            shape = fieldShape,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                errorMsg?.let { msg ->
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ForumColors.HotSoft,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            color = ForumColors.Hot,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
                successMsg?.let { msg ->
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ForumColors.AccentSoft,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            color = ForumColors.Accent,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        if (isLoading) return@Button
                        errorMsg = null
                        successMsg = null
                        if (account.isBlank() || password.isBlank()) {
                            errorMsg = "请输入账号和密码"
                            return@Button
                        }
                        val cs = captchaSession
                        if (cs != null && captchaText.isBlank()) {
                            errorMsg = "请输入验证码"
                            return@Button
                        }
                        isLoading = true
                        scope.launch {
                            try {
                                val result = if (cs != null) {
                                    withContext(Dispatchers.IO) {
                                        cs.login(account, ACCOUNT_TYPES[typeIndex].value, password, captchaText)
                                    }
                                } else {
                                    withContext(Dispatchers.IO) {
                                        NgaApi.login(account, ACCOUNT_TYPES[typeIndex].value, password)
                                    }
                                }
                                CookieStore.save(result.cookie)
                                CookieStore.saveAccountName(result.username.ifBlank { account.trim() })
                                successMsg = "登录成功，正在进入…"
                                kotlinx.coroutines.delay(500)
                                onDismiss()
                            } catch (e: NgaApi.LoginException) {
                                val msg = e.message ?: "登录失败"
                                if (NgaApi.isCaptchaError(msg)) {
                                    if (cs != null) {
                                        captchaText = ""
                                        captchaBitmap = null
                                        cs.refresh()
                                        loadCaptchaImage(cs)
                                        errorMsg = msg
                                    } else {
                                        val newCs = NgaApi.CaptchaSession("login")
                                        captchaSession = newCs
                                        captchaText = ""
                                        captchaBitmap = null
                                        loadCaptchaImage(newCs)
                                        errorMsg = "请输入图形验证码后继续登录"
                                    }
                                } else {
                                    errorMsg = msg
                                }
                            } catch (e: Exception) {
                                errorMsg = "网络错误：${e.message ?: "请稍后重试"}"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("正在登录…")
                    } else {
                        Text("登录", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showCookieFallback = !showCookieFallback }) {
                        Text(
                            if (showCookieFallback) "收起 Cookie 登录" else "已有 Cookie？粘贴登录",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    if (CookieStore.isLogin()) {
                        Text("· 已登录", color = ForumColors.Accent, style = MaterialTheme.typography.labelSmall)
                    }
                }

                AnimatedVisibility(
                    visible = showCookieFallback,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        OutlinedTextField(
                            value = cookieInput,
                            onValueChange = { cookieInput = it },
                            label = { Text("粘贴 Cookie") },
                            singleLine = false,
                            maxLines = 4,
                            shape = fieldShape,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = {
                                CookieStore.save(cookieInput)
                                onDismiss()
                            }) { Text("保存 Cookie", fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("取消") }
        }
    )
}

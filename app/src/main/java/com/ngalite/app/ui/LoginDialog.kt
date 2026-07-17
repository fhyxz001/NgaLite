package com.ngalite.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.text.KeyboardOptions as KeyboardOpts
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** 账号类型选项，对应网页端 _loginUI 的 type 下拉。 */
private data class AccountType(val label: String, val value: String)

private val ACCOUNT_TYPES = listOf(
    AccountType("用户名/昵称/手机号", ""),
    AccountType("邮箱", "mail"),
    AccountType("用户ID", "id"),
    AccountType("手机号", "phone"),
)

/** 登录对话框：支持账号密码登录（RSA 加密）与粘贴 Cookie 兜底。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginDialog(onDismiss: () -> Unit) {
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var typeIndex by remember { mutableStateOf(0) }
    var showPwd by remember { mutableStateOf(false) }

    // 图形验证码
    var captchaSession by remember { mutableStateOf<NgaApi.CaptchaSession?>(null) }
    var captchaText by remember { mutableStateOf("") }
    var captchaBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var captchaLoading by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var successMsg by remember { mutableStateOf<String?>(null) }

    // 粘贴 Cookie 兜底面板（默认折叠）
    var showCookieFallback by remember { mutableStateOf(false) }
    var cookieInput by remember { mutableStateOf(CookieStore.get()) }

    val scope = rememberCoroutineScope()

    /** 加载或刷新验证码图片 */
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

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("登录", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 账号
                OutlinedTextField(
                    value = account,
                    onValueChange = { account = it },
                    label = { Text("账号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                // 账号类型
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
                Spacer(Modifier.height(8.dp))

                // 密码
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
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
                Spacer(Modifier.height(4.dp))

                // 图形验证码区域（仅需要时显示）
                if (captchaSession != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "图形验证码",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 验证码图片
                        if (captchaBitmap != null) {
                            Image(
                                bitmap = captchaBitmap!!,
                                contentDescription = "验证码",
                                modifier = Modifier
                                    .height(48.dp)
                                    .weight(1f)
                            )
                        } else if (captchaLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        // 刷新按钮
                        IconButton(onClick = {
                            captchaSession?.refresh()
                            loadCaptchaImage(captchaSession!!)
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "换一张")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = captchaText,
                        onValueChange = { captchaText = it },
                        label = { Text("请输入验证码（6个字符）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 用户协议默认同意，不显示勾选项

                errorMsg?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                successMsg?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
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
                                    successMsg = "登录成功"
                                    kotlinx.coroutines.delay(600)
                                    onDismiss()
                                } catch (e: NgaApi.LoginException) {
                                    val msg = e.message ?: "登录失败"
                                    if (NgaApi.isCaptchaError(ms
package com.ngalite.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.text.KeyboardOptions as KeyboardOpts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var agree by remember { mutableStateOf(false) }
    var showPwd by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var successMsg by remember { mutableStateOf<String?>(null) }

    // 粘贴 Cookie 兜底面板（默认折叠）
    var showCookieFallback by remember { mutableStateOf(false) }
    var cookieInput by remember { mutableStateOf(CookieStore.get()) }

    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("登录", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                // 账号
                OutlinedTextField(
                    value = account,
                    onValueChange = { account = it },
                    label = { Text("账号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                // 账号类型下拉
                ExposedDropdownLite(
                    label = ACCOUNT_TYPES[typeIndex].label,
                    expanded = typeMenuExpanded,
                    onExpandedChange = { typeMenuExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ACCOUNT_TYPES.forEachIndexed { i, t ->
                        DropdownMenuItem(
                            text = { Text(t.label, fontWeight = if (i == typeIndex) FontWeight.SemiBold else FontWeight.Normal) },
                            onClick = {
                                typeIndex = i
                                typeMenuExpanded = false
                            }
                        )
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

                // 协议勾选
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = agree, onCheckedChange = { agree = it })
                    Text(
                        "我已阅读并同意用户协议及隐私政策",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

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
                            if (!agree) {
                                errorMsg = "请先同意用户协议"
                                return@Button
                            }
                            isLoading = true
                            scope.launch {
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        NgaApi.login(account, ACCOUNT_TYPES[typeIndex].value, password)
                                    }
                                    CookieStore.save(result.cookie)
                                    CookieStore.saveAccountName(result.username.ifBlank { account.trim() })
                                    successMsg = "登录成功"
                                    // 短暂展示后关闭
                                    kotlinx.coroutines.delay(600)
                                    onDismiss()
                                } catch (e: NgaApi.LoginException) {
                                    errorMsg = e.message ?: "登录失败"
                                } catch (e: Exception) {
                                    errorMsg = "网络错误：${e.message ?: "请稍后重试"}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("登录")
                        }
                    }
                }

                // 粘贴 Cookie 兜底入口
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showCookieFallback = !showCookieFallback }) {
                        Text(
                            if (showCookieFallback) "收起粘贴 Cookie" else "已有 Cookie？粘贴登录",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    if (CookieStore.isLogin()) {
                        Spacer(Modifier.size(4.dp))
                        Text("· 已登录", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (showCookieFallback) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = cookieInput,
                        onValueChange = { cookieInput = it },
                        label = { Text("粘贴 Cookie") },
                        singleLine = false,
                        maxLines = 4,
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
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("取消") }
        }
    )
}

/** 轻量下拉选择器：点击标签展开菜单。 */
@Composable
private fun ExposedDropdownLite(
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        TextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { onExpandedChange(!expanded) }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "选择账号类型")
                }
            }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            content()
        }
    }
}

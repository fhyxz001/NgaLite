package com.ngalite.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.ngalite.app.data.CookieStore

/** Cookie 登录对话框：粘贴 Cookie 字符串保存 */
@Composable
fun LoginDialog(onDismiss: () -> Unit) {
    var cookie by remember { mutableStateOf(CookieStore.get()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cookie 登录") },
        text = {
            OutlinedTextField(
                value = cookie,
                onValueChange = { cookie = it },
                label = { Text("粘贴 Cookie") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                CookieStore.save(cookie)
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

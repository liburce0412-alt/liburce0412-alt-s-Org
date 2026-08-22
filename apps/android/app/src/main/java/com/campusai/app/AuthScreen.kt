package com.campusai.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.campusai.core.auth.AuthState
import com.campusai.core.designsystem.BrandMark
import com.campusai.core.designsystem.GlassPanel
import com.campusai.core.designsystem.SpectraPrimaryButton
import com.campusai.core.designsystem.TelemetryChip
import kotlinx.coroutines.launch

private enum class AuthMode { SIGN_IN, SIGN_UP }

@Composable
fun AuthScreen(
    state: AuthState,
    onSignIn: suspend (String, String) -> Boolean,
    onSignUp: suspend (String, String) -> Boolean,
    onClearMessage: () -> Unit,
    onBack: () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(AuthMode.SIGN_IN) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Box(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
        GlassPanel(Modifier.fillMaxWidth(), radius = 24, emphasized = true) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BrandMark(Modifier.size(72.dp))
                Text(if (mode == AuthMode.SIGN_IN) "登录 CampusAI" else "创建 CampusAI 账号", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (mode == AuthMode.SIGN_IN) "登录后可以同步、参与校园社区与交易；本地时间和课程表无需登录。"
                    else "只需邮箱和密码，不增加验证码步骤；注册成功后直接进入应用。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(.62f),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TelemetryChip("登录", mode == AuthMode.SIGN_IN, {
                        mode = AuthMode.SIGN_IN
                        confirmPassword = ""
                        onClearMessage()
                    }, Modifier.weight(1f))
                    TelemetryChip("注册", mode == AuthMode.SIGN_UP, {
                        mode = AuthMode.SIGN_UP
                        onClearMessage()
                    }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(2.dp))
                OutlinedTextField(email, { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("邮箱") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("密码") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(12.dp))
                if (mode == AuthMode.SIGN_UP) OutlinedTextField(
                    confirmPassword,
                    { confirmPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("确认密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    isError = confirmPassword.isNotBlank() && confirmPassword != password,
                    supportingText = if (confirmPassword.isNotBlank() && confirmPassword != password) ({ Text("两次输入的密码不一致。") }) else null,
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                state.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
                SpectraPrimaryButton(
                    text = when {
                        state.busy && mode == AuthMode.SIGN_IN -> "正在登录"
                        state.busy -> "正在创建账号"
                        mode == AuthMode.SIGN_IN -> "安全登录"
                        else -> "直接注册并登录"
                    },
                    onClick = { scope.launch {
                        val succeeded = if (mode == AuthMode.SIGN_IN) onSignIn(email, password) else onSignUp(email, password)
                        if (succeeded) onBack()
                    } },
                    modifier = Modifier.fillMaxWidth(),
                    icon = if (mode == AuthMode.SIGN_IN) Icons.Rounded.Lock else Icons.Rounded.PersonAdd,
                    enabled = email.contains('@') && password.length >= 8 && (mode == AuthMode.SIGN_IN || confirmPassword == password) && !state.busy,
                )
            }
        }
    }
}

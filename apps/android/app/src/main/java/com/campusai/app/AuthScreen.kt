package com.campusai.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
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
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(state: AuthState, onSignIn: suspend (String, String) -> Boolean, onBack: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
        GlassPanel(Modifier.fillMaxWidth(), radius = 24, emphasized = true) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BrandMark(Modifier.size(72.dp))
                Text("登录 CampusAI", style = MaterialTheme.typography.headlineMedium)
                Text("登录后才能同步社区、交易与 AI；本地时间和课程表无需登录。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.62f))
                Spacer(Modifier.height(2.dp))
                OutlinedTextField(email, { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("邮箱") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("密码") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(12.dp))
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                SpectraPrimaryButton(
                    text = if (state.busy) "正在验证" else "安全登录",
                    onClick = { scope.launch { if (onSignIn(email, password)) onBack() } },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Lock,
                    enabled = email.contains('@') && password.length >= 8 && !state.busy,
                )
            }
        }
    }
}

package com.example.features.v2

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.network.SupabaseManager
import kotlinx.coroutines.launch

// High-fidelity avatars grid for premium registration selector
val PRESETS_AVATARS = listOf(
    "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&q=80&w=150",
    "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&q=80&w=150",
    "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&q=80&w=150",
    "https://images.unsplash.com/photo-1560250097-0b93528c311a?auto=format&fit=crop&q=80&w=150",
    "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?auto=format&fit=crop&q=80&w=150",
    "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&q=80&w=150"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    
    // Register Extras
    var nickname by remember { mutableStateOf("") }
    var selectedAvatar by remember { mutableStateOf(PRESETS_AVATARS[0]) }
    var school by remember { mutableStateOf("北京大学") }
    var college by remember { mutableStateOf("软件微电子学院") }
    var grade by remember { mutableStateOf("大一") }
    var bio by remember { mutableStateOf("") }
    
    var isLoading by remember { mutableStateOf(false) }
    var showAvatarPicker by remember { mutableStateOf(false) }

    // Gemini Gradient Colors
    val geminiBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF4285F4),
            Color(0xFF8B5CF6),
            Color(0xFFEC4899),
            Color(0xFFF59E0B)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115)) // Twilight Canvas
    ) {
        // Aesthetic Glowing Gradients (Gemini soft glass glow)
        Box(
            modifier = Modifier
                .size(350.dp)
                .align(Alignment.TopEnd)
                .offset(x = 100.dp, y = (-80).dp)
                .background(Brush.radialGradient(listOf(Color(0xFF4285F4).copy(alpha = 0.15f), Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .size(400.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-120).dp, y = 120.dp)
                .background(Brush.radialGradient(listOf(Color(0xFFEC4899).copy(alpha = 0.12f), Color.Transparent)))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // OpenAI × Gemini Fusion Text Title Header
            Text(
                text = "CampusAI",
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    fontSize = 32.sp,
                    brush = geminiBrush
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "柳比歇夫自律量化 × 校园共享网络 v2.0",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Glassmorphic Login card (white border, low opacity surface)
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2026).copy(alpha = 0.85f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = if (isSignUp) "建立您的学术账号" else "登录您的 Campus 空间",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (isSignUp) {
                        // Avatar Selector Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAvatarPicker = true }
                                .background(Color(0xFF262930), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            AsyncImage(
                                model = selectedAvatar,
                                contentDescription = "avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("选择系统专属头像", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("点击选取更个性的面孔", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, "select", tint = Color.LightGray)
                        }

                        // Nickname field
                        OutlinedTextField(
                            value = nickname,
                            onValueChange = { nickname = it },
                            placeholder = { Text("用户昵称 (如: 学雷锋的杰克)", color = Color.Gray, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Person, "user", tint = Color.White.copy(alpha = 0.6f)) },
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF262930),
                                unfocusedContainerColor = Color(0xFF262930),
                                focusedLabelColor = Color.White,
                                cursorColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().testTag("auth_reg_nickname")
                        )
                    }

                    // Email field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = { Text("电子邮箱 (用于 Supabase 凭证)", color = Color.Gray, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Email, "email", tint = Color.White.copy(alpha = 0.6f)) },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF262930),
                            unfocusedContainerColor = Color(0xFF262930),
                            focusedLabelColor = Color.White,
                            cursorColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("auth_email")
                    )

                    // Password field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text("账户安全密码 (不少于6位)", color = Color.Gray, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Lock, "lock", tint = Color.White.copy(alpha = 0.6f)) },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF262930),
                            unfocusedContainerColor = Color(0xFF262930),
                            focusedLabelColor = Color.White,
                            cursorColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("auth_password")
                    )

                    if (isSignUp) {
                        // School metadata fields
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = school,
                                onValueChange = { school = it },
                                placeholder = { Text("学校", color = Color.Gray, fontSize = 12.sp) },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF262930),
                                    unfocusedContainerColor = Color(0xFF262930)
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = college,
                                onValueChange = { college = it },
                                placeholder = { Text("院系", color = Color.Gray, fontSize = 12.sp) },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF262930),
                                    unfocusedContainerColor = Color(0xFF262930)
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1.2f)
                            )
                            OutlinedTextField(
                                value = grade,
                                onValueChange = { grade = it },
                                placeholder = { Text("年级", color = Color.Gray, fontSize = 12.sp) },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF262930),
                                    unfocusedContainerColor = Color(0xFF262930)
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(0.8f)
                            )
                        }

                        // Bio
                        OutlinedTextField(
                            value = bio,
                            onValueChange = { bio = it },
                            placeholder = { Text("个性签名 (例如: 今日事今日毕)", color = Color.Gray, fontSize = 13.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF262930),
                                unfocusedContainerColor = Color(0xFF262930)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally).size(28.dp),
                            color = Color(0xFF4285F4)
                        )
                    } else {
                        // Main Action Capsule Button
                        Button(
                            onClick = {
                                if (email.isEmpty() || password.isEmpty() || (isSignUp && nickname.isEmpty())) {
                                    Toast.makeText(context, "请完整填写必填项信息！", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (password.length < 6) {
                                    Toast.makeText(context, "密码需至少达到 6 位以确保安全性！", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isLoading = true
                                scope.launch {
                                    if (isSignUp) {
                                        val result = SupabaseManager.register(
                                            context, email, password, nickname, selectedAvatar,
                                            school, college, grade, bio
                                        )
                                        isLoading = false
                                        if (result.isSuccess) {
                                            Toast.makeText(context, "注册成功 & 自动登入！", Toast.LENGTH_SHORT).show()
                                            onAuthSuccess()
                                        } else {
                                            Toast.makeText(context, result.exceptionOrNull()?.message ?: "注册失败", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        val result = SupabaseManager.login(context, email, password)
                                        isLoading = false
                                        if (result.isSuccess) {
                                            Toast.makeText(context, "登录成功！欢迎回到 CampusAI", Toast.LENGTH_SHORT).show()
                                            onAuthSuccess()
                                        } else {
                                            Toast.makeText(context, result.exceptionOrNull()?.message ?: "登录失败", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(100),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            contentPadding = PaddingValues(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("auth_action_btn")
                        ) {
                            Text(
                                text = if (isSignUp) "建立并登入空间" else "进入安全终端",
                                color = Color(0xFF0F1115),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Swap Mode Toggle
            TextButton(
                onClick = { isSignUp = !isSignUp },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("auth_swap_toggle")
            ) {
                Text(
                    text = if (isSignUp) "已经建立过自律空间？立即登录" else "初访 CampusAI？创建独立学术账号",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // High profile premium picker overlay Dialog
    if (showAvatarPicker) {
        AlertDialog(
            onDismissRequest = { showAvatarPicker = false },
            title = { Text("选择系统提供的高阶立体头像", color = Color.White) },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(PRESETS_AVATARS) { av ->
                        Card(
                            modifier = Modifier
                                .size(70.dp)
                                .clickable {
                                    selectedAvatar = av
                                    showAvatarPicker = false
                                }
                                .border(
                                    width = if (selectedAvatar == av) 2.dp else 0.dp,
                                    color = Color(0xFF4285F4),
                                    shape = CircleShape
                                ),
                            shape = CircleShape
                        ) {
                            AsyncImage(
                                model = av,
                                contentDescription = "av",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAvatarPicker = false }) {
                    Text("取消")
                }
            },
            containerColor = Color(0xFF1E2026),
            textContentColor = Color.White,
            titleContentColor = Color.White
        )
    }
}

package com.tavern.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tavern.app.core.security.AppLockManager
import com.tavern.app.core.util.AppSettings
import com.tavern.app.feature.character.CharacterDetailScreen
import com.tavern.app.feature.character.CharacterListScreen
import com.tavern.app.feature.chat.ChatScreen
import com.tavern.app.feature.plugin.PluginScreen
import com.tavern.app.feature.settings.ImageSettingsScreen
import com.tavern.app.feature.settings.ModelSettingsScreen
import com.tavern.app.feature.settings.SettingsHomeScreen
import com.tavern.app.feature.settings.VoiceSettingsScreen
import com.tavern.app.ui.theme.TavernTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 主 Activity（导航入口）
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appSettings: AppSettings

    @Inject
    lateinit var appLock: AppLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkMode by appSettings.darkMode.collectAsState()
            TavernTheme(
                darkTheme = darkMode == "system" && isSystemInDarkTheme()
            ) {
                // 应用锁：退后台30秒后上锁
                val lifecycleOwner = LocalLifecycleOwner.current
                androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_STOP -> appLock.onAppPaused()
                            Lifecycle.Event.ON_START -> appLock.onAppResumed()
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                val locked by appLock.locked.collectAsState()

                Box(modifier = Modifier.fillMaxSize()) {
                    TavernNavHost()
                    if (locked) {
                        LockScreen(
                            appLock = appLock,
                            onUnlocked = { appLock.unlock() }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 应用锁解锁页（PIN）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockScreen(
    appLock: AppLockManager,
    onUnlocked: () -> Unit
) {
    var pin by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("NewLove 已锁定", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "输入 PIN 解锁",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                        pin = it
                        error = false
                    }
                },
                label = { Text("PIN（4-6位）") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = error,
                supportingText = if (error) ({ Text("PIN 错误") }) else null,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (appLock.verifyPin(pin)) {
                        onUnlocked()
                    } else {
                        error = true
                    }
                },
                enabled = pin.length >= 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("解锁")
            }
        }
    }
}

@Composable
fun TavernNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "character_list"
    ) {
        composable("character_list") {
            CharacterListScreen(
                onCardClick = { cardId ->
                    navController.navigate("character_detail/$cardId")
                },
                onOpenSession = { cardId, sessionId ->
                    // 聊天记录直达对应会话
                    navController.navigate("chat/$cardId?sessionId=$sessionId")
                },
                onOpenModelSettings = { navController.navigate("model_settings") },
                onOpenImageSettings = { navController.navigate("image_settings") },
                onOpenPluginSettings = { navController.navigate("plugin_settings") },
                onOpenVoiceSettings = { navController.navigate("voice_settings") }
            )
        }

        composable(
            route = "character_detail/{cardId}?openTab={openTab}",
            arguments = listOf(
                navArgument("cardId") { type = NavType.LongType },
                navArgument("openTab") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { backStackEntry ->
            val cardId = backStackEntry.arguments?.getLong("cardId") ?: -1L
            val openTab = backStackEntry.arguments?.getInt("openTab") ?: 0
            CharacterDetailScreen(
                onBack = { navController.popBackStack() },
                initialTab = openTab,
                onStartChat = { greetingIndex, newSession ->
                    if (newSession) {
                        navController.navigate("chat/$cardId?greeting=$greetingIndex&newSession=1")
                    } else {
                        navController.navigate("chat/$cardId?greeting=$greetingIndex")
                    }
                }
            )
        }

        composable(
            route = "chat/{cardId}?sessionId={sessionId}&newSession={newSession}",
            arguments = listOf(
                navArgument("cardId") { type = NavType.LongType },
                navArgument("sessionId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("newSession") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { backStackEntry ->
            val cardId = backStackEntry.arguments?.getLong("cardId") ?: -1L
            ChatScreen(
                onBack = { navController.popBackStack() },
                onOpenSettings = {
                    navController.navigate("settings_home")
                },
                onOpenMemory = {
                    navController.navigate("character_detail/$cardId?openTab=4")
                }
            )
        }

        composable("settings_home") {
            SettingsHomeScreen(
                onBack = { navController.popBackStack() },
                onOpenModelSettings = { navController.navigate("model_settings") },
                onOpenImageSettings = { navController.navigate("image_settings") },
                onOpenPluginSettings = { navController.navigate("plugin_settings") },
                onOpenVoiceSettings = { navController.navigate("voice_settings") }
            )
        }

        composable("model_settings") {
            ModelSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("image_settings") {
            ImageSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("voice_settings") {
            VoiceSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("plugin_settings") {
            PluginScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

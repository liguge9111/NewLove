package com.tavern.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkMode by appSettings.darkMode.collectAsState()
            TavernTheme(
                darkTheme = darkMode == "system" && isSystemInDarkTheme()
            ) {
                TavernNavHost()
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
                onOpenSession = { cardId ->
                    // 聊天记录直达聊天
                    navController.navigate("chat/$cardId")
                },
                onOpenModelSettings = { navController.navigate("model_settings") },
                onOpenImageSettings = { navController.navigate("image_settings") },
                onOpenPluginSettings = { navController.navigate("plugin_settings") },
                onOpenVoiceSettings = { navController.navigate("voice_settings") }
            )
        }

        composable(
            route = "character_detail/{cardId}",
            arguments = listOf(navArgument("cardId") { type = NavType.LongType })
        ) { backStackEntry ->
            val cardId = backStackEntry.arguments?.getLong("cardId") ?: -1L
            CharacterDetailScreen(
                onBack = { navController.popBackStack() },
                onStartChat = { greetingIndex ->
                    navController.navigate("chat/$cardId?greeting=$greetingIndex")
                }
            )
        }

        composable(
            route = "chat/{cardId}?greeting={greeting}",
            arguments = listOf(
                navArgument("cardId") { type = NavType.LongType },
                navArgument("greeting") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) {
            ChatScreen(
                onBack = { navController.popBackStack() },
                onOpenSettings = {
                    navController.navigate("settings_home")
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

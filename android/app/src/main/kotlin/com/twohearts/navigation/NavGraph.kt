package com.twohearts.navigation

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.twohearts.di.TokenStore
import com.twohearts.ui.chat.ChatScreen
import com.twohearts.ui.login.LoginScreen
import com.twohearts.ui.matches.MatchesScreen
import com.twohearts.ui.profile.CreateProfileScreen
import dagger.hilt.android.EntryPointAccessors
import hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

sealed class Route(val path: String) {
    object Login          : Route("login")
    object CreateProfile  : Route("create_profile")
    object Matches        : Route("matches")
    object Chat           : Route("chat/{conversationId}/{recipientName}") {
        fun build(convId: String, name: String) = "chat/$convId/${name.encodeUrl()}"
    }
}

fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")
fun String.decodeUrl(): String = java.net.URLDecoder.decode(this, "UTF-8")

@Composable
fun TwoHeartsNavHost(initialMagicToken: String? = null) {
    val navController = rememberNavController()
    val scope         = rememberCoroutineScope()

    NavHost(navController = navController, startDestination = Route.Login.path) {

        composable(Route.Login.path) {
            LoginScreen(
                initialToken  = initialMagicToken,
                onLoginSuccess = {
                    navController.navigate(Route.Matches.path) {
                        popUpTo(Route.Login.path) { inclusive = true }
                    }
                },
                onNeedProfile = {
                    navController.navigate(Route.CreateProfile.path) {
                        popUpTo(Route.Login.path) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.CreateProfile.path) {
            CreateProfileScreen(
                onProfileSaved = {
                    navController.navigate(Route.Matches.path) {
                        popUpTo(Route.CreateProfile.path) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.Matches.path) {
            MatchesScreen(
                onOpenChat = { convId, name ->
                    navController.navigate(Route.Chat.build(convId, name))
                }
            )
        }

        composable(
            route     = Route.Chat.path,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("recipientName")  { type = NavType.StringType }
            )
        ) { backStack ->
            val convId = backStack.arguments?.getString("conversationId") ?: return@composable
            val name   = backStack.arguments?.getString("recipientName")?.decodeUrl() ?: "Match"
            ChatScreen(
                conversationId    = convId,
                recipientName     = name,
                onNavigateBack    = { navController.popBackStack() }
            )
        }
    }
}

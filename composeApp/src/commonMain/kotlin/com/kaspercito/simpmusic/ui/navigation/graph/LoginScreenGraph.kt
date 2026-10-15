package com.kaspercito.simpmusic.ui.navigation.graph

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.kaspercito.simpmusic.ui.navigation.destination.login.DiscordLoginDestination
import com.kaspercito.simpmusic.ui.navigation.destination.login.LastfmLoginDestination
import com.kaspercito.simpmusic.ui.navigation.destination.login.LoginDestination
import com.kaspercito.simpmusic.ui.navigation.destination.login.SpotifyLoginDestination
import com.kaspercito.simpmusic.ui.screen.login.DiscordLoginScreen
import com.kaspercito.simpmusic.ui.screen.login.LastfmLoginScreen
import com.kaspercito.simpmusic.ui.screen.login.LoginScreen
import com.kaspercito.simpmusic.ui.screen.login.SpotifyLoginScreen

fun NavGraphBuilder.loginScreenGraph(
    innerPadding: PaddingValues,
    navController: NavController,
    hideBottomBar: () -> Unit,
    showBottomBar: () -> Unit,
) {
    composable<LoginDestination> {
        LoginScreen(
            innerPadding = innerPadding,
            navController = navController,
            hideBottomNavigation = hideBottomBar,
            showBottomNavigation = showBottomBar,
        )
    }

    composable<SpotifyLoginDestination> {
        SpotifyLoginScreen(
            innerPadding = innerPadding,
            navController = navController,
            hideBottomNavigation = hideBottomBar,
            showBottomNavigation = showBottomBar,
        )
    }

    composable<DiscordLoginDestination> {
        DiscordLoginScreen(
            innerPadding = innerPadding,
            navController = navController,
            hideBottomNavigation = hideBottomBar,
            showBottomNavigation = showBottomBar,
        )
    }

    composable<LastfmLoginDestination> {
        LastfmLoginScreen(
            innerPadding = innerPadding,
            navController = navController,
            hideBottomNavigation = hideBottomBar,
            showBottomNavigation = showBottomBar,
        )
    }
}
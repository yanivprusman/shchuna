package com.automatelinux.shchuna

import androidx.compose.runtime.Composable
import com.automatelinux.shchuna.ui.Actions
import com.automatelinux.shchuna.ui.ScreenState
import com.automatelinux.shchuna.ui.WhereScreen
import com.automatelinux.shchuna.ui.theme.AppTheme

// Shared entry composable — rendered by MainActivity on Android and (on a Mac)
// by ComposeUIViewController on iOS. The platform supplies state and actions.
@Composable
fun App(state: ScreenState, actions: Actions) {
    AppTheme {
        WhereScreen(state, actions)
    }
}

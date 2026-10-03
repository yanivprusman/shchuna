package com.automatelinux.whereAmI

import androidx.compose.runtime.Composable
import com.automatelinux.whereAmI.ui.Actions
import com.automatelinux.whereAmI.ui.ScreenState
import com.automatelinux.whereAmI.ui.WhereScreen
import com.automatelinux.whereAmI.ui.theme.AppTheme

// Shared entry composable — rendered by MainActivity on Android and (on a Mac)
// by ComposeUIViewController on iOS. The platform supplies state and actions.
@Composable
fun App(state: ScreenState, actions: Actions) {
    AppTheme {
        WhereScreen(state, actions)
    }
}

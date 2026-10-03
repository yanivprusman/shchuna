package com.automatelinux.whereAmI

import android.Manifest
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.automatelinux.whereAmI.ui.Actions

// Thin Android launcher — the UI is the shared commonMain App() composable.
class MainActivity : ComponentActivity() {
    private val viewModel: WhereViewModel by viewModels()

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light page, so dark bar icons — not guessed from the device's day/night setting.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val actions = Actions(
            refresh = viewModel::refresh,
            grantPermission = { askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
            startParking = { viewModel.startParking(it) },
            confirmParking = viewModel::confirmParking,
            dismissConfirmation = viewModel::dismissConfirmation,
            stopParking = viewModel::stopParking,
            search = viewModel::onQueryChange,
            openPlace = viewModel::openPlace,
            backToLive = viewModel::backToLive,
        )
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            App(state, actions)
        }
        if (savedInstanceState == null) {
            askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.start()
    }
}

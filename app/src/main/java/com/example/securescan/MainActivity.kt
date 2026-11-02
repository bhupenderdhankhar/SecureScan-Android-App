package com.example.securescan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.example.securescan.ui.navigation.AppNavigation
import com.example.securescan.ui.navigation.Screen
import com.example.securescan.ui.theme.SecureScanTheme
import com.example.securescan.utils.AuthManager
import com.example.securescan.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private companion object {
        const val DEFAULT_LOGIN_EMAIL = "rahuldhankher04@gmail.com"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Development login bypass: start as the default user unless they explicitly logged out.
        val authManager     = AuthManager(applicationContext)
        val isSessionValid  = runBlocking {
            authManager.enableDefaultLoginIfAllowed(DEFAULT_LOGIN_EMAIL)
        }
        val startDestination = if (isSessionValid) Screen.Home.route else Screen.Login.route

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val darkMode by settingsViewModel.darkMode.collectAsState(initial = false)

            SecureScanTheme(darkTheme = darkMode)
            {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavigation(
                        navController    = navController,
                        modifier         = Modifier.padding(innerPadding),
                        startDestination = startDestination
                    )
                }
            }
        }
    }
}

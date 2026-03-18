package com.example.simpleshell

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.example.simpleshell.domain.model.Language
import com.example.simpleshell.domain.model.ThemeMode
import com.example.simpleshell.ui.MainViewModel
import com.example.simpleshell.ui.navigation.NavGraph
import com.example.simpleshell.ui.theme.SimpleShellTheme
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.example.simpleshell.utils.BiometricHelper
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val mainUiState by mainViewModel.uiState.collectAsState()

            LaunchedEffect(mainUiState.language) {
                val targetLocaleList = when (mainUiState.language) {
                    Language.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
                    Language.ENGLISH -> LocaleListCompat.forLanguageTags("en-US")
                    Language.CHINESE -> LocaleListCompat.forLanguageTags("zh-CN")
                }
                val currentLocaleList = AppCompatDelegate.getApplicationLocales()
                if (currentLocaleList != targetLocaleList) {
                    AppCompatDelegate.setApplicationLocales(targetLocaleList)
                }
            }

            val darkTheme = when (mainUiState.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            SimpleShellTheme(
                darkTheme = darkTheme,
                dynamicColor = mainUiState.dynamicColor,
                themeColor = mainUiState.themeColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    val isBiometricAvailable = remember { BiometricHelper.isBiometricAvailable(context) }
                    var isAuthenticated by remember { mutableStateOf(!isBiometricAvailable) }

                    LaunchedEffect(isBiometricAvailable) {
                        if (isBiometricAvailable && !isAuthenticated) {
                            BiometricHelper.showBiometricPrompt(
                                activity = context as FragmentActivity,
                                onSuccess = { isAuthenticated = true },
                                onError = { _, _ -> },
                                onFailed = { }
                            )
                        }
                    }

                    if (isAuthenticated) {
                        val navController = rememberNavController()
                        NavGraph(navController = navController)
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    modifier = Modifier.height(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("App is locked")
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = {
                                    BiometricHelper.showBiometricPrompt(
                                        activity = context as FragmentActivity,
                                        onSuccess = { isAuthenticated = true },
                                        onError = { _, _ -> },
                                        onFailed = { }
                                    )
                                }) {
                                    Text("Unlock")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.rgbpos.bigs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.rgbpos.bigs.data.api.ApiClient
import com.rgbpos.bigs.ui.login.LoginScreen
import com.rgbpos.bigs.ui.pos.PosScreen
import com.rgbpos.bigs.ui.theme.BigsPOSTheme
import com.rgbpos.bigs.util.TokenStore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BigsPOSTheme {
                var isLoggedIn by remember { mutableStateOf<Boolean?>(null) }
                var userName by remember { mutableStateOf("") }

                LaunchedEffect(Unit) {
                    val token = TokenStore.getToken(this@MainActivity)
                    if (token != null) {
                        ApiClient.setToken(token)
                        userName = TokenStore.getUserName(this@MainActivity) ?: ""
                        isLoggedIn = true
                    } else {
                        isLoggedIn = false
                    }
                }

                when (isLoggedIn) {
                    null -> {} // Loading
                    false -> LoginScreen(
                        onLoginSuccess = { token, name ->
                            ApiClient.setToken(token)
                            userName = name
                            isLoggedIn = true
                        }
                    )
                    true -> PosScreen(
                        userName = userName,
                        onLogout = {
                            lifecycleScope.launch {
                                try { ApiClient.service.logout() } catch (_: Exception) {}
                                TokenStore.clear(this@MainActivity)
                                ApiClient.setToken(null)
                                isLoggedIn = false
                            }
                        }
                    )
                }
            }
        }
    }
}

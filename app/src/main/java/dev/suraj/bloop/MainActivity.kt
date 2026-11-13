package dev.suraj.bloop

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.edit

enum class Screen { Login, Signup, Home, AddReminder, Profile }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Only the login token is saved on the phone; everything else comes from the backend.
        val prefs = getSharedPreferences("bloop", Context.MODE_PRIVATE)
        BloopApi.token = prefs.getString("token", null)

        setContent {
            BloopTheme {
                var screen by remember { mutableStateOf(if (BloopApi.token != null) Screen.Home else Screen.Login) }
                var user by remember { mutableStateOf<User?>(null) }

                fun loggedIn(token: String, u: User) {
                    BloopApi.token = token
                    prefs.edit { putString("token", token) }
                    user = u
                    screen = Screen.Home
                }

                fun logout() {
                    BloopApi.token = null
                    prefs.edit { remove("token") }
                    user = null
                    screen = Screen.Login
                }

                // Opening the app while logged in: fetch the user's details.
                LaunchedEffect(BloopApi.token) {
                    if (BloopApi.token != null && user == null) {
                        BloopApi.me().onSuccess { user = it }.onFailure { if (it is NotLoggedIn) logout() }
                    }
                }

                BackHandler(enabled = screen == Screen.Signup || screen == Screen.AddReminder || screen == Screen.Profile) {
                    screen = if (screen == Screen.Signup) Screen.Login else Screen.Home
                }

                Box(Modifier.fillMaxSize().background(BloopColors.Bg)) {
                    when (screen) {
                        Screen.Login -> LoginScreen(onLoggedIn = ::loggedIn, onSignup = { screen = Screen.Signup })
                        Screen.Signup -> SignupScreen(onSignedUp = ::loggedIn, onBack = { screen = Screen.Login })
                        Screen.Home -> HomeScreen(
                            user = user,
                            onAddReminder = { screen = Screen.AddReminder },
                            onProfile = { screen = Screen.Profile },
                            onAuthExpired = ::logout,
                        )
                        Screen.Profile -> ProfileScreen(
                            user = user,
                            onBack = { screen = Screen.Home },
                            onLogout = ::logout,
                            onAuthExpired = ::logout,
                            onUserChanged = { user = it },
                        )
                        Screen.AddReminder -> AddReminderScreen(
                            user = user,
                            onSaved = { screen = Screen.Home },
                            onBack = { screen = Screen.Home },
                            onAuthExpired = ::logout,
                        )
                    }
                }
            }
        }
    }
}

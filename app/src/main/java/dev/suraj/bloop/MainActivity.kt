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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.edit

enum class Screen { Phone, Otp, Name, Home, AddReminder, EditReminder, Profile }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Only the login token is saved on the phone; everything else comes from the backend.
        val prefs = getSharedPreferences("bloop", Context.MODE_PRIVATE)
        BloopApi.token = prefs.getString("token", null)

        setContent {
            BloopTheme {
                var screen by remember { mutableStateOf(if (BloopApi.token != null) Screen.Home else Screen.Phone) }
                var phone by remember { mutableStateOf("") }               // number being verified
                var signupToken by remember { mutableStateOf<String?>(null) } // set when that number has no account yet
                var user by remember { mutableStateOf<User?>(null) }
                var editing by remember { mutableStateOf<Reminder?>(null) } // reminder open on the Edit screen

                fun loggedIn(token: String, u: User) {
                    BloopApi.token = token
                    prefs.edit { putString("token", token) }
                    user = u
                    signupToken = null
                    screen = Screen.Home
                }

                fun logout() {
                    BloopApi.token = null
                    prefs.edit { remove("token") }
                    user = null
                    signupToken = null
                    screen = Screen.Phone
                }

                // Opening the app while logged in: fetch the user's details.
                LaunchedEffect(BloopApi.token) {
                    if (BloopApi.token != null && user == null) {
                        BloopApi.me().onSuccess { user = it }.onFailure { if (it is NotLoggedIn) logout() }
                    }
                }

                BackHandler(enabled = screen != Screen.Phone && screen != Screen.Home) {
                    screen = when (screen) {
                        Screen.Otp, Screen.Name -> Screen.Phone
                        else -> Screen.Home
                    }
                }

                Box(Modifier.fillMaxSize().background(BloopColors.Bg)) {
                    when (screen) {
                        Screen.Phone -> PhoneScreen(initialNumber = phone, onOtpSent = { phone = it; screen = Screen.Otp })
                        Screen.Otp -> OtpScreen(
                            whatsappNumber = phone,
                            onBack = { screen = Screen.Phone },
                            onLoggedIn = ::loggedIn,
                            onNewUser = { signupToken = it; screen = Screen.Name },
                        )
                        Screen.Name -> {
                            val t = signupToken
                            if (t == null) {
                                LaunchedEffect(Unit) { screen = Screen.Phone }
                            } else {
                                NameScreen(signupToken = t, onSignedUp = ::loggedIn, onBack = { signupToken = null; screen = Screen.Phone })
                            }
                        }
                        Screen.Home -> HomeScreen(
                            user = user,
                            onAddReminder = { screen = Screen.AddReminder },
                            onEditReminder = { editing = it; screen = Screen.EditReminder },
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
                        Screen.EditReminder -> {
                            val r = editing
                            if (r == null) {
                                LaunchedEffect(Unit) { screen = Screen.Home }
                            } else {
                                // key: a different reminder starts with fresh form state
                                key(r.id) {
                                    EditReminderScreen(
                                        user = user,
                                        reminder = r,
                                        onBack = { screen = Screen.Home },
                                        onDeleted = { editing = null; screen = Screen.Home },
                                        onAuthExpired = ::logout,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

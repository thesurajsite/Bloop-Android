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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.edit
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener

enum class Screen { Loading, Phone, Otp, Name, Paywall, Home, AddReminder, EditReminder, Profile }

class MainActivity : ComponentActivity(), PaymentResultWithDataListener {
    // Razorpay reports the checkout result to the Activity; the paywall waits for it.
    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        RazorpayResults.channel.trySend(CheckoutResult.Success(razorpayPaymentId))
    }

    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        RazorpayResults.channel.trySend(CheckoutResult.Error(code, response))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Checkout.preload(applicationContext) // TEMP test

        // Only the login token is saved on the phone; everything else comes from the backend.
        val prefs = getSharedPreferences("bloop", Context.MODE_PRIVATE)
        BloopApi.token = prefs.getString("token", null)

        setContent {
            BloopTheme {
                // Logged in: check with the backend that the user is premium before opening Home.
                var screen by remember { mutableStateOf(if (BloopApi.token != null) Screen.Loading else Screen.Phone) }
                var phone by remember { mutableStateOf("") }               // number being verified
                var signupToken by remember { mutableStateOf<String?>(null) } // set when that number has no account yet
                var user by remember { mutableStateOf<User?>(null) }
                var editing by remember { mutableStateOf<Reminder?>(null) } // reminder open on the Edit screen
                var paywallFromProfile by remember { mutableStateOf(false) } // renewing: the paywall can be closed
                var loadError by remember { mutableStateOf<String?>(null) }
                var loadKey by remember { mutableIntStateOf(0) }

                // Home opens for a premium user or an old user (has reminders); a new user gets the paywall.
                fun home(u: User): Screen {
                    if (!u.canOpenHome) return Screen.Paywall
                    if (u.isPremium) clearPendingOrder(this@MainActivity) // nothing left to check
                    return Screen.Home
                }

                fun loggedIn(token: String, u: User) {
                    BloopApi.token = token
                    prefs.edit { putString("token", token) }
                    user = u
                    signupToken = null
                    paywallFromProfile = false
                    screen = home(u)
                }

                fun logout() {
                    BloopApi.token = null
                    prefs.edit { remove("token") }
                    clearPendingOrder(this@MainActivity)
                    user = null
                    signupToken = null
                    paywallFromProfile = false
                    screen = Screen.Phone
                }

                BackHandler(
                    enabled = screen !in setOf(Screen.Loading, Screen.Phone, Screen.Home) &&
                        !(screen == Screen.Paywall && !paywallFromProfile),
                ) {
                    screen = when (screen) {
                        Screen.Otp, Screen.Name -> Screen.Phone
                        Screen.Paywall -> Screen.Profile
                        else -> Screen.Home
                    }
                }

                Box(Modifier.fillMaxSize().background(BloopColors.Bg)) {
                    when (screen) {
                        Screen.Loading -> {
                            // Opening the app while logged in: the user's details say whether they are premium.
                            LaunchedEffect(loadKey) {
                                loadError = null
                                BloopApi.me()
                                    .onSuccess { user = it; screen = home(it) }
                                    .onFailure { if (it is NotLoggedIn) logout() else loadError = it.message }
                            }
                            val err = loadError
                            if (err == null) {
                                CenterMessage(title = "Loading…", body = null, spinner = true)
                            } else {
                                CenterMessage(title = "Couldn't connect", body = err, spinner = false) {
                                    PrimaryButton("Try again") { loadKey++ }
                                }
                            }
                        }
                        Screen.Paywall -> PaywallScreen(
                            canClose = paywallFromProfile,
                            onClose = { screen = Screen.Profile },
                            onPremium = { user = it; paywallFromProfile = false; screen = Screen.Home },
                            onLogout = ::logout,
                            onAuthExpired = ::logout,
                        )
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
                            onRenew = { paywallFromProfile = true; screen = Screen.Paywall },
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

package dev.suraj.bloop

import android.util.Patterns
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private fun validEmail(s: String) = Patterns.EMAIL_ADDRESS.matcher(s.trim()).matches()

@Composable
fun LoginScreen(onLoggedIn: (String, User) -> Unit, onSignup: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailErr by remember { mutableStateOf<String?>(null) }
    var pwErr by remember { mutableStateOf<String?>(null) }
    var serverErr by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        emailErr = if (validEmail(email)) null else "Enter a valid email address"
        pwErr = if (password.isNotEmpty()) null else "Enter your password"
        if (emailErr != null || pwErr != null) return
        busy = true
        serverErr = null
        scope.launch {
            BloopApi.login(email.trim(), password)
                .onSuccess { (token, user) -> onLoggedIn(token, user) }
                .onFailure { serverErr = it.message }
            busy = false
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        LogoMark()

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Welcome back", style = TitleStyle)
            Text("Log in to see who's celebrating today.", fontSize = 15.sp, color = BloopColors.Muted)
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BloopField("Email", email, { email = it }, emailErr, KeyboardType.Email)
            BloopField("Password", password, { password = it }, pwErr, KeyboardType.Password, isPassword = true)
        }

        Spacer(Modifier.weight(1f))
        ErrorBox(serverErr)
        PrimaryButton("Log in", busy) { submit() }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("New to Bloop?", fontSize = 14.sp, color = BloopColors.Muted)
            TextButton(onClick = onSignup) { Text("Create an account", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun SignupScreen(onSignedUp: (String, User) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var whatsappNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var agree by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf(mapOf<String, String>()) }
    var serverErr by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        val e = mutableMapOf<String, String>()
        if (name.isBlank()) e["name"] = "Tell us your name"
        if (!validEmail(email)) e["email"] = "Enter a valid email address"
        if (!Regex("^[6-9][0-9]{9}$").matches(whatsappNumber)) e["whatsappNumber"] = "Enter a 10-digit WhatsApp number"
        if (password.length < 8) e["password"] = "Use at least 8 characters"
        if (!agree) e["agree"] = "Please accept the terms to continue"
        errors = e
        if (e.isNotEmpty()) return
        busy = true
        serverErr = null
        scope.launch {
            BloopApi.signup(name.trim(), email.trim(), whatsappNumber, password)
                .onSuccess { (token, user) -> onSignedUp(token, user) }
                .onFailure { serverErr = it.message }
            busy = false
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        BackButton(onBack)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Create your account", style = TitleStyle)
            Text("Never miss a birthday again.", fontSize = 15.sp, color = BloopColors.Muted)
        }

        BloopField("Your name", name, { name = it }, errors["name"])
        BloopField("Email", email, { email = it }, errors["email"], KeyboardType.Email)
        BloopField(
            "WhatsApp number", whatsappNumber, { whatsappNumber = it.filter(Char::isDigit).take(10) }, errors["whatsappNumber"],
            KeyboardType.Phone, prefix = "+91", hint = "Reminders will arrive on this WhatsApp.",
        )
        BloopField("Password", password, { password = it }, errors["password"], KeyboardType.Password, isPassword = true)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = agree,
                onCheckedChange = { agree = it },
                colors = CheckboxDefaults.colors(checkedColor = BloopColors.Violet),
            )
            Text("I agree to the Terms and Privacy Policy", fontSize = 13.sp, color = BloopColors.Muted)
        }
        errors["agree"]?.let { Text(it, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BloopColors.CoralText) }

        Spacer(Modifier.height(4.dp))
        ErrorBox(serverErr)
        PrimaryButton("Create account", busy) { submit() }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Already have an account?", fontSize = 14.sp, color = BloopColors.Muted)
            TextButton(onClick = onBack) { Text("Log in", fontWeight = FontWeight.Bold) }
        }
    }
}

/** The Bloop app icon: violet tile with a yellow balloon (same drawing as the launcher icon). */
@Composable
fun LogoMark() {
    Image(painterResource(R.drawable.bloop_logo), contentDescription = null, modifier = Modifier.size(64.dp))
}

@Composable
fun BackButton(onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = BloopColors.Surface, contentColor = BloopColors.Ink),
        modifier = Modifier.size(44.dp),
    ) {
        Icon(BloopIcons.Back, contentDescription = "Back", modifier = Modifier.size(20.dp))
    }
}

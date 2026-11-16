package dev.suraj.bloop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val OTP_LENGTH = 6
private const val RESEND_AFTER_SECONDS = 30
private val indianMobile = Regex("^[6-9][0-9]{9}$")

/** Step 1: the user enters their WhatsApp number and an OTP is sent to it on WhatsApp. */
@Composable
fun PhoneScreen(initialNumber: String, onOtpSent: (String) -> Unit) {
    var number by remember { mutableStateOf(initialNumber) }
    var country by remember { mutableStateOf(defaultCountry("IN")) }
    var numberErr by remember { mutableStateOf<String?>(null) }
    var serverErr by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy) return
        numberErr = if (indianMobile.matches(number)) null else "Enter a valid 10-digit WhatsApp number"
        if (numberErr != null) return
        busy = true
        serverErr = null
        scope.launch {
            BloopApi.requestOtp(number)
                .onSuccess { onOtpSent(number) }
                .onFailure { serverErr = it.message }
            busy = false
        }
    }

    AuthColumn {
        LogoMark()

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Enter your WhatsApp number", style = TitleStyle)
            Text(
                "We will send an OTP on WhatsApp to verify this number.",
                fontSize = 15.sp, color = BloopColors.Muted,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("WhatsApp number", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BloopColors.Ink)
            // Only India (+91) for now: the country picker is shown but cannot be changed.
            PhoneTextField(
                phone = number,
                onPhoneChange = { number = it; numberErr = null },
                selectedCountry = country,
                onCountryChange = { country = it },
                placeholder = "98765 43210",
                maxLength = 10,
                codeSelectionEnabled = false,
                isError = numberErr != null,
                onDone = { submit() },
            )
            numberErr?.let { Text(it, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BloopColors.CoralText) }
        }

        Spacer(Modifier.weight(1f))
        ErrorBox(serverErr)
        PrimaryButton("Send OTP", busy) { submit() }
    }
}

/** Step 2: the user types the OTP from WhatsApp. */
@Composable
fun OtpScreen(
    whatsappNumber: String,
    onBack: () -> Unit,
    onLoggedIn: (String, User) -> Unit,
    onNewUser: (signupToken: String) -> Unit,
) {
    var otp by remember { mutableStateOf("") }
    var serverErr by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var resending by remember { mutableStateOf(false) }
    var resendIn by remember { mutableIntStateOf(RESEND_AFTER_SECONDS) }
    var resendKey by remember { mutableIntStateOf(0) } // bumped to restart the countdown
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(resendKey) {
        resendIn = RESEND_AFTER_SECONDS
        while (resendIn > 0) {
            delay(1_000)
            resendIn--
        }
    }

    fun verify() {
        if (busy || otp.length != OTP_LENGTH) return
        busy = true
        serverErr = null
        info = null
        scope.launch {
            BloopApi.verifyOtp(whatsappNumber, otp)
                .onSuccess { result ->
                    when (result) {
                        is OtpResult.LoggedIn -> onLoggedIn(result.token, result.user)
                        is OtpResult.NewUser -> onNewUser(result.signupToken)
                    }
                }
                .onFailure { serverErr = it.message; otp = "" }
            busy = false
        }
    }

    fun resend() {
        if (resending || resendIn > 0) return
        resending = true
        serverErr = null
        info = null
        scope.launch {
            BloopApi.requestOtp(whatsappNumber)
                .onSuccess { otp = ""; info = "A new OTP has been sent on WhatsApp."; resendKey++ }
                .onFailure { serverErr = it.message }
            resending = false
        }
    }

    AuthColumn {
        BackButton(onBack)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Enter the OTP", style = TitleStyle)
            Text(
                "We sent a $OTP_LENGTH-digit code on WhatsApp to +91 ${whatsappNumber.chunked(5).joinToString(" ")}.",
                fontSize = 15.sp, color = BloopColors.Muted,
            )
            TextButton(onClick = onBack, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("Change number", fontWeight = FontWeight.Bold)
            }
        }

        BasicTextField(
            value = otp,
            onValueChange = { new ->
                otp = new.filter(Char::isDigit).take(OTP_LENGTH)
                serverErr = null
                if (otp.length == OTP_LENGTH) verify()
            },
            enabled = !busy,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { verify() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "OTP" },
            decorationBox = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    repeat(OTP_LENGTH) { i ->
                        val active = i == otp.length && !busy
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.85f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(BloopColors.Surface)
                                .border(
                                    width = if (active) 2.dp else 1.dp,
                                    color = when {
                                        serverErr != null -> BloopColors.Coral
                                        active -> BloopColors.Violet
                                        else -> BloopColors.Border
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                ),
                        ) {
                            Text(
                                otp.getOrNull(i)?.toString().orEmpty(),
                                fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = BloopColors.Ink,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            },
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Didn't get it?", fontSize = 14.sp, color = BloopColors.Muted)
            TextButton(onClick = ::resend, enabled = resendIn == 0 && !resending) {
                Text(if (resendIn > 0) "Resend in ${resendIn}s" else "Resend OTP", fontWeight = FontWeight.Bold)
            }
        }
        info?.let { Text(it, fontSize = 13.sp, color = BloopColors.MintText, fontWeight = FontWeight.SemiBold) }

        Spacer(Modifier.weight(1f))
        ErrorBox(serverErr)
        PrimaryButton("Verify", busy) { verify() }
    }
}

/** Step 3 (new numbers only): ask the user's name, then create the account. */
@Composable
fun NameScreen(signupToken: String, onSignedUp: (String, User) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var nameErr by remember { mutableStateOf<String?>(null) }
    var serverErr by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy) return
        nameErr = if (name.isNotBlank()) null else "Tell us your name"
        if (nameErr != null) return
        busy = true
        serverErr = null
        scope.launch {
            BloopApi.completeSignup(signupToken, name.trim())
                .onSuccess { (token, user) -> onSignedUp(token, user) }
                .onFailure { serverErr = it.message }
            busy = false
        }
    }

    AuthColumn {
        BackButton(onBack)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("What's your name?", style = TitleStyle)
            Text("Your number is verified. Tell us what to call you.", fontSize = 15.sp, color = BloopColors.Muted)
        }

        BloopField("Your name", name, { name = it.take(60); nameErr = null }, nameErr)

        Spacer(Modifier.weight(1f))
        ErrorBox(serverErr)
        PrimaryButton("Continue", busy) { submit() }
    }
}

@Composable
private fun AuthColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        content = content,
    )
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

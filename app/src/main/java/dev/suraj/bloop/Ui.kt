package dev.suraj.bloop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Bloop colours (same as the website and design canvas)
object BloopColors {
    val Bg = Color(0xFFF5F3FF)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF17132B)
    val Muted = Color(0xFF5E5875)
    val Line = Color(0xFFEEEBF7)
    val Border = Color(0xFFDCD6F2)
    val Violet = Color(0xFF5B3DF5)
    val VioletDeep = Color(0xFF3E24C9)
    val VioletSoft = Color(0xFFE9E4FF)
    val VioletText = Color(0xFFE4DEFF)
    val Lemon = Color(0xFFFFD84D)
    val LemonSoft = Color(0xFFFFF1C2)
    val LemonText = Color(0xFF6B4E00)
    val Coral = Color(0xFFFF6B57)
    val CoralAvatar = Color(0xFFFF8A7A)
    val CoralText = Color(0xFFA3271A)
    val Mint = Color(0xFF6FE3C1)
    val MintSoft = Color(0xFFD6F7EC)
    val MintText = Color(0xFF0E6B50)
    val Lilac = Color(0xFFC9BEFF)
    val Grey = Color(0xFFF1EFF7)
    val GreyText = Color(0xFF4A4560)
}

@Composable
fun BloopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = BloopColors.Violet,
            onPrimary = Color.White,
            background = BloopColors.Bg,
            surface = BloopColors.Surface,
            onSurface = BloopColors.Ink,
            error = BloopColors.Coral,
        ),
        content = content,
    )
}

val TitleStyle = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, color = BloopColors.Ink)

/** Labelled text field in the Bloop style, with an optional error line under it. */
@Composable
fun BloopField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    prefix: String? = null,
    hint: String? = null,
    isPassword: Boolean = false,
) {
    var show by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BloopColors.Ink)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            isError = error != null,
            prefix = prefix?.let { { Text("$it  ", fontWeight = FontWeight.Bold) } },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (isPassword && !show) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = if (isPassword) {
                { TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") } }
            } else null,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = BloopColors.Surface,
                unfocusedContainerColor = BloopColors.Surface,
                errorContainerColor = BloopColors.Surface,
                unfocusedBorderColor = BloopColors.Border,
                focusedBorderColor = BloopColors.Violet,
                errorBorderColor = BloopColors.Coral,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        when {
            error != null -> Text(error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BloopColors.CoralText)
            hint != null -> Text(hint, fontSize = 12.sp, color = BloopColors.Muted)
        }
    }
}

/** Big violet pill button that shows a spinner while busy. */
@Composable
fun PrimaryButton(text: String, busy: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BloopColors.Violet, disabledContainerColor = BloopColors.Violet.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        else Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** Red message box for errors from the backend. */
@Composable
fun ErrorBox(message: String?) {
    if (message == null) return
    Text(
        message,
        color = BloopColors.CoralText,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFFE1DC))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** Round initials avatar. */
@Composable
fun Avatar(name: String, color: Color, size: Int = 40) {
    val initials = name.split(" ").filter { it.firstOrNull()?.isLetter() == true }
        .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    androidx.compose.foundation.layout.Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size.dp).clip(CircleShape).background(color),
    ) {
        Text(initials, fontWeight = FontWeight.Bold, fontSize = (size / 3).sp, color = BloopColors.Ink)
    }
}

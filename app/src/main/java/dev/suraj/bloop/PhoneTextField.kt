package dev.suraj.bloop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

/** One country for the dial-code picker, e.g. Country("IN", "India", "🇮🇳", "+91"). */
data class Country(
    val region: String,
    val name: String,
    val flag: String,
    val dialCode: String,
)

private fun String.toFlagEmoji(): String {
    val first = Character.codePointAt(this, 0) - 'A'.code + 0x1F1E6
    val second = Character.codePointAt(this, 1) - 'A'.code + 0x1F1E6
    return String(Character.toChars(first)) + String(Character.toChars(second))
}

/** Every region libphonenumber knows, sorted by name. */
val allCountries: List<Country> by lazy {
    val util = PhoneNumberUtil.getInstance()
    util.supportedRegions
        .map { region ->
            Country(
                region = region,
                name = Locale.Builder().setRegion(region).build().displayCountry,
                flag = region.toFlagEmoji(),
                dialCode = "+${util.getCountryCodeForRegion(region)}",
            )
        }
        .filter { it.name.isNotBlank() && it.region.length == 2 }
        .sortedBy { it.name }
}

fun defaultCountry(region: String = "IN"): Country =
    allCountries.firstOrNull { it.region == region } ?: allCountries.first()

/**
 * Phone number field with a country dial-code picker, in the Bloop style.
 * With [codeSelectionEnabled] false the picker only shows [selectedCountry] and cannot be opened.
 */
@Composable
fun PhoneTextField(
    phone: String,
    onPhoneChange: (String) -> Unit,
    selectedCountry: Country,
    onCountryChange: (Country) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Enter mobile number",
    maxLength: Int = 15,
    codeSelectionEnabled: Boolean = true,
    isError: Boolean = false,
    onDone: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(BloopColors.Surface, shape)
            .border(if (isError) 2.dp else 1.dp, if (isError) BloopColors.Coral else BloopColors.Border, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Row(
                modifier = Modifier.clickable(enabled = codeSelectionEnabled) { expanded = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = selectedCountry.flag, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = selectedCountry.dialCode,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = BloopColors.Ink,
                )
                if (codeSelectionEnabled) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = BloopIcons.Chevron,
                        contentDescription = "Select country",
                        tint = BloopColors.Muted,
                        modifier = Modifier.size(14.dp).rotate(90f),
                    )
                }
            }

            DropdownMenu(
                expanded = expanded && codeSelectionEnabled,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .width(260.dp)
                    .heightIn(max = 320.dp)
                    .background(BloopColors.Surface),
            ) {
                allCountries.forEach { country ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = country.flag, fontSize = 20.sp)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = country.name,
                                    color = BloopColors.Ink,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(text = country.dialCode, color = BloopColors.Muted)
                            }
                        },
                        onClick = {
                            onCountryChange(country)
                            expanded = false
                        },
                    )
                }
            }
        }

        // divider
        Box(
            Modifier
                .padding(horizontal = 12.dp)
                .width(1.dp)
                .height(32.dp)
                .background(BloopColors.Border),
        )

        // phone input
        BasicTextField(
            value = phone,
            onValueChange = { input ->
                if (input.length <= maxLength && input.all { it.isDigit() }) {
                    onPhoneChange(input)
                }
            },
            modifier = Modifier.weight(1f),
            textStyle = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = BloopColors.Ink,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            singleLine = true,
            cursorBrush = SolidColor(BloopColors.Violet),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (phone.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 16.sp,
                            color = BloopColors.Muted.copy(alpha = 0.6f),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

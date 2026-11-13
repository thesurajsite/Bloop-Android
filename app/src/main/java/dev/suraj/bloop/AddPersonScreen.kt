package dev.suraj.bloop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val EVENT_TYPES = listOf("Birthday", "Anniversary")

/** DatePicker gives UTC millis; turn them into "yyyy-MM-dd". */
private fun isoFromMillis(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPersonScreen(user: User?, onSaved: () -> Unit, onBack: () -> Unit, onAuthExpired: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var eventType by remember { mutableStateOf(EVENT_TYPES.first()) }
    var date by remember { mutableStateOf("") } // yyyy-MM-dd
    var typeOpen by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var nameErr by remember { mutableStateOf<String?>(null) }
    var dateErr by remember { mutableStateOf<String?>(null) }
    var serverErr by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun save() {
        nameErr = if (name.isBlank()) "Enter the person's name" else null
        dateErr = if (date.isEmpty()) "Pick the event date" else null
        if (nameErr != null || dateErr != null) return
        busy = true
        serverErr = null
        scope.launch {
            BloopApi.addPerson(name.trim(), eventType, date)
                .onSuccess { onSaved() }
                .onFailure { if (it is NotLoggedIn) onAuthExpired() else serverErr = it.message }
            busy = false
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Text("Add person", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f).padding(start = 16.dp))
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(80.dp).clip(CircleShape).background(BloopColors.Lemon),
            ) {
                Text(name.trim().firstOrNull()?.uppercase() ?: "?", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        BloopField("Person's name", name, { name = it }, nameErr)

        // Event type dropdown
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Event type", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            ExposedDropdownMenuBox(expanded = typeOpen, onExpandedChange = { typeOpen = it }) {
                OutlinedTextField(
                    value = eventType,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeOpen) },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BloopColors.Surface,
                        unfocusedContainerColor = BloopColors.Surface,
                        unfocusedBorderColor = BloopColors.Border,
                        focusedBorderColor = BloopColors.Violet,
                    ),
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = typeOpen, onDismissRequest = { typeOpen = false }) {
                    EVENT_TYPES.forEach { t ->
                        DropdownMenuItem(text = { Text(t) }, onClick = { eventType = t; typeOpen = false })
                    }
                }
            }
        }

        // Event date: tap to open a calendar
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Event date", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (date.isEmpty()) "Pick a date" else prettyDate(date).substringAfter(", ") + " " + date.take(4),
                fontSize = 15.sp,
                color = if (date.isEmpty()) BloopColors.Muted else BloopColors.Ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(BloopColors.Surface)
                    .border(2.dp, if (dateErr != null) BloopColors.Coral else BloopColors.Border, RoundedCornerShape(16.dp))
                    .clickable(onClickLabel = "Pick event date") { pickDate = true }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
            dateErr?.let { Text(it, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BloopColors.CoralText) }
        }

        // What the WhatsApp reminder will say
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(BloopColors.Surface).padding(16.dp),
        ) {
            Text("WhatsApp reminder", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BloopColors.MintText)
            Text(
                buildAnnotatedString {
                    append("Hey ${user?.name ?: "there"}! It's ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(name.trim().ifEmpty { "Person" }) }
                    append("'s ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(eventType) }
                    append(" today. Give them your kind wishes!")
                },
                fontSize = 14.sp,
            )
        }

        Spacer(Modifier.height(4.dp))
        ErrorBox(serverErr)
        PrimaryButton("Save person", busy) { save() }
    }

    if (pickDate) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = isoFromMillis(it); dateErr = null }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}

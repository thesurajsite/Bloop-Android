package dev.suraj.bloop

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val DeleteRed = Color(0xFFB42318)
private val DeleteBorder = Color(0xFFF3C7C1)

/** "2026-11-14" -> "14 Nov" */
private fun dayMonth(iso: String) = prettyDate(iso).substringAfter(", ")

/**
 * Edit one reminder: change name, event type and date, or delete it.
 * Deleting asks for confirmation first, then goes back via [onDeleted].
 */
@Composable
fun EditReminderScreen(
    user: User?,
    reminder: Reminder,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onAuthExpired: () -> Unit,
) {
    var name by remember { mutableStateOf(reminder.name) }
    var eventType by remember { mutableStateOf(reminder.eventType) }
    var date by remember { mutableStateOf(reminder.eventDate) } // yyyy-MM-dd
    var pickDate by remember { mutableStateOf(false) }
    var nameErr by remember { mutableStateOf<String?>(null) }
    var dateErr by remember { mutableStateOf<String?>(null) }
    var serverErr by remember { mutableStateOf<String?>(null) }
    var current by remember { mutableStateOf(reminder) } // what the backend has now; edits are compared to it
    var saved by remember { mutableStateOf<Reminder?>(null) } // set after a save, for the "Updated" note
    var saving by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun save() {
        nameErr = if (name.isBlank()) "Enter the person's name" else null
        dateErr = if (date.isEmpty()) "Pick the event date" else null
        if (nameErr != null || dateErr != null) return
        // Send only what changed; the backend answers "nothing to update" to an empty edit.
        val base = current
        val newName = name.trim().takeIf { it != base.name }
        val newType = eventType.takeIf { it != base.eventType }
        val newDate = date.takeIf { it != base.eventDate }
        serverErr = null
        if (newName == null && newType == null && newDate == null) {
            name = name.trim()
            serverErr = "Nothing to update. Change a detail first."
            return
        }
        saving = true
        saved = null
        scope.launch {
            BloopApi.editReminder(reminder.id, newName, newType, newDate)
                .onSuccess { current = it; saved = it; name = it.name; eventType = it.eventType; date = it.eventDate }
                .onFailure { if (it is NotLoggedIn) onAuthExpired() else serverErr = it.message }
            saving = false
        }
    }

    fun delete() {
        deleting = true
        serverErr = null
        scope.launch {
            BloopApi.deleteReminder(reminder.id)
                .onSuccess { onDeleted() }
                .onFailure {
                    when {
                        it is NotLoggedIn -> onAuthExpired()
                        it is ApiError && it.status == 404 -> onDeleted() // already gone
                        else -> { confirmingDelete = false; serverErr = it.message }
                    }
                }
            deleting = false
        }
    }

    // Back while the delete confirmation is open just closes it.
    BackHandler(enabled = confirmingDelete && !deleting) { confirmingDelete = false }

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
            Text("Edit reminder", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f).padding(start = 16.dp))
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(BloopColors.Lemon)
                    .border(4.dp, BloopColors.Surface, CircleShape),
            ) {
                Text(name.trim().firstOrNull()?.uppercase() ?: "?", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        BloopField("Person's name", name, { name = it; saved = null }, nameErr)
        EventTypeField(eventType) { eventType = it; saved = null }
        EventDateField(date, dateErr) { pickDate = true }
        WhatsAppPreview(
            user, name, eventType,
            title = if (date.isEmpty()) "WhatsApp reminder" else "WhatsApp reminder · every ${dayMonth(date)}",
        )

        Spacer(Modifier.height(4.dp))
        ErrorBox(serverErr)

        if (confirmingDelete) {
            DeleteConfirmCard(
                label = "${name.trim().ifEmpty { reminder.name }}'s $eventType",
                busy = deleting,
                onCancel = { confirmingDelete = false },
                onDelete = { delete() },
            )
        } else {
            saved?.let { r ->
                Text(
                    "Updated ${r.name}'s ${r.eventType} on ${dayMonth(r.eventDate)}.",
                    color = BloopColors.MintText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .clip(RoundedCornerShape(14.dp))
                        .background(BloopColors.MintSoft)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            PrimaryButton("Save changes", saving) { save() }
            OutlinedButton(
                onClick = { saved = null; confirmingDelete = true },
                enabled = !saving,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(2.dp, DeleteBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BloopColors.CoralText),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(BloopIcons.Trash, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Delete reminder", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (pickDate) {
        ReminderDatePicker(date, onPicked = { date = it; dateErr = null; saved = null }, onDismiss = { pickDate = false })
    }
}

/** Inline "are you sure?" card shown in place of the buttons before a delete. */
@Composable
private fun DeleteConfirmCard(label: String, busy: Boolean, onCancel: () -> Unit, onDelete: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive }
            .clip(RoundedCornerShape(20.dp))
            .background(BloopColors.Surface)
            .border(2.dp, DeleteBorder, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Delete this reminder?", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "You'll stop getting WhatsApp reminders for $label. This can't be undone.",
                fontSize = 14.sp,
                color = BloopColors.Muted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onCancel,
                enabled = !busy,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BloopColors.Grey, contentColor = BloopColors.Ink),
                modifier = Modifier.weight(1f).height(48.dp),
            ) { Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            Button(
                onClick = onDelete,
                enabled = !busy,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeleteRed,
                    contentColor = Color.White,
                    disabledContainerColor = DeleteRed.copy(alpha = 0.6f),
                    disabledContentColor = Color.White,
                ),
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                else Text("Delete", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

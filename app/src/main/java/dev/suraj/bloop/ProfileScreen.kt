package dev.suraj.bloop

import android.content.Context
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import java.util.Locale

// Reminder settings. For now they are kept on the phone: the backend still sends
// every reminder at its own fixed REMINDER_HOUR and has no per-user time yet.
private const val KEY_REMINDER_MINUTES = "reminder_minutes" // minutes after midnight, 0..1439
private const val KEY_REMINDERS_ON = "reminders_on"
private const val DEFAULT_REMINDER_MINUTES = 20 * 60 // 08:00 PM

/** A time on a 12-hour clock: hour 1..12, minute 0..59. */
private data class ClockTime(val hour: Int, val minute: Int, val pm: Boolean) {
    val label: String get() = String.format(Locale.US, "%02d:%02d %s", hour, minute, if (pm) "PM" else "AM")
    val minutesOfDay: Int get() = ((hour % 12) + if (pm) 12 else 0) * 60 + minute

    companion object {
        fun of(minutesOfDay: Int): ClockTime {
            val h24 = minutesOfDay / 60
            val h12 = if (h24 % 12 == 0) 12 else h24 % 12
            return ClockTime(h12, minutesOfDay % 60, h24 >= 12)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(user: User?, onBack: () -> Unit, onLogout: () -> Unit) {
    val prefs = LocalContext.current.getSharedPreferences("bloop", Context.MODE_PRIVATE)
    var reminderMinutes by remember { mutableIntStateOf(prefs.getInt(KEY_REMINDER_MINUTES, DEFAULT_REMINDER_MINUTES)) }
    var remindersOn by remember { mutableStateOf(prefs.getBoolean(KEY_REMINDERS_ON, true)) }
    var picking by remember { mutableStateOf(false) }

    val timeLabel = ClockTime.of(reminderMinutes).label

    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Text(
                "My profile", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(44.dp))
        }

        // Who is logged in
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(BloopColors.Violet).padding(20.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.White).padding(3.dp)
                    .clip(CircleShape).background(BloopColors.Lemon),
            ) {
                Text(user?.name?.firstOrNull()?.uppercase() ?: "?", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = BloopColors.Ink)
            }
            Column(Modifier.weight(1f)) {
                Text(user?.name ?: "…", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(user?.email.orEmpty(), fontSize = 13.sp, color = BloopColors.VioletText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // Reminders
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Reminders", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(BloopColors.Surface).padding(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(BloopColors.SurfaceTint)
                        .border(2.dp, BloopColors.Border, RoundedCornerShape(18.dp))
                        .clickable(onClickLabel = "Change reminder time") { picking = true }
                        .padding(14.dp),
                ) {
                    IconTile(BloopIcons.Clock, BloopColors.Lilac, BloopColors.Ink, size = 44, corner = 14, iconSize = 20)
                    Column(Modifier.weight(1f)) {
                        Text("Reminder time", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BloopColors.Muted)
                        Text(timeLabel, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Change", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BloopColors.Violet)
                        Icon(BloopIcons.Chevron, contentDescription = null, tint = BloopColors.Violet, modifier = Modifier.size(14.dp))
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    IconTile(BloopIcons.WhatsApp, BloopColors.MintSoft, BloopColors.MintText)
                    Text("WhatsApp reminders", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Switch(
                        checked = remindersOn,
                        onCheckedChange = {
                            remindersOn = it
                            prefs.edit { putBoolean(KEY_REMINDERS_ON, it) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = BloopColors.Violet,
                            uncheckedTrackColor = BloopColors.Grey,
                            uncheckedBorderColor = BloopColors.Border,
                        ),
                    )
                }
            }
            Text(
                if (remindersOn) "Bloop will WhatsApp you at $timeLabel on the day of every birthday and anniversary."
                else "WhatsApp reminders are paused. Turn them on to get a message at $timeLabel.",
                fontSize = 13.sp, color = BloopColors.Muted, lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        // Account
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Account", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(BloopColors.Surface).padding(horizontal = 14.dp, vertical = 4.dp),
            ) {
                AccountRow(BloopIcons.Phone, "WhatsApp number", user?.phone?.let { "+91 $it" } ?: "…")
                HorizontalDivider(color = BloopColors.Line)
                AccountRow(BloopIcons.Mail, "Email", user?.email ?: "…")
            }
        }

        Button(
            onClick = onLogout,
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BloopColors.Red, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(BloopIcons.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Log out", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }

    if (picking) {
        ReminderTimeSheet(
            initial = ClockTime.of(reminderMinutes),
            onDismiss = { picking = false },
            onSet = { t ->
                reminderMinutes = t.minutesOfDay
                prefs.edit { putInt(KEY_REMINDER_MINUTES, t.minutesOfDay) }
                picking = false
            },
        )
    }
}

/** Bottom sheet with an hour grid, a minute grid and an AM/PM toggle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeSheet(initial: ClockTime, onDismiss: () -> Unit, onSet: (ClockTime) -> Unit) {
    var draft by remember { mutableStateOf(initial.copy(minute = initial.minute - initial.minute % 5)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BloopColors.Surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Reminder time", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("When should Bloop message you?", fontSize = 13.sp, color = BloopColors.Muted)
                }
                Text(draft.label, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = BloopColors.Violet, letterSpacing = (-0.5).sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PickerGrid("HOUR", (1..12).toList(), draft.hour, Modifier.weight(1f)) { draft = draft.copy(hour = it) }
                PickerGrid("MINUTE", (0..55 step 5).toList(), draft.minute, Modifier.weight(1f)) { draft = draft.copy(minute = it) }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BloopColors.Grey).padding(4.dp),
            ) {
                listOf(false to "AM", true to "PM").forEach { (pm, text) ->
                    val on = draft.pm == pm
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .then(if (on) Modifier.shadow(2.dp, RoundedCornerShape(12.dp)) else Modifier)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) BloopColors.Surface else Color.Transparent)
                            .selectable(selected = on, role = Role.RadioButton) { draft = draft.copy(pm = pm) },
                    ) {
                        Text(text, fontSize = 15.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (on) BloopColors.Ink else BloopColors.GreyText)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(27.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, BloopColors.Border),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BloopColors.Ink),
                    modifier = Modifier.weight(1f).height(54.dp),
                ) { Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = { onSet(draft) },
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BloopColors.Violet),
                    modifier = Modifier.weight(1f).height(54.dp),
                ) { Text("Set time", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** A labelled 3-column grid of number buttons; the selected one is filled violet. */
@Composable
private fun PickerGrid(title: String, values: List<Int>, selected: Int, modifier: Modifier, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = BloopColors.Muted)
        values.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { v ->
                    val on = v == selected
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) BloopColors.Violet else BloopColors.Surface)
                            .then(if (on) Modifier else Modifier.border(2.dp, BloopColors.Line, RoundedCornerShape(12.dp)))
                            .selectable(selected = on, role = Role.RadioButton) { onPick(v) },
                    ) {
                        Text(String.format(Locale.US, "%02d", v), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = if (on) Color.White else BloopColors.Ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountRow(icon: ImageVector, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
    ) {
        IconTile(icon, BloopColors.Grey, BloopColors.GreyText)
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, color = BloopColors.Muted)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector, bg: Color, tint: Color, size: Int = 32, corner: Int = 10, iconSize: Int = 16) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape(corner.dp)).background(bg),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize.dp))
    }
}

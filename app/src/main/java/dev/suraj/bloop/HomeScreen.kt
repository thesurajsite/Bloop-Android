package dev.suraj.bloop

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val avatarColors = listOf(BloopColors.Lemon, BloopColors.Mint, BloopColors.Lilac, BloopColors.CoralAvatar)

/** "2026-11-14" -> "Sat, 14 Nov" */
fun prettyDate(iso: String): String = runCatching {
    val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)!!
    SimpleDateFormat("EEE, d MMM", Locale.US).format(d)
}.getOrDefault(iso)

fun countdown(days: Int) = when (days) {
    0 -> "Today"
    1 -> "Tomorrow"
    else -> "$days days"
}

@Composable
fun HomeScreen(
    user: User?,
    onAddReminder: () -> Unit,
    onEditReminder: (Reminder) -> Unit,
    onProfile: () -> Unit,
    onAuthExpired: () -> Unit,
) {
    var reminders by remember { mutableStateOf<List<Reminder>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var bellError by remember { mutableStateOf<String?>(null) }
    var bellBusy by remember { mutableStateOf(emptySet<String>()) } // reminder ids being saved
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            BloopApi.reminders()
                .onSuccess { reminders = it; error = null }
                .onFailure { if (it is NotLoggedIn) onAuthExpired() else error = it.message }
        }
    }

    fun setBell(id: String, on: Boolean) {
        reminders = reminders?.map { if (it.id == id) it.copy(whatsappRemEnable = on) else it }
    }

    // Bell tap: flip this one reminder at once, save it on the backend, flip back if that fails.
    fun toggleBell(r: Reminder) {
        if (r.id in bellBusy) return
        val on = !r.whatsappRemEnable
        setBell(r.id, on)
        bellError = null
        bellBusy = bellBusy + r.id
        scope.launch {
            BloopApi.toggleWhatsAppReminder(on, reminderId = r.id)
                .onSuccess { setBell(r.id, it.enabled) }
                .onFailure {
                    setBell(r.id, !on)
                    if (it is NotLoggedIn) onAuthExpired() else bellError = it.message
                }
            bellBusy = bellBusy - r.id
        }
    }
    LaunchedEffect(Unit) { load() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(SimpleDateFormat("EEEE, d MMMM", Locale.US).format(Date()), fontSize = 13.sp, color = BloopColors.Muted)
                        Text("Hi ${user?.name?.substringBefore(' ') ?: "there"}", style = TitleStyle.copy(fontSize = 30.sp))
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(BloopColors.Lemon)
                            .combinedClickable(onClickLabel = "My profile", onClick = onProfile),
                    ) {
                        Text(user?.name?.firstOrNull()?.uppercase() ?: "?", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                }
            }

            val list = reminders
            when {
                error != null -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ErrorBox(error)
                        TextButton(onClick = { error = null; load() }) { Text("Try again") }
                    }
                }
                list == null -> item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                list.isEmpty() -> item { EmptyState() }
                else -> {
                    if (bellError != null) item { ErrorBox(bellError) }
                    item { NextUpCard(list.first(), onClick = { onEditReminder(list.first()) }, onBell = { toggleBell(list.first()) }) }
                    if (list.size > 1) {
                        item {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("Upcoming", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("${list.size} events", fontSize = 13.sp, color = BloopColors.Muted)
                            }
                        }
                        item {
                            Column(
                                Modifier.clip(RoundedCornerShape(22.dp)).background(BloopColors.Surface).padding(horizontal = 14.dp, vertical = 4.dp),
                            ) {
                                list.drop(1).forEachIndexed { i, g ->
                                    ReminderRow(
                                        g, avatarColors[(i + 1) % avatarColors.size],
                                        onClick = { onEditReminder(g) }, onBell = { toggleBell(g) },
                                    )
                                    if (i < list.size - 2) HorizontalDivider(color = BloopColors.Line)
                                }
                            }
                        }
                        item { Text("Tap a person to edit or delete their reminder.", fontSize = 12.sp, color = BloopColors.Muted) }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAddReminder,
            containerColor = BloopColors.Ink,
            contentColor = Color.White,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
        ) {
            Text("+  Add person", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun NextUpCard(g: Reminder, onClick: () -> Unit, onBell: () -> Unit) {
    val tag = when (g.daysLeft) {
        0 -> "TODAY"
        1 -> "TOMORROW"
        else -> "NEXT UP · IN ${g.daysLeft} DAYS"
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(BloopColors.Violet)
            .combinedClickable(onClickLabel = "Edit reminder", onClick = onClick).padding(18.dp),
    ) {
        Text(
            tag, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = BloopColors.Ink,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(BloopColors.Lemon).padding(horizontal = 10.dp, vertical = 5.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(g.name, BloopColors.CoralAvatar, size = 52)
            Column(Modifier.weight(1f)) {
                Text(g.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("${g.eventType} · ${prettyDate(g.nextDate)}", fontSize = 13.sp, color = BloopColors.VioletText)
            }
            BellButton(
                on = g.whatsappRemEnable, onClick = onBell,
                onColors = BloopColors.Lemon to BloopColors.Ink,
                offColors = Color.White.copy(alpha = 0.18f) to BloopColors.VioletText,
            )
        }
    }
}

/** Round bell button: on = WhatsApp reminder will be sent, off (crossed out) = it won't. */
@Composable
private fun BellButton(
    on: Boolean,
    onClick: () -> Unit,
    onColors: Pair<Color, Color> = BloopColors.LemonSoft to BloopColors.LemonText,
    offColors: Pair<Color, Color> = BloopColors.Grey to BloopColors.GreyText,
) {
    val (bg, tint) = if (on) onColors else offColors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bg)
            .toggleable(value = on, role = Role.Switch, onValueChange = { onClick() }),
    ) {
        Icon(
            if (on) BloopIcons.Bell else BloopIcons.BellOff,
            contentDescription = if (on) "WhatsApp reminder on" else "WhatsApp reminder off",
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ReminderRow(g: Reminder, color: Color, onClick: () -> Unit, onBell: () -> Unit) {
    val soon = g.daysLeft <= 7
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onClickLabel = "Edit reminder")
            .padding(vertical = 14.dp),
    ) {
        Avatar(g.name, color)
        Column(Modifier.weight(1f)) {
            Text(g.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("${g.eventType} · ${prettyDate(g.nextDate)}", fontSize = 13.sp, color = BloopColors.Muted)
        }
        Text(
            countdown(g.daysLeft),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (soon) BloopColors.LemonText else BloopColors.GreyText,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (soon) BloopColors.LemonSoft else BloopColors.Grey)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
        BellButton(on = g.whatsappRemEnable, onClick = onBell)
    }
}

@Composable
private fun EmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(BloopColors.VioletSoft).padding(28.dp),
    ) {
        Text("No events yet", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text("Add a person and Bloop will remind you on WhatsApp on the day.", fontSize = 14.sp, color = BloopColors.Muted)
    }
}

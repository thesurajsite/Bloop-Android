package dev.suraj.bloop

import android.app.Activity
import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.razorpay.Checkout
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

// Payment flow:
//   paywall (plans from GET /api/plans) -> POST /api/payments/orders -> Razorpay checkout
//   -> poll GET /api/payments/orders/{id} every 3 s, up to 25 times
//   -> not paid? fall back to GET /api/me, which checks Razorpay too
//   -> Home only when the backend says the user is premium.
// A user who already has reminders (an old user) never gets stuck here: see User.canOpenHome.
// The backend decides whether the payment went through; the checkout result alone is not trusted.

private const val POLL_EVERY_MS = 3_000L
private const val POLL_TIMES = 25
// An order left over from an earlier session (app killed mid-payment) is checked only briefly:
// usually it was never paid, and GET /api/me keeps checking it on the backend anyway.
private const val RESTORED_POLL_TIMES = 3

/** What the Razorpay checkout reported. Razorpay calls back on the Activity, which sends it here. */
sealed interface CheckoutResult {
    data class Success(val paymentId: String?) : CheckoutResult
    data class Error(val code: Int, val description: String?) : CheckoutResult
}

object RazorpayResults {
    val channel = Channel<CheckoutResult>(Channel.CONFLATED)
}

/**
 * The order being paid is saved on the phone before the checkout opens, so if the app is
 * killed mid-payment the paywall checks that order the next time it opens.
 */
private const val PENDING_ORDER_KEY = "pending_order_id"

private fun prefs(context: Context) = context.getSharedPreferences("bloop", Context.MODE_PRIVATE)
private fun pendingOrder(context: Context) = prefs(context).getString(PENDING_ORDER_KEY, null)
private fun savePendingOrder(context: Context, id: String) = prefs(context).edit { putString(PENDING_ORDER_KEY, id) }
fun clearPendingOrder(context: Context) = prefs(context).edit { remove(PENDING_ORDER_KEY) }

/** "₹99" for 9900 paise. */
fun formatPrice(amount: Long, currency: String): String {
    val symbol = if (currency == "INR") "₹" else "$currency "
    return if (amount % 100 == 0L) "$symbol${amount / 100}"
    else symbol + String.format(Locale.US, "%.2f", amount / 100.0)
}

/** "19 Nov 2027" for the backend's premium_until, or null if it can't be read. */
fun formatPremiumDate(iso: String?): String? = iso?.let {
    runCatching {
        val d = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(it)!!
        SimpleDateFormat("d MMM yyyy", Locale.US).format(d)
    }.getOrNull()
}

/**
 * The paywall. Shown after OTP login (and on app start) to a new user who has not paid, and from
 * Profile when the plan has run out. It ends by [onPremium] once the backend confirms the payment
 * (or, after login, finds the user has reminders already), or by [onClose] when [canClose]
 * (opened from Profile).
 */
@Composable
fun PaywallScreen(
    canClose: Boolean,
    onClose: () -> Unit,
    onPremium: (User) -> Unit,
    onLogout: () -> Unit,
    onAuthExpired: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()

    var paywall by remember { mutableStateOf<Paywall?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loadKey by remember { mutableIntStateOf(0) } // bumped by Retry
    var selectedId by remember { mutableStateOf<String?>(null) }
    var buying by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var paidUser by remember { mutableStateOf<User?>(null) }

    var latestUser by remember { mutableStateOf<User?>(null) }

    /** Asks the backend (user details API) whether the user is premium now. */
    suspend fun premiumUser(): User? {
        val me = BloopApi.me()
        if (me.exceptionOrNull() is NotLoggedIn) onAuthExpired()
        latestUser = me.getOrNull() ?: latestUser
        return me.getOrNull()?.takeIf { it.isPremium }
    }

    /**
     * Confirms an order with the backend. After a successful checkout it polls the order every
     * 3 s; after a failed or cancelled one it does not wait. Either way, if the order is not
     * confirmed paid, it falls back to the user details API before giving up.
     */
    suspend fun confirm(orderId: String, poll: Boolean, failMessage: String, times: Int = POLL_TIMES) {
        checking = true
        message = null
        var user: User? = null
        if (poll) {
            for (i in 0 until times) {
                val r = BloopApi.checkOrder(orderId)
                if (r.exceptionOrNull() is NotLoggedIn) { onAuthExpired(); return }
                val check = r.getOrNull() // a network error just means: ask again
                if (check?.status == PaymentStatus.Paid) {
                    // Not premium although paid: an old order whose year has run out.
                    if (check.user.isPremium) user = check.user
                    break
                }
                if (check?.status == PaymentStatus.Failed) break
                if (i < times - 1) delay(POLL_EVERY_MS)
            }
        }
        if (user == null) user = premiumUser()
        clearPendingOrder(context) // GET /api/me keeps checking unpaid orders on the backend
        checking = false
        if (user != null) paidUser = user else message = failMessage
    }

    fun buy() {
        val plan = paywall?.plans?.firstOrNull { it.id == selectedId } ?: return
        val act = activity ?: return
        if (buying || checking) return
        buying = true
        message = null
        scope.launch {
            val order = BloopApi.createOrder(plan.id).getOrElse { e ->
                buying = false
                when {
                    e is NotLoggedIn -> onAuthExpired()
                    // The backend says the user is premium already (e.g. a late payment).
                    e is ApiError && e.status == 409 -> premiumUser()?.let { paidUser = it } ?: run { message = e.message }
                    else -> message = e.message
                }
                return@launch
            }
            savePendingOrder(context, order.orderId)
            while (RazorpayResults.channel.tryReceive().isSuccess) Unit // drop any old result
            val opened = runCatching { openCheckout(act, order) }
            if (opened.isFailure) {
                clearPendingOrder(context)
                buying = false
                message = "Could not open the payment page. Please try again."
                return@launch
            }
            val result = RazorpayResults.channel.receive()
            buying = false
            when (result) {
                is CheckoutResult.Success -> confirm(
                    order.orderId, poll = true,
                    failMessage = "We could not confirm your payment yet. If money was deducted, it will be " +
                        "confirmed automatically in a few minutes. Otherwise, please try again.",
                )
                is CheckoutResult.Error -> confirm(
                    order.orderId, poll = false,
                    failMessage = if (result.code == Checkout.PAYMENT_CANCELED) "Payment cancelled. You can try again."
                    else "Payment failed. Please try again.",
                )
            }
        }
    }

    // On open: a payment may have gone through since (app killed mid-payment, late UPI).
    LaunchedEffect(loadKey) {
        loadError = null
        if (paidUser != null) return@LaunchedEffect
        val pending = pendingOrder(context)
        if (pending != null) {
            confirm(
                pending, poll = true, times = RESTORED_POLL_TIMES,
                failMessage = "Your last payment was not completed. Please try again.",
            )
        } else if (loadKey == 0) {
            premiumUser()?.let { paidUser = it }
        }
        if (paidUser != null) return@LaunchedEffect
        // After login the paywall is the default; an old user (has reminders) goes on to Home.
        val u = latestUser
        if (!canClose && u != null && u.canOpenHome) {
            onPremium(u)
            return@LaunchedEffect
        }
        BloopApi.paywall()
            .onSuccess { p ->
                paywall = p
                if (p.plans.none { it.id == selectedId }) {
                    selectedId = (p.plans.firstOrNull { it.selected } ?: p.plans.firstOrNull())?.id
                }
            }
            .onFailure { if (it is NotLoggedIn) onAuthExpired() else loadError = it.message }
    }

    val success = paidUser
    when {
        success != null -> PaymentSuccess(success) { onPremium(success) }
        checking -> CenterMessage(
            title = "Confirming your payment",
            body = "This can take up to a minute. Please keep the app open.",
            spinner = true,
        )
        else -> Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            if (canClose) BackButton(onClose) else LogoMark()

            val p = paywall
            when {
                p == null && loadError == null -> Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BloopColors.Violet)
                }
                p == null -> {
                    ErrorBox(loadError)
                    PrimaryButton("Try again") { loadKey++ }
                }
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(p.title, style = TitleStyle)
                        if (p.subtitle.isNotBlank()) Text(p.subtitle, fontSize = 15.sp, color = BloopColors.Muted)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        p.benefits.forEach { Benefit(it) }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        p.plans.forEach { plan ->
                            PlanCard(plan, selected = plan.id == selectedId) { selectedId = plan.id }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    ErrorBox(message)
                    PrimaryButton(p.ctaText, busy = buying) { buy() }
                    if (!canClose) {
                        TextButton(onClick = onLogout, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Log out", fontWeight = FontWeight.Bold, color = BloopColors.Muted)
                        }
                    }
                }
            }
        }
    }
}

private fun openCheckout(activity: Activity, order: PaymentOrder) {
    val checkout = Checkout()
    checkout.setKeyID(order.keyId)
    val options = JSONObject()
        .put("name", order.name)
        .put("description", order.description)
        .put("order_id", order.orderId)
        .put("currency", order.currency)
        .put("amount", order.amount)
        .put("prefill", JSONObject().put("name", order.prefillName).put("contact", order.prefillContact))
        .put("theme", JSONObject().put("color", "#5B3DF5"))
        .put("retry", JSONObject().put("enabled", true).put("max_count", 4))
    checkout.open(activity, options)
}

@Composable
private fun Benefit(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(28.dp).clip(CircleShape).background(BloopColors.MintSoft),
        ) {
            Icon(BloopIcons.Check, contentDescription = null, tint = BloopColors.MintText, modifier = Modifier.size(16.dp))
        }
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = BloopColors.Ink)
    }
}

@Composable
private fun PlanCard(plan: Plan, selected: Boolean, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) BloopColors.SurfaceTint else BloopColors.Surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) BloopColors.Violet else BloopColors.Border, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(18.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(plan.title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = BloopColors.Ink)
                if (plan.popular) {
                    Text(
                        "POPULAR", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = BloopColors.LemonText,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(BloopColors.LemonSoft)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            if (plan.description.isNotBlank()) Text(plan.description, fontSize = 13.sp, color = BloopColors.Muted)
        }
        Column(horizontalAlignment = Alignment.End) {
            plan.originalAmount?.let {
                Text(
                    formatPrice(it, plan.currency), fontSize = 13.sp, color = BloopColors.Muted,
                    textDecoration = TextDecoration.LineThrough,
                )
            }
            Text(formatPrice(plan.amount, plan.currency), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = BloopColors.Ink)
            Text("per ${plan.term}", fontSize = 12.sp, color = BloopColors.Muted)
        }
    }
}

@Composable
private fun PaymentSuccess(user: User, onHome: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(88.dp).clip(CircleShape).background(BloopColors.Mint),
        ) {
            Icon(BloopIcons.Check, contentDescription = null, tint = BloopColors.Ink, modifier = Modifier.size(44.dp))
        }
        Text("Payment successful", style = TitleStyle, textAlign = TextAlign.Center)
        Text(
            formatPremiumDate(user.premiumUntil)?.let { "Bloop Premium is active until $it." } ?: "Bloop Premium is active.",
            fontSize = 15.sp, color = BloopColors.Muted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton("Go to home", onClick = onHome)
    }
}

/** Full-screen message with an optional spinner (used while confirming a payment and on app start). */
@Composable
fun CenterMessage(title: String, body: String?, spinner: Boolean, action: (@Composable () -> Unit)? = null) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
    ) {
        if (spinner) CircularProgressIndicator(color = BloopColors.Violet)
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = BloopColors.Ink, textAlign = TextAlign.Center)
        body?.let { Text(it, fontSize = 14.sp, color = BloopColors.Muted, textAlign = TextAlign.Center) }
        action?.invoke()
    }
}

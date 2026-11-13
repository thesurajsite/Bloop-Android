package dev.suraj.bloop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class User(
    val id: String,
    val name: String,
    val email: String,
    val whatsappNumber: String,
    val reminderTime: String, // e.g. "09:00 AM"
    val whatsappRemEnable: Boolean, // WhatsApp reminders on or off for this user
)

/**
 * One reminder as the backend's API returns it (GET/POST /api/reminders).
 * The backend also stores user_id, created_at and updated_at, but the API does not send them.
 */
data class Reminder(
    val id: String,           // MongoDB ObjectID as hex
    val name: String,
    val eventType: String,    // "Birthday" or "Anniversary"
    val eventDate: String,    // yyyy-MM-dd as entered
    val reminderDate: String, // yyyy-MM-dd; defaults to eventDate
    val reminderTime: String, // e.g. "09:00 AM"
    val timezone: String,     // IANA name, e.g. "Asia/Kolkata"
    val whatsappRemEnable: Boolean, // false = no WhatsApp message for this reminder
    val nextDate: String,     // next time it comes round (worked out by the API, not stored)
    val daysLeft: Int,        // 0 = today (worked out by the API, not stored)
)

/** Result of toggling WhatsApp reminders: one reminder ([reminderId] set) or all of them ([remindersUpdated] set). */
data class WhatsAppReminderToggle(
    val enabled: Boolean,
    val reminderId: String?,
    val remindersUpdated: Int?,
)

/** A non-2xx reply from the backend; [message] is its {"error": "..."} text. */
class ApiError(val status: Int, message: String) : Exception(message)

/** Thrown when the backend says the login token is missing or expired. */
class NotLoggedIn : Exception("Please log in again")

/** Talks to Bloop-backend. All data lives on the backend; only the token is kept on the phone. */
object BloopApi {
    var baseUrl: String = BuildConfig.BACKEND_URL
    var token: String? = null

    suspend fun signup(name: String, email: String, whatsappNumber: String, password: String): Result<Pair<String, User>> =
        call("POST", "/api/signup", JSONObject()
            .put("name", name).put("email", email).put("whatsapp_number", whatsappNumber).put("password", password)
        ) { it.getString("token") to userFrom(it.getJSONObject("user")) }

    suspend fun login(email: String, password: String): Result<Pair<String, User>> =
        call("POST", "/api/login", JSONObject().put("email", email).put("password", password)) {
            it.getString("token") to userFrom(it.getJSONObject("user"))
        }

    suspend fun me(): Result<User> = call("GET", "/api/me") { userFrom(it) }

    suspend fun reminders(): Result<List<Reminder>> = call("GET", "/api/reminders") { json ->
        val arr = json.getJSONArray("reminders")
        List(arr.length()) { reminderFrom(arr.getJSONObject(it)) }
    }

    /**
     * POST /api/reminders. [reminderTime] (e.g. "09:30 PM") is the time from the user's profile;
     * when it is null or blank it is left out and the backend uses the user's saved reminder time.
     */
    suspend fun addReminder(name: String, eventType: String, eventDate: String, reminderTime: String?): Result<Reminder> {
        val body = JSONObject().put("name", name).put("event_type", eventType).put("event_date", eventDate)
        if (!reminderTime.isNullOrBlank()) body.put("reminder_time", reminderTime)
        return call("POST", "/api/reminders", body) { reminderFrom(it) }
    }

    /**
     * PATCH /api/reminders/{id}: sends only the fields that are not null.
     * If the backend saved the change but could not move the schedule (500), the same request is retried,
     * as the API contract asks. Returns the updated reminder.
     */
    suspend fun editReminder(
        reminderId: String,
        name: String? = null,
        eventType: String? = null,
        eventDate: String? = null,
    ): Result<Reminder> {
        val body = JSONObject()
        name?.let { body.put("name", it) }
        eventType?.let { body.put("event_type", it) }
        eventDate?.let { body.put("event_date", it) }

        var result: Result<Reminder> = Result.failure(IllegalStateException("not sent"))
        for (attempt in 0..SCHEDULE_RETRIES) {
            if (attempt > 0) delay(1_000L * attempt)
            result = call("PATCH", "/api/reminders/$reminderId", body) { reminderFrom(it) }
            if (!result.isScheduleNotUpdated()) break
        }
        return result
    }

    /** DELETE /api/reminders/{id}: 204 with no body on success. */
    suspend fun deleteReminder(reminderId: String): Result<Unit> = call("DELETE", "/api/reminders/$reminderId") { }

    private const val SCHEDULE_RETRIES = 2

    private fun Result<*>.isScheduleNotUpdated(): Boolean {
        val e = exceptionOrNull() as? ApiError ?: return false
        return e.status == 500 && e.message.orEmpty().startsWith("saved, but could not update")
    }

    /**
     * Turns WhatsApp reminders on or off. With [reminderId] only that reminder changes;
     * without it the user's setting and all their reminders change.
     */
    suspend fun toggleWhatsAppReminder(enabled: Boolean, reminderId: String? = null): Result<WhatsAppReminderToggle> {
        val body = JSONObject()
        body.put("whatsapp_rem_enable", enabled)
        if (reminderId != null) {
            body.put("reminder_id", reminderId)
        }

        return call("PATCH", "/api/toggle-whatsapp-reminder", body) { response ->
            var updatedReminderId: String? = null
            if (response.has("reminder_id")) {
                updatedReminderId = response.getString("reminder_id")
            }

            var remindersUpdated: Int? = null
            if (response.has("reminders_updated")) {
                remindersUpdated = response.getInt("reminders_updated")
            }

            WhatsAppReminderToggle(
                enabled = response.getBoolean("whatsapp_rem_enable"),
                reminderId = updatedReminderId,
                remindersUpdated = remindersUpdated,
            )
        }
    }

    /**
     * Saves a new reminder time (e.g. "09:30 PM") on the user and on all their reminders.
     * Returns the time the backend saved.
     */
    suspend fun updateReminderTime(reminderTime: String): Result<String> {
        val body = JSONObject()
        body.put("reminder_time", reminderTime)

        return call("PATCH", "/api/update-reminder-time", body) { response ->
            response.getString("reminder_time")
        }
    }

    private fun userFrom(j: JSONObject) = User(
        id = j.getString("id"),
        name = j.getString("name"),
        email = j.getString("email"),
        whatsappNumber = j.getString("whatsapp_number"),
        reminderTime = j.getString("reminder_time"),
        whatsappRemEnable = j.getBoolean("whatsapp_rem_enable"),
    )

    private fun reminderFrom(j: JSONObject) = Reminder(
        id = j.getString("id"),
        name = j.getString("name"),
        eventType = j.getString("event_type"),
        eventDate = j.getString("event_date"),
        reminderDate = j.getString("reminder_date"),
        reminderTime = j.getString("reminder_time"),
        timezone = j.getString("timezone"),
        whatsappRemEnable = j.getBoolean("whatsapp_rem_enable"),
        nextDate = j.getString("next_date"),
        daysLeft = j.getInt("days_left"),
    )

    /** One HTTP call: sends JSON, reads JSON, turns {"error": "..."} into a failure. */
    private suspend fun <T> call(
        method: String,
        path: String,
        body: JSONObject? = null,
        parse: (JSONObject) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 10_000
                conn.readTimeout = 20_000
                conn.setRequestProperty("Accept", "application/json")
                token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toString().toByteArray()) }
                }

                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = if (text.isBlank()) JSONObject() else JSONObject(text)

                if (code == 401 && token != null) throw NotLoggedIn()
                if (code !in 200..299) throw ApiError(code, json.optString("error", "Something went wrong ($code)"))
                parse(json)
            } finally {
                conn.disconnect()
            }
        }
    }
}

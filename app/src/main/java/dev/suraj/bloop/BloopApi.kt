package dev.suraj.bloop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class User(val id: Long, val name: String, val email: String, val phone: String)

data class Person(
    val id: Long,
    val name: String,
    val eventType: String,
    val eventDate: String, // yyyy-MM-dd as entered
    val nextDate: String,  // next time it comes round
    val daysLeft: Int,     // 0 = today
)

/** Thrown when the backend says the login token is missing or expired. */
class NotLoggedIn : Exception("Please log in again")

/** Talks to Bloop-backend. All data lives on the backend; only the token is kept on the phone. */
object BloopApi {
    var baseUrl: String = BuildConfig.BACKEND_URL
    var token: String? = null

    suspend fun signup(name: String, email: String, phone: String, password: String): Result<Pair<String, User>> =
        call("POST", "/api/signup", JSONObject()
            .put("name", name).put("email", email).put("phone", phone).put("password", password)
        ) { it.getString("token") to userFrom(it.getJSONObject("user")) }

    suspend fun login(email: String, password: String): Result<Pair<String, User>> =
        call("POST", "/api/login", JSONObject().put("email", email).put("password", password)) {
            it.getString("token") to userFrom(it.getJSONObject("user"))
        }

    suspend fun me(): Result<User> = call("GET", "/api/me") { userFrom(it) }

    suspend fun people(): Result<List<Person>> = call("GET", "/api/guests") { json ->
        val arr = json.getJSONArray("guests")
        List(arr.length()) { personFrom(arr.getJSONObject(it)) }
    }

    suspend fun addPerson(name: String, eventType: String, eventDate: String): Result<Person> =
        call("POST", "/api/guests", JSONObject()
            .put("name", name).put("event_type", eventType).put("event_date", eventDate)
        ) { personFrom(it) }

    suspend fun deletePerson(id: Long): Result<Unit> = call("DELETE", "/api/guests/$id") { }

    private fun userFrom(j: JSONObject) =
        User(j.getLong("id"), j.getString("name"), j.getString("email"), j.getString("phone"))

    private fun personFrom(j: JSONObject) = Person(
        id = j.getLong("id"),
        name = j.getString("name"),
        eventType = j.getString("event_type"),
        eventDate = j.getString("event_date"),
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
                if (code !in 200..299) error(json.optString("error", "Something went wrong ($code)"))
                parse(json)
            } finally {
                conn.disconnect()
            }
        }
    }
}

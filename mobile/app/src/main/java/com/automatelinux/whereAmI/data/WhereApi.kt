package com.automatelinux.whereAmI.data

import com.automatelinux.whereAmI.BuildConfig
import com.automatelinux.whereAmI.data.model.Address
import com.automatelinux.whereAmI.data.model.AgeBand
import com.automatelinux.whereAmI.data.model.Cello
import com.automatelinux.whereAmI.data.model.Locality
import com.automatelinux.whereAmI.data.model.ParkingSession
import com.automatelinux.whereAmI.data.model.StreetMatch
import com.automatelinux.whereAmI.data.model.StreetPlace
import com.automatelinux.whereAmI.data.model.Where
import com.automatelinux.whereAmI.data.model.Zone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** The backend refused or failed; `message` is already fit to show. */
class ApiException(message: String, val status: Int) : Exception(message)

/** Result of asking to park. */
sealed interface StartOutcome {
    data class Started(val message: String) : StartOutcome
    data class NeedsConfirmation(val zoneId: Int, val message: String, val code: String) : StartOutcome
    data class Info(val message: String) : StartOutcome
}

/**
 * The whereAmI backend (Next.js on the desktop, reached directly over WireGuard).
 * Plain HttpURLConnection + org.json: four calls do not need a client library.
 */
class WhereApi(
    private val baseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/'),
    private val token: String = BuildConfig.API_TOKEN,
) {
    private suspend fun request(path: String, body: JSONObject? = null): Pair<Int, JSONObject> = withContext(Dispatchers.IO) {
        val conn = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 90_000
            setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val status = conn.responseCode
            val stream = if (status < 400) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
            status to JSONObject(text)
        } catch (e: java.io.IOException) {
            throw ApiException("השרת לא זמין (${e.message ?: "אין חיבור"})", 0)
        } finally {
            conn.disconnect()
        }
    }

    suspend fun where(lat: Double, lon: Double): Where {
        val (status, json) = request("/api/where?lat=$lat&lon=$lon")
        if (status != 200) throw ApiException(json.optString("error", "שגיאה $status"), status)
        return parseWhere(json)
    }

    suspend fun street(query: String): List<StreetMatch> {
        val (status, json) = request("/api/street?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
        if (status != 200) throw ApiException(json.optString("error", "שגיאה $status"), status)
        return json.getJSONArray("matches").objects().map { m ->
            val point = m.getJSONObject("point")
            StreetMatch(
                label = m.getString("label"),
                city = m.str("city"),
                neighborhoods = m.getJSONArray("neighborhoods").objects().map { StreetPlace(it.str("name"), it.getDouble("lat"), it.getDouble("lon")) },
                lat = point.getDouble("lat"),
                lon = point.getDouble("lon"),
            )
        }
    }

    suspend fun sessions(): List<ParkingSession> {
        val (status, json) = request("/api/parking/status")
        if (status != 200) throw ApiException(json.optString("error", "שגיאה $status"), status)
        return json.getJSONArray("sessions").objects().map {
            ParkingSession(it.getLong("transactionId"), it.str("zoneName"), it.str("startedAt"), if (it.isNull("amount")) null else it.getDouble("amount"))
        }
    }

    suspend fun start(lat: Double, lon: Double, accuracyM: Int?, zoneId: Int, confirmationCode: String? = null): StartOutcome {
        val body = JSONObject().put("lat", lat).put("lon", lon).put("zoneId", zoneId)
        accuracyM?.let { body.put("accuracyM", it) }
        confirmationCode?.let { body.put("confirmationCode", it) }
        val (status, json) = request("/api/parking/start", body)
        if (status != 200) throw ApiException(json.str("message") ?: json.optString("error", "שגיאה $status"), status)
        val result = json.getJSONObject("result")
        val zone = json.getJSONObject("zone").getString("name")
        val message = result.str("message")
        return when (result.getString("kind")) {
            "started" -> StartOutcome.Started("החניה התחילה — $zone")
            "already-active" -> StartOutcome.Info("כבר יש חניה פעילה")
            "free-now" -> StartOutcome.Info(message ?: "החניה כאן כרגע ללא תשלום")
            "needs-confirmation" -> StartOutcome.NeedsConfirmation(zoneId, message ?: "", result.getString("confirmationCode"))
            else -> throw ApiException(message ?: "Cello לא התחיל חניה", 409)
        }
    }

    suspend fun stop(lat: Double?, lon: Double?): String {
        val body = JSONObject()
        if (lat != null && lon != null) body.put("lat", lat).put("lon", lon)
        val (status, json) = request("/api/parking/stop", body)
        if (status != 200) throw ApiException(json.str("message") ?: json.optString("error", "שגיאה $status"), status)
        return when (json.getString("outcome")) {
            "nothing-active" -> "לא הייתה חניה פעילה"
            else -> "החניה הופסקה"
        }
    }

    private fun parseWhere(j: JSONObject): Where {
        val a = j.getJSONObject("address")
        val address = Address(
            street = a.str("street"), houseNumber = a.str("houseNumber"), neighborhood = a.str("neighborhood"),
            quarter = a.str("quarter"), city = a.str("city"), subdistrict = a.str("subdistrict"),
            district = a.str("district"), postcode = a.str("postcode"), place = a.str("place"),
        )
        val locality = j.optJSONObject("locality")?.let { l ->
            Locality(
                name = l.getString("name"), population = l.getInt("population"),
                subdistrict = l.str("subdistrict"), regionalCouncil = l.str("regionalCouncil"),
                ages = l.getJSONArray("ages").objects().map { AgeBand(it.getString("label"), it.getInt("count")) },
                source = l.getString("source"),
            )
        }
        val c = j.getJSONObject("cello")
        fun zones(key: String) = c.optJSONArray(key)?.objects()?.map(::zone) ?: emptyList()
        val cello = when (c.getString("kind")) {
            "no-city" -> Cello.NoCity(c.getString("message"))
            "one" -> Cello.One(c.getInt("cityId"), c.getString("city"), zone(c.getJSONObject("zone")), zones("others"))
            "several" -> Cello.Several(c.getInt("cityId"), c.getString("city"), zones("candidates"), c.getString("message"), zones("others"))
            else -> Cello.None(c.getInt("cityId"), c.getString("city"), c.getString("message"), zones("others"))
        }
        return Where(
            address = address,
            neighborhoods = j.getJSONArray("neighborhoods").strings(),
            landmarks = j.getJSONArray("landmarks").strings(),
            industrial = if (j.isNull("industrial")) null else j.getBoolean("industrial"),
            parkingArea = if (j.isNull("parkingArea")) null else j.getInt("parkingArea"),
            locality = locality,
            cello = cello,
        )
    }

    private fun zone(z: JSONObject) = Zone(z.getInt("id"), z.getString("name"), z.str("reason"))
}

private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }
private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

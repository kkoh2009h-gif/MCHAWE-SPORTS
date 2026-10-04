package com.mchawe.sports

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

data class Match(
    val id: String, val home: String, val away: String, val time: String,
    val state: String, val score: String, val competition: String, val venue: String
)

object FootballDataClient {
    private val executor = Executors.newSingleThreadExecutor()
    fun load(token: String, callback: (Result<List<Match>>) -> Unit) {
        executor.execute {
            try {
                val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Baghdad"))
                val fromFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                calendar.add(Calendar.DAY_OF_YEAR, -3)
                val from = fromFormat.format(calendar.time)
                calendar.add(Calendar.DAY_OF_YEAR, 10)
                val to = fromFormat.format(calendar.time)
                val url = URL("https://api.football-data.org/v4/matches?dateFrom=$from&dateTo=$to")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"; connectTimeout = 15000; readTimeout = 15000
                    setRequestProperty("X-Auth-Token", token)
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val body = stream.bufferedReader().use { it.readText() }
                    if (code !in 200..299) {
                        val message = when (code) {
                            401 -> "مفتاح API غير صحيح. تأكد من نسخه بشكل كامل."
                            403 -> "الخطة لا تسمح بهذه البطولة أو لا تملك صلاحية الوصول."
                            429 -> "انتهى حد الطلبات المسموح حالياً؛ حاول لاحقاً."
                            else -> "خطأ من مزود البيانات ($code)."
                        }
                        throw IllegalStateException(message)
                    }
                    val array = JSONObject(body).optJSONArray("matches")
                        ?: throw IllegalStateException("لم تصل قائمة المباريات.")
                    val dateParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val dateOutput = SimpleDateFormat("dd/MM  HH:mm", Locale("ar", "IQ")).apply {
                        timeZone = TimeZone.getTimeZone("Asia/Baghdad")
                    }
                    val matches = mutableListOf<Match>()
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val state = when (item.optString("status")) {
                            "IN_PLAY", "PAUSED", "LIVE" -> "مباشرة"
                            "FINISHED", "AWARDED" -> "منتهية"
                            "POSTPONED", "SUSPENDED", "CANCELLED" -> "مؤجلة/ملغاة"
                            else -> "قادمة"
                        }
                        val kickoff = try {
                            dateOutput.format(dateParser.parse(item.optString("utcDate"))!!)
                        } catch (_: Exception) { item.optString("utcDate") }
                        val fullTime = item.optJSONObject("score")?.optJSONObject("fullTime")
                        val h = fullTime?.opt("home")?.toString()?.takeUnless { it == "null" } ?: "-"
                        val a = fullTime?.opt("away")?.toString()?.takeUnless { it == "null" } ?: "-"
                        matches.add(Match(
                            item.optLong("id").toString(),
                            item.optJSONObject("homeTeam")?.optString("name") ?: "الفريق الأول",
                            item.optJSONObject("awayTeam")?.optString("name") ?: "الفريق الثاني",
                            kickoff, state, if (state == "قادمة") "لم تبدأ" else "$h - $a",
                            item.optJSONObject("competition")?.optString("name") ?: "غير محددة",
                            item.optString("venue").takeUnless { it.isBlank() || it == "null" } ?: "غير متوفر"
                        ))
                    }
                    callback(Result.success(matches))
                } finally { connection.disconnect() }
            } catch (e: Exception) { callback(Result.failure(e)) }
        }
    }
}

package com.mchawe.sports

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

data class Match(
    val id: String,
    val home: String,
    val away: String,
    val time: String,
    val state: String,
    val score: String,
    val competition: String,
    val venue: String
)

object FootballDataClient {
    private val executor = Executors.newSingleThreadExecutor()
    private val formatter = DateTimeFormatter.ofPattern("dd/MM  HH:mm")
        .withZone(ZoneId.of("Asia/Baghdad"))

    fun load(token: String, callback: (Result<List<Match>>) -> Unit) {
        executor.execute {
            try {
                val from = java.time.LocalDate.now(ZoneId.of("Asia/Baghdad")).minusDays(3)
                val to = from.plusDays(10)
                val url = URL("https://api.football-data.org/v4/matches?dateFrom=$from&dateTo=$to")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("X-Auth-Token", token)
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val body = stream.bufferedReader().use { it.readText() }
                    if (code !in 200..299) {
                        val message = when (code) {
                            403 -> "الخطة الحالية لا تسمح بهذه البطولة أو انتهت صلاحية المفتاح."
                            429 -> "وصلنا حد الطلبات المسموح؛ حاول لاحقاً."
                            400 -> "طلب غير صحيح من مزود البيانات."
                            else -> "خطأ من مزود البيانات ($code): $body"
                        }
                        throw IllegalStateException(message)
                    }
                    val array = JSONObject(body).optJSONArray("matches")
                        ?: throw IllegalStateException("لم تصل قائمة المباريات من المصدر.")
                    val matches = buildList {
                        for (i in 0 until array.length()) {
                            val item = array.getJSONObject(i)
                            val home = item.getJSONObject("homeTeam").optString("name", "الفريق الأول")
                            val away = item.getJSONObject("awayTeam").optString("name", "الفريق الثاني")
                            val status = item.optString("status")
                            val state = when (status) {
                                "IN_PLAY", "PAUSED", "LIVE" -> "مباشرة"
                                "FINISHED", "AWARDED" -> "منتهية"
                                "POSTPONED", "SUSPENDED", "CANCELLED" -> "مؤجلة/ملغاة"
                                else -> "قادمة"
                            }
                            val utc = item.optString("utcDate")
                            val kickoff = try { formatter.format(Instant.parse(utc)) } catch (_: Exception) { utc }
                            val scoreObj = item.optJSONObject("score")?.optJSONObject("fullTime")
                            val homeScore = scoreObj?.opt("home")?.toString()?.takeUnless { it == "null" } ?: "-"
                            val awayScore = scoreObj?.opt("away")?.toString()?.takeUnless { it == "null" } ?: "-"
                            val score = if (state == "قادمة") "لم تبدأ" else "$homeScore - $awayScore"
                            add(Match(
                                item.optLong("id").toString(), home, away, kickoff, state, score,
                                item.optJSONObject("competition")?.optString("name") ?: "غير محددة",
                                item.optString("venue").takeUnless { it == "null" } ?: "غير متوفر"
                            ))
                        }
                    }
                    callback(Result.success(matches))
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }
}

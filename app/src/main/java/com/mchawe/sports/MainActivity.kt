package com.mchawe.sports

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {
    private val navy = 0xFF071426.toInt()
    private val panel = 0xFF10243D.toInt()
    private val mint = 0xFF36D6A5.toInt()
    private val white = 0xFFF4F7FB.toInt()
    private val muted = 0xFFA8B8CA.toInt()
    private val prefs by lazy { getSharedPreferences("mchawe", MODE_PRIVATE) }
    private val favorites = mutableSetOf<String>()
    private var matches: List<Match> = emptyList()
    private var loading = false
    private var errorMessage: String? = null
    private var lastUpdated: String? = null
    private var player: ExoPlayer? = null
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val autoRefreshRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing && !loading && !prefs.getString("football_token", null).isNullOrBlank()) {
                refreshMatches()
            }
            refreshHandler.postDelayed(this, 5 * 60 * 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        favorites.addAll(prefs.getStringSet("favorites", emptySet()) ?: emptySet())
        matches = readCachedMatches()
        lastUpdated = prefs.getString("last_updated", null)
        showHome()
        if (prefs.getString("football_token", null).isNullOrBlank()) showTokenDialog() else refreshMatches()
        refreshHandler.postDelayed(autoRefreshRunnable, 5 * 60 * 1000L)
    }

    private fun baseLayout() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(20, 20, 20, 20)
        setBackgroundColor(navy)
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.RIGHT
        }

    private fun actionButton(textValue: String, action: () -> Unit) =
        MaterialButton(this).apply { text = textValue; setOnClickListener { action() } }

    private fun encodeMatches(items: List<Match>): String {
        val array = org.json.JSONArray()
        items.forEach { match ->
            array.put(org.json.JSONObject().apply {
                put("id", match.id); put("home", match.home); put("away", match.away)
                put("time", match.time); put("state", match.state); put("score", match.score)
                put("competition", match.competition); put("venue", match.venue); put("sortKey", match.sortKey)
            })
        }
        return array.toString()
    }

    private fun readCachedMatches(): List<Match> {
        return try {
            val array = org.json.JSONArray(prefs.getString("cached_matches", "[]"))
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                Match(item.optString("id"), item.optString("home"), item.optString("away"),
                    item.optString("time"), item.optString("state"), item.optString("score"),
                    item.optString("competition"), item.optString("venue"), item.optLong("sortKey", 0L))
            }
        } catch (_: Exception) { emptyList() }
    }
    private fun refreshMatches() {
        val token = prefs.getString("football_token", null).orEmpty()
        if (token.isBlank()) { showTokenDialog(); return }
        loading = true; errorMessage = null; showHome()
        FootballDataClient.load(token) { result ->
            runOnUiThread {
                loading = false
                result.onSuccess {
                    matches = it
                    lastUpdated = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("ar", "IQ")).format(java.util.Date())
                    prefs.edit {
                        putString("cached_matches", encodeMatches(it))
                        putString("last_updated", lastUpdated)
                    }
                    errorMessage = if (it.isEmpty()) "ماكو مباريات ضمن الفترة الحالية أو ضمن البطولات المتاحة بحسابك." else null
                }.onFailure { errorMessage = it.message ?: "تعذر جلب المباريات." }
                showHome()
            }
        }
    }

    private fun showHome(filter: String = "الكل", query: String = "") {
        player?.release(); player = null
        val root = baseLayout()
        root.addView(label("MCHAWE SPORTS", 25f, mint, true))
        root.addView(label("مواعيد ونتائج كرة القدم من مصدر بيانات حقيقي", 13f, muted))
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(actionButton(if (loading) "جارٍ التحديث…" else "تحديث") { if (!loading) refreshMatches() }.apply { isEnabled = !loading }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(actionButton("الإعدادات") { showSettings() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(top)
        val liveCount = matches.count { it.state == "مباشرة" }
        root.addView(label("⚽ مركز المباريات\n\n$liveCount مباراة مباشرة حسب آخر تحديث", 17f, white, true).apply {
            setPadding(16, 16, 16, 16); setBackgroundColor(panel)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 14; bottomMargin = 12 })
        val search = EditText(this).apply {
            hint = "ابحث عن فريق أو بطولة"; textSize = 14f; setSingleLine(true)
            setTextColor(white); setHintTextColor(muted); setPadding(12, 8, 12, 8)
            setBackgroundColor(panel); setText(query)
        }
        val searchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        searchRow.addView(search, LinearLayout.LayoutParams(0, -2, 1f))
        searchRow.addView(actionButton("بحث") { showHome(filter, search.text.toString()) })
        root.addView(searchRow)
        val filters = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("الكل", "مباشرة", "قادمة", "منتهية", "مؤجلة", "المفضلة").forEach { f ->
            filters.addView(MaterialButton(this).apply {
                text = f; textSize = 10f; minWidth = 0
                setOnClickListener { showHome(f, search.text.toString()) }
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = 6 })
        }
        root.addView(ScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(filters)
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(label("المباريات", 19f, white, true).apply { setPadding(0, 14, 0, 8) })
        when {
            loading && matches.isEmpty() -> root.addView(label("جاري تحميل المباريات من المصدر...", 14f, mint))
            else -> {
                if (errorMessage != null) {
                    root.addView(label("تعذر التحديث: " + errorMessage + " - نعرض آخر بيانات تم تحميلها إن وجدت.", 13f, 0xFFFFC27A.toInt()))
                    root.addView(actionButton("إعادة المحاولة") { refreshMatches() })
                }
                val shown = matches.filter { match ->
                    val stateOk = when (filter) {
                        "الكل" -> true
                        "المفضلة" -> favorites.contains(match.id)
                        "مؤجلة" -> match.state == "مؤجلة/ملغاة"
                        else -> match.state == filter
                    }
                    val q = query.trim()
                    stateOk && (q.isEmpty() || match.home.contains(q, true) ||
                        match.away.contains(q, true) || match.competition.contains(q, true))
                }
                if (shown.isEmpty()) root.addView(label(if (errorMessage != null && matches.isEmpty()) "ما متوفرة بيانات حالياً. تحقق من الإنترنت ومفتاح API." else "ماكو مباريات تطابق الاختيار.", 14f, muted))
                shown.forEach { match ->
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL; setPadding(14, 12, 14, 12)
                        setBackgroundColor(panel)
                    }
                    val header = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    header.addView(label(match.competition, 12f, mint, true),
                        LinearLayout.LayoutParams(0, -2, 1f))
                    header.addView(label(
                        when (match.state) {
                            "مباشرة" -> "● مباشر"
                            "منتهية" -> "منتهية"
                            "قادمة" -> "قادمة"
                            else -> match.state
                        },
                        11f,
                        if (match.state == "مباشرة") mint else muted,
                        true
                    ))
                    card.addView(header)
                    card.addView(label("${match.home}   ×   ${match.away}", 17f, white, true).apply {
                        gravity = Gravity.CENTER
                        setPadding(4, 12, 4, 8)
                    })
                    card.addView(label("${match.time}  •  ${match.score}", 13f,
                        if (match.state == "مباشرة") mint else muted).apply {
                        gravity = Gravity.CENTER
                    })
                    val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    buttons.addView(actionButton(if (favorites.contains(match.id)) "★ محفوظة" else "☆ المفضلة") {
                        if (favorites.contains(match.id)) favorites.remove(match.id) else favorites.add(match.id)
                        prefs.edit { putStringSet("favorites", favorites.toSet()) }
                        showHome(filter, query)
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    buttons.addView(actionButton("التفاصيل") { showMatchDetails(match, filter, query) },
                        LinearLayout.LayoutParams(0, -2, 1f))
                    card.addView(buttons)
                    root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })
                }
            }
        }
        root.addView(label("آخر تحديث: ${lastUpdated ?: "لم يتم التحديث بعد"}", 11f, muted))
        root.addView(label("المصدر: football-data.org • التوقيت بتوقيت بغداد • البطولات حسب الخطة.", 11f, muted))
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun showTokenDialog() {
        val input = EditText(this).apply { hint = "ألصق مفتاح X-Auth-Token هنا"; setSingleLine(true) }
        android.app.AlertDialog.Builder(this)
            .setTitle("ربط مصدر المباريات")
            .setMessage("أنشئ حساباً مجانياً في football-data.org ثم ألصق مفتاح API. سيُحفظ على هذا الجهاز فقط.")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("حفظ وربط") { _, _ ->
                val token = input.text.toString().trim()
                if (token.isBlank()) {
                    Toast.makeText(this, "أدخل مفتاح API أولاً", Toast.LENGTH_LONG).show()
                    showTokenDialog()
                } else {
                    prefs.edit { putString("football_token", token) }
                    refreshMatches()
                }
            }
            .setNeutralButton("لاحقاً") { _, _ -> showHome() }
            .show()
    }

    private fun showSettings() {
        val root = baseLayout()
        root.addView(actionButton("رجوع") { showHome() })
        root.addView(label("الإعدادات", 24f, mint, true))
        root.addView(label("المباريات المحفوظة: ${favorites.size}", 15f, white))
        root.addView(label("مصدر البيانات: football-data.org", 14f, muted))
        root.addView(label("المفتاح محفوظ محلياً على جهازك ولا يُرفع إلى مستودع GitHub.", 12f, muted))
        root.addView(actionButton("إدخال / تغيير مفتاح API") { showTokenDialog() })
        root.addView(actionButton("تحديث المباريات الآن") { refreshMatches() })
        root.addView(actionButton("مسح مفتاح API") {
            android.app.AlertDialog.Builder(this).setTitle("حذف المفتاح؟")
                .setMessage("سيتم فصل مصدر البيانات من هذا الجهاز.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حذف") { _, _ ->
                    prefs.edit { remove("football_token") }; matches = emptyList()
                    Toast.makeText(this, "تم حذف المفتاح", Toast.LENGTH_SHORT).show()
                    showHome()
                }.show()
        })
        root.addView(actionButton("مسح جميع المفضلة") {
            android.app.AlertDialog.Builder(this).setTitle("مسح المفضلة")
                .setMessage("متأكد تريد حذف كل المباريات المحفوظة؟")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("مسح") { _, _ ->
                    favorites.clear(); prefs.edit { putStringSet("favorites", emptySet()) }
                    showSettings()
                }.show()
        })
        root.addView(actionButton("مشاركة التطبيق") {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, "MCHAWE SPORTS: https://github.com/kkoh2009h-gif/MCHAWE-SPORTS")
            }
            startActivity(android.content.Intent.createChooser(intent, "مشاركة التطبيق"))
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun showMatchDetails(match: Match, filter: String, query: String) {
        val root = baseLayout()
        root.addView(actionButton("رجوع") { showHome(filter, query) })
        root.addView(label("تفاصيل المباراة", 23f, mint, true))
        root.addView(label("${match.home}\n\n${match.score}\n\n${match.away}", 22f, white, true).apply {
            gravity = Gravity.CENTER; setPadding(16, 24, 16, 24); setBackgroundColor(panel)
        })
        val statusColor = if (match.state == "مباشرة") mint else white
        root.addView(label("● ${match.state}", 16f, statusColor, true).apply {
            setPadding(14, 14, 14, 14)
            setBackgroundColor(panel)
        })
        root.addView(label("الموعد: ${match.time} (توقيت بغداد)", 14f, muted))
        root.addView(label("البطولة: ${match.competition}", 14f, muted))
        root.addView(label("الملعب: ${if (match.venue.isBlank()) "غير محدد" else match.venue}", 14f, muted))
        root.addView(actionButton(if (favorites.contains(match.id)) "★ إزالة من المفضلة" else "☆ إضافة للمفضلة") {
            if (favorites.contains(match.id)) favorites.remove(match.id) else favorites.add(match.id)
            prefs.edit { putStringSet("favorites", favorites.toSet()) }
            showMatchDetails(match, filter, query)
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun showPlayer(streamUrl: String) {
        val root = baseLayout()
        root.addView(label("مشغل MCHAWE SPORTS", 22f, mint, true))
        val view = PlayerView(this)
        root.addView(view, LinearLayout.LayoutParams(-1, 240))
        player = ExoPlayer.Builder(this).build().also {
            view.player = it; it.setMediaItem(MediaItem.fromUri(streamUrl))
            it.prepare(); it.playWhenReady = true
        }
        setContentView(root)
    }

    override fun onStop() {
        super.onStop()
        player?.release(); player = null
    }

    override fun onDestroy() {
        refreshHandler.removeCallbacks(autoRefreshRunnable)
        player?.release(); player = null
        super.onDestroy()
    }
}

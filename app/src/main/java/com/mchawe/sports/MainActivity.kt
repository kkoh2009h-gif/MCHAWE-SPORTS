package com.mchawe.sports

import android.os.Bundle
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
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        favorites.addAll(prefs.getStringSet("favorites", emptySet()) ?: emptySet())
        showHome()
        if (prefs.getString("football_token", null).isNullOrBlank()) showTokenDialog() else refreshMatches()
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

    private fun refreshMatches() {
        val token = prefs.getString("football_token", null).orEmpty()
        if (token.isBlank()) { showTokenDialog(); return }
        loading = true; errorMessage = null; showHome()
        FootballDataClient.load(token) { result ->
            runOnUiThread {
                loading = false
                result.onSuccess {
                    matches = it
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
        top.addView(actionButton("تحديث") { refreshMatches() }, LinearLayout.LayoutParams(0, -2, 1f))
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
        listOf("الكل", "مباشرة", "قادمة", "منتهية", "المفضلة").forEach { f ->
            filters.addView(MaterialButton(this).apply {
                text = f; textSize = 9f; setOnClickListener { showHome(f, search.text.toString()) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(filters)
        root.addView(label("المباريات", 19f, white, true).apply { setPadding(0, 14, 0, 8) })
        when {
            loading -> root.addView(label("جاري تحميل المباريات من المصدر...", 14f, mint))
            errorMessage != null -> root.addView(label(errorMessage!!, 14f, muted))
            else -> {
                val shown = matches.filter { match ->
                    val stateOk = when (filter) {
                        "الكل" -> true
                        "المفضلة" -> favorites.contains(match.id)
                        else -> match.state == filter
                    }
                    val q = query.trim()
                    stateOk && (q.isEmpty() || match.home.contains(q, true) ||
                        match.away.contains(q, true) || match.competition.contains(q, true))
                }
                if (shown.isEmpty()) root.addView(label("ماكو مباريات تطابق الاختيار.", 14f, muted))
                shown.forEach { match ->
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL; setPadding(14, 12, 14, 12)
                        setBackgroundColor(panel)
                    }
                    card.addView(label(match.competition, 12f, mint, true))
                    card.addView(label("${match.home}   ×   ${match.away}", 16f, white, true))
                    card.addView(label("${match.state}  •  ${match.time}  •  ${match.score}", 13f,
                        if (match.state == "مباشرة") mint else muted))
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
        root.addView(label("الحالة: ${match.state}", 15f, white))
        root.addView(label("الموعد: ${match.time} (توقيت بغداد)", 14f, muted))
        root.addView(label("البطولة: ${match.competition}", 14f, muted))
        root.addView(label("الملعب: ${match.venue}", 14f, muted))
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
}

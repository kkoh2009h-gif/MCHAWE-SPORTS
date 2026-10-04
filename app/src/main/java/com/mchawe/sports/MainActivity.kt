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

data class Match(val home: String, val away: String, val time: String, val state: String) {
    val id: String get() = "$home-$away"
}

class MainActivity : AppCompatActivity() {
    private val navy = 0xFF071426.toInt()
    private val panel = 0xFF10243D.toInt()
    private val mint = 0xFF36D6A5.toInt()
    private val white = 0xFFF4F7FB.toInt()
    private val muted = 0xFFA8B8CA.toInt()
    private val matches = listOf(
        Match("ريال مدريد", "برشلونة", "21:00", "قادمة"),
        Match("ليفربول", "مانشستر سيتي", "18:30", "مباشرة"),
        Match("باريس سان جيرمان", "بايرن ميونخ", "غداً 22:00", "قادمة"),
        Match("ميلان", "إنتر ميلان", "انتهت", "منتهية")
    )
    private val favorites = mutableSetOf<String>()
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        favorites.addAll(getSharedPreferences("mchawe", MODE_PRIVATE)
            .getStringSet("favorites", emptySet()) ?: emptySet())
        showHome()
    }

    private fun baseLayout() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(20, 20, 20, 20)
        setBackgroundColor(navy)
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.RIGHT
        }

    private fun showHome(filter: String = "الكل", query: String = "") {
        player?.release()
        player = null
        val content = baseLayout()
        content.addView(label("MCHAWE SPORTS", 25f, mint, true))
        content.addView(label("عالم المباريات بين يديك", 14f, muted))
        val liveCount = matches.count { it.state == "مباشرة" }
        val hero = TextView(this).apply {
            text = "⚽  مركز المباريات\n\n$liveCount مباراة مباشرة الآن\n\nتابع جدول المباريات ونتائجها"
            textSize = 18f
            setTextColor(white)
            gravity = Gravity.RIGHT
            setPadding(18, 18, 18, 18)
            setBackgroundColor(panel)
        }
        content.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 20; bottomMargin = 14 })

        val search = EditText(this).apply {
            hint = "ابحث عن فريق..."
            textSize = 15f
            setSingleLine(true)
            setTextColor(white)
            setHintTextColor(muted)
            setPadding(14, 10, 14, 10)
            setBackgroundColor(panel)
            setText(query)
        }
        content.addView(search, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        val filters = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("الكل", "مباشرة", "قادمة", "منتهية", "المفضلة").forEach { name ->
            val button = MaterialButton(this).apply {
                text = name
                textSize = 10f
                setOnClickListener { showHome(name, search.text.toString()) }
            }
            filters.addView(button, LinearLayout.LayoutParams(0, -2, 1f))
        }
        content.addView(filters)
        content.addView(label("المباريات", 20f, white, true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = 18; bottomMargin = 10 })

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val normalizedQuery = query.trim()
        val shown = matches.filter { match ->
            val stateOk = when (filter) {
                "الكل" -> true
                "المفضلة" -> favorites.contains(match.id)
                else -> match.state == filter
            }
            val queryOk = normalizedQuery.isEmpty() ||
                match.home.contains(normalizedQuery, true) ||
                match.away.contains(normalizedQuery, true)
            stateOk && queryOk
        }
        if (shown.isEmpty()) list.addView(label("ماكو مباريات تطابق البحث حالياً.", 14f, muted))
        shown.forEach { match ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 14, 16, 14)
                setBackgroundColor(panel)
            }
            card.addView(label(match.home + "   ×   " + match.away, 17f, white, true))
            card.addView(label(match.state + "  •  " + match.time, 13f,
                if (match.state == "مباشرة") mint else muted))
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val favoriteButton = MaterialButton(this).apply {
                text = if (favorites.contains(match.id)) "★ محفوظة" else "☆ أضف للمفضلة"
                textSize = 10f
                setOnClickListener {
                    if (favorites.contains(match.id)) favorites.remove(match.id) else favorites.add(match.id)
                    getSharedPreferences("mchawe", MODE_PRIVATE).edit {
                        putStringSet("favorites", favorites.toSet())
                    }
                    showHome(filter, search.text.toString())
                }
            }
            val detailsButton = MaterialButton(this).apply {
                text = "التفاصيل"
                textSize = 10f
                setOnClickListener {
                    Toast.makeText(this@MainActivity,
                        "سيتم ربط تفاصيل وبيانات المباراة الحقيقية لاحقاً",
                        Toast.LENGTH_LONG).show()
                }
            }
            actions.addView(favoriteButton, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(detailsButton, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(actions)
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        }
        content.addView(list)
        content.addView(label("تنبيه: هذه مباريات تجريبية وليست جدولاً مباشراً.", 12f, muted))
        val searchButton = MaterialButton(this).apply {
            text = "بحث"
            setOnClickListener {
                showHome(filter, search.text.toString())
            }
        }
        content.addView(searchButton)
        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)
    }

    private fun showPlayer(streamUrl: String) {
        val root = baseLayout()
        root.addView(label("مشغل MCHAWE SPORTS", 22f, mint, true))
        val view = PlayerView(this)
        root.addView(view, LinearLayout.LayoutParams(-1, 240))
        player = ExoPlayer.Builder(this).build().also {
            view.player = it
            it.setMediaItem(MediaItem.fromUri(streamUrl))
            it.prepare()
            it.playWhenReady = true
        }
        setContentView(root)
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }
}

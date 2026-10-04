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
        val settingsButton = MaterialButton(this).apply {
            text = "الإعدادات"
            setOnClickListener { showSettings() }
        }
        content.addView(settingsButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10 })
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
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val searchButton = MaterialButton(this).apply {
            text = "بحث"
            setOnClickListener { showHome(filter, search.text.toString()) }
        }
        searchRow.addView(search, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = 8 })
        searchRow.addView(searchButton, LinearLayout.LayoutParams(-2, -2))
        content.addView(searchRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
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
                setOnClickListener { showMatchDetails(match, filter, search.text.toString()) }
            }
            actions.addView(favoriteButton, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(detailsButton, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(actions)
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        }
        content.addView(list)
        content.addView(label("تنبيه: هذه مباريات تجريبية وليست جدولاً مباشراً.", 12f, muted))
        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)
    }

    private fun showSettings() {
        val root = baseLayout()
        root.addView(MaterialButton(this).apply {
            text = "رجوع"
            setOnClickListener { showHome() }
        })
        root.addView(label("الإعدادات", 25f, mint, true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = 22; bottomMargin = 16 })

        val savedCount = favorites.size
        root.addView(label("المباريات المحفوظة: $savedCount", 16f, white, true))
        root.addView(label("MCHAWE SPORTS", 18f, mint, true).apply {
            setPadding(0, 22, 0, 8)
        })
        root.addView(label("الإصدار الأولي • تطبيق لمتابعة المباريات", 14f, muted))
        root.addView(label(
            "يعرض التطبيق حالياً بيانات تجريبية. ستتوفر المواعيد والنتائج والبث عند ربط مصادر موثوقة ومرخّصة.",
            13f, muted
        ).apply { setPadding(0, 10, 0, 18) })

        root.addView(MaterialButton(this).apply {
            text = "مسح جميع المفضلة"
            setOnClickListener {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("مسح المفضلة")
                    .setMessage("متأكد تريد حذف كل المباريات المحفوظة؟")
                    .setNegativeButton("إلغاء", null)
                    .setPositiveButton("مسح") { _, _ ->
                        favorites.clear()
                        getSharedPreferences("mchawe", MODE_PRIVATE).edit {
                            putStringSet("favorites", emptySet())
                        }
                        Toast.makeText(this@MainActivity, "تم مسح المفضلة", Toast.LENGTH_SHORT).show()
                        showSettings()
                    }
                    .show()
            }
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun showMatchDetails(match: Match, previousFilter: String, previousQuery: String) {
        val root = baseLayout()
        val back = MaterialButton(this).apply {
            text = "رجوع للمباريات"
            setOnClickListener { showHome(previousFilter, previousQuery) }
        }
        root.addView(back)
        root.addView(label("تفاصيل المباراة", 23f, mint, true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = 20; bottomMargin = 18 })

        val scorePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(18, 24, 18, 24)
            setBackgroundColor(panel)
        }
        scorePanel.addView(label(match.home, 21f, white, true).apply { gravity = Gravity.CENTER })
        scorePanel.addView(label("VS", 16f, mint, true).apply { gravity = Gravity.CENTER })
        scorePanel.addView(label(match.away, 21f, white, true).apply { gravity = Gravity.CENTER })
        scorePanel.addView(label(match.state + "  •  " + match.time, 14f, muted).apply {
            gravity = Gravity.CENTER
        })
        root.addView(scorePanel, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 18 })

        root.addView(label("معلومات المباراة", 18f, white, true))
        root.addView(label("البطولة: غير محددة", 14f, muted).apply {
            setPadding(0, 10, 0, 6)
        })
        root.addView(label("الملعب: غير متوفر", 14f, muted).apply {
            setPadding(0, 6, 0, 6)
        })
        root.addView(label("القناة الناقلة: غير متوفرة", 14f, muted).apply {
            setPadding(0, 6, 0, 14)
        })
        val note = label(
            "ملاحظة: تفاصيل هذه المباراة تجريبية. ستظهر البطولة والملعب والنتيجة والقناة عند ربط مصدر بيانات موثوق.",
            13f, muted
        )
        root.addView(note)
        val scroll = ScrollView(this).apply { addView(root) }
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

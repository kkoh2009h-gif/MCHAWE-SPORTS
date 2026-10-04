package com.mchawe.sports

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.android.material.button.MaterialButton

data class Match(val home: String, val away: String, val time: String, val state: String)

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
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome("الكل")
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

    private fun showHome(filter: String) {
        player?.release()
        player = null
        val root = baseLayout()
        root.addView(label("MCHAWE SPORTS", 24f, mint, true))
        root.addView(label("تابع المباريات لحظة بلحظة", 14f, muted))
        val liveCount = matches.count { it.state == "مباشرة" }
        val hero = TextView(this).apply {
            text = "⚽  مركز المباريات\n\n" + liveCount + " مباراة مباشرة الآن\n\nتغطية رياضية بواجهة عربية"
            textSize = 18f
            setTextColor(white)
            gravity = Gravity.RIGHT
            setPadding(18, 18, 18, 18)
            setBackgroundColor(panel)
        }
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 22; bottomMargin = 18 })
        val filters = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("الكل", "مباشرة", "قادمة", "منتهية").forEach { name ->
            val button = MaterialButton(this).apply {
                text = name
                textSize = 11f
                setOnClickListener { showHome(name) }
            }
            filters.addView(button, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(filters)
        root.addView(label("مباريات اليوم", 20f, white, true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = 20; bottomMargin = 10 })
        val shown = if (filter == "الكل") matches else matches.filter { it.state == filter }
        shown.forEach { match ->
            val card = TextView(this).apply {
                text = match.home + "   ×   " + match.away + "\n" +
                    match.state + "  •  " + match.time + "\nاضغط لعرض التفاصيل"
                textSize = 16f
                setTextColor(white)
                setPadding(16, 16, 16, 16)
                gravity = Gravity.RIGHT
                setBackgroundColor(panel)
                setOnClickListener {
                    Toast.makeText(this@MainActivity,
                        "بيانات المباراة والبث المرخّص ستُربط في مرحلة لاحقة",
                        Toast.LENGTH_LONG).show()
                }
            }
            root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        }
        root.addView(label("ملاحظة: المباريات المعروضة حالياً بيانات تجريبية.", 12f, muted))
        setContentView(root)
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

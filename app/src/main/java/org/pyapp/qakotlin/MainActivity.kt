package org.pyapp.qakotlin

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.EditText

/**
 * App native Kotlin (Android Views) — គ្មាន WebView។
 * សរសេរកូដនៅទីនេះ ហើយ Build APK ដើម្បីទទួល APK ពិត។
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad * 2, pad, pad)
            setBackgroundColor(Color.parseColor("#0D1117"))
        }

        val title = TextView(this).apply {
            text = "សួស្តី 👋"
            textSize = 26f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        // ឈ្មោះមិនត្រូវត្រូវនឹង property របស់ View (ឧ. hint) — Kotlin នឹងយក local
        // val ជំនួស setter ក្នុង apply { } → "Val cannot be reassigned" ពេល build
        val subtitle = TextView(this).apply {
            text = "app នេះសរសេរដោយ Kotlin"
            textSize = 15f
            setTextColor(Color.parseColor("#8B949E"))
            gravity = Gravity.CENTER
            setPadding(0, pad / 2, 0, pad)
        }

        val name = EditText(this).apply {
            hint = "ឈ្មោះរបស់អ្នក"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#8B949E"))
        }

        val btn = Button(this).apply {
            text = "ចុចទីនេះ"
            setOnClickListener {
                val who = name.text.toString().trim()
                title.text = if (who.isEmpty()) "បានចុច 🎉" else "សួស្តី $who 🎉"
                Toast.makeText(this@MainActivity, "Kotlin + Android", Toast.LENGTH_SHORT).show()
            }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(name, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(btn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }
}

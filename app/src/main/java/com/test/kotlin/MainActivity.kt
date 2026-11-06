package com.test.kotlin

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#0D1117"))
        }
        val title = TextView(this).apply {
            text = "សួស្តី Kotlin 👋"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val button = Button(this).apply {
            text = "ចុចទីនេះ"
            setOnClickListener {
                title.text = "បានចុច 🎉"
                Toast.makeText(this@MainActivity, "Kotlin", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(title)
        root.addView(button)
        setContentView(root)
    }
}

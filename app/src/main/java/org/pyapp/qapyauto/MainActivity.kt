package org.pyapp.qapyauto

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val message = try {
            if (!Python.isStarted()) Python.start(AndroidPlatform(this))
            Python.getInstance().getModule("main").callAttr("main").toString()
        } catch (e: Exception) {
            "Python error: " + (e.message ?: e.toString())
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 140, 56, 56)
            setBackgroundColor(Color.parseColor("#0D1117"))
        }
        val label = TextView(this).apply {
            text = message
            textSize = 18f
            setTextColor(Color.WHITE)
        }
        root.addView(label)
        setContentView(root)
    }
}

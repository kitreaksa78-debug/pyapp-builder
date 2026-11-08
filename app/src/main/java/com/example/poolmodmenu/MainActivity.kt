package com.example.poolmodmenu

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val requestOverlay = 701
    private val poolPackageName = "com.miniclip.eightballpool"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.parseColor("#0F172A"))
        }

        val title = TextView(this).apply {
            text = "8 Ball Pool Mod Menu & Multi-Bounce Aim"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val startBtn = Button(this).apply {
            text = "១. បើក Floating Mod Menu"
            setBackgroundColor(Color.parseColor("#16A34A"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                if (Settings.canDrawOverlays(this@MainActivity)) {
                    startService(Intent(this@MainActivity, ModMenuService::class.java))
                    Toast.makeText(this@MainActivity, "បានបើក Mod Menu", Toast.LENGTH_SHORT).show()
                } else {
                    val uri = Uri.parse("package:$packageName")
                    startActivityForResult(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri),
                        requestOverlay
                    )
                }
            }
        }

        val launchGameBtn = Button(this).apply {
            text = "២. ចូលលេង 8 Ball Pool"
            setBackgroundColor(Color.parseColor("#2563EB"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                if (Settings.canDrawOverlays(this@MainActivity)) {
                    startService(Intent(this@MainActivity, ModMenuService::class.java))
                }
                launch8BallPool()
            }
        }

        val stopBtn = Button(this).apply {
            text = "បិទ Mod Menu"
            setBackgroundColor(Color.parseColor("#DC2626"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                stopService(Intent(this@MainActivity, ModMenuService::class.java))
                Toast.makeText(this@MainActivity, "បានបិទ Mod Menu", Toast.LENGTH_SHORT).show()
            }
        }

        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 16, 0, 16)
        }

        root.addView(title)
        root.addView(startBtn, lp)
        root.addView(launchGameBtn, lp)
        root.addView(stopBtn, lp)

        setContentView(root)
    }

    private fun launch8BallPool() {
        val pm = packageManager
        val intent = pm.getLaunchIntentForPackage(poolPackageName)
        if (intent != null) {
            startActivity(intent)
        } else {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$poolPackageName")))
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$poolPackageName")))
            }
        }
    }
}

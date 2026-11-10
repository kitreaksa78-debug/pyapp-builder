package com.example.poolmod

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnStart = findViewById<Button>(R.id.btnStart)
        btnStart.setOnClickListener {
            if (checkOverlayPermission()) {
                startOverlayService()
            }
        }
    }

    private fun checkOverlayPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, 1001)
                Toast.makeText(this, "សូមបើកសិទ្ធិ Display over other apps ជាមុនសិន!", Toast.LENGTH_LONG).show()
                return false
            }
        }
        return true
    }

    private fun startOverlayService() {
        val serviceIntent = Intent(this, PoolModService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Toast.makeText(this, "Aim Guideline កម្រិតខ្ពស់បានបើកដំណើរការ!", Toast.LENGTH_SHORT).show()
        finish()
    }
}

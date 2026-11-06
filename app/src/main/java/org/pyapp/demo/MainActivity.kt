package org.pyapp.demo

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
                    
                            val layout = LinearLayout(this).apply {
                                        orientation = LinearLayout.VERTICAL
                                                    setPadding(50, 50, 50, 50)
                                                            }

                                                                    val btnStart = Button(this).apply {
                                                                                text = "បើកខ្សែរត់ (Start Overlay)"
                                                                                            setOnClickListener {
                                                                                                            if (Settings.canDrawOverlays(this@MainActivity)) {
                                                                                                                                startOverlay()
                                                                                                                                                } else {
                                                                                                                                                                    // បើមិនទាន់មានសិទ្ធិ សូមបើកទៅកាន់ការកំណត់
                                                                                                                                                                                        val intent = Intent(
                                                                                                                                                                                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                                                                                                                                                                                                        Uri.parse("package:$packageName")
                                                                                                                                                                                                                                                            )
                                                                                                                                                                                                                                                                                startActivity(intent)
                                                                                                                                                                                                                                                                                                    Toast.makeText(this@MainActivity, "សូមចុច Allow ដើម្បីអនុញ្ញាត", Toast.LENGTH_LONG).show()
                                                                                                                                                                                                                                                                                                                    }
                                                                                                                                                                                                                                                                                                                                }
                                                                                                                                                                                                                                                                                                                                        }

                                                                                                                                                                                                                                                                                                                                                layout.addView(btnStart)
                                                                                                                                                                                                                                                                                                                                                        setContentView(layout)
                                                                                                                                                                                                                                                                                                                                                            }

                                                                                                                                                                                                                                                                                                                                                                private fun startOverlay() {
                                                                                                                                                                                                                                                                                                                                                                        val serviceIntent = Intent(this, OverlayService::class.java)
                                                                                                                                                                                                                                                                                                                                                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                                                                                                                                                                                                                                                                                                                                                            startForegroundService(serviceIntent)
                                                                                                                                                                                                                                                                                                                                                                                                    } else {
                                                                                                                                                                                                                                                                                                                                                                                                                startService(serviceIntent)
                                                                                                                                                                                                                                                                                                                                                                                                                        }
                                                                                                                                                                                                                                                                                                                                                                                                                                // បិទ App ដើម្បីឱ្យអ្នកអាចទៅលេង App ផ្សេងបាន
                                                                                                                                                                                                                                                                                                                                                                                                                                        finish()
                                                                                                                                                                                                                                                                                                                                                                                                                                            }
                                                                                                                                                                                                                                                                                                                                                                                                                                            }
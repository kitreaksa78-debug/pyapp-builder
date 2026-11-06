package com.test.androidx
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val n = NotificationCompat.Builder(this, "x").setContentTitle("Demo").build()
        setContentView(android.widget.TextView(this).apply { text = n.channelId ?: "ok" })
    }
}

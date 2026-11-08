package com.example.teststream

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var tokenInput: EditText
    private lateinit var chatIdInput: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.parseColor("#0F172A"))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "Test Screen Stream & Bot Notifier"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        // Disclaimer Box
        val disclaimerText = TextView(this).apply {
            text = "⚠️ ការបញ្ជាក់ (Disclaimer):\nកម្មវិធីនេះត្រូវបានបង្កើតឡើងសម្រាប់គោលបំណងសិក្សាបច្ចេកវិទ្យា និងធ្វើតេស្តសាកល្បងផ្ទាល់ខ្លួន (Educational & Testing Purpose Only) ប៉ុណ្ណោះ។ មិនមានការប្រមូលទិន្នន័យ ឬយកទៅប្រើប្រាស់ក្នុងทางអាក្រក់ឡើយ។"
            textSize = 12f
            setTextColor(Color.parseColor("#FBBF24"))
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(20, 20, 20, 20)
        }

        val space = TextView(this).apply { height = 24 }

        tokenInput = EditText(this).apply {
            hint = "បញ្ចូល Telegram Bot Token"
            setHintTextColor(Color.parseColor("#94A3B8"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(20, 20, 20, 20)
        }

        chatIdInput = EditText(this).apply {
            hint = "បញ្ចូល Telegram Chat ID"
            setHintTextColor(Color.parseColor("#94A3B8"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(20, 20, 20, 20)
        }

        val sendBtn = Button(this).apply {
            text = "ផ្ញើ Test Link ចូល Telegram Bot"
            setBackgroundColor(Color.parseColor("#2563EB"))
            setTextColor(Color.WHITE)
            setPadding(0, 20, 0, 20)
            setOnClickListener {
                val token = tokenInput.text.toString().trim()
                val chatId = chatIdInput.text.toString().trim()
                if (token.isNotEmpty() && chatId.isNotEmpty()) {
                    sendDisclaimerAndLink(token, chatId)
                } else {
                    Toast.makeText(this@MainActivity, "សូមបញ្ចូល Token និង Chat ID ឱ្យបានគ្រប់គ្រាន់!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        statusText = TextView(this).apply {
            text = "ស្ថានភាព៖ រួចរាល់..."
            textSize = 13f
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 16, 0, 0)
            gravity = Gravity.CENTER
        }

        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 16) }

        root.addView(title)
        root.addView(disclaimerText, lp)
        root.addView(space)
        root.addView(tokenInput, lp)
        root.addView(chatIdInput, lp)
        root.addView(sendBtn, lp)
        root.addView(statusText)

        setContentView(root)
    }

    private fun sendDisclaimerAndLink(token: String, chatId: String) {
        statusText.text = "កំពុងផ្ញើសំណើ..."
        thread {
            try {
                val testUrl = "https://example.com/test-browser-view"
                val message = "🧪 [TEST APP NOTIFICATION]\n\n⚠️ ការបញ្ជាក់៖ នេះគឺជាការបង្កើត App សាកល្បងបច្ចេកវិទ្យាប៉ុណ្ណោះ (Educational Test).\n\n🔗 Test Browser Link:\n$testUrl"
                val encoded = URLEncoder.encode(message, "UTF-8")
                val apiURL = "https://api.telegram.org/bot$token/sendMessage?chat_id=$chatId&text=$encoded"

                val url = URL(apiURL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                val code = conn.responseCode
                runOnUiThread {
                    if (code == 200) {
                        statusText.text = "ជោគជ័យ! បានផ្ញើសារសាកល្បងនិង Link ចូល Telegram ហើយ។"
                        Toast.makeText(this, "ផ្ញើជោគជ័យ!", Toast.LENGTH_LONG).show()
                    } else {
                        statusText.text = "កំហុស Response Code: $code"
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "កំហុស: ${e.localizedMessage}"
                }
            }
        }
    }
}

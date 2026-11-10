package org.pyapp.aiphone

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * AI Bubble — ប៉ូតុង "AI" អណ្តែតលើ app ដទៃ។
 * ចុច → បើកផ្ទាំងបញ្ជាតូចអណ្តែតលើអេក្រង់ (មិនបើក AI Phone ពេញ)។
 * អូស → ផ្លាស់ទីប៉ូតុង។ សង្កត់យូរ → បិទ bubble។
 */
class AiBubbleService : Service() {

    companion object {
        @Volatile
        var running = false
            private set
    }

    private lateinit var wm: WindowManager
    private lateinit var lp: WindowManager.LayoutParams
    private var root: LinearLayout? = null
    private var prefs: SharedPreferences? = null
    private val ui = Handler(Looper.getMainLooper())
    private var longPress: Runnable? = null

    // ── Floating command panel ────────────────────────────────
    private var panel: LinearLayout? = null
    private var panelLp: WindowManager.LayoutParams? = null
    private var panelLog: TextView? = null
    private var panelStatus: TextView? = null
    private var panelInput: EditText? = null
    private var panelScroller: ScrollView? = null
    private val logLines = ArrayList<String>()
    private var panelBusy = false
    private var panelFocusing = false
    private var agentSteps = 0
    private val MAX_AGENT_STEPS = 4
    private var modelCache: List<String> = emptyList()
    private val history = ArrayList<Pair<String, String>>()
    private val CHAT_MODELS = listOf(
        "openai/gpt-oss-120b",
        "openai/gpt-oss-20b",
        "qwen/qwen3.8-27b",
        "openai/gpt-oss-safeguard-20b"
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        prefs = getSharedPreferences("aiphone", Context.MODE_PRIVATE)
        startForegroundCompat()
        showBubble()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun round(color: Int, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.setColor(color)
        g.cornerRadius = dp(radiusDp).toFloat()
        return g
    }

    // ═════════════════════════════════════════════════════════
    //  Bubble
    // ═════════════════════════════════════════════════════════
    private fun showBubble() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(dp(4), dp(4), dp(4), dp(4))

        val circle = TextView(this)
        circle.text = "AI"
        circle.setTextColor(Color.WHITE)
        circle.textSize = 17f
        circle.typeface = Typeface.DEFAULT_BOLD
        circle.gravity = Gravity.CENTER
        val cg = GradientDrawable()
        cg.shape = GradientDrawable.OVAL
        cg.setColor(0xFFE8A33D.toInt())
        cg.setStroke(dp(3), 0xFFFFE0A3.toInt())
        circle.background = cg
        val csz = dp(68)
        box.addView(circle, LinearLayout.LayoutParams(csz, csz))

        val auto = TextView(this)
        auto.text = "auto"
        auto.setTextColor(Color.WHITE)
        auto.textSize = 9f
        auto.typeface = Typeface.DEFAULT_BOLD
        auto.gravity = Gravity.CENTER
        auto.setPadding(dp(9), dp(2), dp(9), dp(2))
        val ag = GradientDrawable()
        ag.cornerRadius = dp(12).toFloat()
        ag.setColor(0xFFD93025.toInt())
        auto.background = ag
        val model = prefs?.getString("model", "auto") ?: "auto"
        auto.visibility = if (model.isEmpty() || model.equals("auto", true)) View.VISIBLE else View.GONE
        box.addView(auto, LinearLayout.LayoutParams(-2, -2).also {
            it.gravity = Gravity.CENTER_HORIZONTAL
            it.topMargin = dp(3)
        })

        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = dp(10)
        lp.y = dp(260)

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        box.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    longPress?.let { ui.removeCallbacks(it) }
                    val r = Runnable { if (!moved) stopSelf() }
                    longPress = r
                    ui.postDelayed(r, 900)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt()
                    val dy = (e.rawY - downY).toInt()
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved) {
                        lp.x = startX + dx
                        lp.y = startY + dy
                        try { wm.updateViewLayout(box, lp) } catch (_: Throwable) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPress?.let { ui.removeCallbacks(it) }
                    longPress = null
                    if (!moved && e.actionMasked == MotionEvent.ACTION_UP) togglePanel()
                    true
                }
                else -> false
            }
        }

        root = box
        try {
            wm.addView(box, lp)
        } catch (_: Throwable) {
            stopSelf()
        }
    }

    private fun togglePanel() {
        if (panel != null) hidePanel() else showPanel()
    }

    // ═════════════════════════════════════════════════════════
    //  Floating command panel (no Activity, no app takeover)
    // ═════════════════════════════════════════════════════════
    private fun showPanel() {
        if (panel != null) return

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.background = round(0xF21A140C.toInt(), 22)
        box.setPadding(dp(14), dp(10), dp(14), dp(10))
        box.elevation = dp(10).toFloat()

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL

        val title = TextView(this)
        title.text = "AI Phone"
        title.setTextColor(0xFFF0C75E.toInt())
        title.textSize = 16f
        title.typeface = Typeface.DEFAULT_BOLD
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))

        val auto = TextView(this)
        val model = prefs?.getString("model", "auto") ?: "auto"
        if (model.isEmpty() || model.equals("auto", true)) {
            auto.text = "auto"
            auto.setTextColor(Color.WHITE)
            auto.textSize = 9f
            auto.typeface = Typeface.DEFAULT_BOLD
            auto.setPadding(dp(8), dp(2), dp(8), dp(2))
            auto.background = round(0xFFD93025.toInt(), 10)
            header.addView(auto, LinearLayout.LayoutParams(-2, -2).also { it.rightMargin = dp(4) })
        }

        val gear = TextView(this)
        gear.text = "⚙"
        gear.setTextColor(0xFFF0C75E.toInt())
        gear.textSize = 17f
        gear.setPadding(dp(8), 0, dp(8), 0)
        gear.isClickable = true
        gear.setOnClickListener { openFullApp() }
        header.addView(gear)

        val close = TextView(this)
        close.text = "✕"
        close.setTextColor(0xFFF0C75E.toInt())
        close.textSize = 17f
        close.setPadding(dp(8), 0, dp(2), 0)
        close.isClickable = true
        close.setOnClickListener { hidePanel() }
        header.addView(close)
        box.addView(header)

        val status = TextView(this)
        status.text = "សរសេរបញ្ជា — ខ្ញុំមើលអេក្រង់ និងចុចជំនួសអ្នក"
        status.setTextColor(0xFF9C8B6A.toInt())
        status.textSize = 11f
        status.setPadding(0, dp(2), 0, dp(5))
        panelStatus = status
        box.addView(status)

        val scroller = ScrollView(this)
        scroller.isVerticalScrollBarEnabled = false
        val log = TextView(this)
        log.setTextColor(0xFFF3E6CB.toInt())
        log.textSize = 13f
        log.setLineSpacing(dp(2).toFloat(), 1f)
        log.setTextIsSelectable(true)
        log.setPadding(dp(2), dp(2), dp(2), dp(2))
        scroller.addView(log)
        box.addView(scroller, LinearLayout.LayoutParams(-1, dp(150)))
        panelLog = log
        panelScroller = scroller

        if (logLines.isEmpty()) {
            logLines.add("សួស្តី! ខ្ញុំអណ្តែតនៅលើអេក្រង់។\nសរសេរ បើក… / ចុច… / អានអេក្រង់")
            if ((prefs?.getString("key", "") ?: "").isEmpty()) {
                logLines.add("⚠ ដាក់ Groq API key ក្នុង ⚙ សិន។")
            }
        }
        renderLog()

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(6), 0, 0)

        val input = EditText(this)
        input.hint = "សរសេរ បញ្ជា…"
        input.setTextColor(0xFFF5E7C8.toInt())
        input.setHintTextColor(0xFF7D6E52.toInt())
        input.textSize = 14f
        input.setSingleLine(true)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.background = round(0xFF241C11.toInt(), 28)
        input.setPadding(dp(12), dp(9), dp(12), dp(9))
        input.setOnClickListener { focusPanelInput() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitPanel()
                true
            } else false
        }
        panelInput = input
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f).also { it.rightMargin = dp(7) })

        val send = TextView(this)
        send.text = "➤"
        send.setTextColor(0xFF1A1208.toInt())
        send.textSize = 18f
        send.gravity = Gravity.CENTER
        send.background = round(0xFFD9A441.toInt(), 24)
        send.isClickable = true
        send.setOnClickListener { submitPanel() }
        row.addView(send, LinearLayout.LayoutParams(dp(46), dp(42)))
        box.addView(row)

        val hint = TextView(this)
        hint.text = "បញ្ជា: បើក… • ចុច… • វាយ… • អានអេក្រង់ • back • home"
        hint.setTextColor(0xFF9C8B6A.toInt())
        hint.textSize = 10f
        hint.setPadding(0, dp(6), 0, 0)
        box.addView(hint)

        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE
        val dm = resources.displayMetrics
        val width = minOf(dm.widthPixels - dp(28), dp(360))
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = dp(100)
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        panelLp = params
        panel = box
        try {
            wm.addView(box, params)
        } catch (_: Throwable) {
            panel = null
            panelLp = null
            panelLog = null
            panelStatus = null
            panelInput = null
            panelScroller = null
        }
    }

    private fun hidePanel() {
        val p = panel ?: return
        panel = null
        panelLp = null
        panelLog = null
        panelStatus = null
        panelInput = null
        panelScroller = null
        try { wm.removeView(p) } catch (_: Throwable) {}
    }

    private fun openFullApp() {
        hidePanel()
        try {
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {}
    }

    private fun focusPanelInput() {
        if (panelFocusing) return
        val p = panel ?: return
        val params = panelLp ?: return
        val et = panelInput ?: return
        panelFocusing = true
        try {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            wm.updateViewLayout(p, params)
            et.requestFocus()
            ui.postDelayed({
                try {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
                } catch (_: Throwable) {}
            }, 150)
        } catch (_: Throwable) {
        } finally {
            panelFocusing = false
        }
    }

    private fun blurPanelInput() {
        val p = panel ?: return
        val params = panelLp ?: return
        try {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            wm.updateViewLayout(p, params)
            val et = panelInput ?: return
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(et.windowToken, 0)
            et.clearFocus()
        } catch (_: Throwable) {}
    }

    private fun submitPanel() {
        val et = panelInput ?: return
        val text = et.text.toString().trim()
        if (text.isEmpty()) return
        et.setText("")
        blurPanelInput()
        addLog("អ្នក: $text")
        handlePanelCommand(text)
    }

    private fun setPanelStatus(s: String) {
        panelStatus?.text = s
    }

    private fun renderLog() {
        panelLog?.text = logLines.joinToString("\n")
        panelScroller?.post { panelScroller?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addLog(line: String) {
        if (line.isBlank()) return
        logLines.add(line)
        while (logLines.size > 60) logLines.removeAt(0)
        renderLog()
    }

    // ═════════════════════════════════════════════════════════
    //  Command router
    // ═════════════════════════════════════════════════════════
    private fun handlePanelCommand(text: String) {
        val lower = text.lowercase(Locale.ROOT)
        fun has(vararg k: String): Boolean = k.any { lower.contains(it) }

        val mass = Regex("(follow|like|subscribe|តាមដាន|ចូលចិត្ត|ជាវ)[^0-9]{0,40}([0-9]{2,})", RegexOption.IGNORE_CASE).find(text)
        if (mass != null && (mass.groupValues[2].toIntOrNull() ?: 0) >= 5) {
            addLog("AI: ខ្ញុំមិនធ្វើ follow/like មនុស្សច្រើនស្វ័យប្រវត្តិទេ — វាបំពានច្បាប់ និងអាចធ្វើឲ្យគណនីត្រូវ ban។")
            return
        }

        val openPrefix = listOf("បើក ", "open ", "ចូល ", "ចាប់ផ្តើម ", "play ", "លេង ")
            .firstOrNull { lower.startsWith(it) }

        val local: String? = when {
            lower == "បិទ" || lower == "close" || lower == "exit" -> {
                hidePanel()
                return
            }
            has("ម៉ោង", "time") && !has("facebook") -> {
                val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                "ឥឡូវម៉ោង $t"
            }
            has("ថ្ម", "battery") -> {
                val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                "ថ្មនៅ ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%"
            }
            has("ការកំណត់", "settings") -> {
                openSettings()
                "បើក Settings ✓"
            }
            has("កាមេរ៉ា", "camera") -> {
                openCamera()
                "បើកកាមេរ៉ា ✓"
            }
            has("accessibility", "ការចូលប្រើ") -> {
                openAccessibilitySettings()
                "បើកការកំណត់ Accessibility — សូមបើក 'AI Phone' ✓"
            }
            has("អានអេក្រង់", "read screen", "មើលអេក្រង់") -> {
                val s = AutoClickAccessibilityService.instance?.screenText(600).orEmpty()
                if (s.isBlank()) "Accessibility មិនទាន់បើក ឬអេក្រង់គ្មានអត្ថបទ"
                else "អេក្រង់: $s"
            }
            lower.startsWith("ចុច ") || lower.startsWith("click ") -> {
                val target = if (lower.startsWith("click ")) text.substringAfter(" ").trim()
                else text.substringAfter(" ").trim()
                val svc = AutoClickAccessibilityService.instance
                when {
                    target.isEmpty() -> "សរសេរអត្ថបទដែលត្រូវចុច"
                    svc == null -> "Accessibility មិនទាន់បើក"
                    svc.clickByText(target) -> "បានចុច '$target' ✓"
                    else -> "មិនឃើញ '$target' លើអេក្រង់"
                }
            }
            lower.startsWith("វាយ ") || lower.startsWith("type ") -> {
                val txt = if (lower.startsWith("type ")) text.substringAfter(" ").trim()
                else text.substringAfter(" ").trim()
                val svc = AutoClickAccessibilityService.instance
                when {
                    txt.isEmpty() -> "សរសេរអត្ថបទដែលត្រូវវាយ"
                    svc == null -> "Accessibility មិនទាន់បើក"
                    svc.typeText(txt) -> "បានវាយ '$txt' ✓"
                    else -> "វាយអត្ថបទមិនបាន — សូមចុចប្រអប់វាយក្នុង app សិន"
                }
            }
            has("scroll down", "រមូរចុះ", "រមូរចុះក្រោម") -> {
                if (AutoClickAccessibilityService.instance?.scrollDown() == true) "រមូរចុះ ✓" else "Accessibility មិនទាន់បើក"
            }
            has("scroll up", "រមូរឡើង") -> {
                if (AutoClickAccessibilityService.instance?.scrollUp() == true) "រមូរឡើង ✓" else "Accessibility មិនទាន់បើក"
            }
            has("back", "ថយក្រោយ") -> {
                if (AutoClickAccessibilityService.instance?.goBack() == true) "ថយក្រោយ ✓" else "Accessibility មិនទាន់បើក"
            }
            has("home", "ទំព័រដើម") -> {
                if (AutoClickAccessibilityService.instance?.goHome() == true) "ទំព័រដើម ✓" else "Accessibility មិនទាន់បើក"
            }
            openPrefix != null -> {
                if (has("follow", "search", "click", "comment", "post", "like", "message", "ចុច", "ស្វែងរក", "តាមដាន")) {
                    null
                } else {
                    val name = text.substring(openPrefix.length).trim()
                    if (name.isEmpty()) {
                        "សរសេរឈ្មោះ app បន្ទាប់ពី '${openPrefix.trim()}'"
                    } else if (openAnyApp(name)) {
                        "បើក $name ឲ្យហើយ ✓"
                    } else {
                        "រកមិនឃើញ app '$name' — ពិនិត្យឈ្មោះ ឬដំឡើងវា"
                    }
                }
            }
            else -> null
        }

        if (local != null) {
            setPanelStatus("")
            addLog("AI: $local")
            return
        }

        if (panelBusy) return
        panelBusy = true
        agentSteps = 0
        setPanelStatus("កំពុងគិត…")
        askGroq(text) { ans -> handleAiAnswer(text, ans) }
    }

    private fun handleAiAnswer(goal: String, ans: String) {
        panelBusy = false
        val res = executeAiCommands(ans)
        if (res.log.isNotBlank()) addLog(res.log)
        addLog("AI: ${res.reply}")
        history.add("user" to goal)
        history.add("assistant" to ans)
        if (res.scan && agentSteps < MAX_AGENT_STEPS) {
            agentSteps++
            setPanelStatus("កំពុងមើលអេក្រង់បន្ត… ($agentSteps/$MAX_AGENT_STEPS)")
            ui.postDelayed({ continueAgent(goal) }, 900)
        } else {
            agentSteps = 0
            setPanelStatus("")
        }
    }

    private fun continueAgent(goal: String) {
        if (panelBusy) return
        if (agentSteps >= MAX_AGENT_STEPS) {
            setPanelStatus("")
            return
        }
        val screen = AutoClickAccessibilityService.instance?.screenText(600).orEmpty()
        if (screen.isBlank()) {
            addLog("AI: Accessibility មិនទាន់បើក — មើលអេក្រង់បន្តមិនបាន")
            agentSteps = 0
            setPanelStatus("")
            return
        }
        panelBusy = true
        setPanelStatus("កំពុងគិត… (${agentSteps + 1}/$MAX_AGENT_STEPS)")
        askGroq("$goal\n(បន្ត — មើល Screen ថ្មី ហើយបញ្ជាជំហានបន្ទាប់)") { ans ->
            handleAiAnswer(goal, ans)
        }
    }

    private data class CmdResult(val reply: String, val scan: Boolean, val log: String)

    private fun executeAiCommands(text: String): CmdResult {
        val spoken = mutableListOf<String>()
        val logs = mutableListOf<String>()
        var scan = false
        for (raw in text.lines()) {
            val line = raw.trim().removePrefix("- ").trim().trim('`').trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("OPEN:", ignoreCase = true) -> {
                    logs.add("📱 $line")
                    val what = line.substringAfter(":").trim().lowercase(Locale.ROOT)
                    when {
                        what.contains("facebook") -> openFacebook()
                        what.contains("youtube") -> openAppPackage("com.google.android.youtube")
                        what.contains("camera") -> openCamera()
                        what.contains("settings") -> openSettings()
                        what.contains("playstore") -> openUrl("market://search?q=apps")
                        what.contains("chrome") -> openAppPackage("com.android.chrome")
                        what.contains("phone") -> openUrl("tel:")
                        what.contains("messages") -> {
                            try {
                                startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: Throwable) {}
                        }
                        else -> if (!openAnyApp(what)) spoken.add("រកមិនឃើញ app '$what'")
                    }
                    scan = true
                    break
                }
                line.startsWith("CLICK:", ignoreCase = true) -> {
                    logs.add("👆 $line")
                    val target = line.substringAfter(":").trim()
                    val svc = AutoClickAccessibilityService.instance
                    if (svc == null) spoken.add("Accessibility មិនទាន់បើក")
                    else if (!svc.clickByText(target)) spoken.add("មិនឃើញ '$target'")
                }
                line.startsWith("TYPE:", ignoreCase = true) -> {
                    logs.add("⌨️ $line")
                    val txt = line.substringAfter(":").trim()
                    val svc = AutoClickAccessibilityService.instance
                    if (svc == null) spoken.add("Accessibility មិនទាន់បើក")
                    else if (!svc.typeText(txt)) spoken.add("វាយអត្ថបទមិនបាន")
                }
                line.equals("SCROLL_DOWN", ignoreCase = true) -> {
                    logs.add("↕️ SCROLL_DOWN")
                    AutoClickAccessibilityService.instance?.scrollDown()
                }
                line.equals("SCROLL_UP", ignoreCase = true) -> {
                    logs.add("↕️ SCROLL_UP")
                    AutoClickAccessibilityService.instance?.scrollUp()
                }
                line.equals("BACK", ignoreCase = true) -> {
                    logs.add("⬅️ BACK")
                    AutoClickAccessibilityService.instance?.goBack()
                }
                line.equals("HOME", ignoreCase = true) -> {
                    logs.add("🏠 HOME")
                    AutoClickAccessibilityService.instance?.goHome()
                }
                line.equals("SCAN", ignoreCase = true) -> {
                    logs.add("🔄 SCAN")
                    scan = true
                }
                else -> spoken.add(line)
            }
        }
        val out = spoken.joinToString(" ").trim()
        return CmdResult(if (out.isEmpty()) "រួចរាល់ ✓" else out, scan, logs.joinToString("\n"))
    }

    // ═════════════════════════════════════════════════════════
    //  Phone actions
    // ═════════════════════════════════════════════════════════
    private fun launch(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    private fun openUrl(url: String) {
        try {
            launch(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (_: Throwable) {}
    }

    private fun openAppPackage(pkg: String): Boolean {
        val i = try { packageManager.getLaunchIntentForPackage(pkg) } catch (_: Throwable) { null } ?: return false
        return try {
            launch(i)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun openFacebook() {
        for (p in listOf("com.facebook.katana", "com.facebook.lite")) {
            if (openAppPackage(p)) return
        }
        openUrl("https://m.facebook.com")
    }

    private fun openSettings() {
        try { launch(Intent(Settings.ACTION_SETTINGS)) } catch (_: Throwable) {}
    }

    private fun openAccessibilitySettings() {
        try { launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: Throwable) {}
    }

    private fun openCamera() {
        try { launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) } catch (_: Throwable) {}
    }

    private fun openAnyApp(query: String): Boolean {
        var q = query.trim()
        val prefixes = listOf("game ", "app ", "the ", "ហ្គេម ", "កម្មវិធី ")
        var changed = true
        while (changed) {
            changed = false
            for (p in prefixes) {
                if (q.startsWith(p, true)) {
                    q = q.substring(p.length).trim()
                    changed = true
                }
            }
        }
        if (q.isEmpty()) return false
        val lower = q.lowercase(Locale.ROOT)
        val qn = lower.replace(Regex("[^a-z0-9]"), "")

        val known = when {
            qn.contains("8ball") || qn.contains("8pall") || qn.contains("8pool") ||
                qn.contains("miniclip") || qn == "pool" -> listOf("com.miniclip.eightballpool")
            qn.contains("facebook") -> listOf("com.facebook.katana", "com.facebook.lite")
            qn.contains("youtube") -> listOf("com.google.android.youtube")
            qn.contains("tiktok") -> listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
            qn.contains("instagram") -> listOf("com.instagram.android")
            qn.contains("telegram") -> listOf("org.telegram.messenger")
            qn.contains("messenger") -> listOf("com.facebook.orca")
            qn.contains("chrome") -> listOf("com.android.chrome")
            else -> emptyList()
        }
        for (pkg in known) {
            if (openAppPackage(pkg)) return true
        }

        if (q.contains(".")) {
            if (openAppPackage(q)) return true
        }

        return try {
            val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = packageManager.queryIntentActivities(main, 0)
            var best: android.content.pm.ResolveInfo? = null
            var bestScore = 0
            for (ai in apps) {
                if (ai.activityInfo.packageName == packageName) continue
                val label = try {
                    ai.loadLabel(packageManager).toString().lowercase(Locale.ROOT)
                } catch (_: Throwable) { "" }
                val pkg = ai.activityInfo.packageName.lowercase(Locale.ROOT)
                val ln = label.replace(Regex("[^a-z0-9]"), "")
                val pn = pkg.replace(Regex("[^a-z0-9]"), "")
                val score = when {
                    label == lower || pkg == lower || (qn.isNotEmpty() && ln == qn) || (qn.isNotEmpty() && pn == qn) -> 100
                    label.startsWith(lower) || (qn.isNotEmpty() && ln.startsWith(qn)) -> 80
                    pkg.startsWith(lower) || (qn.isNotEmpty() && pn.startsWith(qn)) -> 70
                    label.contains(lower) || (qn.isNotEmpty() && ln.contains(qn)) -> 60
                    (qn.length >= 5 && ln.length >= 5 && editDistance(qn, ln) <= 1) -> 55
                    pkg.contains(lower) || (qn.isNotEmpty() && pn.contains(qn)) -> 50
                    else -> 0
                }
                if (score > bestScore) {
                    bestScore = score
                    best = ai
                }
            }
            val found = best ?: return false
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(found.activityInfo.packageName, found.activityInfo.name)
            launch(i)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val swap = prev
            prev = cur
            cur = swap
        }
        return prev[b.length]
    }

    // ═════════════════════════════════════════════════════════
    //  Groq
    // ═════════════════════════════════════════════════════════
    private val panelSysPrompt =
        "You are AI Phone, an on-device Android agent that CONTROLS the user's phone through accessibility. " +
            "You can SEE the current screen (Screen: ...). Reply in Khmer or English, very short. " +
            "If the user asks to open or play a real app/game, output OPEN:<app or game name> first. " +
            "Khmer verbs ចូល / ចាប់ផ្តើម / លេង + an app name also mean OPEN; do not explain that you cannot open it. " +
            "To operate inside an app, output one command per line based on the visible Screen text: " +
            "CLICK:<visible text> ; TYPE:<text> ; SCROLL_DOWN ; SCROLL_UP ; BACK ; HOME ; SCAN. " +
            "Use SCAN when you need the next screen before deciding; the app will send a fresh Screen and you continue. " +
            "Only CLICK/TYPE text that appears in Screen. If the target is not visible, SCAN or SCROLL. " +
            "Never invent actions. Never perform mass actions such as following, liking, subscribing or messaging many people; refuse those. " +
            "Put a 1-sentence confirmation at the end."

    private fun fetchModels(key: String): List<String> {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL("https://api.groq.com/openai/v1/models").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            if (conn.responseCode !in 200..299) return emptyList()
            val body = conn.inputStream.bufferedReader().readText()
            val arr = JSONObject(body).optJSONArray("data") ?: return emptyList()
            val ids = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val id = arr.optJSONObject(i)?.optString("id", "") ?: ""
                if (id.isEmpty()) continue
                val low = id.lowercase(Locale.ROOT)
                if (low.contains("whisper") || low.contains("tts") || low.contains("guard") ||
                    low.contains("orpheus") || low.contains("compound") || low.contains("playai")
                ) continue
                ids.add(id)
            }
            val preferred = listOf(
                "openai/gpt-oss-120b", "openai/gpt-oss-20b", "qwen/qwen3.8-27b",
                "openai/gpt-oss-safeguard-20b", "llama-3.3-70b-versatile",
                "llama-3.1-8b-instant"
            )
            ids.sortedWith(compareBy { preferred.indexOf(it).let { i -> if (i < 0) 99 else i } })
        } catch (_: Throwable) {
            emptyList()
        } finally {
            conn?.disconnect()
        }
    }

    private fun askGroq(text: String, done: (String) -> Unit) {
        val key = prefs?.getString("key", "").orEmpty()
        if (key.isEmpty()) {
            done("សូមដាក់ Groq API key ក្នុង ⚙ សិន។ (console.groq.com/keys)")
            return
        }
        val modelPref = (prefs?.getString("model", "auto") ?: "auto").trim()
        val auto = modelPref.isEmpty() || modelPref.equals("auto", true)
        val modelsToTry = if (auto) {
            if (modelCache.isEmpty()) modelCache = fetchModels(key)
            modelCache.ifEmpty { CHAT_MODELS }
        } else listOf(modelPref)
        val sys = (prefs?.getString("sysprompt", "") ?: "").ifEmpty { panelSysPrompt }
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", sys))
        for (h in history.takeLast(8)) {
            msgs.put(JSONObject().put("role", h.first).put("content", h.second))
        }
        val screen = AutoClickAccessibilityService.instance?.screenText(400).orEmpty()
        val userMsg = if (screen.isNotBlank()) "$text\nScreen: $screen" else text
        msgs.put(JSONObject().put("role", "user").put("content", userMsg))
        val basePayload = JSONObject()
            .put("messages", msgs)
            .put("temperature", 0.5)
            .put("max_tokens", 300)

        Thread {
            var lastErr = ""
            for (m in modelsToTry) {
                var conn: HttpURLConnection? = null
                try {
                    val payload = JSONObject(basePayload.toString()).put("model", m)
                    conn = URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Authorization", "Bearer $key")
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.connectTimeout = 30000
                    conn.readTimeout = 60000
                    conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                    val code = conn.responseCode
                    val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                        ?.bufferedReader()?.readText() ?: ""
                    if (code !in 200..299) {
                        lastErr = "Groq error $code: ${body.take(100)}"
                        if (code == 400 || code == 404 || code == 422 || code == 429 || code >= 500) continue
                        ui.post { done(lastErr) }
                        return@Thread
                    }
                    val ans = JSONObject(body).getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message").optString("content", "").trim()
                    ui.post { done(ans.ifEmpty { "…" }) }
                    return@Thread
                } catch (e: Throwable) {
                    lastErr = "បណ្តាញមានបញ្ហា: ${e.message}"
                } finally {
                    conn?.disconnect()
                }
            }
            ui.post { done(lastErr.ifEmpty { "…" }) }
        }.start()
    }

    // ═════════════════════════════════════════════════════════
    //  Foreground notification
    // ═════════════════════════════════════════════════════════
    private fun startForegroundCompat() {
        val ch = "ai_bubble"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(ch, "AI Bubble", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val pi = PendingIntent.getActivity(this, 3, open, flags)
        val n = NotificationCompat.Builder(this, ch)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("AI Phone")
            .setContentText("ចុច AI ដើម្បីបើកផ្ទាំងបញ្ជា • សង្កត់យូរដើម្បីបិទ")
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(7, n)
        }
    }

    override fun onDestroy() {
        running = false
        longPress?.let { ui.removeCallbacks(it) }
        hidePanel()
        try {
            root?.let { wm.removeView(it) }
        } catch (_: Throwable) {}
        root = null
        super.onDestroy()
    }
}

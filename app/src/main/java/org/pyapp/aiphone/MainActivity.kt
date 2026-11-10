package org.pyapp.aiphone

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.media.MediaRecorder
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * AI Phone — ជំនួយការសំឡេង AI សម្រាប់ទូរស័ព្ទ Android។
 * Groq API: Whisper (សំឡេង → អត្ថបទ) + Llama (ឆ្លើយ) + TTS (និយាយ)។
 * Offline command: បើក app / settings / dial / ម៉ោង / ថ្ម។
 */

open class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var chat: LinearLayout
    private lateinit var scroller: ScrollView
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var mic: Button
    private lateinit var autoSwitch: Switch

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recorder: MediaRecorder? = null
    private var recFile: File? = null
    private var busy = false
    private var holdToTalk = false
    private var modelCache: List<String> = emptyList()

    // Model ថ្មីដែល Groq គាំទ្រឥឡូវ (វិច្ឆិកា 2026) — ចាស់ៗ (llama-3.3, mixtral, gemma2)
    // ត្រូវបាន decommission ហើយ។ បើ auto បើក កូដនឹងទាញ list ពី API ដោយផ្ទាល់ដែរ។
    private val CHAT_MODELS = listOf(
        "openai/gpt-oss-120b",
        "openai/gpt-oss-20b",
        "qwen/qwen3.8-27b",
        "openai/gpt-oss-safeguard-20b"
    )
    private val history = ArrayList<Pair<String, String>>() // role to content
    private val ui = Handler(Looper.getMainLooper())
    private val defaultSysPrompt =
        "You are AI Phone, a voice assistant living on the user's Android phone that can SEE the screen " +
            "(via accessibility) and tap/type/scroll for the user. " +
            "Reply in the same language the user used (Khmer or English). Be very short (1-2 sentences) " +
            "because your answer is read aloud. " +
            "To act, put commands on the FIRST lines, then a short sentence. Commands: " +
            "OPEN:<any app name or package, e.g. OPEN:tiktok or OPEN:com.zhiliaoapp.musically> ; " +
            "CLICK:<text on screen> ; TYPE:<text> ; SCROLL_DOWN ; SCROLL_UP ; BACK ; HOME. " +
            "Example: CLICK:Login\nបានចុច Login ឲ្យហើយ។ " +
            "Only use CLICK/TYPE on text that appears in the provided Screen: context. " +
            "Never invent that you did something you cannot do."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("aiphone", Context.MODE_PRIVATE)

        // AI Bubble → ផ្ទាំង chat អណ្តែតលើហ្គេម/app ផ្សេង (មិនបើក UI ពេញ)
        if (intent?.getBooleanExtra("overlay", false) == true) {
            setupOverlayUi()
            initTts()
            ui.postDelayed({ if (recorder == null && !busy) startRecording() }, 350)
            return
        }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xFF100D08.toInt())

        // ── Top bar ─────────────────────────────────────────────
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(28, 34, 20, 18)
        val title = TextView(this)
        title.text = "AI Phone"
        title.setTextColor(0xFFF0C75E.toInt())
        title.textSize = 21f
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        bar.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val gear = Button(this)
        gear.text = "⚙"
        gear.textSize = 19f
        gear.setTextColor(0xFFF0C75E.toInt())
        gear.background = null
        gear.setOnClickListener { showSettings() }
        bar.addView(gear)
        root.addView(bar)

        // ── Chat ────────────────────────────────────────────────
        scroller = ScrollView(this)
        chat = LinearLayout(this)
        chat.orientation = LinearLayout.VERTICAL
        chat.setPadding(24, 10, 24, 10)
        scroller.addView(chat, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))

        status = TextView(this)
        status.text = "ចុច ⚙ ដើម្បីដាក់ Groq API key"
        status.setTextColor(0xFF9C8B6A.toInt())
        status.textSize = 12f
        status.setPadding(28, 0, 28, 8)
        root.addView(status)

        // ── Quick chips ─────────────────────────────────────────
        val chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        chips.gravity = Gravity.CENTER_VERTICAL
        chips.setPadding(24, 0, 24, 8)
        for (c in listOf("បើក Facebook", "ម៉ោងប៉ុន្មាន", "ថ្មប៉ុន្មាន%", "បើក YouTube", "បើក Accessibility", "អានអេក្រង់")) {
            val b = pill(c)
            b.setOnClickListener { handleUser(c) }
            chips.addView(b, LinearLayout.LayoutParams(-2, -2).also { it.rightMargin = 16 })
        }
        val bubbleBtn = pill(if (AiBubbleService.running) "បិទ AI Bubble" else "AI Bubble")
        bubbleBtn.setOnClickListener { toggleBubble(bubbleBtn) }
        chips.addView(bubbleBtn, LinearLayout.LayoutParams(-2, -2).also { it.rightMargin = 16 })
        val permBtn = pill("សិទ្ធិទាំងអស់")
        permBtn.setOnClickListener { requestAllPermissions() }
        chips.addView(permBtn, LinearLayout.LayoutParams(-2, -2).also { it.rightMargin = 16 })
        autoSwitch = Switch(this)
        autoSwitch.text = "auto"
        autoSwitch.textSize = 12f
        autoSwitch.setTextColor(0xFFF0C75E.toInt())
        autoSwitch.setOnCheckedChangeListener { _, on ->
            setStatus(if (on) "auto: ស្តាប់បន្តបន្ទាប់ពីឆ្លើយ" else "")
            if (on) startRecording()
        }
        chips.addView(autoSwitch, LinearLayout.LayoutParams(-2, -2))
        val chipsScroll = android.widget.HorizontalScrollView(this)
        chipsScroll.isHorizontalScrollBarEnabled = false
        chipsScroll.addView(chips)
        root.addView(chipsScroll)

        // ── Input row ───────────────────────────────────────────
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(24, 6, 24, 26)

        input = EditText(this)
        input.hint = "សរសេរ ឬចុចមីក្រូហូ្តន៍…"
        input.setTextColor(0xFFF5E7C8.toInt())
        input.setHintTextColor(0xFF7D6E52.toInt())
        input.textSize = 15f
        input.setSingleLine(true)
        input.background = round(0xFF221B11.toInt(), 40f)
        input.setPadding(30, 18, 30, 18)
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f).also { it.rightMargin = 14 })

        val send = pill("➤")
        send.setOnClickListener {
            val t = input.text.toString().trim()
            if (t.isNotEmpty()) { input.setText(""); handleUser(t) }
        }
        row.addView(send)

        mic = Button(this)
        mic.text = "🎤"
        mic.textSize = 26f
        mic.setTextColor(0xFF1A1208.toInt())
        mic.background = round(0xFFD9A441.toInt(), 200f)
        mic.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { v.alpha = 0.7f; holdToTalk = true; startRecording(); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1f
                    if (holdToTalk) { holdToTalk = false; stopRecordingAndSend() }
                    true
                }
                else -> false
            }
        }
        row.addView(mic, LinearLayout.LayoutParams(160, 160))

        root.addView(row)

        setContentView(root)

        initTts()

        if ((prefs.getString("key", "") ?: "").isEmpty()) {
            addBubble("ai", "សូស្តី! ខ្ញុំជា AI Phone។\nដាក់ Groq API key (ចាប់ផ្តើមដោយ gsk_) ក្នុង ⚙ សិន រួចចុចមីក្រូហូ្តន៍និយាយ ឬសរសេរ។")
        }

        if (intent?.getBooleanExtra("listen", false) == true) {
            intent.removeExtra("listen")
            ui.postDelayed({ startRecording() }, 600)
        }

        // ស្នើ permissions ទាំងអស់ពេលបើកដំបូង
        if (!prefs.getBoolean("asked_perms", false)) {
            prefs.edit().putBoolean("asked_perms", true).apply()
            ui.postDelayed({ requestAllPermissions() }, 900)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.getBooleanExtra("listen", false) == true) {
            intent.removeExtra("listen")
            ui.postDelayed({ startRecording() }, 600)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  AI Bubble (overlay)
    // ═══════════════════════════════════════════════════════════
    private fun toggleBubble(btn: Button) {
        if (AiBubbleService.running) {
            stopService(Intent(this, AiBubbleService::class.java))
            btn.text = "AI Bubble"
            toast("បិទ AI Bubble")
            return
        }
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            toast("សូមអនុញ្ញាត 'Display over other apps'សិន")
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } catch (_: Throwable) {}
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        }
        val i = Intent(this, AiBubbleService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        btn.text = "បិទ AI Bubble"
        toast("AI Bubble ដំណើរការ — អូសបាន, ចុចដើម្បីនិយាយ")
    }

    // ═══════════════════════════════════════════════════════════
    //  All permissions
    // ═══════════════════════════════════════════════════════════
    private fun requestAllPermissions() {
        if (Build.VERSION.SDK_INT < 23) return
        val p = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BODY_SENSORS
        )
        if (Build.VERSION.SDK_INT >= 29) p.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= 31) {
            p.add(Manifest.permission.BLUETOOTH_CONNECT)
            p.add(Manifest.permission.BLUETOOTH_SCAN)
            p.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            p.add(Manifest.permission.POST_NOTIFICATIONS)
            p.add(Manifest.permission.READ_MEDIA_IMAGES)
            p.add(Manifest.permission.READ_MEDIA_VIDEO)
            p.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            p.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            p.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        val need = p.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) {
            requestPermissions(need.toTypedArray(), 43)
        } else {
            toast("សិទ្ធិទាំងអស់បានអនុញ្ញាតរួច ✓")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  UI helpers
    // ═══════════════════════════════════════════════════════════
    private fun round(color: Int, radius: Float): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = radius
        return g
    }

    private fun pill(label: String): Button {
        val b = Button(this)
        b.text = label
        b.textSize = 13f
        b.isAllCaps = false
        b.setTextColor(0xFFF0C75E.toInt())
        b.background = round(0xFF241C11.toInt(), 60f)
        b.setPadding(28, 8, 28, 8)
        return b
    }

    private fun addBubble(who: String, text: String) {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 15f
        tv.setTextColor(if (who == "me") 0xFF20140A.toInt() else 0xFFF3E6CB.toInt())
        tv.background = round(if (who == "me") 0xFFD9A441.toInt() else 0xFF221B11.toInt(), 36f)
        tv.setPadding(30, 22, 30, 22)
        val lp = LinearLayout.LayoutParams(-2, -2)
        lp.topMargin = 10
        lp.gravity = if (who == "me") Gravity.END else Gravity.START
        chat.addView(tv, lp)
        scroller.post { scroller.fullScroll(View.FOCUS_DOWN) }
    }

    private fun setStatus(s: String) = ui.post { status.text = s }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aiphone")
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** ផ្ទាំង chat តូចសម្រាប់ AI Bubble — បង្ហាញលើហ្គេមដោយមិនបើក app ពេញ។ */
    private fun setupOverlayUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(8), dp(8), dp(8), dp(8))

        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.background = round(0xF21A140C.toInt(), 26f)
        panel.setPadding(dp(18), dp(12), dp(18), dp(12))
        root.addView(panel, LinearLayout.LayoutParams(-1, -2))

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(this)
        title.text = "AI Phone"
        title.setTextColor(0xFFF0C75E.toInt())
        title.textSize = 16f
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val close = Button(this)
        close.text = "✕"
        close.textSize = 16f
        close.setTextColor(0xFFF0C75E.toInt())
        close.background = null
        close.setOnClickListener { finish() }
        header.addView(close)
        panel.addView(header)

        status = TextView(this)
        status.text = "ចុច 🎤 ហើយនិយាយ"
        status.setTextColor(0xFF9C8B6A.toInt())
        status.textSize = 12f
        status.setPadding(0, dp(2), 0, dp(4))
        panel.addView(status)

        scroller = ScrollView(this)
        chat = LinearLayout(this)
        chat.orientation = LinearLayout.VERTICAL
        scroller.addView(chat, ViewGroup.LayoutParams(-1, -2))
        panel.addView(scroller, LinearLayout.LayoutParams(-1, dp(185)))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(8), 0, 0)

        mic = Button(this)
        mic.text = "🎤"
        mic.textSize = 22f
        mic.setTextColor(0xFF1A1208.toInt())
        mic.background = round(0xFFD9A441.toInt(), 200f)
        mic.setOnClickListener {
            if (recorder != null) stopRecordingAndSend() else startRecording()
        }
        row.addView(mic, LinearLayout.LayoutParams(dp(60), dp(60)))

        val hint = TextView(this)
        hint.text = "ចុចមីក្រូ ដើម្បីនិយាយ • ចុចម្តងទៀតដើម្បីផ្ញើ"
        hint.setTextColor(0xFF9C8B6A.toInt())
        hint.textSize = 11f
        hint.setPadding(dp(12), 0, 0, 0)
        row.addView(hint, LinearLayout.LayoutParams(0, -2, 1f))
        panel.addView(row)

        autoSwitch = Switch(this) // hidden — TTS callback uses it
        setContentView(root)
    }

    private fun initTts() {
        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) {
                ttsReady = true
                val km = Locale("km", "KH")
                val r = tts?.setLanguage(km)
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.US)
                }
                tts?.setSpeechRate(0.98f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onError(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (autoSwitch.isChecked && !busy) {
                            ui.postDelayed({ if (autoSwitch.isChecked) startRecording() }, 500)
                        }
                    }
                })
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Settings
    // ═══════════════════════════════════════════════════════════
    private fun showSettings() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(40, 40, 40, 40)
        box.setBackgroundColor(0xFF1A140C.toInt())

        val lbl = TextView(this)
        lbl.text = "Groq API key  (console.groq.com/keys)"
        lbl.setTextColor(0xFFF0C75E.toInt())
        lbl.textSize = 14f
        box.addView(lbl)

        val key = EditText(this)
        key.setText(prefs.getString("key", ""))
        key.hint = "gsk_..."
        key.setTextColor(0xFFFFFFFF.toInt())
        key.setHintTextColor(0xFF7D6E52.toInt())
        key.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        key.background = round(0xFF241C11.toInt(), 24f)
        key.setPadding(24, 18, 24, 18)
        box.addView(key)

        val lbl2 = TextView(this)
        lbl2.text = "Model (auto = សាកល្បងតាមលំដាប់)"
        lbl2.setTextColor(0xFFF0C75E.toInt())
        lbl2.textSize = 14f
        lbl2.setPadding(0, 24, 0, 0)
        box.addView(lbl2)

        val modelSpinner = Spinner(this)
        val opts = listOf("auto") + CHAT_MODELS
        val ad = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opts)
        modelSpinner.adapter = ad
        val cur = prefs.getString("model", "auto") ?: "auto"
        val idx = opts.indexOf(cur).coerceAtLeast(0)
        modelSpinner.setSelection(idx)
        modelSpinner.background = round(0xFF241C11.toInt(), 24f)
        box.addView(modelSpinner)

        val lbl3 = TextView(this)
        lbl3.text = "System prompt (ទទេ = default)"
        lbl3.setTextColor(0xFFF0C75E.toInt())
        lbl3.textSize = 14f
        lbl3.setPadding(0, 24, 0, 0)
        box.addView(lbl3)

        val sysEdit = EditText(this)
        sysEdit.setText(prefs.getString("sysprompt", ""))
        sysEdit.setTextColor(0xFFFFFFFF.toInt())
        sysEdit.background = round(0xFF241C11.toInt(), 24f)
        sysEdit.setPadding(24, 18, 24, 18)
        sysEdit.minLines = 3
        sysEdit.gravity = Gravity.TOP
        box.addView(sysEdit)

        val note = TextView(this)
        note.text = "Key រក្សាទុកក្នុងទូរស័ព្ទតែម្នាក់ឯង (មិនដាក់ក្នុង APK)។"
        note.setTextColor(0xFF9C8B6A.toInt())
        note.textSize = 11f
        note.setPadding(0, 18, 0, 0)
        box.addView(note)

        val save = Button(this)
        save.text = "រក្សាទុក"
        save.setTextColor(0xFF1A1208.toInt())
        save.background = round(0xFFD9A441.toInt(), 60f)
        box.addView(save, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = 26 })

        val dialog = android.app.AlertDialog.Builder(this).setView(box).create()
        save.setOnClickListener {
            prefs.edit()
                .putString("key", key.text.toString().trim())
                .putString("model", modelSpinner.selectedItem?.toString() ?: "auto")
                .putString("sysprompt", sysEdit.text.toString().trim())
                .apply()
            dialog.dismiss()
            toast("រក្សាទុករួច ✓")
        }
        dialog.show()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    // ═══════════════════════════════════════════════════════════
    //  Recording
    // ═══════════════════════════════════════════════════════════
    private fun startRecording() {
        if (busy || recorder != null) return
        if (packageManager.checkPermission(Manifest.permission.RECORD_AUDIO, packageName) !=
            PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41)
            return
        }
        try {
            val f = File(cacheDir, "rec.m4a")
            if (f.exists()) f.delete()
            recFile = f
            @Suppress("DEPRECATION")
            val r = MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(16000)
            r.setAudioChannels(1)
            r.setAudioEncodingBitRate(32000)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            mic.text = "⏹"
            setStatus("🔴 កំពុងស្តាប់… ចុចមីក្រូម្តងទៀតដើម្បីផ្ញើ")
        } catch (e: Throwable) {
            recorder = null
            setStatus("មីក្រូហូ្តន៍មិនដំណើរការ: ${e.message}")
        }
    }

    private fun stopRecordingAndSend() {
        val r = recorder ?: return
        recorder = null
        mic.text = "🎤"
        var ok = true
        try { r.stop() } catch (_: Throwable) { ok = false }
        try { r.release() } catch (_: Throwable) {}
        val f = recFile
        if (!ok || f == null || !f.exists() || f.length() < 1200) {
            setStatus("សំឡេងខ្លីពេក — សូមកាន់យូរបន្តិច")
            return
        }
        setStatus("កំពុងស្តាប់សំឡេង → អត្ថបទ…")
        busy = true
        Thread {
            val text = transcribe(f)
            ui.post {
                busy = false
                if (text.isNullOrBlank()) {
                    setStatus("ស្តាប់មិនបាន — សូមនិយាយឮៗជាងនេះ")
                } else {
                    handleUser(text)
                }
            }
        }.start()
    }

    // ═══════════════════════════════════════════════════════════
    //  Groq: Whisper
    // ═══════════════════════════════════════════════════════════
    private fun transcribe(f: File): String? {
        val key = prefs.getString("key", "") ?: ""
        if (key.isEmpty()) { ui.post { toast("ដាក់ API key សិន (⚙)") }; return null }
        val url = URL("https://api.groq.com/openai/v1/audio/transcriptions")
        val boundary = "----aiphone" + System.currentTimeMillis()
        var conn: HttpURLConnection? = null
        try {
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.connectTimeout = 30000
            conn.readTimeout = 90000
            val out = DataOutputStream(conn.outputStream)
            fun field(name: String, value: String) {
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                out.writeBytes(value + "\r\n")
            }
            field("model", "whisper-large-v3-turbo")
            field("response_format", "json")
            field("temperature", "0")
            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"rec.m4a\"\r\n")
            out.writeBytes("Content-Type: audio/m4a\r\n\r\n")
            FileInputStream(f).use { it.copyTo(out) }
            out.writeBytes("\r\n--$boundary--\r\n")
            out.flush()
            out.close()

            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText() ?: ""
            if (code !in 200..299) {
                setStatus("Groq error $code: ${body.take(90)}")
                return null
            }
            return JSONObject(body).optString("text", "").trim()
        } catch (e: Throwable) {
            setStatus("បណ្តាញ: ${e.message}")
            return null
        } finally {
            conn?.disconnect()
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Groq: chat
    // ═══════════════════════════════════════════════════════════
    private fun askGroq(text: String, done: (String) -> Unit) {
        val key = prefs.getString("key", "") ?: ""
        if (key.isEmpty()) {
            done("សូមដាក់ Groq API key ក្នុង ⚙ សិន។ (console.groq.com/keys)")
            return
        }
        val modelPref = (prefs.getString("model", "auto") ?: "auto").trim()
        val auto = modelPref.isEmpty() || modelPref.equals("auto", true)
        // auto → យក list ពី Groq API ម្តងក្នុងមួយ session (model ប្តូរញឹកញាប់)
        val modelsToTry = if (auto) {
            if (modelCache.isEmpty()) modelCache = fetchModels(key)
            modelCache.ifEmpty { CHAT_MODELS }
        } else listOf(modelPref)
        val sys = (prefs.getString("sysprompt", "") ?: "").ifEmpty { defaultSysPrompt }
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", sys))
        for (h in history.takeLast(10)) msgs.put(JSONObject().put("role", h.first).put("content", h.second))
        val screen = if (AutoClickAccessibilityService.isEnabled()) {
            AutoClickAccessibilityService.instance?.screenText(400) ?: ""
        } else ""
        val userMsg = if (screen.isNotBlank()) "$text\nScreen: $screen" else text
        msgs.put(JSONObject().put("role", "user").put("content", userMsg))
        val basePayload = JSONObject()
            .put("messages", msgs)
            .put("temperature", 0.5)
            .put("max_tokens", 320)
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
                        lastErr = "Groq error $code: ${body.take(120)}"
                        // 429 = rate limit per model, 5xx = model down: try next model
                        if (code == 400 || code == 404 || code == 422 || code == 429 || code >= 500) continue
                        ui.post { done(lastErr) }
                        return@Thread
                    }
                    val ans = JSONObject(body).getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message").optString("content", "").trim()
                    if (modelsToTry.size > 1) setStatus("auto → $m")
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

    // ═══════════════════════════════════════════════════════════
    //  Command router
    // ═══════════════════════════════════════════════════════════
    private fun handleUser(text: String) {
        addBubble("me", text)
        val lower = text.lowercase(Locale.ROOT)

        fun has(vararg k: String): Boolean = k.any { lower.contains(it) }

        val local = when {
            has("ម៉ោង", "time") && !has("facebook") -> {
                val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                "ឥឡូវម៉ោង $t"
            }
            has("ថ្ម", "battery") -> {
                val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                "ថ្មនៅ ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%"
            }
            has("facebook", "ហ្វេសប៊ុក", "បើក ហ្វ") -> { openFacebook(); "បើក Facebook ឲ្យហើយ ✓" }
            has("youtube", "យូធូប") -> { openApp("com.google.android.youtube", "https://m.youtube.com", null); "បើក YouTube ឲ្យហើយ ✓" }
            has("បើកកាមេរ៉ា", "open camera", "កាមេរ៉ា") -> { openCamera(); "បើកកាមេរ៉ាឲ្យហើយ ✓" }
            has("settings", "ការកំណត់") -> { startAct(Intent(Settings.ACTION_SETTINGS)); "បើក Settings ✓" }
            has("ទូរស័ព្ទទៅ", "call ") -> {
                val num = lower.filter { it.isDigit() || it == '+' }
                if (num.length >= 3) { startAct(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num"))); "បើកការហៅ $num ✓" }
                else null
            }
            has("accessibility", "access", "ការចូលប្រើ") || lower.contains("បើក accessibility") -> {
                openAccessibilitySettings(); "បើកការកំណត់ Accessibility — សូមបើក 'AI Phone' ✓"
            }
            has("អានអេក្រង់", "read screen", "មើលអេក្រង់", "look") -> {
                val s = AutoClickAccessibilityService.instance?.screenText(500)
                if (s.isNullOrBlank()) {
                    promptAccessibility()
                    "Accessibility មិនទាន់បើក — ខ្ញុំបើកការកំណត់ឲ្យហើយ"
                } else "អេក្រង់: $s"
            }
            lower.startsWith("click ") || lower.startsWith("ចុច ") -> {
                val target = if (lower.startsWith("click ")) text.substringAfter(" ", "").trim()
                else text.substringAfter(" ", "").trim()
                if (!AutoClickAccessibilityService.isEnabled()) {
                    promptAccessibility()
                    "សូមបើក Accessibility សិន — ខ្ញុំបើកការកំណត់ឲ្យហើយ"
                } else {
                    val ok = AutoClickAccessibilityService.instance?.clickByText(target) == true
                    if (ok) "បានចុច '$target' ✓" else "មិនឃើញ '$target' លើអេក្រង់"
                }
            }
            has("scroll down", "រមូរចុះ", "រមូរចុះក្រោម") -> {
                if (AutoClickAccessibilityService.instance?.scrollDown() == true) "រមូរចុះ ✓" else "Accessibility មិនទាន់បើក"
            }
            has("scroll up", "រមូរឡើង") -> {
                if (AutoClickAccessibilityService.instance?.scrollUp() == true) "រមូរឡើង ✓" else "Accessibility មិនទាន់បើក"
            }
            has("back", "ថយក្រោយ") && !has("facebook") -> {
                if (AutoClickAccessibilityService.instance?.goBack() == true) "ថយក្រោយ ✓" else "Accessibility មិនទាន់បើក"
            }
            has("home", "ទំព័រដើម") -> {
                if (AutoClickAccessibilityService.instance?.goHome() == true) "ទំព័រដើម ✓" else "Accessibility មិនទាន់បើក"
            }
            lower.startsWith("បើក ") || lower.startsWith("open ") -> {
                val name = text.substringAfter(" ").trim()
                if (name.isEmpty()) null
                else if (openAnyApp(name)) "បើក $name ឲ្យហើយ ✓"
                else "រកមិនឃើញ app '$name'"
            }
            else -> null
        }

        if (local != null) {
            addBubble("ai", local)
            speak(local)
            return
        }

        setStatus("កំពុងគិត…")
        busy = true
        askGroq(text) { ans ->
            busy = false
            val reply = executeAiCommands(ans)
            history.add("user" to text)
            history.add("assistant" to ans)
            addBubble("ai", reply)
            speak(reply)
            setStatus("")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Phone actions
    // ═══════════════════════════════════════════════════════════
    private fun startAct(i: Intent) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Throwable) {
            toast("បើកមិនបាន: ${e.message}")
        }
    }

    private fun openAccessibilitySettings() {
        try {
            startAct(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Throwable) {
            toast("បើកមិនបាន: ${e.message}")
        }
    }

    private fun promptAccessibility() {
        try {
            android.app.AlertDialog.Builder(this)
                .setTitle("បើក Accessibility")
                .setMessage("ដើម្បីឲ្យ AI មើលអេក្រង់ និងចុចបាន សូមបើក 'AI Phone' ក្នុង Settings → Accessibility។")
                .setPositiveButton("បើកការកំណត់") { _, _ -> openAccessibilitySettings() }
                .setNegativeButton("បិទ", null)
                .show()
        } catch (_: Throwable) {}
    }

    /** ទាញ model ដែលកំពុងមានពី Groq API (auto)។ */
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

    private fun executeAiCommands(text: String): String {
        val spoken = mutableListOf<String>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("OPEN:", ignoreCase = true) -> {
                    val what = line.substringAfter(":").trim().lowercase(Locale.ROOT)
                    when {
                        what.contains("facebook") -> openFacebook()
                        what.contains("youtube") -> openApp("com.google.android.youtube", "https://m.youtube.com", null)
                        what.contains("camera") -> openCamera()
                        what.contains("settings") -> startAct(Intent(Settings.ACTION_SETTINGS))
                        what.contains("playstore") -> startAct(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=apps")))
                        what.contains("chrome") -> openApp("com.android.chrome", "https://www.google.com", null)
                        what.contains("phone") -> startAct(Intent(Intent.ACTION_DIAL))
                        what.contains("messages") -> startAct(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING))
                        else -> if (!openAnyApp(what)) spoken.add("រកមិនឃើញ app '$what'")
                    }
                }
                line.startsWith("CLICK:", ignoreCase = true) -> {
                    val target = line.substringAfter(":").trim()
                    val svc = AutoClickAccessibilityService.instance
                    if (svc == null) spoken.add("Accessibility មិនទាន់បើក")
                    else if (!svc.clickByText(target)) spoken.add("មិនឃើញ '$target'")
                }
                line.startsWith("TYPE:", ignoreCase = true) -> {
                    val txt = line.substringAfter(":").trim()
                    val svc = AutoClickAccessibilityService.instance
                    if (svc == null) spoken.add("Accessibility មិនទាន់បើក")
                    else if (!svc.typeText(txt)) spoken.add("វាយអត្ថបទមិនបាន")
                }
                line.equals("SCROLL_DOWN", ignoreCase = true) -> {
                    AutoClickAccessibilityService.instance?.scrollDown()
                }
                line.equals("SCROLL_UP", ignoreCase = true) -> {
                    AutoClickAccessibilityService.instance?.scrollUp()
                }
                line.equals("BACK", ignoreCase = true) -> {
                    AutoClickAccessibilityService.instance?.goBack()
                }
                line.equals("HOME", ignoreCase = true) -> {
                    AutoClickAccessibilityService.instance?.goHome()
                }
                else -> spoken.add(line)
            }
        }
        val out = spoken.joinToString(" ").trim()
        return if (out.isEmpty()) "រួចរាល់ ✓" else out
    }

    private fun openApp(pkg: String, fallbackUrl: String, scheme: String?) {
        // 1) launch intent (requires <queries> in manifest for Android 11+)
        try {
            val i = packageManager.getLaunchIntentForPackage(pkg)
            if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return }
        } catch (_: Throwable) {}
        // 2) scheme URI (opens the app if installed)
        if (scheme != null) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(scheme)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Throwable) {}
        }
        // 3) fallback to web
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Throwable) {
            toast("បើកមិនបាន — សូមបើកដោយដៃ")
        }
    }

    private fun openFacebook() {
        // try main app, then lite, then web
        for (p in listOf("com.facebook.katana", "com.facebook.lite")) {
            val i = try { packageManager.getLaunchIntentForPackage(p) } catch (_: Throwable) { null }
            if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return }
        }
        openApp("com.facebook.katana", "https://m.facebook.com", "fb://")
    }

    /** បើក app ណាមួយតាមឈ្មោះ ឬ package name (ក្រៅពី list ខាងលើ)។ */
    private fun openAnyApp(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return false
        val lower = q.lowercase(Locale.ROOT)
        if (q.contains(".")) {
            try {
                val i = packageManager.getLaunchIntentForPackage(q)
                if (i != null) { startAct(i); return true }
            } catch (_: Throwable) {}
        }
        return try {
            val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = packageManager.queryIntentActivities(main, 0)
            var best: android.content.pm.ResolveInfo? = null
            var bestScore = 0
            for (ai in apps) {
                val label = try {
                    ai.loadLabel(packageManager).toString().lowercase(Locale.ROOT)
                } catch (_: Throwable) { "" }
                val pkg = ai.activityInfo.packageName.lowercase(Locale.ROOT)
                val score = when {
                    label == lower || pkg == lower -> 100
                    label.startsWith(lower) -> 80
                    pkg.startsWith(lower) -> 70
                    label.contains(lower) -> 60
                    pkg.contains(lower) -> 50
                    else -> 0
                }
                if (score > bestScore) { bestScore = score; best = ai }
            }
            val found = best ?: return false
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(found.activityInfo.packageName, found.activityInfo.name)
            startAct(i)
            true
        } catch (_: Throwable) { false }
    }

    private fun openCamera() = startAct(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))

    // ═══════════════════════════════════════════════════════════
    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(code, p, g)
        if (code == 41 && g.isNotEmpty() && g[0] == PackageManager.PERMISSION_GRANTED) {
            setStatus("មីក្រូហូ្តន៍រួចរាល់ — ចុចម្តងទៀត")
        } else if (code == 41) {
            setStatus("ត្រូវការសិទ្ធិមីក្រូហូ្តន៍ (Settings → Apps → AI Phone → Permissions)")
        } else if (code == 43) {
            val granted = g.count { it == PackageManager.PERMISSION_GRANTED }
            setStatus("សិទ្ធិ: បានអនុញ្ញាត $granted/${g.size}")
        }
    }

    override fun onDestroy() {
        try { recorder?.release() } catch (_: Throwable) {}
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

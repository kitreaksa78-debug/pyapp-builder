package org.pyapp.aiphone

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AutoClickAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AutoClickAccessibilityService? = null
            private set
        fun isEnabled(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /** ជ្រើស window របស់ app ពិត (មិនមែន AI Phone/overlay/keyboard)។ */
    private fun appRoot(): AccessibilityNodeInfo? {
        try {
            val ws = windows
            for (i in ws.indices.reversed()) {
                val r = ws[i].root ?: continue
                val pkg = r.packageName?.toString() ?: ""
                if (pkg.isEmpty() || pkg == packageName) continue
                val low = pkg.lowercase()
                if (low.contains("inputmethod") || low.contains("keyboard") ||
                    low.contains("gboard") || low.contains("ime") ||
                    low == "com.android.systemui") continue
                return r
            }
        } catch (_: Throwable) {}
        return rootInActiveWindow
    }

    fun screenText(limit: Int = 600): String {
        val root = appRoot() ?: return ""
        val sb = StringBuilder()
        collectText(root, sb)
        val t = sb.toString().replace(Regex("\\s+"), " ").trim()
        return if (t.length > limit) t.take(limit) + "…" else t
    }

    /** អានគ្រប់ window រួមទាំង AI Phone ខ្លួនឯង — សម្រាប់ self-test។ */
    fun screenTextHere(limit: Int = 600): String {
        val sb = StringBuilder()
        try {
            for (w in windows) {
                val r = w.root ?: continue
                collectText(r, sb)
            }
        } catch (_: Throwable) {}
        if (sb.isEmpty()) {
            try { rootInActiveWindow?.let { collectText(it, sb) } } catch (_: Throwable) {}
        }
        val t = sb.toString().replace(Regex("\\s+"), " ").trim()
        return if (t.length > limit) t.take(limit) + "…" else t
    }

    private fun collectText(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        val txt = node.text?.toString()
        val desc = node.contentDescription?.toString()
        if (!txt.isNullOrBlank()) sb.append(txt).append(" ")
        if (!desc.isNullOrBlank() && desc != txt) sb.append(desc).append(" ")
        for (i in 0 until node.childCount) collectText(node.getChild(i), sb)
    }

    fun clickByText(target: String): Boolean {
        val root = appRoot() ?: return false
        return clickFromRoot(root, target)
    }

    /** សម្រាប់ self-test: ស្វែងរកគ្រប់ window រួមទាំង app ខ្លួនឯង។ */
    fun clickByTextHere(target: String): Boolean {
        val key = target.trim()
        if (key.isEmpty()) return false
        try {
            val ws = windows
            for (i in ws.indices.reversed()) {
                val r = ws[i].root ?: continue
                if (clickFromRoot(r, key)) return true
            }
        } catch (_: Throwable) {}
        return false
    }

    private fun clickFromRoot(root: AccessibilityNodeInfo, target: String): Boolean {
        val key = target.trim()
        if (key.isEmpty()) return false
        val found = root.findAccessibilityNodeInfosByText(key)
        for (n in found) {
            if (clickNode(n)) return true
        }
        return clickRecursive(root, key.lowercase())
    }

    private fun clickNode(n: AccessibilityNodeInfo?): Boolean {
        if (n == null) return false
        if (n.isClickable) {
            return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        var p = n.parent
        while (p != null) {
            if (p.isClickable) {
                return p.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            p = p.parent
        }
        return false
    }

    private fun clickRecursive(node: AccessibilityNodeInfo?, key: String): Boolean {
        if (node == null) return false
        val t = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
        if (t.lowercase().contains(key) && clickNode(node)) return true
        for (i in 0 until node.childCount) {
            if (clickRecursive(node.getChild(i), key)) return true
        }
        return false
    }

    fun typeText(text: String): Boolean {
        val root = appRoot() ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) {
            val args = android.os.Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
        return typeRecursive(root, text)
    }

    private fun typeRecursive(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false
        if (node.isEditable) {
            val args = android.os.Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
        for (i in 0 until node.childCount) {
            if (typeRecursive(node.getChild(i), text)) return true
        }
        return false
    }

    fun scrollDown(): Boolean = scroll(true)
    fun scrollUp(): Boolean = scroll(false)

    private fun scroll(forward: Boolean): Boolean {
        val root = appRoot() ?: return false
        return scrollRecursive(root, forward)
    }

    private fun scrollRecursive(node: AccessibilityNodeInfo?, forward: Boolean): Boolean {
        if (node == null) return false
        if (node.isScrollable) {
            val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            if (node.performAction(action)) return true
        }
        for (i in 0 until node.childCount) {
            if (scrollRecursive(node.getChild(i), forward)) return true
        }
        return false
    }

    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun openRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
}

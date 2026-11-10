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

    fun screenText(limit: Int = 600): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        collectText(root, sb)
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
        val root = rootInActiveWindow ?: return false
        val key = target.trim()
        if (key.isEmpty()) return false
        // 1) direct clickable matches
        val found = root.findAccessibilityNodeInfosByText(key)
        for (n in found) {
            if (clickNode(n)) return true
        }
        // 2) recursive search (case-insensitive contains)
        return clickRecursive(root, key.lowercase())
    }

    private fun clickNode(n: AccessibilityNodeInfo?): Boolean {
        if (n == null) return false
        if (n.isClickable) {
            n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        var p = n.parent
        while (p != null) {
            if (p.isClickable) {
                p.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
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
        val root = rootInActiveWindow ?: return false
        // find focused editable node
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) {
            val args = android.os.Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
        // fallback: first editable
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
        val root = rootInActiveWindow ?: return false
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
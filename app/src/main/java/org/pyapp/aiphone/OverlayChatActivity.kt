package org.pyapp.aiphone

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager

/**
 * ផ្ទាំង chat អណ្តែតលើហ្គេម/app ផ្សេង។
 * Window ថ្លាស់ និងតូចនៅបាតអេក្រង់ → ចុចក្រៅផ្ទាំងនៅតែលេងហ្គេមបាន។
 * មានប្រអប់វាយបញ្ជា; window បើក focus តែពេលចុចប្រអប់វាយ។
 */
class OverlayChatActivity : MainActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        super.onCreate(savedInstanceState)
        val dm = resources.displayMetrics
        val w = minOf(dm.widthPixels - (24 * dm.density).toInt(), (420 * dm.density).toInt())
        window.setLayout(w, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    /** បើក keyboard សម្រាប់ប្រអប់វាយបញ្ជា (ហើយឲ្យ window ទទួល focus)។ */
    fun enableTyping() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        window.decorView.requestFocus()
    }

    /** ត្រឡប់ទៅ non-focusable ដើម្បីកុំឲ្យហ្គេមខាងក្រោយបាត់ focus។ */
    fun disableTyping() {
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }
}

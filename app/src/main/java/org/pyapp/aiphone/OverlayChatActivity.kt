package org.pyapp.aiphone

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager

/**
 * ផ្ទាំង chat អណ្តែតលើហ្គេម/app ផ្សេង។
 * Window ថ្លាស់ និងតូចនៅបាតអេក្រង់ → ចុចក្រៅផ្ទាំងនៅតែលេងហ្គេមបាន។
 * ស្តាប់សំឡេង, ឆ្លើយ, និងចុចតាមបញ្ជាដោយប្រើ logic របស់ MainActivity។
 */
class OverlayChatActivity : MainActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setGravity(Gravity.BOTTOM)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        super.onCreate(savedInstanceState)
        val dm = resources.displayMetrics
        window.setLayout(
            dm.widthPixels - (16 * dm.density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }
}

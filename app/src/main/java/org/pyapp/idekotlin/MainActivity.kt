package org.pyapp.idekotlinimport android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val t = TextView(this)
        t.text = "Kotlin native ដំណើរការ ✅"
        setContentView(t)
    }
}

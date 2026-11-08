package org.pyapp.qandk

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    // C/C++ នៅ app/src/main/cpp/ → libnativeapp.so (JNI)
    private external fun stringFromJNI(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            textSize = 18f
            setPadding(48, 96, 48, 48)
            text = stringFromJNI()
        }
        setContentView(tv)
    }

    companion object {
        init {
            System.loadLibrary("nativeapp")
        }
    }
}

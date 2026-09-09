package com.kairo.reader

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity

class TestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

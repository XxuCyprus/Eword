package com.eword.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.eword.app.data.AppCore
import com.eword.app.ui.AppRoot
import com.eword.app.ui.EwordTheme

class MainActivity : ComponentActivity() {

    private lateinit var core: AppCore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        core = AppCore(applicationContext)
        core.load()
        setContent {
            EwordTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    AppRoot(core)
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching { core.pronouncer.release() }
        super.onDestroy()
    }
}

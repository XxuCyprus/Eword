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
        // 词包 16 MB：解析放后台线程，界面先显示「正在载入词包…」。
        // 以前是在主线程 load() 完才 setContent，冷启动每次白屏一到几秒。
        core.loadAsync()
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

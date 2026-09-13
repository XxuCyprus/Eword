package com.eword.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eword.app.data.AppCore

@Composable
fun HomeScreen(core: AppCore, push: (Screen) -> Unit) {
    val pack = core.enabledPack

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(48.dp))
        Text("Eword", fontSize = 30.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text("真题语境记单词", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(22.dp))

        // 出过错就摆一行红字出来，并且能关掉。
        // 以前这些异常全被静默吞掉：进度「清零」、词本「消失」、喇叭「不响」，
        // 用户侧完全无法归因。
        core.lastError?.let { err ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(cardShape())
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(14.dp)
            ) {
                Text(err, fontSize = 12.sp, lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.height(8.dp))
                Text("知道了", fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.clickable { core.clearError() })
            }
            Spacer(Modifier.height(22.dp))
        }

        // 当前启用词本
        Column(
            Modifier
                .fillMaxWidth()
                .clip(cardShape())
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(14.dp)
        ) {
            Text("当前启用词本", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                pack?.manifest?.name ?: "尚未启用任何词本",
                fontSize = 17.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (pack != null) {
                Spacer(Modifier.height(4.dp))
                val done = core.countOf(pack.manifest.packId, com.eword.app.data.WordState.DONE)
                Text(
                    "共 ${pack.words.size} 词 · 已彻底记住 $done 词 · 每单元 ${core.unitSize} 词",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        FuncCard("识记功能区", "按单元记忆启用词本里的单词", 0xFF2F6BFF) {
            if (pack == null) push(Screen.Wordbook) else push(Screen.MemorizeUnits)
        }
        Spacer(Modifier.height(12.dp))
        FuncCard("温习功能区", "温习一 → 温习二 → 彻底记住", 0xFF1D9E75) {
            if (pack == null) push(Screen.Wordbook) else push(Screen.ReviewHome)
        }
        Spacer(Modifier.height(12.dp))
        FuncCard("我的单词本", "导入词本 · 管理单词 · 打乱顺序", 0xFFBA7517) {
            push(Screen.Wordbook)
        }
        Spacer(Modifier.height(12.dp))
        FuncCard("设置功能区", "单元词数 · 发音 · 显示项", 0xFF5A6472) {
            push(Screen.Settings)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FuncCard(title: String, subtitle: String, accent: Long, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(cardShape())
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(androidx.compose.ui.graphics.Color(accent))
        )
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

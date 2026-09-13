package com.eword.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eword.app.data.AppCore
import com.eword.app.data.WordState

@Composable
fun MemorizeUnitsScreen(core: AppCore, push: (Screen) -> Unit, pop: () -> Unit) {
    val pack = core.enabledPack
    if (pack == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), Alignment.Center) {
            Text("请先到「我的单词本」导入并启用一个词本", fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val key = "mm_${pack.manifest.packId}"
    UnitListScreen(
        title = "识记功能区",
        subtitle = pack.manifest.name,
        units = core.units(pack, WordState.NEW),
        pendingLabel = "待记",
        onBack = pop,
        onPick = { push(Screen.Memorize(it)) },
        initialScrollIndex = core.unitScrollIndexOf(key),
        onScrollIndexChanged = { core.setUnitScrollIndex(key, it) },
        onLeaving = { core.flushUnitScroll(key) },
        emptyText = "本词本的单词都识记过了，去「温习功能区」继续吧。"
    )
}

@Composable
fun MemorizeScreen(core: AppCore, unit: Int, push: (Screen) -> Unit, pop: () -> Unit) {
    val pack = core.enabledPack ?: return
    val packId = pack.manifest.packId

    var round by remember { mutableIntStateOf(0) }
    val queue = remember(unit, packId, round) { core.queue(pack, unit, WordState.NEW) }

    // 记住进度：回到本单元时，从上次学到的那个词继续
    var idx by remember(round) {
        val savedId = core.memorizeWordId(packId, unit)
        val pos = if (round == 0 && savedId.isNotBlank())
            queue.indexOfFirst { it.id == savedId } else -1
        val fallback = if (round == 0) core.memorizeIndexOf(packId, unit) else 0
        mutableIntStateOf(
            if (pos >= 0) pos else fallback.coerceIn(0, queue.size)
        )
    }

    // 位置一变就记下（记住「当前这个词」+「当前位置」）
    LaunchedEffect(idx, round, queue.size) {
        if (round == 0) {
            if (idx in queue.indices) {
                core.setMemorizeIndex(packId, unit, idx)
                core.setMemorizeWordId(packId, unit, queue[idx].id)
            } else if (idx >= queue.size) {
                core.clearMemorize(packId, unit)
            }
        }
    }

    LaunchedEffect(idx, round) {
        if (core.autoPronounce && idx < queue.size) {
            val w = queue[idx]
            core.pronouncer.pronounce(packId, w.id, w.word)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        EwordTopBar(
            "识记 · 第 ${unit + 1} 单元",
            onBack = pop,
            subtitle = if (queue.isEmpty()) null else "${idx.coerceAtMost(queue.size)} / ${queue.size}"
        )

        if (queue.isEmpty()) {
            Centered { Text("本单元没有待识记的单词", fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@Column
        }

        if (idx >= queue.size) {
            val id = packId
            val remaining = queue.count { core.stateOf(id, it.id) == WordState.NEW }
            Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("本单元已过完", fontSize = 20.sp, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(10.dp))
                    Text("共 ${queue.size} 词 · 记住 ${queue.size - remaining} · 待重记 $remaining",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    if (remaining > 0) {
                        Button(onClick = { round++; idx = 0 }, shape = cardShape()) {
                            Text("重记未记住的 $remaining 词")
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    OutlinedButton(onClick = pop, shape = cardShape()) { Text("返回单元列表") }
                }
            }
            return@Column
        }

        val w = queue[idx]

        // 换词时把滚动位置归零：否则看完上一个词的例句后，新词会停在半路、看不到单词本身
        val scrollState = remember(w.id) { ScrollState(0) }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            // 与详情页共用同一份排版，保证完全一致
            WordFullBody(
                core = core,
                pack = pack,
                w = w,
                onOpenForm = { push(Screen.WordDetail(packId, it.lowercase())) }
            )
            Spacer(Modifier.height(28.dp))
        }

        Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // 上一个：回看上一条
                OutlinedButton(
                    onClick = { if (idx > 0) idx-- },
                    enabled = idx > 0,
                    shape = cardShape(),
                    modifier = Modifier.weight(1f)
                ) { Text("上一个") }
                // 没记住：写回「未识记」，留在本单元，跳到下一个。
                // 必须显式写回：用户可能先误点了「记住了」，再用「上一个」回来改判，
                // 此时若不写状态，这个词仍然是「记住了」——改判不回来。
                // 已经是「未识记」的不重复写：第一次判「没记住」时状态本来就是 NEW，
                // 再写一遍只是白序列化一次全量进度（进度落盘在主线程做序列化）。
                OutlinedButton(
                    onClick = {
                        if (core.stateOf(packId, w.id) != WordState.NEW) {
                            core.setState(packId, w.id, WordState.NEW)
                        }
                        idx++
                    },
                    shape = cardShape(),
                    modifier = Modifier.weight(1f)
                ) { Text("没记住") }
                // 记住了：加入温习一，跳到下一个
                Button(
                    onClick = {
                        core.setState(packId, w.id, WordState.REVIEW1)
                        idx++
                    },
                    shape = cardShape(),
                    modifier = Modifier.weight(1f)
                ) { Text("记住了") }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "「没记住」留在本单元，之后可再来一轮；「记住了」进入温习一。" +
                    "点错了就点「上一个」回去，再按另一个按钮改判。",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

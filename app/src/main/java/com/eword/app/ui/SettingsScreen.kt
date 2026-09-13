package com.eword.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eword.app.BuildConfig
import com.eword.app.data.AppCore
import com.eword.app.data.WordState

/**
 * 设置功能区。
 * 所有改动先进入「草稿」，必须点「保存更改」并确认后才真正生效。
 * （清空学习进度不在这里，在「我的单词本 → 具体某个词本」）
 */
@Composable
fun SettingsScreen(core: AppCore, pop: () -> Unit) {
    // ---- 草稿状态：初始值来自当前已保存的设置 ----
    var dUnit by remember { mutableIntStateOf(core.unitSize) }
    var dAuto by remember { mutableStateOf(core.autoPronounce) }
    var dColl by remember { mutableStateOf(core.showCollocations) }
    var dEx by remember { mutableStateOf(core.showExamples) }

    val dirty = dUnit != core.unitSize || dAuto != core.autoPronounce ||
        dColl != core.showCollocations || dEx != core.showExamples

    var showSaveConfirm by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var savedMsg by remember { mutableStateOf<String?>(null) }

    // 有未保存改动时，返回先问一句
    BackHandler(enabled = dirty) { showLeaveConfirm = true }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        EwordTopBar(
            "设置功能区",
            onBack = { if (dirty) showLeaveConfirm = true else pop() }
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            Text("每单元单词数", fontSize = 15.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text("识记、温习、我的单词本三处共用同一个值，默认 52",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            // 第一行：减号 / 数字 / 加号。
            // 数字允许 5–200，最大三位数；给它固定宽度并禁止换行，
            // 否则三位数会把自己压成两行，并挤掉右侧预设按钮。
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    dUnit = (dUnit - 1).coerceAtLeast(5)
                }, shape = cardShape()) { Text("－") }
                Box(
                    Modifier.width(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$dUnit",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                OutlinedButton(onClick = {
                    dUnit = (dUnit + 1).coerceAtMost(200)
                }, shape = cardShape()) { Text("＋") }
            }
            Spacer(Modifier.height(8.dp))
            // 第二行：常用预设。单独占一行，任何位数都不会互相挤压。
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 52, 100).forEach { n ->
                    OutlinedButton(onClick = { dUnit = n }, shape = cardShape()) {
                        Text("$n", fontSize = 12.sp, maxLines = 1)
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            Text("发音", fontSize = 15.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            SwitchRow("自动发音", "单词出现时自动播放美式发音", dAuto) { dAuto = it }

            Spacer(Modifier.height(26.dp))
            Text("显示项", fontSize = 15.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            SwitchRow("显示短语搭配", "含短语搭配及其意思", dColl) { dColl = it }
            SwitchRow("显示例句", "含例句与例句意思", dEx) { dEx = it }

            Spacer(Modifier.height(30.dp))
            Text("关于内容来源", fontSize = 15.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                "例句与译文、短语搭配取自四六级真题原文及官方译文，不使用自造例句。\n" +
                    "例句逐条核验过中英是否对得上，核不实的宁可空着。\n" +
                    "句式用法从已核验的真题例句里抽模板，只在真题里稳定复现的才收。\n" +
                    "单词变形只收真题原文里出现过的形态。\n" +
                    "形近词只收不同族、拼写真的相近的词：同族派生词（arrival 与 arrive）\n" +
                    "和变形形态都不算，它们是单词变形那一栏的内容。\n" +
                    "近义词要求双方列出的主要义项里有同一条意思（并参照 WordNet 同义词集），\n" +
                    "有一侧只是冷僻义项撞上的不收，核不实的留空。\n" +
                    "词义按来源分组：真题里出现过的标「真题」，真题之外按词典补的标「词典补充」。\n" +
                    "音标与词性来自其他来源，发音为随词包导入的美式音频。",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp
            )

            // App 与词包各自独立发版，两个版本号都列出来，便于对不上时排查。
            // 词包版本只报**当前启用**的那个：停用了还想看某个词本的版本，
            // 到「我的单词本」里看，那里每个词本都列着自己的版本。
            // 早先未启用时会退而显示第一个词本的版本 —— 装了两个以上词本时，
            // 那句话显示的版本并不属于当前在用的词本，是错的。
            val curPack = core.enabledPack?.manifest
            Spacer(Modifier.height(26.dp))
            Text("关于版本", fontSize = 15.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                "应用版本 v${BuildConfig.VERSION_NAME}\n" +
                    "词包版本 " + (curPack?.let { "${it.name} ${it.versionLabel}" }
                        ?: "当前没有启用词本") + "\n" +
                    "应用与词包分别更新：词包换了新版，应用不必跟着升级，学习进度照旧保留。\n" +
                    "同一个词本导入新版本会直接替换旧的，不必先删；装了多个词本时，" +
                    "这里显示的是当前启用的那个。",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp
            )

            // ---- 保存更改（未改动时禁用） ----
            Spacer(Modifier.height(30.dp))
            Button(
                onClick = { showSaveConfirm = true },
                enabled = dirty,
                shape = cardShape(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (dirty) "保存更改" else "没有需要保存的更改") }
            Spacer(Modifier.height(6.dp))
            Text(
                if (dirty) "改动尚未生效，点击上方按钮并确认后才会保存"
                else "当前设置已全部保存",
                fontSize = 11.sp,
                color = if (dirty) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )

            savedMsg?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, fontSize = 12.sp, color = AccentGreen)
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // ---- 确认保存 ----
    if (showSaveConfirm) {
        ConfirmDialog(
            title = "保存更改？",
            message = "将保存以下设置：\n\n" +
                "· 每单元单词数：${dUnit}" +
                (if (dUnit != core.unitSize)
                    "（当前 ${core.unitSize} —— 改了之后全部词条的单元编号会重排，\n" +
                        "  已学状态保留，但每个单元「上次学到第几个词」会按新划分重算）\n"
                else "\n") +
                "· 自动发音：${if (dAuto) "开" else "关"}\n" +
                "· 显示短语搭配：${if (dColl) "开" else "关"}\n" +
                "· 显示例句：${if (dEx) "开" else "关"}\n\n" +
                "保存后立即生效。",
            confirmText = "确认保存",
            onConfirm = {
                core.updateUnitSize(dUnit)
                core.updateAutoPronounce(dAuto)
                core.updateShowCollocations(dColl)
                core.updateShowExamples(dEx)
                showSaveConfirm = false
                savedMsg = "已保存，设置已生效"
            },
            onDismiss = { showSaveConfirm = false }
        )
    }

    // ---- 未保存就返回 ----
    if (showLeaveConfirm) {
        ConfirmDialog(
            title = "放弃未保存的更改？",
            message = "你有改动还没保存。选择「放弃并返回」将丢弃这些改动，" +
                "设置保持原样。",
            confirmText = "放弃并返回",
            dismissText = "继续编辑",
            danger = true,
            onConfirm = {
                showLeaveConfirm = false
                pop()
            },
            onDismiss = { showLeaveConfirm = false }
        )
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 词条详情：由派生词点击进入 */
@Composable
fun WordDetailScreen(core: AppCore, packId: String, wordId: String, push: (Screen) -> Unit, pop: () -> Unit) {
    val pack = core.packs.firstOrNull { it.manifest.packId == packId } ?: return
    val w = core.findWord(pack, wordId)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        EwordTopBar("词条详情", onBack = pop, subtitle = pack.manifest.name)

        if (w == null) {
            Centered { Text("该词未被本词本收录", fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@Column
        }

        // 从形近词/近义词跳转到另一个词时滚动位置归零，保证新词从顶部开始显示
        val scrollState = remember(wordId) { ScrollState(0) }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            // 三处共用同一份排版：识记卡片、温习最终揭示、此处详情页，完全一致
            WordFullBody(
                core = core,
                pack = pack,
                w = w,
                onOpenForm = { push(Screen.WordDetail(packId, it.lowercase())) }
            )

            Spacer(Modifier.height(28.dp))
            val st = core.stateOf(packId, w.id)
            Text(
                "当前状态：" + when (st) {
                    WordState.NEW -> "未识记"
                    WordState.REVIEW1 -> "温习一"
                    WordState.REVIEW2 -> "温习二"
                    WordState.DONE -> "已彻底记住"
                },
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(48.dp))
        }
    }
}

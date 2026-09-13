package com.eword.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eword.app.data.AppCore
import com.eword.app.data.WordState

/** 温习功能区首页：温习一 / 温习二 / 最终页面 */
@Composable
fun ReviewHomeScreen(core: AppCore, push: (Screen) -> Unit, pop: () -> Unit) {
    val pack = core.enabledPack
    if (pack == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), Alignment.Center) {
            Text("请先到「我的单词本」导入并启用一个词本", fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val packId = pack.manifest.packId
    val r1 = core.countOf(packId, WordState.REVIEW1)
    val r2 = core.countOf(packId, WordState.REVIEW2)
    val done = core.countOf(packId, WordState.DONE)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        EwordTopBar("温习功能区", onBack = pop, subtitle = pack.manifest.name)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StageCard("温习一", "识记里「记住了」的单词", r1, AccentGreen) {
                push(Screen.Review(-1, 1))
            }
            StageCard("温习二", "温习一里想起来的单词，再巩固一遍", r2, AccentGreen) {
                push(Screen.Review(-2, 2))
            }
            StageCard("最终页面", "已彻底记住，不再出现", done, AccentBlue) {
                push(Screen.DoneList)
            }
        }
    }
}

@Composable
private fun StageCard(title: String, desc: String, count: Int, accent: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(cardShape())
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text("$count 词", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = accent)
        }
        Spacer(Modifier.height(4.dp))
        Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 温习单元选择（stage=1/2），unit=-1/-2 表示从功能区首页进入 */
@Composable
fun ReviewScreen(core: AppCore, unit: Int, stage: Int, push: (Screen) -> Unit, pop: () -> Unit) {
    val pack = core.enabledPack ?: return
    val state = if (stage == 1) WordState.REVIEW1 else WordState.REVIEW2

    if (unit < 0) {
        val key = "rv${stage}_${pack.manifest.packId}"
        UnitListScreen(
            title = if (stage == 1) "温习一" else "温习二",
            subtitle = "每单元最多 ${core.unitSize} 词",
            units = core.units(pack, state),
            pendingLabel = "待温习",
            onBack = pop,
            onPick = { push(Screen.Review(it, stage)) },
            initialScrollIndex = core.unitScrollIndexOf(key),
            onScrollIndexChanged = { core.setUnitScrollIndex(key, it) }
        )
        return
    }

    val packId = pack.manifest.packId
    var round by remember { mutableIntStateOf(0) }
    val queue = remember(unit, stage, packId, round) { core.queue(pack, unit, state) }
    var idx by remember(round) { mutableIntStateOf(0) }

    LaunchedEffect(idx, round) {
        if (core.autoPronounce && idx < queue.size) {
            val w = queue[idx]
            core.pronouncer.pronounce(packId, w.id, w.word)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        EwordTopBar(
            "${if (stage == 1) "温习一" else "温习二"} · 第 ${unit + 1} 单元",
            onBack = pop,
            subtitle = if (queue.isEmpty()) null else "${idx.coerceAtMost(queue.size)} / ${queue.size}"
        )

        if (queue.isEmpty()) {
            Centered { Text("本单元没有待温习的单词", fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@Column
        }

        if (idx >= queue.size) {
            Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("本单元已过完", fontSize = 20.sp, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(10.dp))
                    Text("共 ${queue.size} 词", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    OutlinedButton(onClick = pop, shape = cardShape()) { Text("返回单元列表") }
                }
            }
            return@Column
        }

        val w = queue[idx]

        // 每次换到新卡片都从「只露单词」开始，不继承上一次的揭示进度。
        // 上一次的揭示若不清，重新进来会直接看到答案，等于没温习。
        LaunchedEffect(w.id) { core.clearReveal(packId, w.id) }

        // 揭示进度存在 AppCore 里（按 词包|词 记），而不是 remember 里：
        // 从形近词/近义词跳到词条详情再返回时，页面重新组合，remember 会丢，
        // 存起来才能让这一次温习里已经翻开的例句不白翻。
        // 它不跨次：换卡片、判定、重新进入都会清掉，下次进来仍是光单词。
        val revealed = core.revealCountOf(packId, w.id)
        val showMeaning = core.revealFullOf(packId, w.id)

        // 换词时滚动位置归零：否则上一个词滚到下方后，新词会停在半路、看不到单词本身
        val scrollState = remember(w.id) { ScrollState(0) }
        val known = core.vocabIds(pack)

        // 例句与意思全部展示完毕 → finished
        val finished = showMeaning
        // 判定按钮的统一开关：**必须先看过完整答案**
        // （revealFull 由「显示完整答案（核对用）」或「显示例句意思」置位），
        // 否则按钮一律不可点，避免没看就过。
        // 例外：没有例句的词无从核对，直接放行，否则按钮会永远点不动。
        val canJudge = w.examples.isEmpty() || showMeaning
        // 两条「看答案」的路径性质完全不同，必须分开处理：
        //
        //   逐条展示例句，一条条翻到底 —— 靠提示才想得起来，就是没记住。
        //                              自动定案「没记住」，不给改判。
        //   显示完整答案（核对用）    —— 只是核对有没有记错，**不代表不会**。
        //                              不自动判定，看完后仍由用户在
        //                              「没记住 / 记住了」里自己选。
        //
        // 早先版本把第二条也强制成「没记住」，后果是任何词都走不到「记住了」，
        // 温习二与最终页面永远进不去，整条流程断死。
        val exhausted = w.examples.isNotEmpty() && revealed >= w.examples.size
        val autoForgetful = finished && revealed > 0 && exhausted

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            if (!finished) {
                // 阶段一：只露出单词、音标与发音，其余一概不可见
                WordHead(w.word, w.phonetic) { core.pronouncer.pronounce(packId, w.id, w.word) }

                if (core.showExamples && w.examples.isNotEmpty()) {
                    Spacer(Modifier.height(28.dp))
                    if (revealed == 0) {
                        Text("想不起来？可以逐条看例句；只是确认一下就点「显示完整答案」",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                    }
                    // 阶段一：只显示英文句子，不给翻译
                    ExampleList(w.examples.take(revealed), showTranslation = false,
                                highlight = w.word, known = known,
                                suffix = w.allSenses.any { s ->
                                    val p = s.pos.lowercase()
                                    p.contains("suffix") || p.contains("suf.")
                                })

                    Spacer(Modifier.height(14.dp))
                    if (revealed < w.examples.size) {
                        OutlinedButton(
                            onClick = { core.setRevealCount(packId, w.id, revealed + 1) },
                            shape = cardShape()
                        ) {
                            Text(if (revealed == 0) "逐条展示例句"
                            else "下一句（${revealed}/${w.examples.size}）")
                        }
                    } else {
                        // 例句已全部露完。此时看意思 = 承认没记住，直接定案且不许改判。
                        OutlinedButton(
                            onClick = {
                                // 展开答案即定案「没记住」，留在本功能区：
                                // 温习二的退回温习一，温习一的原地留住。
                                core.setState(packId, w.id, WordState.REVIEW1)
                                core.setRevealFull(packId, w.id, true)
                            },
                            shape = cardShape()
                        ) {
                            Text("显示例句意思")
                        }
                    }
                }

                // 自认记住了、只想核对一下：直接铺开完整答案。
                // 这一步**不代替用户判定**——核对完仍由用户在「记住了 / 没记住」里选。
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { core.setRevealFull(packId, w.id, true) },
                    shape = cardShape()
                ) { Text("显示完整答案（核对用）") }
                Spacer(Modifier.height(6.dp))
                Text(
                    "只是确认自己有没有记错，展开后仍然由你决定「记住了 / 没记住」",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // 阶段三：完整卡片（与识记功能区完全一致）
                WordFullBody(
                    core = core,
                    pack = pack,
                    w = w,
                    onOpenForm = { push(Screen.WordDetail(packId, it.lowercase())) }
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    // 两种「看过答案」的路径，性质不同，文案必须分开：
                    //   逐条看完全部例句 → 已直接记为「没记住」，不给改判
                    //   直接核对          → 不代替判定，等用户自己选
                    when {
                        autoForgetful ->
                            "例句需要一条条提示才看得下来，本词已记为「没记住」"
                        else ->
                            "已显示完整答案，请自行判断是否记住了，再选择下方按钮"
                    },
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(28.dp))
        }

        Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            // 只有「逐条揭示到底」才自动定案，此时两个判定按钮锁死：
            // 本词留在原功能区，用户只能往下走，翻不回「记住了」。
            // 「显示完整答案（核对用）」不定案，两个按钮照常可用。
            val locked = autoForgetful

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        if (stage == 2) core.setState(packId, w.id, WordState.REVIEW1)
                        core.clearReveal(packId, w.id)
                        idx++
                    },
                    // 必须先看过完整答案才可判定（没有例句的词除外），
                    // 避免"没看就点记住了"把单词跳过。
                    enabled = canJudge && !locked,
                    shape = cardShape(),
                    modifier = Modifier.weight(1f)
                ) { Text(if (locked) "已记为没记住" else "没记住") }

                Button(
                    onClick = {
                        core.setState(
                            packId, w.id,
                            if (stage == 1) WordState.REVIEW2 else WordState.DONE
                        )
                        core.clearReveal(packId, w.id)
                        idx++
                    },
                    enabled = canJudge && !locked,
                    shape = cardShape(),
                    modifier = Modifier.weight(1f)
                ) { Text("记住了") }
            }

            if (locked) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        core.clearReveal(packId, w.id)
                        idx++
                    },
                    shape = cardShape(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("下一个") }
                Spacer(Modifier.height(6.dp))
                Text(
                    // 温习二的词退回温习一，温习一的词原地留住：两种情况都留在温习里
                    "本词已记为「没记住」，留在温习一，不会进入" +
                        (if (stage == 2) "最终页面" else "温习二"),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (w.examples.isEmpty()) {
                // 这个词在真题里没有可用的例句/译文，无从核对，直接判定
                Spacer(Modifier.height(6.dp))
                Text(
                    "本词在真题里没有可用例句，直接判断是否记住即可",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (!canJudge) {
                // 还没看过答案：两个判定按钮都是灰的，这里说清为什么
                Spacer(Modifier.height(6.dp))
                Text(
                    "请先点上方「显示完整答案（核对用）」看过释义，再判断是否记住",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.error
                )
            } else if (showMeaning) {
                // 看完答案后才说这条：此时按钮刚变成可点，得讲清两个方向各去哪里
                Spacer(Modifier.height(6.dp))
                Text(
                    if (stage == 1) "确认记对了 → 进入温习二；不对 → 留在温习一"
                    else "确认记对了 → 进入最终页面；不对 → 退回温习一",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (stage == 1) "「记住了」→ 进入温习二"
                    else "「记住了」→ 进入最终页面",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 最终页面：已彻底记住的单词。可把某词退回「未识记」，重新走一遍识记流程。 */
@Composable
fun DoneListScreen(core: AppCore, push: (Screen) -> Unit, pop: () -> Unit) {
    val pack = core.enabledPack ?: return
    val packId = pack.manifest.packId
    val doneCount = core.countOf(packId, WordState.DONE)
    val words = remember(packId, doneCount) {
        core.words(pack).filter { core.stateOf(packId, it.id) == WordState.DONE }
    }
    var confirm by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        EwordTopBar("最终页面", onBack = pop, subtitle = "已彻底记住 ${words.size} 词")
        if (words.isEmpty()) {
            Centered { Text("还没有彻底记住的单词", fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(words) { w ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(cardShape())
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { push(Screen.WordDetail(packId, w.id)) }
                    ) {
                        Text(w.word, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("第 ${core.unitIndexOf(pack, w.id) + 1} 单元", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(
                        onClick = { confirm = w.id },
                        shape = cardShape()
                    ) { Text("再记一次", fontSize = 12.sp) }
                }
            }
        }
    }

    confirm?.let { wid ->
        val w = core.findWord(pack, wid)
        ConfirmDialog(
            title = "再记一次？",
            message = "「${w?.word ?: wid}」将退回为「未识记」状态：\n" +
                "· 从最终页面移除\n" +
                "· 重新出现在识记功能区，可以再走一遍识记 → 温习一 → 温习二",
            confirmText = "确认退回",
            onConfirm = {
                core.setState(packId, wid, WordState.NEW)
                core.clearReveal(packId, wid)
                // 让它重新从识记队列开始
                core.clearMemorize(packId, core.unitIndexOf(pack, wid).coerceAtLeast(0))
                confirm = null
            },
            onDismiss = { confirm = null }
        )
    }
}

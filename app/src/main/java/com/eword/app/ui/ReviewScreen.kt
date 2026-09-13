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
import androidx.compose.runtime.DisposableEffect
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
import com.eword.app.data.ReviewFlow
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
            // 计数为 0 时卡片不可点：点进去只会看到一屏点不动的灰条，
            // 用户分不清是「还没学过」还是「App 坏了」
            StageCard("温习一", "识记里「记住了」的单词", r1, AccentGreen,
                hint = "先在识记功能区把单词记一遍") { push(Screen.Review(-1, 1)) }
            StageCard("温习二", "温习一里想起来的单词，再巩固一遍", r2, AccentGreen,
                hint = "先在温习一里过一遍") { push(Screen.Review(-2, 2)) }
            StageCard("最终页面", "已彻底记住，不再出现", done, AccentBlue,
                hint = "还没有彻底记住的词") { push(Screen.DoneList) }
        }
    }
}

@Composable
private fun StageCard(
    title: String,
    desc: String,
    count: Int,
    accent: androidx.compose.ui.graphics.Color,
    hint: String,
    onClick: () -> Unit
) {
    val usable = count > 0
    Column(
        Modifier
            .fillMaxWidth()
            .clip(cardShape())
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = usable) { onClick() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                color = if (usable) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
            Text("$count 词", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                color = if (usable) accent else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (usable) desc else "暂时没有可温习的词 —— $hint",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
            onScrollIndexChanged = { core.setUnitScrollIndex(key, it) },
            onLeaving = { core.flushUnitScroll(key) },
            emptyText = if (stage == 1)
                "温习一是识记里「记住了」的词，现在还没有。先去识记功能区把单词记一遍吧。"
            else "温习二是温习一里想起来的词，现在还没有。先去温习一吧。"
        )
        return
    }

    val packId = pack.manifest.packId
    // 判定规则的自检：只在第一次进温习时真跑一遍。规则被改坏时当场抛异常，
    // 比等用户点进温习、发现判定按钮全灰了再回头查要快得多。
    remember { ReviewFlow.selfCheck() }
    var round by remember { mutableIntStateOf(0) }
    val queue = remember(unit, stage, packId, round) { core.queue(pack, unit, state) }

    // 位置记在 AppCore 里：点近义词/形近词跳去词条详情时本页会离开组合，
    // remember 里的下标随之归零，回到原本的卡就回不去了（识记页早有这套机制，
    // 温习页一直没有；近义词覆盖扩到六千词之后这个动作会经常发生）。
    var idx by remember(round) {
        val saved = if (round == 0) core.reviewIndexOf(packId, unit, stage) else 0
        mutableIntStateOf(saved.coerceIn(0, queue.size))
    }
    LaunchedEffect(idx, round, queue.size) {
        if (round == 0) {
            if (idx < queue.size) core.setReviewIndex(packId, unit, stage, idx)
            else core.clearReviewIndex(packId, unit, stage)
        }
    }
    DisposableEffect(Unit) { onDispose { core.flushReviewIndex(packId, unit, stage) } }

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
                    // 最后一张判错了也能退回来改。没有这个按钮，「已过完」这一屏就是
                    // 死胡同：误点「记住了」的那个词已经离开本功能区的队列，
                    // 要等下一轮温习才可能再遇到。
                    OutlinedButton(
                        onClick = {
                            val prev = queue[queue.size - 1]
                            core.setState(packId, prev.id, state)
                            core.clearReveal(packId, prev.id)
                            idx = queue.size - 1
                        },
                        shape = cardShape()
                    ) { Text("上一个") }
                    Spacer(Modifier.height(10.dp))
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
        val byHints = core.revealByHintsOf(packId, w.id)

        // 换词时滚动位置归零：否则上一个词滚到下方后，新词会停在半路、看不到单词本身
        val scrollState = remember(w.id) { ScrollState(0) }
        val known = core.vocabIds(pack)

        // 判定规则见 data/ReviewFlow.kt —— 那里写清了每条分支为什么这么判，
        // 并附一份自检覆盖所有状态组合。这里只做取值与调用。
        val canJudge = ReviewFlow.canJudge(showMeaning)
        val autoForgetful = ReviewFlow.autoForgetful(byHints)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            // 未看过答案时才露单词头部；看过之后由下面的完整卡片接管
            if (!showMeaning) {
                WordHead(w.word, w.phonetic) { core.pronouncer.pronounce(packId, w.id, w.word) }
            }

            // 例句提示区：只在**答案还没铺开**时出现在单词下方。
            // 答案一铺开，整张卡片就由下面的 WordFullBody 接管（那里例句在单词下方、
            // 带译文，顺序是单词 → 词义 → 变形 → 形近词 → 近义词 → 搭配 → 例句）。
            // 这里若继续渲染，同一条例句会在单词上方和下方各出现一次。
            if (!showMeaning && core.showExamples && w.examples.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                if (revealed == 0) {
                    Text("想不起来？可以逐条看例句；只是确认一下就点「显示完整答案」",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                }
                // 逐条展示阶段只给英文：译文会先把词义说破，例句就不成其为提示了。
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
                    // 例句已全部露完，还要看译文才想得起来 —— 这就是靠提示才答上来的。
                    // 点下去即定案「没记住」，留在本功能区，不许改判。
                    // 只想核对的人不走这个按钮，他走下面的「显示完整答案」。
                    OutlinedButton(
                        onClick = {
                            // 定案并置「靠提示」标志。温习二的退回温习一，
                            // 温习一的原地留住，两种情况都留在温习里。
                            core.setState(packId, w.id, WordState.REVIEW1)
                            core.setRevealByHints(packId, w.id, true)
                            core.setRevealFull(packId, w.id, true)
                        },
                        shape = cardShape()
                    ) {
                        Text("显示例句意思")
                    }
                }
            }

            // 铺开完整答案。这一步**不代替用户判定** ——
            // 核对完仍由用户在「记住了 / 没记住」里自己选。
            // 例句翻到底之后也是走这里：翻完例句只是确认有没有记错，不是认输。
            //
            // 两道「不能藏」的约束，都是踩过坑之后加的：
            //   ① 不能跟着「显示例句」这个显示项一起藏 —— 判定按钮要看过释义才亮，
            //      而这里是看过释义的唯一入口。绑在一起的后果是关掉「显示例句」的人
            //      在温习里点不动任何判定按钮，整条流程卡死在这一张卡上。
            //   ② 不能跟着「有没有例句」一起藏 —— 全库有一百多个词在真题里
            //      只出现在词库或选项里、查不到完整句子（monk 就是一个）。这类词
            //      例句区本来就空着，若连这个按钮也一起没了，用户就只能对着一张
            //      光秃秃的卡片猜自己记没记住，判断失去依据。
            if (!showMeaning) {
                // 上面若没渲染例句提示区（关掉「显示例句」，或这个词没有例句），
                // 这里要多留一段间距，否则按钮会紧贴在音标下面。
                Spacer(Modifier.height(
                    if (core.showExamples && w.examples.isNotEmpty()) 10.dp else 28.dp
                ))
                Button(
                    onClick = { core.setRevealFull(packId, w.id, true) },
                    shape = cardShape()
                ) { Text("显示完整答案（核对用）") }
                Spacer(Modifier.height(6.dp))
                Text(
                    "只是确认自己有没有记错，展开后仍然由你决定「记住了 / 没记住」",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (showMeaning) {
                Spacer(Modifier.height(24.dp))
                // 完整卡片（与识记功能区完全一致）
                WordFullBody(
                    core = core,
                    pack = pack,
                    w = w,
                    onOpenForm = { push(Screen.WordDetail(packId, it.lowercase())) }
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    // 两条路径性质不同，文案必须分开
                    if (autoForgetful) "这条是靠着例句提示才想起来的，本词已记为「没记住」"
                    else "答案已展开，请自行判断是否记住了，再选择下方按钮",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // 还没看过答案：说清为什么两个判定按钮都是灰的。
                // 关掉「显示例句」时上方并没有「显示例句意思」这个按钮，文案不能提它。
                Spacer(Modifier.height(18.dp))
                Text(
                    if (core.showExamples && w.examples.isNotEmpty())
                        "请先点上方「显示完整答案（核对用）」或「显示例句意思」看过释义，再判断是否记住"
                    else
                        "请先点上方「显示完整答案（核对用）」看过释义，再判断是否记住",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(28.dp))
        }

        Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            // 只有「逐条看例句 → 再看译文」这条路径才自动定案，此时两个判定按钮锁死：
            // 本词留在原功能区，用户只能往下走，翻不回「记住了」。
            // 「显示完整答案（核对用）」不定案，两个按钮照常可用；例句翻到底之后
            // 再点「显示完整答案」同样不定案 —— 那是核对，不是认输。
            val locked = autoForgetful

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // 上一个：回到上一张，并把它复位成「待温习」。
                // 复位是必需的：上一张可能已经被判成温习二/最终页面，状态不写回来，
                // 它就不在本功能区的队列里了，改判也无从生效。
                // 揭示状态一并清掉 —— 回来必须重新看过释义才能再判，
                // 不能因为回退就把「没看过释义不许判定」这条规则破掉。
                OutlinedButton(
                    onClick = {
                        val prev = queue[idx - 1]
                        core.setState(packId, prev.id, state)
                        core.clearReveal(packId, prev.id)
                        idx--
                    },
                    enabled = idx > 0,
                    shape = cardShape()
                ) { Text("上一个") }

                OutlinedButton(
                    onClick = {
                        if (stage == 2) core.setState(packId, w.id, WordState.REVIEW1)
                        core.clearReveal(packId, w.id)
                        idx++
                    },
                    // 必须点过「显示完整答案（核对用）」或「显示例句意思」才可判定。
                    // 例句翻到底不算 —— 没看过释义就点「记住了」等于把词跳过。
                    // 例外：没有例句的词无从核对，直接放行，否则按钮永远点不动。
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
            } else if (!canJudge) {
                // 还没看过答案：两个判定按钮都是灰的，这里说清为什么。
                // 例句翻到头也一样 —— 没点过「显示例句意思」或「显示完整答案」就不算看过释义。
                // 没有例句的词同样走这里：它只有一个「显示完整答案」可点，但必须先点。
                Spacer(Modifier.height(6.dp))
                Text(
                    "请先点上方按钮看过释义，再判断是否记住",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.error
                )
            } else {
                // 看完答案后才说这条：此时按钮刚变成可点，得讲清两个方向各去哪里
                Spacer(Modifier.height(6.dp))
                Text(
                    if (stage == 1) "确认记对了 → 进入温习二；不对 → 留在温习一"
                    else "确认记对了 → 进入最终页面；不对 → 退回温习一",
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
                // 让它重新从识记队列开始。unitIndexOf 找不到这个 id 时返回 -1，
                // 不能 coerce 成 0 —— 那会把第 1 单元的识记进度清掉。
                val u = core.unitIndexOf(pack, wid)
                if (u >= 0) core.clearMemorize(packId, u)
                confirm = null
            },
            onDismiss = { confirm = null }
        )
    }
}

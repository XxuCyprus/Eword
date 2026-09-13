package com.eword.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eword.app.Inflections
import com.eword.app.data.AppCore
import com.eword.app.data.Collocation
import com.eword.app.data.Example
import com.eword.app.data.Sense
import com.eword.app.data.UnitInfo
import com.eword.app.data.WordEntry
import com.eword.app.data.WordPack

// ============ 统一圆角（全应用一致，避免忽大忽小） ============
val RadiusCard = 14.dp
val RadiusChip = 10.dp

val AccentBlue = Color(0xFF2F6BFF)
val AccentGreen = Color(0xFF1D9E75)

/** 词义分组标题里「真题」的颜色：与「大纲补充」的灰区分开，一眼能看出主次 */
private val SensedLabelColor = Color(0xFF1D7A50)

fun cardShape() = RoundedCornerShape(RadiusCard)
fun chipShape() = RoundedCornerShape(RadiusChip)

/**
 * 例句/搭配文本清洗。
 *
 * 真题 PDF 提取出来的文本里混着排版残留字符，会破坏正文排版：
 *   "\u00a0" 不换行空格 → 吃掉断行点，出现「一行只排两三个单词就换行、中间一大段空白」
 *   "\u00ad" 软连字符   → 在词中间插入看不见的断点，把单词劈开
 * 这里统一换成普通空格或直接去掉，并压缩连续空格。
 */
fun normalizeText(s: String): String {
    if (s.isEmpty()) return s
    val sb = StringBuilder(s.length)
    for (ch in s) {
        when (ch) {
            '\u00a0', '\u2007', '\u202f', '\u2009', '\u200a', '\u2002', '\u2003',
            '\u2004', '\u2005', '\u2006', '\u2008', '\t', '\n', '\r' -> sb.append(' ')
            '\u00ad', '\u200b', '\u200c', '\u200d', '\ufeff' -> { /* 丢弃 */ }
            '\u037e' -> sb.append('\u003b')   // 希腊问号 → 普通分号
            else -> sb.append(ch)
        }
    }
    // 压缩连续空格
    return sb.toString().replace(Regex(" {2,}"), " ").trim()
}

@Composable
fun EwordTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            Spacer(Modifier.size(36.dp))
        }
        Column(Modifier.weight(1f).padding(start = 6.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
    }
}

/**
 * 单元选择列表（识记 / 温习 / 我的单词本共用）。
 * 支持记住上一次的滚动位置（从第 32 单元退出后再进来仍停在 32）。
 */
@Composable
fun UnitListScreen(
    title: String,
    subtitle: String,
    units: List<UnitInfo>,
    pendingLabel: String,
    onBack: () -> Unit,
    onPick: (Int) -> Unit,
    initialScrollIndex: Int = 0,
    onScrollIndexChanged: ((Int) -> Unit)? = null
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        EwordTopBar(title, onBack = onBack, subtitle = subtitle)

        if (units.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有可用的单元", fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = initialScrollIndex.coerceAtLeast(0)
        )
        if (onScrollIndexChanged != null) {
            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemIndex }
                    .collect { onScrollIndexChanged(it) }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(units.size) { i ->
                val u = units[i]
                val usable = u.pending > 0
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(cardShape())
                        .background(
                            if (usable) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface
                        )
                        .clickable(enabled = usable) { onPick(u.index) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "第 ${u.index + 1} 单元",
                        Modifier.weight(1f),
                        fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        color = if (usable) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "$pendingLabel ${u.pending} / ${u.total}",
                        fontSize = 13.sp,
                        color = if (usable) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 通用二次确认弹框。
 * 打乱顺序 / 恢复原顺序 / 清空进度 / 删除词本 / 保存设置 等操作都先过它，避免误点。
 * [danger] 为真时确认按钮用警示色。
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "确定",
    dismissText: String = "取消",
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = cardShape(),
        // 弹框一律白底：默认取 SurfaceContainerHigh，在本应用里是一层灰，
        // 叠在白色页面上会显出灰块，不如白底干净。
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, cardShape()),
        title = {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            Text(message, fontSize = 13.sp, lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        confirmButton = {
            // 警示操作直接把按钮底色换成警示色。只把文字改红会变成「蓝底红字」，
            // 对比不足，也看不出是危险操作。
            Button(
                onClick = onConfirm,
                shape = cardShape(),
                colors = if (danger) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = cardShape()) { Text(dismissText) }
        }
    )
}

/** 小区块标题（形近词 / 近义词 / 例句 / 单词变形 …） */
@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

/** 词汇级别徽章：CET4 / CET6 */
@Composable
fun LevelBadge(level: String) {
    if (level.isBlank()) return
    val cet6 = level.equals("CET6", true)
    val bg = if (cet6) Color(0xFFEDE3FF) else Color(0xFFE1F2EA)
    val fg = if (cet6) Color(0xFF6B3FA0) else Color(0xFF1D7A50)
    Box(
        Modifier
            .clip(chipShape())
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(level.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

/**
 * 真题句数徽章。
 *
 * [freq] = 该词在四六级历年真题的逐句语料里出现在**多少个不同的句子**中。
 * 它与下方展示的例句条数是同一个来源，所以：
 *   · freq >= 例句条数 恒成立（例句是这些句子里挑出来的）
 *   · 差别只在于例句每词最多显示 5 条，且按契合度排序取前几条
 * 没有例句的词，freq 就是 0 —— 真题里查不到成句，徽章不显示。
 *
 * 文案用「真题 N 句」而不是「N 次」：「次」容易被理解成出现次数，
 * 而这里数的是句子；也不必再解释「为什么次数和例句数不一样」。
 */
@Composable
fun FreqBadge(freq: Int) {
    if (freq <= 0) return
    val bg: Color
    val fg: Color
    when {
        freq >= 8 -> { bg = Color(0xFFFFE1E1); fg = Color(0xFFB3261E) }
        freq >= 5 -> { bg = Color(0xFFFFF0DA); fg = Color(0xFFB26A00) }
        freq >= 3 -> { bg = Color(0xFFE3EDFF); fg = Color(0xFF2F6BFF) }
        else -> { bg = Color(0xFFEDEDED); fg = Color(0xFF6B6B6B) }
    }
    Box(
        Modifier
            .clip(chipShape())
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text("真题 $freq 句", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = fg)
    }
}

/**
 * 单词头部：
 *   第一行：单词（长词自动换行，不挤占右侧内容）+ 发音按钮
 *   第二行：音标 + CET4/CET6 + 真题出现次数
 *
 * 级别与次数放在第二行而非单词同行：长单词（telecommunications、
 * entrepreneurialism 这类 18 字母词）会把它们整个推出屏幕外。
 */
@Composable
fun WordHead(
    word: String,
    phonetic: String,
    compact: Boolean = false,
    level: String = "",
    freq: Int = 0,
    onSpeak: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                word,
                modifier = Modifier.weight(1f, fill = false),
                fontSize = if (compact) 26.sp else 34.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { onSpeak() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.VolumeUp,
                    contentDescription = "发音",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        if (phonetic.isNotBlank() || level.isNotBlank() || freq > 0) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (phonetic.isNotBlank()) {
                    Text(
                        phonetic,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                }
                LevelBadge(level)
                if (level.isNotBlank() && freq > 0) Spacer(Modifier.width(6.dp))
                FreqBadge(freq)
            }
        }
    }
}

/**
 * 词性 + 意思，按来源分组列出。
 *
 * 义项分两类：真题里出现过的、以及词典补充的（大纲补充）。同一类的义项
 * **共用一个标签**，标签作为该组的小标题放在义项上方，而不是每个义项后面
 * 各挂一遍同样的四个字——那样既重复又看不出分组。
 *
 * 分组时保持词包里的原始顺序：先出现的类别先列出，不重排义项。
 */
@Composable
fun SenseList(senses: List<Sense>) {
    if (senses.isEmpty()) return
    val groups = senses.groupBy { it.label }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        groups.forEach { (label, items) ->
            Column {
                if (label.isNotBlank()) {
                    Text(
                        label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (label == "真题") SensedLabelColor
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.forEach { s ->
                        Row(verticalAlignment = Alignment.Top) {
                            if (s.pos.isNotBlank()) {
                                Text(
                                    s.pos,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(chipShape())
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                s.meaning.ifBlank { "—" },
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 24.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 把句子里的目标词（含常见变形）加粗高亮。
 * 例：manage → manages / managed / managing 都会被加粗。
 *
 * 两种匹配方式：
 * 1) 普通词：\b词[a-z]{0,8}\b，且候选词形必须是**本词包收录的真实词条**。
 *    后一条约束是必需的：th 是序数词后缀（来自 20th/9th），
 *    只按前缀匹配会把 the / that / this 全当成它的变形加粗。
 * 2) 序数词后缀（th/st/nd/rd，词性标为 suffix/suf.）：匹配「数字+后缀」整体，
 *    即 20th / 9th / 18th 整块加粗。\bth\b 这种写法匹配不到 20th，
 *    因为数字与 th 之间没有单词边界。
 */
fun highlightWord(
    text: String,
    word: String,
    known: Set<String> = emptySet(),
    isSuffix: Boolean = false,
    color: Color = AccentBlue
): AnnotatedString {
    if (word.isBlank()) return AnnotatedString(text)

    val re = if (isSuffix) {
        // 序数词后缀：既匹配 20th / 9th，也匹配单独出现的 th（如「th century」）。
        // (?![a-z]) 保证不会把 the/that/this 当成后缀。
        Regex("(?i)(?<![a-z])\\d*\\s*" + Regex.escape(word.lowercase()) + "(?![a-z])")
    } else {
        Regex("(?i)\\b" + Regex.escape(word.lowercase()) + "[a-z]{0,8}\\b")
    }

    return buildAnnotatedString {
        var last = 0
        re.findAll(text).forEach { m ->
            val hit = text.substring(m.range.first, m.range.last + 1)
            val ok = if (isSuffix) {
                true   // 数字+后缀本身就是目标形态
            } else {
                val low = hit.lowercase()
                low == word.lowercase() || (known.isNotEmpty() && low in known)
            }
            if (m.range.first > last) append(text.substring(last, m.range.first))
            if (ok) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) { append(hit) }
            } else {
                append(hit)
            }
            last = m.range.last + 1
        }
        if (last < text.length) append(text.substring(last))
    }
}

/** 该词条是否是词缀类（序数词后缀 th / st / nd / rd） */
private fun WordEntry.isSuffixEntry(): Boolean =
    allSenses.any { s ->
        val p = s.pos.lowercase()
        p.contains("suffix") || p.contains("suf.")
    }

/** 短语搭配及其用法（搭配 + 该词用法放在一起；搭配中的原词加粗） */
@Composable
fun CollocationList(
    list: List<Collocation>,
    usage: String = "",
    highlight: String = "",
    known: Set<String> = emptySet(),
    suffix: Boolean = false
) {
    if (list.isEmpty() && usage.isBlank()) return
    SectionLabel("短语搭配及其用法")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        list.forEach { c ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(cardShape())
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(
                    if (highlight.isBlank()) AnnotatedString(normalizeText(c.phrase))
                    else highlightWord(normalizeText(c.phrase), highlight, known, suffix),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (c.meaning.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(normalizeText(c.meaning), fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (usage.isNotBlank()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(cardShape())
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text("句式", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(2.dp))
                // 词包里最多两条句式，用「 ｜ 」分隔；一条一行比挤成一行好读
                Text(usage.split("｜").joinToString("\n") { it.trim() },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    lineHeight = 21.sp)
            }
        }
    }
}

/** 词形标签行（形近词 / 近义词），被收录的词可点击跳转 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormChipRow(
    label: String,
    forms: List<String>,
    isKnown: (String) -> Boolean,
    onOpen: (String) -> Unit
) {
    if (forms.isEmpty()) return
    SectionLabel(label)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        forms.forEach { f ->
            val known = isKnown(f)
            Box(
                Modifier
                    .clip(chipShape())
                    .background(
                        if (known) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .then(if (known) Modifier.clickable { onOpen(f) } else Modifier)
                    .padding(horizontal = 11.dp, vertical = 7.dp)
            ) {
                Text(
                    f,
                    fontSize = 14.sp,
                    color = if (known) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 单词变形区块（名词复数 / 动词三单·现在分词·过去式·过去分词 / 形容词副词比较级·最高级）。
 * 数据随词包下发，只收真题原文里出现过的形态（见 Models.kt 的 inflections 字段）。
 * 每行一条：左侧变形名称，右侧变形形式。
 */
@Composable
fun InflectionList(forms: List<Inflections.Form>) {
    if (forms.isEmpty()) return
    SectionLabel("单词变形")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        forms.forEach { f ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(cardShape())
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    f.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(chipShape())
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    f.values.joinToString(" / "),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 例句列表；showTranslation=false 时只显示英文句子。句中目标词加粗 */
@Composable
fun ExampleList(
    examples: List<Example>,
    showTranslation: Boolean,
    showSource: Boolean = true,
    highlight: String = "",
    known: Set<String> = emptySet(),
    suffix: Boolean = false
) {
    if (examples.isEmpty()) return
    SectionLabel("例句")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        examples.forEachIndexed { i, e ->
            Column(Modifier.fillMaxWidth()) {
                Row {
                    Text(
                        "${i + 1}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (highlight.isBlank()) AnnotatedString(normalizeText(e.en))
                        else highlightWord(normalizeText(e.en), highlight, known, suffix),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 21.sp
                    )
                }
                if (showTranslation && e.zh.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        normalizeText(e.zh),
                        Modifier.padding(start = 20.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp
                    )
                }
                if (showSource && e.source.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Row(Modifier.padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (e.level.isNotBlank()) {
                            LevelBadge(e.level)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            "—— ${e.source}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 单词详情正文 —— 全应用唯一的一份排版。
 * 识记卡片、温习最终揭示、我的单词本 / 最终页面跳进来的详情页，三处全部调用它。
 * 顺序：单词（音标在下，右侧级别与真题次数）→ 词性+意思 → 单词变形 → 形近词 → 近义词 → 短语搭配及其用法 → 例句
 */
@Composable
fun WordFullBody(
    core: AppCore,
    pack: WordPack,
    w: WordEntry,
    onOpenForm: (String) -> Unit
) {
    // 本词包全部词形，用于「原词加粗」时排除 th→the 这类假词形
    val known = core.vocabIds(pack)
    val suffix = w.isSuffixEntry()

    WordHead(w.word, w.phonetic, level = w.level, freq = w.freq) {
        core.pronouncer.pronounce(pack.manifest.packId, w.id, w.word)
    }

    Spacer(Modifier.height(16.dp))
    SenseList(w.allSenses)

    // 单词变形（词义下方、形近词上方）
    val inflections = Inflections.of(w)
    if (inflections.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        InflectionList(inflections)
    }

    if (w.nearForms.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        FormChipRow("形近词", w.nearForms, { core.contains(pack, it) }, onOpenForm)
    }

    if (w.synonyms.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        FormChipRow("近义词", w.synonyms, { core.contains(pack, it) }, onOpenForm)
    }

    if (core.showCollocations && (w.collocations.isNotEmpty() || w.usage.isNotBlank())) {
        Spacer(Modifier.height(20.dp))
        CollocationList(w.collocations, w.usage, w.word, known, suffix)
    }

    if (core.showExamples && w.examples.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        ExampleList(w.examples, showTranslation = true, highlight = w.word,
                    known = known, suffix = suffix)
    }
}

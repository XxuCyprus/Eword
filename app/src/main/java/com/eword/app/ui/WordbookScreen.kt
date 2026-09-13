package com.eword.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/** 我的单词本功能区 */
@Composable
fun WordbookScreen(core: AppCore, push: (Screen) -> Unit, pop: () -> Unit) {
    var msg by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            msg = core.importPack(uri).fold(
                { "导入成功：$it（已自动启用）" },
                { "导入失败：${it.message}" }
            )
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        EwordTopBar("我的单词本", onBack = pop, subtitle = "可导入多个，同时只能启用一个")

        Column(Modifier.padding(horizontal = 16.dp)) {
            Button(
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape()
            ) { Text("导入单词本（.ewp / .json）") }
            msg?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (core.packs.isEmpty()) {
            Centered { Text("还没有词本，先导入一个吧", fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(core.packs, key = { it.manifest.packId }) { p ->
                val enabled = p.manifest.packId == core.enabledPackId
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(cardShape())
                        .background(
                            if (enabled) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(14.dp)
                ) {
                    // 名称区域可点：进词本详情
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { push(Screen.PackDetail(p.manifest.packId)) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.manifest.name, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            if (enabled) {
                                Text("已启用", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${p.words.size} 词 · ${p.manifest.versionLabel}" +
                                (if (p.manifest.coverage.isNotBlank()) " · ${p.manifest.coverage}" else ""),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (enabled) core.enablePack("") else core.enablePack(p.manifest.packId)
                            },
                            modifier = Modifier.weight(1f),
                            shape = cardShape()
                        ) { Text(if (enabled) "停用" else "启用", fontSize = 12.sp) }
                        OutlinedButton(
                            onClick = { push(Screen.PackDetail(p.manifest.packId)) },
                            modifier = Modifier.weight(1f),
                            shape = cardShape()
                        ) { Text("管理", fontSize = 12.sp) }
                        OutlinedButton(
                            onClick = { pendingDelete = p.manifest.packId },
                            modifier = Modifier.weight(1f),
                            shape = cardShape()
                        ) {
                            Text("删除", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    // 删除确认
    val delId = pendingDelete
    if (delId != null) {
        val delPack = core.packs.firstOrNull { it.manifest.packId == delId }
        ConfirmDialog(
            title = "删除这个词本？",
            message = "将从设备上删除「${delPack?.manifest?.name ?: delId}」：\n" +
                "包括词本数据与随包导入的音频，以及它的学习进度。\n\n" +
                "此操作不可撤销（重新导入 .ewp 可恢复词本，但学习进度无法恢复）。",
            confirmText = "确认删除",
            danger = true,
            onConfirm = {
                core.deletePack(delId)
                pendingDelete = null
                msg = "已删除词本"
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

/**
 * 词本详情：启用/停用、删除词本、打乱/恢复原顺序、清空学习进度、搜索、单元列表。
 * 打乱 / 恢复 / 清空 / 删除 都会先弹二次确认，避免误点。
 */
@Composable
fun PackDetailScreen(core: AppCore, packId: String, push: (Screen) -> Unit, pop: () -> Unit) {
    val pack = core.packs.firstOrNull { it.manifest.packId == packId } ?: return
    val enabled = core.enabledPackId == packId
    val shuffled = core.isShuffled(packId)
    // 搜索词存在 AppCore 里（按 packId 记）：从搜索结果进词条详情再返回时不会丢，
    // 可以接着改（例如已输入 app，再补全成 application）。
    val query = core.searchQueryOf(packId)
    val onQueryChange: (String) -> Unit = { core.setSearchQuery(packId, it) }
    var msg by remember { mutableStateOf<String?>(null) }

    // 二次确认状态：shuffle / restore / reset / delete
    var confirm by remember { mutableStateOf<String?>(null) }

    // 整页滚动：上半部分（启用/打乱/清空/删除）与下半部分（搜索、单元列表）
    // 共用一个 scrollState，任何位置都能上下滑。
    val pageScroll = rememberScrollState()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        EwordTopBar(pack.manifest.name, onBack = pop,
            subtitle = "${pack.words.size} 词 · ${pack.manifest.versionLabel}")

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(pageScroll)
                .padding(horizontal = 16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (enabled) {
                    OutlinedButton(
                        onClick = { core.enablePack("") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = cardShape()
                    ) { Text("当前已启用 · 点击停用") }
                } else {
                    Button(
                        onClick = { core.enablePack(packId) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = cardShape()
                    ) { Text("启用这个词本") }
                }

                // 两个按钮始终并排：左＝打乱，右＝恢复原顺序（点击即时生效，先确认）
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { confirm = "shuffle" },
                        modifier = Modifier.weight(1f),
                        shape = cardShape()
                    ) { Text("打乱单词顺序") }
                    OutlinedButton(
                        onClick = { confirm = "restore" },
                        enabled = shuffled,
                        modifier = Modifier.weight(1f),
                        shape = cardShape()
                    ) { Text("恢复原顺序") }
                }

                Text(
                    if (shuffled) "当前：已打乱（单元划分随之重排，已学状态不受影响）"
                    else "当前：按字母顺序（A→Z）",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 清空本词本的学习进度
                OutlinedButton(
                    onClick = { confirm = "reset" },
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShape()
                ) { Text("清空这个词本的学习进度") }

                // 删除词本
                OutlinedButton(
                    onClick = { confirm = "delete" },
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShape()
                ) {
                    Text("删除这个词本", color = MaterialTheme.colorScheme.error)
                }

                msg?.let {
                    Text(it, fontSize = 12.sp, color = AccentGreen)
                }
            }

            Spacer(Modifier.height(14.dp))

            // 搜索：输入单词 → 显示它属于哪个单元 → 点击进入详情页
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = cardShape(),
                placeholder = { Text("搜索单词，例如 address", fontSize = 14.sp) }
            )
            Spacer(Modifier.height(10.dp))

            val q = query.trim().lowercase()
            if (q.isNotEmpty()) {
                // 搜索结果：最多 60 条，有界，跟整页一起滚
                val hits = core.search(pack, q)
                if (hits.isEmpty()) {
                    Text("没有找到匹配的单词", fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        hits.forEach { hit ->
                            val w = hit.first
                            val uidx = hit.second
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(cardShape())
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { push(Screen.WordDetail(packId, w.id)) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(w.word, Modifier.weight(1f), fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface)
                                Text("第 ${uidx + 1} 单元", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
                return@Column
            }

            Text(
                "每单元 ${core.unitSize} 词",
                fontSize = 12.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))

            // 单元列表：整页滚动的普通 Column，与上半部分连成一体，
            // 不用 LazyColumn 以免嵌套滚动互相抢占。
            val unitCount = core.unitCount(pack)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until unitCount) {
                    val size = core.unitWords(pack, i).size
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(cardShape())
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { push(Screen.PackUnit(packId, i)) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("第 ${i + 1} 单元", Modifier.weight(1f), fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("$size 词", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // ---------- 二次确认弹框 ----------
    when (confirm) {
        "shuffle" -> ConfirmDialog(
            title = "打乱单词顺序？",
            message = "打乱后单元划分会随之重排（已学状态不受影响）。\n" +
                "可随时点「恢复原顺序」回到字母序。",
            confirmText = "确认打乱",
            onConfirm = {
                core.reshuffle(packId)
                confirm = null
                msg = "已打乱单词顺序"
            },
            onDismiss = { confirm = null }
        )

        "restore" -> ConfirmDialog(
            title = "恢复原顺序？",
            message = "将恢复为字母顺序（A→Z），单元划分随之重排。\n" +
                "已学状态不受影响。",
            confirmText = "确认恢复",
            onConfirm = {
                core.setShuffled(packId, false)
                confirm = null
                msg = "已恢复字母顺序"
            },
            onDismiss = { confirm = null }
        )

        "reset" -> ConfirmDialog(
            title = "清空学习进度？",
            message = "将把「${pack.manifest.name}」里全部单词的学习状态重置为未识记，\n" +
                "识记进度与温习进度一并清空。\n\n" +
                "词本内容（单词、释义、例句、音频）不受影响。此操作不可撤销。",
            confirmText = "确认清空",
            danger = true,
            onConfirm = {
                core.resetPack(packId)
                confirm = null
                msg = "已清空「${pack.manifest.name}」的学习进度"
            },
            onDismiss = { confirm = null }
        )

        "delete" -> ConfirmDialog(
            title = "删除这个词本？",
            message = "将从设备上删除「${pack.manifest.name}」：\n" +
                "包括词本数据与随包导入的音频，以及它的学习进度。\n\n" +
                "此操作不可撤销（重新导入 .ewp 可恢复词本，但学习进度无法恢复）。",
            confirmText = "确认删除",
            danger = true,
            onConfirm = {
                core.deletePack(packId)
                confirm = null
                pop()
            },
            onDismiss = { confirm = null }
        )
    }
}

/** 某个单元的词表（本区只显示光溜溜的单词，点击进详情页） */
@Composable
fun PackUnitScreen(core: AppCore, packId: String, unit: Int, push: (Screen) -> Unit, pop: () -> Unit) {
    val pack = core.packs.firstOrNull { it.manifest.packId == packId } ?: return
    val ws = core.unitWords(pack, unit)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        EwordTopBar(
            "第 ${unit + 1} 单元",
            onBack = pop,
            subtitle = "${ws.size} 词 · ${pack.manifest.name}"
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(ws, key = { it.id }) { w ->
                Text(
                    w.word,
                    Modifier
                        .fillMaxWidth()
                        .clip(cardShape())
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { push(Screen.WordDetail(packId, w.id)) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

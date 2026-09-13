package com.eword.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.eword.app.data.AppCore

/** 页面路由 */
sealed interface Screen {
    data object Home : Screen

    // 识记功能区
    data object MemorizeUnits : Screen
    data class Memorize(val unit: Int) : Screen

    // 温习功能区
    data object ReviewHome : Screen
    data class Review(val unit: Int, val stage: Int) : Screen
    data object DoneList : Screen

    // 我的单词本功能区
    data object Wordbook : Screen
    data class PackDetail(val packId: String) : Screen

    /** 词本内某个单元的词表（独立页面，左上角逐级返回） */
    data class PackUnit(val packId: String, val unit: Int) : Screen

    // 词条详情（由派生词点击进入）
    data class WordDetail(val packId: String, val wordId: String) : Screen

    // 设置功能区
    data object Settings : Screen
}

@Composable
fun AppRoot(core: AppCore) {
    var stack by remember { mutableStateOf<List<Screen>>(listOf(Screen.Home)) }

    val push: (Screen) -> Unit = { stack = stack + it }
    val pop: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }

    // 系统返回键：只要还在二级及以下页面就退栈，不再直接退出 APP
    BackHandler(enabled = stack.size > 1) { pop() }

    when (val s = stack.last()) {
        is Screen.Home -> HomeScreen(core, push)

        is Screen.MemorizeUnits -> MemorizeUnitsScreen(core, push, pop)
        is Screen.Memorize -> MemorizeScreen(core, s.unit, push, pop)

        is Screen.ReviewHome -> ReviewHomeScreen(core, push, pop)
        is Screen.Review -> ReviewScreen(core, s.unit, s.stage, push, pop)
        is Screen.DoneList -> DoneListScreen(core, push, pop)

        is Screen.Wordbook -> WordbookScreen(core, push, pop)
        is Screen.PackDetail -> PackDetailScreen(core, s.packId, push, pop)
        is Screen.PackUnit -> PackUnitScreen(core, s.packId, s.unit, push, pop)

        is Screen.WordDetail -> WordDetailScreen(core, s.packId, s.wordId, push, pop)

        is Screen.Settings -> SettingsScreen(core, pop)
    }
}

package com.eword.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6BFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3ECFF),
    onPrimaryContainer = Color(0xFF0B2E7A),
    secondary = Color(0xFF5A6472),
    onSecondary = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14161A),
    surfaceVariant = Color(0xFFEFF1F4),
    onSurfaceVariant = Color(0xFF5A6472),
    outline = Color(0xFFD8DCE2),
    outlineVariant = Color(0xFFE6E9ED),
    error = Color(0xFFC0392B),

    // 下面这组「表面容器」色阶 Material 3 组件会直接取用，不定义就落到
    // Material 自带的基准色（淡紫灰 0xFFECE6F0），在纯白页面上会显出一层紫灰。
    // 弹框底色、开关轨道、未选中状态都从这里取值，统一成本应用的浅灰。
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = Color(0xFFF2F4F7),
    surfaceContainerHigh = Color(0xFFEFF1F4),
    surfaceContainerHighest = Color(0xFFE9ECF1)
)

@Composable
fun EwordTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}

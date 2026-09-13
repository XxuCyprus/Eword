package com.eword.app

import com.eword.app.data.WordEntry

/**
 * 单词变形的展示。
 *
 * 变形内容**不在这里生成**：数据随词包下发，词包构建时已经逐个查证过
 * 每个形态都在四六级真题原文里出现过（见 Models.kt 的 inflections 字段）。
 * 这里只负责把词包里的映射按固定顺序整理成可渲染的列表。
 */
object Inflections {

    /** 一条变形：名称 + 形式列表 */
    data class Form(val label: String, val values: List<String>)

    /** 展示顺序：名词 → 动词 → 形容词（含副词派生），与语法书的习惯一致 */
    private val ORDER = listOf(
        "复数", "第三人称单数", "现在分词", "过去式", "过去分词",
        "比较级", "最高级", "副词"
    )

    /**
     * 取该词条已证实的变形，按 [ORDER] 排序。
     * 词包里没登记的名称一概不显示——没有查证过就不摆出来。
     */
    fun of(w: WordEntry): List<Form> {
        if (w.inflections.isEmpty()) return emptyList()
        val out = ArrayList<Form>(w.inflections.size)
        for (label in ORDER) {
            val vs = w.inflections[label]?.filter { it.isNotBlank() } ?: continue
            if (vs.isNotEmpty()) out.add(Form(label, vs))
        }
        return out
    }
}

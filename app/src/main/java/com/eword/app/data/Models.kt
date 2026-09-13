package com.eword.app.data

import com.google.gson.annotations.SerializedName

/** 一条真题例句 */
data class Example(
    @SerializedName("en") val en: String = "",
    @SerializedName("zh") val zh: String = "",
    /** 出处，如「2023.6 第1套」 */
    @SerializedName("source") val source: String = "",
    /** 该例句所属级别：CET4 / CET6 */
    @SerializedName("level") val level: String = ""
)

/**
 * 一个词性 + 该词性下的意思，一一对应。
 * 例如 {pos:"n.", meaning:"平均水平"} / {pos:"v.", meaning:"处理，应对"}
 */
data class Sense(
    @SerializedName("pos") val pos: String = "",
    @SerializedName("meaning") val meaning: String = "",
    /**
     * 该义项的依据：
     *   "真题"     —— 这个意思在四六级真题里确实出现过（词汇例句的译文里能对上）
     *   "大纲补充" —— 真题里没出现过，是词典补充的义项，供了解全貌
     * 空字符串表示旧词包没有这个字段，按「不标注」处理。
     */
    @SerializedName("evidence") val evidence: String = ""
) {
    /** 是否在真题里出现过 */
    val fromExam: Boolean get() = evidence == "真题"

    /**
     * 分组用的标签。同一词的义项按它分组，同组共用一个标题。
     * 旧词包没有 evidence 字段时给空标签，整组不显示标题。
     */
    val label: String get() = when (evidence) {
        "真题" -> "真题"
        "大纲补充" -> "大纲补充"
        else -> ""
    }
}

/** 一条短语搭配及其意思（取自真题原文与官方译文） */
data class Collocation(
    @SerializedName("phrase") val phrase: String = "",
    @SerializedName("meaning") val meaning: String = ""
)

/**
 * 一个词条。
 *
 * 完整词包的字段填充情况（10,693 词）：
 *   senses        全部有值，合计 12,093 条；其中真题里出现过的 10,424 条，
 *                 词典补充的 1,669 条（标为「大纲补充」）
 *   examples      10,570 词有，合计 39,687 条（上限 5 条/词）
 *   collocations   2,316 词有，合计 3,719 条（只收跨卷出现的搭配）
 *   usage            948 词有（句式模板，只收真题例句里反复出现的）
 *   nearForms      6,594 词有，合计 18,762 条（只收拼写真的相近、会认错的词）
 *   synonyms       1,952 词有，合计 2,810 条（双向互认且共同义项是常用义）
 *   inflections    2,662 词有，合计 4,859 条（只收真题原文里出现过的形态）
 * 例句与搭配取自四六级真题原文与官方译文；变形、形近词、近义词的做法见各自字段说明。
 * phonetic / level 允许来自其他来源。
 *
 * 123 个词条没有例句、freq 也记为 0：这些词在真题里只出现在选词填空的**词库**或
 * 选择题的选项里，卷面上没有一句完整用到它的英文。词义来自大纲，例句留空。
 */
data class WordEntry(
    @SerializedName("id") val id: String = "",
    @SerializedName("word") val word: String = "",
    @SerializedName("phonetic") val phonetic: String = "",
    /** 词汇级别：CET4 或 CET6，按词表登记 */
    @SerializedName("level") val level: String = "",
    /**
     * 该词在历年真题的逐句语料里出现在**多少个不同的句子**中。
     *
     * 与 [examples] 同源，所以 freq >= 例句条数恒成立：
     *   freq     = 核验合格、含该词的语料句子数（本词的任何形态都算）
     *   examples = 这些句子里按契合度排序取的前 5 条
     * 真题里只能查到词库/选项、查不到成句的词，freq 为 0，界面上不显示徽章。
     */
    @SerializedName("freq") val freq: Int = 0,
    /** 词性 + 意思，一对一（按词性分组，不再混在一起） */
    @SerializedName("senses") val senses: List<Sense> = emptyList(),
    @SerializedName("collocations") val collocations: List<Collocation> = emptyList(),
    /**
     * 该词在真题里的典型句式，如「manage to do sth」。
     *
     * 多人句式用「 ｜ 」分隔（最多 2 条）。只收在**已核验的真题例句**里
     * 反复出现、且介词/补足语稳定跟在词后的模板；不足 2 次或只是某一句里
     * 恰好挨着的组合不收。
     */
    @SerializedName("usage") val usage: String = "",
    /** 形近词：按词形相似度得出，非严格派生 */
    @SerializedName("nearForms") val nearForms: List<String> = emptyList(),
    /** 近义词：意思相近（与词形无关） */
    @SerializedName("synonyms") val synonyms: List<String> = emptyList(),
    /**
     * 单词变形：名词复数 / 动词三单·现在分词·过去式·过去分词 / 形容词副词比较级·最高级。
     *
     * 与旧做法（应用按语法规则现场生成）的区别：这里的每一个形态都在
     * **四六级真题原文里真实出现过**，词包构建时逐个查证过；没出现过的形态不收。
     * 所以「长词硬凑比较级」「名词硬塞动词形式」这类问题从数据上就不存在。
     * 键是变形名称，顺序已在词包里排好（名词 → 动词 → 形容词）。
     * 空对象表示这个词查证过、确实没有可收的变形。
     */
    @SerializedName("inflections") val inflections: Map<String, List<String>> = emptyMap(),
    @SerializedName("examples") val examples: List<Example> = emptyList(),
    /** 兼容旧包：旧格式的单一词性与单一释义 */
    @SerializedName("pos") val legacyPos: String = "",
    @SerializedName("meaning") val legacyMeaning: String = ""
) {
    /** 词性-意思对；旧包（无 senses）时用 legacyPos/legacyMeaning 兜底 */
    val allSenses: List<Sense>
        get() = senses.ifEmpty {
            if (legacyPos.isBlank() && legacyMeaning.isBlank()) emptyList()
            else listOf(Sense(legacyPos, legacyMeaning))
        }
}

/** 词包元信息 */
data class PackManifest(
    @SerializedName("packId") val packId: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("version") val version: Int = 1,
    @SerializedName("coverage") val coverage: String = "",
    @SerializedName("wordCount") val wordCount: Int = 0,
    /**
     * 该词包要求的最低应用版本，如 "4.1.0"。留空表示不限。
     *
     * 应用与词包各自独立发版，就必然出现「新包配旧应用」的组合。
     * 由词包声明要求、应用自己比对，才能在导入前给出提示，
     * 而不是等用户发现界面上少了一块却不知道原因。
     */
    @SerializedName("minAppVersion") val minAppVersion: String = ""
) {
    /**
     * 词包版本号。
     *
     * 词包与 App 各自独立发版，所以这个号必须让用户看得到 ——
     * 否则「你更新词包了吗」会变成一笔糊涂账：App 内看不到手上的包是第几版。
     */
    val versionLabel: String get() = "v$version"

    /** 本机应用版本是否满足这个词包的要求 */
    fun compatibleWith(appVersion: String?): Boolean =
        PackCompat.satisfied(appVersion, minAppVersion)
}

/** 一个完整词包 */
data class WordPack(
    @SerializedName("manifest") val manifest: PackManifest = PackManifest(),
    @SerializedName("words") val words: List<WordEntry> = emptyList()
)

/**
 * 单词在学习链路中的位置。
 * NEW → 识记·记住了 → REVIEW1 → 温习一·记住了 → REVIEW2 → 温习二·记住了 → DONE
 */
enum class WordState { NEW, REVIEW1, REVIEW2, DONE }

/** 一个单元（默认 52 词一组） */
data class UnitInfo(
    val index: Int,
    val total: Int,
    val pending: Int
)

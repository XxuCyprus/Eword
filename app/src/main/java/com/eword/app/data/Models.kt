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
     *   "大纲补充" —— 真题里没出现过，是按词汇大纲（借助 ECDICT 词典数据）补充的义项
     * 空字符串表示旧词包没有这个字段，按「不标注」处理。
     */
    @SerializedName("evidence") val evidence: String = ""
) {
    /** 是否在真题里出现过 */
    val fromExam: Boolean get() = evidence == "真题"

    /**
     * 分组用的标签。同一词的义项按它分组，同组共用一个标题。
     * 旧词包没有 evidence 字段时给空标签，整组不显示标题。
     *
     * 补充义项在词包里存的值是 "大纲补充"（旧包也这么存，改了会让新旧包不一致），
     * 界面上显示为「真题补充」。
     */
    val label: String get() = when (evidence) {
        "真题" -> "真题"
        "大纲补充" -> "真题补充"
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
 * 词包 v16 的字段填充情况（10,684 词）：
 *   senses        全部有值，合计 26,068 条（真题 10,865 条、大纲补充 15,203 条）
 *                 每词条数分布：1 条 2,908 · 2 条 2,685 · 3 条 3,103 · 4 条 1,479 ·
 *                 5 条 490 · 6 条 18 · 7 条 1
 *   examples      10,547 词有，合计 37,891 条（上限 5 条/词）
 *   collocations   2,316 词有，合计 3,719 条（只收跨卷出现的搭配）
 *   usage            948 词有（句式模板，只收真题例句里反复出现的）
 *   nearForms      3,807 词有，合计 12,642 条（只收不同族、拼写真的相近的词）
 *   synonyms       7,230 词有，合计 21,026 条（共同义项必须是双方都列出的主要义项）
 *   inflections    2,662 词有，合计 4,859 条（只收真题原文里出现过的形态）
 * 例句与搭配取自四六级真题原文与官方译文；变形、形近词、近义词的做法见各自字段说明。
 * 补充义项取自 ECDICT（MIT 许可的英汉词典），只按词条精确匹配，取值规则见
 * tools/add_dict_senses.py。phonetic / level 允许来自其他来源。
 *
 * 137 个词条没有例句、freq 也记为 0：这些词在真题里只出现在选词填空的**词库**或
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
     *
     * 同一句话被语料切成长短两条时只算一句（与例句去重用的是同一套判据），
     * 否则同一句话会被数两遍，界面上就成了「真题 5 句」配 4 条例句。
     *
     * 反过来，freq 只要求句子本身核验合格，而例句还要过中英契合门槛，
     * 所以句数多于例句数是允许的：这类词条目前 535 个，多出来的那几句
     * 中英对不上，按「核不实的宁可空着」不收作例句。
     *
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
    /**
     * 形近词：**不同的词**，只是拼写相像、容易认错（adapt/adopt、affect/effect、
     * state/stage、ability/liability）。
     *
     * 与「单词变形」分工：变形是同一个词的不同形态，形近词是各不相干的两个词。
     * 因此下面两类都不收：
     *   · 同族派生词与变形形态（accommodation/accommodating、arrival/arrive、
     *     authority/authorize 都不算形近词）—— 同族关系由词包登记的变形表、
     *     词形归元、WordNet 派生关系、加派生后缀/否定前缀四路核对；
     *   · 只是形态的词条（复数、分词、比较级），但 building / meeting / teacher /
     *     lodging 这类本身就是独立词条的保留。
     *
     * 拼写判据不限差异位置 —— 头部不同（affect/effect）、中部不同（quite/quiet）、
     * 尾部不同（state/stage）都收；相邻字母互换算一次差异。
     */
    @SerializedName("nearForms") val nearForms: List<String> = emptyList(),
    /**
     * 近义词：意思相近（与词形无关）。
     *
     * 证据有两条，任一成立即收，都不成立就留空（核不实的宁可空着）：
     *   · 词包自己列的中文意思里有完全相同的词元，**且这个词元落在双方各自前两条
     *     义项之内** —— problem「问题」／question「问题」／issue「问题，议题」／
     *     matter「事情，问题」互为近义。这条用的就是界面上显示的那条意思，
     *     所以不会出现「住宿 ↔ 调整」这种错配。
     *     「必须在双方前两条之内」是必须的：义项补到两万多条之后，只要求「有一处
     *     共同词元」会把 matter→job、increase→behalf 这类「各自的冷僻意思撞车」
     *     全收进来 —— 近义的本义是**主要义项相同**。
     *     词元以两字起算，单字也收（书、包、吃、船），只排除「的/了/是」这类虚字。
     *   · WordNet 同义词集里的等价词，且该义项与**双方**列出的中文意思都对得上。
     *
     * 只收原形词条：issues/adds/discarded 这类变形形态不作近义词（归「单词变形」）。
     * 与形近词两栏不重复 —— 既同义又形近的词只出现在这一栏。
     */
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

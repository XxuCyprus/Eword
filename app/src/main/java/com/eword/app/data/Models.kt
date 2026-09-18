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
    @SerializedName("evidence") val evidence: String = "",
    /**
     * 该义项在真题里的**支撑句**（词包 v21 起带），是义项判读的依据：
     *   support_kind   "译文"  = 该句有同卷官方译文（最强依据）
     *                  "题干"  = 卷面原句，但不配官方译文
     *   support_en/zh  原句与译文
     *   source/level   出自哪一套（如 "2016.12 第3套" / "CET6"）
     *
     * **界面上不展示**。用户的原始要求是「这个单词的意思必须有近十年真题里出现过的
     * 所有意思，不是词典查来的」——义项就是照这些原句逐条判读出来的，支撑句则用来
     * 给每条义项在「例句」一节占一个名额（见 tools/reselect_examples_v21.py），
     * 这样同一个词的几条例句会落在不同义项上，而不是五条全是一个意思。
     * 曾经把原句直接摆在义项下面，同一句话在义项处与「例句」一节各出现一次，
     * 是错的 —— 例句只在「例句」一节出现。
     */
    @SerializedName("support_kind") val supportKind: String = "",
    @SerializedName("support_en") val supportEn: String = "",
    @SerializedName("support_zh") val supportZh: String = "",
    @SerializedName("source") val source: String = "",
    @SerializedName("level") val level: String = "",
    /** 从释义里摘出来的括注（如例句式说明），不参与释义显示 */
    @SerializedName("note") val note: String = ""
) {
    /** 是否在真题里出现过 */
    val fromExam: Boolean get() = evidence == "真题"

    /**
     * 分组用的标签。同一词的义项按它分组，同组共用一个标题。
     * 旧词包没有 evidence 字段时给空标签，整组不显示标题。
     *
     * 补充义项在词包里存的值是 "大纲补充"（旧包也这么存，改了会让新旧包不一致），
     * 界面上显示为「词典补充」。
     */
    val label: String get() = when (evidence) {
        "真题" -> "真题"
        "大纲补充" -> "词典补充"
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
 * 词包 v21 的字段填充情况（14,827 词）：
 *   senses        全部有值，合计 39,180 条（真题 20,251 条、词典补充 18,929 条）。
 *                 v21 起真题义项里 17,971 条带**支撑句**（source/level 也都齐全）：
 *                 既有原句也有同卷官方译文的 14,539 条（support_kind=译文），
 *                 只有卷面原句的 3,432 条（=题干）。支撑句是义项判读的依据，
 *                 **界面不展示**；它替每条义项在「例句」一节占一个名额，
 *                 同一个词的五条例句因此落在不同义项上，而不是五条一个意思。
 *   examples     13,683 词有，合计 46,860 条（上限 5 条/词）；没有例句的 1,144 词
 *                 其卷面出现句都缺官方整句译文（选项行、夹中文括注、含完形空号），
 *                 界面上有一句说明，不塞半句充数。
 *   collocations   2,383 词有，合计 3,805 条（只收跨卷出现的搭配；功能词不给搭配）
 *   usage            948 词有（句式模板，只收真题例句里反复出现的）
 *   nearForms      5,562 词有，合计 18,785 条（只收不同族、拼写真的相近的词）
 *   synonyms       7,014 词有，合计 16,037 条（共同义项必须是双方都列出的主要义项）
 *   inflections    4,613 词有，合计 9,281 条（只收真题原文里出现过的形态）
 * 例句与搭配取自四六级真题原文与官方译文；变形、形近词、近义词的做法见各自字段说明。
 * 补充义项取自 ECDICT（MIT 许可的英汉词典），只按词条精确匹配，取值规则见
 * tools/add_dict_senses.py。phonetic / level 允许来自其他来源。
 *
 * v18 相比 v17 多了 3,939 个词条：卷面出现过、旧词表却没有的词（`boredom`、`sarcasm`、
 * `crowdfunding`、`ebike` 这类）。取舍见 tools/classify_add_words.py —— 只收「一个词」，
 * 临时拼出来的多词短语（`oh-gosh-get-out-and-look-at-it`）、被版面切开的碎片
 * （`tence` 来自 `sen`+`tence`）、引述的外语词（`colere`、`sarkazein`）都不收。
 *
 * 例句这一版收得更紧：新词的候选句先按「对应关系是不是版面印出来的」分级
 * （英文块与中文块一句对一句、或两边句数相等），只有逐条复核过的才收；
 * 同时用「汉字数 / 英文词数」的长度比筛掉对位错开的，v17 遗留的错配例句
 * 也一并清掉 212 条。所以 v18 的例句比 v17 少了一批错的，多了 3,487 条新的。
 *
 * v19 相比 v18 的三处变化：
 *   ① 多收 205 个**功能词**（`while`、`the`、`has`、`should`…）：这些词卷面天天出现，
 *      旧词表却按「实词才有词义」把它们挡掉了，于是 `while` 明明在真题里，用户搜不到。
 *      取舍见 tools/build_funcwords_v19.py —— 只收真正的词，`d`/`ll`/`n't` 这类缩合碎片
 *      与 `mr`/`etc` 这类缩写不收。
 *   ② 补词的「卷面语境义」补齐：v18 只给 45 条义项标了「真题」，其余都来自词典；
 *      这一版按卷面官方译文逐条定出真题义项，真题义项从 10,910 条增到 13,029 条。
 *      `while` 因此不再首条显示名词「一会儿」，而是连词「当…的时候」。
 *   ③ 例句出处的级别标注纠错 3,752 条 —— 旧数据把**词条自身的级别**写进了例句，
 *      于是六级词条出现在四级卷里的例句被标成 CET6（徽章颜色跟着错）。现在按
 *      「这句译文实际出现在哪一级别的同名卷里」重新判定。
 * 例句在同一词条内按**年份从新到旧**排列（用户要求：超过 5 条时尽量举近年的）。
 *
 * v20 相比 v19 的三处变化（都在例句与两张关系表上，词表本身没动）：
 *   ① 例句清掉 1,062 条**中英对不上**的：译文被挂到同卷的另一句上、以及 14 条
 *      英文里根本没有这个词的（`hatched`/`rigor` 这类）。补收 4,838 条新的；
 *      同时把「每词至多 5 条」真正执行到位 —— 补收后有 2,297 个词超过 5 条，
 *      按年份从新到旧砍掉 3,347 条（绝大多数是 2019 年及以前的旧例句）。
 *      净结果 42,320 → 42,685 条，且整体更靠近年。出处写法也归一了（`2016.06` → `2016.6`）。
 *   ② 近义、形近两张表整栏重建：近义栏换了 6,345 个词（旧表有 4,367 个词条带着
 *      7,510 条「共同义项其实不成立」的对，如 `abandon`/`ditch`）；形近栏换了
 *      3,946 个词、并把「同族派生」判据补强（`whole`/`wholly`、`courage`/`encourage`
 *      以前漏判），判据实现收敛成一份（tools/lex_family.py）。
 *   ③ 搭配守住「不是搭配就不收」：**功能词不给搭配**（旧表把 `in` 收了 50 条、
 *      `to` 40 条语料碎片，如 `a sharp increase in`），每词至多 4 条。
 *
 * v21 相比 v20 的四处变化（词表本身只动了一个词条）：
 *   ① 义项 35,625 → 39,180 条，真题义项 13,029 → 20,251 条。做法是把 2015–2026 年
 *      152 套卷面全部按词判读一遍（约 1.5 万词、247 批），每个义项挂上它在卷面里的
 *      **支撑句**；判读只认材料里真实出现的用法，词典有而卷面没有的义项不开。
 *      `submission` 因此不再只列「屈服/服从」，而有了真题里真用的「提交，投稿」。
 *   ② 每条真题义项带原句 + 同卷官方译文 + 出处（字段见 [Sense.supportEn]），
 *      作为判读依据存在包里，**界面上不摆出来**：原句出现在正文最下面的「例句」
 *      一节。译文只取**同一套卷子**里的那句，避免「英文一句、中文是隔壁那句译文」。
 *   ③ 例句 42,685 → 46,845 条，且**按义项铺开、年份从新到旧**：每个义项先占一个
 *      名额，同一个用法不重复堆例句。卷面逐字可查的比例从 96.8% 提到 99.3%——
 *      早期配对流程「补全」出来的句子（卷面 `who have a [29] ability…`，词条写成
 *      `Put another way, people who have a heightened ability…`）全部清掉。
 *      年份分布随之改善：2025/2026 两年的例句从 6,894 条增到 14,575 条。
 *   ④ 删掉 100 余条**与四六级语境对不上的老词典义项**（`amygdala` 曾写作「巴旦杏」，
 *      真题里是「杏仁核」；`exhaustive` 曾写作「枯竭的」，真题里是「详尽的」），
 *      并删掉垃圾词条 `nd`（词形残片，义项无法判读）。
 */
data class WordEntry(
    @SerializedName("id") val id: String = "",
    @SerializedName("word") val word: String = "",
    @SerializedName("phonetic") val phonetic: String = "",
    /** 词汇级别：CET4 或 CET6，按词表登记 */
    @SerializedName("level") val level: String = "",
    /**
     * 该词在 2015–2026 年真题**卷面正文里出现在多少个不同的句子**中。
     *
     * 口径（v18 起）：把 152 份全卷中英对照 PDF 的正文拼起来，按句末标点切句，
     * 同一句里出现该词的任一形态（词包登记的 inflections 也算）就计一句，
     * 完全相同的句子只算一次。**题干与选项也算** —— 它们也在卷面上。
     *
     * 所以 freq >= 例句条数恒成立：
     *   freq     = 卷面上含该词的句子数
     *   examples = 其中中英能对上（有官方译文可核对）、按契合度取的前 5 条
     *
     * 于是有两类「句数多于例句数」的词条，都是正常的：
     *   · 只在题干或选项里出现的 1,924 词：卷面不给题干与选项配译文，一条例句都收不到，
     *     界面上会说明一句（见 Components.kt 里 WordFullBody 的 showExamples 分支）；
     *   · 出现在正文里、但中英没对上的词：按「核不实的宁可空着」不收作例句。
     *
     * v17 之前这个数字是词表登记的出现次数（`ability` 记 145、卷面实际 70 句），
     * 与界面上「真题 N 句」的写法对不上；v18 起改成真的句数，新老词同一条尺子。
     *
     * 卷面上查不到这个词时 freq 为 0，界面上不显示徽章（v20 里 34 个）。
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

package com.eword.app.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Random
import java.util.concurrent.Executors

/**
 * 应用核心状态：词包、启用项、设置、学习进度。
 * 内容（词包）与进度（用户状态）物理隔离，进度以 词包id|词条id 为键，
 * 因此更换或升级词包不会丢失进度。
 */
class AppCore(private val ctx: Context) {

    private val prefs = ctx.getSharedPreferences("eword_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val repo = PackRepository(ctx)
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** 主线程的句柄，后台线程算完把结果交回主线程套用 */
    val pronouncer = Pronouncer(ctx)

    var packs by mutableStateOf<List<WordPack>>(emptyList())
        private set

    /** 词包与进度是否已经载入完毕。载入前界面显示「正在载入词包」，不显示空白 */
    var ready by mutableStateOf(false)
        private set

    /** 正在导入/重新载入 —— 期间界面显示进度，不接受重复操作 */
    var busy by mutableStateOf(false)
        private set

    /**
     * 最近一次失败的原因，供界面显示一行红字。
     *
     * 载入词包、写学习进度这些地方以前全用 runCatching 静默吞掉：进度「清零」、
     * 词本「凭空消失」、喇叭「不响」，用户侧完全无法归因。现在留一条通道。
     */
    var lastError by mutableStateOf<String?>(null)
        private set

    fun clearError() {
        lastError = null
    }

    var enabledPackId by mutableStateOf(prefs.getString(K_ENABLED, "").orEmpty())
        private set

    // ---------- 设置 ----------
    var unitSize by mutableIntStateOf(prefs.getInt(K_UNIT, 52))
        private set
    var autoPronounce by mutableStateOf(prefs.getBoolean(K_AUTO, true))
        private set
    var showCollocations by mutableStateOf(prefs.getBoolean(K_COLL, true))
        private set
    var showExamples by mutableStateOf(prefs.getBoolean(K_EX, true))
        private set

    // ---------- 进度 ----------
    private val progress = mutableStateMapOf<String, WordState>()

    /** 识记进度：回到某单元时从上次的位置继续。key = "packId|unit"。内存镜像，落盘一次 */
    private val memorizePos = HashMap<String, Int>()

    /** 温习卡片位置：跳去词条详情（点近义词/形近词）再回来时，从原来那张卡继续 */
    private val reviewPos = HashMap<String, Int>()

    /**
     * 单元列表滚动位置（记住上次翻到第几个单元）。key = packId。
     *
     * 刻意**不是** Compose 快照状态：它是「进入页面时的初值」，读它的人不该因为
     * 它变化而重组。早先用 mutableStateMapOf，滚动每过一行就写一次 → 读它的
     * 整个单元列表页重组 → units()（一万多个词逐个查表）重算一遍 → 越滑越涩，
     * 同时每行还写一次 SharedPreferences（整份配置重新编码）。
     */
    private val unitScroll = HashMap<String, Int>()

    /**
     * 词本详情页的搜索词。key = packId。
     *
     * 放在这里而不是页面内的 remember：从搜索结果点进词条详情再返回时，
     * 页面会重新组合，remember 里的搜索词会丢失。
     * 保留搜索词才能接着改（例如已输入 app，再补全成 application，不必重打）。
     */
    private val searchQuery = mutableStateMapOf<String, String>()

    fun searchQueryOf(packId: String): String = searchQuery[packId].orEmpty()

    fun setSearchQuery(packId: String, q: String) {
        searchQuery[packId] = q
    }

    /** 打乱状态：可观察，点击即时生效 */
    private val shuffleState = mutableStateMapOf<String, Boolean>()

    /**
     * 温习卡片的「揭示进度」：key = "packId|wordId"。
     *
     * 放在这里而不是 remember 里，是为了让点形近词/近义词跳转后再返回时，
     * 之前逐条揭示出来的例句不会丢失（跳转前什么样，跳转回来还是什么样）。
     * 只存在内存中，随进程结束清空。
     */
    private val revealCount = mutableStateMapOf<String, Int>()

    /** 答案是否已经铺开。两条路径都会置位：逐条看例句看到底、直接点「显示完整答案」 */
    private val revealFull = mutableStateMapOf<String, Boolean>()

    /**
     * 这一次是**靠逐条例句的提示**才看得下去的：key = "packId|wordId"。
     *
     * 与 revealFull 必须分开存。「显示完整答案（核对用）」也置位 revealFull，
     * 但它的意思是「我只是核对有没有记错」，不代表答不上来；两者混用一个标志时
     * 会把「核对」也当成「靠提示才想起来」，于是主观核对的用户被判成没记住，
     * 判定按钮还被锁死。
     */
    private val revealByHints = mutableStateMapOf<String, Boolean>()

    fun revealCountOf(packId: String, wordId: String): Int =
        revealCount["$packId|$wordId"] ?: 0

    fun setRevealCount(packId: String, wordId: String, n: Int) {
        revealCount["$packId|$wordId"] = n
    }

    fun revealFullOf(packId: String, wordId: String): Boolean =
        revealFull["$packId|$wordId"] ?: false

    fun setRevealFull(packId: String, wordId: String, b: Boolean) {
        revealFull["$packId|$wordId"] = b
    }

    fun revealByHintsOf(packId: String, wordId: String): Boolean =
        revealByHints["$packId|$wordId"] ?: false

    fun setRevealByHints(packId: String, wordId: String, b: Boolean) {
        revealByHints["$packId|$wordId"] = b
    }

    /** 清掉某个词的揭示进度（换卡片、判定后调用） */
    fun clearReveal(packId: String, wordId: String) {
        revealCount.remove("$packId|$wordId")
        revealFull.remove("$packId|$wordId")
        revealByHints.remove("$packId|$wordId")
    }

    private val orderCache = HashMap<String, List<WordEntry>>()
    private val idIndex = HashMap<String, Set<String>>()

    val enabledPack: WordPack?
        get() = packs.firstOrNull { it.manifest.packId == enabledPackId }

    // ================= 载入 =================

    /**
     * 载入词包与进度。**解析词包 JSON 放到后台线程** —— 词包 16 MB，
     * 反射式反序列化在主线程要几百毫秒到几秒，低端机上冷启动会白屏甚至 ANR。
     * 解析完把结果交回主线程套用，套用是纯赋值，很快。
     */
    fun loadAsync() {
        if (busy) return
        busy = true
        Thread({
            val loaded = runCatching { repo.loadAll() }
                .onFailure { e -> main.post { lastError = "载入词包失败：${describe(e)}" } }
                .getOrDefault(emptyList())
            val failures = repo.lastFailures.toList()
            main.post {
                applyLoaded(loaded)
                if (failures.isNotEmpty()) {
                    lastError = "有 ${failures.size} 个词本读不出来，已跳过：" +
                        failures.joinToString("、") { it.first }
                }
                busy = false
                ready = true
            }
        }, "eword-load").start()
    }

    /** 载入完成后套用状态（只在主线程调用） */
    private fun applyLoaded(loaded: List<WordPack>) {
        packs = loaded
        orderCache.clear()
        idIndex.clear()
        progress.clear()
        runCatching {
            val type = object : TypeToken<Map<String, Int>>() {}.type
            val m: Map<String, Int>? = gson.fromJson(prefs.getString(K_PROGRESS, "{}"), type)
            m?.forEach { (k, v) -> progress[k] = WordState.entries.getOrElse(v) { WordState.NEW } }
        }.onFailure { e ->
            lastError = "学习进度读不出来，已从空白开始：${describe(e)}"
        }
        // 恢复识记/温习进度与单元列表滚动位置
        runCatching {
            prefs.all.forEach { (k, v) ->
                if (v !is Int) return@forEach
                when {
                    k.startsWith("mpos_") -> memorizePos[k.removePrefix("mpos_")] = v
                    k.startsWith("uscr_") -> unitScroll[k.removePrefix("uscr_")] = v
                    k.startsWith("rpos_") -> reviewPos[k.removePrefix("rpos_")] = v
                }
            }
        }
        packs.forEach { shuffleState[it.manifest.packId] = seedOf(it.manifest.packId) != 0L }
        // 自动选一个启用的词本；但「主动停用了全部词本」要尊重：
        // 只有当前选中的 id 指向的词本已不存在时才回退到第一个。
        val known = prefs.getString(K_ENABLED, null)
        if (enabledPackId.isNotBlank() && packs.none { it.manifest.packId == enabledPackId }) {
            enabledPackId = packs.firstOrNull()?.manifest?.packId ?: ""
            prefs.edit { putString(K_ENABLED, enabledPackId) }
        } else if (known == null && packs.isNotEmpty()) {
            // 首次运行（从未写过该配置）才自动启用第一个
            enabledPackId = packs.first().manifest.packId
            prefs.edit { putString(K_ENABLED, enabledPackId) }
        }
    }

    private fun describe(e: Throwable): String = e.message ?: e.javaClass.simpleName

    // ================= 词包 =================
    fun enablePack(id: String) {
        enabledPackId = id
        prefs.edit { putString(K_ENABLED, id) }
    }

    /**
     * 导入词包（后台线程）。导入成功后**自动启用**，
     * 之后可以在「我的单词本」里点「停用」把它关掉。
     *
     * 导入要拷 66 MB、解包 106 MB 音频、再把词包整个解析一遍，全程几十秒。
     * 放在主线程界面会假死且没有任何提示，用户会以为死机去强杀，
     * 而强杀正好留下「有词表、没音频」的半成品词包。
     */
    fun importPackAsync(uri: Uri, onDone: (Result<String>) -> Unit) {
        if (busy) return
        busy = true
        Thread({
            val r = repo.import(uri)
            val loaded = runCatching { repo.loadAll() }.getOrDefault(emptyList())
            main.post {
                applyLoaded(loaded)
                r.onSuccess { enablePack(it) }
                    .onFailure { e -> lastError = "导入失败：${describe(e)}" }
                busy = false
                onDone(r)
            }
        }, "eword-import").start()
    }

    /** 删除词包（后台线程，删完要重新载入） */
    fun deletePackAsync(packId: String, onDone: (Result<Unit>) -> Unit) {
        if (busy) return
        busy = true
        Thread({
            val r = repo.delete(packId)
            val loaded = runCatching { repo.loadAll() }.getOrDefault(emptyList())
            main.post {
                applyLoaded(loaded)
                if (enabledPackId == packId) enablePack("")
                busy = false
                onDone(r)
            }
        }, "eword-delete").start()
    }

    // ================= 设置 =================
    fun updateUnitSize(n: Int) {
        unitSize = n
        prefs.edit { putInt(K_UNIT, n) }
    }

    fun updateAutoPronounce(b: Boolean) {
        autoPronounce = b
        prefs.edit { putBoolean(K_AUTO, b) }
    }

    fun updateShowCollocations(b: Boolean) {
        showCollocations = b
        prefs.edit { putBoolean(K_COLL, b) }
    }

    fun updateShowExamples(b: Boolean) {
        showExamples = b
        prefs.edit { putBoolean(K_EX, b) }
    }

    // ================= 顺序与打乱 =================
    private fun seedOf(packId: String): Long = prefs.getLong("seed_$packId", 0L)

    fun isShuffled(packId: String): Boolean = shuffleState[packId] ?: (seedOf(packId) != 0L)

    fun words(pack: WordPack): List<WordEntry> = orderCache.getOrPut(pack.manifest.packId) {
        val seed = seedOf(pack.manifest.packId)
        if (seed == 0L) pack.words else pack.words.shuffled(Random(seed))
    }

    fun setShuffled(packId: String, on: Boolean) {
        prefs.edit { putLong("seed_$packId", if (on) System.nanoTime() else 0L) }
        shuffleState[packId] = on
        orderCache.remove(packId)
        idIndex.remove(packId)
    }

    fun reshuffle(packId: String) {
        prefs.edit { putLong("seed_$packId", System.nanoTime()) }
        shuffleState[packId] = true
        orderCache.remove(packId)
        idIndex.remove(packId)
    }

    /** 某个词形是否被本词包收录（决定形近词/近义词能否点击跳转） */
    fun contains(pack: WordPack, form: String): Boolean {
        val idx = idIndex.getOrPut(pack.manifest.packId) { pack.words.map { it.id }.toHashSet() }
        return form.lowercase() in idx
    }

    /**
     * 本词包全部词形，供「原词加粗」判断用。
     * 加粗时只认本词包收录的真实词形，避免 th → the/that 这类误加粗
     * （th 是序数词后缀，从 20th/9th 提取而来）。
     */
    fun vocabIds(pack: WordPack): Set<String> =
        idIndex.getOrPut(pack.manifest.packId) { pack.words.map { it.id }.toHashSet() }

    fun findWord(pack: WordPack, form: String): WordEntry? =
        pack.words.firstOrNull { it.id == form.lowercase() }

    /** 在词本内搜索，返回「词条 + 所属单元序号」 */
    fun search(pack: WordPack, query: String, limit: Int = 60): List<Pair<WordEntry, Int>> {
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        val ws = words(pack)
        val out = ArrayList<Pair<WordEntry, Int>>()
        for (i in ws.indices) {
            if (ws[i].word.lowercase().contains(q)) {
                out.add(ws[i] to (i / unitSize))
                if (out.size >= limit) break
            }
        }
        return out
    }

    /** 某词当前所在的单元序号（从 0 开始；未收录返回 -1） */
    fun unitIndexOf(pack: WordPack, wordId: String): Int {
        val i = words(pack).indexOfFirst { it.id == wordId }
        return if (i < 0) -1 else i / unitSize
    }

    // ================= 单元 =================
    fun unitCount(pack: WordPack): Int {
        val n = words(pack).size
        return if (n == 0) 0 else (n + unitSize - 1) / unitSize
    }

    fun unitWords(pack: WordPack, unitIndex: Int): List<WordEntry> {
        val ws = words(pack)
        val from = unitIndex * unitSize
        if (from !in ws.indices) return emptyList()
        val to = minOf(from + unitSize, ws.size)
        return ws.subList(from, to)
    }

    /** 各单元统计；state 为 null 表示不筛状态 */
    fun units(pack: WordPack, state: WordState?): List<UnitInfo> =
        (0 until unitCount(pack)).map { i ->
            val ws = unitWords(pack, i)
            val pending = if (state == null) ws.size
            else ws.count { stateOf(pack.manifest.packId, it.id) == state }
            UnitInfo(i, ws.size, pending)
        }

    /** 某单元中处于指定状态的词，构成学习队列 */
    fun queue(pack: WordPack, unitIndex: Int, state: WordState): List<WordEntry> =
        unitWords(pack, unitIndex).filter { stateOf(pack.manifest.packId, it.id) == state }

    // ================= 进度 =================
    private fun key(packId: String, wordId: String) = "$packId|$wordId"

    fun stateOf(packId: String, wordId: String): WordState =
        progress[key(packId, wordId)] ?: WordState.NEW

    fun setState(packId: String, wordId: String, s: WordState) {
        progress[key(packId, wordId)] = s
        persistProgress()
    }

    /**
     * 处于某状态的词数。
     *
     * 只数**当前词包里还有的**词：词包换新版本时可能删掉过词条，而进度是按
     * 「词包id|词条id」保留的，那些孤儿进度会让「已彻底记住 10 词」和列表里
     * 数出来的 9 条对不上。
     */
    fun countOf(packId: String, state: WordState): Int {
        val pack = packs.firstOrNull { it.manifest.packId == packId } ?: return 0
        val vocab = vocabIds(pack)
        val prefix = "$packId|"
        return progress.count { (k, v) ->
            v == state && k.startsWith(prefix) && k.removePrefix(prefix) in vocab
        }
    }

    /**
     * 进度落盘：**必须串行**。
     *
     * 每次判定都新起一个线程去写，落地顺序就没有保证：旧快照的线程慢一拍，
     * 就会把新快照覆盖掉，重启后那个词的状态退回上一次。排到同一个单线程上，
     * 快照按调用顺序落地。序列化也在调用线程做完，进队列的是结果而不是可变状态。
     */
    private val persistExec = Executors.newSingleThreadExecutor { r ->
        Thread(r, "eword-progress").apply { isDaemon = true }
    }

    private fun persistProgress() {
        val json = gson.toJson(progress.mapValues { it.value.ordinal })
        runCatching {
            persistExec.execute {
                runCatching { prefs.edit { putString(K_PROGRESS, json) } }
                    .onFailure { e -> main.post { lastError = "学习进度没能保存：${describe(e)}" } }
            }
        }.onFailure { e -> lastError = "学习进度没能排队保存：${describe(e)}" }
    }

    fun resetPack(packId: String) {
        val prefix = "$packId|"
        progress.keys.filter { it.startsWith(prefix) }.forEach { progress.remove(it) }
        memorizePos.keys.filter { it.startsWith(prefix) }.forEach {
            memorizePos.remove(it)
            prefs.edit { remove("mpos_$it") }
        }
        persistProgress()
    }

    // ================= 识记进度 =================
    fun memorizeIndexOf(packId: String, unit: Int): Int = memorizePos["$packId|$unit"] ?: 0

    /** 只改内存 + 落盘一次。识记页每换一张卡调用一次，没必要为它重组整页 */
    fun setMemorizeIndex(packId: String, unit: Int, idx: Int) {
        memorizePos["$packId|$unit"] = idx
        prefs.edit { putInt("mpos_$packId|$unit", idx) }
    }

    /** 上次学到哪一个词（优先按词定位，避免队列变化导致错位） */
    fun memorizeWordId(packId: String, unit: Int): String =
        prefs.getString("mwid_$packId|$unit", "").orEmpty()

    fun setMemorizeWordId(packId: String, unit: Int, wordId: String) {
        prefs.edit { putString("mwid_$packId|$unit", wordId) }
    }

    /** 清掉某单元的识记进度（学完该单元时调用） */
    fun clearMemorize(packId: String, unit: Int) {
        memorizePos.remove("$packId|$unit")
        prefs.edit {
            remove("mpos_$packId|$unit")
            remove("mwid_$packId|$unit")
        }
    }

    // ================= 温习进度 =================
    //
    // 温习页原来没有位置记忆：点一张卡上的近义词跳去词条详情，返回时 remember 里的
    // 下标已经随页面销毁而清零，人又回到本单元第 1 张卡。近义词覆盖扩到六千多个词之后
    // 这个动作会经常发生，所以按识记页的做法把位置存起来。

    private fun rk(packId: String, unit: Int, stage: Int) = "$packId|$unit|$stage"

    fun reviewIndexOf(packId: String, unit: Int, stage: Int): Int =
        reviewPos[rk(packId, unit, stage)] ?: 0

    /** 只改内存；离开温习页时由 [flushReviewIndex] 落盘一次 */
    fun setReviewIndex(packId: String, unit: Int, stage: Int, idx: Int) {
        reviewPos[rk(packId, unit, stage)] = idx
    }

    fun flushReviewIndex(packId: String, unit: Int, stage: Int) {
        prefs.edit { putInt("rpos_${rk(packId, unit, stage)}", reviewIndexOf(packId, unit, stage)) }
    }

    fun clearReviewIndex(packId: String, unit: Int, stage: Int) {
        reviewPos.remove(rk(packId, unit, stage))
        prefs.edit { remove("rpos_${rk(packId, unit, stage)}") }
    }

    // ================= 单元列表滚动位置 =================
    fun unitScrollIndexOf(packId: String): Int = unitScroll[packId] ?: 0

    /** 只改内存（滚动时每过一行调一次），落盘留给离开页面时的 [flushUnitScroll] */
    fun setUnitScrollIndex(packId: String, index: Int) {
        unitScroll[packId] = index
    }

    /** 离开单元列表时落盘一次 */
    fun flushUnitScroll(packId: String) {
        prefs.edit { putInt("uscr_$packId", unitScroll[packId] ?: 0) }
    }

    companion object {
        private const val K_ENABLED = "enabledPackId"
        private const val K_UNIT = "unitSize"
        private const val K_AUTO = "autoPronounce"
        private const val K_COLL = "showCollocations"
        private const val K_EX = "showExamples"
        private const val K_PROGRESS = "progress"
    }
}

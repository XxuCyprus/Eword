package com.eword.app.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Random

/**
 * 应用核心状态：词包、启用项、设置、学习进度。
 * 内容（词包）与进度（用户状态）物理隔离，进度以 词包id|词条id 为键，
 * 因此更换或升级词包不会丢失进度。
 */
class AppCore(private val ctx: Context) {

    private val prefs = ctx.getSharedPreferences("eword_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val repo = PackRepository(ctx)

    val pronouncer = Pronouncer(ctx)

    var packs by mutableStateOf<List<WordPack>>(emptyList())
        private set

    var enabledPackId by mutableStateOf(prefs.getString(K_ENABLED, "").orEmpty())
        private set

    // ---------- 设置 ----------
    var unitSize by mutableStateOf(prefs.getInt(K_UNIT, 52))
        private set
    var autoPronounce by mutableStateOf(prefs.getBoolean(K_AUTO, true))
        private set
    var showCollocations by mutableStateOf(prefs.getBoolean(K_COLL, true))
        private set
    var showExamples by mutableStateOf(prefs.getBoolean(K_EX, true))
        private set

    // ---------- 进度 ----------
    private val progress = mutableStateMapOf<String, WordState>()

    /** 识记进度：回到某单元时从上次的位置继续。key = "packId|unit" */
    private val memorizePos = mutableStateMapOf<String, Int>()

    /** 单元列表滚动位置（记住上次翻到第几个单元）。key = packId */
    private val unitScroll = mutableStateMapOf<String, Int>()

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
     * 温习卡片的「揭示进度」：key = "packId|wordId"，值 = 已揭示的例句条数；
     * 以及是否已点开「显示例句意思」。
     *
     * 放在这里而不是 remember 里，是为了让点形近词/近义词跳转后再返回时，
     * 之前逐条揭示出来的例句不会丢失（跳转前什么样，跳转回来还是什么样）。
     * 只存在内存中，随进程结束清空。
     */
    private val revealCount = mutableStateMapOf<String, Int>()
    private val revealFull = mutableStateMapOf<String, Boolean>()

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

    /** 清掉某个词的揭示进度（切换词/换包时用不到，保留给后续「重置」类操作） */
    fun clearReveal(packId: String, wordId: String) {
        revealCount.remove("$packId|$wordId")
        revealFull.remove("$packId|$wordId")
    }

    private val orderCache = HashMap<String, List<WordEntry>>()
    private val idIndex = HashMap<String, Set<String>>()

    val enabledPack: WordPack?
        get() = packs.firstOrNull { it.manifest.packId == enabledPackId }

    // ================= 载入 =================
    fun load() {
        packs = repo.loadAll()
        orderCache.clear()
        idIndex.clear()
        progress.clear()
        runCatching {
            val type = object : TypeToken<Map<String, Int>>() {}.type
            val m: Map<String, Int>? = gson.fromJson(prefs.getString(K_PROGRESS, "{}"), type)
            m?.forEach { (k, v) -> progress[k] = WordState.entries.getOrElse(v) { WordState.NEW } }
        }
        // 恢复识记进度与单元列表滚动位置
        runCatching {
            prefs.all.forEach { (k, v) ->
                if (v !is Int) return@forEach
                when {
                    k.startsWith("mpos_") -> memorizePos[k.removePrefix("mpos_")] = v
                    k.startsWith("uscr_") -> unitScroll[k.removePrefix("uscr_")] = v
                }
            }
        }
        packs.forEach { shuffleState[it.manifest.packId] = seedOf(it.manifest.packId) != 0L }
        // 自动选一个启用的词本；但「主动停用了全部词本」要尊重：
        // 只有当前选中的 id 指向的词本已不存在时才回退到第一个。
        val known = prefs.getString(K_ENABLED, null)
        if (enabledPackId.isNotBlank() && packs.none { it.manifest.packId == enabledPackId }) {
            enabledPackId = packs.firstOrNull()?.manifest?.packId ?: ""
            prefs.edit().putString(K_ENABLED, enabledPackId).apply()
        } else if (known == null && packs.isNotEmpty()) {
            // 首次运行（从未写过该配置）才自动启用第一个
            enabledPackId = packs.first().manifest.packId
            prefs.edit().putString(K_ENABLED, enabledPackId).apply()
        }
    }

    // ================= 词包 =================
    fun enablePack(id: String) {
        enabledPackId = id
        prefs.edit().putString(K_ENABLED, id).apply()
    }

    /**
     * 导入词包。导入成功后**自动启用**，
     * 之后可以在「我的单词本」里点「停用」把它关掉。
     */
    fun importPack(uri: Uri): Result<String> =
        repo.import(uri).onSuccess { id ->
            load()
            enablePack(id)
        }

    fun deletePack(packId: String): Result<Unit> =
        repo.delete(packId).onSuccess {
            if (enabledPackId == packId) enablePack("")
            load()
        }

    // ================= 设置 =================
    fun updateUnitSize(n: Int) {
        unitSize = n
        prefs.edit().putInt(K_UNIT, n).apply()
    }

    fun updateAutoPronounce(b: Boolean) {
        autoPronounce = b
        prefs.edit().putBoolean(K_AUTO, b).apply()
    }

    fun updateShowCollocations(b: Boolean) {
        showCollocations = b
        prefs.edit().putBoolean(K_COLL, b).apply()
    }

    fun updateShowExamples(b: Boolean) {
        showExamples = b
        prefs.edit().putBoolean(K_EX, b).apply()
    }

    // ================= 顺序与打乱 =================
    private fun seedOf(packId: String): Long = prefs.getLong("seed_$packId", 0L)

    fun isShuffled(packId: String): Boolean = shuffleState[packId] ?: (seedOf(packId) != 0L)

    fun words(pack: WordPack): List<WordEntry> = orderCache.getOrPut(pack.manifest.packId) {
        val seed = seedOf(pack.manifest.packId)
        if (seed == 0L) pack.words else pack.words.shuffled(Random(seed))
    }

    fun setShuffled(packId: String, on: Boolean) {
        prefs.edit().putLong("seed_$packId", if (on) System.nanoTime() else 0L).apply()
        shuffleState[packId] = on
        orderCache.remove(packId)
        idIndex.remove(packId)
    }

    fun reshuffle(packId: String) {
        prefs.edit().putLong("seed_$packId", System.nanoTime()).apply()
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

    fun countOf(packId: String, state: WordState): Int =
        progress.count { it.key.startsWith("$packId|") && it.value == state }

    private var persistJob: Thread? = null

    private fun persistProgress() {
        val snapshot = progress.mapValues { it.value.ordinal }
        persistJob = Thread {
            runCatching {
                prefs.edit().putString(K_PROGRESS, gson.toJson(snapshot)).apply()
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun resetPack(packId: String) {
        val prefix = "$packId|"
        progress.keys.filter { it.startsWith(prefix) }.forEach { progress.remove(it) }
        memorizePos.keys.filter { it.startsWith(prefix) }.forEach {
            memorizePos.remove(it)
            prefs.edit().remove("mpos_$it").apply()
        }
        persistProgress()
    }

    // ================= 识记进度 =================
    fun memorizeIndexOf(packId: String, unit: Int): Int = memorizePos["$packId|$unit"] ?: 0

    fun setMemorizeIndex(packId: String, unit: Int, idx: Int) {
        memorizePos["$packId|$unit"] = idx
        prefs.edit().putInt("mpos_$packId|$unit", idx).apply()
    }

    /** 上次学到哪一个词（优先按词定位，避免队列变化导致错位） */
    fun memorizeWordId(packId: String, unit: Int): String =
        prefs.getString("mwid_$packId|$unit", "").orEmpty()

    fun setMemorizeWordId(packId: String, unit: Int, wordId: String) {
        prefs.edit().putString("mwid_$packId|$unit", wordId).apply()
    }

    /** 清掉某单元的识记进度（学完该单元时调用） */
    fun clearMemorize(packId: String, unit: Int) {
        memorizePos.remove("$packId|$unit")
        prefs.edit()
            .remove("mpos_$packId|$unit")
            .remove("mwid_$packId|$unit")
            .apply()
    }

    // ================= 单元列表滚动位置 =================
    fun unitScrollIndexOf(packId: String): Int = unitScroll[packId] ?: 0

    fun setUnitScrollIndex(packId: String, index: Int) {
        unitScroll[packId] = index
        prefs.edit().putInt("uscr_$packId", index).apply()
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

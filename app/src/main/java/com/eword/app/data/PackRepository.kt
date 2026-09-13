package com.eword.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.gson.Gson
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 词包仓库。
 * 内置词包放在 assets/packs 目录下的 json 文件；
 * 用户导入的词包落在 filesDir/packs/，同名时用户导入的覆盖内置。
 * 导入的音频落在 filesDir/audio/词包id/。
 */
class PackRepository(private val ctx: Context) {

    private val gson = Gson()

    /**
     * 上一次 [loadAll] 读不出来的词包（名字 → 原因）。
     *
     * 早先每个词包各自 runCatching 静默跳过：词包文件半写（导入中途被强杀）时
     * 词本会「凭空消失」，用户侧零提示。留下来交给界面显示一行红字。
     */
    val lastFailures = mutableListOf<Pair<String, String>>()

    fun loadAll(): List<WordPack> {
        lastFailures.clear()
        val out = LinkedHashMap<String, WordPack>()

        runCatching {
            ctx.assets.list("packs")
                ?.filter { it.endsWith(".json", true) }
                ?.forEach { name ->
                    runCatching {
                        ctx.assets.open("packs/$name").use { ins ->
                            val p = gson.fromJson(ins.reader(Charsets.UTF_8), WordPack::class.java)
                            if (p?.manifest?.packId?.isNotBlank() == true) out[p.manifest.packId] = p
                        }
                    }.onFailure { lastFailures.add(name to reason(it)) }
                }
        }

        val dir = File(ctx.filesDir, "packs")
        if (dir.isDirectory) {
            dir.listFiles()?.filter { it.name.endsWith(".json", true) }?.sortedBy { it.name }?.forEach { f ->
                runCatching {
                    val p = gson.fromJson(f.readText(Charsets.UTF_8), WordPack::class.java)
                    if (p?.manifest?.packId?.isNotBlank() == true) out[p.manifest.packId] = p
                }.onFailure { lastFailures.add(f.name to reason(it)) }
            }
        }

        return out.values.toList()
    }

    private fun reason(e: Throwable): String = e.message ?: e.javaClass.simpleName

    /**
     * 从系统文件选择器导入 .json 或 .ewp（压缩包内含 pack.json 与 audio/）。
     *
     * 内存要点：全程**只作流式拷贝**，不把整个文件读进堆。
     * 全量词包 .ewp 约 66 MB，解压后音频约 106 MB；整包读入并把音频攒在
     * 内存里峰值可达 ~188 MB，而中低端机 dalvik 堆上限只有 192 MB，
     * 且 pack.json 先落盘、音频后落盘，一旦 OOM 会留下「有词表、没音频」的半成品。
     * 现在压成临时文件再流式解包，堆占用与词包大小无关。
     */
    fun import(uri: Uri): Result<String> = runCatching {
        val name = displayName(uri) ?: "imported"
        val dir = File(ctx.filesDir, "packs").apply { mkdirs() }

        if (name.endsWith(".zip", true) || name.endsWith(".ewp", true)) {
            // 1) 流式拷到临时文件（堆占用恒定 ~8KB）
            val tmp = File(ctx.cacheDir, "import_tmp.ewp")
            try {
                ctx.contentResolver.openInputStream(uri)?.use { ins ->
                    tmp.outputStream().use { out -> ins.copyTo(out) }
                } ?: error("无法读取所选文件")

                // 2) 流式解包
                tmp.inputStream().use { ins -> unzipStream(ins, dir) }
            } finally {
                tmp.delete()
            }
        } else {
            // 只流式读到 manifest.packId 就停。早先是把整包 16 MB 解析成对象图再取一个
            // 字段（构造近 30 万个 String），而紧接着 loadAll() 还会再整包解析一次 ——
            // 主线程峰值内存能到 ~100 MB，中低端机直接 OOM。
            val id = ctx.contentResolver.openInputStream(uri)?.use { ins ->
                readPackId(ins)
            } ?: error("无法读取所选文件")
            // 词表 json 本身远小于音频包，直接落盘
            ctx.contentResolver.openInputStream(uri)?.use { ins ->
                File(dir, "$id.json").outputStream().use { out -> ins.copyTo(out) }
            } ?: error("无法读取所选文件")
            id
        }
    }

    /** 流式读到 manifest 里的 packId 就返回，不构造整包对象 */
    private fun readPackId(ins: java.io.InputStream): String {
        val type = object : com.google.gson.reflect.TypeToken<PackManifest>() {}.type
        val reader = com.google.gson.stream.JsonReader(ins.reader(Charsets.UTF_8))
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() == "manifest") {
                val m: PackManifest = gson.fromJson(reader, type)
                return m.packId.takeIf { it.isNotBlank() } ?: error("词包缺少 packId")
            }
            reader.skipValue()
        }
        error("词包缺少 packId")
    }

    /**
     * 流式解包：pack.json 直接落盘；audio/ 条目逐条流式写入，不在内存里攒。
     *
     * 本词包格式固定为「pack.json 在最前，audio/ 在后」，因此读完 pack.json
     * 就知道音频该落到 filesDir/audio/<packId>/。若顺序不按预期（packId 还没拿到），
     * 先把音频落到缓存临时目录，拿到 packId 后再整体改名过去。
     */
    private fun unzipStream(ins: java.io.InputStream, dir: File): String {
        var packId: String? = null
        var audioDir: File? = null          // 最终音频目录（拿到 packId 后确定）
        var pendingAudio: File? = null      // packId 尚未确定时的临时音频目录
        var pending = 0

        ZipInputStream(ins).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                val nm = e.name.replace('\\', '/')
                when {
                    nm.endsWith("pack.json") || nm.endsWith("manifest.json") -> {
                        // 词表约 12 MB：落临时文件 → 解析出 packId → 改名为 <packId>.json
                        val tmpPack = File(dir, ".importing.json")
                        tmpPack.outputStream().use { out -> zis.copyTo(out) }
                        val p = tmpPack.inputStream().use { i2 ->
                            gson.fromJson(i2.reader(Charsets.UTF_8), WordPack::class.java)
                        }
                        val id = p?.manifest?.packId?.takeIf { it.isNotBlank() }
                            ?: error("词包缺少 packId")
                        packId = id
                        val target = File(dir, "$id.json")
                        if (!tmpPack.renameTo(target)) {
                            tmpPack.copyTo(target, overwrite = true)
                            tmpPack.delete()
                        }
                        val ad = File(ctx.filesDir, "audio/$id").apply { mkdirs() }
                        audioDir = ad
                        // 若音频条目先于 pack.json 出现，此刻把它们搬过去
                        pendingAudio?.listFiles()?.forEach { f ->
                            val dst = File(ad, f.name)
                            if (!f.renameTo(dst)) {
                                f.copyTo(dst, overwrite = true)
                                f.delete()
                            }
                        }
                        pendingAudio?.delete()
                        pendingAudio = null
                    }

                    nm.contains("audio/") && !e.isDirectory -> {
                        val fn = nm.substringAfterLast('/')
                        if (fn.isNotBlank()) {
                            var ad = audioDir
                            if (ad == null) {
                                ad = pendingAudio
                                if (ad == null) {
                                    ad = File(ctx.cacheDir, "import_audio_tmp")
                                    ad.deleteRecursively()
                                    ad.mkdirs()
                                    pendingAudio = ad
                                }
                            }
                            File(ad, fn).outputStream().use { out -> zis.copyTo(out) }
                            pending++
                        }
                    }
                }
                zis.closeEntry()
                e = zis.nextEntry
            }
        }

        pendingAudio?.deleteRecursively()
        val id = packId ?: error("压缩包内未找到 pack.json")
        if (pending == 0) {
            // 压缩包里没有音频：删掉可能是空壳的目录，保持交付目录整洁
            File(ctx.filesDir, "audio/$id").takeIf { it.isDirectory && it.listFiles()?.isEmpty() == true }
                ?.delete()
        }
        return id
    }

    private fun displayName(uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }
    }.getOrNull()

    /** 删除用户导入的词包（含随包落地的音频） */
    fun delete(packId: String): Result<Unit> = runCatching {
        File(File(ctx.filesDir, "packs"), "$packId.json").delete()
        File(ctx.filesDir, "audio/$packId").deleteRecursively()
        Unit
    }
}

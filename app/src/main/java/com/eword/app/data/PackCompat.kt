package com.eword.app.data

/**
 * 词包与应用的版本兼容检查。
 *
 * 应用与词包各自独立发版，就必然存在「新包配旧应用」「旧包配新应用」两种组合。
 * 词包 manifest 里的 [PackManifest.minAppVersion] 声明它需要的**最低应用版本**；
 * 应用拿自己的 versionName 去比，不够就提示用户升级应用，而不是让词包导入后
 * 在界面上莫名其妙地缺一块 —— 那种问题用户根本无从判断原因。
 *
 * 比较按「主.次.修订」逐段数值比，不做字符串比较：
 * '4.10.0' 字符串比 '4.9.0' 小，但版本号上它更大。
 */
object PackCompat {

    /** 从 "4.1.0" 这类版本串里取出数值段；取不出的段按 0 算。 */
    fun parseVersion(v: String?): List<Int> =
        (v ?: "").trim()
            .split(".", "-", "+")
            .map { seg -> seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    /** a < b 时返回负数，相等返回 0，a > b 时返回正数。 */
    fun compare(a: String?, b: String?): Int {
        val x = parseVersion(a)
        val y = parseVersion(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = (x.getOrElse(i) { 0 }) - (y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    /** 当前应用版本是否满足词包要求。词包没声明要求时一律算满足。 */
    fun satisfied(appVersion: String?, minAppVersion: String?): Boolean {
        val need = (minAppVersion ?: "").trim()
        if (need.isEmpty()) return true
        val have = (appVersion ?: "").trim()
        if (have.isEmpty()) return true
        return compare(have, need) >= 0
    }

    // ================= 自检 =================
    //
    // 这段是真判断：改坏了在词包列表页立刻抛异常，不必等用户导入后才发现版本比错。

    private fun check(cond: Boolean, msg: String) {
        if (!cond) throw IllegalStateException("版本比较被改坏：$msg")
    }

    fun selfCheck(): Boolean {
        check(compare("4.1.0", "4.1.0") == 0, "同版本应相等")
        check(compare("4.1.0", "4.0.0") > 0, "4.1.0 应大于 4.0.0")
        check(compare("4.0.0", "4.1.0") < 0, "4.0.0 应小于 4.1.0")
        check(compare("4.10.0", "4.9.0") > 0, "4.10.0 应大于 4.9.0（不能按字符串比）")
        check(compare("5.0", "4.9.9") > 0, "缺段按 0 算")
        check(compare("4.1", "4.1.0") == 0, "4.1 与 4.1.0 应相等")

        check(satisfied("4.1.0", "4.0.0"), "4.1.0 应满足 4.0.0 的要求")
        check(satisfied("4.1.0", "4.1.0"), "恰好相等应满足")
        check(!satisfied("4.0.0", "4.1.0"), "4.0.0 不应满足 4.1.0 的要求")
        check(satisfied("4.1.0", null), "词包没声明要求时应满足")
        check(satisfied("4.1.0", ""), "空声明应满足")
        check(satisfied("4.1.0", "   "), "空白声明应满足")
        return true
    }
}

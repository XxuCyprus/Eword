package com.eword.app.data

/**
 * 温习卡片的状态判定。
 *
 * 抽出来单独放，是因为这里的规则**只有一条是对的**，而它被改错过两次：
 *
 *   ① 早先「看过答案」只用一个标志表示 —— 于是不管用户点的是哪条路径，
 *      只要答案铺开了就一律判成「没记住」，两个判定按钮同时锁死。
 *      后果是任何词都走不到「记住了」，温习二与最终页面永远进不去，整条流程断死。
 *   ② 后来又错在把「例句逐条翻到底」也算成看过答案。翻到底只是把提示看完了，
 *      释义还没露，此时放行就等于没看答案就能点「记住了」，词会被跳过。
 *
 * 定下来的规则：
 *
 *   判定按钮 —— **点过「显示完整答案（核对用）」或「显示例句意思」之后才亮**。
 *               例句翻到底不算；只有本身没有例句的词例外（否则永远点不动）。
 *
 *   自动定案 —— 只有「逐条展示例句翻到底，最后点的是显示例句意思」这条路径
 *               才自动判「没记住」并锁死按钮：那是靠提示才想起来的。
 *               点「显示完整答案（核对用）」无论何时点，都只是核对，看完由用户自己选。
 *
 * 所以「例句翻到底」与「看过答案」必须分开记 —— 前者是事实，后者是用户的选择。
 */
object ReviewFlow {

    /** 词条有例句、且例句已经逐条翻到底。 */
    fun examplesExhausted(exampleCount: Int, revealed: Int): Boolean =
        exampleCount > 0 && revealed >= exampleCount

    /**
     * 判定按钮是否可用。
     *
     * [revealedFull] = 已点过「显示完整答案（核对用）」或「显示例句意思」。
     * 注意 [revealed] / [exampleCount] 只用来判断「没有例句的词」这一例外 ——
     * 例句翻到底**不**解锁按钮。
     */
    fun canJudge(exampleCount: Int, revealedFull: Boolean): Boolean =
        exampleCount == 0 || revealedFull

    /**
     * 是否已由「例句翻到底之后再点显示例句意思」定案为「没记住」。
     *
     * 只看 [revealedByHints] 这一个标志 —— 它只在那种情况下置位。
     * **[revealedFull] 绝不能参与判定**：点「显示完整答案（核对用）」也会置位它，
     * 用它参与会让核对的人被当成靠提示才想起来的。
     */
    fun autoForgetful(revealedByHints: Boolean): Boolean = revealedByHints

    // ================= 自检 =================
    //
    // 这段是真判断，不是注释：改坏了在首次温习时立刻抛异常，不必等回到手机上才发现。
    // 覆盖下面这张表里的每一种状态。
    //
    //   什么都没点                     → 灰
    //   翻到一半点显示完整答案          → 可点，自己选
    //   翻到底（释义未露）              → 灰
    //   翻到底 → 显示完整答案           → 可点，自己选
    //   翻到底 → 显示例句意思           → 自动没记住，锁死
    //   没有例句的词                    → 可点

    private fun check(cond: Boolean, msg: String) {
        if (!cond) throw IllegalStateException("温习判定规则被改坏：$msg")
    }

    fun selfCheck(): Boolean {
        // 什么都没点：灰
        check(!canJudge(5, false), "没点过任何『看答案』按钮时不应可判")
        check(!canJudge(1, false), "只有一条例句、没看过释义时不应可判")

        // 例句没翻完就点显示完整答案：可判，且不算没记住
        check(canJudge(5, true), "点过显示完整答案后应当可判")
        check(!autoForgetful(false), "显示完整答案不等于靠提示，不应自动判没记住")

        // 例句翻到底但释义还没露：仍然灰
        check(examplesExhausted(5, 5), "5 条翻完应当算翻到底")
        check(!canJudge(5, false), "例句翻到底但没看释义时不应可判")

        // 翻到底之后再点显示完整答案：可判，且不算没记住
        check(canJudge(5, true), "翻完例句后点显示完整答案应当可判")
        check(!autoForgetful(false), "翻完例句后看完整答案不应自动判没记住")

        // 翻到底之后再点显示例句意思：自动没记住
        check(autoForgetful(true), "靠例句提示才想起来，应当自动判没记住")

        // 没有例句的词：直接可判，否则按钮永远点不动
        check(canJudge(0, false), "没有例句的词应当可直接判定")
        check(!examplesExhausted(0, 0), "没有例句不应算翻到底")

        check(!examplesExhausted(5, 4), "5 条只翻 4 条不应算翻到底")

        return true
    }
}

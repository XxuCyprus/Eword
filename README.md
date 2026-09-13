# Eword

**用四六级历年真题的原句背单词。**

<p>
  <img src="https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white" alt="Android 11+">
  <img src="https://img.shields.io/badge/%E5%BA%94%E7%94%A8-v4.1.1-2F6BFF" alt="应用版本 v4.1.1">
  <img src="https://img.shields.io/badge/%E8%AF%8D%E5%8C%85-v13-6B7280" alt="词包版本 v13">
  <img src="https://img.shields.io/badge/license-CC%20BY--NC--SA%204.0-6B7280" alt="许可证">
</p>

---

## 这是什么

一款离线的 Android 背单词应用，语料是四六级历年真题。

与常见背单词应用的区别在于释义的来源：**它不转录词典，而是取这个词在那道真题
句子里实际表达的含义**。每条释义都配该词所在的真题原句与官方中文译文，
出处标注到「哪年哪月第几套」；出处从小节语料里核实不了的例句宁可留空，不猜。
记一个词，等于同时记住它在真题里怎么用。

由此带出一条贯穿整个项目的数据原则：

> 凡是声称来自真题的内容，都必须能在真题原文里逐条核实。核实不了的宁可留空。

---

## 特性

**释义按来源分组**
真题里出现过的义项归在「真题」下，真题里没有、来自词汇大纲的归在「大纲补充」下。
用户始终清楚哪些考过、哪些只需了解。

**每词配美式发音**
随词包分发的离线音频，不使用系统语音合成。音标取自 CMU 发音词典的美式 IPA；
屈折形式凡是单独成条的，各自注音（`climbing /ˈklaɪmɪŋ/`、`carrots /ˈkærəts/`）。

**四阶段记忆闭环**

| 阶段 | 行为 |
|---|---|
| 识记 | 按单元逐词过，完整卡片一次性铺开 |
| 温习一 | 只显示单词，主动回忆 |
| 温习二 | 温习一里想起来的词，再巩固一轮 |
| 最终页面 | 已彻底记住的词入册，可「再记一次」退回重走 |

回忆环节有两条路径：**逐条展开例句**（翻到例句意思即记为「没记住」），
或**显示完整答案核对**（核对后自主判定）。
**看过释义之前「记住了」不可点击** —— 不让用户靠模糊印象蒙过去。
例句逐条展开时只给英文，看过释义之后才补上中文译文。

**一张卡片看全一个词**

```
单词 → 音标 · 级别 · 真题句数 → 词义 → 单词变形 → 形近词 → 近义词 → 短语搭配 → 例句
```

形近词可直接点击跳转，返回后原进度保留；短语搭配与例句可在设置中隐藏。

**词包与应用分离**
应用约 2 MB，词包约 67 MB。词包更新频繁、应用更新少，分开后换词包无需重装应用。
学习进度按「词包 + 词条」记录，换词包或升级词包均不受影响。词包支持多本共存。

---

## 安装

1. 前往 [Releases](https://github.com/XxuCyprus/Eword/releases/latest) 下载 `Eword-v4.1.1.apk`
2. 安装应用（首次安装需在系统设置中允许安装未知来源应用）
3. 打开[全部 Releases](https://github.com/XxuCyprus/Eword/releases)，找到词包那一版
   （标题形如 `Eword CET46 Pack v13`），下载 `Eword-CET46-Pack-v13.ewp`
4. 打开 Eword → **我的单词本** → **导入单词本** → 选择刚下载的 `.ewp`，等待导入完成

> 需要 Android 11 及以上。词包必须单独下载并导入，应用内不含词库数据。
> 应用与词包**各发各的 Release**：应用那一版标 Latest，词包不标，所以词包不在
> 「最新版」页面里，要到 Releases 列表中去找。两者**分别更新**，换新版词包
> 不必重装应用；已装词包的版本号在「我的单词本」和设置里都能看到。

---

## 项目结构

```
Eword/
├── app/
│   ├── build.gradle.kts              应用模块：SDK 版本、发布签名、混淆开关
│   ├── proguard-rules.pro            R8 混淆规则
│   └── src/main/
│       ├── AndroidManifest.xml       清单（未声明任何权限）
│       ├── java/com/eword/app/
│       │   ├── MainActivity.kt       入口 Activity
│       │   ├── Inflections.kt        单词变形：把词包里的形态数据整理成可展示的行
│       │   ├── data/
│       │   │   ├── Models.kt         数据模型（词条、义项、例句、搭配、变形…）
│       │   │   ├── ReviewFlow.kt     温习判定规则：什么时候可判定、什么时候自动判没记住
│       │   │   ├── PackRepository.kt 词包导入、解压、启停与删除
│       │   │   ├── PackCompat.kt     词包要求的应用版本比对：新包配旧应用时提示升级
│       │   │   ├── AppCore.kt        全局状态与学习进度读写
│       │   │   └── Pronouncer.kt     音频播放
│       │   └── ui/
│       │       ├── Nav.kt            页面路由
│       │       ├── Theme.kt          配色与圆角
│       │       ├── Components.kt     通用组件（词条详情、义项、变形、搭配、例句…）
│       │       ├── HomeScreen.kt     首页与功能区入口
│       │       ├── MemorizeScreen.kt 识记功能区
│       │       ├── ReviewScreen.kt   温习一 / 温习二 / 最终页面
│       │       ├── WordbookScreen.kt 我的单词本：导入、启停、打乱、搜索
│       │       └── SettingsScreen.kt 设置
│       └── res/                      图标、主题、字符串
├── .github/ISSUE_TEMPLATE/           Issue 模板
├── LICENSE.md                        许可证（CC BY-NC-SA 4.0）
├── SECURITY.md                       安全问题报告渠道（不走公开 Issue）
├── gradle/                           Gradle Wrapper
├── build.gradle.kts                  根构建脚本：插件版本
├── settings.gradle.kts               模块声明与依赖仓库
└── gradle.properties                 Gradle 运行参数
```

---

## 隐私

- **不申请任何权限** —— `AndroidManifest.xml` 里没有一条权限声明，包括网络权限；
  没有网络权限，它就没有能力把数据发出去。安装包里唯一一条权限是 AndroidX 自动
  注入的 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`（签名级、只供应用自己注册
  广播用，系统不向用户提示，也不碰任何数据）。
  例句、译文、音标、音频全部随词包离线提供。
- **数据仅存本机** —— 词包解压至应用私有目录，学习进度存于本地。
- **卸载即清空** —— 学习进度随卸载一并删除。

---

## 数据来源

例句、中文译文与短语搭配取自四六级历年真题原文及官方译文，并逐条核验中英是否对应；
近义词取自 WordNet；音标取自 CMU 发音词典。

> 真题原文与官方译文的版权归原出版方所有，本项目仅作学习用途。

---

## 许可证

[CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/)：署名、非商业性使用、相同方式共享。

---

<p align="center">
  <sub>Made by <a href="https://github.com/XxuCyprus">Lnaaa</a></sub>
</p>

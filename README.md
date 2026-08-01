# Calorease

个人卡路里记账 Android 应用。**纯本地运行，零网络权限，数据不出手机。**

Kotlin + Jetpack Compose 原生应用。构建与发布全部由 GitHub Actions 完成。

---

## 为什么做这个

市面上的卡路里应用大多要求注册账号、上传数据、推送广告，而记账本身只需要一个本地文件。
更重要的是：几乎所有应用都用固定公式（基础代谢 × 活动系数）估算每日消耗，
但这个数字对每个人都不准，而且没有任何机制去修正它。

Calorease 的核心是**校准**——用你实际的体重变化反推真实消耗，让公式的系统性误差被数据抵消。

> 公式只是假设，身体的实际反应才是证据。

---

## 主要功能

- **饮食记录**：内置 208 条食物库、13 个分类，支持按 100g 与按份两种计量；另有营养标签录入与自定义食物
- **消耗估算**：Mifflin-St Jeor 基础代谢 + 手表活动卡路里 + 手动运动条目
- **消耗校准**：`真实每日消耗 = 区间内平均摄入 − (体重变化 kg × 7700 ÷ 天数)`，三道数据质量守卫，条件不满足时拒绝出数并说明原因
- **目标模式**：可按「每日摄入」或「每日结余」设定，后者随运动量浮动
- **体重与体脂**：支持补录历史、7 次记录移动平均趋势线、瘦体重计算与趋势判断
- **备份与恢复**：走系统文件选择器，无需存储权限；支持覆盖与合并两种模式

---

## 技术栈

| 项 | 值 |
|---|---|
| 语言 / UI | Kotlin 2.0.21 + Jetpack Compose（BOM 2024.10.01） |
| 构建 | Gradle 8.10.2 / AGP 8.7.2 / JDK 17 |
| SDK | compileSdk 35，minSdk 24（core library desugaring 以使用 `java.time`） |
| 序列化 | kotlinx-serialization |
| 权限 | **无**。`AndroidManifest.xml` 中零权限声明，连 `INTERNET` 都没有 |
| 存储 | `filesDir` 下的 JSON 文件 |
| CI/CD | GitHub Actions（编译 + 单元测试 + 产物校验 + 自动发布 Release） |

依赖只有 AndroidX 基础库、Compose 与 kotlinx-serialization。无 Room、无 Hilt、无网络库。

---

## 架构

```
ui/      Compose 界面。只读 Repository 的 StateFlow，所有改动调 Repository 的方法
data/    Models（数据结构）· Store（落盘）· Repository（唯一状态源）· Foods · Backup
logic/   纯函数：Nutrition · Calibration · Validation · Dates
```

`logic/` 与 `data/Store` 不依赖任何 Android API，因此 **55 项单元测试可直接在 JVM 上运行**，
不需要模拟器。

---

## 几个刻意的设计取舍

- **不用活动系数**：会与手表的活动卡路里重复计算。代价是模型系统性偏低，但校准会自动吸收这个偏差。
- **校验必须在修改数据之前**：`Validation.kt` 全是纯函数，输入字符串、返回 `Checked.Valid / Invalid`，
  拿不到可变状态——「校验前改数据」在类型上就做不到。
- **食物库放在仓库根目录**：`foods.json` 由构建期 Copy 任务搬进 assets，这样增删食物只需在 GitHub 网页上编辑一个 JSON。
  CI 会 `unzip -l` 检查它是否真的进了 APK——构建成功不等于产物正确。
- **失败的默认分支选安全的一侧**：备份文件认不出时什么都不做，而不是清空或强行导入。
- **备份格式向后兼容**：字段名与上一代 WebView 版本完全一致，旧备份可直接导入，并有测试守着这一点。

---

## 构建与发布

两条 workflow：

- `check.yml` — 推送任意分支时触发。`actionlint` 静态检查 → 编译 → 跑 55 项单元测试。不发布。
- `build-apk.yml` — 合并进 `main` 时触发。测试通过后 `assembleRelease`，用固定密钥签名，
  校验 `assets/foods.json` 是否在包内，然后发布到 Releases。

签名密钥固定并存放于 GitHub Secrets，因此每次更新都能覆盖安装、数据保留。

---

## 版本沿革

- **v1–v8「记账本」** — 单文件 `index.html`（原生 HTML/CSS/JS）套 WebView 壳。仓库 [Vassucavo/Ledger](https://github.com/Vassucavo/Ledger)，已停止维护。
- **v9 起「Calorease」** — Kotlin + Compose 原生重写。业务逻辑一字未改，单元测试从 jsdom 逐条搬到 JVM，
  断言几乎原样复用——这本身就是「测行为而非测实现」的证明。

---

## 开发方式

本项目由我完成需求定义、架构约束、方案取舍、真机验收与问题诊断，**代码由大语言模型编写**，
我通过 GitHub 网页评审并合并 PR。整个项目在没有电脑的条件下完成。

[`PROJECT.md`](./PROJECT.md) 中的「错误档案」记录了 15 个已踩过的坑，每条含症状、根因、修法与可推广的教训。
那一节是这个仓库里最有价值的部分。

---

## 关于数字的说明

食物表数值、Mifflin-St Jeor 公式、7700 这个常数、手表的活动卡路里——全部是估算值。
这个应用的价值不在于任何单个数字有多准，而在于连续记录数周后，校准能把系统性误差抵消掉。
**看趋势，不要看单日。**

要针对个人健康状况设定目标，请咨询医生或注册营养师。

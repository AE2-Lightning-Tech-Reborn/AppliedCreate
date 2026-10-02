# Applied Create (应用机动)

> **仓库说明：** 本仓库是 Applied Create（应用机动）的独立副本，并非原模组仓库。原模组作者为 **loliball**。原仓库为私有仓库，需要相应权限才能访问。

连接 [Create (机械动力)](https://modrinth.com/mod/create) 与 [Applied Energistics 2 (应用能源2)](https://modrinth.com/mod/ae2) 的桥梁模组。

将旋转应力存储在 ME 网络中，通过 P2P 通道传输，并使用样板供应器自动化动力合成。

[English](README.md)

---

## 功能特性

### 安山样板供应器 / 黄铜样板供应器

专为 Create 动力合成器设计的样板供应器。将其放置在已连接的动力合成器阵列旁，插入 AE2 处理样板，供应器会根据配方布局自动将原材料推入正确的合成器网格位置。

- **安山样板供应器** — 9 个样板槽（标准）
- **黄铜样板供应器** — 36 个样板槽（扩展），可从安山版本升级
- 同时提供完整方块和线缆面板两种形态

### ME 蓝图加农炮

从 ME 网络扣除材料建造 Create 蓝图，支持加速卡、合成卡、缺料等待、分页材料清单和存档续建。可放置 ME 线缆、部件与伪装板，按内存卡恢复过滤器和设置；每张编码样板消耗一张空白样板，升级卡按种类和数量扣除。蓝图内的样板和升级卡会先校验组件，普通库存及返还缓存不会复制。

### 应力 P2P 通道

通过 AE2 P2P 通道传输 Create 的旋转应力（转速 + 扭矩）。

- **应力 P2P 通道** — 安装在 AE2 线缆上的 P2P 通道部件

支持通过 Create 的机械物品（齿轮、传动杆等）进行调谐。

### ME 齿轮箱

ME 网络能量存储与 Create 旋转应力之间的双向转换器。

- **输出模式** (ME → 动力)：从 ME 网络读取存储的应力并输出为旋转
- **输入模式** (动力 → ME)：接收动力网络的旋转应力并存储至 ME 网络

右键打开 GUI。输出模式只设置转速，输入模式自动收取。整个动力网络的齿轮箱共同分配：原生动力先满足机器负载，输出箱只支付缺口，输入箱只收取原生剩余容量。空载可在 0 SU 下旋转；共享库存和重复更新不会凭空增加应力。

### 动能接收器

接收 Create 的旋转应力并转换为 AE2 电网功率 (AE)。将其连接到动力网络，即可用机械力为 ME 系统供电。滚轮调节转换倍率（1x–256x）。

### 创造应力元件

创造模式下的无限应力来源，用于测试和建造。无需任何动力输入即可提供无限应力容量。可通过创造马达互相转换合成。

### 应力存储系统

完整的分级系统，用于在 ME 网络中存储旋转应力值，架构类似于 AE2 的物品/流体存储元件：

| 等级 | 组件 | 元件 |
|------|------|------|
| 1k–256k | 安山样式存储组件 | 安山外壳存储元件 |
| 1M–256M | 黄铜样式存储组件 | 黄铜外壳存储元件 |

包含自定义处理器（安山/黄铜应力处理器）和电路板，可通过 AE2 压印器或切割机制作。元件可拆解为外壳和组件。

---

## 支持版本

| 分支 | Minecraft | 模组加载器 | 状态 |
|------|-----------|-----------|------|
| [main](https://github.com/loliball/AppliedCreate/tree/main) | 1.20.1 | Forge | 活跃 |
| [1.21.1-neoforge](https://github.com/loliball/AppliedCreate/tree/1.21.1-neoforge) | 1.21.1 | NeoForge | 活跃 |

## 依赖 (1.21.1 NeoForge)

| 模组 | 版本 | 是否必需 |
|------|------|----------|
| [NeoForge](https://neoforged.net/) | 21.1.x (1.21.1) | 是 |
| [Create (机械动力)](https://modrinth.com/mod/create) | 6.0+ | 是 |
| [Applied Energistics 2 (应用能源2)](https://modrinth.com/mod/ae2) | 19.0+ | 是 |
| [Kotlin for Forge](https://modrinth.com/mod/kotlin-for-forge) | 5.0+ | 是 |

## 安装

1. 安装 NeoForge 1.21.1
2. 安装以上全部必需依赖
3. 将 `appliedcreate-x.x.x.jar` 放入 `mods/` 文件夹
4. 启动游戏

## 链接

- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/applied-create)
- [Modrinth](https://modrinth.com/mod/applied-create)

## 许可证

All Rights Reserved (保留所有权利)

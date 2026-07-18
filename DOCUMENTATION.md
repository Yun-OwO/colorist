# Colorist — 深度技术文档

> 本文档对 Colorist 模组进行了全方位、极为详细的剖析，涵盖技术架构、玩法机制、数值系统、艺术设计、视觉效果、音效设计、掉落体系、合成体系、世界生成等多个维度。

---

## 一、项目概述

**Colorist** 是一个基于 **KubeJS** 框架开发的 Minecraft 模组，核心主题是"颜色"与"属性"的深度融合。它将 Minecraft 原版的 16 种染料颜色体系与 RGB 色彩空间相结合，构建了一套完整的"魔法染色与属性注入"系统。玩家可以通过收集染料、合成特殊道具，最终锻造出拥有动态属性的魔法书，释放基于颜色计算的射线攻击。

**核心设计理念**：颜色即力量。每一种颜色都被量化为 RGB 数值，并进一步转化为攻击力、生命值、暴击率、暴击伤害、消耗等 RPG 属性。颜色越纯、配方越复杂，最终产出的装备属性越强。

---

## 二、技术架构

### 2.1 框架依赖

| 依赖 | 说明 |
|------|------|
| **KubeJS** | 核心脚本框架，允许用 JavaScript 编写 Minecraft 模组逻辑 |
| **ProbeJS** | KubeJS 的辅助工具，提供 IDE 智能提示和代码补全支持 |
| **Minecraft 1.20+** | 数据包格式版本 10（`pack_format: 10`） |

### 2.2 目录结构与职责

```
colorist/
├── startup_scripts/        # 启动脚本：注册物品、方块、全局对象
│   ├── lib.js              # 全局工具库：颜色计算、属性计算、格式化函数
│   ├── item.js             # 物品注册
│   ├── block.js            # 方块注册（含魔法台交互逻辑）
│   ├── magic_table.js      # 魔法台右键交互核心逻辑
│   └── ore.js              # 矿石世界生成（已注释，改用 datapack）
│
├── server_scripts/         # 服务端脚本：配方、掉落、事件处理
│   ├── recipe.js           # 所有合成配方定义
│   ├── loot.js             # 实体/方块战利品表修改
│   ├── block.js            # 方块破坏事件处理
│   ├── magic_book.js       # 魔法书右键射线攻击逻辑
│   └── inventory.js        # 背包变更/玩家重生：生命值加成
│
├── client_scripts/         # 客户端脚本：粒子、音效、提示
│   ├── magic_book.js       # 客户端粒子特效与音效处理
│   └── tooltip.js          # 物品提示框（tooltip）渲染
│
├── assets/colorist/        # 资源包（纹理、模型、本地化）
│   ├── lang/zh_cn.json     # 中文语言文件
│   ├── models/block/       # 方块模型 JSON
│   └── textures/           # 物品和方块纹理（PNG）
│
├── datapacks/colorist/     # 数据包（世界生成）
│   └── data/worldgen/configured_feature/
│       └── colorist_magic_crystal_ore.json  # 魔虹矿石生成配置
│
├── config/                 # KubeJS 配置文件
│   ├── common.properties   # 通用配置
│   ├── client.properties   # 客户端配置
│   └── probejs.json        # ProbeJS 配置
│
└── documentation/          # 生成的项目文档（HTML）
```

### 2.3 脚本优先级系统

通过 `// priority: N` 注释控制加载顺序：

| 文件 | 优先级 | 说明 |
|------|--------|------|
| `lib.js` | 1000 | 最高优先级，全局工具函数最先加载 |
| `magic_table.js` | 999 | 次高优先级，魔法台核心逻辑依赖 lib.js |

### 2.4 全局命名空间设计

所有全局变量和函数挂载在 `global` 对象上，这是 KubeJS 的跨脚本共享机制。

**核心常量：**
- `global.MAX_ATTRS = 12` — 魔法书最多可容纳 12 张魔虹术纸
- `global.PROG_LENGTH = 18` — 属性进度条总长度
- `global.MOD_ID = "colorist"` — 模组 ID
- `global.COLOR` — 17 种颜色染料到十六进制色值的映射表
- `global.ALL_ITEMS` — 所有注册物品的 ID 收集数组

---

## 三、物品系统详解

### 3.1 物品清单

| 物品 ID | 中文名 | 堆叠上限 | 类型 | 说明 |
|---------|--------|----------|------|------|
| `rainbow_dye` | 虹染料 | 64 | basic | 七彩染料，合成魔法书的核心材料 |
| `grayscale_dye` | 阴阳染料 | 64 | basic | 黑白灰四色灰度染料 |
| `bleak_dye` | 黯淡染料 | 64 | basic | 土色系暗调染料 |
| `soil_dye` | 土色染料 | 64 | basic | 泥土色染料，基础合成材料 |
| `magic_book` | 魔虹术书 | 1 | 带耐久 | 核心武器，最大耐久 1000 |
| `magic_paper` | 魔虹术纸 | 1 | basic | 承载"颜色属性"的载体纸张 |
| `magic_crystal` | 魔虹术晶 | 64 | basic | 升级材料，增加颜色等级 |

### 3.2 物品注册机制

所有物品通过 `global.ITEM(e, id, maxStack, type)` 统一注册，该函数：
1. 将物品 ID 推入 `global.ALL_ITEMS` 数组
2. 调用 `e.create()` 创建物品
3. 设置最大堆叠大小（默认 64）

### 3.3 魔法书特殊属性

魔法书注册时设置 `maxDamage(1000)`，但它的耐久度并非传统意义上的"消耗耐久"——其耐久度是一个**动态显示**，反映当前属性等级与消耗等级的比值：

```javascript
item.setDamageValue((1 - Math.max(item.nbt.attr.level / (cost * 100), 0)) * 1000);
```

这意味着：
- 当属性等级足够高时，耐久条几乎是满的
- 当属性等级低于消耗时，耐久条会下降
- 魔法书不会真正"损坏消失"，而是通过属性等级消耗来限制使用

---

## 四、方块系统详解

### 4.1 方块清单

| 方块 ID | 中文名 | 硬度 | 亮度 | 工具要求 | 标签 |
|---------|--------|------|------|----------|------|
| `magic_table` | 魔虹术台 | 1.0 | 3 | 无 | 无 |
| `magic_crystal_ore` | 魔虹矿石 | 2.0 | 无 | 镐子 | `mineable/pickaxe`, `forge:ore` |

### 4.2 魔虹术台详细设计

**模型结构：**
- 采用 `block/block` 父模型
- 尺寸：16×12×16 像素（比标准方块矮 4 像素，高度 12/16 = 0.75 格）
- 顶面纹理：`magic_table_top.png`
- 底面纹理：`magic_table_bottom.png`
- 侧面纹理：`magic_table_side.png`（UV 映射从 4 像素处开始，偏移后视觉上居中）

**方块实体数据：**
```javascript
{
    data: {
        item: "minecraft:air",  // 当前放置的物品 ID
        nbt: {}                  // 放置物品的 NBT 数据
    }
}
```

**交互逻辑（`global.MAGIC_TABLE`）：**

| 操作 | 条件 | 效果 |
|------|------|------|
| **放置物品** | 台面为空 | 手持物品放上台面，物品悬浮在台上方（漂浮实体） |
| **取出物品** | 蹲下 + 右键 | 取回台面上的物品 |
| **染色** | 台面有魔虹术纸 + 手持染料 | 消耗染料，将颜色混入术纸（颜色混合算法） |
| **添加术晶** | 台面有物品 + 手持术晶 | 提升等级 +5 |
| **添加术纸** | 台面有魔法书 + 手持术纸 | 将术纸属性注入魔法书（最多 12 张） |
| **破坏方块** | 方块被破坏 | 自动归还台面物品给玩家 |

### 4.3 漂浮物品展示机制

魔虹术台使用独创的**漂浮物品实体**来展示台面上的物品：

```javascript
global.FLOAT_ITEM = (world, item, block) => {
    const iteme = world.createEntity("item");
    iteme.setPos(x + 0.5, y + 1, z + 0.5);  // 方块上方 1 格
    iteme.age = -32768;       // 年龄设为最小值，防止自动消失
    iteme.setPickUpDelay(-1); // 拾取延迟为 -1，防止被拾取
    iteme.setNoGravity(true); // 无重力漂浮
    iteme.item.count = 1;     // 只显示 1 个
    iteme.spawn();
};
```

这个设计使玩家可以直观地看到台面上正在处理的物品，并且该物品不受物理影响。

---

## 五、颜色系统

### 5.1 颜色映射表

`global.COLOR` 将 Minecraft 原版 16 种染料 + 1 种自定义染料映射为十六进制色值：

| 染料 | 十六进制 | 视觉预览 | 分类 |
|------|----------|----------|------|
| `white_dye` | `#F9FFFE` | 近白 | 无色系 |
| `orange_dye` | `#F9801D` | 橙色 | 暖色系 |
| `magenta_dye` | `#C74EBD` | 品红 | 暖色系 |
| `light_blue_dye` | `#3AB3DA` | 淡蓝 | 冷色系 |
| `yellow_dye` | `#FED83D` | 黄色 | 暖色系 |
| `lime_dye` | `#80C71F` | 黄绿 | 绿色系 |
| `pink_dye` | `#F38BAA` | 粉色 | 暖色系 |
| `gray_dye` | `#474F52` | 灰色 | 无色系 |
| `light_gray_dye` | `#9D9D97` | 淡灰 | 无色系 |
| `cyan_dye` | `#169C9C` | 青色 | 冷色系 |
| `purple_dye` | `#8932B8` | 紫色 | 冷色系 |
| `blue_dye` | `#3C44AA` | 蓝色 | 冷色系 |
| `brown_dye` | `#835432` | 棕色 | 土色系 |
| `green_dye` | `#5E7C16` | 绿色 | 绿色系 |
| `red_dye` | `#B02E26` | 红色 | 暖色系 |
| `black_dye` | `#1D1D21` | 近黑 | 无色系 |
| `soil_dye` | `#8B7E6B` | 土色 | 土色系 |

### 5.2 颜色混合算法

颜色混合是模组的核心算法之一，用于在染色过程中逐步改变颜色：

```javascript
global.MERGE_COLOR = (c1, c2, ratio) => {
    // ratio 默认 0.5，表示新颜色占比
    const r = Math.round(r1 * (1 - ratio) + r2 * ratio);
    const g = Math.round(g1 * (1 - ratio) + g2 * ratio);
    const b = Math.round(b1 * (1 - ratio) + b2 * ratio);
    return `#${r.toString(16).padStart(2, "0")}...`;
};
```

**染色时的混合比例：**
```javascript
data.nbt.attr = global.MERGE_COLOR(data.nbt.attr, icolor, 1 / data.nbt.level);
```

关键设计：`ratio = 1 / level`，意味着：
- 等级 1：ratio = 1.0 → 新颜色完全覆盖旧颜色
- 等级 5：ratio = 0.2 → 新颜色占 20%，旧颜色占 80%
- 等级越高，颜色变化越"粘稠"，越难改变

这模拟了"颜色积累"的概念——前期颜色容易改变，后期颜色趋于稳定。

### 5.3 颜色到属性映射

```javascript
global.CALC_ATTR = nbt => {
    const rgb = global.HEX_TO_RGB(color);
    for (let key in rgb) {
        attrs.brightness += rgb[key] / 255 / 3;  // 亮度 = RGB 平均值
        attrs[key] = Math.round((rgb[key] / 255) * 10);  // 0-255 → 0-10
    }
    attrs.brightness = Math.round(attrs.brightness * 10);
    attrs.darkness = 10 - attrs.brightness;  // 暗度 = 10 - 亮度
};
```

**属性映射关系：**
| 颜色通道 | 属性名 | 映射范围 | 含义 |
|----------|--------|----------|------|
| R（红色） | `r` | 0-10 | 朱赤 |
| G（绿色） | `g` | 0-10 | 碧青 |
| B（蓝色） | `b` | 0-10 | 苍蓝 |
| 亮度（RGB均值） | `brightness` | 0-10 | 阳 |
| 暗度（10-亮度） | `darkness` | 0-10 | 阴 |

### 5.4 多属性聚合

当魔法书包含多张术纸（最多 12 张）时，使用 `CALC_ATTRS` 聚合：

```javascript
global.CALC_ATTRS = attrs => {
    // 对所有属性求和后取平均
    r.color = global.RGB_T0_HEX({
        r: Math.round((r.r / l / 10) * 255),
        g: Math.round((r.g / l / 10) * 255),
        b: Math.round((r.b / l / 10) * 255)
    });
};
```

---

## 六、数值系统与战斗公式

### 6.1 属性计算器

`global.VALUE_COUNTER(attr, zero)` 将颜色属性转换为战斗数值：

```javascript
res = {
    cost: 0.9 - b / 150,           // 消耗：蓝色越高，消耗越低
    atk: r^1.1 / 10 + level^0.8 / 5, // 攻击力：红色和等级驱动
    hp:  g^1.1 / 5 + level^0.8 / 5,  // 生命值：绿色和等级驱动
    br:  sqrt(brightness) * 2.5 / 100, // 暴击率：亮度驱动
    bd:  darkness / 100              // 暴击伤害：暗度驱动
};
```

### 6.2 数值公式详解

| 属性 | 公式 | 基础值 | 范围估计 | 设计意图 |
|------|------|--------|----------|----------|
| **消耗（cost）** | `0.9 - b/150` | 0.9 | 0.83 ~ 0.9 | 蓝色系降低消耗，鼓励蓝色路线 |
| **攻击力（atk）** | `r^1.1/10 + level^0.8/5` | 0 | 0 ~ 6+ | 指数增长，红色越高收益越大 |
| **生命值（hp）** | `g^1.1/5 + level^0.8/5` | 0 | 0 ~ 12+ | 绿色路线生存向，等级加成显著 |
| **暴击率（br）** | `sqrt(brightness)*2.5/100` | 0 | 0 ~ 25% | 亮度=暴击率，阴阳平衡概念 |
| **暴击伤害（bd）** | `darkness/100` | 0 | 0 ~ 0.1 (即 1.0x−1.1x) | 暗度=暴击倍率加成 |

**指数函数的作用：**
- `r^1.1` 和 `g^1.1`：当属性值较高时，收益呈超线性增长，鼓励玩家追求极致颜色
- `level^0.8`：等级收益递减但有上限，避免无限升级
- `sqrt(brightness)`：暴击率增长递减，防止暴击率过高

### 6.3 魔法书使用机制

**使用条件：**
1. 必须持有魔法书
2. 不能重复使用（`usingMagic` 标记防止连发）
3. 当前属性等级必须 >= 消耗值

**使用时消耗：**
```javascript
global.ATTR_ADDER(item.nbt.attrs[0], "level", -cost);
// 每次使用消耗第一张术纸的等级
// 耐久条随之更新：越用越少，最终需要补充术晶
```

**射线攻击流程：**
1. 右键触发射线（10 格射程 RayTrace）
2. 持续 10 ticks（0.5 秒）
3. 命中实体时计算伤害
4. 暴击判定：`Math.random() < br`
5. 暴击时伤害倍率：`damage *= bd`

### 6.4 生命值加成系统

魔法书在背包中时，为玩家提供基于属性计算的生命值加成：

```javascript
// inventory.js
const hp = global.VALUE_COUNTER(attr).hp;
player.getAttribute("minecraft:generic.max_health").setBaseValue(mhp + hp);
```

- 魔法书在背包中 → 获得 HP 加成
- 魔法书被移除 → 移除 HP 加成
- 玩家重生 → 重置加成状态并重新计算
- 使用 `persistentData.hasHpBonus` 布尔值防止重复加成

---

## 七、合成体系

### 7.1 基础染料合成

模组扩展了原版染料的获取途径，使所有染料可以通过基础材料合成：

| 染料 | 配方 | 原材料 |
|------|------|--------|
| 橙色染料 | 无形状 | 萤石粉 |
| 绿色染料 | 无形状 | 草 / 睡莲 / 竹子 / 苔藓块 |
| 黄绿色染料 | 无形状 | 小麦种子 |
| 棕色染料 | 无形状 | 棕色蘑菇 |
| 红色染料 | 无形状 | 红石粉 / 蜘蛛眼 |
| 黑色染料 | 无形状 | 煤炭 |
| 白色染料 | 无形状 | 糖 |
| 灰色染料 | 无形状 | 火药 |
| 黄色染料 | 无形状 | 土色染料 + 白色染料 |
| 土色染料 | 无形状 | 泥土（1→4）/ 马铃薯 / 小麦 |

### 7.2 特殊染料合成

**虹染料（rainbow_dye）：**
```
赤 + 橙 + 黄 + 绿 + 青 + 蓝 + 紫 → 虹染料
```
（红、橙、黄、绿、青、蓝、紫 7种染料）

**阴阳染料（grayscale_dye）：**
```
黑 + 灰 + 淡灰 + 白 → 阴阳染料
```

**黯淡染料（bleak_dye）：**
```
棕 + 黄绿 + 淡蓝 + 品红 + 粉红 + 土色 → 黯淡染料
```

这三个特殊染料代表了色彩理论的三个维度：
- **虹染料**：全色相（hue）
- **阴阳染料**：全明度（lightness）
- **黯淡染料**：低饱和度（saturation）

### 7.3 魔法书合成

**3×3 有序合成：**

| 虹染料 | 黯淡染料 | 阴阳染料 |
|--------|----------|----------|
| 红石粉 | 书 | 红石粉 |
| 糖 | 萤石粉 | 火药 |

初始 NBT：
```javascript
{ attrs: [], attr: {}, hasHpBonus: false }
```

这个配方设计富有象征意义：
- 顶部三种特殊染料代表"色彩三维度"
- 中间红石+书+红石代表"能量注入书籍"
- 底部糖（白）、萤石（橙）、火药（灰）分别对应三种染料的原料

### 7.4 魔虹术台合成

**3×3 有序合成：**

| 空 | 魔法书 | 空 |
|----|--------|-----|
| 术晶 | 哭泣黑曜石 | 术晶 |
| 哭泣黑曜石 | 哭泣黑曜石 | 哭泣黑曜石 |

哭泣黑曜石通过 **TNT + 黑曜石** 无形状合成获得。

### 7.5 魔虹术纸清洗

```
术晶 + 魔虹术纸（任意等级/颜色） + 白色染料 → 魔虹术纸（保留等级，颜色重置为 #FFFFFF）
```

通过 `.modifyResult` 回调实现 NBT 数据的保留和重置。

### 7.6 烧炼配方

```
魔虹矿石 → 熔炉烧炼（3秒）→ 魔虹术晶（+50经验）
```

---

## 八、掉落与战利品体系

### 8.1 实体掉落

| 实体 | 掉落物 | 数量 | 概率 | 设计意图 |
|------|--------|------|------|----------|
| **女巫** | 魔虹术纸（青蓝色 `#00CCCC`） | 1-2 | 100% pool | 女巫=魔法生物，掉落术纸 |
| **女巫** | 魔虹术晶 | 2-3 | 100% pool | 女巫是主要术晶来源 |
| **苦力怕** | 魔虹术纸（绿色 `#66FF00`） | 0-1 | 100% pool | 绿色=爆炸=苦力怕 |
| **苦力怕** | TNT | 0-1 | 100% pool | 辅助合成哭泣黑曜石 |
| **骷髅** | 魔虹术纸（白色 `#FFFFFF`） | 0-1 | 100% pool | 白色=骨头=骷髅 |
| **监守者** | 魔虹术晶 | 4-6 | 100% pool | 最高端掉落，奖励挑战 |
| **监守者** | 黑色染料 | 0-5 | 100% pool | 黑色=深邃=监守者 |
| **监守者** | 魔虹术纸（深青色 `#008888`） | 0-3 | 100% pool | 深青色=幽暗=古城 |
| **末影人** | 魔虹术晶 | 1-2 | 100% pool | 末影=紫色=魔法 |
| **末影人** | 黑色染料 | 0-2 | 100% pool | 末影人掉落黑色 |
| **末影人** | 黑曜石 | 1 | 100% pool | 辅助合成哭泣黑曜石 |
| **末影人** | 魔虹术纸（青蓝色 `#00CCCC`） | 0-2 | 100% pool | 末影粒子颜色 |

### 8.2 方块掉落

| 方块 | 额外掉落 | 设计意图 |
|------|----------|----------|
| 紫水晶块 | 魔虹矿石 | 关联紫水晶与魔法 |

### 8.3 掉落颜色设计哲学

每种实体掉落的魔虹术纸颜色都与其主题对应：
- 女巫 → `#00CCCC`（青蓝色，魔法药水色）
- 苦力怕 → `#66FF00`（亮绿色，爆炸色）
- 骷髅 → `#FFFFFF`（白色，骨色）
- 监守者 → `#008888`（深青色，幽暗深邃）
- 末影人 → `#00CCCC`（青蓝色，末影粒子色）

---

## 九、世界生成

### 9.1 魔虹矿石生成

使用数据包 `configured_feature` 方式（而非 KubeJS 脚本）：

```json
{
    "type": "minecraft:ore",
    "config": {
        "discard_chance_on_air_exposure": 0.2,
        "size": 12,
        "targets": [
            { "state": "colorist:magic_crystal_ore",
              "target": "minecraft:stone_ore_replaceables" },
            { "state": "colorist:magic_crystal_ore",
              "target": "minecraft:deepslate_ore_replaceables" }
        ]
    }
}
```

| 参数 | 值 | 说明 |
|------|----|------|
| 矿脉大小 | 12 | 每个矿脉最多 12 个矿石 |
| 空气暴露概率 | 20% | 暴露在空气中的矿石有 20% 概率不生成 |
| 替换目标 | 石头和深板岩 | 两种岩层均可生成 |

> 注意：`ore.js` 中的 KubeJS 世界生成脚本已被注释，当前使用数据包方式。注释的脚本显示原计划使用 `uniformHeight(-64, 16)` 且 `count(100)` 的高频生成。

---

## 十、视觉效果与音效设计

### 10.1 粒子系统

魔法书射线施法期间，客户端每 tick 生成多种粒子，形成华丽的视觉轨迹：

| 粒子类型 | 频率 | 效果描述 |
|----------|------|----------|
| `dripping_obsidian_tear` | 每 tick | 黑曜石泪滴，代表暗色系魔法 |
| `electric_spark` | 每 tick | 电火花，代表能量爆发 |
| `enchant` | 每 tick | 附魔光效，代表魔法本质 |
| `sonic_boom` | 交替 | 音爆波纹，代表冲击力 |
| `dripping_dripstone_lava` | 交替 | 熔岩滴落，代表炽热力量 |
| `dripping_dripstone_water` | 交替 | 水滴，代表柔和力量 |

**粒子推进机制：**
```javascript
progress += Math.random() / 50 + 0.05;
// 每次增加 0.05-0.07，从玩家眼部沿视线方向前进
// 粒子位置 = 眼部位置 + 视线方向 × progress × 10格
```

这意味着粒子从玩家眼部开始，沿着视线方向以随机速度推进，形成一条 10 格长的粒子轨迹。

### 10.2 音效设计

| 时机 | 音效 | 音量 | 音高 | 说明 |
|------|------|------|------|------|
| 施法开始（循环） | `amethyst_block.hit` | 1.0 | 1.0+随机 | 水晶敲击声，每 tick 交替播放 |
| 施法开始（一次性） | `warden.death` | 0.6 | 1.5+随机 | 监守者死亡声，低频震撼音效 |
| 命中（普通） | `amethyst_block.break` | 0.8 | 1.4+随机 | 水晶破碎声 |
| 暴击 | `lightning_bolt.impact` | 0.8 | 1.4+随机 | 雷电冲击声，强调暴击的震撼感 |

### 10.3 Tooltip 渲染

**魔虹术纸 Tooltip：**
```
等级: [等级数值]（颜色与术纸颜色一致）
虹彩: ▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍  (红/绿/蓝比例条)
阴阳: ▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍  (白/暗灰比例条)
按住shift查看详情

[按住 Shift 后展开]
朱赤: 5
碧青: 3
苍蓝: 7
阴: 2
阳: 8
消耗: 0.85
攻击: +2.3
生命: +4.1
暴击率: +0.15
暴击伤害: +0.02
```

**魔虹术书 Tooltip：**
```
等级: [聚合等级]（颜色与聚合颜色一致）
虹彩: [聚合比例条]
阴阳: [聚合比例条]
数量: 5/12（当前术纸数量/上限）
按住shift查看详情

[按住 Shift 后展开]
朱赤: ...
碧青: ...
苍蓝: ...
阴: ...
阳: ...
消耗: ...
攻击: ...
生命: ...
暴击率: ...
暴击伤害: ...
数量: 5/12
```

### 10.4 渐变色文本

`global.GRADIENT_TEXT(text, startColor, endColor)` 函数将文本的每个字符渲染为渐变色，从起始色平滑过渡到结束色。虽然当前模组代码中没有直接调用此函数，但它提供了未来扩展的可能性（如标题、特殊提示等）。

---

## 十一、本地化与命名艺术

所有自定义物品/方块名称使用 Minecraft 格式化代码（§）进行多彩渲染：

| 物品 | 本地化代码 | 渲染效果 |
|------|-----------|----------|
| 虹染料 | `§d彩§e虹§3染§a料` | 粉/黄/青/绿四色渐变 |
| 阴阳染料 | `§8阴§7阳§r染料` | 深灰/浅灰渐变 |
| 黯淡染料 | `§d黯§3淡§r染料` | 粉/青色渐变 |
| 魔虹术书 | `§d魔§e虹§3术§a书` | 粉/黄/青/绿四色渐变 |
| 魔虹术纸 | `§d魔§e虹§3术§a纸` | 粉/黄/青/绿四色渐变 |
| 魔虹术晶 | `§d魔§e虹§3术§a晶` | 粉/黄/青/绿四色渐变 |
| 魔虹术台 | `§d魔§e虹§3术§a台` | 粉/黄/青/绿四色渐变 |
| 魔虹矿石 | `§d魔§e虹§3矿§a石` | 粉/黄/青/绿四色渐变 |

命名模式分析：
- "魔虹"系列物品使用统一的粉→黄→青→绿四色渐变
- 四种特殊染料各有独特的色彩渐变，与自身属性对应
- 土色染料无多彩渲染，贴合其"朴素"定位

---

## 十二、配置系统

### 12.1 通用配置 (`common.properties`)

| 配置项 | 值 | 说明 |
|--------|----|------|
| `matchJsonRecipes` | true | 启用 JSON 配方匹配 |
| `allowAsyncStreams` | true | 允许异步流处理 |
| `announceReload` | true | 重载时广播通知 |
| `startupErrorGUI` | true | 启动错误在 GUI 中显示 |
| `serverOnly` | false | 非纯服务端 |
| `creativeModeTabIcon` | `minecraft:purple_dye` | 创造模式标签图标为紫色染料 |

### 12.2 客户端配置 (`client.properties`)

| 配置项 | 值 | 说明 |
|--------|----|------|
| `backgroundColor` | `2E3440` | 深蓝灰背景（Nord 调色板） |
| `barColor` | `ECEFF4` | 亮白灰色文字 |
| `barBorderColor` | `ECEFF4` | 与文字同色边框 |
| `menuBackgroundBrightness` | 64 | 菜单背景亮度 |
| `menuBackgroundScale` | 32.0 | 背景缩放 |
| `blurScaledPackIcon` | true | 模糊缩放的图标 |

配色方案采用 **Nord 主题**风格，给人冷峻、专业的感觉。

---

## 十三、数据流与事件系统

### 13.1 网络通信

魔法书使用 KubeJS 的网络数据包系统进行客户端-服务端通信：

```
服务端                                      客户端
  │                                          │
  ├─ player.sendData("colorist:magic_start")─→ 接收并开始粒子渲染
  │   { r, g, b }                              │
  │                                          │
  ├─ player.sendData("colorist:magic_stop") ─→ 停止粒子渲染
  │   {}                                       │
  │                                          │
  ├─ player.sendData("colorist:crit") ──────→ 触发暴击特效与音效
      { crit: true/false }
```

### 13.2 事件驱动架构

| 事件 | 脚本 | 触发条件 |
|------|------|----------|
| `StartupEvents.registry("item")` | `startup_scripts/item.js` | 游戏启动 |
| `StartupEvents.registry("block")` | `startup_scripts/block.js` | 游戏启动 |
| `ItemEvents.rightClicked` | `server_scripts/magic_book.js` | 右键魔法书 |
| `ServerEvents.tick` | `server_scripts/magic_book.js` | 每 tick |
| `BlockEvents.broken` | `server_scripts/block.js` | 破坏方块 |
| `PlayerEvents.inventoryChanged` | `server_scripts/inventory.js` | 背包变化 |
| `PlayerEvents.respawned` | `server_scripts/inventory.js` | 玩家重生 |
| `ServerEvents.recipes` | `server_scripts/recipe.js` | 配方加载 |
| `ServerEvents.entityLootTables` | `server_scripts/loot.js` | 战利品表加载 |
| `ServerEvents.blockLootTables` | `server_scripts/loot.js` | 方块掉落表加载 |
| `ItemEvents.tooltip` | `client_scripts/tooltip.js` | 渲染物品提示 |
| `NetworkEvents.dataReceived` | `client_scripts/magic_book.js` | 接收网络数据 |
| `ClientEvents.tick` | `client_scripts/magic_book.js` | 客户端每 tick |

---

## 十四、扩展研究价值

### 14.1 色彩理论应用

Colorist 模组对色彩理论进行了系统化的游戏化转译：
- **色相（Hue）**：R/G/B 三通道对应红/绿/蓝
- **明度（Lightness）**：brightness/darkness 阴阳系统
- **饱和度（Saturation）**：通过多色混合来间接控制

这是一个值得深入研究的**色彩理论游戏化**案例。

### 14.2 数值平衡设计

模组的数值系统值得分析：
- 指数增长（`^1.1`）的设计意图和平衡考量
- 消耗与收益的权衡（蓝色降低消耗 vs 红色增加攻击）
- 多属性聚合的归一化策略

### 14.3 KubeJS 编程范式

Colorist 是 KubeJS 框架的优良实践案例：
- 全局函数库模式（`lib.js`）
- 优先级管理（`// priority: N`）
- 方块实体数据（BlockEntity）的运用
- 网络通信（NetworkEvents）的客户端-服务端分离
- 自定义工具提示（Tooltip）的渲染

### 14.4 可扩展方向

基于现有代码，可扩展的方向包括：
- 更多颜色效果（如颜色护盾、颜色光环）
- 颜色组合套装效果（类似 RPG 套装加成）
- 更多魔法台交互（如颜色提取、颜色转换）
- PvP 颜色克制系统（如红克绿、蓝克红）
- 颜色成就/进度系统
- 渐变色文本在 UI 中的实际应用
- 药水效果与颜色属性的联动

### 14.5 纹理与视觉设计

模组包含 8 张 PNG 纹理，可研究：
- 像素艺术风格分析
- 颜色搭配与主题一致性
- 方块模型 UV 映射技巧（侧面 UV 偏移 4 像素）

---

## 附录 A：文件清单

| 文件路径 | 行数 | 用途 |
|----------|------|------|
| `startup_scripts/lib.js` | 326 | 全局工具函数库 |
| `startup_scripts/item.js` | 12 | 物品注册 |
| `startup_scripts/block.js` | 23 | 方块注册 |
| `startup_scripts/magic_table.js` | 108 | 魔法台交互逻辑 |
| `startup_scripts/ore.js` | 12 | 矿石生成（已注释） |
| `server_scripts/recipe.js` | 199 | 合成配方 |
| `server_scripts/loot.js` | 67 | 战利品表 |
| `server_scripts/magic_book.js` | 72 | 魔法书服务端逻辑 |
| `server_scripts/block.js` | 6 | 方块破坏事件 |
| `server_scripts/inventory.js` | 50 | 背包/重生事件 |
| `client_scripts/magic_book.js` | 134 | 魔法书客户端特效 |
| `client_scripts/tooltip.js` | 29 | 物品提示框 |
| `assets/colorist/lang/zh_cn.json` | 13 | 中文语言文件 |
| `assets/colorist/models/block/magic_table.json` | 43 | 魔法台模型 |
| `datapacks/colorist/data/worldgen/configured_feature/colorist_magic_crystal_ore.json` | 27 | 矿石世界生成 |

---

## 附录 B：版本历史

| 提交 | 说明 |
|------|------|
| `82014ca` | 初始提交 |
| `5fe89b4` | 通用更新 |
| `df32700` | 通用更新 |
| `39178b2` | 通用更新 |
| `88f0ff6` | 通用更新 |
| `1df84d9` | 添加更多掉落 |
| `27470d2` | 添加粒子特效和音效 |
| `a55b823` | 修复 Bug |
| `c0ce6ba` | 当前最新（初始提交标记） |

---

> **文档生成时间**：2026-07-18  
> **分析范围**：全部源代码、配置文件、资源文件、数据包、Git 历史  
> **覆盖率**：100% 文件均被读取分析

---

---

# 十五、NeoForge 1.21.1 原生 Jar 模组移植分析

## 15.1 移植可行性总览

| 维度 | 原版（KubeJS） | 移植后（NeoForge 原生 Jar） | 评估 |
|------|----------------|----------------------------|------|
| 语言 | JavaScript | Kotlin | 类型安全大幅提升 |
| 加载器 | KubeJS 脚本引擎 | NeoForge Mod Loader | 独立运行，无需 KubeJS |
| 注册方式 | `StartupEvents.registry` | `DeferredRegister` | 更规范，IDE 支持更好 |
| 事件系统 | KubeJS 事件钩子 | NeoForge Event Bus / `@SubscribeEvent` | 类型安全事件 |
| 网络通信 | `player.sendData` | NeoForge `CustomPacketPayload` | 结构化，性能更好 |
| 世界生成 | 数据包 JSON / KubeJS API | `BiomeModifier` + `ConfiguredFeature` | 更灵活 |
| 配置 | `config/*.properties` | Forge Config API / `NightConfig` | 自动同步、GUI 编辑 |
| 构建工具 | 无（KubeJS 管理） | NeoGradle（`net.neoforged.moddev`） | 标准 Gradle 构建 |
| Java 版本 | 运行时 JVM | 编译目标 Java 21 | NeoForge 1.21.1 要求 |

### 15.1.1 可行性结论

**完全可行**。Colorist 的架构非常适合原生移植，原因如下：

1. **代码量适中**：原始脚本约 1000 行 JS，对应 Kotlin 约 1200-1500 行，属于小型 mod
2. **无外部复杂依赖**：仅依赖 Minecraft 原版内容，没有跨 mod 兼容性负担
3. **逻辑清晰分离**：物品/方块注册、事件处理、网络通信、客户端渲染各自独立
4. **KubeJS 原版已形成良好分层**：`startup_scripts` / `server_scripts` / `client_scripts` 的分层可以直接映射到 NeoForge 的 `common` / `client` / `server` source set

## 15.2 架构映射对照

### 15.2.1 注册映射

| KubeJS 原版 | NeoForge 原生 |
|-------------|---------------|
| `StartupEvents.registry("item")` → `global.ITEM(e, id)` | `DeferredRegister.Items` → `ITEMS.register(name, supplier)` |
| `StartupEvents.registry("block")` → `global.BLOCK(e, id)` | `DeferredRegister.Blocks` → `BLOCKS.register(name, supplier)` |
| `e.create(id, "basic")` | `new Item(new Item.Properties())` 或 `new Block(BlockBehaviour.Properties.of())` |
| `.maxStackSize(64)` | `.stacksTo(64)` |
| `.maxDamage(1000)` | `.durability(1000)` |
| `.soundType("stone")` | `.sound(SoundType.STONE)` |
| `.hardness(1.0)` | `.strength(1.0f)` |
| `.lightLevel(3)` | `.lightLevel(state -> 3)` |
| `.requiresTool(true)` | `.requiresCorrectToolForDrops()` |
| `.tagBlock("minecraft:mineable/pickaxe")` | `.tag(BlockTags.MINEABLE_WITH_PICKAXE, ...)` |
| `.blockEntity(e => e.initialData({...}))` | 独立的 `BlockEntity` 类 + `BlockEntityType` 注册 |
| `.rightClick(global.MAGIC_TABLE)` | 覆写 `BlockBehaviour.useItemOn`（或 `useWithoutItem`） + `InteractionResult` |

### 15.2.2 事件映射

| KubeJS 原版 | NeoForge 原生 |
|-------------|---------------|
| `ItemEvents.rightClicked` | `@SubscribeEvent` 监听 `PlayerInteractEvent.RightClickItem` |
| `ServerEvents.tick` | `@SubscribeEvent` 监听 `ServerTickEvent.Post` |
| `BlockEvents.broken` | `@SubscribeEvent` 监听 `BlockEvent.BreakEvent` |
| `PlayerEvents.inventoryChanged` | `@SubscribeEvent` 监听 `PlayerEvent.InventoryChanged` |
| `PlayerEvents.respawned` | `@SubscribeEvent` 监听 `PlayerEvent.PlayerRespawnEvent` |
| `ServerEvents.recipes` | 直接在 `Mod` 构造中调用 `registerRecipes` 静态方法，或使用数据包 JSON |
| `ServerEvents.entityLootTables` | `LootModifier` 系统 + `GlobalLootModifierSerializer` |
| `ItemEvents.tooltip` | `@SubscribeEvent` 监听 `ItemTooltipEvent` 或覆写 `Item.appendHoverText` |
| `ClientEvents.tick` | `@SubscribeEvent` 监听 `ClientTickEvent.Post` |
| `NetworkEvents.dataReceived` | `ClientPayloadHandler` / `ServerPayloadHandler` |

### 15.2.3 方块实体映射

KubeJS 的 `blockEntity(e => e.initialData({...}))` 在 NeoForge 中需要拆分为：

```
BlockEntity 类
├── 定义存储字段（item, nbt）
├── getUpdateTag() / handleUpdateTag() — 客户端同步
├── saveAdditional() / loadAdditional() — 持久化
└── 静态 BlockEntityType 注册
```

魔虹术台当前的 `BlockEntity` 数据模型：
```kotlin
class MagicTableBlockEntity(pos: BlockPos, state: BlockState) 
    : BlockEntity(ModBlockEntities.MAGIC_TABLE.get(), pos, state) {
    var storedItem: ItemStack = ItemStack.EMPTY
    var storedNbt: CompoundTag = CompoundTag()
}
```

### 15.2.4 网络通信映射

| 原版 | NeoForge |
|------|----------|
| `player.sendData("colorist:magic_start", {r, g, b})` | `PacketDistributor.sendToPlayer(player, MagicStartPayload(r, g, b))` |
| `CustomPacketPayload` 实现 | 需要 `StreamCodec` 编解码 |
| `NetworkEvents.dataReceived("colorist:magic_start")` | `ClientPayloadHandler.handle(MagicStartPayload)` |

**Payload 设计：**
```kotlin
// 施法开始
data class MagicStartPayload(val r: Float, val g: Float, val b: Float) 
    : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<MagicStartPayload>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "magic_start"))
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, MagicStartPayload::r,
            ByteBufCodecs.FLOAT, MagicStartPayload::g,
            ByteBufCodecs.FLOAT, MagicStartPayload::b,
            ::MagicStartPayload
        )
    }
    override fun type() = TYPE
}

// 施法停止
data class MagicStopPayload : CustomPacketPayload { ... }

// 暴击通知
data class CritPayload(val crit: Boolean) : CustomPacketPayload { ... }
```

## 15.3 合成配方移植

KubeJS 的 `ServerEvents.recipes` 中所有配方可以两种方式移植：

**方案 A：数据包 JSON（推荐用于简单配方）**
- 所有 `shapeless` 和 `shaped` 配方直接写成 JSON 文件放入 `data/colorist/recipe/`
- 缺点：无 NBT 输出的配方（如魔虹术纸清洗）需要特殊处理

**方案 B：代码注册（推荐用于复杂配方）**
- 在 `Mod` 主类的构造中监听 `RegisterRecipesEvent`
- 对于有 NBT 输出或需要 `.modifyResult` 的配方，使用自定义 `RecipeSerializer`

**特殊配方处理：**

1. **魔虹术纸清洗**（带 NBT 保留）——需要自定义 `Recipe` 实现：
   ```kotlin
   class WashMagicPaperRecipe : ShapelessRecipe {
       // 保留原 level，重置 attr 为 #FFFFFF
   }
   ```

2. **魔法书合成**（带初始 NBT）——使用 `ItemStack` 的 `applyComponents` 或 `DataComponentMap`：
   ```kotlin
   val result = ItemStack(ModItems.MAGIC_BOOK.get()).apply {
       // 设置初始 attrs 为空数组
   }
   ```

## 15.4 世界生成移植

原版通过数据包 JSON 的 `configured_feature` 已经可以直接在 NeoForge 中使用。但更推荐使用 NeoForge 的 `BiomeModifier` 系统：

```kotlin
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
object ModWorldGen {
    @SubscribeEvent
    fun registerBiomeModifiers(event: RegisterBiomeModifiersEvent) {
        event.register(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "magic_crystal_ore"),
            AddFeaturesBiomeModifier(
                // 在所有主世界生物群系中生成
                BiomeFilter.biome(),
                HolderSet.direct(
                    PlacedFeature.CONFIGURED_FEATURE_FINDER.get(
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "colorist_magic_crystal_ore")
                    )
                )
            )
        )
    }
}
```

## 15.5 掉落表移植

使用 NeoForge 的 `GlobalLootModifier` 系统：

```kotlin
class AddMagicLootModifier(
    private val paperItem: ItemStack,
    private val crystalCount: IntRange,
    looter: LootItemCondition
) : LootModifier(looter) {
    override fun doApply(generatedLoot: MutableList<ItemStack>, context: LootContext): MutableList<ItemStack> {
        generatedLoot.add(paperItem.copy())
        generatedLoot.add(ItemStack(ModItems.MAGIC_CRYSTAL.get(), crystalCount.random()))
        return generatedLoot
    }
}
```

---

# 十六、前置库选择与依赖分析

## 16.1 核心前置：Kotlin 语言支持

### 16.1.1 Kotlin for Forge（推荐）

| 属性 | 详情 |
|------|------|
| 名称 | **Kotlin for Forge** (KFF) |
| Modrinth | `ordsPcFz` |
| 作者 | thedarkcolour |
| 许可证 | LGPL-2.1 |
| 下载量 | 3900 万+ |
| NeoForge 1.21.1 | 支持（v5.12.0+） |
| 最新版本 | v6.3.0（对应 MC 1.21.11 / NeoForge 26.2） |

**为什么选 KFF 而非 KotlinLangForge：**
- KFF 是生态标准，几乎所有 Kotlin mod 都用它
- 提供 `KotlinLanguageProvider` 自动加载 `@Mod` object 声明
- 提供 `AutoKotlinEventBusSubscriber` 简化事件注册
- 支持 Kotlin 协程和序列化库
- 社区活跃，Discord 支持

**Gradle 配置：**
```kotlin
// build.gradle.kts
plugins {
    id("net.neoforged.moddev") version "2.0.78"
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
}

dependencies {
    // Kotlin for Forge 作为运行时依赖
    implementation("thedarkcolour:kotlinforforge-neoforge:6.3.0")
}
```

**neoforge.mods.toml 依赖声明：**
```toml
[[dependencies.colorist]]
    modId = "kotlinforforge"
    type = "required"
    versionRange = "[6.0.0,)"
```

### 16.1.2 Kotlin 语言优势与本项目的契合点

| Kotlin 特性 | 对应 Colorist 场景 |
|-------------|-------------------|
| `data class` | `ATTR`、`BASIC`、Payload 类 |
| 扩展函数 | `global.FULL()` → `String.toModId()` |
| 中缀函数 | 颜色混合运算 |
| 作用域函数 `apply`/`also` | ItemStack 构建、NBT 操作 |
| `sealed class` | 网络 Payload 类型层级 |
| 协程 | 粒子动画、延迟操作 |
| 类型安全构建器 DSL | 配方注册、配置定义 |
| 空安全 | 消除 NPE（如 `item.nbt?.attr`） |

## 16.2 GUI 框架前置

### 16.2.1 方案对比

| 框架 | 开发方式 | 样式系统 | 组件丰富度 | NeoForge 1.21.1 | 成熟度 |
|------|----------|----------|------------|-----------------|--------|
| **VoxelUI** | XML + MCSS | CSS 级联样式 | 高（按钮/输入框/滑块/下拉/网格/列表/物品槽） | 支持 | Beta |
| **LibGuiFoxified** | Kotlin/Java 声明式 API | 面板样式 | 中（按钮/标签/文本框/滑块/复选框/下拉） | 支持 | 稳定 |
| **Modern UI** | 声明式 API | 自定义 | 高（含扩展字体/emoji/模糊） | 支持 | 非常稳定 |
| 原生 Screen | 直接继承 `Screen` 类 | 手动绘制 | 无（需手写） | 原生 | 原生 |

### 16.2.2 推荐：LibGuiFoxified

对于 Colorist 的 GUI 需求（魔虹术台交互界面、魔法书属性面板、颜色混合预览），**LibGuiFoxified** 是最佳选择：

**理由：**
1. **声明式布局**：基于网格系统，避免手动计算像素坐标
2. **轻量级**：MIT 许可证，无额外依赖链
3. **物品槽支持**：原生支持 `WItemSlot`，完美适配魔虹术台需要展示物品的需求
4. **面板样式**：支持背景色、圆角、透明度控制，契合 Colorist 的色彩主题
5. **成熟稳定**：基于 Fabric 的成熟 LibGui 项目移植，API 文档完善

**魔虹术台 GUI 概念设计：**
```kotlin
class MagicTableScreen(syncId: Int, playerInv: PlayerInventory, ctx: ScreenHandlerContext) 
    : CottonInventoryScreen<MagicTableGui>(...) {
    
    init {
        // 中央：物品展示区 + 颜色预览
        val root = GridPanel(9, 3)
        
        // 左侧：输入槽（放染料/术纸/术晶）
        root.add(WItemSlot.of(blockInventory, INPUT_SLOT), 2, 1)
        
        // 中央：术纸预览（颜色实时渲染）
        root.add(ColorPreviewWidget(blockInventory), 4, 1)
        
        // 右侧：输出槽（取出成品）
        root.add(WItemSlot.of(blockInventory, OUTPUT_SLOT).setInsertingAllowed(false), 6, 1)
        
        // 底部：属性进度条
        root.add(AttrBarWidget(blockInventory), 0, 2, 9, 1)
        
        setRootPanel(root)
    }
}
```

**若选择 Modern UI：**
- 优势：提供更精细的渲染控制、圆角矩形、模糊效果
- 适合：需要高度自定义 UI 视觉效果（如颜色渐变背景、动画过渡）
- 代价：体积较大（~24MB），学习曲线较陡

**若选择 VoxelUI：**
- 优势：XML + MCSS 开发方式最接近现代前端，热重载支持
- 适合：UI 界面较多的中型 mod
- 代价：仍处于 Beta 阶段，API 可能变动

## 16.3 其他推荐前置库

### 16.3.1 配置管理

| 库 | 用途 | 推荐度 |
|----|------|--------|
| **NightConfig**（NeoForge 内置） | TOML/JSON 配置文件读写 | 默认使用 |
| **SuperMartijn642's Config Lib** | 自动生成配置 GUI | 可选 |
| **Cloth Config API** | 配置 GUI 框架 | 如果跨平台 |

### 16.3.2 粒子特效增强

| 库 | 用途 | 推荐度 |
|----|------|--------|
| **AAA Particles** | Effekseer 粒子特效（`.efkefc`） | 可选 |
| **AsyncParticles** | 异步粒子渲染（性能优化） | 玩家端可选 |

### 16.3.3 通用工具库

| 库 | 用途 | 推荐度 |
|----|------|--------|
| **Architectury API** | 跨平台抽象（如果未来考虑 Fabric 移植） | 视需 |
| **Resourceful Lib** | 通用工具集、配置、网络 | 推荐 |
| **BlueLib** | 简化 NBT、动画、数据管线 | 可选 |
| **GeckoLib** | 实体/方块动画（如果未来扩展） | 暂不需要 |

### 16.3.4 开发工具

| 库 | 用途 | 推荐度 |
|----|------|--------|
| **JEI / REI** | 配方查看集成 | 推荐（提供 API 依赖） |
| **Jade / The One Probe** | 方块信息提示 | 推荐（提供 API 依赖） |

## 16.4 依赖树总览

```
Colorist (NeoForge 1.21.1)
├── [必需] Kotlin for Forge (v6.x)
├── [必需] LibGuiFoxified (GUI 框架)
├── [推荐] NightConfig (NeoForge 内置)
├── [推荐] Resourceful Lib (工具集)
├── [可选] JEI API (配方查看)
├── [可选] Jade API (方块信息)
└── [玩家端可选] AsyncParticles (性能优化)
```

---

# 十七、GUI 交互方案设计

## 17.1 原版交互方式的问题

原版 Colorist 使用聊天栏消息进行所有交互反馈：

```javascript
player.tell(Text.of("染色成功").color(data.nbt.attr));
player.tell("该物品不可用于染色");
player.tell(`(${attrs.length}/${global.MAX_ATTRS})添加成功`);
```

**问题：**
1. 信息密度低：每次只能显示一行文本
2. 无法可视化：颜色预览只能用文字描述
3. 操作步骤繁琐：需要反复右键 + 看聊天栏确认
4. 无撤销/预览：放错染料无法撤销，颜色混合后不可逆
5. 属性展示不直观：需要按住 Shift 才能看详细数值

## 17.2 全新 GUI 设计方案

### 17.2.1 魔虹术台 GUI（核心交互界面）

**界面布局：**
```
┌──────────────────────────────────────────────┐
│  魔虹术台                                     │
├──────────┬───────────────────┬───────────────┤
│          │                   │               │
│  输入槽  │    颜色预览区      │   输出槽      │
│  (染料/  │   (实时渲染渐变色)  │  (成品术纸/   │
│   术纸/  │                   │   魔法书)     │
│   术晶)  │   当前颜色: #RRGGBB│               │
│          │                   │               │
├──────────┴───────────────────┴───────────────┤
│  颜色属性                                     │
│  虹彩: ▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍                  │
│  阴阳: ▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍                  │
│                                              │
│  朱赤: 5  碧青: 3  苍蓝: 7                    │
│  阴: 2    阳: 8    等级: 12                   │
├──────────────────────────────────────────────┤
│  战斗属性预览                                 │
│  消耗: 0.85  攻击: +2.3  生命: +4.1           │
│  暴击率: +15%  暴击伤害: +2%                  │
├──────────────────────────────────────────────┤
│         [ 确认合成 ]    [ 取出 ]    [ 撤销 ]   │
└──────────────────────────────────────────────┘
```

**交互流程：**
1. 右键打开魔虹术台 → 弹出 GUI 界面
2. 放入待处理物品（术纸/魔法书）→ 自动显示在中央预览区
3. 放入染料 → 颜色预览区实时更新混合结果
4. 放入术晶 → 等级实时更新
5. 点击"确认合成"→ 执行染色/注入操作
6. 点击"取出"→ 取回成品
7. 点击"撤销"→ 还原到上一步状态

**关键交互改进：**
- **实时预览**：放入染料后，颜色预览区立即显示混合后的颜色（使用 `GuiGraphics.fill` 绘制）
- **批量操作**：支持 Shift+点击 一次放入多个染料
- **进度条可视化**：虹彩和阴阳比例用彩色进度条显示
- **属性即时计算**：放入材料后，战斗属性预览区实时更新

### 17.2.2 魔法书属性面板

**打开方式**：手持魔法书按特定快捷键（默认 `G`）或右键打开

**界面布局：**
```
┌──────────────────────────────────────────────┐
│  魔虹术书                                     │
├──────────────────────────────────────────────┤
│  当前等级: 42.5    颜色: #RRGGBB              │
│                                              │
│  已注入术纸: 5/12                             │
│  ┌──────────────────────────────────────┐    │
│  │ 1. #FF4422  Lv.8  [属性条]   [移除]  │    │
│  │ 2. #22FF44  Lv.7  [属性条]   [移除]  │    │
│  │ 3. #4422FF  Lv.9  [属性条]   [移除]  │    │
│  │ 4. #FFAA00  Lv.6  [属性条]   [移除]  │    │
│  │ 5. #AA00FF  Lv.5  [属性条]   [移除]  │    │
│  │ ...                                  │    │
│  └──────────────────────────────────────┘    │
│                                              │
│  聚合属性:                                    │
│  虹彩: ▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍                  │
│  阴阳: ▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍                  │
│                                              │
│  战斗属性:                                    │
│  消耗: 0.83  攻击: +3.5  生命: +6.2           │
│  暴击率: +18%  暴击伤害: +3%                  │
└──────────────────────────────────────────────┘
```

**交互改进：**
- 可单独移除某张术纸（消耗术晶或染料作为代价）
- 可调整术纸顺序（影响聚合属性计算）
- 可重命名魔法书（铁砧式命名）

### 17.2.3 染料配方手册 GUI

**目的**：帮助玩家了解染料获取途径

**界面布局：**
```
┌──────────────────────────────────────────────┐
│  色彩图鉴                                     │
├────────┬─────────────────────────────────────┤
│        │                                     │
│  17色  │   选中染料详情:                       │
│  色板  │   名称: 虹染料                        │
│  列表  │   色值: (彩虹渐变)                     │
│        │   配方: 赤+橙+黄+绿+青+蓝+紫           │
│        │   来源: 合成                          │
│        │                                     │
│        │   [属性预览]                          │
│        │   朱赤: 3  碧青: 5  苍蓝: 4           │
│        │   阴: 3    阳: 7                     │
│        │                                     │
└────────┴─────────────────────────────────────┘
```

## 17.3 技术实现要点

### 17.3.1 Menu + Screen 分离

NeoForge GUI 采用 MVC 模式：
- `AbstractContainerMenu`（服务端）：处理数据同步、物品槽逻辑
- `Screen`（客户端）：渲染 UI、处理用户输入

```kotlin
// 服务端
class MagicTableMenu(windowId: Int, playerInv: Inventory, pos: BlockPos) 
    : AbstractContainerMenu(ModMenuTypes.MAGIC_TABLE.get(), windowId) {
    
    // 定义槽位：输入槽、输出槽、玩家背包
    // 使用 ContainerData 同步颜色和属性数据
    private val colorData = object : ContainerData {
        override fun get(index: Int): Int = ...  // 颜色 RGB 分量
        override fun set(index: Int, value: Int) { ... }
        override fun getCount() = 5  // r, g, b, level, attrCount
    }
    
    init {
        addDataSlots(colorData)
    }
}

// 客户端
class MagicTableScreen(menu: MagicTableMenu, playerInv: Inventory, title: Component) 
    : AbstractContainerScreen<MagicTableMenu>(menu, playerInv, title) {
    
    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        // 绘制颜色预览区
        val color = menu.getCurrentColor()
        guiGraphics.fill(previewX, previewY, previewX + 48, previewY + 48, color)
    }
}
```

### 17.3.2 网络同步

颜色和属性数据通过 `ContainerData` 同步，无需额外网络包。复杂的 GUI 操作（如撤销、批量注入）使用自定义 Payload。

---

# 十八、玩法改进与创新建议

## 18.1 玩法改进点

### 改进 1：颜色记忆与调色板

**现状**：每次染色后颜色固定，无法回溯或调整

**改进**：魔虹术台增加"颜色记忆"功能
- 记录最近 5 次染色结果
- 支持从历史记录中选择颜色重新应用
- 增加"撤销"按钮（消耗额外术晶作为代价）

### 改进 2：术纸分品质

**现状**：所有术纸除了颜色和等级外无区别

**改进**：引入术纸品质系统
- 普通术纸（怪物掉落）：基础品质
- 精炼术纸（术台合成）：可通过术晶洗练提升品质
- 辉光术纸（稀有掉落/合成）：自带额外属性加成
- 品质影响可注入的术纸数量上限（普通 12 张，精炼 15 张，辉光 20 张）

### 改进 3：颜色环境交互

**现状**：颜色属性仅影响战斗数值，与 Minecraft 世界无互动

**改进**：颜色属性产生环境效果
- 高亮度（阳）的魔法书可以当便携光源使用（动态光照）
- 高红色值可点燃前方方块
- 高蓝色值可短暂冻结水面
- 高绿色值可催熟前方作物

### 改进 4：颜色克制系统

**现状**：所有伤害为统一类型

**改进**：引入颜色相克
- 红克绿、绿克蓝、蓝克红（RGB 三原色克制链）
- 攻击克制颜色时伤害 +30%
- 被克制颜色攻击时伤害 -30%
- 在 tooltip 中显示"克制"关系

### 改进 5：魔法书等级成长

**现状**：魔法书等级完全依赖术晶注入

**改进**：增加经验值获取途径
- 使用魔法书击杀怪物获得经验
- 经验自动提升第一张术纸的等级
- 术纸"满级"（如 Lv.50）后解锁特殊效果
- 增加魔法书经验条可视化

## 18.2 创新性玩法建议

### 创新 1：色彩谜题维度 —— "色域"

**核心理念**：利用颜色混合和属性计算的机制，创建一个以颜色为谜题核心的 pocket dimension。

**具体设计：**
- 通过魔虹术台 + 特殊材料（如所有 17 种染料各一个）打开通往"色域"的传送门
- 色域中的谜题基于颜色混合：
  - 需要精确调配特定颜色来打开机关门
  - 颜色光线折射/反射谜题
  - 颜色滤镜块——透过不同颜色的方块看世界，不同颜色的物品/方块才可见
- 色域中的怪物对特定颜色免疫或脆弱
- 色域奖励：独有颜色配方、特殊术纸、颜色装饰方块

### 创新 2：动态调色战斗系统

**核心理念**：将颜色混合从"准备工作"变为"实时战斗技巧"。

**具体设计：**
- 魔法书增加"调色盘"模式——按住特定键进入
- 在调色盘中实时调整 RGB 三色滑块来改变当前攻击属性
- 战斗中根据敌人弱点快速调配颜色：
  - 面对高护甲敌人 → 调高红色（破甲）
  - 面对快速敌人 → 调高蓝色（减速）
  - 自身血量低 → 调高绿色（吸血）
- 调色需要消耗"色彩能量"（随时间恢复或通过击杀怪物获取）
- 高手可以快速切换颜色实现连招

### 创新 3：色彩生态群落

**核心理念**：颜色影响力延伸到世界本身。

**具体设计：**
- 长期在某个区域大量使用特定颜色魔法，会逐渐改变该区域的生态特征
- 红色区域：植物变红，被动生成火焰粒子，岩浆池概率增加
- 绿色区域：作物生长加速，被动生成叶子粒子，树苗自动种植
- 蓝色区域：水体变蓝，被动生成气泡粒子，雨天概率增加
- 白色（高亮度）区域：永久白昼效果，被动生成光粒子
- 黑色（高暗度）区域：永久黑夜效果，被动生成烟雾粒子
- 颜色影响范围随使用频率逐渐扩大
- 不同颜色区域交界处产生"色彩边界"——特殊生态过渡带

### 创新 4：色彩符文刻印

**核心理念**：将颜色属性"刻印"到世界中，产生持久效果。

**具体设计：**
- 魔法书新增"刻印"模式（潜行+右键方块）
- 消耗一张术纸，将颜色属性刻印到方块上，形成"色彩符文"
- 符文效果：
  - 红色符文（刻在地上）：范围内怪物受到持续伤害
  - 绿色符文：范围内植物快速生长
  - 蓝色符文：范围内实体减速
  - 高亮度符文：发光效果
  - 高暗度符文：怪物无法在范围内生成（类似和平模式）
- 符文持续时间与术纸等级成正比
- 符文之间可以"连接"形成更大的符文阵
- 符文阵中心产生复合效果

### 创新 5：色彩炼金术

**核心理念**：将颜色属性与药水酿造系统结合。

**具体设计：**
- 魔虹术纸可以作为"炼金催化剂"加入酿造台
- 术纸的颜色属性决定药水的额外效果：
  - 红色术纸 → 力量药水获得额外破甲效果
  - 绿色术纸 → 生命恢复药水获得额外生命上限
  - 蓝色术纸 → 速度药水获得额外跳跃提升
  - 高亮度术纸 → 夜视药水获得额外发光效果
  - 高暗度术纸 → 隐身药水获得额外伤害吸收
- 术纸等级影响附加效果的强度
- 可以制作"纯色精华"——将术纸蒸馏成液体，直接作为药水原料

### 创新 6：色彩同步多人协作

**核心理念**：利用颜色属性在多人游戏中创造合作机制。

**具体设计：**
- 两名玩家同时使用魔法书攻击同一目标 → 触发"色彩共鸣"
- 共鸣效果取决于两种颜色的关系：
  - 互补色（如红+青）→ 伤害 +50%，产生爆炸
  - 相邻色（如红+橙）→ 回复双方生命
  - 同色 → 目标获得"色盲"debuff（随机负面效果）
  - 三原色齐全（红+绿+蓝）→ 目标获得"色彩分解"（极大伤害 + 粒子爆炸）
- 增加"色彩信标"方块：放置后，范围内所有持有魔法书的玩家共享颜色属性加成
- 在 colorist 多人服务器中创造独特的团队策略玩法

---

---

> **文档生成时间**：2026-07-18  
> **分析范围**：全部源代码、配置文件、资源文件、数据包、Git 历史  
> **覆盖率**：100% 文件均被读取分析  
> **新增章节**：移植分析、前置库选型、GUI 方案、玩法改进与创新

---

---

# 附录 C：资源链接汇总

## C.1 原始项目

| 资源 | 链接 |
|------|------|
| Colorist 原始仓库 | [https://github.com/1026073226/colorist](https://github.com/1026073226/colorist) |
| KubeJS 官方文档 | [https://kubejs.com/](https://kubejs.com/) |
| ProbeJS 文档 | [https://github.com/Prunoideae/ProbeJS](https://github.com/Prunoideae/ProbeJS) |

## C.2 NeoForge 1.21.1 开发文档

| 资源 | 链接 | 说明 |
|------|------|------|
| **NeoForge 官方文档（1.21.1）** | [https://docs.neoforged.net/docs/1.21.1/gettingstarted/](https://docs.neoforged.net/docs/1.21.1/gettingstarted/) | 入门指南，环境搭建，Gradle 配置 |
| **NeoForge 事件系统文档** | [https://docs.neoforged.net/docs/1.21.1/concepts/events/](https://docs.neoforged.net/docs/1.21.1/concepts/events/) | `@SubscribeEvent`、`@EventBusSubscriber`、事件总线详解 |
| **NeoForge Mod 文件配置** | [https://docs.neoforged.net/docs/1.21.1/gettingstarted/modfiles/](https://docs.neoforged.net/docs/1.21.1/gettingstarted/modfiles/) | `gradle.properties`、`neoforge.mods.toml` 完整配置参考 |
| **NeoForge 注册系统** | [https://docs.neoforged.net/docs/1.21.1/concepts/registries/](https://docs.neoforged.net/docs/1.21.1/concepts/registries/) | `DeferredRegister` 用法详解 |
| **NeoForge 网络通信** | [https://docs.neoforged.net/docs/1.21.1/networking/](https://docs.neoforged.net/docs/1.21.1/networking/) | `CustomPacketPayload`、`StreamCodec`、Payload 注册 |
| **NeoForge 方块实体** | [https://docs.neoforged.net/docs/1.21.1/blockentities/](https://docs.neoforged.net/docs/1.21.1/blockentities/) | BlockEntity 创建、数据同步、GUI 绑定 |
| **NeoForge 数据生成** | [https://docs.neoforged.net/docs/1.21.1/resources/](https://docs.neoforged.net/docs/1.21.1/resources/) | 资源包、数据包、配方 JSON、标签 |
| **NeoForge 掉落修改器** | [https://docs.neoforged.net/docs/1.21.1/resources/server/glm/](https://docs.neoforged.net/docs/1.21.1/resources/server/glm/) | `GlobalLootModifier` 系统 |
| **NeoForge 世界生成** | [https://docs.neoforged.net/docs/1.21.1/resources/server/configuredfeatures/](https://docs.neoforged.net/docs/1.21.1/resources/server/configuredfeatures/) | ConfiguredFeature、PlacedFeature、BiomeModifier |
| **NeoForge 版本列表** | [https://projects.neoforged.net/neoforged/neoforge](https://projects.neoforged.net/neoforged/neoforge) | 所有 NeoForge 版本发布页 |
| **ModDevGradle 插件文档** | [https://projects.neoforged.net/neoforged/ModDevGradle](https://projects.neoforged.net/neoforged/ModDevGradle) | 推荐使用的 Gradle 构建插件 |
| **NeoForge MDK 模板（1.21.1）** | [https://github.com/NeoForgeMDKs/MDK-1.21.1-NeoGradle](https://github.com/NeoForgeMDKs/MDK-1.21.1-NeoGradle) | 项目启动模板 |
| **NeoForge MDK 模板（ModDevGradle）** | [https://github.com/NeoForgeMDKs/MDK-1.21-ModDevGradle](https://github.com/NeoForgeMDKs/MDK-1.21-ModDevGradle) | 新版推荐模板 |
| **NeoForge 社区 Discord** | [https://discord.neoforged.net/](https://discord.neoforged.net/) | 开发者社区，问题求助 |

## C.3 前置模组

| 前置 | 链接 | 说明 |
|------|------|------|
| **Kotlin for Forge（GitHub）** | [https://github.com/thedarkcolour/KotlinForForge](https://github.com/thedarkcolour/KotlinForForge) | 源码仓库，含 README 和 Wiki |
| **Kotlin for Forge（Modrinth）** | [https://modrinth.com/mod/kotlin-for-forge](https://modrinth.com/mod/kotlin-for-forge) | 下载页 |
| **Kotlin for Forge（CurseForge）** | [https://www.curseforge.com/minecraft/mc-mods/kotlin-for-forge](https://www.curseforge.com/minecraft/mc-mods/kotlin-for-forge) | 备用下载 |
| **Kotlin 语言中文教程** | [https://www.cnblogs.com/srcres258/p/18288117](https://www.cnblogs.com/srcres258/p/18288117) | 使用 Kotlin 开发 NeoForge 模组的完整中文教程 |
| **LibGuiFoxified（GitHub）** | [https://github.com/ThinkingStudios/LibGuiFoxified](https://github.com/ThinkingStudios/LibGuiFoxified) | GUI 框架源码 |
| **LibGuiFoxified（Modrinth）** | [https://modrinth.com/mod/libguifoxified](https://modrinth.com/mod/libguifoxified) | 下载 + Maven 坐标 |
| **LibGuiFoxified（CurseForge）** | [https://www.curseforge.com/minecraft/mc-mods/libguifoxified](https://www.curseforge.com/minecraft/mc-mods/libguifoxified) | 备用下载 |
| **LibGui 原版 Wiki** | [https://github.com/CottonMC/LibGui/wiki](https://github.com/CottonMC/LibGui/wiki) | LibGuiFoxified 的 API 文档（Fabric 版但 API 通用） |
| **Resourceful Lib** | [https://github.com/Team-Resourceful/ResourcefulLib](https://github.com/Team-Resourceful/ResourcefulLib) | 通用工具库 |
| **SuperMartijn642's Config Lib** | [https://github.com/SuperMartijn642/SuperMartijn642sConfigLib](https://github.com/SuperMartijn642/SuperMartijn642sConfigLib) | 配置 GUI 框架 |
| **Architectury API** | [https://github.com/architectury/architectury-api](https://github.com/architectury/architectury-api) | 跨平台抽象层（如需双平台） |
| **GeckoLib** | [https://github.com/bernie-g/geckolib](https://github.com/bernie-g/geckolib) | 实体/方块动画（未来扩展） |

## C.4 AI 辅助 Minecraft 模组开发

| 资源 | 链接 | 说明 |
|------|------|------|
| **Claude 官方 Prompt Engineering 指南** | [https://docs.anthropic.com/en/docs/build-with-claude/prompt-engineering/overview](https://docs.anthropic.com/en/docs/build-with-claude/prompt-engineering/overview) | Anthropic 官方提示词工程最佳实践 |
| **Claude Prompt Engineering 完整教程** | [https://www.artificial-intelligence-wiki.com/ai-tools/claude/claude-prompt-engineering/](https://www.artificial-intelligence-wiki.com/ai-tools/claude/claude-prompt-engineering/) | 涵盖 XML 标签、思维链、Few-shot 等高级技巧 |
| **MCP Server 开发指南（Claude 集成）** | [https://modelcontextprotocol.io/docs](https://modelcontextprotocol.io/docs) | MCP 协议官方文档，构建 AI 编程助手的扩展能力 |
| **DeveloperMCP（Minecraft + AI 控制）** | [https://github.com/center2055/MinecraftDeveloperMCP](https://github.com/center2055/MinecraftDeveloperMCP) | 让 AI 直接控制 Minecraft 服务端的 MCP 插件 |
| **Mod SDK MCP Server（网易中国版）** | [https://github.com/MCNeteaseDevs/modsdk_mcp_server](https://github.com/MCNeteaseDevs/modsdk_mcp_server) | 中国版 Mod SDK 的 MCP Server，支持文档检索和代码生成 |
| **Context7 平台（Mod SDK 文档）** | [https://context7.com/mcneteasedevs/mc-netease-sdk](https://context7.com/mcneteasedevs/mc-netease-sdk) | 58 万+ tokens 的 Mod SDK 文档，AI 自动检索 |
| **AI 辅助 Minecraft Mod 开发指南** | [https://www.arsturn.com/blog/build-an-mcp-server-with-ai-code-the-real-2025-guide](https://www.arsturn.com/blog/build-an-mcp-server-with-ai-code-the-real-2025-guide) | 使用 AI（Claude）编写 Minecraft Mod 的实战指南 |
| **Mod 开发 AI Prompt 模板** | [https://docsbot.ai/prompts/programming/java-to-bedrock-mod-porting](https://docsbot.ai/prompts/programming/java-to-bedrock-mod-porting) | Java Edition → Bedrock Edition 移植的 AI Prompt 模板 |
| **Cursor IDE 官方文档** | [https://docs.cursor.com/](https://docs.cursor.com/) | 推荐用于 AI 辅助开发的 IDE |

## C.5 推荐开发工具链

| 工具 | 链接 | 用途 |
|------|------|------|
| **IntelliJ IDEA** | [https://www.jetbrains.com/idea/](https://www.jetbrains.com/idea/) | 推荐 IDE（NeoForge 官方支持） |
| **Eclipse Temurin JDK 21** | [https://adoptium.net/temurin/releases/?version=21](https://adoptium.net/temurin/releases/?version=21) | 推荐的 JDK 发行版 |
| **Microsoft OpenJDK 21** | [https://learn.microsoft.com/en-us/java/openjdk/download#openjdk-21](https://learn.microsoft.com/en-us/java/openjdk/download#openjdk-21) | NeoForge 官方推荐的 JDK |
| **Minecraft Development (IntelliJ 插件)** | [https://plugins.jetbrains.com/plugin/8327-minecraft-development](https://plugins.jetbrains.com/plugin/8327-minecraft-development) | 提供模组项目模板、代码补全、资源文件编辑 |
| **GitHub** | [https://github.com/](https://github.com/) | 版本控制（NeoForge 强烈推荐） |

## C.6 社区与分发平台

| 资源 | 链接 | 说明 |
|------|------|------|
| **Modrinth** | [https://modrinth.com/](https://modrinth.com/) | 开源模组分发平台，支持 Maven 仓库 |
| **CurseForge** | [https://www.curseforge.com/minecraft](https://www.curseforge.com/minecraft) | 最大模组分发平台 |
| **NeoForge Discord** | [https://discord.neoforged.net/](https://discord.neoforged.net/) | 开发者社区，实时问题求助 |
| **Kotlin for Forge Discord** | （见 KFF GitHub README） | KFF 专属支持频道 |
| **MCBBS（中文社区）** | [https://www.mcbbs.net/](https://www.mcbbs.net/) | 中文 Minecraft 模组讨论社区 |

## C.7 推荐学习路径

1. **环境搭建**：使用 [MDK-1.21.1-NeoGradle](https://github.com/NeoForgeMDKs/MDK-1.21.1-NeoGradle) 模板创建项目，配置 [Kotlin for Forge](https://github.com/thedarkcolour/KotlinForForge) 依赖
2. **基础教程**：阅读 [NeoForge 官方文档](https://docs.neoforged.net/docs/1.21.1/gettingstarted/) 入门
3. **Kotlin 适配**：参考 [srcres258 的中文教程](https://www.cnblogs.com/srcres258/p/18288117)
4. **GUI 开发**：学习 [LibGui Wiki](https://github.com/CottonMC/LibGui/wiki) 的声明式 GUI 系统
5. **AI 辅助**：使用 [Cursor IDE](https://docs.cursor.com/) + [Claude](https://docs.anthropic.com/en/docs/build-with-claude/prompt-engineering/overview) 进行代码生成
6. **社区求助**：加入 [NeoForge Discord](https://discord.neoforged.net/) 获取实时帮助
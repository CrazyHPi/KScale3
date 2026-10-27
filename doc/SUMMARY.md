# HUAWEI Scale 3 / Scale 3 Pro 蓝牙驱动 — 项目总结

> 本文档是对 [TECH.md](TECH.md)（逆向协议文档）与 [scale3.py](scale3.py)（Python 参考实现）的中文整理与摘要。
> 项目目标：在不使用华为官方 App 的情况下，通过 BLE 直接驱动用户自有的 HUAWEI Scale 3 / 3 Pro（型号 HEM-B19 / HaigeBLE / M00F7），为 openScale 等开源项目提供互操作支持。

---

## 1. 两个文件的角色

| 文件 | 角色 | 内容 |
| :--- | :--- | :--- |
| [TECH.md](TECH.md) | 协议规格（逆向文档） | 加密体系、帧格式、GATT 表、状态码、两种工作模式的完整时序、报文字段定义 |
| [scale3.py](scale3.py) | 参考实现（Python） | 基于 `bleak` + `cryptography` 的独立可运行驱动，863 行，无厂商账号依赖 |

`scale3.py` 是 TECH.md 的忠实落地，两者的算法、常量、报文结构完全一致。

---

## 2. 核心加密体系（TECH.md §2 / scale3.py L87-L124）

- **静态常量**：`CAK`、`WB1033`、`WB2033`（各 16 字节）。
- **RootKey 派生**（`derive_root_key`，L112）：由秤的**真实物理 MAC** 确定性派生
  `SHA256( (SHA256((WB1033<<4)^WB2033)>>6) ^ (MAC去冒号+"0000") )` 取前 16 字节。
  ⚠️ macOS CoreBluetooth 只暴露随机 UUID，必须使用秤身标签上的真实 MAC，否则握手"看似成功"但 BIA 数据解密为乱码。
- **双向认证 token**（`generate_auth_tokens`，L120）：双重 HMAC-SHA256，对 `randAB = randA‖randB`：
  - `tokenB = HMAC(HMAC(CAK+"1123", randAB), randAB)`（客户端 → 秤）
  - `tokenA = HMAC(HMAC(CAK+"9856", randAB), randAB)`（秤 → 客户端）
  - **key/data 顺序不可颠倒**；tokenB 错误会表现为 0x31 持续返回 Code 2。
- **载荷加密**：AES-128-CTR，格式 `[16B 随机 IV] + 密文`，会话 WorkKey 由客户端每次随机生成，用 RootKey 加密后经 0x29 下发。
- **握手关键澄清**：0x29 返回 Code 0 仅表示 WorkKey 安装成功，**不代表认证通过**；认证的真正凭据是 tokenB。

## 3. 帧协议（TECH.md §3 / scale3.py L127-L168）

```
+--------+--------+------------------+-----------------+-----------------+
| Magic  | Length | Frame Sequence   | Payload 0..15B  | CRC-16 (LE)     |
+--------+--------+------------------+-----------------+-----------------+
```

- Magic：`0xDB` 明文上行 · `0xDC` 加密上行 · `0xBD` 明文下行 · `0xCD` 加密下行。
- Length = 载荷长 + 3；Sequence = `((总帧数-1)<<4) | 帧索引`；载荷按 15 字节分片。
- CRC-16 CCITT（256 项查表法，CRC_TABLE 见 scale3.py L92）。
- 实现对应：`build_framed_chunks`（分片打包+校验）、`FrameReassembler`（重组）。

## 4. GATT 特征一览（TECH.md §4，scale3.py L60-L79）

**必须按 UUID 寻址**（macOS 会重排 handle）：

| 特征 | 简称 | 用途 | 加密 |
| :--- | :--- | :--- | :--- |
| `02b2a08e-…eee679` | 0x21 | 认证请求 / randA | 明文 |
| `32330a04-…7525c9` | 0x25 | randB + tokenB 交换 | 明文 |
| `a3d330f8-…3b597a` | 0x29 | 会话 WorkKey 下发 | AES(RootKey) |
| `4338c65e-…ca6b54` | 0x45 | 绑定武装（写 0x01） | 明文 |
| `42596cbe-…1c9174` | 0x2D | HUID 写闪存 / 皮重称量 | AES(WorkKey) |
| `8cc61d7d-…63592e` | 0x31 | 用户档案 & 提交 | AES(WorkKey) |
| `00002a2b-…5f9b34fb` | 0x52 | 时间同步（标准 GATT） | 明文 |
| `11872f15-…84f3425` | 0x5A | 序列号查询 / **单位设置** | 明文 |
| `7e6dbc73-…d7834695` | — | **单位查询**（实测 handle 0x4B） | 明文 |
| `bfc36f6e-…e52962e` | 0x62 | 设备配置状态（恒回 1） | 明文 |
| `426f058d-…a08bf` | 0x7A | 硬件型号（如 M00F7） | 明文 |
| `0212f42a-…cc71a4` | 0x8F | 离线历史记录 | 明文 |
| `46797c17-…472878` | 0x97 | 实时 BIA 流（38B） | AES(WorkKey) |
| `0000fe01-…5f9b34fb` | 0xD7 | 能力上报（无响应写） | 明文 |
| `0000fe02-…5f9b34fb` | 0xDA | 能力响应 | 明文 |
| `ba216311-…e62293e` | Sentinel | 状态/事件通知 | 明文 |

> 单位设置/查询为 2026-10-28 官方 App HCI 抓包实测；handle 依协议栈重排，必须按 UUID 寻址。

通用查询/武装命令固定为 `db0300c140`（单帧空载荷）；能力上报为 `5a000500013701001ca9`。

## 5. 状态码（TECH.md §5）

短回复 `bd 04 00 XX …` 中的 `XX`：

| Code | 含义 | 出现场景 |
| :--- | :--- | :--- |
| 0 | 成功 / 用户已识别 | 0x29、0x52、0x31（已知用户，或新用户完成验证称重后） |
| 1 | 已接受但不完整：档案收到但**尚无验证称重** | 配对时 0x2D 与 0x31 之间没人上秤；0x62 的恒定回复 |
| 2 | 拒绝：认证失败（tokenB 错）或未知用户 | 0x31 |

> 经验数据，来自单台 Scale 3 Pro，按"工作模型"而非正式规格对待。

## 6. 两种工作模式（TECH.md §6）

**公共握手**（两种模式相同）：
订阅 Sentinel + FE02 → 0x21 取 randA → 0x25 发 randB+tokenB（收 tokenA）→ 0x29 发加密 WorkKey → 0xD7 上报能力。

**Mode 1 — 首次配对 / 写闪存**（新秤或 `--reconfig`）：
1. 0x45 写 0x01 武装绑定
2. 0x2D 写 AES(WorkKey, HUID[30B])
3. **用户上秤做验证称重**（赤脚+握把手柄），秤回传 `[status=0][皮重 uint16_le/100]` —— 这是后续 0x31 返回 Code 0 的前提
4. 0x52 时间同步；0x5A/0x7A/0x62 硬件查询
5. 0x31 下发用户档案（type=0，体重 = 第 3 步的皮重）→ Code 0
6. 0x97 武装 BIA 流，接收并解密 38B 测量帧
7. 0x31 提交（type=2）；本地持久化 HUID

⚠️ 第 3 步的验证称重不是被动的：0x2D→0x31 之间没人上秤则 0x31 返回 Code 1。原始 App 流量中**没有**"0x45 写 0x00 解除绑定"的步骤，不要自行添加。

**Mode 2 — 日常称重**（HUID 已写入）：
0x52 时间同步 → 0x31 档案（type=0，Code 0 识别用户）→ 0x7A/0x8F 查询 → 0x31 档案刷新 → 0x97 武装 BIA 流 → 用户上秤 → 收 38B → 0x31 提交（type=2）。约 25 秒完成。

## 7. 报文定义（TECH.md §7）

### 7.1 用户档案（0x31，明文 69 字节 → `build_user_info_profile`，scale3.py L171）

| 偏移 | 长 | 格式 | 字段 |
| :-- | :- | :--- | :--- |
| 0 | 30 | ASCII 补 `\x00` | HUID |
| 30 | 32 | ASCII 补 `\x00` | UID（type=0 时置零） |
| 62 | 1 | uint8 | 性别（1=男 0=女） |
| 63 | 1 | uint8 | 年龄 |
| 64 | 2 | uint16_le | 身高（cm） |
| 66 | 2 | uint16_le | 体重 ×100（kg，配对时用 0x2D 皮重） |
| 68 | 1 | uint8 | 类型：0=档案/测量，2=测后提交 |

### 7.2 实时 BIA 流（0x97，解密后 38 字节 → `on_meas_rx`，scale3.py L630）

| 偏移 | 格式 | 单位 | 字段 |
| :-- | :--- | :-- | :--- |
| 0..2 | uint16_le | /100 kg | 总体重 |
| 2..4 | uint16_le | /10 % | 体脂率 |
| 4..11 | 年月日时分秒+星期 | — | 时间戳 |
| 12..24 | 6 × uint16_le | 原始值 | 脚部 6 路生物阻抗 |
| 24..26 | uint16_le | BPM | 心率 |
| 26..38 | 6 × uint16_le | 原始值 | 手部 6 路生物阻抗 |

> 阻抗为原始计数；换算成 Ω 或派生指标（肌肉量、水分等）需要厂商 BIA 模型，超出本项目范围。

### 7.3 体重单位设置 / 查询（0x5A + 7e6dbc73，2026-10-28 实测）

官方 App 可把秤显示单位切为 斤 / 公斤 / 磅。单位代码：

| Code | 单位 |
| :-- | :-- |
| 1 | 公斤（kg） |
| 2 | 磅（lb） |
| 3 | 斤（jin） |

**设置单位**：向 `11872f15-…84f3425`（0x5A）写 ATT Write Command（无响应写），帧格式 `DB 04 00 <unit> <CRC16_LE>`：

| 单位 | 完整帧 |
| :-- | :--- |
| 公斤 | `DB 04 00 01 20 D9` |
| 磅 | `DB 04 00 02 70 80` |
| 斤 | `DB 04 00 03 40 B7` |

秤回 Indication `BD 04 00 00 65 89`（status 0）。

**查询单位**：向 `7e6dbc73-…d7834695` 写通用查询 `DB 03 00 C1 40`（同样是 Write Command），秤回：

| 单位 | 回包 |
| :-- | :--- |
| 公斤 | `BD 04 00 01 A4 49` |
| 磅 | `BD 04 00 02 E4 48` |
| 斤 | `BD 04 00 03 25 88` |

CRC 规则同 §3：上行 XMODEM（CRC 字段预置 0），下行 MODBUS，均小端。
`DB 04 00 01 20 D9` 就是 §3 里那个 CRC 黄金向量，实际含义是“设为公斤”。

## 8. scale3.py 实现要点

**运行流程**（`main`，L731）：
- 无 `scale_config.json`（或带 `--reconfig`）→ 交互式初始化（MAC/性别/年龄/身高，HUID 自动生成，体重由配对称重自动捕获）→ Mode 1 → 保存配置退出。
- 有配置 → Mode 2 日常 BIA 称重 → 展示结果（体重、体脂、心率、8 电极阻抗）→ 更新 `last_weight` 退出。

**命令行选项**：`--debug/-d`（完整十六进制帧调试输出）· `--reconfig`（强制重新配对）。

**代码结构**：
- 常量与工具：GATT UUID 表（L60）、加密常量（L87）、CRC 表（L92）、`derive_root_key`（L112）、`generate_auth_tokens`（L120）、`build_framed_chunks`（L127）、`FrameReassembler`（L148）、`build_user_info_profile`（L171）、`safe_write`（写失败自动翻转 response 标志重试，L186）。
- `Scale3Driver` 类（L272）：8 个步骤顺序执行 —— ①认证握手（0x21/0x25/0x29）→ ②能力上报（0xD7）→ ③时间同步（0x52）→ ④用户档案（0x31）→ ⑤硬件查询（0x7A/0x8F）→ ⑥档案刷新（0x31）→ ⑦BIA 测量（0x97，最长等待 120s）→ ⑧测后提交（0x31 type=2）。
- Mode 1 额外步骤嵌入其中：0x45 武装、0x2D HUID 写入+皮重捕获（25s 超时）、0x5A/0x7A/0x62 查询、0x45 解除（实现中发了 `db0300c140`；TECH.md 指出原始 App 无此步骤）。
- 异步事件驱动：`asyncio.Event` 同步各 notify 回调（auth_req / work_key / bind / reg / user_info / measure_done）。
- 单位控制：`build_weight_unit_frame` / `parse_weight_unit_reply` / `set_weight_unit` / `get_weight_unit`（文件末尾，2026-10-28 官方 App 抓包补充）。
- 日志：全部帧记录到 `scale3_live.log`，`--debug` 时同时打印到终端。

**配置文件 `scale_config.json`** 字段：`mac`、`huid`、`sex`、`age`、`height`、`last_weight`。
HUID 生成规则：`"300330000"` + 8 位随机数字（`generate_random_huid`，L196）。

## 9. 实现与文档的差异 / 注意事项

1. **0x45 解除步骤**：scale3.py 在 Mode 1 的 0x31 之后向 0x45 写了 `db0300c140`（L587-L591），而 TECH.md 明确说原始 App 流量中**没有**解除绑定步骤（§6 Mode 1 注）。移植时建议以 TECH.md 为准或保持观察。
2. **0x97 解密判断**：实现中仅当帧 magic 为 `0xCD` 才解密（L635），TECH.md 表述为固定 AES(WorkKey) 加密。
3. **安全提醒**（TECH.md §8）：不要提交真实 MAC/HUID 或原始 `--debug` 日志（明文包含身份信息）；所有标识符均为占位符。
4. **适用范围**：结论基于单台 Scale 3 Pro（macOS 环境）；Scale 3 与 3 Pro、不同固件版本可能存在差异。
5. **技术栈依赖**：Python 3 + `bleak`（BLE）+ `cryptography`（AES-CTR/HMAC）。
6. **单位设置/查询**：旧版 GATT 表只把 0x5A 标为序列号，实测官方 App 也用它设置单位；单位查询走 `7e6dbc73-42e7-45b9-a6ec-6aa2d7834695`（旧表未列）。详见 §7.3。


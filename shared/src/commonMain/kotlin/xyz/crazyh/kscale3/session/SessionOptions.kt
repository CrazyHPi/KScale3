package xyz.crazyh.kscale3.session

import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import xyz.crazyh.kscale3.protocol.packets.TimeSyncPacket
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 会话时序（PLAN「双模式状态机」超时参考值；测试可用 `runTest` 虚拟时间，无需改小）。
 *
 * ⚠️「帧间写入 20ms」只作用于 0x25 与 0x29（PLAN 差异补记 5）；0x2D/0x31 多帧连续写。
 * 订阅常驻会话期间，参考实现的"订阅后延时三档"合并为会话开头统一静置 [settle]。
 */
data class SessionTiming(
    /** 连接后/首个写入前静置（合并 Python 订阅后 0.1/0.15/0.2s 三档）。 */
    val settle: Duration = 200.milliseconds,
    /** 0x25/0x29 多帧写入的帧间间隔（其余多帧写连续发）。 */
    val interFrameWrite: Duration = 20.milliseconds,
    val randA: Duration = 3.seconds,
    /** tokenA 等待（参考实现不校验也不等；KMP 校验后需要一个上限）。 */
    val tokenA: Duration = 3.seconds,
    val workKey: Duration = 2.seconds,
    /** 0x45 武装确认（软：超时继续）。 */
    val bindArm: Duration = 2.seconds,
    /** 0x2D 验证称重皮重回复（Mode 1 硬超时）。 */
    val tare: Duration = 25.seconds,
    /** 0x52 时间同步确认（软：超时继续，同 Python）。 */
    val timeSync: Duration = 2.seconds,
    /** 0x5A/0x7A/0x62/0x8F 查询回复（软：超时继续）。 */
    val query: Duration = 500.milliseconds,
    /** 体重单位设置/查询回复（TECH.md §7.3；同参考实现 2s）。 */
    val unit: Duration = 2.seconds,
    /** 0x31 首发回复（硬：Code 是归属确认）。 */
    val profileFirst: Duration = 5.seconds,
    /** 0x31 刷新回复（软：超时继续，同 Python）。 */
    val profileRefresh: Duration = 2.seconds,
    /** 0x97 BIA 流等待。 */
    val bia: Duration = 120.seconds,
    /** 0x31 提交后的收尾静置（同 Python 的 0.5s；提交不等回复）。 */
    val commitSettle: Duration = 500.milliseconds,
)

/**
 * 会话选项（调试开关默认关，PLAN 风险项 3/4/7）。
 *
 * @param verifyTokenA 校验 0x25 回的 tokenA（KMP 新增，PLAN 差异补记 1；若真机行为不符可关掉观察）。
 * @param sendUnbindStep Mode 1 在 0x31 首发后向 0x45 写 `db0300c140`"解绑"（参考实现无条件发；
 *   线上实测无此步骤 → **默认关**，A/B 实测用，PLAN 差异补记 8）。
 * @param acceptFirstBiaFrame true=收首个合理 38B 即完成（同参考实现）；false=静默窗口 [biaQuietWindow]
 *   内取最后一帧（"等末帧"调试开关，防固件先发未稳定帧）。
 * @param rejectImplausibleBia 用 [xyz.crazyh.kscale3.protocol.packets.BiaMeasurement.isPlausible] 过滤
 *   乱码帧（MAC 错 → RootKey 错 → BIA 解密乱码的兜底）。
 * @param biaQuietWindow acceptFirstBiaFrame=false 时的静默窗口。
 * @param timeProvider 0x52 时间源（测试注入固定值；默认本地 naive 时间）。
 * @param onLog 帧日志回调（SettingsScreen 调试帧日志屏；日志只含帧 hex——HUID/载荷均加密，MAC 不上线）。
 */
data class SessionOptions(
    val timing: SessionTiming = SessionTiming(),
    val verifyTokenA: Boolean = true,
    val sendUnbindStep: Boolean = false,
    val acceptFirstBiaFrame: Boolean = true,
    val rejectImplausibleBia: Boolean = true,
    val biaQuietWindow: Duration = 3.seconds,
    val timeProvider: () -> TimeSyncPacket = ::currentTimeSync,
    val onLog: ((String) -> Unit)? = null,
)

/** 本地 naive 时间 → 0x52 载荷（无时区字段，TimeSyncPacket KDoc）。 */
fun currentTimeSync(): TimeSyncPacket {
    val now = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return TimeSyncPacket(
        year = now.year,
        month = now.monthNumber,
        day = now.dayOfMonth,
        hour = now.hour,
        minute = now.minute,
        second = now.second,
        // kotlinx-datetime: Monday=1 … Sunday=7（与协议一致）
        dayOfWeek = now.dayOfWeek.isoDayNumber,
    )
}

package xyz.crazyh.kscale3.ui

import xyz.crazyh.kscale3.session.SessionError
import xyz.crazyh.kscale3.session.SessionState

/** 会话状态 → 中文引导文案（PLAN UI-3：按 SessionState 显示引导）。 */
fun sessionGuide(state: SessionState): String = when (state) {
    SessionState.Idle -> "准备就绪"
    SessionState.Connecting -> "正在连接秤…"
    SessionState.Handshaking -> "正在认证…"
    SessionState.AwaitingTare -> "请上秤站稳并握住把手（验证称重，赤脚）"
    SessionState.SyncingTime -> "正在同步时间…"
    SessionState.SendingProfile -> "正在确认用户…"
    is SessionState.ProfileResponded -> when (state.code) {
        0 -> "用户已识别"
        1 -> "缺少验证称重"
        2 -> "用户不匹配"
        else -> "设备回应码 ${state.code}"
    }

    SessionState.AwaitingMeasurement -> "请上秤站稳并握住把手，保持约 5 秒"
    SessionState.Committing -> "正在完成…"
    is SessionState.Done -> "称重完成"
    is SessionState.Paired -> "注册成功"
    is SessionState.Failed -> "失败：${errorText(state.error)}"
}

/** 会话失败 → 中文原因（PLAN UI-3 恢复动作的文案基础）。 */
fun errorText(error: SessionError): String = when (error) {
    SessionError.TokenMismatch -> "认证失败（tokenA 校验未通过）"
    SessionError.UserNotRecognised -> "秤未识别该用户（也可能 MAC 填错）——可换人重试或重新注册"
    SessionError.MissingTare -> "缺少验证称重——请重新注册"
    is SessionError.Timeout -> "等待超时（${error.step}）——请靠近秤并保持上秤姿势后重试"
    SessionError.Disconnected -> "连接断开——请靠近秤后重试"
    SessionError.CrcError -> "通信校验失败——请重试"
    SessionError.MacInvalid -> "MAC 可能错误——请核对秤身标签后修改"
    is SessionError.UnexpectedReply ->
        "设备回应异常（${error.step}${error.code?.let { "，码 $it" } ?: ""}）"
}

/** HUID 打码显示（SettingsScreen；真实 HUID 不落日志/截图）。 */
fun maskHuid(huid: String): String =
    if (huid.length <= 7) "****" else huid.take(5) + "****" + huid.takeLast(2)

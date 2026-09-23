package xyz.crazyh.kscale3.ble

/**
 * GATT 特征 UUID 全表（TECH.md §4）。
 * ⚠️ 必须按 UUID 寻址，勿用 handle（各系统栈会重排 handle，UUID 稳定）。
 */
object GattUuids {
    /** 0x21 认证请求 / randA（明文，Write/Notify） */
    const val AUTH_REQ = "02b2a08e-f8b0-4047-b1fd-f4e0efeee679"

    /** 0x25 randB + tokenB 交换（明文，Write/Notify） */
    const val AUTH_TOKENS = "32330a04-15d9-421a-91c5-2a2d5c7525c9"

    /** 0x29 会话 WorkKey 下发（AES(RootKey)，Write/Notify） */
    const val WORK_KEY = "a3d330f8-b84f-4f48-a78c-f8d1e33b597a"

    /** 0x45 绑定武装（写 0x01，明文）；⚠️ 无"解绑"步骤，不要发 */
    const val BINDING = "4338c65e-ed8e-4085-bbea-a25e33ca6b54"

    /** 0x2D HUID 写闪存 / 皮重称量（AES(WorkKey)） */
    const val HUID_TARE = "42596cbe-d291-4da3-8ca6-d1ae5d1c9174"

    /** 0x31 用户档案 & 提交（AES(WorkKey)） */
    const val USER_PROFILE = "8cc61d7d-66c0-4802-89c3-38c5a163592e"

    /** 0x52 时间同步（明文；⚠️ 私有 8B 布局，见 TimeSyncPacket） */
    const val TIME_SYNC = "00002a2b-0000-1000-8000-00805f9b34fb"

    /** 0x5A 序列号查询（明文） */
    const val SERIAL = "11872f15-a91d-49da-ac89-5107284f3425"

    /** 0x62 设备配置状态（恒回 Code 1） */
    const val CONFIG = "bfc36f6e-4150-4a4b-9052-3d359e52962e"

    /** 0x7A 硬件型号（如 M00F7） */
    const val MODEL = "426f058d-8211-413e-8320-397a890a08bf"

    /** 0x8F 离线历史（格式未知，v1 仅留调试/入库原始回复） */
    const val OFFLINE_HISTORY = "0212f42a-5f19-4bc1-ba52-d7ec7ccb71a4"

    /** 0x97 实时 BIA 流 38B（AES(WorkKey)） */
    const val BIA = "46797c17-d639-488d-9476-4789e8472878"

    /** 0xD7 能力上报（无响应写） */
    const val CAPABILITIES = "0000fe01-0000-1000-8000-00805f9b34fb"

    /** 0xDA 能力响应（Notify） */
    const val CAPABILITIES_RSP = "0000fe02-0000-1000-8000-00805f9b34fb"

    /** 状态/事件哨兵通知 */
    const val SENTINEL = "ba216311-1787-472b-bef6-3eb29e62293e"
}

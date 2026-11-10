package xyz.crazyh.kscale3.ui.i18n

/** English strings. */
object EnStrings : AppStrings {

    // ---- Bottom tabs ----
    override val tabWeighIn: String = "Weigh"
    override val tabUsers: String = "Users"
    override val tabHistory: String = "History"
    override val tabSettings: String = "Settings"

    // ---- Common buttons ----
    override val commonCancel: String = "Cancel"
    override val commonClose: String = "Close"
    override val commonHide: String = "Hide"
    override val commonRetry: String = "Retry"
    override val commonDelete: String = "Delete"
    override val commonEdit: String = "Edit"
    override val commonSave: String = "Save"
    override val commonBack: String = "Back"

    // ---- Session state ----
    override val sessionIdle: String = "Ready"
    override val sessionConnecting: String = "Connecting to scale…"
    override val sessionHandshaking: String = "Authenticating…"
    override val sessionAwaitingTare: String =
        "Step on the scale and stand still (verification weigh-in, barefoot)"

    override val sessionSyncingTime: String = "Syncing time…"
    override val sessionSendingProfile: String = "Confirming user…"
    override val sessionProfileRecognized: String = "User recognised"
    override val sessionProfileMissingTare: String = "Verification weigh-in missing"
    override val sessionProfileMismatch: String = "User mismatch"
    override fun sessionProfileUnknownCode(code: Int): String = "Device response code $code"
    override val sessionAwaitingMeasurement: String =
        "Step on the scale and stand still\nWaiting for weight / body fat / heart rate"

    override val sessionCommitting: String = "Finishing…"
    override val sessionDone: String = "Weigh-in complete"
    override val sessionPaired: String = "Registration successful"
    override val sessionFailedPrefix: String = "Failed: "
    override val sessionDialogTitle: String = "Weigh-in / registration"

    // ---- Session errors ----
    override val errorTokenMismatch: String = "Authentication failed (verification failed)"
    override val errorUserNotRecognised: String =
        "The scale cannot identify the user — switch user or re-register"

    override val errorMissingTare: String =
        "Verification weigh-in missing — please re-register"

    override fun errorTimeout(step: String): String =
        "Timed out waiting for $step — move closer to the scale and try again"

    override val errorDisconnected: String =
        "Connection lost — move closer to the scale and try again"

    override val errorCrc: String = "Communication checksum failed — please retry"
    override val errorMacInvalid: String =
        "MAC may be incorrect — check the label on the scale"

    override fun errorUnexpectedReply(step: String, code: Int?): String =
        "Unexpected device response ($step${code?.let { ", code $it" } ?: ""})"

    override fun scanFailed(detail: String?): String =
        detail ?: "Scan failed. Check Bluetooth permission and that Bluetooth is on."

    // ---- Measurement details ----
    override val measurementTrueSection: String = "Measured (directly from scale)"
    override val measurementBodyCard: String = "Body data"
    override val metricWeight: String = "Weight"
    override val metricFatPct: String = "Body fat"
    override val metricHeartRate: String = "Heart rate"
    override val measurementEstimatesSection: String =
        "Estimates (empirical formulas, for reference only)"

    override val measurementCompositionCard: String = "Body composition"
    override val estimateBadge: String = "estimate"
    override val metricFatFreeMass: String = "Fat-free mass"
    override val metricFatMass: String = "Fat mass"
    override val metricBodyWater: String = "Body water"
    override val metricMuscleMass: String = "Muscle mass"
    override val metricBoneMass: String = "Bone mass"
    override val measurementOtherCard: String = "Other"
    override val metricBmi: String = "BMI"
    override val metricBmr: String = "Basal metabolic rate"
    override fun deviceTime(deviceTs: String): String = "Device time: $deviceTs"

    // ---- Device scan / pairing ----
    override val scanTitle: String = "Device scan & pairing"
    override val scanSavedDevice: String = "Saved device"
    override val scanUseSavedDevice: String = "Use saved device"
    override val scanPermissionRequired: String =
        "Bluetooth permission is required to scan and connect — tap “Grant” and allow it in the system dialog."

    override val scanGrantPermission: String = "Grant Bluetooth permission"
    override val scanMacSection: String = "Device MAC address"
    override val scanMacLabelIos: String = "Scale label MAC (required)"
    override val scanMacLabelAndroid: String =
        "MAC (leave blank to use scanned address; can override)"

    override val scanChooseSection: String = "Scan / select device"
    override val scanScanning: String = "Scanning…"
    override val scanStart: String = "Start scan"
    override val scanStop: String = "Stop"
    override fun scanBroadcastCount(count: Int): String = "$count advertisements received"
    override val scanBroadcastWakeHint: String =
        " — if it stays 0: step on the scale to wake it (it does not advertise while idle), and make sure Bluetooth is on"

    override val scanUnknownDevice: String = "(unknown device)"
    override val scanLikelyScale: String = "Likely scale"
    override val scanIosHint: String =
        "On iOS, scan and select the device first (CoreBluetooth UUID); the MAC is only used for the RootKey and cannot replace scanning."

    override val scanIosMacHint: String =
        "Enter the MAC from the scale label (required for the RootKey)."
    override val scanSaveErrorIosSelection: String =
        "Select a scanned device and enter the MAC from the scale label"

    override val scanSaveErrorInvalidMac: String = "Invalid MAC format (expected XX:XX:XX:XX:XX:XX)"
    override val scanSaveUpdate: String = "Save and update device"
    override val scanSaveAndContinue: String = "Save device and continue"

    // ---- Users ----
    override val usersTitle: String = "Users"
    override val usersAdd: String = "Add user"
    override val usersNoDevice: String =
        "No scale paired — scan and pair on the Device page first (registration requires a connection)."

    override val usersEmpty: String =
        "No users yet. Tap “Add user” in the top-right corner to create a profile."

    override val usersRegistered: String = "Registered"
    override val usersUnregistered: String = "Not registered"
    override fun usersCardSummary(
        age: Int,
        heightCm: Int,
        goalKg: Double?,
        lastKg: Double?,
    ): String =
        "$age y · $heightCm cm" +
                (goalKg?.let { " · Goal $it kg" } ?: "") +
                (lastKg?.let { " · Last $it kg" } ?: "")

    override val usersReRegister: String = "Re-register on scale"
    override val usersRegister: String = "Register on scale"
    override fun usersDeleteTitle(name: String): String = "Delete user “$name”?"
    override val usersDeleteMessage: String =
        "This deletes the local profile and all history for this user. This cannot be undone.\n" +
                "The HUID registered on the scale is not affected."

    // ---- User edit ----
    override val editNewTitle: String = "New user"
    override val editTitle: String = "Edit user"
    override val editName: String = "Name"
    override val editMale: String = "Male"
    override val editFemale: String = "Female"
    override val editAge: String = "Age"
    override val editHeight: String = "Height (cm)"
    override val editGoal: String = "Goal weight (kg, optional)"
    override val editErrorNameRequired: String = "Enter a name"
    override val editErrorAgeRange: String = "Age must be between 10 and 120"
    override val editErrorHeightRange: String = "Height must be between 80 and 230 cm"
    override val editErrorGoalFormat: String = "Invalid goal weight"
    override fun editHuidLine(maskedHuid: String): String = "HUID: $maskedHuid (registered)"
    override val editReRegisterNewHuid: String = "Re-register on scale (new HUID)"
    override val editRegisterMode1: String = "Register on scale (Mode 1)"

    // ---- Weigh-in ----
    override val weighTitle: String = "Start weighing"
    override fun weighCurrentDevice(name: String): String = "Paired device: $name"
    override val weighNoDevice: String = "No scale paired — scan and pair on the Device page first."
    override val weighGoPair: String = "Pair a device"
    override val weighSelectUser: String = "Select user"
    override val weighUnregistered: String = "Not registered (register on the scale first)"
    override val weighStart: String = "Start weighing (Mode 2)"
    override val weighRecoveryGoRegister: String = "Register (finish verification)"
    override val weighRecoveryReRegister: String = "Re-register"
    override val weighRecoveryChangeUser: String = "Change user"
    override val weighRecoveryEditMac: String = "Edit MAC"

    // ---- Result ----
    override val resultTitle: String = "Measurement result"
    override val resultSave: String = "Save to history"

    // ---- History ----
    override val historyTitle: String = "History & trends"
    override val historyAll: String = "All"
    override val historyWeightTrend: String = "Weight trend"
    override val historyEmpty: String = "No measurements yet"
    override val historyList: String = "Measurements"
    override val historyTapHint: String = "Tap a card to see details"

    // ---- Settings ----
    override val settingsTitle: String = "Settings"
    override val settingsDeviceInfo: String = "Device info"
    override val settingsNoDevice: String = "No device paired."
    override val settingsName: String = "Name"
    override val settingsMac: String = "MAC address"
    override val settingsBleId: String = "BLE identifier"
    override val settingsWeightUnit: String = "Scale display unit"
    override val settingsConnectingScale: String = "Connecting to scale…"
    override val settingsRegisteredUsers: String = "Registered users"
    override val settingsNoRegisteredUsers: String = "None — register on the Users page."
    override val settingsForgetDevice: String = "Forget paired scale"
    override val settingsFactoryResetting: String = "Restoring factory settings…"
    override val settingsFactoryReset: String = "Restore scale factory settings"
    override val settingsLanguage: String = "Language"
    override val settingsDebugMode: String = "Debug mode"
    override val settingsLogFramesOnly: String = "Log: frames only (hex)"
    override val settingsLogEmpty: String = "(none — start a weigh-in/registration to see frames)"
    override val settingsForgetDeviceTitle: String = "Forget the paired scale?"
    override val settingsForgetDeviceMessage: String =
        "This clears the saved scale on this device.\n" +
                "You will need to pair again to reconnect.\n" +
                "User profiles and weight history are kept."

    override val settingsForgetConfirm: String = "Forget"
    override val settingsFactoryResetTitle: String = "Restore scale factory settings?"
    override val settingsFactoryResetMessage: String =
        "This clears the bond and user data on the scale.\n" +
                "The local pairing will also be removed; you will need to pair and register again.\n" +
                "Local user profiles and weight history are kept."

    override val settingsFactoryResetConfirm: String = "Restore factory settings"

    // ---- Scale display units ----
    override val unitKg: String = "Kilograms"
    override val unitJin: String = "Jin"
    override val unitLb: String = "Pounds"

    // ---- Language options ----
    override val languageFollowSystem: String = "Follow system"
    override val languageChinese: String = "中文"
    override val languageEnglish: String = "English"
    override fun languageFollowSystemValue(effectiveName: String): String =
        "Follow system ($effectiveName)"
}

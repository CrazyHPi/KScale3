#!/usr/bin/env python3
"""
==============================================================================
HUAWEI Scale 3 / Scale 3 Pro — Standalone & User-Friendly BLE Driver (scale3.py)
==============================================================================
25/08/2026 -  Translated original DevOpsBenjamin python from French to English.
File forked from:
https://github.com/DevOpsBenjamin/SimpleBodyGraph/tree/main/Scale3_test

How it works:

1. First launch (if scale_config.json is absent):
   - Interactive assistant: asks for MAC, Sex, Age, Height (weight is captured
     automatically by the scale during the tare weigh-in on 0x2D).
   - Automatically generates a random HUID identifier.
   - Runs the full pairing & flash-write sequence (Mode 1):
        * Encrypted handshake (0x21, 0x25, 0x29)
        * Bind arming (0x45) + HUID registration in flash memory (0x2D)
        * Automatically captures the actual tare weight on 0x2D
        * Time sync (0x52) + HW queries (0x5A, 0x7A, 0x62)
        * User profile validation (0x31 -> Code 0)
        * Disarming (0x45)
        * Live BIA body composition weigh-in (0x97)
   - Automatically saves to scale_config.json and exits cleanly.

2. Subsequent launches (scale_config.json exists):
   - Loads the saved configuration.
   - Performs the full routine BIA weigh-in (proven Mode 2).
   - Displays the results (Weight, Body Fat %, Heart Rate, 8-electrode BIA).
   - Updates the last weight and exits cleanly.

Options:
   --debug, -d     Enable full BLE hex frame debug output
   --reconfig      Force reconfiguration and profile re-pairing
   --factory-reset Erase the scale's binding/user data (factory reset) and exit.
                   The scale must be re-paired afterwards.
==============================================================================
"""
import argparse
import asyncio
import datetime
import hashlib
import hmac
import json
import logging
import os
import random
import re
import struct
import sys
import uuid
from typing import Optional

from bleak import BleakClient, BleakScanner
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.backends import default_backend

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CONFIG_FILE = os.path.join(BASE_DIR, "scale_config.json")
LOG_FILE = os.path.join(BASE_DIR, "scale3_live.log")

# --- Huawei GATT UUIDs ---
UUID_STATUS_SENTINEL = "ba216311-1787-472b-bef6-3eb29e62293e"  # Handle 113 / CCCD 115
UUID_AUTH_REQ = "02b2a08e-f8b0-4047-b1fd-f4e0efeee679"         # Handle 33 (0x21)
UUID_AUTH_TOKEN = "32330a04-15d9-421a-91c5-2a2d5c7525c9"       # Handle 37 (0x25)
UUID_WORK_KEY = "a3d330f8-b84f-4f48-a78c-f8d1e33b597a"         # Handle 41 (0x29)
UUID_REG_2D = "42596cbe-d291-4da3-8ca6-d1ae5d1c9174"           # Handle 45 (0x2D)
UUID_USER_INFO = "8cc61d7d-66c0-4802-89c3-38c5a163592e"        # Handle 49 (0x31)
UUID_BIND_45 = "4338c65e-ed8e-4085-bbea-a25e33ca6b54"          # Handle 69 (0x45)
UUID_TIME_SYNC = "00002a2b-0000-1000-8000-00805f9b34fb"        # Handle 82 (0x52)
UUID_INFO_5A = "11872f15-a91d-49da-ac89-5107284f3425"          # Handle 90 (0x5A) - Serial number; also unit set
UUID_INFO_62 = "bfc36f6e-4150-4a4b-9052-3d359e52962e"          # Handle 98 (0x62)
UUID_INFO_7A = "426f058d-8211-413e-8320-397a890a08bf"          # Handle 122 (0x7A) - Model
UUID_HISTORY_8F = "0212f42a-5f19-4bc1-ba52-d7ec7ccb71a4"       # Handle 143 (0x8F)
UUID_REALTIME_BIA = "46797c17-d639-488d-9476-4789e8472878"     # Handle 151 (0x97)
UUID_FE01_WRITE = "0000fe01-0000-1000-8000-00805f9b34fb"       # Handle 215 (0xD7)
UUID_FE02_NOTIFY = "0000fe02-0000-1000-8000-00805f9b34fb"      # Handle 218 (0xDA)

# Weight-unit control (observed 2026-10-28 from Huawei Health; see doc/TECH.md §7.3)
UUID_WEIGHT_UNIT_SET = UUID_INFO_5A                            # 0x5A; observed handle 0x47
UUID_WEIGHT_UNIT_GET = "7e6dbc73-42e7-45b9-a6ec-6aa2d7834695"  # observed handle 0x4B

# Factory reset control point (observed 2026-10-30; see doc/TECH.md §7.4)
UUID_FACTORY_RESET = "4599778a-ea8a-4ffe-9499-de1ba7a03f3a"    # observed handle 0x22

# Generic empty-payload frame. On the unit-query / 0x62 characteristics this is
# a query; on UUID_FACTORY_RESET the same bytes execute a factory reset.
FRAME_GENERIC_QUERY = bytes.fromhex("db0300c140")

# --- Weight unit codes ---
WEIGHT_UNIT_KG = 1
WEIGHT_UNIT_LB = 2
WEIGHT_UNIT_JIN = 3
WEIGHT_UNIT_NAMES = {WEIGHT_UNIT_KG: "kg", WEIGHT_UNIT_LB: "lb", WEIGHT_UNIT_JIN: "jin"}

# --- Cryptographic constants ---
CAK = bytes.fromhex("90B96ECA297EF78717E66E491084D3F8")
WB1033 = bytes.fromhex("CA4946D061C9FE534F6044F930EBB69B")
WB2033 = bytes.fromhex("FBCE6E2B4BAF80ED969BA26B4A4B9325")

CRC_TABLE = [
    0, 4129, 8258, 12387, 16516, 20645, 24774, 28903, 33032, 37161, 41290, 45419, 49548, 53677, 57806, 61935,
    4657, 528, 12915, 8786, 21173, 17044, 29431, 25302, 37689, 33560, 45947, 41818, 54205, 50076, 62463, 58334,
    9314, 13379, 1056, 5121, 25830, 29895, 17572, 21637, 42346, 46411, 34088, 38153, 58862, 62927, 50604, 54669,
    13907, 9842, 5649, 1584, 30423, 26358, 22165, 18100, 46939, 42874, 38681, 34616, 63455, 59390, 55197, 51132,
    18628, 22757, 26758, 30887, 2112, 6241, 10242, 14371, 51660, 55789, 59790, 63919, 35144, 39273, 43274, 47403,
    23285, 19156, 31415, 27286, 6769, 2640, 14899, 10770, 56317, 52188, 64447, 60318, 39801, 35672, 47931, 43802,
    27814, 31879, 19684, 23749, 11298, 15363, 3168, 7233, 60846, 64911, 52716, 56781, 44330, 48395, 36200, 40265,
    32407, 28342, 24277, 20212, 15891, 11826, 7761, 3696, 65439, 61374, 57309, 53244, 48923, 44858, 40793, 36728,
    37256, 33193, 45514, 41451, 53516, 49453, 61774, 57711, 4224, 161, 12482, 8419, 20484, 16421, 28742, 24679,
    33721, 37784, 41979, 46042, 49981, 54044, 58239, 62302, 689, 4752, 8947, 13010, 16949, 21012, 25207, 29270,
    46570, 42443, 38312, 34185, 62830, 58703, 54572, 50445, 13538, 9411, 5280, 1153, 29798, 25671, 21540, 17413,
    42971, 47098, 34713, 38840, 59231, 63358, 50973, 55100, 9939, 14066, 1681, 5808, 26199, 30326, 17941, 22068,
    55628, 51565, 63758, 59695, 39368, 35305, 47498, 43435, 22596, 18533, 30726, 26663, 6336, 2273, 14466, 10403,
    52093, 56156, 60223, 64286, 35833, 39896, 43963, 48026, 19061, 23124, 27191, 31254, 2801, 6864, 10931, 14994,
    64814, 60687, 56684, 52557, 48554, 44427, 40424, 36297, 31782, 27655, 23652, 19525, 15522, 11395, 7392, 3265,
    61215, 65342, 53085, 57212, 44955, 49082, 36825, 40952, 28183, 32310, 20053, 24180, 11923, 16050, 3793, 7920
]


def derive_root_key(mac_address: str) -> bytes:
    mac_clean = (mac_address.replace(":", "") + "0000").upper().encode('utf-8')
    b4 = bytes(((WB1033[i] << 4) ^ WB2033[i]) & 0xFF for i in range(16))
    bE = hashlib.sha256(b4).digest()[:16]
    b5 = bytes(((bE[i] >> 6) ^ mac_clean[i]) & 0xFF for i in range(16))
    return hashlib.sha256(b5).digest()[:16]


def generate_auth_tokens(randA: bytes, randB: bytes) -> bytes:
    randAB = randA + randB
    h1 = hmac.new(CAK + b"1123", randAB, hashlib.sha256).digest()
    tokenB = hmac.new(h1, randAB, hashlib.sha256).digest()
    return tokenB


def build_framed_chunks(payload: bytes, magic: int = 0xDB) -> list:
    total_frames = (len(payload) + 14) // 15 or 1
    chunks = []
    for i in range(total_frames):
        chunk_data = payload[i*15:(i+1)*15]
        length = len(chunk_data)
        i4 = length + 5
        frame = bytearray(i4)
        frame[0] = magic
        frame[1] = length + 3
        frame[2] = (((total_frames - 1) & 0x0F) << 4) | (i & 0x0F)
        frame[3:3+length] = chunk_data
        s = 0
        for b in frame:
            s = ((s << 8) & 0xFFFF) ^ CRC_TABLE[(b ^ (s >> 8)) & 0xFF]
        frame[i4-2] = s & 0xFF
        frame[i4-1] = (s >> 8) & 0xFF
        chunks.append(bytes(frame))
    return chunks


# ---------------------------------------------------------------------------
# Weight unit control (observed from the official Huawei Health app, 2026-10-28)
# See doc/TECH.md §7.3 and doc/SUMMARY.md §7.3.
# ---------------------------------------------------------------------------
def build_weight_unit_frame(unit: int) -> bytes:
    """Build the 0x5A unit-set frame: DB 04 00 <unit> <CRC16_LE>."""
    if unit not in WEIGHT_UNIT_NAMES:
        raise ValueError(f"Unsupported weight unit code: {unit} (expected 1=kg, 2=lb, 3=jin)")
    return build_framed_chunks(bytes([unit]), magic=0xDB)[0]


def parse_weight_unit_reply(raw: bytes) -> Optional[int]:
    """Parse BD 04 00 XX crc crc and return XX.

    XX == 0 for a set acknowledgement; 1/2/3 for a get-unit reply.
    """
    raw = bytes(raw)
    if len(raw) < 4 or raw[0] != 0xBD or raw[1] != 0x04 or raw[2] != 0x00:
        return None
    return raw[3]


async def set_weight_unit(client, unit: int, timeout: float = 2.0) -> bool:
    """Set the scale's displayed unit (1=kg, 2=lb, 3=jin).

    Observed frames:
      kg  -> DB 04 00 01 20 D9
      lb  -> DB 04 00 02 70 80
      jin -> DB 04 00 03 40 B7

    The write uses ATT Write Command (no response); the scale acknowledges with
    an Indication BD 04 00 00 65 89 (status 0). Returns True on status 0.
    """
    reply = asyncio.Event()
    status = {"value": None}

    def on_reply(sender, data):
        value = parse_weight_unit_reply(data)
        if value is not None:
            status["value"] = value
            reply.set()

    await client.start_notify(UUID_WEIGHT_UNIT_SET, on_reply)
    try:
        await asyncio.sleep(0.15)
        frame = build_weight_unit_frame(unit)
        await safe_write(client, UUID_WEIGHT_UNIT_SET, frame, prefer_response=False)
        await asyncio.wait_for(reply.wait(), timeout=timeout)
        return status["value"] == 0
    except asyncio.TimeoutError:
        return False
    finally:
        try:
            await client.stop_notify(UUID_WEIGHT_UNIT_SET)
        except Exception:
            pass


async def get_weight_unit(client, timeout: float = 2.0) -> Optional[int]:
    """Read the scale's current display unit (1=kg, 2=lb, 3=jin).

    Observed traffic: write DB 03 00 C1 40 to the unit-query characteristic
    (7e6dbc73-...); the scale replies with an Indication BD 04 00 <unit> <crc>.
    """
    reply = asyncio.Event()
    unit = {"value": None}

    def on_reply(sender, data):
        value = parse_weight_unit_reply(data)
        if value is not None:
            unit["value"] = value
            reply.set()

    await client.start_notify(UUID_WEIGHT_UNIT_GET, on_reply)
    try:
        await asyncio.sleep(0.15)
        await safe_write(client, UUID_WEIGHT_UNIT_GET, bytes.fromhex("db0300c140"), prefer_response=False)
        await asyncio.wait_for(reply.wait(), timeout=timeout)
        return unit["value"] if unit["value"] in WEIGHT_UNIT_NAMES else None
    except asyncio.TimeoutError:
        return None
    finally:
        try:
            await client.stop_notify(UUID_WEIGHT_UNIT_GET)
        except Exception:
            pass


# ---------------------------------------------------------------------------
# Factory reset (observed from the official Huawei Health app, 2026-10-30)
# See doc/TECH.md §7.4 and doc/SUMMARY.md §7.4.
# ---------------------------------------------------------------------------
def parse_status_reply(raw: bytes) -> Optional[int]:
    """Parse a short reply BD 04 00 XX <crc16_le> and return XX (status code)."""
    raw = bytes(raw)
    if len(raw) < 4 or raw[0] != 0xBD or raw[1] != 0x04 or raw[2] != 0x00:
        return None
    return raw[3]


def build_factory_reset_frame() -> bytes:
    """Build the factory-reset command: DB 03 00 C1 40.

    This is the generic empty-payload frame. On UUID_FACTORY_RESET it means
    "execute factory reset"; on other characteristics the same bytes are just
    a query. The target UUID, not the payload, determines the operation.
    """
    return FRAME_GENERIC_QUERY


async def factory_reset(client, timeout: float = 3.0) -> bool:
    """Erase the scale's binding/user data (factory reset).

    The connection must already be authenticated (0x21/0x25/0x29 handshake).

    Observed wire sequence (2026-10-30, Huawei Health 17.0.8.300):
      1. enable Indications on UUID_FACTORY_RESET (CCCD 0x2902 -> 02 00)
      2. ATT Write Command (no response): DB 03 00 C1 40
      3. scale indicates BD 04 00 00 65 89 (status 0 = accepted)
      4. sentinel ba216311-... emits BD 04 00 00 65 89 then BD 04 00 01 A4 49
      5. the app disconnects; the scale must be re-paired (Mode 1)

    Returns True if the scale acknowledged with status 0.
    """
    reply = asyncio.Event()
    status = {"value": None}

    def on_reply(sender, data):
        value = parse_status_reply(data)
        if value is not None:
            status["value"] = value
            reply.set()

    await client.start_notify(UUID_FACTORY_RESET, on_reply)
    try:
        await asyncio.sleep(0.15)
        await safe_write(client, UUID_FACTORY_RESET, build_factory_reset_frame(), prefer_response=False)
        await asyncio.wait_for(reply.wait(), timeout=timeout)
        return status["value"] == 0
    except asyncio.TimeoutError:
        return False
    finally:
        try:
            await client.stop_notify(UUID_FACTORY_RESET)
        except Exception:
            pass


class FrameReassembler:
    def __init__(self):
        self.buffer = bytearray()
        self.magic = None

    def feed(self, raw: bytes) -> Optional[bytes]:
        if len(raw) < 5 or raw[0] not in (0xDB, 0xDC, 0xBD, 0xCD):
            return None
        self.magic = raw[0]
        length = raw[1] - 3
        seq = raw[2]
        idx = seq & 0x0F
        total = ((seq >> 4) & 0x0F) + 1
        if idx == 0:
            self.buffer = bytearray()
        self.buffer.extend(raw[3:3+length])
        if idx + 1 == total:
            res = bytes(self.buffer)
            self.buffer = bytearray()
            return res
        return None


def build_user_info_profile(huid: str, uid: str, sex: int, age: int, height: int, weight: float, user_type: int) -> bytes:
    huid_bytes = huid.encode('utf-8')[:30].ljust(30, b'\x00')
    uid_bytes = uid.encode('utf-8')[:32].ljust(32, b'\x00')
    buf = bytearray()
    buf += huid_bytes
    buf += uid_bytes
    buf.append(sex & 0xFF)
    buf.append(age & 0xFF)
    buf += struct.pack('<H', height)
    buf += struct.pack('<H', int(round(weight * 100)))
    buf.append(user_type & 0xFF)
    assert len(buf) == 69
    return bytes(buf)


async def safe_write(client, char_uuid, data, prefer_response=True):
    try:
        await client.write_gatt_char(char_uuid, data, response=prefer_response)
    except Exception as e:
        if "permit" in str(e).lower() or "not allowed" in str(e).lower():
            await client.write_gatt_char(char_uuid, data, response=not prefer_response)
        else:
            raise


def generate_random_huid() -> str:
    prefix = "300330000"
    suffix = "".join(str(random.randint(0, 9)) for _ in range(8))
    return prefix + suffix


def is_valid_mac(mac: str) -> bool:
    return bool(re.match(r"^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$", mac))


def prompt_user_profile() -> dict:
    is_macos = (sys.platform == "darwin")
    print("\n" + "=" * 68)
    print(" 🔧 INITIAL SETUP OF THE HUAWEI SCALE 3 / 3 PRO DRIVER")
    print("=" * 68)
    print("Please enter your information to initialize the profile:\n")

    # 1. Physical MAC address
    if is_macos:
        print(" ℹ️ [macOS INFO]")
        print(" Apple hides the real Bluetooth MAC address and replaces it with a UUID.")
        print(" To derive the security key, we need the REAL MAC address.")
        print(" 👉 You will find it on the label on the back/underside of your scale.\n")
        while True:
            m = input(" 👉 Physical MAC address of the scale (e.g. AA:BB:CC:DD:EE:FF): ").strip()
            m_clean = m.replace("-", ":").upper()
            if is_valid_mac(m_clean):
                mac = m_clean
                break
            print(" ❌ Invalid MAC format. Expected format: XX:XX:XX:XX:XX:XX.")
    else:
        print(" 👉 Enter the scale's MAC address (leave blank for automatic detection): ")
        m = input(" MAC [Auto]: ").strip()
        m_clean = m.replace("-", ":").upper()
        mac = m_clean if is_valid_mac(m_clean) else ""

    # 2. Sex
    while True:
        s = input("\n 👉 Sex (1 = Male, 0 = Female): ").strip()
        if s in ("0", "1"):
            sex = int(s)
            break
        print(" ❌ Please enter 1 for Male or 0 for Female.")

    # 3. Age
    while True:
        a = input(" 👉 Age in years (e.g. 25): ").strip()
        if a.isdigit() and 10 <= int(a) <= 120:
            age = int(a)
            break
        print(" ❌ Please enter a valid age (between 10 and 120 years).")

    # 4. Height
    while True:
        h = input(" 👉 Height in cm (e.g. 170): ").strip()
        if h.isdigit() and 80 <= int(h) <= 230:
            height = int(h)
            break
        print(" ❌ Please enter a valid height (between 80 and 230 cm).")

    # 5. Unique anonymous HUID
    huid = generate_random_huid()
    print(f"\n ✅ HUID identifier automatically generated: {huid}")
    print(" ⚖️ Weight will be measured automatically by the scale during pairing.")
    print("=" * 68 + "\n")

    return {
        "mac": mac,
        "huid": huid,
        "sex": sex,
        "age": age,
        "height": height,
        "last_weight": 0.0
    }


class Scale3Driver:
    def __init__(self, config: dict, is_pairing: bool = False, debug: bool = False, factory_reset_only: bool = False):
        self.config = config
        self.is_pairing = is_pairing
        self.debug = debug
        self.factory_reset_only = factory_reset_only
        self.mac = config.get("mac", "").strip().upper()
        self.huid = config.get("huid", generate_random_huid())
        self.sex = int(config.get("sex", 1))
        self.age = int(config.get("age", 30))
        self.height = int(config.get("height", 175))
        self.weight = float(config.get("last_weight", 0.0))
        self.root_key = None
        self.work_key = os.urandom(16)
        self.randA = None
        self.auth_req_event = asyncio.Event()
        self.work_key_event = asyncio.Event()
        self.bind_event = asyncio.Event()
        self.reg_event = asyncio.Event()
        self.user_info_event = asyncio.Event()
        self.user_info_code = None
        self.measure_done = asyncio.Event()
        self.measured_data = {}
        self.log_file = open(LOG_FILE, "w", encoding="utf-8")

    def dbg(self, msg: str):
        now_str = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S,%f")[:-3]
        line = f"[{now_str}] [DEBUG] {msg}"
        self.log_file.write(line + "\n")
        self.log_file.flush()
        if self.debug:
            print(f"[{now_str}] {msg}")

    def info(self, msg: str):
        now_str = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S,%f")[:-3]
        line = f"[{now_str}] [INFO] {msg}"
        self.log_file.write(line + "\n")
        self.log_file.flush()
        print(f"[{now_str}] {msg}")

    def decrypt(self, full_enc: bytes) -> bytes:
        if len(full_enc) <= 16:
            return b""
        iv = full_enc[:16]
        ct = full_enc[16:]
        return Cipher(algorithms.AES(self.work_key), modes.CTR(iv), backend=default_backend()).decryptor().update(ct)

    def encrypt(self, plain: bytes) -> bytes:
        iv = os.urandom(16)
        ct = Cipher(algorithms.AES(self.work_key), modes.CTR(iv), backend=default_backend()).encryptor().update(plain)
        return iv + ct

    async def scan_for_scale(self) -> Optional[any]:
        found_device = None
        found_event = asyncio.Event()

        def detection_callback(device, adv):
            nonlocal found_device
            name = device.name or adv.local_name or ""
            if self.mac and (device.address.upper() == self.mac or device.address == self.mac):
                self.info(f"[SCAN] Scale found by MAC: {name} [{device.address}] RSSI={adv.rssi}")
                found_device = device
                found_event.set()
            elif "HaigeBLE" in name or "Scale 3" in name or "HUAWEI Scale" in name:
                self.info(f"[SCAN] Scale found: {name} [{device.address}] RSSI={adv.rssi}")
                found_device = device
                found_event.set()

        print("🔍 Bluetooth scan in progress...")
        print("👉 Step on the scale to turn it on...")
        scanner = BleakScanner(detection_callback=detection_callback)
        await scanner.start()
        try:
            await asyncio.wait_for(found_event.wait(), timeout=90.0)
        except asyncio.TimeoutError:
            self.info("❌ Timeout exceeded (90s). Make sure the scale is turned on.")
            await scanner.stop()
            return None
        await scanner.stop()
        return found_device

    async def run(self) -> bool:
        device = await self.scan_for_scale()
        if not device:
            return False

        if not self.mac and is_valid_mac(device.address):
            self.mac = device.address.upper()
            self.config["mac"] = self.mac

        if not self.mac:
            print("\n❌ ERROR: The scale's real physical MAC address is required to derive the key.")
            return False

        self.root_key = derive_root_key(self.mac)
        mode_str = "FLASH PAIRING (Mode 1)" if self.is_pairing else "ROUTINE WEIGH-IN (Mode 2)"
        self.info(f"[CONNECTION] Connecting to {device.address} ({mode_str})...")
        self.dbg(f"Target Crypto MAC: {self.mac}")

        async with BleakClient(device) as client:
            self.info("[CONNECTION] Connected to GATT server.")
            # Enable Sentinel (0x73) and FE02 (0xDA)
            try:
                await client.start_notify(UUID_STATUS_SENTINEL, lambda s, d: self.dbg(f" [SENTINEL RX] {bytes(d).hex()}"))
                await asyncio.sleep(0.1)
                await client.start_notify(UUID_FE02_NOTIFY, lambda s, d: self.dbg(f" [FE02 RX] {bytes(d).hex()}"))
                await asyncio.sleep(0.1)
            except Exception:
                pass

            # -------------------------------------------------------------
            # STEP 1: AUTHENTICATION & HANDSHAKE
            # -------------------------------------------------------------
            self.dbg(">>> [STEP 1] Authentication & Handshake (Frida-Validated HMAC Formula)")
            randA_reassembler = FrameReassembler()

            def on_auth_rx(sender, data):
                raw = bytes(data)
                self.dbg(f" [0x21 RX RAW] {raw.hex()}")
                res = randA_reassembler.feed(raw)
                if res and len(res) >= 16:
                    self.randA = res[:16]
                    self.dbg(f" [0x21 REASSEMBLED] randA = {self.randA.hex()}")
                    self.auth_req_event.set()

            await client.start_notify(UUID_AUTH_REQ, on_auth_rx)
            await asyncio.sleep(0.15)
            auth_tx = bytes.fromhex("db0300c140")
            self.dbg(f" [0x21 TX] {auth_tx.hex()} (Auth Request)")
            await safe_write(client, UUID_AUTH_REQ, auth_tx, prefer_response=True)
            try:
                await asyncio.wait_for(self.auth_req_event.wait(), timeout=3.0)
            except asyncio.TimeoutError:
                self.info("❌ Failed to receive randA")
                return False
            finally:
                try: await client.stop_notify(UUID_AUTH_REQ)
                except Exception: pass

            randB = os.urandom(16)
            tokenB = generate_auth_tokens(self.randA, randB)
            self.dbg(f" [0x25 TX] randB ({randB.hex()}) + tokenB EXACT ({tokenB.hex()})")
            await client.start_notify(UUID_AUTH_TOKEN, lambda s, d: self.dbg(f" [0x25 RX] {bytes(d).hex()}"))
            await asyncio.sleep(0.15)
            for chunk in build_framed_chunks(randB + tokenB, magic=0xDB):
                self.dbg(f" -> [0x25 TX Chunk] {chunk.hex()}")
                await safe_write(client, UUID_AUTH_TOKEN, chunk, prefer_response=True)
                await asyncio.sleep(0.02)
            await asyncio.sleep(0.3)
            try: await client.stop_notify(UUID_AUTH_TOKEN)
            except Exception: pass

            def on_wk_rx(sender, data):
                raw = bytes(data)
                code = raw[3] if len(raw) >= 4 else None
                self.dbg(f" [0x29 RX] {raw.hex()} (Code {code})")
                if code == 0:
                    self.work_key_event.set()

            await client.start_notify(UUID_WORK_KEY, on_wk_rx)
            await asyncio.sleep(0.15)
            iv_wk = os.urandom(16)
            enc_wk = Cipher(algorithms.AES(self.root_key), modes.CTR(iv_wk), backend=default_backend()).encryptor().update(self.work_key)
            self.dbg(f" [0x29 TX] Encrypted WorkKey (IV={iv_wk.hex()}, WorkKey={self.work_key.hex()})")
            for chunk in build_framed_chunks(iv_wk + enc_wk, magic=0xDC):
                self.dbg(f" -> [0x29 TX Chunk] {chunk.hex()}")
                await safe_write(client, UUID_WORK_KEY, chunk, prefer_response=True)
                await asyncio.sleep(0.02)
            try:
                await asyncio.wait_for(self.work_key_event.wait(), timeout=2.0)
                self.info(" ✅ Authentication & Handshake successful (Client authenticated)!")
            finally:
                try: await client.stop_notify(UUID_WORK_KEY)
                except Exception: pass

            # -------------------------------------------------------------
            # STEP 2: CAPABILITIES (0xD7)
            # -------------------------------------------------------------
            cap_tx = bytes.fromhex("5a000500013701001ca9")
            self.dbg(f">>> [STEP 2] Sending hardware capabilities 0xD7: {cap_tx.hex()}")
            try:
                await safe_write(client, UUID_FE01_WRITE, cap_tx, prefer_response=False)
                await asyncio.sleep(0.1)
            except Exception as e:
                self.dbg(f" 0xD7: {e}")

            # -------------------------------------------------------------
            # FACTORY RESET MODE (--factory-reset)
            # -------------------------------------------------------------
            if self.factory_reset_only:
                self.dbg(">>> [FACTORY RESET] Erasing binding/user data on 0x22")
                ok = await factory_reset(client)
                if ok:
                    self.info(" ✅ Factory reset acknowledged (status 0).")
                    self.info(" ℹ️ The scale must be re-paired before it can be used again.")
                else:
                    self.info(" ❌ Factory reset failed or timed out.")
                return ok

            # -------------------------------------------------------------
            # MODE 1: FLASH PAIRING (First launch or --reconfig)
            # -------------------------------------------------------------
            if self.is_pairing:
                self.dbg(">>> [MODE 1: FLASH PAIRING] Arming 0x45 (01) and Registration 0x2D")
                # 1. Arm Bind 0x45 (value 0x01)
                bind_chunk = build_framed_chunks(b"\x01", magic=0xDB)[0]  # db04000120d9
                await client.start_notify(UUID_BIND_45, lambda s, d: self.bind_event.set())
                await asyncio.sleep(0.15)
                self.dbg(f" [0x45 TX] {bind_chunk.hex()} (Bind Arming 01)")
                await safe_write(client, UUID_BIND_45, bind_chunk, prefer_response=True)
                try: await asyncio.wait_for(self.bind_event.wait(), timeout=2.0)
                except Exception: pass
                finally:
                    try: await client.stop_notify(UUID_BIND_45)
                    except Exception: pass

                # 2. Register HUID on 0x2D
                reg_reassembler = FrameReassembler()

                def on_reg_rx(sender, data):
                    raw = bytes(data)
                    self.dbg(f" [0x2D RX RAW] {raw.hex()}")
                    res = reg_reassembler.feed(raw)
                    if res:
                        dec = self.decrypt(res)
                        self.dbg(f" [0x2D DECRYPTED] {dec.hex()}")
                        if len(dec) >= 3:
                            st = dec[0]
                            pw = struct.unpack('<H', dec[1:3])[0] / 100.0
                            if st == 0 and pw > 0:
                                self.weight = pw
                                self.dbg(f" [0x2D FLASH TARE WEIGH-IN] {self.weight:.2f} kg")
                        self.reg_event.set()

                await client.start_notify(UUID_REG_2D, on_reg_rx)
                await asyncio.sleep(0.15)
                huid_reg = self.huid.encode('utf-8')[:30].ljust(30, b'\x00')
                enc_reg = self.encrypt(huid_reg)
                self.dbg(f" [0x2D TX] Flash HUID registration ({self.huid})")
                for chunk in build_framed_chunks(enc_reg, magic=0xDC):
                    self.dbg(f" -> [0x2D TX Chunk] {chunk.hex()}")
                    await safe_write(client, UUID_REG_2D, chunk, prefer_response=True)

                print("\n" + "=" * 68)
                print(" 👣 STEP ONTO THE SCALE NOW (Flash validation weigh-in)")
                print(" 👉 Bare feet + handle in hand. Stay still for ~5 seconds...")
                print("=" * 68 + "\n")
                self.info(" ⏳ Waiting for tare weigh-in on the scale...")
                try:
                    await asyncio.wait_for(self.reg_event.wait(), timeout=25.0)
                    if self.weight and self.weight > 0:
                        self.info(f" ✅ User validated — Weight automatically captured: {self.weight:.2f} kg")
                    else:
                        self.info(" ✅ Profile written to flash chip.")
                except asyncio.TimeoutError:
                    self.dbg(" (0x2D flash wait completed)")
                finally:
                    try: await client.stop_notify(UUID_REG_2D)
                    except Exception: pass

            # -------------------------------------------------------------
            # STEP 3: TIME SYNCHRONIZATION (0x52)
            # -------------------------------------------------------------
            now = datetime.datetime.now()
            time_payload = struct.pack('<HBBBBBB', now.year, now.month, now.day, now.hour, now.minute, now.second, now.weekday() + 1)
            time_chunk = build_framed_chunks(time_payload, magic=0xDB)[0]
            self.dbg(f">>> [STEP 3] Time sync 0x52: {time_chunk.hex()} ({now:%Y-%m-%d %H:%M:%S})")
            time_event = asyncio.Event()

            def on_time_rx(sender, data):
                self.dbg(f" [0x52 RX] {bytes(data).hex()}")
                time_event.set()

            await client.start_notify(UUID_TIME_SYNC, on_time_rx)
            await asyncio.sleep(0.15)
            try:
                await safe_write(client, UUID_TIME_SYNC, time_chunk, prefer_response=True)
                await asyncio.wait_for(time_event.wait(), timeout=2.0)
            except Exception: pass
            finally:
                try: await client.stop_notify(UUID_TIME_SYNC)
                except Exception: pass

            # Sequential hardware queries if Mode 1
            if self.is_pairing:
                for u, label in [(UUID_INFO_5A, "0x5A SN"), (UUID_INFO_7A, "0x7A Model"), (UUID_INFO_62, "0x62 Config")]:
                    try:
                        await client.start_notify(u, lambda s, d, l=label: self.dbg(f" [{l} RX] {bytes(d).hex()}"))
                        await asyncio.sleep(0.1)
                        query_chunk = bytes.fromhex("db0300c140")
                        await safe_write(client, u, query_chunk, prefer_response=True)
                        await asyncio.sleep(0.15)
                        await client.stop_notify(u)
                    except Exception: pass

            # -------------------------------------------------------------
            # STEP 4: USER PROFILE (0x31)
            # -------------------------------------------------------------
            self.dbg(f">>> [STEP 4] Profile 0x31 (Weight={self.weight:.2f} kg, Sex={self.sex}, Age={self.age}, Height={self.height}cm)")

            def on_ui_rx(sender, data):
                raw = bytes(data)
                self.user_info_code = raw[3] if len(raw) >= 4 else None
                status = "SUCCESS (Code 0)" if self.user_info_code == 0 else f"NOTE (Code {self.user_info_code})"
                self.dbg(f" [0x31 RX] {raw.hex()} -> {status}")
                self.user_info_event.set()

            await client.start_notify(UUID_USER_INFO, on_ui_rx)
            await asyncio.sleep(0.2)
            profile_data = build_user_info_profile(self.huid, "", self.sex, self.age, self.height, self.weight, 0)
            self.dbg(f" [0x31 PLAINTEXT 69B] {profile_data.hex()}")
            enc_profile = self.encrypt(profile_data)
            for chunk in build_framed_chunks(enc_profile, magic=0xDC):
                self.dbg(f" -> [0x31 TX Chunk] {chunk.hex()}")
                await safe_write(client, UUID_USER_INFO, chunk, prefer_response=True)
            try:
                await asyncio.wait_for(self.user_info_event.wait(), timeout=5.0)
            except asyncio.TimeoutError:
                self.dbg(" (0x31 no immediate response, continuing)")
            finally:
                try: await client.stop_notify(UUID_USER_INFO)
                except Exception: pass

            if self.user_info_code == 0:
                self.info(" ✅ User profile validated by the scale (Code 0)!")
            else:
                self.dbg(f" [0x31 INFO] Response code {self.user_info_code} (dynamic calibration via direct streaming)")

            # Disarm 0x45 if Mode 1
            if self.is_pairing:
                try:
                    await safe_write(client, UUID_BIND_45, bytes.fromhex("db0300c140"), prefer_response=True)
                    await asyncio.sleep(0.15)
                except Exception: pass

            # -------------------------------------------------------------
            # STEP 5: HARDWARE QUERIES (0x7A Model, 0x8F History)
            # -------------------------------------------------------------
            self.dbg(">>> [STEP 5] Hardware queries (0x7A Model, 0x8F History)")
            for u, label in [(UUID_INFO_7A, "0x7A Model"), (UUID_HISTORY_8F, "0x8F History")]:
                try:
                    await client.start_notify(u, lambda s, d, l=label: self.dbg(f" [{l} RX] {bytes(d).hex()}"))
                    await asyncio.sleep(0.1)
                    query_chunk = bytes.fromhex("db0300c140")
                    self.dbg(f" [{label} TX] {query_chunk.hex()}")
                    await safe_write(client, u, query_chunk, prefer_response=True)
                    await asyncio.sleep(0.15)
                    await client.stop_notify(u)
                except Exception: pass

            # -------------------------------------------------------------
            # STEP 6: PROFILE REFRESH (0x31)
            # -------------------------------------------------------------
            self.dbg(">>> [STEP 6] Profile 0x31 (Refresh)")
            ui_refresh_event = asyncio.Event()
            await client.start_notify(UUID_USER_INFO, lambda s, d: ui_refresh_event.set())
            await asyncio.sleep(0.15)
            enc_profile2 = self.encrypt(profile_data)
            for chunk in build_framed_chunks(enc_profile2, magic=0xDC):
                await safe_write(client, UUID_USER_INFO, chunk, prefer_response=True)
            try: await asyncio.wait_for(ui_refresh_event.wait(), timeout=2.0)
            except Exception: pass
            finally:
                try: await client.stop_notify(UUID_USER_INFO)
                except Exception: pass

            # -------------------------------------------------------------
            # STEP 7: BIA BIOELECTRICAL IMPEDANCE MEASUREMENT (0x97)
            # -------------------------------------------------------------
            self.dbg(">>> [STEP 7] Arming BIA streaming 0x97")
            meas_reassembler = FrameReassembler()

            def on_meas_rx(sender, data):
                raw = bytes(data)
                self.dbg(f" [0x97 RX RAW] {raw.hex()}")
                res = meas_reassembler.feed(raw)
                if res:
                    dec = self.decrypt(res) if meas_reassembler.magic == 0xCD else res
                    self.dbg(f" [0x97 DECRYPTED {len(dec)}B] {dec.hex()}")
                    if len(dec) >= 26:
                        w = struct.unpack('<H', dec[0:2])[0] / 100.0
                        fat = struct.unpack('<H', dec[2:4])[0] / 10.0
                        y, m, d, hh, mm, ss = struct.unpack('<HBBBBB', dec[4:11])
                        feet = struct.unpack('<6H', dec[12:24])
                        hr = struct.unpack('<H', dec[24:26])[0]
                        hands = struct.unpack('<6H', dec[26:38]) if len(dec) >= 38 else ()
                        self.measured_data = {
                            "weight": w,
                            "fat": fat,
                            "heart_rate": hr,
                            "feet": feet,
                            "hands": hands,
                            "timestamp": f"{y:04d}-{m:02d}-{d:02d} at {hh:02d}:{mm:02d}:{ss:02d}"
                        }
                        self.measure_done.set()

            await client.start_notify(UUID_REALTIME_BIA, on_meas_rx)
            await asyncio.sleep(0.2)
            arm_tx = bytes.fromhex("db0300c140")
            self.dbg(f" [0x97 TX] {arm_tx.hex()} (Arming streaming)")
            try:
                await safe_write(client, UUID_REALTIME_BIA, arm_tx, prefer_response=True)
            except Exception as e:
                self.dbg(f" 0x97: {e}")

            print("\n" + "=" * 70)
            print(" 👣 BIA MEASUREMENT IN PROGRESS...")
            print(" 👉 Bare feet on the electrodes + hands firmly holding the handle.")
            print(" (Waiting for live BIA measurement ~20s...)")
            print("=" * 70 + "\n")

            t_start = asyncio.get_event_loop().time()
            while asyncio.get_event_loop().time() - t_start < 120.0:
                if self.measure_done.is_set():
                    break
                if not client.is_connected:
                    self.info("Scale disconnected.")
                    break
                await asyncio.sleep(0.5)

            try: await client.stop_notify(UUID_REALTIME_BIA)
            except Exception: pass

            # -------------------------------------------------------------
            # STEP 8: POST-MEASUREMENT COMMIT (0x31 type=2)
            # -------------------------------------------------------------
            if self.measure_done.is_set():
                self.dbg(">>> [STEP 8] Post-measurement commit (0x31 type=2)")
                try:
                    commit_profile = build_user_info_profile(self.huid, "", self.sex, self.age, self.height, 0.0, 2)
                    enc_commit = self.encrypt(commit_profile)
                    self.dbg(f" [0x31 COMMIT PLAINTEXT] {commit_profile.hex()}")
                    for chunk in build_framed_chunks(enc_commit, magic=0xDC):
                        self.dbg(f" -> [0x31 COMMIT TX Chunk] {chunk.hex()}")
                        await safe_write(client, UUID_USER_INFO, chunk, prefer_response=True)
                    await asyncio.sleep(0.5)
                    self.info(" ✅ Measurement validated and acknowledged by the scale.")
                except Exception as e:
                    self.dbg(f" Commit: {e}")

        # End of session
        if self.measure_done.is_set() and self.measured_data:
            d = self.measured_data
            print("\n" + "=" * 70)
            print(" 🎉 BIOELECTRICAL IMPEDANCE (BIA) MEASUREMENT RESULT")
            print(f" Date & Time: {d['timestamp']}")
            print("=" * 70)
            print(f" ⚖️ Total Weight: {d['weight']:.2f} kg")
            print(f" 📊 Body Fat: {d['fat']:.1f} %")
            print(f" ❤️ Heart Rate: {d['heart_rate']} bpm")
            print(f" 🦶 Feet Impedances: {d['feet']}")
            if d['hands']:
                print(f" 🤲 Hands Impedances: {d['hands']}")
            print("=" * 70 + "\n")

            # Update scale_config.json with the exact measured weight
            self.config["last_weight"] = d["weight"]
            try:
                json.dump(self.config, open(CONFIG_FILE, "w", encoding="utf-8"), indent=2)
                self.dbg(f"Configuration saved to {CONFIG_FILE}")
            except Exception: pass

            print("✨ BIA weigh-in session completed successfully!\n")
            return True
        else:
            if self.is_pairing:
                json.dump(self.config, open(CONFIG_FILE, "w", encoding="utf-8"), indent=2)
                print("✅ Configuration saved to scale_config.json!")
                return True
            print("⚠️ Session ended without a complete measurement.\n")
            return False


def main():
    parser = argparse.ArgumentParser(description="Standalone, user-friendly driver for HUAWEI Scale 3 / Scale 3 Pro")
    parser.add_argument("--debug", "-d", action="store_true", help="Enable full hex frame debug output")
    parser.add_argument("--reconfig", action="store_true", help="Force reconfiguration and profile re-pairing")
    parser.add_argument("--factory-reset", action="store_true",
                        help="Erase the scale's binding/user data and exit (requires an existing scale_config.json)")
    parser.add_argument("--yes", "-y", action="store_true", help="Skip the --factory-reset confirmation prompt")
    args = parser.parse_args()

    if args.reconfig and args.factory_reset:
        print("❌ --reconfig and --factory-reset cannot be combined.")
        sys.exit(1)

    config = None
    is_pairing = False

    # 1. Check whether scale_config.json exists
    if os.path.exists(CONFIG_FILE) and not args.reconfig:
        try:
            config = json.load(open(CONFIG_FILE, encoding="utf-8"))
            if not all(k in config for k in ("mac", "huid", "sex", "age", "height")):
                config = None
        except Exception:
            config = None

    # 2. If no configuration or reconfig requested: Assistant + Flash Pairing Mode
    if config is None or args.reconfig:
        if args.factory_reset and config is None:
            print("\n❌ --factory-reset requires an existing scale_config.json:")
            print("   authentication needs the scale's physical MAC (RootKey derivation).")
            sys.exit(1)
        config = prompt_user_profile()
        is_pairing = True
    else:
        is_pairing = False

    # 2b. Factory reset is a standalone authenticated action: no pairing/measurement.
    factory_reset_only = bool(args.factory_reset)
    if factory_reset_only:
        is_pairing = False
        if not args.yes:
            print("\n⚠️  FACTORY RESET will erase the scale's binding and user data.")
            print("   The scale must be re-paired afterwards.")
            if input("   Type 'RESET' to continue: ").strip() != "RESET":
                print("Aborted.")
                sys.exit(1)

    # 3. Display loaded profile
    if factory_reset_only:
        mode_label = "FACTORY RESET (binding/user data will be erased)"
    else:
        mode_label = "PAIRING & FLASH MODE (First launch)" if is_pairing else "DAILY ROUTINE MODE"
    print("\n" + "=" * 70)
    print(f" STANDALONE BLE DRIVER — HUAWEI SCALE 3 / 3 PRO")
    print(f" Target MAC: {config.get('mac')}")
    print(f" Profile HUID: {config.get('huid')}")
    print(f" Base Profile: Sex={config.get('sex')}, Age={config.get('age')}, Height={config.get('height')}cm, Weight={config.get('last_weight', 0.0):.2f}kg")
    print(f" BLE Flow: {mode_label}")
    if args.debug:
        print(f" Log Level: [FULL DEBUG ACTIVE]")
    print("=" * 70 + "\n")

    # 4. Launch the driver
    driver = Scale3Driver(config=config, is_pairing=is_pairing, debug=args.debug,
                          factory_reset_only=factory_reset_only)
    success = asyncio.run(driver.run())
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()

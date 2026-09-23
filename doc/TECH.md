# Reverse-Engineering Protocol Documentation: HUAWEI Scale 3 / Scale 3 Pro (BLE)

> Community interoperability notes for driving a **user-owned** HUAWEI Scale 3 /
> Scale 3 Pro over BLE without the vendor app. Intended for the openScale project.
> All identifiers below are placeholders — replace with your own device values.

---

## 1. Executive Summary

Complete, anonymized technical specification for communicating autonomously over
Bluetooth Low Energy (BLE) with the **HUAWEI Scale 3 / Scale 3 Pro**
(models HEM-B19 / HaigeBLE / M00F7).

Covers:
- **Mode 1 — Association / Initial Flash Pairing** (virgin scale, random HUID, flash user registration, **live validation weigh-in**).
- **Mode 2 — Routine Measurement** (fast daily BIA using a saved profile).
- Cryptographic handshake (`RootKey`, mutual `tokenA`/`tokenB`, session `WorkKey`, AES-128-CTR).
- Huawei framing protocol (CRC-16 CCITT, multi-packet sequencing).
- GATT map, profile payload (`0x31`), BIA telemetry decoding (`0x97`).
- **Response status codes (0 / 1 / 2)** — critical for understanding pairing behaviour.
- macOS CoreBluetooth caveat (UUID vs physical MAC).

---

## 2. Cryptographic Architecture

Sensitive characteristics are AES-128-CTR encrypted with a per-session `WorkKey`.

```
   Client / Host                                   HUAWEI Scale 3 / Pro
   =============                                   ====================
   0x21 Auth Req  ------------ "db0300c140" ----------------->
                  <----------- randA (16B) --------------------  0x21 Notify
   0x25 Auth Tok  --- randB (16B) + tokenB (32B) ------------->
                  <----------- tokenA (32B) -------------------  0x25 Notify
   0x29 WorkKey   --- IV (16B) + AES-CTR(RootKey, WorkKey) --->
                  <----------- Status (Code 0) ----------------  0x29 Notify
```

> **Note on Code 0 at 0x29:** a `Code 0` here means the frame was received and the
> WorkKey installed for transport — it does **not** by itself mean the client is
> authenticated. Authentication is proven by a correct `tokenB` (section 2.3). A
> wrong `tokenB` still yields `Code 0` on 0x29 but later `Code 2` on 0x31.

### 2.1 Static Constants
```python
CAK    = bytes.fromhex("90B96ECA297EF78717E66E491084D3F8")
WB1033 = bytes.fromhex("CA4946D061C9FE534F6044F930EBB69B")
WB2033 = bytes.fromhex("FBCE6E2B4BAF80ED969BA26B4A4B9325")
```

### 2.2 RootKey Derivation
Derived deterministically from the scale's **true physical MAC** (encrypts the
WorkKey exchange on 0x29):

```python
import hashlib
def derive_root_key(mac_address: str) -> bytes:
    mac_clean = (mac_address.replace(":", "") + "0000").upper().encode('utf-8')
    b4 = bytes(((WB1033[i] << 4) ^ WB2033[i]) & 0xFF for i in range(16))
    bE = hashlib.sha256(b4).digest()[:16]
    b5 = bytes(((bE[i] >> 6) ^ mac_clean[i]) & 0xFF for i in range(16))
    return hashlib.sha256(b5).digest()[:16]
```

> [!IMPORTANT]
> **macOS CoreBluetooth:** macOS hides the physical MAC and exposes a random UUID
> (e.g. `XXXXXXXX-XXXX-...`). Do **NOT** feed that UUID to `derive_root_key()`.
> Provide the physical MAC printed on the scale label. Linux/Windows expose the
> real MAC via BlueZ/WinRT directly. Deriving the RootKey from the wrong identifier
> produces a valid-length but wrong key: the handshake appears to work, yet the
> BIA frame decrypts to garbage (impossible weights, corrupt dates).

### 2.3 Mutual Authentication Tokens (`tokenB` / `tokenA`)
Double HMAC-SHA256 over `randAB = randA || randB`. **Key/data order matters** — the
CAK-derived value is the HMAC *key*, `randAB` is the *data* (a common pitfall is to
swap them):

```
H1     = HMAC-SHA256(key = CAK + b"1123", data = randA || randB)
tokenB = HMAC-SHA256(key = H1,            data = randA || randB)

H2     = HMAC-SHA256(key = CAK + b"9856", data = randA || randB)
tokenA = HMAC-SHA256(key = H2,            data = randA || randB)   # scale's reply
```

```python
import hmac, hashlib
def generate_token_b(randA: bytes, randB: bytes) -> bytes:
    randAB = randA + randB
    h1 = hmac.new(CAK + b"1123", randAB, hashlib.sha256).digest()
    return hmac.new(h1, randAB, hashlib.sha256).digest()
```

Verified byte-identical against real app captures. An incorrect `tokenB` is the
classic cause of a persistent `Code 2` on 0x31 despite a correct profile.

### 2.4 Payload Encryption (`AES-128-CTR`)
Protected characteristics (`0x31`, `0x2D`, `0x97`): `[ 16B random IV ] + AES-CTR(WorkKey, IV, body)`.

---

## 3. Framing & Transport Protocol

```
+-----------+-------------+--------------------+-----------------------+-----------------+
| Magic (1B)| Length (1B) | Frame Sequence (1B)| Payload Data (0..15B) | CRC-16 (2B, LE) |
+-----------+-------------+--------------------+-----------------------+-----------------+
```
- **Magic:** `0xDB` cleartext Host→Scale · `0xDC` encrypted Host→Scale · `0xBD` cleartext Scale→Host · `0xCD` encrypted Scale→Host.
- **Length:** `len(payload) + 3`
- **Frame Sequence:** `((total_frames - 1) << 4) | frame_index`
- **CRC-16:** CCITT (lookup table), over all preceding frame bytes, little-endian.

### 3.1 CRC-16 Lookup Table
```python
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
```

---

## 4. GATT Characteristics Map

Address characteristics **by UUID**, not by handle: handle numbers are renumbered by
the OS/stack (notably macOS CoreBluetooth), while UUIDs are stable.

| UUID | Function | Properties | Encryption |
| :--- | :--- | :--- | :--- |
| `02b2a08e-f8b0-4047-b1fd-f4e0efeee679` | 0x21 Auth Request (`randA`) | Write / Notify | Clear |
| `32330a04-15d9-421a-91c5-2a2d5c7525c9` | 0x25 Auth Tokens (`randB`+`tokenB`) | Write / Notify | Clear |
| `a3d330f8-b84f-4f48-a78c-f8d1e33b597a` | 0x29 Session WorkKey | Write / Notify | `AES(RootKey)` |
| `4338c65e-ed8e-4085-bbea-a25e33ca6b54` | 0x45 Binding Mode (write `0x01` to arm) | Write / Notify | Clear |
| `42596cbe-d291-4da3-8ca6-d1ae5d1c9174` | 0x2D HUID Flash Registration / tare query | Write / Notify | `AES(WorkKey)` |
| `8cc61d7d-66c0-4802-89c3-38c5a163592e` | 0x31 User Profile & Commit | Write / Notify | `AES(WorkKey)` |
| `00002a2b-0000-1000-8000-00805f9b34fb` | 0x52 Time Sync (standard GATT) | Write / Notify | Clear |
| `11872f15-a91d-49da-ac89-5107284f3425` | 0x5A Serial Number Info | Write / Notify | Clear |
| `bfc36f6e-4150-4a4b-9052-3d359e52962e` | 0x62 Device Config State (returns 1) | Write / Notify | Clear |
| `426f058d-8211-413e-8320-397a890a08bf` | 0x7A Hardware Model (e.g. `M00F7`) | Write / Notify | Clear |
| `0212f42a-5f19-4bc1-ba52-d7ec7ccb71a4` | 0x8F Offline History | Write / Notify | Clear |
| `46797c17-d639-488d-9476-4789e8472878` | 0x97 Real-Time BIA Stream (38B) | Write / Notify | `AES(WorkKey)` |
| `0000fe01-0000-1000-8000-00805f9b34fb` | 0xD7 Capabilities (write-without-response) | Write No Resp | Clear |
| `0000fe02-0000-1000-8000-00805f9b34fb` | 0xDA Capabilities Response | Notify | Clear |
| `ba216311-1787-472b-bef6-3eb29e62293e` | Status Sentinel / Event notifications | Notify | Clear |

---

## 5. Response Status Codes (observed)

Short scale replies have the form `bd 04 00 XX ...` where `XX` is the status:

| Code | Meaning | When seen |
| :--- | :--- | :--- |
| **0** | Success / user recognised | 0x29, 0x52, and 0x31 for a **known** user, or a **new** user after a valid weigh-in |
| **1** | Accepted-but-incomplete: profile received but **no validation weigh-in** captured yet (the scale has no reference weight for this user) | 0x31 during association if the user did not stand on the scale during 0x2D; also the constant reply of 0x62 |
| **2** | Rejected — **authentication failure** (wrong `tokenB`) or unknown/invalid user | 0x31 whenever `tokenB` is incorrect |

> These are empirical, from one Scale 3 Pro unit. Treat as a working model, not a spec.

---

## 6. Flow Sequences

### Common Handshake (both modes)
```
1. Subscribe to Status Sentinel + FE02 notifications
2. 0x21: write "db0300c140" -> receive 16B randA
3. 0x25: send randB + tokenB   -> receive tokenA
4. 0x29: send IV + AES-CTR(RootKey, WorkKey) -> Code 0
5. 0xD7: write "5a000500013701001ca9" (write-without-response)
```

### Mode 1 — Initial Flash Pairing (virgin scale / new HUID)
```
6.  0x45: write 0x01 (arm binding)
7.  0x2D: write AES-CTR(WorkKey, HUID[30B])
8.  >>> USER STANDS ON THE SCALE NOW <<<   (feet bare, hands on handle)
        The scale takes a reference weigh-in and, once stable, replies on 0x2D:
        decrypted = [status=0][tare_weight uint16_le /100][padding]
        Capture that tare_weight — it is what makes step 11 return Code 0.
9.  0x52: time sync
10. 0x5A (SN), 0x7A (model), 0x62 (config) queries
11. 0x31: user profile (type=0), Estimated Weight = tare_weight from step 8  -> Code 0
12. 0x97: arm BIA stream
13. Receive 38-byte BIA stream (magic 0xCD), decrypt with WorkKey
14. 0x31: commit (type=2)
15. Persist HUID (+ optional weight) locally for Mode 2
```

> **Critical:** the reference weigh-in in step 8 is not passive. If no one is on the
> scale between 0x2D and 0x31, the scale has no reference weight and 0x31 returns
> **Code 1** instead of Code 0. There is **no** "disarm 0x45 = 0x00" step in the
> observed app traffic; do not send one.

### Mode 2 — Routine Measurement (known HUID already flashed)
```
6.  0x52: time sync
7.  0x31: user profile (type=0)   -> Code 0 (user recognised, no weigh-in needed)
8.  0x7A (model), 0x8F (history) queries
9.  0x31: profile refresh (type=0)
10. 0x97: arm BIA stream
11. User stands on the scale
12. Receive 38-byte BIA stream (magic 0xCD)
13. 0x31: commit (type=2)
```

---

## 7. Packet Specifications

### 7.1 User Profile (`0x31`, 69 bytes cleartext)
| Offset | Len | Format | Field |
| :--- | :--- | :--- | :--- |
| 0 | 30 | ASCII + `\x00` | HUID (numeric account/user id) |
| 30 | 32 | ASCII + `\x00` | UID (optional secondary uuid; zeroed for type=0) |
| 62 | 1 | uint8 | Sex (1=male, 0=female) |
| 63 | 1 | uint8 | Age (years) |
| 64 | 2 | uint16_le | Height (cm) |
| 66 | 2 | uint16_le | Weight ×100 (kg). Use the 0x2D tare weight during pairing. |
| 68 | 1 | uint8 | Type: 0 = profile/measurement, 2 = post-measurement commit |

### 7.2 Real-Time BIA Stream (`0x97`, 38 bytes decrypted)
| Offset | Format | Unit | Field |
| :--- | :--- | :--- | :--- |
| 0..2 | uint16_le | raw/100 kg | Total Weight |
| 2..4 | uint16_le | raw/10 % | Body Fat % |
| 4..6 | uint16_le | YYYY | Year |
| 6 | uint8 | 1..12 | Month |
| 7 | uint8 | 1..31 | Day |
| 8 | uint8 | 0..23 | Hour |
| 9 | uint8 | 0..59 | Minute |
| 10 | uint8 | 0..59 | Second |
| 11 | uint8 | 1..7 | Day of week / reserved |
| 12..24 | 6 × uint16_le | raw | Feet bio-impedances (6 paths) |
| 24..26 | uint16_le | BPM | Heart Rate |
| 26..38 | 6 × uint16_le | raw | Hands bio-impedances (6 paths) |

> Impedance values are raw counts; converting to Ω or to derived metrics (muscle,
> water, etc.) requires the vendor's BIA model, which is out of scope here.

---

## 8. Reference Implementation

`scale3.py` — standalone Python (bleak + cryptography), no vendor account required.
- **First launch:** prompts for physical MAC, sex, age, height; generates a random
  HUID; runs Mode 1 (with the live validation weigh-in); saves a local config.
- **Subsequent launches:** Mode 2 fast BIA read (~25 s).
- Flags: `--debug` full packet trace; `--reconfig` reset profile.

Notes for contributors:
- Replace all placeholder identifiers with your own; never commit real MAC/HUID or
  raw `--debug` logs (they contain your MAC and HUID in clear).
- Findings are from a single unit on macOS; Scale 3 vs 3 Pro and firmware revisions
  may differ. Corrections welcome.

# ProxyVault — Secure Networking Suite

**Your Wire. Your Rules. No Compromise.**

Lightweight modular tunneling client for Android.

- SSH Direct
- SlowDNS evasion
- Encrypted `.MR` cloud config sync
- Payload / SNI / buffer controls
- No analytics. No ads. Source-transparent.

## Download APK

1. Open **Actions**
2. Latest green **Build APK** run
3. Artifact **ProxyVault-v2-Debug-APK**
4. Unzip → install

Or: **Actions → Build APK → Run workflow**

## Features

| Module | Detail |
|--------|--------|
| SSH Direct | Native TCP probe + keep-alive |
| SlowDNS | DNS-resolver tunnel path |
| Advanced | Custom payload + SNI |
| .MR Export/Import | Scrypt N=2^18 + AES-256-GCM + HMAC-SHA256 |
| Tools | Check IP, day/night theme |

## Profiles

Create tunnels under **Profiles**:
- Host / port / user / password / private key
- Local SOCKS port (default 1080)
- Buffer, compression, UDP forward, keep-alive
- HTTP ping URL for liveness

## .MR Security

```
Scrypt (N=262144, r=8, p=1) → 256-bit key
AES-256-GCM payload
HMAC-SHA256 over entire file
Magic string PROXYVAULT_MR_V2
```

Wrong password or bit-flip → hard reject. No partial load.

## Build locally

```bash
git clone https://github.com/yosefbatru-cmd/proxyvault-app.git
cd proxyvault-app
# Android Studio Open, or:
gradle assembleDebug
```

## Version

**2.0.0** — Secure Networking Suite rebuild

ProxyVault. Built sharp. Delivered clean.

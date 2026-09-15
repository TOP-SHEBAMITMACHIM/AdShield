# AdShield — Android ad blocker (browser + system-wide)

A no-root Android ad blocker that filters every DNS lookup on the device through a local
`VpnService`, ships a built-in browser with request-level and cosmetic ad blocking, and can write
the merged blocklists straight into the system hosts file on rooted devices.

The UI is available in English and Hebrew (RTL).

```
┌───────────────────────────── AdShield ──────────────────────────────┐
│ Dashboard   live counters, 7-day chart, top domains, pause timers    │
│ Browser     request blocking + cosmetic filters + popup blocking     │
│ Apps        per-app exclusion (banking/VPN apps keep working)        │
│ Filters     blocklists (URL/file/bundled), whitelist, blacklist      │
│ Settings    encrypted DNS, bypass guards, root hosts mode, backup    │
└──────────────────────────────────────────────────────────────────────┘
```

## How blocking works

**1. System-wide DNS firewall (no root, default)**

The app opens a `VpnService` tunnel that routes only:

* its own in-tunnel resolver address (`10.111.222.3` / `fd00:1:2:3::3`), and
* the addresses of well known public resolvers (`1.1.1.1`, `8.8.8.8`, `9.9.9.9`, OpenDNS,
  AdGuard, NextDNS, Quad9, Yandex, … IPv4 *and* IPv6).

Every DNS query that lands in the tunnel is parsed (IPv4 and IPv6, UDP 53), matched against the
blocklists with suffix matching (`doubleclick.net` also blocks `ads.doubleclick.net`) and answered
with `NXDOMAIN` when blocked. Allowed queries are relayed to the configured upstream resolver —
plain UDP or DNS over HTTPS (RFC 8484) — and written back through the tunnel.

Because plain web/app traffic never enters the tunnel, battery use stays low, and because the
resolver addresses are hijacked:

* apps that hardcode `8.8.8.8` still get filtered,
* apps that try DNS over TLS/HTTPS *to those addresses* get a `RST`, so they fall back to the
  resolver the system hands out — which is ours,
* IPv6 resolver bypasses are covered as well.

Normal traffic (browsing, banking, streaming) keeps using the device network untouched.

**2. Built-in browser (request-level + cosmetic)**

The bundled WebView browser blocks *before* a request leaves the device:

* every host on your blocklists returns an empty response (or a block page for the main frame),
* strict mode also kills known ad/tracker URL patterns (`/pagead/`, `/adserver`, `doubleclick`,
  `/prebid`, `gtag/js`, …),
* an optional aggressive mode blocks all third-party requests,
* cosmetic filtering hides leftover ad slots, sponsored widgets and cookie walls through injected
  CSS plus a `MutationObserver` sweep,
* popups (`window.open`) are disabled, third-party cookies are off, HTTP is upgraded to HTTPS,
  and "allow ads on this site" adds the current host to the whitelist in one tap.

**3. Optional root mode (fully hermetic)**

On a rooted device AdShield can merge every enabled list into the system hosts file
(`0.0.0.0 domain` **and** `:: domain`, so IPv6 lookups are blocked too) using a bind mount from
`/data/local/tmp` or a direct write after remounting. This blocks ads even in apps that use their
own DNS or encrypted DNS, and it keeps working while the VPN is off. A backup of the original
hosts file is kept and can be restored from the app.

## Features

* Live dashboard: blocked today / total, queries today, active rule count, 7-day bar chart, live
  activity feed, top blocked domains — updated in real time without restarting the app.
* Blocklists: bundled starter list (upgradeable to the full StevenBlack hosts list in one tap), add
  any list by URL, import a **hosts file**, a plain domain list, an `http(s)://…` URL list or
  `||domain^` Adblock-style syntax, enable/disable each list, per-list domain counts.
* Daily automatic list refresh through WorkManager (Wi-Fi only, optional).
* Whitelist and blacklist with URL/wildcard normalisation; whitelist always wins.
* Per-app filtering: exclude apps (banking, VPN clients) — the tunnel is rebuilt instantly.
* Pause 5/15/60 minutes from the app or from the notification; resumes automatically.
* Persistent notification with live counter and quick actions.
* Quick Settings tile to toggle protection.
* Start on boot, restart after app update.
* Upstream resolver choice: system DNS, Cloudflare, Google, Quad9, AdGuard, custom, or DoH
  endpoints (Cloudflare / Google / AdGuard / custom URL) with UDP fallback.
* Bypass guards: hijack hardcoded resolvers, block known encrypted-DNS provider hostnames.
* Statistics persisted across restarts (daily buckets, top domains, recent activity).
* Export/import settings, rules, exclusions and custom list URLs as JSON.
* Material 3 UI with dynamic colour (Android 12+), light/dark/system themes, Hebrew + English.

## Build

Requirements: **JDK 17**, **Android SDK with compileSdk 35 and build-tools 35.0.0**, Gradle 8.9.

### This workspace already has the toolchain

A complete toolchain was installed inside the project under `.toolchain/` (gitignored), so there is
nothing left to download here:

| Path | Contents |
| --- | --- |
| `.toolchain/jdk` | Temurin JDK 17.0.20.1 |
| `.toolchain/gradle-8.9` | Gradle 8.9 |
| `.toolchain/android-sdk` | cmdline-tools 12.0, platform-tools, `platforms;android-35`, `build-tools;35.0.0` |
| `.toolchain/gradle-home` | Gradle dependency cache (kept in the workspace, not in `~/.gradle`) |

```bash
tools/build-windows.sh                          # debug APK
tools/build-windows.sh :app:assembleRelease     # release APK (R8 + resource shrinking)
tools/build-windows.sh :app:testDebugUnitTest :app:lintDebug
```

The helper exists because this project path contains Hebrew characters and spaces: `cmd.exe`
encodes its arguments with the Windows ANSI code page and mangles them, so the script reaches the
tools through a short ASCII `S:` SUBST mapping of the same folder — nothing is copied, moved or
renamed. It creates the mapping when it is missing and is safe to re-run. `local.properties` points
at the same mapped SDK and is regenerated by Android Studio when the project is opened there.

### Any other machine (Linux, macOS, plain Windows path)

```bash
gradle wrapper --gradle-version 8.9   # the wrapper JAR is binary, so it is not committed here
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The app needs no account, no server and no analytics. On first start, switch **Protection** on and
accept the Android VPN consent dialog. Android shows its own VPN key icon in the status bar next to
the AdShield notification — that is normal for any local VPN firewall.

## What was verified here

| Check | Result |
| --- | --- |
| `:app:assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk`, 16 MB, `com.adshield.app`, minSdk 24, target 35, VPN service + all permissions present |
| `:app:assembleRelease` | `app/build/outputs/apk/release/app-release-unsigned.apk`, **1.3 MB** with R8 + resource shrinking |
| `:app:testDebugUnitTest` | **27 tests, 0 failures** |
| `:app:lintDebug` | **0 errors**, 25 warnings |
| `python3 tools/verify_project.py` | all resource references resolve, XML valid, brackets balanced, 153 strings in both languages |

R8 keeps the classes the system instantiates by name — `AdVpnService`, `MainActivity`,
`AdShieldTileService`, `BootReceiver`, `DailyUpdateWorker` are unrenamed in
`app/build/outputs/mapping/release/mapping.txt` — while the rest of the code is obfuscated.

The unit tests cover the parts that decide whether blocking actually works:

* DNS query parsing (single question, long names, compression pointers, responses, empty queries),
* `NXDOMAIN` / `SERVFAIL` response construction and transaction-id matching,
* IPv4 and IPv6 UDP packet building, validating **both the IP header checksum and the UDP
  pseudo-header checksum** with an independent calculation,
* TCP RST generation (endpoint swap, flags, sequence/acknowledgement numbers),
* blocklist suffix matching, whitelist/blacklist precedence, case and trailing-dot handling,
* hosts-file, Adblock (`||domain^`), URL and bundled-list parsing, including the guarantee that
  `localhost` and raw addresses never become rules.

Lint earned its keep: it caught three genuine bugs that were then fixed — the missing
notification-permission check before `notify()`, `Process.waitFor(timeout, unit)` being API 26 while
the app supports 24, and the deprecated Quick Settings tile API on older devices. The fourth lint
error (`foregroundServiceType="systemExempted"` wanting an exact-alarm permission) is a static
false positive: the Android docs list *VPN apps configured with VpnService* as a qualifying
criterion for that type, so it is suppressed on that single service element with a comment.

**Not verified:** the app has not run on a device or emulator (none exists in this environment), so
the VPN consent dialog, tunnel establishment and real-world blocking have not been exercised at
runtime. Protocol logic is unit tested; everything else is compile-, lint- and package-level
verification.

### Static checks without any SDK

```bash
python3 tools/verify_project.py
```

## Permissions

| Permission | Why |
| --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE` | relay allowed DNS queries, download blocklists, read the current network's DNS |
| `BIND_VPN_SERVICE` (declared by the system) | the local filter tunnel |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` | keep filtering alive with a visible notification |
| `POST_NOTIFICATIONS` | live counter notification (Android 13+) |
| `RECEIVE_BOOT_COMPLETED` | optional start on boot |
| `QUERY_ALL_PACKAGES` | list installed apps for per-app exclusions |

No data ever leaves the device: blocklists are downloaded, lookups are resolved anonymously,
statistics live in the app's private storage.

## Limitations (please read)

* Filtering happens at DNS level. An app that ships its own resolver *and* talks to an address not
  in the hijack list could still resolve names; the "block encrypted DNS providers" switch closes
  the common cases, and the root hosts mode closes all of them.
* Blocking a domain breaks anything that shares it. If a site or app misbehaves, add its domain to
  the whitelist (the browser has a one-tap action for that).
* `connect.facebook.net` is in the starter list; some apps' "log in with Facebook" flows need it
  whitelisted.
* The system hosts file written in root mode is lost after a reboot (the bind mount is not
  persistent) — write it again from Settings, or keep using VPN filtering.
* This is a tool for your own device. It is not meant to defeat filters, parental controls or
  network policies that are enforced on equipment you do not own.

## Project layout

```
app/src/main/java/com/adshield/app/
  AdShieldApp.kt              application, notification channel, worker scheduling
  MainActivity.kt             Compose host + VPN consent flow
  core/                       EngineState (live UI state), AppGraph (DI), Format helpers
  data/                       SettingsStore, StatsStore, RulesStore, BlocklistRepository,
                              AppsRepository, BackupManager
  filter/                     FilterEngine (suffix matching), DohHosts (bypass guard list)
  vpn/                        AdVpnService (tunnel + DNS firewall), Net (IPv4/IPv6/UDP/TCP),
                              DnsMessage, DnsUpstream (UDP + DoH), ResolverIps
  browser/                    AdBlockWebViewClient (request blocking), CosmeticFilter (CSS+JS)
  ui/                         Dashboard, Browser, Apps, Filters, Settings, theme, shared widgets
  root/                       RootHostsManager (optional hosts-file mode)
  tile/, boot/, work/         Quick Settings tile, boot receiver, daily update worker
app/src/test/java/com/adshield/app/   27 unit tests for packets, DNS, filters and list parsing
app/src/main/assets/default_blocklist.txt    bundled starter blocklist
app/src/main/res/values-iw/                  Hebrew translation
tools/verify_project.py                      static project checker (no SDK needed)
tools/build-windows.sh                       one-command build using .toolchain/
.toolchain/                                  local JDK + Gradle + Android SDK (gitignored)
```

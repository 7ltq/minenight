# MineNight AntiCheat integration (preview)

The embedded engine is built from TotemGuard at commit 48655b552acd33db01b0f60056fa2e96abacc5a5, not a reimplementation. Its GPL-3.0-or-later headers remain intact. The build archives corresponding modified upstream source, the integration script, and the MineNight source. Do not distribute the combined plugin without meeting the GPL requirements for the combined work, including providing corresponding source and applicable license notices.

## Build

Use JDK 25, Git, Maven and Python 3. Run build.bat on Windows or `mvn -B clean package` on Linux. The Maven package phase now builds the engine automatically. Deploy only target/MineNightAllInOne.jar. The intermediate core JAR under target/internal is not deployable as the complete plugin. CI verifies that the final artifact contains the engine and integrity metadata.

Install a compatible PacketEvents plugin (upstream compiles against 2.14.0-SNAPSHOT; minimum declared by the engine is 2.11.1). Do not install the standalone TotemGuard plugin alongside the embedded engine.

## Server

Configuration lives under plugins/MineNightAllInOne/anticheat. The embedded engine retains the upstream AutoTotem, Inventory, Protocol, Tick, ManualTotem and mod checks. Commands: /alerts, /sus <player>, /totemcheck <player>, /mnac. Staff permission: minenight.anticheat.staff. Upstream internal permission identifiers remain for compatibility. The old monitor is started only if the embedded engine does not initialize; legacy /totemmonitor is otherwise not available.

All check punishments start disabled for shadow testing on the practice server. When verified, enable punishable per check in checks.yml; configured ban commands route to MineNight moderation for seven days. Mod detections default to alerts only. Do not treat a single heuristic flag as proof. Database/Redis remain optional; without a database, history is limited to available session data. Upstream update checks, metrics and shutdown command are disabled for embedding.

## Acceptance tests

- Combined JAR builds and includes one MineNight plugin descriptor; integrity fingerprint verifies.
- Startup on Paper 1.21.11 with PacketEvents, no standalone TotemGuard, no duplicate legacy monitor.
- Legitimate crystal PvP, kit GUI, teleport, spawn, spectator, totem interactions, disconnect/rejoin and server shutdown do not cause false punishments.
- Verify /alerts, /sus, /totemcheck, staff permissions, bypass behavior and check-specific messages.
- Test a known authorized test case for each check category before enabling punishments.
- Verify MineNight ban screen, seven-day expiry, unban and log history.
- Confirm anti-cheat startup failure leaves other modules running; test shutdown and restart.

The original two-step CI build succeeded. The updated automatic Maven build and Paper runtime still require verification.

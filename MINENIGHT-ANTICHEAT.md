# MineNightAntiCheat (standalone preview)

This independently built plugin uses the original TotemGuard Paper engine pinned to revision 48655b552acd33db01b0f60056fa2e96abacc5a5. Original checks and GPL-3.0-or-later notices are retained. The build supplies the modified upstream corresponding source and license alongside the JAR.

Install MineNightAllInOne, a compatible PacketEvents build and MineNightAntiCheat.jar. Do not install unmodified TotemGuard alongside it or merge the embedded-engine proposal. The MineNightAllInOne core build is unchanged.

Use JDK 25, Python 3 and Git to run `python tools/build_minenight_anticheat.py`. Output: target/anticheat/MineNightAntiCheat.jar and MineNightAntiCheat-source.zip.

The plugin requires MineNightAllInOne and PacketEvents, and checks for the namespaced MineNight ban command before starting. The default ban template is `[BAN] minenightallinone:ban %tg_player% cheating 7d`. Mod-detection bans use the same command. Kicks remain separate from bans. The engine runs with alerts-only sanctions by default; enable punishable per check after staging verification. Detection and mitigation remain enabled according to upstream defaults. Do not enable blanket bans based on unverified heuristic results.

Commands: /mnac, /mnac reload, /alerts, /sus <player>, /mntotemcheck <player>. The existing AllInOne /totemcheck is not overwritten. Staff permission: minenight.anticheat.staff. Existing upstream permission IDs remain for compatibility. Data folder: plugins/MineNightAntiCheat.

To avoid duplicate monitoring, set enabled: false in plugins/MineNightAllInOne/totemmonitor/config.yml and restart. Test legitimate gameplay, all staff commands, sanction routing, ban duration, ban screen, unban, reconnect and restart on Paper 1.21.11. Optional database remains disabled by default.

No successful build or runtime test has yet been confirmed.

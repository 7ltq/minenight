import hashlib
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
UPSTREAM = ROOT / ".build-tools" / "totemguard-upstream"
REVISION = "48655b552acd33db01b0f60056fa2e96abacc5a5"
STAMP = "META-INF/totemguard/integrity.sha256"

def run(args, cwd=ROOT):
    subprocess.run([str(x) for x in args], cwd=cwd, check=True)

def patch(path, old, new):
    file = UPSTREAM / path
    text = file.read_text(encoding="utf-8")
    if text.count(old) != 1:
        raise RuntimeError(f"Unexpected upstream source: {path}: {old[:80]}")
    file.write_text(text.replace(old, new), encoding="utf-8")

def edit(path, transform):
    file = UPSTREAM / path
    file.write_text(transform(file.read_text(encoding="utf-8")), encoding="utf-8")

def configure():
    common = "common/src/main/"
    paper = "platforms/paper/src/main/java/com/deathmotion/totemguard/paper/TGPaperPlatform.java"
    descriptor = "platforms/paper/src/main/resources/plugin.yml"
    patch(descriptor, "name: TotemGuard", "name: MineNightAntiCheat")
    patch(descriptor, "  - packetevents\n", "  - packetevents\n  - MineNightAllInOne\n")
    patch(descriptor, "description: ${description}", "description: MineNight AntiCheat powered by TotemGuard")
    edit(descriptor, lambda s: s.rstrip() + """
commands:
  alerts:
    description: Toggle AntiCheat alerts.
    permission: minenight.anticheat.staff
  sus:
    description: View a player AntiCheat profile.
    usage: /sus <player>
    permission: minenight.anticheat.staff
  mntotemcheck:
    description: Start a manual AntiCheat totem check.
    usage: /mntotemcheck <player>
    permission: minenight.anticheat.staff
permissions:
  minenight.anticheat.staff:
    default: op
    children:
      totemguard.alerts: true
      totemguard.check: true
      totemguard.gui.profile: true
      totemguard.gui.history: true
      totemguard.gui.info: true
      totemguard.teleport: true
  totemguard.bypass:
    default: false
""")
    patch(common + "java/com/deathmotion/totemguard/common/TGPlatform.java", 'Logger.getLogger("TotemGuard")', 'Logger.getLogger("MineNightAntiCheat")')
    patch(common + "java/com/deathmotion/totemguard/common/TotemGuardLifecycle.java", 'new JarIntegrityChecker(p.getLogger(), "TotemGuard")', 'new JarIntegrityChecker(p.getLogger(), "MineNightAntiCheat")')
    patch(paper, 'metrics = new TGMetrics(plugin, TGPlatform.getBStatsId(), TGVersions.CURRENT::toDisplayString);', '')
    patch(common + "java/com/deathmotion/totemguard/common/commands/CommandManagerImpl.java", '        new ShutdownCommand().register(commandManager);', '')
    edit(common + "java/com/deathmotion/totemguard/common/commands/CommandManagerImpl.java", lambda s: re.sub(r'        if \(TGVersions\.CURRENT\.snapshot\(\)\) \{.*?\n        \}', '', s, count=1, flags=re.S))
    entry = "platforms/paper/src/main/java/com/deathmotion/totemguard/paper/TGPaper.java"
    patch(entry, "        tg.commonOnEnable();", """        if (Bukkit.getPluginCommand("minenightallinone:ban") == null) {
            getLogger().severe("MineNight moderation command not found; AntiCheat will not start.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        tg.commonOnEnable();""")
    patch(entry, "        if (!tg.isEnabled()) return;", """        if (!tg.isEnabled()) return;
        for (String name : java.util.List.of("alerts", "sus", "mntotemcheck")) {
            var command = java.util.Objects.requireNonNull(getCommand(name));
            command.setExecutor((sender, cmd, label, args) -> {
                if (!sender.hasPermission("minenight.anticheat.staff")) return true;
                if (name.equals("alerts") && args.length == 0) {
                    Bukkit.dispatchCommand(sender, "mnac alerts");
                    return true;
                }
                if (!name.equals("alerts") && args.length == 1 && args[0].matches("[A-Za-z0-9_]{1,16}")) {
                    Bukkit.dispatchCommand(sender, "mnac " + (name.equals("sus") ? "profile " : "check ") + args[0]);
                    return true;
                }
                return false;
            });
            command.setTabCompleter((sender, cmd, label, args) -> {
                if (!sender.hasPermission("minenight.anticheat.staff") || name.equals("alerts") || args.length != 1) return java.util.List.of();
                String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
                return Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !(sender instanceof org.bukkit.entity.Player viewer) || viewer.canSee(p))
                        .map(org.bukkit.entity.Player::getName)
                        .filter(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
                        .sorted(String.CASE_INSENSITIVE_ORDER).toList();
            });
        }""")
    config = common + "resources/config.yml"
    patch(config, "server: Default", "server: MineNight")
    patch(config, "  base: totemguard\n  aliases:\n    - tg", "  base: mnac\n  aliases: []")
    patch(config, '  command: "/tg teleport %tg_player%"', '  command: "/mnac teleport %tg_player%"')
    patch(config, "update-checker:\n  enabled: true", "update-checker:\n  enabled: false")
    patch(config, "ban-animation:\n  enabled: true", "ban-animation:\n  enabled: false")
    patch(config, "debug-modifier-kick:\n  enabled: true", "debug-modifier-kick:\n  enabled: false")
    checks = common + "resources/checks.yml"
    patch(checks, '[BAN] ban %tg_player% Unfair Advantage', '[BAN] minenightallinone:ban %tg_player% cheating 7d')
    edit(checks, lambda s: s.replace('[KICK] kick %tg_player% Packet Manipulation', '[KICK] minecraft:kick %tg_player% Packet Manipulation').replace('punishable: true', 'punishable: false'))
    mods = common + "resources/mods.yml"
    patch(mods, 'kick-command: "kick %tg_player% Unfair mods detected: %tg_mod_list%"', 'kick-command: "minecraft:kick %tg_player% Unfair mods detected: %tg_mod_list%"')
    patch(mods, 'ban-command: "ban %tg_player% Unfair mods detected: %tg_mod_list%"', 'ban-command: "minenightallinone:ban %tg_player% cheating 7d"')
    messages = common + "resources/messages.yml"
    patch(messages, 'prefix: "&#FBAF00&lTG &r&#6E6651»"', 'prefix: "&#6685E8&lMineNight &r&#8298C4»"')
    edit(messages, lambda s: re.sub(r'\bTotemGuard\b(?!\.)', 'MineNight AntiCheat', s).replace('&#FBAF00', '&#6685E8').replace('&#FEE067', '&#FFFFFF').replace('&#B8AC8F', '&#B7C7E8').replace('/tg ', '/mnac ').replace('/totemguard ', '/mnac '))
    edit(common + "java/com/deathmotion/totemguard/common/commands/CommandDefaults.java", lambda s: s.replace('The root command for TotemGuard.', 'MineNight AntiCheat commands.'))

def obtain_source():
    if UPSTREAM.exists():
        shutil.rmtree(UPSTREAM)
    UPSTREAM.parent.mkdir(parents=True, exist_ok=True)
    run(["git", "init", UPSTREAM])
    run(["git", "remote", "add", "origin", "https://github.com/Bram1903/TotemGuard.git"], UPSTREAM)
    run(["git", "fetch", "--depth", "1", "origin", REVISION], UPSTREAM)
    run(["git", "checkout", "--detach", "FETCH_HEAD"], UPSTREAM)
    actual = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=UPSTREAM, text=True).strip()
    if actual != REVISION:
        raise RuntimeError("Unexpected TotemGuard revision")
    configure()

def license_bytes():
    for name in ["LICENSE", "LICENSE.txt", "LICENSE.md", "COPYING"]:
        p = UPSTREAM / name
        if p.is_file():
            return p.read_bytes()
    with urllib.request.urlopen("https://www.gnu.org/licenses/gpl-3.0.txt", timeout=30) as response:
        data = response.read()
    if b"GNU GENERAL PUBLIC LICENSE" not in data:
        raise RuntimeError("GPL license text not found")
    return data

def pack(own, engine):
    with zipfile.ZipFile(engine) as dep:
        entries = {i.filename: dep.read(i) for i in dep.infolist() if not i.is_dir() and i.filename != STAMP}
    entries["META-INF/licenses/TotemGuard-GPL-3.0.txt"] = license_bytes()
    entries["META-INF/licenses/TotemGuard-source.txt"] = (
        f"TotemGuard by Bram and contributors, GPL-3.0-or-later.\n"
        f"Original source: https://github.com/Bram1903/TotemGuard/tree/{REVISION}\n"
        "Modified MineNight AntiCheat build; corresponding source accompanies the artifact.\n"
    ).encode()
    digest = hashlib.sha256()
    for name, data in sorted(entries.items()):
        key = name.encode()
        digest.update(struct.pack(">I", len(key)))
        digest.update(key)
        digest.update(struct.pack(">Q", len(data)))
        digest.update(data)
    entries[STAMP] = (digest.hexdigest() + "\n").encode()
    own.parent.mkdir(parents=True, exist_ok=True)
    temporary = own.with_suffix(".tmp")
    with zipfile.ZipFile(temporary, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as out:
        for name, data in entries.items():
            out.writestr(name, data)
    with zipfile.ZipFile(temporary) as out:
        if out.testzip() is not None:
            raise RuntimeError("JAR failed ZIP validation")
        if "com/deathmotion/totemguard/paper/TGPaperPlatform.class" not in out.namelist():
            raise RuntimeError("Missing Paper engine")
        if b"name: MineNightAntiCheat" not in out.read("plugin.yml"):
            raise RuntimeError("Invalid plugin descriptor")
    temporary.replace(own)

def source_archive():
    target = ROOT / "target" / "anticheat" / "MineNightAntiCheat-source.zip"
    target.parent.mkdir(parents=True, exist_ok=True)
    tracked = subprocess.check_output(["git", "ls-files", "-z"], cwd=UPSTREAM).decode().split("\0")
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as z:
        for name in tracked:
            if name and (UPSTREAM / name).is_file():
                z.write(UPSTREAM / name, name)
        z.writestr("MINENIGHT-UPSTREAM-REVISION.txt", REVISION)
        z.writestr("COPYING-GPL-3.0.txt", license_bytes())
        z.write(Path(__file__), "minenight-build-integration.py")

def main():
    own = ROOT / "target" / "anticheat" / "MineNightAntiCheat.jar"
    obtain_source()
    wrapper = UPSTREAM / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if os.name != "nt":
        wrapper.chmod(0o755)
    command = [str(wrapper), ":platforms:paper:shadowJar", "--no-configuration-cache", "--stacktrace"]
    if os.name == "nt":
        command = ["cmd", "/c"] + command
    run(command, UPSTREAM)
    jars = list((UPSTREAM / "build").glob("TotemGuard-Paper-*.jar"))
    if len(jars) != 1:
        raise RuntimeError(f"Expected one upstream Paper JAR, got {jars}")
    source_archive()
    pack(own, jars[0])
    print("MineNightAntiCheat.jar created with pinned TotemGuard checks.")

if __name__ == "__main__":
    main()

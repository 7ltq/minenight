package de.minenight.allinone;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

final class MineNightVoteListener implements Listener, CommandExecutor {
    private final MineNightAllInOne plugin;
    private final File file;
    private final YamlConfiguration data;
    private boolean active;

    MineNightVoteListener(MineNightAllInOne plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "votes.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    void register() {
        Plugin votifier = Bukkit.getPluginManager().getPlugin("Votifier");
        if (votifier == null) votifier = Bukkit.getPluginManager().getPlugin("NuVotifier");
        if (votifier == null || !votifier.isEnabled()) {
            plugin.getLogger().info("Voting disabled: no running Votifier instance was found.");
            return;
        }
        try {
            Class<?> rawType = Class.forName("com.vexsoftware.votifier.model.VotifierEvent", true, votifier.getClass().getClassLoader());
            Class<? extends Event> eventType = rawType.asSubclass(Event.class);
            Method getVote = rawType.getMethod("getVote");
            Bukkit.getPluginManager().registerEvent(eventType, this, EventPriority.MONITOR, (listener, event) -> {
                try {
                    Object vote = getVote.invoke(event);
                    if (vote == null) return;
                    Object name = vote.getClass().getMethod("getUsername").invoke(vote);
                    if (!(name instanceof String username) || !username.matches("[A-Za-z0-9_]{3,16}")) return;
                    String service = String.valueOf(vote.getClass().getMethod("getServiceName").invoke(vote));
                    String timestamp = String.valueOf(vote.getClass().getMethod("getTimeStamp").invoke(vote));
                    Runnable confirmed = () -> {
                        handleVote(username);
                        if (plugin.module("perms") instanceof de.minenightperms.MineNightPerms perms && perms.getApplicationBridge() != null) perms.getApplicationBridge().confirmedVote(username, service, timestamp);
                    };
                    if (Bukkit.isPrimaryThread()) confirmed.run();
                    else Bukkit.getScheduler().runTask(plugin, confirmed);
                } catch (ReflectiveOperationException error) {
                    plugin.getLogger().warning("Vote processing failed: " + error.getClass().getSimpleName());
                }
            }, plugin, true);
            active = true;
            plugin.getLogger().info("MineNight vote announcements and /votetotal enabled.");
        } catch (ReflectiveOperationException | LinkageError | IllegalArgumentException error) {
            plugin.getLogger().warning("Votifier listener unavailable: " + error.getClass().getSimpleName());
        }
    }

    private void handleVote(String username) {
        String key = "players." + username.toLowerCase(Locale.ROOT);
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        int total = data.getInt(key + ".total", 0);
        int daily = today.equals(data.getString(key + ".date")) ? data.getInt(key + ".today", 0) : 0;
        data.set(key + ".name", username);
        data.set(key + ".total", total + 1);
        data.set(key + ".date", today);
        data.set(key + ".today", daily + 1);
        try {
            data.save(file);
        } catch (IOException error) {
            plugin.getLogger().warning("Could not save vote counts: " + error.getClass().getSimpleName());
        }
        Bukkit.broadcast(Component.text("MineNight", NamedTextColor.AQUA)
                .append(Component.text(" » ", NamedTextColor.GRAY))
                .append(Component.text(username, NamedTextColor.AQUA))
                .append(Component.text(" voted for MineNight!", NamedTextColor.WHITE)));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("minenight.links.use")) {
            sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
            return true;
        }
        String username;
        if (args.length > 0) username = args[0];
        else if (sender instanceof Player player) username = player.getName();
        else {
            sender.sendMessage("Usage: /votetotal <player>");
            return true;
        }
        if (!username.matches("[A-Za-z0-9_]{3,16}")) {
            sender.sendMessage("Invalid player name.");
            return true;
        }
        String key = "players." + username.toLowerCase(Locale.ROOT);
        int total = data.getInt(key + ".total", 0);
        int today = LocalDate.now(ZoneOffset.UTC).toString().equals(data.getString(key + ".date"))
                ? data.getInt(key + ".today", 0) : 0;
        String displayName = data.getString(key + ".name", username);
        sender.sendMessage(Component.text("MineNight", NamedTextColor.AQUA)
                .append(Component.text(" » ", NamedTextColor.GRAY))
                .append(Component.text(displayName, NamedTextColor.AQUA))
                .append(Component.text(" has " + total + " confirmed votes (" + today + " today).", NamedTextColor.WHITE)));
        if (!active) sender.sendMessage(Component.text("Vote listener is inactive. Counts may be outdated.", NamedTextColor.GRAY));
        return true;
    }
}

package de.minenight.tpa;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class MineNightTPA extends de.minenight.allinone.MineNightModule implements CommandExecutor, Listener {
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final Map<Pair, Request> requests = new HashMap<>();
    private final Map<Pair, Long> cooldowns = new HashMap<>();
    private final Set<UUID> autoAccept = new HashSet<>();
    private File dataFile;
    private YamlConfiguration data;

    private record Pair(UUID sender, UUID receiver) { }
    private record Request(Pair pair, boolean here, long created) { }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        for (String value : data.getStringList("auto-accept")) {
            try {
                autoAccept.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (String name : List.of("tpa", "tpahere", "tpaccept", "tpadecline", "tpauto")) {
            PluginCommand command = getCommand(name);
            if (command != null) command.setExecutor(this);
        }
        Bukkit.getPluginManager().registerEvents(this, core());
        Bukkit.getScheduler().runTaskTimer(core(), this::expireRequests, 20L, 20L);
        getLogger().info("MineNightTPA enabled.");
    }

    @Override
    public void onDisable() {
        saveAutoAccept();
        requests.clear();
        cooldowns.clear();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        switch (command.getName().toLowerCase()) {
            case "tpa", "tpahere" -> sendRequest(player, args, command.getName().equalsIgnoreCase("tpahere"));
            case "tpaccept" -> answer(player, args, true);
            case "tpadecline" -> answer(player, args, false);
            case "tpauto" -> toggleAuto(player, args);
            default -> { return false; }
        }
        return true;
    }

    private void toggleAuto(Player player, String[] args) {
        if (args.length != 0) {
            send(player, "usage-auto");
            return;
        }
        if (autoAccept.remove(player.getUniqueId())) {
            send(player, "auto-disabled");
        } else {
            autoAccept.add(player.getUniqueId());
            send(player, "auto-enabled");
        }
        saveAutoAccept();
    }

    private void saveAutoAccept() {
        if (data == null) return;
        data.set("auto-accept", autoAccept.stream().map(UUID::toString).sorted().toList());
        try {
            data.save(dataFile);
        } catch (IOException ex) {
            getLogger().warning("Could not save auto-accept settings: " + ex.getMessage());
        }
    }

    public void cancelIncoming(UUID receiver, boolean here) {
        requests.entrySet().removeIf(entry -> entry.getKey().receiver().equals(receiver) && entry.getValue().here() == here);
    }

    private boolean accepts(Player receiver, boolean here) {
        if (core().module("settings") instanceof de.minenight.settings.MineNightSettings settings) {
            return here ? settings.allowsTpaHere(receiver.getUniqueId()) : settings.allowsTpa(receiver.getUniqueId());
        }
        return true;
    }

    private void sendRequest(Player sender, String[] args, boolean here) {
        if (args.length != 1) {
            send(sender, here ? "usage-tpahere" : "usage-tpa");
            return;
        }
        Player receiver = Bukkit.getPlayerExact(args[0]);
        if (receiver == null || !receiver.isOnline()) {
            send(sender, "not-online", "{player}", args[0]);
            return;
        }
        if (receiver.getUniqueId().equals(sender.getUniqueId())) {
            send(sender, "self-request");
            return;
        }
        if (!accepts(receiver, here)) {
            send(sender, here ? "receiver-disabled-tpahere" : "receiver-disabled-tpa", "{player}", receiver.getName());
            return;
        }
        expireRequests();
        Pair pair = new Pair(sender.getUniqueId(), receiver.getUniqueId());
        long now = System.currentTimeMillis();
        long cooldown = TimeUnit.SECONDS.toMillis(Math.max(0, getConfig().getLong("settings.send-cooldown-seconds", 20)));
        long last = cooldowns.getOrDefault(pair, 0L);
        if (cooldown > 0 && now - last < cooldown) {
            long seconds = (last + cooldown - now + 999) / 1000;
            send(sender, "cooldown", "{seconds}", Long.toString(seconds), "{player}", receiver.getName());
            return;
        }
        if (requests.containsKey(pair)) {
            send(sender, "already-pending", "{player}", receiver.getName());
            return;
        }
        cooldowns.put(pair, now);
        Request request = new Request(pair, here, now);
        if (autoAccept.contains(receiver.getUniqueId())) {
            send(sender, "auto-accepted-sender", "{player}", receiver.getName());
            executeTeleport(request, sender, receiver);
            return;
        }
        requests.put(pair, request);
        send(sender, here ? "sent-here" : "sent", "{player}", receiver.getName());
        send(receiver, here ? "received-here" : "received", "{player}", sender.getName());
        Component accept = mini.deserialize(getConfig().getString("messages.accept-button", "<green><bold>[ACCEPT]</bold></green>"))
                .clickEvent(ClickEvent.runCommand("/tpaccept " + sender.getName()))
                .hoverEvent(HoverEvent.showText(mini.deserialize("<gray>Click to accept the request")));
        Component decline = mini.deserialize(getConfig().getString("messages.decline-button", "<red><bold>[DECLINE]</bold></red>"))
                .clickEvent(ClickEvent.runCommand("/tpadecline " + sender.getName()))
                .hoverEvent(HoverEvent.showText(mini.deserialize("<gray>Click to decline the request")));
        receiver.sendMessage(accept.append(Component.space()).append(decline));
    }

    private void answer(Player receiver, String[] args, boolean accepted) {
        if (args.length > 1) {
            send(receiver, accepted ? "usage-accept" : "usage-decline");
            return;
        }
        expireRequests();
        Request selected = null;
        for (Request request : requests.values()) {
            if (!request.pair().receiver().equals(receiver.getUniqueId())) continue;
            Player requester = Bukkit.getPlayer(request.pair().sender());
            if (requester == null || !requester.isOnline()) continue;
            if (args.length == 1 && !requester.getName().equalsIgnoreCase(args[0])) continue;
            if (selected == null || request.created() > selected.created()) selected = request;
        }
        if (selected == null) {
            send(receiver, "no-request");
            return;
        }
        requests.remove(selected.pair());
        if (!accepts(receiver, selected.here())) {
            send(receiver, "request-disabled");
            return;
        }
        Player requester = Bukkit.getPlayer(selected.pair().sender());
        if (requester == null || !requester.isOnline()) {
            send(receiver, "offline-before-teleport");
            return;
        }
        if (!accepted) {
            send(receiver, "declined-receiver", "{player}", requester.getName());
            send(requester, "declined-sender", "{player}", receiver.getName());
            return;
        }
        executeTeleport(selected, requester, receiver);
    }

    private void executeTeleport(Request request, Player requester, Player receiver) {
        Player traveler = request.here() ? receiver : requester;
        Player destination = request.here() ? requester : receiver;
        if (!traveler.isOnline() || !destination.isOnline()) {
            send(requester, "offline-before-teleport");
            return;
        }
        traveler.teleportAsync(destination.getLocation(), PlayerTeleportEvent.TeleportCause.COMMAND)
                .whenComplete((success, error) -> Bukkit.getScheduler().runTask(core(), () -> {
                    if (error != null || !Boolean.TRUE.equals(success)) {
                        send(requester, "teleport-failed");
                        send(receiver, "teleport-failed");
                        return;
                    }
                    if (!requester.isOnline() || !receiver.isOnline()) return;
                    send(requester, "accepted-sender", "{player}", receiver.getName());
                    send(receiver, "accepted-receiver", "{player}", requester.getName());
                }));
    }

    private void expireRequests() {
        long maxAge = TimeUnit.SECONDS.toMillis(Math.max(1, getConfig().getLong("settings.request-expiry-seconds", 60)));
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Pair, Request>> iterator = requests.entrySet().iterator();
        while (iterator.hasNext()) {
            Request request = iterator.next().getValue();
            if (now - request.created() < maxAge) continue;
            iterator.remove();
            Player sender = Bukkit.getPlayer(request.pair().sender());
            Player receiver = Bukkit.getPlayer(request.pair().receiver());
            if (sender != null && sender.isOnline()) send(sender, "expired-sender", "{player}", receiver != null ? receiver.getName() : "Player");
            if (receiver != null && receiver.isOnline()) send(receiver, "expired-receiver", "{player}", sender != null ? sender.getName() : "Player");
        }
        cooldowns.entrySet().removeIf(entry -> now - entry.getValue() > Math.max(60000L, TimeUnit.SECONDS.toMillis(getConfig().getLong("settings.send-cooldown-seconds", 20))));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        requests.keySet().removeIf(key -> key.sender().equals(id) || key.receiver().equals(id));
    }

    private void send(CommandSender target, String key, String... replacements) {
        String value = getConfig().getString("messages." + key, "<gray>" + key);
        for (int i = 0; i + 1 < replacements.length; i += 2) value = value.replace(replacements[i], replacements[i + 1]);
        String prefix = getConfig().getString("messages.prefix", "<gradient:#8175AE:#D5DCE8><bold>MineNight</bold></gradient> <dark_gray>» <reset>");
        target.sendMessage(mini.deserialize(prefix + value));
    }
}

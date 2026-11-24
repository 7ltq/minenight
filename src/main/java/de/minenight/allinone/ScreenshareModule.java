package de.minenight.allinone;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.util.Vector;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;

public final class ScreenshareModule extends MineNightModule implements Listener {
    private record Session(String name, String staff, float walkSpeed, float flySpeed) {}
    private final Map<UUID, Session> sessions = new HashMap<>();
    private BukkitTask reminders;

    @Override public void onEnable() {
        var command = getCommand("screenshare");
        if (command == null) throw new IllegalStateException("screenshare not registered");
        command.setExecutor(this);
        command.setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, core());
        reminders = Bukkit.getScheduler().runTaskTimer(core(), () -> {
            for (UUID id : sessions.keySet()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) notifyPlayer(player);
            }
        }, 200L, 200L);
    }

    @Override public void onDisable() {
        if (reminders != null) reminders.cancel();
        for (UUID id : sessions.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) restore(player, sessions.get(id));
        }
        sessions.clear();
    }

    private boolean frozen(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    private void notifyPlayer(Player player) {
        String url = core().module("utility").getConfig().getString("links.discord", "https://discord.com/invite/mXjqNYF4b6");
        player.showTitle(Title.title(Component.text("SCREENSHARE", NamedTextColor.RED),
                Component.text("Join the Discord voice channel", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("You are frozen for a staff review. ", NamedTextColor.RED)
                .append(Component.text("Join MineNight Discord", NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(url))));
        player.sendMessage(Component.text("Share only your Minecraft window, or ask staff for an alternative review. Disconnecting during the review results in an immediate 7-day ban.", NamedTextColor.YELLOW));
    }

    private void release(UUID id) {
        Session session = sessions.remove(id);
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            if (session != null) restore(player, session);
            player.sendMessage(Component.text("Your staff review has ended.", NamedTextColor.GREEN));
        }
    }

    private void restore(Player player, Session session) {
        player.setWalkSpeed(session.walkSpeed());
        player.setFlySpeed(session.flySpeed());
        player.clearTitle();
    }

    private void alert(String message) {
        getLogger().info(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("minenight.screenshare")) player.sendMessage(Component.text(message, NamedTextColor.YELLOW));
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("minenight.screenshare")) return true;
        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            sender.sendMessage("Active reviews: " + sessions.values().stream().map(Session::name).sorted().toList());
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("release")) {
            UUID id = sessions.entrySet().stream().filter(e -> e.getValue().name().equalsIgnoreCase(args[1]))
                    .map(Map.Entry::getKey).findFirst().orElse(null);
            if (id == null) sender.sendMessage("No active review for that player.");
            else { release(id); sender.sendMessage("Review ended for " + args[1] + "."); }
            return true;
        }
        if (args.length == 1) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { sender.sendMessage("Player is not online."); return true; }
            if (target.hasPermission("minenight.screenshare")) { sender.sendMessage("Staff members are exempt."); return true; }
            if (frozen(target)) { sender.sendMessage("Player is already frozen."); return true; }
            sessions.put(target.getUniqueId(), new Session(target.getName(), sender.getName(), target.getWalkSpeed(), target.getFlySpeed()));
            target.leaveVehicle();
            target.setWalkSpeed(0f);
            target.setFlySpeed(0f);
            target.setVelocity(new Vector());
            target.closeInventory();
            notifyPlayer(target);
            alert(sender.getName() + " started a review for " + target.getName());
            return true;
        }
        sender.sendMessage("Usage: /screenshare <player|list> or /screenshare release <player>");
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("minenight.screenshare")) return List.of();
        var options = new java.util.ArrayList<String>();
        if (args.length == 1) {
            options.addAll(List.of("release", "list"));
            for (Player player : Bukkit.getOnlinePlayers()) if (!player.hasPermission("minenight.screenshare") && !frozen(player)) options.add(player.getName());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("release")) {
            sessions.values().forEach(s -> options.add(s.name()));
        }
        if (args.length == 0) return List.of();
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Session session = sessions.remove(id);
        if (session == null) return;
        restore(event.getPlayer(), session);
        if (!core().isEnabled() || Bukkit.getServer().isStopping()) return;
        if (core().module("moderation") instanceof de.minenight.moderation.MineNightModeration moderation
                && moderation.banScreenshareAvoid(id, session.name())) {
            alert(session.name() + " disconnected during a review and was banned for 7 days.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!frozen(event.getPlayer()) || event.getTo() == null) return;
        var from = event.getFrom();
        var to = event.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) { if (frozen(event.getPlayer())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) { if (frozen(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) { if (event.getEntity() instanceof Player player && frozen(player)) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && frozen(player)) event.setCancelled(true);
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter && frozen(shooter)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) { if (frozen(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) { if (frozen(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) { if (frozen(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) { if (frozen(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) { if (event.getWhoClicked() instanceof Player player && frozen(player)) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) { if (event.getWhoClicked() instanceof Player player && frozen(player)) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) { if (frozen(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) { if (frozen(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!frozen(event.getPlayer())) return;
        String name = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (!name.equals("discord")) event.setCancelled(true);
    }
}

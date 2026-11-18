package de.minenight.performance;

import de.minenight.allinone.MineNightModule;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

/**
 * Removes only reliably player-owned entities when their owner leaves an area.
 * Does not scan worlds or load chunks. Entity IDs are bounded and pruned on demand.
 */
public final class MineNightEntityCleanup extends MineNightModule implements Listener {
    private final Map<UUID, LinkedHashSet<UUID>> byOwner = new HashMap<>();
    private BukkitTask pruneTask;
    private boolean enabled;
    private int maxTracked;
    private double minimumTeleportDistanceSquared;

    @Override public void onEnable() {
        saveDefaultConfig();
        enabled = getConfig().getBoolean("enabled", true);
        maxTracked = Math.max(32, Math.min(4096, getConfig().getInt("max-tracked-per-player", 1024)));
        double minDistance = Math.max(0.0, getConfig().getDouble("minimum-teleport-distance-blocks", 2.0));
        minimumTeleportDistanceSquared = minDistance * minDistance;
        if (enabled) Bukkit.getPluginManager().registerEvents(this, core());
    }

    @Override public void onDisable() {
        if (pruneTask != null) pruneTask.cancel();
        pruneTask = null;
        byOwner.clear();
    }

    private void remember(Player owner, Entity entity) {
        if (!enabled || owner == null || entity == null || entity instanceof Player) return;
        LinkedHashSet<UUID> entities = byOwner.computeIfAbsent(owner.getUniqueId(), key -> new LinkedHashSet<>());
        entities.add(entity.getUniqueId());
        if (entities.size() > maxTracked) {
            Iterator<UUID> oldest = entities.iterator();
            if (oldest.hasNext()) {
                oldest.next();
                oldest.remove();
            }
        }
        if (pruneTask == null) {
            pruneTask = Bukkit.getScheduler().runTaskTimer(core(), this::prune, 1200L, 1200L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(EntityPlaceEvent event) {
        remember(event.getPlayer(), event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectile(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        ProjectileSource source = projectile.getShooter();
        if (source instanceof Player player) remember(player, projectile);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        remember(event.getPlayer(), event.getItemDrop());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!getConfig().getBoolean("on-teleport", true)) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (!from.getWorld().equals(to.getWorld()) || from.distanceSquared(to) >= minimumTeleportDistanceSquared) {
            cleanup(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (getConfig().getBoolean("on-disconnect", true)) cleanup(event.getPlayer().getUniqueId());
        else byOwner.remove(event.getPlayer().getUniqueId());
        stopPruningIfEmpty();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        if (getConfig().getBoolean("on-respawn", true)) cleanup(event.getPlayer().getUniqueId());
    }

    private void cleanup(UUID owner) {
        Set<UUID> ids = byOwner.remove(owner);
        if (ids == null) return;
        for (UUID id : ids) {
            // Bukkit.getEntity only resolves loaded entities: never force-load chunks.
            Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid() && !(entity instanceof Player)) entity.remove();
        }
        stopPruningIfEmpty();
    }

    private void prune() {
        Iterator<Map.Entry<UUID, LinkedHashSet<UUID>>> owners = byOwner.entrySet().iterator();
        while (owners.hasNext()) {
            Map.Entry<UUID, LinkedHashSet<UUID>> entry = owners.next();
            entry.getValue().removeIf(id -> {
                Entity entity = Bukkit.getEntity(id);
                return entity == null || !entity.isValid();
            });
            if (entry.getValue().isEmpty()) owners.remove();
        }
        stopPruningIfEmpty();
    }

    private void stopPruningIfEmpty() {
        if (byOwner.isEmpty() && pruneTask != null) {
            pruneTask.cancel();
            pruneTask = null;
        }
    }
}

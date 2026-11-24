package de.minenightperms.listeners;

import de.minenightperms.MineNightPerms;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class JoinQuitListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final MineNightPerms plugin;

    public JoinQuitListener(MineNightPerms plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("join.enabled", true)) {
            event.joinMessage(null);
            return;
        }
        Player p = event.getPlayer();
        String msg = plugin.getConfig()
            .getString("join.message", "<#8298C4>[<#91B2FF>+<#8298C4>] <white>{player}")
            .replace("{player}", p.getName());
        event.joinMessage(MM.deserialize(msg));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        if (!plugin.getConfig().getBoolean("quit.enabled", true)) {
            event.quitMessage(null);
            return;
        }
        Player p = event.getPlayer();
        String msg = plugin.getConfig()
            .getString("quit.message", "<#8298C4>[<#6685E8>-<#8298C4>] <white>{player}")
            .replace("{player}", p.getName());
        event.quitMessage(MM.deserialize(msg));
    }
}

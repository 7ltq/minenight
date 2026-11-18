package de.minenightperms.listeners;

import de.minenightperms.MineNightPerms;
import de.minenightperms.model.Group;
import de.minenightperms.model.PlayerData;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final MineNightPerms plugin;

    public ChatListener(MineNightPerms plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.getConfig().getBoolean("chat.enabled", true)) return;

        Player player = event.getPlayer();
        PlayerData pd = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        Group group = plugin.getGroupManager().getGroup(pd.getGroupName());

        String formattedRole = group.getFormattedRole();
        String formattedName = group.getFormattedName(player.getName());
        String rawMsg = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());

        String template = formattedRole.isEmpty()
            ? plugin.getConfig().getString("chat.format-default", "{name} <white>» {message}")
            : plugin.getConfig().getString("chat.format-with-role", "{role} {name} <white>» {message}");

        String built = template
            .replace("{role}", formattedRole)
            .replace("{name}", formattedName)
            .replace("{message}", rawMsg);

        final Component line = MM.deserialize(built);
        event.renderer((source, sourceDisplayName, message, viewer) -> line);
    }
}

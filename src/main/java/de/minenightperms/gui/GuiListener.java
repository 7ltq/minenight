package de.minenightperms.gui;

import de.minenightperms.MineNightPerms;
import de.minenightperms.model.Group;
import de.minenightperms.util.GradientPresets;
import de.minenightperms.util.PermissionCollector;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class GuiListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    
    private static final List<String> TITLES = List.of(
        "MineNightPerms", "All Groups", "Edit:", "Gradient", "Gradients",
        "Solid Colors", "Color Type", "Perms:", "Assign Role", "Role for:", "Emoji Picker"
    );

    private final MineNightPerms plugin;
    private final java.util.Map<java.util.UUID, GuiSession> sessions = new java.util.HashMap<>();


    public GuiListener(MineNightPerms plugin) { this.plugin = plugin; }

    public void openMainMenu(Player player) {
        sessions.put(player.getUniqueId(), new GuiSession());
        player.openInventory(GuiManager.buildMainMenu());
    }

    public void openGroupList(Player player) {
        GuiSession session = sessions.computeIfAbsent(player.getUniqueId(), k -> new GuiSession());
        session.setState(GuiSession.State.GROUP_LIST);
        player.openInventory(GuiManager.buildGroupList(plugin));
    }

    private void goMain(Player player, GuiSession session) {
        session.setState(GuiSession.State.MAIN_MENU);
        player.openInventory(GuiManager.buildMainMenu());
    }


    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String plain = PlainTextComponentSerializer.plainText()
            .serialize(event.getView().title());
        if (TITLES.stream().noneMatch(plain::contains)) return;

        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        int     slot    = event.getSlot();
        boolean isRight = event.getClick() == ClickType.RIGHT;

        GuiSession session = sessions.computeIfAbsent(player.getUniqueId(), k -> new GuiSession());

        switch (session.getState()) {

            case MAIN_MENU -> {
                if (slot == 10) {
                    session.setState(GuiSession.State.GROUP_LIST);
                    player.openInventory(GuiManager.buildGroupList(plugin));
                } else if (slot == 16) {
                    session.setState(GuiSession.State.SET_ROLE_PLAYER_LIST);
                    player.openInventory(GuiManager.buildPlayerList(plugin, player));
                }
            }

            case GROUP_LIST -> {
                List<Group> groupList = new ArrayList<>(plugin.getGroupManager().getGroups());
                int size = event.getInventory().getSize();

                if (item.getType() == Material.ARROW || slot == size - 1) {
                    goMain(player, session);
                    return;
                }
                if (slot == size - 5 && item.getType() == Material.PAPER) {
                    session.setState(GuiSession.State.GROUP_LIST);
                    plugin.getServer().getScheduler().runTaskLater(plugin.core(), () -> {
                        player.closeInventory();
                        player.sendMessage(MM.deserialize("<#91B2FF>Create a group using <white>/mnperms create <name>"));
                    }, 1L);
                    return;
                }
                if (slot < groupList.size()) {
                    Group g = groupList.get(slot);
                    session.setActiveGroup(g.getName().toLowerCase());
                    session.setState(GuiSession.State.GROUP_EDITOR);
                    player.openInventory(GuiManager.buildGroupEditor(plugin, g));
                }
            }

            case GROUP_EDITOR -> {
                Group g = plugin.getGroupManager().getGroup(session.getActiveGroup());
                if (g == null) { goMain(player, session); return; }

                switch (slot) {
                    case 10 -> {
                        session.setState(GuiSession.State.COLOR_CATEGORY_ROLE);
                        player.openInventory(GuiManager.buildColorCategoryPicker(
                            "<gradient:#4B63C4:#91B2FF><bold>Color Type</bold></gradient><white>: Role Color",
                            g.getRoleGradient()));
                    }
                    case 12 -> {
                        session.setState(GuiSession.State.COLOR_CATEGORY_NAME);
                        player.openInventory(GuiManager.buildColorCategoryPicker(
                            "<gradient:#4B63C4:#91B2FF><bold>Color Type</bold></gradient><white>: Name Color",
                            g.getNameGradient()));
                    }
                    case 14 -> {
                        session.setPermPage(0);
                        session.setPermFilter("");
                        session.setState(GuiSession.State.PERMISSIONS);
                        player.openInventory(GuiManager.buildPermissions(plugin, g, 0, ""));
                    }
                    case 16 -> {
                        if (item.getType() == Material.TNT) {
                            plugin.getGroupManager().deleteGroup(g.getName());
                            player.sendMessage(MM.deserialize(
                                "<#6685E8>✗ Group <white>" + g.getName() + "<#6685E8> deleted!"));
                            session.setState(GuiSession.State.GROUP_LIST);
                            player.openInventory(GuiManager.buildGroupList(plugin));
                        }
                    }
                    case 19 -> {
                        g.setRoleBold(!g.isRoleBold());
                        plugin.getGroupManager().saveGroups();
                        player.openInventory(GuiManager.buildGroupEditor(plugin, g));
                    }
                    case 21 -> {
                        g.setNameBold(!g.isNameBold());
                        plugin.getGroupManager().saveGroups();
                        player.openInventory(GuiManager.buildGroupEditor(plugin, g));
                    }
                    case 23 -> {
                        if (isRight) {
                            g.setEmoji("");
                            plugin.getGroupManager().saveGroups();
                            player.openInventory(GuiManager.buildGroupEditor(plugin, g));
                        } else {
                            session.setState(GuiSession.State.EMOJI_PICKER);
                            player.openInventory(GuiManager.buildEmojiPicker(g.getName()));
                        }
                    }
                    case 25 -> {
                        if (isRight) {

                            g.setPrefix("");
                            plugin.getGroupManager().saveGroups();
                            player.sendMessage(MM.deserialize(
                                "<#91B2FF>✔ Prefix cleared for <white>" + g.getName()));
                            player.openInventory(GuiManager.buildGroupEditor(plugin, g));
                        } else {
                            session.setState(GuiSession.State.GROUP_EDITOR);
                            plugin.getServer().getScheduler().runTaskLater(plugin.core(), () -> {
                                player.closeInventory();
                                player.sendMessage(MM.deserialize("<#91B2FF>Set a prefix using <white>/mnperms group " + g.getName() + " setprefix <text>"));
                            }, 1L);
                        }
                    }
                    case 28 -> {
                        int step = isRight ? 10 : 1;
                        g.setPriority(g.getPriority() - step);
                        plugin.getGroupManager().saveGroups();
                        player.openInventory(GuiManager.buildGroupEditor(plugin, g));
                    }
                    case 30 -> {  }
                    case 32 -> {
                        int step = isRight ? 10 : 1;
                        g.setPriority(g.getPriority() + step);
                        plugin.getGroupManager().saveGroups();
                        player.openInventory(GuiManager.buildGroupEditor(plugin, g));
                    }
                    case 34 -> {
                        plugin.getGroupManager().saveGroups();
                        player.sendMessage(MM.deserialize(
                            "<#91B2FF>✔ Group <white>" + g.getName() + "<#91B2FF> saved!"));
                        session.setState(GuiSession.State.GROUP_LIST);
                        player.openInventory(GuiManager.buildGroupList(plugin));
                    }
                    case 40 -> {
                        session.setState(GuiSession.State.GROUP_LIST);
                        player.openInventory(GuiManager.buildGroupList(plugin));
                    }
                }
            }

            case EMOJI_PICKER -> {
                Group g = plugin.getGroupManager().getGroup(session.getActiveGroup());
                if (g == null) { goMain(player, session); return; }

                if (slot == 45 || item.getType() == Material.ARROW) {

                    session.setState(GuiSession.State.GROUP_EDITOR);
                    player.openInventory(GuiManager.buildGroupEditor(plugin, g));
                } else if (slot == 46 || item.getType() == Material.WRITABLE_BOOK) {
                    session.setState(GuiSession.State.GROUP_EDITOR);
                    plugin.getServer().getScheduler().runTaskLater(plugin.core(), () -> {
                        player.closeInventory();
                        player.sendMessage(MM.deserialize("<#91B2FF>Set a custom symbol using <white>/mnperms group " + g.getName() + " setemoji <text>"));
                    }, 1L);
                } else if (slot == 49 || item.getType() == Material.BARRIER) {

                    g.setEmoji("");
                    plugin.getGroupManager().saveGroups();
                    session.setState(GuiSession.State.GROUP_EDITOR);
                    player.openInventory(GuiManager.buildGroupEditor(plugin, g));
                } else if (slot < 45 && slot < GuiManager.EMOJIS.size()) {

                    g.setEmoji(GuiManager.EMOJIS.get(slot));
                    plugin.getGroupManager().saveGroups();
                    session.setState(GuiSession.State.GROUP_EDITOR);
                    player.openInventory(GuiManager.buildGroupEditor(plugin, g));
                }
            }

            case COLOR_CATEGORY_ROLE, COLOR_CATEGORY_NAME -> {
                Group g = plugin.getGroupManager().getGroup(session.getActiveGroup());
                if (g == null) { goMain(player, session); return; }

                boolean isRole = session.getState() == GuiSession.State.COLOR_CATEGORY_ROLE;
                if (slot == 22 || item.getType() == Material.ARROW) {
                    session.setState(GuiSession.State.GROUP_EDITOR);
                    player.openInventory(GuiManager.buildGroupEditor(plugin, g));
                } else if (slot == 11) {
                    session.setState(isRole ? GuiSession.State.GRADIENT_PICKER_ROLE
                                            : GuiSession.State.GRADIENT_PICKER_NAME);
                    session.setGradientPage(0);
                    String title = isRole
                        ? "<gradient:#4B63C4:#91B2FF><bold>Gradients</bold></gradient><white>: Role Color"
                        : "<gradient:#4B63C4:#91B2FF><bold>Gradients</bold></gradient><white>: Name Color";
                    player.openInventory(GuiManager.buildColorPicker(title, 0, GradientPresets.GRADIENTS));
                } else if (slot == 15) {
                    session.setState(isRole ? GuiSession.State.SOLID_COLOR_PICKER_ROLE
                                            : GuiSession.State.SOLID_COLOR_PICKER_NAME);
                    session.setGradientPage(0);
                    String title = isRole
                        ? "<#91B2FF><bold>Solid Colors</bold></#91B2FF><white>: Role Color"
                        : "<#91B2FF><bold>Solid Colors</bold></#91B2FF><white>: Name Color";
                    player.openInventory(GuiManager.buildColorPicker(title, 0, GradientPresets.SOLID_COLORS));
                }
            }

            case GRADIENT_PICKER_ROLE, GRADIENT_PICKER_NAME,
                 SOLID_COLOR_PICKER_ROLE, SOLID_COLOR_PICKER_NAME -> {
                Group g = plugin.getGroupManager().getGroup(session.getActiveGroup());
                if (g == null) { goMain(player, session); return; }

                GuiSession.State state = session.getState();
                boolean isRole = state == GuiSession.State.GRADIENT_PICKER_ROLE
                    || state == GuiSession.State.SOLID_COLOR_PICKER_ROLE;
                boolean isGradient = state == GuiSession.State.GRADIENT_PICKER_ROLE
                    || state == GuiSession.State.GRADIENT_PICKER_NAME;
                List<GradientPresets.GradientPreset> presets = isGradient
                    ? GradientPresets.GRADIENTS : GradientPresets.SOLID_COLORS;
                GuiSession.State categoryState = isRole
                    ? GuiSession.State.COLOR_CATEGORY_ROLE : GuiSession.State.COLOR_CATEGORY_NAME;
                String pickerTitle;
                if (isGradient) {
                    pickerTitle = isRole
                        ? "<gradient:#4B63C4:#91B2FF><bold>Gradients</bold></gradient><white>: Role Color"
                        : "<gradient:#4B63C4:#91B2FF><bold>Gradients</bold></gradient><white>: Name Color";
                } else {
                    pickerTitle = isRole
                        ? "<#91B2FF><bold>Solid Colors</bold></#91B2FF><white>: Role Color"
                        : "<#91B2FF><bold>Solid Colors</bold></#91B2FF><white>: Name Color";
                }

                int page = session.getGradientPage();
                int total = presets.size();
                int maxPage = total == 0 ? 0 : (total - 1) / GradientPresets.PER_PAGE;

                if (slot == 47 || item.getType() == Material.BARRIER) {
                    session.setState(categoryState);
                    String categoryTitle = isRole
                        ? "<gradient:#4B63C4:#91B2FF><bold>Color Type</bold></gradient><white>: Role Color"
                        : "<gradient:#4B63C4:#91B2FF><bold>Color Type</bold></gradient><white>: Name Color";
                    player.openInventory(GuiManager.buildColorCategoryPicker(categoryTitle,
                        isRole ? g.getRoleGradient() : g.getNameGradient()));
                } else if (slot == 45 && page > 0) {
                    session.setGradientPage(page - 1);
                    player.openInventory(GuiManager.buildColorPicker(pickerTitle, page - 1, presets));
                } else if (slot == 53 && page < maxPage) {
                    session.setGradientPage(page + 1);
                    player.openInventory(GuiManager.buildColorPicker(pickerTitle, page + 1, presets));
                } else if (slot < GradientPresets.PER_PAGE) {
                    int index = page * GradientPresets.PER_PAGE + slot;
                    if (index < total) {
                        String color = presets.get(index).gradient();
                        if (isRole) g.setRoleGradient(color);
                        else g.setNameGradient(color);
                        plugin.getGroupManager().saveGroups();
                        session.setState(GuiSession.State.GROUP_EDITOR);
                        player.openInventory(GuiManager.buildGroupEditor(plugin, g));
                    }
                }
            }

            case PERMISSIONS -> {
                Group g = plugin.getGroupManager().getGroup(session.getActiveGroup());
                if (g == null) { goMain(player, session); return; }

                String filter  = session.getPermFilter();
                int    page    = session.getPermPage();
                List<String> all = PermissionCollector.getAllPermissions();
                List<String> filtered = filter.isBlank() ? all
                    : all.stream()
                         .filter(p -> p.toLowerCase().contains(filter.toLowerCase()))
                         .toList();
                int maxPage = filtered.isEmpty() ? 0 : (filtered.size() - 1) / 36;

                if (slot == 49) {
                    session.setState(GuiSession.State.GROUP_EDITOR);
                    player.openInventory(GuiManager.buildGroupEditor(plugin, g));
                } else if (slot == 45 && page > 0) {
                    session.setPermPage(page - 1);
                    player.openInventory(GuiManager.buildPermissions(plugin, g, page - 1, filter));
                } else if (slot == 53 && page < maxPage) {
                    session.setPermPage(page + 1);
                    player.openInventory(GuiManager.buildPermissions(plugin, g, page + 1, filter));
                } else if (slot < 36) {
                    int idx = page * 36 + slot;
                    if (idx < filtered.size()) {
                        String perm = filtered.get(idx);
                        List<String> perms = new ArrayList<>(g.getPermissions());
                        if (perms.contains(perm)) perms.remove(perm);
                        else perms.add(perm);
                        g.setPermissions(perms);
                        plugin.getGroupManager().saveGroups();
                        player.openInventory(GuiManager.buildPermissions(plugin, g, page, filter));
                    }
                }
            }

            case SET_ROLE_PLAYER_LIST -> {
                if (item.getType() == Material.ARROW) { goMain(player, session); return; }
                var players = new ArrayList<>(org.bukkit.Bukkit.getOnlinePlayers());
                int size = event.getInventory().getSize();
                if (slot < size - 9 && slot < players.size()) {
                    session.setTargetPlayerName(players.get(slot).getName());
                    session.setState(GuiSession.State.SET_ROLE_GROUP_PICK);
                    player.openInventory(GuiManager.buildGroupPick(plugin, players.get(slot).getName()));
                }
            }

            case SET_ROLE_GROUP_PICK -> {
                if (item.getType() == Material.ARROW) {
                    session.setState(GuiSession.State.SET_ROLE_PLAYER_LIST);
                    player.openInventory(GuiManager.buildPlayerList(plugin, player));
                    return;
                }
                List<Group> groupList = new ArrayList<>(plugin.getGroupManager().getGroups());
                int size = event.getInventory().getSize();
                if (slot < size - 9 && slot < groupList.size()) {
                    String targetName = session.getTargetPlayerName();
                    String groupName  = groupList.get(slot).getName();
                    var target = org.bukkit.Bukkit.getPlayerExact(targetName);
                    if (target != null) {
                        plugin.getPlayerDataManager()
                              .setPlayerGroup(target.getUniqueId(), groupName.toLowerCase());
                        player.sendMessage(MM.deserialize(
                            "<#91B2FF>✔ <white>" + targetName + "<#91B2FF> → <white>" + groupName));
                    }
                    session.setState(GuiSession.State.SET_ROLE_PLAYER_LIST);
                    player.openInventory(GuiManager.buildPlayerList(plugin, player));
                }
            }

            default -> {}
        }
    }

}

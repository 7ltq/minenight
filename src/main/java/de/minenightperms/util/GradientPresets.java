package de.minenightperms.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;

public class GradientPresets {

    
    public static final int PER_PAGE = 36;

    
    public static final List<GradientPreset> PRESETS = List.of(




        new GradientPreset("MineNight",     "<gradient:#8175AE:#D5DCE8>",                              Material.AMETHYST_SHARD),
        new GradientPreset("White",         "white",                                                          Material.WHITE_WOOL),
        new GradientPreset("Gray",          "gray",                                                           Material.GRAY_WOOL),
        new GradientPreset("Dark Gray",     "dark_gray",                                                      Material.GRAY_CONCRETE),
        new GradientPreset("Red",           "red",                                                            Material.RED_WOOL),
        new GradientPreset("Dark Red",      "dark_red",                                                       Material.NETHER_BRICK),
        new GradientPreset("Green",         "green",                                                          Material.GREEN_WOOL),
        new GradientPreset("Dark Green",    "dark_green",                                                     Material.MOSS_BLOCK),
        new GradientPreset("Blue",          "blue",                                                           Material.BLUE_WOOL),
        new GradientPreset("Dark Blue",     "dark_blue",                                                      Material.BLUE_GLAZED_TERRACOTTA),
        new GradientPreset("Aqua",          "aqua",                                                           Material.CYAN_WOOL),
        new GradientPreset("Dark Aqua",     "dark_aqua",                                                      Material.CYAN_GLAZED_TERRACOTTA),
        new GradientPreset("Yellow",        "yellow",                                                         Material.YELLOW_WOOL),
        new GradientPreset("Gold",          "gold",                                                           Material.GOLD_INGOT),
        new GradientPreset("Purple",        "dark_purple",                                                    Material.PURPLE_WOOL),
        new GradientPreset("Pink",          "light_purple",                                                   Material.PINK_WOOL),
        new GradientPreset("Black",         "black",                                                          Material.BLACK_WOOL),

        new GradientPreset("Fire",          "<gradient:#FF4500:#FFD700>",                                     Material.BLAZE_POWDER),
        new GradientPreset("Ocean",         "<gradient:#00BFFF:#00008B>",                                     Material.HEART_OF_THE_SEA),
        new GradientPreset("Rainbow",       "<gradient:#FF0000:#FF7700:#FFFF00:#00FF00:#0000FF:#8B00FF>",     Material.DIAMOND),
        new GradientPreset("Rose",          "<gradient:#FF69B4:#FF1493>",                                     Material.PINK_CONCRETE),
        new GradientPreset("Emerald",       "<gradient:#00FF7F:#006400>",                                     Material.EMERALD),
        new GradientPreset("Sunset",        "<gradient:#FF6347:#FF4500:#8B0000>",                             Material.NETHER_STAR),
        new GradientPreset("Ice",           "<gradient:#ADD8E6:#FFFFFF:#4169E1>",                             Material.PACKED_ICE),
        new GradientPreset("Lava",          "<gradient:#FF0000:#FF8C00:#FF4500>",                             Material.MAGMA_CREAM),
        new GradientPreset("Night Sky",     "<gradient:#191970:#000080:#0000CD>",                             Material.OBSIDIAN),
        new GradientPreset("Spring",        "<gradient:#98FB98:#7CFC00:#32CD32>",                             Material.GRASS_BLOCK),
        new GradientPreset("Galaxy",        "<gradient:#6A0DAD:#0000FF:#00CED1>",                             Material.END_CRYSTAL),
        new GradientPreset("Gold Rush",     "<gradient:#FFD700:#FFA500:#FF8C00>",                             Material.GOLD_BLOCK),
        new GradientPreset("Toxic",         "<gradient:#39FF14:#00FF00:#008000>",                             Material.SLIME_BALL),
        new GradientPreset("Candy",         "<gradient:#FF6EC7:#FF69B4:#FF1493:#FF6EC7>",                    Material.SUGAR),
        new GradientPreset("Storm",         "<gradient:#708090:#B0C4DE:#4682B4:#191970>",                    Material.LIGHTNING_ROD),
        new GradientPreset("Autumn",        "<gradient:#FF4500:#FF6347:#FFA500:#FFD700>",                    Material.OAK_LEAVES),
        new GradientPreset("Aquamarine",    "<gradient:#7FFFD4:#00CED1:#1E90FF>",                            Material.PRISMARINE_CRYSTALS),
        new GradientPreset("Sakura",        "<gradient:#FFB7C5:#FF69B4:#DB7093>",                            Material.CHERRY_LEAVES),
        new GradientPreset("Void",          "<gradient:#000000:#4B0082:#000000>",                            Material.CRYING_OBSIDIAN),
        new GradientPreset("Blood Moon",    "<gradient:#8B0000:#FF0000:#DC143C>",                            Material.REDSTONE_BLOCK),




        new GradientPreset("Neon",          "<gradient:#FF00FF:#00FFFF:#FF00FF>",                            Material.GLOWSTONE),
        new GradientPreset("Royal",         "<gradient:#6A0DAD:#9B30FF:#FFD700>",                            Material.AMETHYST_SHARD),
        new GradientPreset("Arctic",        "<gradient:#E0FFFF:#B0E0E6:#87CEEB:#4682B4>",                   Material.BLUE_ICE),
        new GradientPreset("Cyber",         "<gradient:#00FF41:#39FF14:#00BFFF>",                            Material.LIME_STAINED_GLASS),
        new GradientPreset("Nebula",        "<gradient:#FF00FF:#4B0082:#0000FF:#00BFFF>",                    Material.PURPLE_STAINED_GLASS),
        new GradientPreset("Inferno",       "<gradient:#FFFF00:#FF4500:#8B0000>",                            Material.FIRE_CHARGE),
        new GradientPreset("Lavender",      "<gradient:#E6E6FA:#9B59B6:#6C3483>",                            Material.PURPLE_CONCRETE_POWDER),
        new GradientPreset("Matrix",        "<gradient:#003B00:#00FF41:#39FF14>",                            Material.LIME_CONCRETE),
        new GradientPreset("Dusk",          "<gradient:#FFD700:#FF8C00:#FF4500:#8B0000:#000000>",            Material.ORANGE_GLAZED_TERRACOTTA),
        new GradientPreset("Holographic",   "<gradient:#FF00FF:#00FFFF:#FF00FF:#FFFFFF>",                   Material.GLASS),
        new GradientPreset("Copper",        "<gradient:#B87333:#CD7F32:#DA8A67>",                            Material.COPPER_INGOT),
        new GradientPreset("Peach",         "<gradient:#FFDAB9:#FFB6C1:#FF69B4>",                            Material.MELON_SLICE),
        new GradientPreset("Crimson",       "<gradient:#DC143C:#8B0000:#FF1493>",                            Material.CRIMSON_STEM),
        new GradientPreset("Steel",         "<gradient:#708090:#B0C4DE:#2F4F4F>",                            Material.IRON_BLOCK),
        new GradientPreset("Amber",         "<gradient:#FFBF00:#FF8C00:#FF4500>",                            Material.ORANGE_WOOL),
        new GradientPreset("Mint",          "<gradient:#98FF98:#00FF7F:#3CB371>",                            Material.LIME_DYE),
        new GradientPreset("Bubblegum",     "<gradient:#FF85C2:#FF1493:#FF85C2>",                            Material.PINK_STAINED_GLASS),
        new GradientPreset("Deep Sea",      "<gradient:#006994:#004F6D:#00354A>",                            Material.PRISMARINE),
        new GradientPreset("Aurora",        "<gradient:#00FF87:#60EFFF:#0061FF:#FF00C8>",                   Material.MAGENTA_GLAZED_TERRACOTTA),
        new GradientPreset("Coffee",        "<gradient:#6F4E37:#A0522D:#D2691E>",                            Material.BROWN_MUSHROOM),
        new GradientPreset("Plasma",        "<gradient:#9400D3:#4B0082:#0000FF:#00FF00:#FFFF00:#FF7F00:#FF0000>", Material.PURPLE_STAINED_GLASS_PANE),
        new GradientPreset("Coral",         "<gradient:#FF7F50:#FF6347:#FF4500>",                            Material.BRAIN_CORAL),
        new GradientPreset("Midnight",      "<gradient:#001B44:#003580:#0057B7>",                            Material.BLUE_CONCRETE),
        new GradientPreset("Forest",        "<gradient:#228B22:#32CD32:#98FB98>",                            Material.JUNGLE_LEAVES),
        new GradientPreset("Volcano",       "<gradient:#8B0000:#FF0000:#FF4500:#FFD700:#FFFFFF>",            Material.MAGMA_BLOCK),
        new GradientPreset("Bubbly",        "<gradient:#87CEEB:#00BFFF:#1E90FF:#87CEEB>",                   Material.LIGHT_BLUE_STAINED_GLASS),
        new GradientPreset("Poison",        "<gradient:#7CFC00:#00FF00:#006400:#7CFC00>",                   Material.POISONOUS_POTATO),
        new GradientPreset("Radioactive",   "<gradient:#ADFF2F:#7FFF00:#00FF00>",                            Material.LIME_GLAZED_TERRACOTTA),
        new GradientPreset("Titanium",      "<gradient:#878681:#C0C0C0:#E8E8E8:#878681>",                   Material.IRON_INGOT),
        new GradientPreset("Blaze",         "<gradient:#FF6600:#FF4500:#FF0000:#FFD700>",                   Material.BLAZE_ROD),
        new GradientPreset("Twilight",      "<gradient:#4B0082:#800080:#C71585:#FF69B4>",                   Material.MAGENTA_WOOL),
        new GradientPreset("Obsidian",      "<gradient:#1A0033:#2D0057:#4B0082>",                            Material.OBSIDIAN),
        new GradientPreset("Sunrise",       "<gradient:#FF4500:#FF8C00:#FFD700:#FFF8DC>",                   Material.YELLOW_STAINED_GLASS),
        new GradientPreset("Moonlight",     "<gradient:#2C3E50:#4A4E69:#9A8C98:#C9ADA7>",                   Material.LIGHT_GRAY_WOOL),
        new GradientPreset("Acid",          "<gradient:#CCFF00:#88FF00:#44FF00>",                            Material.SLIME_BLOCK),
        new GradientPreset("Cherry",        "<gradient:#DE3163:#FF69B4:#FFB6C1>",                            Material.CHERRY_LOG),




        new GradientPreset("📷 Camera",     "<gradient:#FF69B4:#FF1493>",                                    Material.SPYGLASS),
        new GradientPreset("🚀 Rocket",     "<gradient:#FF4500:#FFD700>",                                    Material.FIREWORK_ROCKET),
        new GradientPreset("👑 Crown",       "<gradient:#FFD700:#FF8C00>",                                    Material.GOLD_NUGGET),
        new GradientPreset("⭐ Star",         "<gradient:#FFD700:#FFA500>",                                   Material.NETHER_STAR),
        new GradientPreset("❄ Frost",        "<gradient:#ADD8E6:#FFFFFF:#4169E1>",                           Material.ICE),
        new GradientPreset("🔥 Flame",       "<gradient:#FF4500:#FF0000>",                                   Material.MAGMA_BLOCK),
        new GradientPreset("💀 Shadow",      "dark_gray",                                                     Material.SKELETON_SKULL),
        new GradientPreset("🌿 Nature",      "<gradient:#228B22:#00FF00>",                                   Material.FERN),
        new GradientPreset("🌊 Wave",        "<gradient:#006994:#00BFFF:#E0FFFF>",                           Material.WATER_BUCKET),
        new GradientPreset("⚡ Thunder",     "<gradient:#FFD700:#FFFF00:#FFFFFF>",                           Material.LIGHTNING_ROD),
        new GradientPreset("🎯 Target",      "<gradient:#FF0000:#8B0000>",                                   Material.TARGET),
        new GradientPreset("💎 Diamond",     "<gradient:#00BFFF:#1E90FF:#FFFFFF>",                           Material.DIAMOND),
        new GradientPreset("🎮 Gamer",       "<gradient:#00FF41:#FF00FF>",                                   Material.COMMAND_BLOCK),
        new GradientPreset("🏆 Trophy",      "<gradient:#FFD700:#DAA520>",                                   Material.BEACON),
        new GradientPreset("🌙 Moon",        "<gradient:#C0C0C0:#E8E8E8:#FFFFFF>",                           Material.CHISELED_STONE_BRICKS),
        new GradientPreset("☠ Reaper",       "<gradient:#4B0082:#000000>",                                   Material.WITHER_SKELETON_SKULL),
        new GradientPreset("🛡 Shield",       "<gradient:#4169E1:#1E90FF:#87CEEB>",                          Material.SHIELD),
        new GradientPreset("⚔ Warrior",      "<gradient:#C0C0C0:#808080:#404040>",                          Material.IRON_SWORD),
        new GradientPreset("🎭 Jester",      "<gradient:#FF0000:#0000FF:#FF0000>",                           Material.PURPLE_BANNER),
        new GradientPreset("🔮 Oracle",      "<gradient:#9400D3:#4B0082:#191970>",                           Material.END_PORTAL_FRAME),
        new GradientPreset("🌈 Arc",         "<gradient:#FF0000:#FF7F00:#FFFF00:#00FF00:#0000FF:#4B0082>",   Material.WHITE_STAINED_GLASS),
        new GradientPreset("🦋 Butterfly",   "<gradient:#FF69B4:#9B59B6:#4169E1>",                          Material.FEATHER),
        new GradientPreset("🐉 Dragon",      "<gradient:#FF0000:#8B0000:#FF6600>",                           Material.DRAGON_EGG),
        new GradientPreset("🦅 Eagle",       "<gradient:#8B6914:#D2691E:#FFFFFF>",                          Material.ARROW),
        new GradientPreset("🌺 Blossom",     "<gradient:#FF1493:#FF69B4:#FFB6C1>",                          Material.CHERRY_SAPLING),
        new GradientPreset("🍀 Clover",      "<gradient:#00FF00:#006400:#00FF00>",                           Material.FERN),
        new GradientPreset("🎵 Music",       "<gradient:#9B59B6:#6C3483:#FF69B4>",                           Material.JUKEBOX),
        new GradientPreset("📢 Herald",      "<gradient:#FFD700:#FF8C00:#FF4500>",                           Material.BELL),
        new GradientPreset("🔑 Key",         "<gradient:#DAA520:#8B6914:#D4AF37>",                          Material.TRIPWIRE_HOOK),
        new GradientPreset("💥 Boom",        "<gradient:#FF4500:#FFD700:#FFFFFF>",                           Material.TNT),
        new GradientPreset("🌟 Radiant",     "<gradient:#FFFACD:#FFD700:#FFA500>",                          Material.GLOWSTONE_DUST),
        new GradientPreset("🏅 Medal",       "<gradient:#DAA520:#C0C0C0:#CD7F32>",                          Material.GOLD_NUGGET),
        new GradientPreset("🔱 Trident",     "<gradient:#00CED1:#1E90FF:#00BFFF>",                          Material.TRIDENT),
        new GradientPreset("⚜ Fleur",        "<gradient:#FFD700:#FFA500:#DAA520>",                          Material.YELLOW_BANNER),
        new GradientPreset("🌠 Comet",       "<gradient:#FFFFFF:#87CEEB:#191970>",                           Material.GUNPOWDER),
        new GradientPreset("🪄 Magic",       "<gradient:#9B30FF:#FF00FF:#00FFFF>",                           Material.BREEZE_ROD),




        new GradientPreset("Cotton Candy",  "<gradient:#FFBFE0:#B8A9FF:#A8E6FF>",                           Material.PINK_WOOL),
        new GradientPreset("Baby Blue",     "<gradient:#89CFF0:#AED6F1:#D6EAF8>",                           Material.LIGHT_BLUE_WOOL),
        new GradientPreset("Lilac",         "<gradient:#C8A2C8:#DDA0DD:#EE82EE>",                           Material.PURPLE_CONCRETE_POWDER),
        new GradientPreset("Cream",         "<gradient:#FFFDD0:#FFEFD5:#FFE4B5>",                           Material.BIRCH_PLANKS),
        new GradientPreset("Blush",         "<gradient:#FFB6C1:#FFC0CB:#FFDDE1>",                           Material.PINK_STAINED_GLASS_PANE),
        new GradientPreset("Mint Cream",    "<gradient:#F5FFFA:#CCFFCC:#99FFCC>",                           Material.LIME_CONCRETE_POWDER),
        new GradientPreset("Powder",        "<gradient:#B0E0E6:#87CEEB:#ADD8E6>",                           Material.LIGHT_BLUE_CONCRETE_POWDER),
        new GradientPreset("Mauve",         "<gradient:#E0B0FF:#C8A2C8:#9B59B6>",                           Material.PURPLE_WOOL),
        new GradientPreset("Champagne",     "<gradient:#F7E7CE:#EDD9A3:#D4A96A>",                           Material.SAND),
        new GradientPreset("Watermelon",    "<gradient:#FC6C85:#FF9A8B:#FFBAC3>",                           Material.MELON),
        new GradientPreset("Periwinkle",    "<gradient:#CCCCFF:#9999FF:#6666FF>",                           Material.BLUE_CONCRETE_POWDER),
        new GradientPreset("Pistachio",     "<gradient:#93C572:#B2D8B2:#D5EACC>",                           Material.LIME_DYE),
        new GradientPreset("Apricot",       "<gradient:#FBCEB1:#FFA07A:#FF7F50>",                           Material.ORANGE_CONCRETE_POWDER),
        new GradientPreset("Thistle",       "<gradient:#D8BFD8:#DDA0DD:#C8A2C8>",                           Material.MAGENTA_WOOL),
        new GradientPreset("Seafoam",       "<gradient:#9FE2BF:#40E0D0:#48D1CC>",                           Material.CYAN_CONCRETE_POWDER),
        new GradientPreset("Wisteria",      "<gradient:#C9A0DC:#B57EDC:#9966CC>",                           Material.PURPLE_STAINED_GLASS_PANE),
        new GradientPreset("Petal",         "<gradient:#FFD1DC:#FAABD2:#F77DC4>",                           Material.PINK_PETALS),
        new GradientPreset("Lemon",         "<gradient:#FFF44F:#FFEC8B:#FFD700>",                           Material.YELLOW_CONCRETE_POWDER),
        new GradientPreset("Sky Drift",     "<gradient:#87CEEB:#B0E2FF:#E0F0FF>",                           Material.BLUE_STAINED_GLASS_PANE),
        new GradientPreset("Rosewood",      "<gradient:#65000B:#C21E56:#E75480>",                           Material.RED_CONCRETE),
        new GradientPreset("Opal",          "<gradient:#A8C5DA:#B5D5E8:#D1EAF0:#E8F4F8>",                  Material.WHITE_STAINED_GLASS_PANE),
        new GradientPreset("Bubble Gum",    "<gradient:#FF85A1:#FF5C8D:#FF3380>",                           Material.PINK_CONCRETE),
        new GradientPreset("Dream",         "<gradient:#DDA0DD:#FFB6C1:#ADD8E6:#B0E0E6>",                  Material.AMETHYST_CLUSTER),
        new GradientPreset("Cinnamon",      "<gradient:#D2691E:#CD853F:#DEB887>",                           Material.SPRUCE_LOG),
        new GradientPreset("Dew Drop",      "<gradient:#C8F5E0:#80DEEA:#4DB6AC>",                           Material.PRISMARINE_SHARD),
        new GradientPreset("Sorbet",        "<gradient:#FF6B6B:#FFD93D:#6BCB77>",                           Material.SLIME_BLOCK),
        new GradientPreset("Vanilla",       "<gradient:#F3E5AB:#FFFDE7:#FFF9C4>",                           Material.WHITE_CONCRETE_POWDER),
        new GradientPreset("Plum Blossom",  "<gradient:#B03060:#DA70D6:#DDA0DD>",                           Material.MAGENTA_CONCRETE),
        new GradientPreset("Icy Pastel",    "<gradient:#E8F8FF:#B8E4FF:#80CAFF>",                           Material.LIGHT_BLUE_STAINED_GLASS_PANE),
        new GradientPreset("Honey",         "<gradient:#FFCC02:#FFB300:#FF8F00>",                           Material.HONEYCOMB),
        new GradientPreset("Sherbet",       "<gradient:#FF9A9E:#FECFEF:#FECFEF:#A18CD1>",                  Material.PINK_GLAZED_TERRACOTTA),
        new GradientPreset("Teal Mist",     "<gradient:#E0F2F1:#B2DFDB:#80CBC4>",                           Material.CYAN_STAINED_GLASS_PANE),
        new GradientPreset("Rose Quartz",   "<gradient:#F7CAC9:#F6B8B8:#F48DB4>",                           Material.PINK_DYE),
        new GradientPreset("Lagoon",        "<gradient:#00C9FF:#92EFFD:#C2F0FF>",                           Material.CYAN_STAINED_GLASS),
        new GradientPreset("Dove",          "<gradient:#F0F0F0:#E8E8E8:#D8D8D8>",                           Material.WHITE_WOOL),
        new GradientPreset("Fig",           "<gradient:#7B3F6E:#9B4D96:#C47DC4>",                           Material.PURPLE_DYE),




        new GradientPreset("Abyss",         "<gradient:#000000:#010020:#000033>",                           Material.BLACK_CONCRETE),
        new GradientPreset("Dark Matter",   "<gradient:#0A0010:#1A0025:#2D0057>",                           Material.PURPLE_CONCRETE),
        new GradientPreset("Eclipse",       "<gradient:#000000:#1A0033:#FF4500>",                           Material.NETHER_WART_BLOCK),
        new GradientPreset("Shade",         "<gradient:#1C1C1C:#2C2C2C:#3C3C3C>",                           Material.GRAY_CONCRETE),
        new GradientPreset("Black Ice",     "<gradient:#000000:#191970:#00008B>",                           Material.BLUE_ICE),
        new GradientPreset("Charcoal",      "<gradient:#36454F:#4A5568:#2D3748>",                           Material.GRAY_WOOL),
        new GradientPreset("Hellfire",      "<gradient:#8B0000:#FF0000:#FF4500>#7F0000>",                   Material.SOUL_SAND),
        new GradientPreset("Venom",         "<gradient:#003300:#006600:#004400>",                           Material.GREEN_CONCRETE),
        new GradientPreset("Shadow Step",   "<gradient:#1A1A2E:#16213E:#0F3460>",                           Material.DEEPSLATE_BRICKS),
        new GradientPreset("Void Rift",     "<gradient:#000000:#200040:#400080:#200040:#000000>",           Material.RESPAWN_ANCHOR),
        new GradientPreset("Dark Web",      "<gradient:#0D0D0D:#1A1A1A:#00FF41>",                           Material.GREEN_STAINED_GLASS),
        new GradientPreset("Phantom",       "<gradient:#2C003E:#4A0072:#6A0A9F>",                           Material.PHANTOM_MEMBRANE),
        new GradientPreset("Corrupted",     "<gradient:#1B0000:#3D0000:#6B0000:#3D0000:#1B0000>",           Material.NETHER_BRICK_WALL),
        new GradientPreset("Death",         "<gradient:#1A1A1A:#333333:#000000>",                           Material.WITHER_SKELETON_SKULL),
        new GradientPreset("Sable",         "<gradient:#0B0C10:#1F2833:#C5C6C7>",                           Material.GRAY_GLAZED_TERRACOTTA),
        new GradientPreset("Darkwood",      "<gradient:#3D1A00:#5C2700:#7A3300>",                           Material.DARK_OAK_LOG),
        new GradientPreset("Murk",          "<gradient:#1C2526:#2E3B3C:#3F5354>",                           Material.DEEPSLATE),
        new GradientPreset("Midnight Oil",  "<gradient:#000014:#000028:#00003C>",                           Material.BLACK_WOOL),
        new GradientPreset("Wraith",        "<gradient:#151515:#2A2A2A:#3F3F3F:#2A2A2A>",                  Material.COAL_BLOCK),
        new GradientPreset("Abyssal Red",   "<gradient:#3B0000:#5C0000:#8B0000>",                           Material.RED_CONCRETE),
        new GradientPreset("Dark Aurora",   "<gradient:#002200:#004400:#006600:#00FF87>",                   Material.MOSS_BLOCK),
        new GradientPreset("Obsidian Fire", "<gradient:#2D0057:#4B0082:#8B0000:#FF4500>",                   Material.CRYING_OBSIDIAN),
        new GradientPreset("Black Hole",    "<gradient:#000000:#0A000A:#000000>",                           Material.BLACK_STAINED_GLASS),
        new GradientPreset("Dark Teal",     "<gradient:#003333:#004D4D:#006666>",                           Material.CYAN_CONCRETE),
        new GradientPreset("Grimoire",      "<gradient:#1A0033:#33006B:#4B009F>",                           Material.BOOK),
        new GradientPreset("Iron Curtain",  "<gradient:#222222:#444444:#666666>",                           Material.IRON_BARS),
        new GradientPreset("Infernal",      "<gradient:#3D0000:#7A0000:#B80000:#FF0000>",                   Material.NETHER_BRICK),
        new GradientPreset("Dreadnought",   "<gradient:#1C1C1C:#333333:#4A4A4A:#1C1C1C>",                  Material.DARK_OAK_PLANKS),
        new GradientPreset("Poison Cloud",  "<gradient:#1A2400:#2E4000:#3D5500:#1A2400>",                   Material.SLIME_BALL),
        new GradientPreset("Dark Honey",    "<gradient:#4A2000:#6B2F00:#8B3A00>",                           Material.BROWN_MUSHROOM_BLOCK),
        new GradientPreset("Wither Storm",  "<gradient:#000000:#1A0033:#2D0057:#000000>",                   Material.NETHERITE_BLOCK),
        new GradientPreset("Tar",           "<gradient:#0A0A0A:#141414:#1A1A1A>",                           Material.BLACK_GLAZED_TERRACOTTA),
        new GradientPreset("Maroon",        "<gradient:#800000:#A00000:#C00000>",                           Material.RED_NETHER_BRICKS),
        new GradientPreset("Underworld",    "<gradient:#1A0000:#330000:#4D0000:#FF2200>",                   Material.SOUL_LANTERN),
        new GradientPreset("Grim",          "<gradient:#2B2B2B:#1A1A1A:#3C3C3C:#2B2B2B>",                  Material.NETHERITE_INGOT),
        new GradientPreset("Nightmare",     "<gradient:#10002B:#26004C:#4A006F:#10002B>",                   Material.AMETHYST_SHARD),




        new GradientPreset("Silver",        "<gradient:#C0C0C0:#E8E8E8:#A8A8A8:#C0C0C0>",                  Material.IRON_INGOT),
        new GradientPreset("Platinum",      "<gradient:#E5E4E2:#D0CFC9:#BDBDBD:#E5E4E2>",                  Material.QUARTZ),
        new GradientPreset("Chrome",        "<gradient:#A8A9AD:#D4D5D9:#8E8FA3:#A8A9AD>",                  Material.LIGHT_GRAY_STAINED_GLASS),
        new GradientPreset("Bronze",        "<gradient:#CD7F32:#A0522D:#8B4513:#CD7F32>",                   Material.COPPER_INGOT),
        new GradientPreset("Rose Gold",     "<gradient:#B76E79:#C7848E:#D4A5A5:#B76E79>",                   Material.PINK_GLAZED_TERRACOTTA),
        new GradientPreset("Pyrite",        "<gradient:#CFB53B:#BFA030:#A88B28:#CFB53B>",                   Material.GOLD_BLOCK),
        new GradientPreset("Netherite",     "<gradient:#4A4040:#5A5050:#4A4040:#3A3030>",                   Material.NETHERITE_INGOT),
        new GradientPreset("Palladium",     "<gradient:#D0D0D0:#C4C4C4:#B8B8B8:#D0D0D0>",                  Material.IRON_BLOCK),
        new GradientPreset("Iridium",       "<gradient:#3D9970:#48B985:#2ECC71:#3D9970>",                   Material.EMERALD_BLOCK),
        new GradientPreset("Tungsten",      "<gradient:#808080:#909090:#707070:#808080>",                   Material.GRAY_CONCRETE),
        new GradientPreset("Oxidized",      "<gradient:#4CBB6C:#3DAA5B:#2E994A:#4CBB6C>",                   Material.OXIDIZED_COPPER),
        new GradientPreset("Gilded",        "<gradient:#FFD700:#DAA520:#B8860B:#FFD700>",                   Material.GILDED_BLACKSTONE),
        new GradientPreset("Carbon Fiber",  "<gradient:#1C1C1C:#2C2C2C:#1C1C1C:#3C3C3C>",                  Material.COAL_BLOCK),
        new GradientPreset("Cobalt",        "<gradient:#0047AB:#0055C8:#0063E5:#0047AB>",                   Material.BLUE_CONCRETE),
        new GradientPreset("Vanadium",      "<gradient:#4D7EAC:#6A9FC9:#4D7EAC:#3A6A9A>",                  Material.LIGHT_BLUE_CONCRETE),
        new GradientPreset("Molten Gold",   "<gradient:#FF8C00:#FFD700:#FF6600:#FFD700>",                   Material.GOLD_INGOT),
        new GradientPreset("Ingot",         "<gradient:#C0C0C0:#A0A0A0:#808080:#C0C0C0>",                  Material.IRON_INGOT),
        new GradientPreset("Electrum",      "<gradient:#DFC97F:#D4AF37:#C9A22A:#DFC97F>",                   Material.GOLD_NUGGET),
        new GradientPreset("Steel Blue",    "<gradient:#4682B4:#5B9BD5:#4682B4:#36729F>",                   Material.BLUE_GLAZED_TERRACOTTA),
        new GradientPreset("Polished",      "<gradient:#EEEEEE:#DDDDDD:#CCCCCC:#EEEEEE>",                  Material.QUARTZ_BLOCK),
        new GradientPreset("Gunmetal",      "<gradient:#2a3439:#3d4e54:#2a3439:#1e2d32>",                   Material.GRAY_STAINED_GLASS),
        new GradientPreset("Flux",          "<gradient:#FF4500:#FF8C00:#FFD700:#FF8C00:#FF4500>",           Material.MAGMA_CREAM),
        new GradientPreset("Forged",        "<gradient:#5A4A3A:#7A6A5A:#5A4A3A:#4A3A2A>",                  Material.ANVIL),
        new GradientPreset("Sapphire",      "<gradient:#0F52BA:#1565C0:#1976D2:#0F52BA>",                   Material.LAPIS_LAZULI),
        new GradientPreset("Ruby",          "<gradient:#9B111E:#CC2929:#E83838:#9B111E>",                   Material.REDSTONE),
        new GradientPreset("Amethyst",      "<gradient:#9B59B6:#8E44AD:#7D3C98:#9B59B6>",                   Material.AMETHYST_SHARD),
        new GradientPreset("Jade",          "<gradient:#00A36C:#00C17A:#00D988:#00A36C>",                   Material.EMERALD),
        new GradientPreset("Topaz",         "<gradient:#FFC87C:#FFB347:#FFA500:#FFC87C>",                   Material.YELLOW_DYE),
        new GradientPreset("Opal Fire",     "<gradient:#FF6B35:#FF8C69:#FFAAA5:#FF6B35>",                   Material.FIRE_CHARGE),
        new GradientPreset("Bismuth",       "<gradient:#E75480:#DA70D6:#9370DB:#4169E1>",                   Material.AMETHYST_CLUSTER),
        new GradientPreset("Obsidian Ore",  "<gradient:#1A0033:#290055:#380077:#1A0033>",                   Material.OBSIDIAN),
        new GradientPreset("Mithril",       "<gradient:#BCC9DC:#D0DFEE:#A8B9CC:#BCC9DC>",                  Material.LIGHT_BLUE_DYE),
        new GradientPreset("Valerian",      "<gradient:#9966CC:#AA77DD:#BB88EE:#9966CC>",                   Material.PURPLE_DYE),
        new GradientPreset("Golden Hour",   "<gradient:#FFF176:#FFD54F:#FFAB40:#FFF176>",                   Material.YELLOW_GLAZED_TERRACOTTA),
        new GradientPreset("Ferrite",       "<gradient:#8B4513:#6B3410:#AB5213:#8B4513>",                   Material.BROWN_CONCRETE),
        new GradientPreset("Quartz Vein",   "<gradient:#FAFAFA:#F0EEE0:#E8E4D0:#FAFAFA>",                  Material.NETHER_QUARTZ_ORE),




        new GradientPreset("🦁 Lion",        "<gradient:#C4912B:#DAA520:#FFC300>",                          Material.GOLD_BLOCK),
        new GradientPreset("🐺 Wolf",         "<gradient:#808080:#B0B0B0:#D8D8D8>",                         Material.LIGHT_GRAY_WOOL),
        new GradientPreset("🦊 Fox",          "<gradient:#E86928:#D45F20:#C04818>",                         Material.ORANGE_WOOL),
        new GradientPreset("🐻 Bear",         "<gradient:#6B3A2A:#8B4513:#A0522D>",                         Material.BROWN_WOOL),
        new GradientPreset("🐯 Tiger",        "<gradient:#FF8C00:#FF6600:#FF4500>",                         Material.ORANGE_TERRACOTTA),
        new GradientPreset("🌋 Volcano",      "<gradient:#8B0000:#FF4500:#FFD700>",                         Material.MAGMA_BLOCK),
        new GradientPreset("⛰ Mountain",      "<gradient:#808080:#A8A8A8:#D0D0D0>",                         Material.STONE),
        new GradientPreset("🌪 Tornado",       "<gradient:#B0C4DE:#708090:#4682B4>",                        Material.COBWEB),
        new GradientPreset("💠 Crystal",      "<gradient:#00BFFF:#7DF9FF:#FFFFFF>",                         Material.DIAMOND),
        new GradientPreset("☀ Solar",         "<gradient:#FF8C00:#FFD700:#FFFACD>",                         Material.GLOWSTONE),
        new GradientPreset("🌌 Cosmos",       "<gradient:#000033:#191970:#4B0082:#9400D3>",                 Material.END_CRYSTAL),
        new GradientPreset("🏰 Castle",       "<gradient:#808080:#A0A0A0:#C0C0C0>",                         Material.STONE_BRICKS),
        new GradientPreset("🗡 Blade",        "<gradient:#C0C0C0:#D8D8D8:#A8A8A8>",                        Material.IRON_SWORD),
        new GradientPreset("🌍 Earth",        "<gradient:#228B22:#4169E1:#8B4513>",                         Material.GRASS_BLOCK),
        new GradientPreset("🌀 Vortex",       "<gradient:#00BFFF:#4169E1:#9400D3:#00BFFF>",                Material.RESPAWN_ANCHOR),
        new GradientPreset("🎪 Circus",       "<gradient:#FF0000:#FFFFFF:#0000FF>",                         Material.WHITE_BANNER),
        new GradientPreset("🔬 Scientist",    "<gradient:#00FF7F:#00BFFF:#FFFFFF>",                         Material.SPYGLASS),
        new GradientPreset("🏴 Pirate",       "<gradient:#1C1C1C:#333333:#000000>",                         Material.BLACK_BANNER),
        new GradientPreset("⚙ Mechanic",      "<gradient:#708090:#A8A9AD:#C0C0C0>",                        Material.PISTON),
        new GradientPreset("🎲 Gambler",      "<gradient:#FF0000:#FFFFFF:#000000>",                         Material.CHISELED_POLISHED_BLACKSTONE),
        new GradientPreset("🌊 Poseidon",     "<gradient:#006994:#00BFFF:#40E0D0>",                         Material.HEART_OF_THE_SEA),
        new GradientPreset("🔱 Neptune",      "<gradient:#00008B:#0000CD:#0000FF:#00BFFF>",                 Material.TRIDENT),
        new GradientPreset("🦅 Griffin",      "<gradient:#DAA520:#8B4513:#C0C0C0>",                         Material.FEATHER),
        new GradientPreset("🍄 Shroom",       "<gradient:#FF6347:#CC3300:#FF9966>",                         Material.RED_MUSHROOM),
        new GradientPreset("🎇 Sparks",       "<gradient:#FFD700:#FF4500:#FFFFFF>",                         Material.FIREWORK_STAR),
        new GradientPreset("💫 Stardust",     "<gradient:#FFF8DC:#FFD700:#E6E6FA>",                         Material.GLOWSTONE_DUST),
        new GradientPreset("🌑 Dark Moon",    "<gradient:#1A1A2E:#16213E:#0F3460>",                         Material.BLACK_CONCRETE),
        new GradientPreset("🎖 Commander",    "<gradient:#8B6914:#B8860B:#DAA520>",                         Material.GOLDEN_CHESTPLATE),
        new GradientPreset("🔭 Explorer",     "<gradient:#4169E1:#00BFFF:#87CEEB>",                         Material.SPYGLASS),
        new GradientPreset("🧊 Cryo",         "<gradient:#E0FFFF:#B0E0E6:#87CEEB:#4682B4>",                Material.ICE),
        new GradientPreset("🌶 Spice",        "<gradient:#FF4500:#CC3300:#990000>",                         Material.RED_DYE),
        new GradientPreset("🎸 Rockstar",     "<gradient:#FF00FF:#AA00FF:#5500FF>",                         Material.JUKEBOX),
        new GradientPreset("🧬 Bio",          "<gradient:#00FF7F:#00CC66:#009933>",                         Material.LIME_DYE),
        new GradientPreset("🌺 Lotus",        "<gradient:#FFB6C1:#FF69B4:#FF1493:#FFB6C1>",                Material.PINK_PETALS),
        new GradientPreset("🏔 Summit",       "<gradient:#FFFFFF:#E0E0E0:#C0C0C0:#FFFFFF>",                Material.SNOW_BLOCK),
        new GradientPreset("🌙 Crescent",     "<gradient:#F4D03F:#F7DC6F:#FFFFFF>",                         Material.YELLOW_CONCRETE_POWDER),




        new GradientPreset("Hot Pink",       "<gradient:#FF007F:#FF66B2:#FF99CC>",                           Material.PINK_DYE),
        new GradientPreset("Electric Blue",  "<gradient:#0000FF:#0080FF:#00FFFF>",                           Material.BLUE_DYE),
        new GradientPreset("Vivid Orange",   "<gradient:#FF4500:#FF6A00:#FF8C00>",                           Material.ORANGE_DYE),
        new GradientPreset("Lime Shock",     "<gradient:#00FF00:#66FF00:#CCFF00>",                           Material.LIME_CONCRETE),
        new GradientPreset("Ultra Violet",   "<gradient:#7B00D4:#9B00FF:#C26DFF>",                           Material.PURPLE_DYE),
        new GradientPreset("Hyper Red",      "<gradient:#FF0000:#FF3333:#FF6666>",                           Material.RED_DYE),
        new GradientPreset("Tropical",       "<gradient:#00FA9A:#00CED1:#1E90FF>",                           Material.CYAN_DYE),
        new GradientPreset("Vivid Yellow",   "<gradient:#FFE000:#FFD000:#FFC000>",                           Material.YELLOW_DYE),
        new GradientPreset("Neon Pink",      "<gradient:#FF00A0:#FF00FF:#FF66FF>",                           Material.MAGENTA_DYE),
        new GradientPreset("Supernova",      "<gradient:#FF8C00:#FF4500:#FF0000:#8B0000>",                   Material.FIRE_CHARGE),
        new GradientPreset("Laser",          "<gradient:#00FF00:#00CC00:#009900>",                           Material.LIME_STAINED_GLASS_PANE),
        new GradientPreset("Deep Purple",    "<gradient:#4B0082:#6A0DAD:#8B00FF>",                           Material.PURPLE_GLAZED_TERRACOTTA),
        new GradientPreset("Shock Wave",     "<gradient:#00FFFF:#00BFFF:#0080FF:#0000FF>",                   Material.CYAN_GLAZED_TERRACOTTA),
        new GradientPreset("Flare",          "<gradient:#FF6600:#FF3300:#FF0000:#FF6600>",                   Material.BLAZE_POWDER),
        new GradientPreset("Vivid Teal",     "<gradient:#00FFCC:#00CCAA:#009988>",                           Material.CYAN_CONCRETE),
        new GradientPreset("Mango",          "<gradient:#FFB300:#FF8F00:#FF6F00>",                           Material.ORANGE_CONCRETE),
        new GradientPreset("Neon Green",     "<gradient:#39FF14:#00FF00:#00DD00>",                           Material.LIME_CONCRETE_POWDER),
        new GradientPreset("Flashbang",      "<gradient:#FFFFFF:#EEEEEE:#DDDDDD>",                           Material.WHITE_STAINED_GLASS_PANE),
        new GradientPreset("Crimson Surge",  "<gradient:#FF0033:#CC0000:#990000>",                           Material.RED_CONCRETE),
        new GradientPreset("Azure",          "<gradient:#007FFF:#0080FF:#0099FF>",                           Material.LIGHT_BLUE_CONCRETE),
        new GradientPreset("Vivid Magenta",  "<gradient:#FF00FF:#DD00DD:#BB00BB>",                           Material.MAGENTA_CONCRETE),
        new GradientPreset("Tangerine",      "<gradient:#FF8000:#FF9900:#FFAA00>",                           Material.ORANGE_TERRACOTTA),
        new GradientPreset("Cobalt Surge",   "<gradient:#0047AB:#0055C8:#1565C0>",                           Material.BLUE_CONCRETE),
        new GradientPreset("Vivid Spring",   "<gradient:#00FF7F:#33FF99:#66FFBB>",                           Material.LIME_DYE),
        new GradientPreset("Cherry Bomb",    "<gradient:#FF0033:#FF3366:#FF6699>",                           Material.RED_CONCRETE_POWDER),
        new GradientPreset("Electric Lime",  "<gradient:#CCFF00:#BBFF00:#AAFF00>",                           Material.LIME_GLAZED_TERRACOTTA),
        new GradientPreset("Cyan Pop",       "<gradient:#00FFFF:#00E5FF:#00CCFF>",                           Material.CYAN_STAINED_GLASS_PANE),
        new GradientPreset("Vivid Gold",     "<gradient:#FFD700:#FFB800:#FF9900>",                           Material.GOLD_BLOCK),
        new GradientPreset("Hyper Grape",    "<gradient:#8A2BE2:#9B30FF:#AA44FF>",                           Material.PURPLE_CONCRETE),
        new GradientPreset("Solar Flare",    "<gradient:#FFF200:#FFB300:#FF4500>",                           Material.YELLOW_GLAZED_TERRACOTTA),
        new GradientPreset("Neon Sunset",    "<gradient:#FF006E:#FF4500:#FF8C00>#FFD700>",                   Material.ORANGE_GLAZED_TERRACOTTA),
        new GradientPreset("Electric Storm", "<gradient:#6600FF:#0055FF:#00AAFF>",                           Material.BLUE_STAINED_GLASS),
        new GradientPreset("Vivid Coral",    "<gradient:#FF4040:#FF6060:#FF8080>",                           Material.FIRE_CORAL_BLOCK),
        new GradientPreset("Magma Surge",    "<gradient:#FF0000:#FF2200:#FF4400>",                           Material.MAGMA_BLOCK),
        new GradientPreset("Rave",           "<gradient:#FF00FF:#00FFFF:#FFFF00:#FF00FF>",                   Material.GLOWSTONE),
        new GradientPreset("Pop Art",        "<gradient:#FF007F:#FF7F00:#FFFF00:#00FF7F>",                   Material.CYAN_STAINED_GLASS),




        new GradientPreset("Sepia",          "<gradient:#704214:#A0522D:#C68642>",                           Material.BROWN_TERRACOTTA),
        new GradientPreset("Vintage Rose",   "<gradient:#C08081:#9C5C60:#7A3B3F>",                           Material.PINK_TERRACOTTA),
        new GradientPreset("Retro Teal",     "<gradient:#008080:#009090:#00A0A0>",                           Material.CYAN_TERRACOTTA),
        new GradientPreset("Old Gold",       "<gradient:#CFB53B:#B8A530:#A08A20>",                           Material.YELLOW_TERRACOTTA),
        new GradientPreset("Faded Denim",    "<gradient:#7B96B2:#6A82A0:#5A6E8E>",                           Material.BLUE_TERRACOTTA),
        new GradientPreset("Dusty Pink",     "<gradient:#D4A0A0:#BC8888:#A47070>",                           Material.RED_TERRACOTTA),
        new GradientPreset("Vintage Navy",   "<gradient:#1C2951:#253361:#2E3D71>",                           Material.BLUE_GLAZED_TERRACOTTA),
        new GradientPreset("Faded Olive",    "<gradient:#6B703F:#808050:#959660>",                           Material.GREEN_TERRACOTTA),
        new GradientPreset("Rustic Orange",  "<gradient:#C45E2A:#B04A20:#9C3810>",                           Material.ORANGE_TERRACOTTA),
        new GradientPreset("Antique White",  "<gradient:#F0EAD6:#E8E0CC:#DED6C2>",                           Material.BIRCH_PLANKS),
        new GradientPreset("Faded Maroon",   "<gradient:#8B2252:#7A1A42:#691232>",                           Material.MAGENTA_TERRACOTTA),
        new GradientPreset("Retro Green",    "<gradient:#4A7A4A:#3D6A3D:#305A30>",                           Material.GREEN_GLAZED_TERRACOTTA),
        new GradientPreset("Tobacco",        "<gradient:#7A5C3C:#6A4C2C:#5A3C1C>",                           Material.BROWN_GLAZED_TERRACOTTA),
        new GradientPreset("Vintage Violet", "<gradient:#7A5280:#6A4270:#5A3260>",                           Material.PURPLE_TERRACOTTA),
        new GradientPreset("Faded Crimson",  "<gradient:#9B3040:#883040:#752040>",                           Material.RED_GLAZED_TERRACOTTA),
        new GradientPreset("Washed Out",     "<gradient:#B8B0A0:#A8A090:#989080>",                           Material.GRAY_TERRACOTTA),
        new GradientPreset("Retro Cyan",     "<gradient:#4A9A9A:#3A8A8A:#2A7A7A>",                           Material.CYAN_CONCRETE_POWDER),
        new GradientPreset("Old Parchment",  "<gradient:#F4E0A0:#E8D090:#DCC080>",                           Material.OAK_PLANKS),
        new GradientPreset("Mustard",        "<gradient:#FFDB58:#ECC842:#D9B52C>",                           Material.YELLOW_CONCRETE),
        new GradientPreset("Terracotta",     "<gradient:#E2725B:#C85A45:#AE422F>",                           Material.TERRACOTTA),
        new GradientPreset("Vintage Purple", "<gradient:#5D4E6D:#4D3E5D:#3D2E4D>",                           Material.PURPLE_GLAZED_TERRACOTTA),
        new GradientPreset("Faded Sky",      "<gradient:#87AABE:#7090AA:#5A7A96>",                           Material.LIGHT_BLUE_TERRACOTTA),
        new GradientPreset("Dusty Sage",     "<gradient:#8A9A7A:#7A8A6A:#6A7A5A>",                           Material.GREEN_CONCRETE_POWDER),
        new GradientPreset("Retro Red",      "<gradient:#9A3030:#8A2020:#7A1010>",                           Material.RED_CONCRETE),
        new GradientPreset("Sienna",         "<gradient:#A0522D:#8B4513:#764213>",                           Material.ORANGE_CONCRETE_POWDER),
        new GradientPreset("Faded Lime",     "<gradient:#8AB88A:#7AA87A:#6A986A>",                           Material.LIME_TERRACOTTA),
        new GradientPreset("Old Copper",     "<gradient:#8C5A3C:#7C4A2C:#6C3A1C>",                           Material.EXPOSED_COPPER),
        new GradientPreset("Vintage Teal",   "<gradient:#3A7070:#2A6060:#1A5050>",                           Material.CYAN_CONCRETE),
        new GradientPreset("Sahara",         "<gradient:#C4A882:#B49870:#A48860>",                           Material.SANDSTONE),
        new GradientPreset("Retro Indigo",   "<gradient:#4040A0:#303090:#202080>",                           Material.BLUE_CONCRETE),
        new GradientPreset("Dusty Orange",   "<gradient:#C87040:#B86030>#A85020>",                           Material.ORANGE_CONCRETE),
        new GradientPreset("Film Noir",      "<gradient:#2A2A2A:#1A1A1A:#3A3A3A>",                           Material.GRAY_GLAZED_TERRACOTTA),
        new GradientPreset("Vintage Cream",  "<gradient:#FAF0DC:#EAE0CC:#DAD0BC>",                           Material.WHITE_TERRACOTTA),
        new GradientPreset("Retro Purple",   "<gradient:#6A3A7A:#5A2A6A:#4A1A5A>",                           Material.MAGENTA_CONCRETE),
        new GradientPreset("Dusty Blue",     "<gradient:#6A88AA:#5A7898:#4A6886>",                           Material.LIGHT_BLUE_CONCRETE),
        new GradientPreset("Bronze Age",     "<gradient:#CD853F:#B8722A:#A35F15>",                           Material.CHISELED_COPPER),




        new GradientPreset("Pine",           "<gradient:#1A4A1A:#245524:#2E6030>",                           Material.SPRUCE_LEAVES),
        new GradientPreset("Moss Green",     "<gradient:#4A7C4A:#5A8C5A:#6A9C6A>",                           Material.MOSS_BLOCK),
        new GradientPreset("Clay",           "<gradient:#B46A55:#A45A45:#945040>",                           Material.TERRACOTTA),
        new GradientPreset("Sandstone",      "<gradient:#E8C880:#D8B870:#C8A860>",                           Material.SANDSTONE),
        new GradientPreset("Obsidian Rock",  "<gradient:#2A2030:#1A1025:#100A18>",                           Material.OBSIDIAN),
        new GradientPreset("Bark",           "<gradient:#6B4226:#5A3520:#4A2818>",                           Material.OAK_LOG),
        new GradientPreset("Soil",           "<gradient:#5C3D1E:#4A2D12>#382108>",                           Material.ROOTED_DIRT),
        new GradientPreset("River Stone",    "<gradient:#7A7A7A:#888888:#969696>",                           Material.COBBLESTONE),
        new GradientPreset("Jungle",         "<gradient:#1A6B1A:#228B22:#2CAA2C>",                           Material.JUNGLE_LEAVES),
        new GradientPreset("Desert Sand",    "<gradient:#F0D090:#E0C080:#D0B070>",                           Material.SAND),
        new GradientPreset("Tundra",         "<gradient:#C8D8E0:#B8C8D0:#A8B8C0>",                           Material.POWDER_SNOW_BUCKET),
        new GradientPreset("Swamp",          "<gradient:#4A5A30:#3A4A22:#2A3A14>",                           Material.MUD),
        new GradientPreset("Savanna",        "<gradient:#C8A050:#B89040:#A88030>",                           Material.DIRT_PATH),
        new GradientPreset("Meadow",         "<gradient:#7ECC60:#6EBC50:#5EAC40>",                           Material.SHORT_GRASS),
        new GradientPreset("Glacier",        "<gradient:#B0D8F0:#90C0E0:#78B0D4>",                           Material.BLUE_ICE),
        new GradientPreset("Driftwood",      "<gradient:#8B7355:#7A6245:#695035>",                           Material.STRIPPED_OAK_LOG),
        new GradientPreset("Pebble",         "<gradient:#909090:#808080:#707070>",                           Material.STONE),
        new GradientPreset("Red Earth",      "<gradient:#8B2500:#7A2000:#691800>",                           Material.RED_SAND),
        new GradientPreset("Mountain Snow",  "<gradient:#F0F0F0:#E0E8F0:#D0E0F0>",                           Material.SNOW_BLOCK),
        new GradientPreset("Rainforest",     "<gradient:#0A4A0A:#0E5E0E:#127212>",                           Material.JUNGLE_LOG),
        new GradientPreset("Fossil",         "<gradient:#C8C0A0:#B8B090:#A8A080>",                           Material.BONE_BLOCK),
        new GradientPreset("Marsh",          "<gradient:#556B2F:#4A5F24:#3F5319>",                           Material.VINE),
        new GradientPreset("Amber Stone",    "<gradient:#C8882A:#B87820:#A8681A>",                           Material.YELLOW_TERRACOTTA),
        new GradientPreset("Slate",          "<gradient:#708090:#606880:#505870>",                           Material.DEEPSLATE),
        new GradientPreset("Volcanic Rock",  "<gradient:#3A3030:#2A2020:#1A1010>",                           Material.BASALT),
        new GradientPreset("Peat",           "<gradient:#4A3828:#3A2818:#2A1808>",                           Material.PODZOL),
        new GradientPreset("Autumn Leaves",  "<gradient:#D2691E:#B8570A:#FF6600>",                           Material.RED_MUSHROOM_BLOCK),
        new GradientPreset("River Mud",      "<gradient:#5C4033:#4A3023:#382010>",                           Material.MUD_BRICKS),
        new GradientPreset("Oak Wood",       "<gradient:#B88B4A:#A07838:#8A6526>",                           Material.OAK_PLANKS),
        new GradientPreset("Storm Cloud",    "<gradient:#5A6A7A:#4A5A6A:#3A4A5A>",                           Material.GRAY_STAINED_GLASS),
        new GradientPreset("Birch",          "<gradient:#D4C89A:#C4B888:#B4A876>",                           Material.BIRCH_LOG),
        new GradientPreset("Cave Rock",      "<gradient:#4A4A4A:#3A3A3A:#5A5A5A>",                           Material.DEEPSLATE_TILES),
        new GradientPreset("Sea Cliff",      "<gradient:#6A8070:#5A7060:#4A6050>",                           Material.MOSSY_STONE_BRICKS),
        new GradientPreset("Lakewater",      "<gradient:#2A6A8A:#1A5A7A:#0A4A6A>",                           Material.WATER_BUCKET),
        new GradientPreset("Flint",          "<gradient:#555555:#666666:#444444>",                           Material.FLINT),
        new GradientPreset("Terracotta Red", "<gradient:#C0503A:#B04028:#A03018>",                           Material.RED_TERRACOTTA),




        new GradientPreset("Synthwave",      "<gradient:#FF00FF:#8800FF:#0000FF:#00FFFF>",                  Material.MAGENTA_STAINED_GLASS),
        new GradientPreset("Retrowave",      "<gradient:#FF007F:#FF4500:#FF8C00>#FFFF00>",                  Material.ORANGE_STAINED_GLASS),
        new GradientPreset("Vaporwave",      "<gradient:#FF71CE:#05D9E8:#D4A5A5>#01FF70>",                  Material.PINK_STAINED_GLASS),
        new GradientPreset("Outrun",         "<gradient:#00FFFF:#0080FF:#8000FF:#FF0080>",                  Material.CYAN_STAINED_GLASS),
        new GradientPreset("Lofi",           "<gradient:#7986CB:#5C6BC0:#3949AB:#303F9F>",                  Material.BLUE_STAINED_GLASS),
        new GradientPreset("Dreamy",         "<gradient:#FFB3DE:#B3D9FF:#B3FFE0:#FFD9B3>",                  Material.WHITE_STAINED_GLASS),
        new GradientPreset("Psychedelic",    "<gradient:#FF0000:#FF8800:#FFFF00:#00FF00:#0000FF:#8800FF>",  Material.PURPLE_STAINED_GLASS),
        new GradientPreset("Disco",          "<gradient:#FF00FF:#FF4400:#FFFF00:#00FF44:#00FFFF>",          Material.LIME_STAINED_GLASS),
        new GradientPreset("Prism",          "<gradient:#FF0000:#FF7F00:#FFFF00:#00FF00:#0000FF:#4B0082>",  Material.GLASS),
        new GradientPreset("Cosmic",         "<gradient:#1A0033:#330066:#660099:#9900CC:#CC00FF>",          Material.PURPLE_CONCRETE),
        new GradientPreset("Glitch",         "<gradient:#00FF41:#FF00FF:#00FFFF:#FF0000>",                  Material.COMMAND_BLOCK),
        new GradientPreset("Ethereal",       "<gradient:#E8D5F5:#D0B3F0:#B891E8:#9B6EDF>",                  Material.AMETHYST_BLOCK),
        new GradientPreset("Hyperspace",     "<gradient:#000033:#000066:#000099:#0000CC:#0000FF>",          Material.BLUE_CONCRETE_POWDER),
        new GradientPreset("Spectral",       "<gradient:#FF00FF:#FF0099:#FF0033:#FF3300>",                  Material.MAGENTA_GLAZED_TERRACOTTA),
        new GradientPreset("Ultralight",     "<gradient:#FFFFFF:#F8F8FF:#F0F0FF>",                          Material.WHITE_CONCRETE_POWDER),
        new GradientPreset("Phantom Glow",   "<gradient:#7B00D4:#9400FF:#B84DFF:#D499FF>",                  Material.PURPLE_STAINED_GLASS_PANE),
        new GradientPreset("Digital Rain",   "<gradient:#003300:#006600:#009900:#00CC00>",                  Material.GREEN_STAINED_GLASS),
        new GradientPreset("Stargate",       "<gradient:#00BFFF:#1E90FF:#4169E1:#6A5ACD>",                  Material.BEACON),
        new GradientPreset("Time Warp",      "<gradient:#FF00FF:#8B00FF:#0000FF:#00BFFF:#00FF7F>",          Material.END_PORTAL_FRAME),
        new GradientPreset("Quantum",        "<gradient:#00FF00:#00FFAA:#00AAFF:#0055FF>",                  Material.SEA_LANTERN),
        new GradientPreset("Portal",         "<gradient:#7700AA:#AA00FF:#CC44FF:#AA00FF>",                  Material.RESPAWN_ANCHOR),
        new GradientPreset("Wormhole",       "<gradient:#000000:#330033:#660066:#990099:#CC00CC>",          Material.AMETHYST_SHARD),
        new GradientPreset("Hypnosis",       "<gradient:#FF0000:#FF8000:#FFFF00:#00FF00:#0000FF:#8B00FF>",  Material.NETHER_STAR),
        new GradientPreset("Zero Gravity",   "<gradient:#000014:#001428:#002840:#003C54>",                  Material.IRON_BARS),
        new GradientPreset("Blacklight",     "<gradient:#3300AA:#5500CC:#7700EE:#9900FF>",                  Material.PURPLE_CONCRETE_POWDER),
        new GradientPreset("Neon Abyss",     "<gradient:#00FF41:#FF00AA:#00AAFF>#FF0044>",                  Material.LIME_CONCRETE_POWDER),
        new GradientPreset("Mind Melt",      "<gradient:#FF6600:#AA00FF:#0066FF>#00FFAA>",                  Material.BRAIN_CORAL_BLOCK),
        new GradientPreset("Hyperion",       "<gradient:#FFAA00:#FF5500:#FF0000:#AA0000>",                  Material.SHROOMLIGHT),
        new GradientPreset("Arcane",         "<gradient:#9900CC:#6600AA:#330088>#000066>",                  Material.ENCHANTING_TABLE),
        new GradientPreset("Deep Freeze",    "<gradient:#00AAFF:#0055FF:#0000FF:#000099>",                  Material.PACKED_ICE),
        new GradientPreset("Dawnbreak",      "<gradient:#FF4500:#FF6A00:#FF8C00:#FFB300:#FFD700>",          Material.YELLOW_STAINED_GLASS_PANE),
        new GradientPreset("Midnight Nova",  "<gradient:#000000:#110033:#220066:#330099:#4400CC>",          Material.BLACK_STAINED_GLASS),
        new GradientPreset("Starfield",      "<gradient:#000000:#0A0A1A:#141428>#1E1E3C>",                  Material.BLACK_CONCRETE_POWDER),
        new GradientPreset("Phase Shift",    "<gradient:#00FF99:#00CCFF:#9900FF>#FF0099>",                  Material.BUBBLE_CORAL_BLOCK),
        new GradientPreset("Helios",         "<gradient:#FFFF00:#FFCC00:#FF9900:#FF6600>",                  Material.SUNFLOWER),
        new GradientPreset("Oblivion",       "<gradient:#000000:#050010:#0A0020>#0F0030>",                  Material.SCULK),

        // MineNight role/name colors based on the supplied in-game references.
        // These presets are available in BOTH the role-prefix and player-name pickers.
        new GradientPreset("Owner Cyan",          "#16ACCF",                            Material.CYAN_DYE),
        new GradientPreset("SR Admin Red",        "#E63B0C",                            Material.RED_DYE),
        new GradientPreset("Dev Admin Purple",    "<gradient:#7617A8:#A20B9A:#D31986>", Material.PURPLE_DYE),
        new GradientPreset("SR Admin Name Gold",  "#E9B833",                            Material.GOLD_INGOT),
        new GradientPreset("Napooo Aqua",         "#01FCE7",                            Material.PRISMARINE_CRYSTALS),
        new GradientPreset("SR Mod Green",        "#02F939",                            Material.LIME_DYE),
        new GradientPreset("Media Pink",          "#E52DBB",                            Material.PINK_DYE),
        new GradientPreset("Media Cyan",          "#00C8F0",                            Material.CYAN_DYE)
    );

    public static final List<GradientPreset> GRADIENTS = PRESETS.stream()
        .filter(preset -> preset.gradient() != null && preset.gradient().startsWith("<gradient"))
        .toList();

    public static final List<GradientPreset> SOLID_COLORS = PRESETS.stream()
        .filter(preset -> preset.gradient() == null || !preset.gradient().startsWith("<gradient"))
        .toList();

    public static ItemStack buildItem(GradientPreset preset) {
        ItemStack item = new ItemStack(preset.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        String n  = preset.name();
        String g  = preset.gradient();
        String mm = buildMiniMessage(g, n);
        meta.displayName(MiniMessage.miniMessage().deserialize(mm));
        item.setItemMeta(meta);
        return item;
    }

    
    public static String buildMiniMessage(String gradient, String text) {
        if (gradient == null || gradient.isEmpty()) return text;
        if (gradient.startsWith("<gradient")) {

            return gradient + text + "</gradient>";
        }

        return "<" + gradient + ">" + text + "</" + gradient + ">";
    }

    public record GradientPreset(String name, String gradient, Material material) {}
}

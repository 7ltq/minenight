package de.minenight.tiertest;

import de.minenight.allinone.KitModule;
import de.minenight.allinone.MineNightAllInOne;
import de.minenight.allinone.MineNightModule;
import de.minenight.pracspawn.MineNightPracticeSpawn;
import de.minenight.rtp.MineNightRTP;
import de.minenight.rtpqueue.MineNightRTPQueue;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.*;
import net.kyori.adventure.text.Component;

public final class TierTestModule extends MineNightModule implements Listener {
    private enum Stage { TYPE, KIT, BIOME, READY, SEARCHING, COUNTDOWN, ACTIVE, REVIEW, PAUSED, END }
    private static final List<String> BIOMES = List.of("plain", "desert", "snow", "mushroom", "badlands");
    private static final List<Material> ICONS = List.of(Material.GRASS_BLOCK, Material.SAND, Material.SNOW_BLOCK, Material.MYCELIUM, Material.RED_SAND);
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final Map<UUID, Match> roundDrops = new HashMap<>();
    private final NamespacedKey regearKey = new NamespacedKey("minenight","tier_regear");
    private final Map<UUID, Resume> suspendedMenus = new HashMap<>();
    private final Set<UUID> openingMenus = new HashSet<>();
    private final Set<UUID> pendingReopen = new HashSet<>();
    private record Resume(Match match, Stage stage, Runnable action, long after) {}
    private final Map<UUID, Match> matches = new HashMap<>();
    private final Map<UUID, Scoreboard> originalBoards = new HashMap<>();
    private final Set<UUID> internalTeleport = new HashSet<>();
    private final Set<UUID> recovery = new HashSet<>();
    private final Set<UUID> restoring = new HashSet<>();
    private File backups;
    private File premadeFile;
    private YamlConfiguration premades;
    private boolean stopping;
    private record Invite(UUID from, UUID to, long expires) {}

    private static final class Match {
        final UUID a, b;
        final Map<UUID, TierKitRules.Type> types = new HashMap<>();
        final Map<UUID, String> biomes = new HashMap<>();
        final Map<UUID, TierKitSnapshot> kits = new HashMap<>();
        final Map<UUID, String> kitNames = new HashMap<>();
        final Set<UUID> ready = new HashSet<>();
        final Set<UUID> dead = new HashSet<>();
        final Set<UUID> absent = new HashSet<>();
        final Set<UUID> rerunUsed = new HashSet<>();
        final Map<UUID, Objective> boards = new HashMap<>();
        final Map<UUID, Location> origins = new HashMap<>();
        boolean enteredArena;
        final Map<Location, UUID> containers = new HashMap<>();
        final Map<Location, UUID> regearBoxes = new HashMap<>();
        final Map<UUID, UUID> droppedBy = new HashMap<>();
        final UUID regearId=UUID.randomUUID();
        Stage stage = Stage.TYPE;
        TierKitRules.Type type;
        String biome;
        TierRoundScore score;
        long token, epoch, rerunUntil;
        UUID rerunBy;
        Location center;
        boolean deathScheduled, backedUp, resultShown;
        final Map<UUID, Draft> drafts = new HashMap<>();
        Match(UUID a, UUID b) { this.a = a; this.b = b; }
        List<UUID> ids() { return List.of(a, b); }
        UUID other(UUID id) { return a.equals(id) ? b : a; }
        boolean playing() { return backedUp && stage != Stage.END; }
    }

    private static final class Draft {
        final int number;
        final ItemStack[] storage, armor, ender;
        ItemStack offhand;
        Draft(int number, TierKitSnapshot snapshot) {
            this.number=number; storage=snapshot.storage(); armor=snapshot.armor(); ender=snapshot.ender(); offhand=snapshot.offhand();
        }
        TierKitSnapshot snapshot(){return new TierKitSnapshot(storage,armor,offhand,ender);}
        ItemStack get(int slot){return slot<36?storage[slot]:slot<40?armor[slot-36]:slot==40?offhand:ender[slot-41];}
        void set(int slot,ItemStack item){
            ItemStack copy=item==null?null:item.clone();
            if(slot<36)storage[slot]=copy;else if(slot<40)armor[slot-36]=copy;else if(slot==40)offhand=copy;else ender[slot-41]=copy;
        }
    }

    private static final class Menu implements InventoryHolder {
        final UUID owner;
        final Map<Integer, Runnable> actions = new HashMap<>();
        final Inventory inventory;
        Stage stage;
        Runnable resume;
        Menu(UUID owner, int size, Component title) {
            this.owner = owner;
            inventory = size == 27 ? Bukkit.createInventory(this, InventoryType.BARREL, title) : Bukkit.createInventory(this, size, title);
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    private static Component legacy(String value) { return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(value); }

    public static boolean canEditKit(Player player) {
        MineNightAllInOne plugin=MineNightAllInOne.instance();
        if(plugin==null||!(plugin.module("tiertest") instanceof TierTestModule module))return false;
        Match match=module.matches.get(player.getUniqueId());
        return match!=null&&module.current(match)&&!match.backedUp&&match.stage==Stage.KIT;
    }

    public static boolean protectedInventory(Player player) {
        MineNightAllInOne plugin = MineNightAllInOne.instance();
        if (plugin == null || !(plugin.module("tiertest") instanceof TierTestModule module)) return false;
        Match match = module.matches.get(player.getUniqueId());
        return module.recovery.contains(player.getUniqueId()) || module.restoring.contains(player.getUniqueId()) || (match != null && match.backedUp);
    }

    public static boolean locked(Player player) {
        MineNightAllInOne plugin = MineNightAllInOne.instance();
        if (plugin == null || !(plugin.module("tiertest") instanceof TierTestModule module)) return false;
        return module.recovery.contains(player.getUniqueId()) || module.restoring.contains(player.getUniqueId()) || module.matches.containsKey(player.getUniqueId());
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        backups = new File(getDataFolder(), "recovery");
        if (!backups.isDirectory() && !backups.mkdirs()) throw new IllegalStateException("Cannot create tier-test recovery directory");
        premadeFile = new File(getDataFolder(), "premades.yml");
        premades = YamlConfiguration.loadConfiguration(premadeFile);
        File[] pending = backups.listFiles((dir, name) -> name.endsWith(".yml"));
        if (pending != null) for (File file : pending) {
            try { recovery.add(UUID.fromString(file.getName().substring(0, file.getName().length() - 4))); }
            catch (IllegalArgumentException ignored) {}
        }
        for (String name : List.of("tiertest", "below", "above", "rerun")) {
            PluginCommand command = Objects.requireNonNull(getCommand(name));
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this, core());
        for (Player p : Bukkit.getOnlinePlayers()) if (recovery.contains(p.getUniqueId())) recover(p);
    }

    private int setting(String key, int fallback, int min, int max) {
        return Math.max(min, Math.min(max, getConfig().getInt("settings." + key, fallback)));
    }
    private String msg(String key, String fallback) { return getConfig().getString("messages." + key, fallback); }
    private void say(Player player, String message) { player.sendMessage(msg("prefix", "§8[§9MineNight§8] §7") + message); }
    private void tell(Match m, String message) { for (UUID id : m.ids()) { Player p = Bukkit.getPlayer(id); if (p != null) say(p, message); } }
    private boolean current(Match m) { return !stopping && matches.get(m.a) == m && matches.get(m.b) == m && m.stage != Stage.END; }
    private boolean available(Player p) {
        if (p == null || !p.isOnline() || p.isDead() || locked(p) || p.getGameMode() != GameMode.SURVIVAL) return false;
        if (core().module("rtpqueue") instanceof MineNightRTPQueue queue && queue.isBusy(p.getUniqueId())) return false;
        return !(core().module("rtp") instanceof MineNightRTP rtp && rtp.isSearching(p.getUniqueId()));
    }
    private String name(UUID id) { Player p = Bukkit.getPlayer(id); return p == null ? Optional.ofNullable(Bukkit.getOfflinePlayer(id).getName()).orElse("Offline") : p.getName(); }
    private String typeName(TierKitRules.Type type) { return type == TierKitRules.Type.BELOW ? "Below HT3" : "Above HT3"; }
    private String biomeName(String biome) { return biome.equals("plain") ? "Plains" : biome.substring(0,1).toUpperCase(Locale.ROOT) + biome.substring(1); }
    private int wins(TierKitRules.Type type) { return Math.max(1, Math.min(20, getConfig().getInt("wins." + type.key(), type == TierKitRules.Type.BELOW ? 3 : 4))); }
    private TierKitRules rules(TierKitRules.Type type) { return new TierKitRules(getConfig(), type); }
    private TierKitSnapshot capture(Player p) {
        return new TierKitSnapshot(p.getInventory().getStorageContents(), p.getInventory().getArmorContents(), p.getInventory().getItemInOffHand(), p.getEnderChest().getContents());
    }
    private void apply(Player p, TierKitSnapshot kit) {
        p.getInventory().setStorageContents(kit.storage());
        p.getInventory().setArmorContents(kit.armor());
        p.getInventory().setItemInOffHand(kit.offhand()==null?new ItemStack(Material.AIR):kit.offhand());
        p.getEnderChest().setContents(kit.ender());
        p.setItemOnCursor(new ItemStack(Material.AIR));
        p.updateInventory();
    }
    private void stats(Player p) {
        for (PotionEffect effect : p.getActivePotionEffects()) p.removePotionEffect(effect.getType());
        p.setHealth(Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getValue());
        p.setFoodLevel(20); p.setSaturation(20); p.setExhaustion(0); p.setFireTicks(0); p.setFallDistance(0); p.setAbsorptionAmount(0);
        p.setRemainingAir(p.getMaximumAir()); p.setVelocity(new org.bukkit.util.Vector());
    }
    private static void writeKit(YamlConfiguration yaml, String path, TierKitSnapshot kit) {
        yaml.set(path + ".storage", Arrays.asList(kit.storage()));
        yaml.set(path + ".armor", Arrays.asList(kit.armor()));
        yaml.set(path + ".offhand", kit.offhand());
        yaml.set(path + ".ender", Arrays.asList(kit.ender()));
    }
    private static ItemStack[] readItems(YamlConfiguration yaml, String path, int size) {
        ItemStack[] items = new ItemStack[size];
        List<?> stored = yaml.getList(path, List.of());
        for (int i = 0; i < size && i < stored.size(); i++) if (stored.get(i) instanceof ItemStack item) items[i] = item.clone();
        return items;
    }
    private static TierKitSnapshot readKit(YamlConfiguration yaml, String path) {
        return new TierKitSnapshot(readItems(yaml,path+".storage",36),readItems(yaml,path+".armor",4),yaml.getItemStack(path+".offhand"),readItems(yaml,path+".ender",27));
    }
    private static void atomicSave(YamlConfiguration data, File file) throws IOException {
        File tmp = new File(file.getParentFile(), file.getName()+".tmp");
        data.save(tmp);
        try { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException error) { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING); }
    }
    private File backup(UUID id) { return new File(backups, id+".yml"); }
    private void saveBackup(Player p) throws IOException {
        File file = backup(p.getUniqueId());
        if (file.exists()) throw new IOException("Pending recovery already exists for "+p.getUniqueId());
        YamlConfiguration y = new YamlConfiguration();
        writeKit(y,"kit",capture(p));
        y.set("view-distance",p.getViewDistance());
        y.set("location",p.getLocation());
        y.set("health",p.getHealth()); y.set("food",p.getFoodLevel()); y.set("saturation",p.getSaturation());
        y.set("exhaustion",p.getExhaustion()); y.set("absorption",p.getAbsorptionAmount());
        y.set("fire",p.getFireTicks()); y.set("level",p.getLevel()); y.set("exp",p.getExp()); y.set("total-exp",p.getTotalExperience());
        y.set("effects",new ArrayList<>(p.getActivePotionEffects()));
        atomicSave(y,file);
        recovery.add(p.getUniqueId());
        originalBoards.put(p.getUniqueId(),p.getScoreboard());
    }
    private boolean recover(Player p) {
        UUID id=p.getUniqueId();
        File file=backup(id);
        if (!recovery.contains(id) || !file.isFile()) return false;
        if (p.isDead()) return false;
        restoring.add(id);
        try {
            YamlConfiguration y = new YamlConfiguration();
            y.load(file);
            if (!y.isList("kit.storage") || !y.isList("kit.armor") || !y.isList("kit.ender")) throw new IOException("Invalid recovery file");
            p.closeInventory();
            apply(p,readKit(y,"kit"));
            stats(p);
            for(Object effect:y.getList("effects",List.of())) if(effect instanceof PotionEffect potion) p.addPotionEffect(potion);
            p.setHealth(Math.max(1,Math.min(Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getValue(),y.getDouble("health",20))));
            p.setFoodLevel(y.getInt("food",20));p.setSaturation((float)y.getDouble("saturation",5));p.setExhaustion((float)y.getDouble("exhaustion"));
            p.setAbsorptionAmount(Math.max(0,y.getDouble("absorption")));p.setFireTicks(y.getInt("fire"));
            p.setTotalExperience(y.getInt("total-exp"));p.setLevel(y.getInt("level"));p.setExp((float)y.getDouble("exp"));
            p.setViewDistance(Math.max(2,Math.min(32,y.getInt("view-distance",8))));
            Scoreboard original=originalBoards.get(id);
            if(original!=null&&p.getScoreboard()!=original)p.setScoreboard(original);
            else if(original==null)p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            p.saveData();
            Files.delete(file.toPath());
            recovery.remove(id); originalBoards.remove(id);
            p.removeScoreboardTag("minenight_tiertest");
            return true;
        } catch(Exception error) {
            getLogger().log(java.util.logging.Level.SEVERE,"Tier test recovery failed for "+id+"; keeping backup",error);
            say(p,"§cInventory recovery is pending. Contact staff.");
            return false;
        } finally { restoring.remove(id); }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        UUID id=p.getUniqueId();
        String cmd=command.getName();
        Match m=matches.get(id);
        if (cmd.equals("above") || cmd.equals("below")) {
            if (!p.hasPermission("minenight.tiertest.staff") || locked(p)) { say(p,"§cYou cannot save a premade kit now."); return true; }
            if (args.length!=3 || !String.join(" ",args).equalsIgnoreCase("ht3 kit create")) return false;
            TierKitRules.Type type=cmd.equals("below")?TierKitRules.Type.BELOW:TierKitRules.Type.ABOVE;
            TierKitRules.Result result=rules(type).sanitize(capture(p));
            writeKit(premades,type.key(),result.kit());
            try { atomicSave(premades,premadeFile); say(p,"Saved "+typeName(type)+" premade kit."); report(p,result); }
            catch(IOException e) { premades=YamlConfiguration.loadConfiguration(premadeFile);say(p,"§cCould not save premade kit.");getLogger().warning(e.toString()); }
            return true;
        }
        if(cmd.equals("rerun")) {
            if(args.length!=2 || !String.join(" ",args).equalsIgnoreCase("this round")) return false;
            requestRerun(p,m);return true;
        }
        if(args.length>0 && args[0].equalsIgnoreCase("check")) {
            if(locked(p)) { say(p,msg("blocked","That action is unavailable."));return true; }
            if(args.length==1) {
                say(p,"Below HT3:");report(p,rules(TierKitRules.Type.BELOW).sanitize(capture(p)));
                say(p,"Above HT3:");report(p,rules(TierKitRules.Type.ABOVE).sanitize(capture(p)));return true;
            }
            if(args.length>3 || !(args[1].equalsIgnoreCase("below") || args[1].equalsIgnoreCase("above"))) { say(p,"Usage: /tiertest check <below|above> [1-9]");return true; }
            TierKitRules.Type type=args[1].equalsIgnoreCase("below")?TierKitRules.Type.BELOW:TierKitRules.Type.ABOVE;
            TierKitSnapshot kit=capture(p);
            if(args.length==3) {
                if(!args[2].matches("[1-9]")) {say(p,"Choose kit 1-9.");return true;}
                var saved=((KitModule)core().module("kits")).snapshotForTierTest(id,Integer.parseInt(args[2]));
                if(saved.isEmpty()){say(p,"That kit is empty.");return true;}kit=saved.get();
            }
            report(p,rules(type).sanitize(kit));return true;
        }
        if(args.length==1&&args[0].equalsIgnoreCase("icons")){
            if(locked(p)){say(p,msg("blocked","Action unavailable during a tier test."));return true;}
            boolean heads=de.minenight.allinone.GuiPlayerIcons.toggle(p);
            say(p,heads?"Player head icons enabled.":"Name tag icons enabled (head rendering fallback).");
            return true;
        }
        if(args.length>0 && args[0].equalsIgnoreCase("leave")) {
            if(m!=null) finish(m,m.backedUp?m.other(id):null,"Player left the tier test.");
            else { invites.values().removeIf(i->i.from.equals(id)||i.to.equals(id)); say(p,"No active tier test."); }
            return true;
        }
        if(args.length>=1 && (args[0].equalsIgnoreCase("accept")||args[0].equalsIgnoreCase("decline"))) {
            if(args.length>2){say(p,"Usage: /tiertest "+args[0].toLowerCase(Locale.ROOT)+" [player]");return true;}
            Invite i=invites.get(id);
            if(i==null){say(p,"You have no pending tier test invitation.");return true;}
            if(i.expires<System.currentTimeMillis()){invites.remove(id,i);say(p,"That invitation has expired.");return true;}
            Player a=Bukkit.getPlayer(i.from);
            if(a==null){invites.remove(id,i);say(p,"The inviting player is no longer online.");return true;}
            if(args.length==2&&!a.getName().equalsIgnoreCase(args[1])){say(p,"Your pending invitation is from "+a.getName()+".");return true;}
            invites.remove(id);
            if(args[0].equalsIgnoreCase("decline")){say(a,p.getName()+" declined the tier test.");return true;}
            if(!available(p)||!available(a)){say(p,"A player is no longer available.");return true;}
            Match created=new Match(a.getUniqueId(),id);
            matches.put(created.a,created);matches.put(created.b,created);
            invites.values().removeIf(v->created.ids().contains(v.from)||created.ids().contains(v.to));
            openBoth(created);
            Bukkit.getScheduler().runTaskLater(core(),()->{if(current(created)&&!created.backedUp)finish(created,null,"Tier test setup expired.");},20L*setting("setup-seconds",180,30,600));
            return true;
        }
        if(args.length!=0) return false;
        if(m!=null) { if(!m.backedUp) resumeNow(p,m); else say(p,"Tier test in progress. /tiertest leave to forfeit."); }
        else if(available(p)) openOpponents(p,0);
        else say(p,"You are not available for a tier test.");
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> choices=List.of();
        if(command.getName().equals("tiertest")) {
            if(args.length==1) choices=List.of("accept","decline","leave","check","icons");
            else if(args.length==2&&args[0].equalsIgnoreCase("check"))choices=List.of("below","above");
            else if(args.length==3&&args[0].equalsIgnoreCase("check"))choices=List.of("1","2","3","4","5","6","7","8","9");
            else if(args.length==2&&sender instanceof Player p&&invites.containsKey(p.getUniqueId()))choices=List.of(name(invites.get(p.getUniqueId()).from));
        } else if(command.getName().equals("rerun")) choices=args.length==1?List.of("this"):args.length==2?List.of("round"):List.of();
        else choices=args.length==1?List.of("ht3"):args.length==2?List.of("kit"):args.length==3?List.of("create"):List.of();
        if(args.length==0)return choices;
        String prefix=args[args.length-1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private static final net.kyori.adventure.text.format.TextColor BLUE = net.kyori.adventure.text.format.TextColor.color(0x5B9BD5);
    private static final net.kyori.adventure.text.format.TextColor LIGHT = net.kyori.adventure.text.format.TextColor.color(0x91C8FF);
    private static final net.kyori.adventure.text.format.TextColor MUTED = net.kyori.adventure.text.format.TextColor.color(0xA4B4C6);
    private static Component flat(Component c){return c.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,false);}
    private ItemStack icon(Material material,String title,String... lore) {
        ItemStack item=new ItemStack(material);ItemMeta meta=item.getItemMeta();
        Component name=legacy(title);
        if(name.color()==null&&!title.startsWith("§"))name=name.color(LIGHT);
        meta.displayName(flat(name));
        meta.lore(Arrays.stream(lore).map(line->flat(line.startsWith("§")?legacy(line):Component.text(line,MUTED))).toList());
        item.setItemMeta(meta);return item;
    }
    private ItemStack selected(ItemStack item,boolean selected){
        if(selected){ItemMeta meta=item.getItemMeta();meta.setEnchantmentGlintOverride(true);item.setItemMeta(meta);}
        return item;
    }
    private Material configuredIcon(String key,Material fallback) {
        Material m=Material.matchMaterial(getConfig().getString("gui."+key,fallback.name()));
        return m!=null&&m.isItem()&&!m.isAir()?m:fallback;
    }
    private Menu menu(Player p,int size,String title) {
        Component header=net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<gradient:#4682B4:#91C8FF><bold>MineNight</bold></gradient><dark_gray> » </dark_gray>");
        return new Menu(p.getUniqueId(),size,header.append(Component.text(title,LIGHT)));
    }
    private void put(Menu menu,int slot,ItemStack item,Runnable action){menu.inventory.setItem(slot,item);menu.actions.put(slot,action);}
    private void decoration(Menu menu,int slot,Material material){menu.inventory.setItem(slot,icon(material," "));}
    private void footer(Menu menu,int first,int last){
        for(int i=first;i<=last;i++)decoration(menu,i,i==first||i==last?Material.BLUE_STAINED_GLASS_PANE:Material.GRAY_STAINED_GLASS_PANE);
    }
    private void display(Player p,Menu menu){
        UUID id=p.getUniqueId();
        suspendedMenus.remove(id);openingMenus.add(id);
        try{p.openInventory(menu.inventory);}finally{openingMenus.remove(id);}
    }
    private void openOpponents(Player p,int page) {
        List<Player> players=Bukkit.getOnlinePlayers().stream().filter(other->!other.equals(p)&&p.canSee(other)&&available(other)).sorted(Comparator.comparing(Player::getName)).map(other->(Player)other).toList();
        int last=Math.max(0,(players.size()-1)/18);int index=Math.max(0,Math.min(last,page));
        Menu menu=menu(p,27,"Select Opponent");footer(menu,18,26);
        for(int i=index*18;i<Math.min(players.size(),index*18+18);i++){
            Player other=players.get(i);
            ItemStack head=icon(de.minenight.allinone.GuiPlayerIcons.material(p),"§b"+other.getName(),"§aAvailable"," ","Click to send an invitation");
            de.minenight.allinone.GuiPlayerIcons.applyProfile(head,other);
            put(menu,i-index*18,head,()->invite(p,other));
        }
        if(players.isEmpty())menu.inventory.setItem(4,icon(Material.CLOCK,"No opponents available","Try again when another player is online."));
        menu.inventory.setItem(22,icon(Material.COMPASS,"§bSelect Opponent","Page "+(index+1)+" / "+(last+1),players.size()+" available player(s)"));
        if(index>0)put(menu,18,icon(Material.ARROW,"Previous page"),()->openOpponents(p,index-1));
        if(index<last)put(menu,26,icon(Material.ARROW,"Next page"),()->openOpponents(p,index+1));
        display(p,menu);
    }
    private void invite(Player a,Player b) {
        if(!available(a)||!available(b)||!a.canSee(b)){say(a,"Player is unavailable.");return;}
        UUID from=a.getUniqueId(),to=b.getUniqueId();
        if(invites.containsKey(to)||invites.values().stream().anyMatch(i->i.from.equals(from))){say(a,"An invitation is already pending.");return;}
        Invite invite=new Invite(from,to,System.currentTimeMillis()+1000L*setting("invitation-seconds",60,5,300));
        invites.put(to,invite);a.closeInventory();
        say(a,"Invitation sent to "+b.getName()+".");
        say(b,a.getName()+" invited you to a tier test.");
        b.sendMessage(Component.text("[Accept]",net.kyori.adventure.text.format.NamedTextColor.GREEN)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/tiertest accept "+a.getName()))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text("Accept the invitation")))
                .append(Component.space())
                .append(Component.text("[Decline]",net.kyori.adventure.text.format.NamedTextColor.RED)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/tiertest decline "+a.getName()))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text("Decline the invitation")))));
        say(b,"You can also use /tiertest accept or /tiertest decline.");
        Bukkit.getScheduler().runTaskLater(core(),()->{if(invites.remove(to,invite)){Player p=Bukkit.getPlayer(from);if(p!=null)say(p,msg("invite-expired","Invitation expired."));}},20L*setting("invitation-seconds",60,5,300));
    }
    private void openBoth(Match m){
        for(UUID id:m.ids()){
            Player p=Bukkit.getPlayer(id);
            if(p==null)continue;
            Resume resume=suspendedMenus.get(id);
            if(resume!=null&&resume.match()==m)continue;
            openStage(p,m);
        }
    }
    private void updateChoices(Player p,Match m){
        openStage(p,m);
        Player other=Bukkit.getPlayer(m.other(p.getUniqueId()));
        if(other!=null&&other.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu&&menu.stage==m.stage&&menu.resume==null)openStage(other,m);
    }
    private String stepTitle(Stage stage){return switch(stage){case TYPE->"Test Type";case KIT->"Your Kit";case BIOME->"Select Biome";case READY->"Ready";default->"Waiting";};}
    private int stepNumber(Stage stage){return switch(stage){case TYPE->1;case KIT->2;case BIOME->3;case READY->4;default->0;};}
    private String choice(boolean chosen){return chosen?"§a✔ Selected":"§7Click to select";}
    private void stageFooter(Menu menu,Player p,Match m){
        footer(menu,18,26);
        int current=stepNumber(m.stage);
        String[] steps={"Test Type","Your Kit","Biome","Ready"};
        for(int i=0;i<4;i++){
            int number=i+1;
            Material material=number<current?Material.LIME_STAINED_GLASS_PANE:number==current?Material.LIGHT_BLUE_STAINED_GLASS_PANE:Material.GRAY_STAINED_GLASS_PANE;
            menu.inventory.setItem(18+i,icon(material,(number<current?"§a":number==current?"§b":"§7")+number+". "+steps[i]));
        }
        menu.inventory.setItem(23,icon(Material.CLOCK,"§bStep "+current+" / 4","Opponent: "+name(m.other(p.getUniqueId())),"Close to change settings; move to resume."));
        put(menu,26,icon(Material.BARRIER,"§cCancel Tier Test","End this setup for both players."),()->finish(m,null,"Tier test cancelled."));
    }
    private String status(Object value){return value==null?"§7Waiting...":"§a"+value;}
    private void openStage(Player p,Match m){
        if(!current(m)||m.backedUp)return;
        UUID id=p.getUniqueId(),other=m.other(id);
        Menu menu=menu(p,27,stepTitle(m.stage));menu.stage=m.stage;
        switch(m.stage){
            case TYPE->{
                for(int i=0;i<18;i++)if(i!=11&&i!=15&&i!=4&&i!=13)decoration(menu,i,Material.BLACK_STAINED_GLASS_PANE);
                menu.inventory.setItem(4,icon(Material.NETHER_STAR,"§bChoose your test","Both players must select the same type."));
                for(TierKitRules.Type type:TierKitRules.Type.values()){
                    boolean below=type==TierKitRules.Type.BELOW,chosen=Objects.equals(m.types.get(id),type);
                    String path="rules."+type.key()+".";
                    int totems=getConfig().getInt(path+"limits.TOTEM_OF_UNDYING",below?14:8);
                    int rockets=getConfig().getInt(path+"limits.FIREWORK_ROCKET",getConfig().getInt("rules.limits.FIREWORK_ROCKET",24));
                    int slot=below?11:15;
                    ItemStack item=selected(icon(configuredIcon(type.key()+"-icon",below?Material.DIAMOND_SWORD:Material.NETHERITE_SWORD),
                            (below?"§b§l":"§d§l")+typeName(type),"§fFirst to "+wins(type)+" wins"," ",
                            "§7Totems: §f"+totems,"§7Rockets: §f"+rockets,
                            below?"§cShulkers & Ender Chests disabled":"§aShulkers & Ender Chests allowed",
                            getConfig().getBoolean(path+"allow-weakness-arrows",!below)?"§aWeakness arrows allowed":"§cWeakness arrows disabled"," ",choice(chosen)),chosen);
                    put(menu,slot,item,()->{
                        if(!current(m)||m.stage!=Stage.TYPE)return;
                        m.types.put(id,type);
                        if(m.types.get(m.a)==type&&m.types.get(m.b)==type){m.type=type;m.stage=Stage.KIT;openBoth(m);}
                        else{tell(m,name(id)+" selected "+typeName(type)+".");updateChoices(p,m);}
                    });
                }
                TierKitRules.Type own=m.types.get(id),opponent=m.types.get(other);
                menu.inventory.setItem(13,icon(Material.PAPER,"§bSelections","You: "+status(own==null?null:typeName(own)),"Opponent: "+status(opponent==null?null:typeName(opponent))));
            }
            case KIT->{
                KitModule kits=(KitModule)core().module("kits");
                for(int n=1;n<=9;n++){
                    int number=n;var saved=kits.snapshotForTierTest(id,n);boolean chosen=("Kit "+n).equals(m.kitNames.get(id));
                    if(saved.isPresent())put(menu,n-1,selected(icon(Material.CHEST,"§bKit "+n,chosen?"§a✔ Selected":"§7Saved personal kit","Click to preview and validate"),chosen),()->preview(p,m,saved.get(),"Kit "+number));
                    else put(menu,n-1,icon(Material.TRAPPED_CHEST,"§bCreate Kit "+n,"Click to create using the kitroom"),()->editKit(p,m,number));
                }
                if(premades.contains(m.type.key()+".storage"))put(menu,13,selected(icon(configuredIcon("premade-icon",Material.ENDER_CHEST),"§b§l"+typeName(m.type)+" Premade","Official ready-to-use kit","Click to preview"),"Premade".equals(m.kitNames.get(id))),()->preview(p,m,readKit(premades,m.type.key()),"Premade"));
                else menu.inventory.setItem(13,icon(Material.BARRIER,"§cPremade kit unavailable","Staff have not saved this kit yet."));
                put(menu,10,icon(Material.CRAFTING_TABLE,"§b§lCreate / Edit Kit","Choose a personal kit slot","Build it using the shared kitroom"),()->chooseEditSlot(p,m));
                menu.inventory.setItem(16,icon(Material.PAPER,"§bKit Status","You: "+status(m.kitNames.get(id)),"Opponent: "+status(m.kits.containsKey(other)?"Ready":null),"Saved kits are never changed."));
            }
            case BIOME->{
                menu.inventory.setItem(4,icon(Material.COMPASS,"§bChoose an arena","Both players must agree on the biome."));
                for(int i=0;i<BIOMES.size();i++){
                    String biome=BIOMES.get(i);boolean chosen=Objects.equals(m.biomes.get(id),biome);
                    put(menu,11+i,selected(icon(ICONS.get(i),"§b"+biomeName(biome),"Random location in this world","Spawn distance: "+setting("spawn-distance",15,1,64)+" blocks"," ",choice(chosen)),chosen),()->{
                        if(!current(m)||m.stage!=Stage.BIOME)return;m.biomes.put(id,biome);
                        if(biome.equals(m.biomes.get(m.a))&&biome.equals(m.biomes.get(m.b))){m.biome=biome;m.stage=Stage.READY;openBoth(m);}
                        else{tell(m,name(id)+" selected "+biomeName(biome)+".");updateChoices(p,m);}
                    });
                }
                String own=m.biomes.get(id),opponent=m.biomes.get(other);
                menu.inventory.setItem(17,icon(Material.PAPER,"§bSelections","You: "+status(own==null?null:biomeName(own)),"Opponent: "+status(opponent==null?null:biomeName(opponent))));
            }
            case READY->{
                menu.inventory.setItem(4,icon(Material.NETHER_STAR,"§b§lMatch Summary",typeName(m.type),"First to "+wins(m.type)+" wins"));
                Player rival=Bukkit.getPlayer(other);
                ItemStack head=icon(de.minenight.allinone.GuiPlayerIcons.material(p),"§b"+name(other),m.ready.contains(other)?"§a✔ Ready":"§7Waiting for confirmation");
                if(rival!=null)de.minenight.allinone.GuiPlayerIcons.applyProfile(head,rival);
                menu.inventory.setItem(10,head);
                menu.inventory.setItem(12,icon(Material.CHEST,"§bYour Kit",m.kitNames.get(id),"Validated and ready"));
                menu.inventory.setItem(14,icon(ICONS.get(BIOMES.indexOf(m.biome)),"§b"+biomeName(m.biome),"Spawn distance: "+setting("spawn-distance",15,1,64)+" blocks"));
                boolean ready=m.ready.contains(id);
                put(menu,16,selected(icon(ready?Material.LIME_STAINED_GLASS_PANE:Material.EMERALD,ready?"§a§l✔ READY":"§a§lCONFIRM READY"," ",ready?"Waiting for your opponent":"Click to start when both are ready"),ready),()->{
                    if(!current(m)||m.stage!=Stage.READY)return;m.ready.add(id);
                    if(m.ready.size()==2)beginMatch(m);else{tell(m,name(id)+" is ready.");updateChoices(p,m);}
                });
            }
            default->{return;}
        }
        stageFooter(menu,p,m);display(p,menu);
    }
    private void preview(Player p,Match m,TierKitSnapshot raw,String kitName){
        if(!current(m)||m.stage!=Stage.KIT)return;
        TierKitRules.Result checked=rules(m.type).sanitize(raw);
        Menu menu=menu(p,54,"Preview · "+kitName);menu.stage=Stage.KIT;menu.resume=()->preview(p,m,raw,kitName);
        TierKitSnapshot kit=checked.kit();ItemStack[] storage=kit.storage(),armor=kit.armor();
        for(int i=0;i<storage.length;i++)menu.inventory.setItem(i,storage[i]);
        for(int i=0;i<4;i++)menu.inventory.setItem(36+i,armor[i]);
        menu.inventory.setItem(40,kit.offhand());footer(menu,45,53);
        put(menu,45,icon(Material.ARROW,"§bBack to kits"),()->openStage(p,m));
        if(kitName.startsWith("Kit "))put(menu,53,icon(Material.CRAFTING_TABLE,"§bEdit this kit"),()->editKit(p,m,Integer.parseInt(kitName.substring(4))));
        put(menu,47,icon(Material.ENDER_CHEST,"§bEnder Chest","Preview the validated contents"),()->previewEnder(p,m,raw,kitName,kit));
        menu.inventory.setItem(51,icon(checked.changes().isEmpty()?Material.LIME_DYE:Material.PAPER,checked.changes().isEmpty()?"§a✔ Kit passed":"§eKit corrected",checked.changes().size()+" correction(s)","Original kit remains unchanged."));
        put(menu,49,icon(Material.EMERALD,"§a§lSELECT "+kitName.toUpperCase(Locale.ROOT),"Use this validated kit"),()->{
            if(!current(m)||m.stage!=Stage.KIT)return;
            UUID id=p.getUniqueId();m.kits.put(id,kit);m.kitNames.put(id,kitName);report(p,checked);
            if(m.kits.size()==2){m.stage=Stage.BIOME;openBoth(m);}else updateChoices(p,m);
        });
        display(p,menu);
    }
    private void previewEnder(Player p,Match m,TierKitSnapshot raw,String kitName,TierKitSnapshot kit){
        if(!current(m)||m.stage!=Stage.KIT)return;
        Menu ender=menu(p,36,"Ender Chest");ender.stage=Stage.KIT;ender.resume=()->previewEnder(p,m,raw,kitName,kit);
        ItemStack[] contents=kit.ender();
        for(int i=0;i<contents.length;i++)ender.inventory.setItem(i,contents[i]);
        footer(ender,27,35);
        put(ender,31,icon(Material.ARROW,"§bBack to kit preview"),()->preview(p,m,raw,kitName));display(p,ender);
    }
    private void chooseEditSlot(Player p,Match m){
        if(!canEditKit(p))return;
        Menu menu=menu(p,27,"Create / Edit Kit");menu.stage=Stage.KIT;menu.resume=()->chooseEditSlot(p,m);footer(menu,18,26);
        KitModule kits=(KitModule)core().module("kits");
        for(int n=1;n<=9;n++){
            int number=n;boolean exists=kits.snapshotForTierTest(p.getUniqueId(),n).isPresent();
            put(menu,9+n-1,icon(exists?Material.CHEST:Material.TRAPPED_CHEST,"§bKit "+n,exists?"Edit existing kit":"Create new kit"),()->editKit(p,m,number));
        }
        put(menu,22,icon(Material.ARROW,"Back"),()->openStage(p,m));display(p,menu);
    }
    private void editKit(Player p,Match m,int number){
        if(!canEditKit(p))return;
        UUID id=p.getUniqueId();
        Draft draft=m.drafts.get(id);
        if(draft==null||draft.number!=number){
            KitModule kits=(KitModule)core().module("kits");
            TierKitSnapshot initial=kits.snapshotForTierTest(id,number).orElseGet(()->new TierKitSnapshot(new ItemStack[36],new ItemStack[4],null,new ItemStack[27]));
            draft=new Draft(number,initial);m.drafts.put(id,draft);
        }
        editor(p,m,draft,false);
    }
    private boolean draftValid(Player p,Match m,Draft draft){return canEditKit(p)&&m.drafts.get(p.getUniqueId())==draft;}
    private void editor(Player p,Match m,Draft draft,boolean ender){
        if(!draftValid(p,m,draft))return;
        Menu menu=menu(p,54,ender?"Edit Ender Chest":"Edit Kit "+draft.number);
        menu.stage=Stage.KIT;menu.resume=()->editor(p,m,draft,ender);
        int slots=ender?27:41;
        for(int i=0;i<slots;i++){
            int slot=ender?41+i:i;
            ItemStack existing=draft.get(slot);
            String label=slot<36?"Inventory "+(slot+1):slot<40?List.of("Boots","Leggings","Chestplate / Elytra","Helmet").get(slot-36):slot==40?"Offhand":"Ender Chest "+(slot-40);
            ItemStack display=existing==null||existing.getType().isAir()?icon(Material.GRAY_STAINED_GLASS_PANE,"§7"+label,"Click to select an item from Kitroom"):existing.clone();
            put(menu,i,display,()->kitroom(p,m,draft,slot,1,ender));
        }
        footer(menu,45,53);
        put(menu,45,icon(Material.ARROW,"§bBack","Keep the unsaved draft"),()->chooseEditSlot(p,m));
        put(menu,46,icon(Material.BUCKET,"§bUse current inventory","Replace the draft with a copy of your inventory"),()->{
            TierKitSnapshot current=capture(p);Draft replacement=new Draft(draft.number,current);m.drafts.put(p.getUniqueId(),replacement);editor(p,m,replacement,ender);
        });
        if(m.type==TierKitRules.Type.ABOVE)put(menu,47,icon(Material.ENDER_CHEST,ender?"§bEdit main inventory":"§bEdit Ender Chest"),()->editor(p,m,draft,!ender));
        put(menu,49,icon(Material.EMERALD,"§a§lSAVE & SELECT","Validate and save to personal Kit "+draft.number),()->saveDraft(p,m,draft));
        menu.inventory.setItem(51,icon(Material.BOOK,"§bHow to edit","Click a slot, then choose an item from Kitroom.","Clear slots using the Kitroom barrier.","Items never enter your real inventory."));
        put(menu,53,icon(Material.BARRIER,"§cDiscard draft"),()->{m.drafts.remove(p.getUniqueId());openStage(p,m);});
        display(p,menu);
    }
    private boolean fitsSlot(ItemStack item,int slot){
        if(slot<36||slot>=40)return true;
        String type=item.getType().name();
        return switch(slot){case 36->type.endsWith("_BOOTS");case 37->type.endsWith("_LEGGINGS");case 38->type.endsWith("_CHESTPLATE")||item.getType()==Material.ELYTRA;case 39->type.endsWith("_HELMET");default->false;};
    }
    private void kitroom(Player p,Match m,Draft draft,int slot,int page,boolean ender){
        if(!draftValid(p,m,draft))return;
        Menu menu=menu(p,54,"Kitroom · "+page+"/3");menu.stage=Stage.KIT;menu.resume=()->kitroom(p,m,draft,slot,page,ender);
        KitModule kits=(KitModule)core().module("kits");
        ItemStack[] items=kits.tierKitroomPage(page);
        for(int i=0;i<items.length;i++){
            ItemStack item=items[i];
            if(item==null||item.getType().isAir()||!fitsSlot(item,slot))continue;
            put(menu,i,item.clone(),()->{if(draftValid(p,m,draft)){draft.set(slot,item);editor(p,m,draft,ender);}});
        }
        footer(menu,45,53);
        if(page>1)put(menu,45,icon(Material.ARROW,"Previous page"),()->kitroom(p,m,draft,slot,page-1,ender));
        put(menu,47,icon(Material.ARROW,"Back to editor"),()->editor(p,m,draft,ender));
        put(menu,49,icon(Material.BARRIER,"§cClear this slot"),()->{draft.set(slot,null);editor(p,m,draft,ender);});
        menu.inventory.setItem(51,icon(Material.CHEST,"§bShared Kitroom","Choose an item for this slot","Templates are unchanged."));
        if(page<3)put(menu,53,icon(Material.ARROW,"Next page"),()->kitroom(p,m,draft,slot,page+1,ender));
        display(p,menu);
    }
    private void saveDraft(Player p,Match m,Draft draft){
        if(!draftValid(p,m,draft))return;
        TierKitRules.Result result=rules(m.type).sanitize(draft.snapshot());
        try{((KitModule)core().module("kits")).saveTierKit(p,draft.number,result.kit());}
        catch(Exception error){say(p,"§cCould not save kit. Your draft was kept.");getLogger().log(java.util.logging.Level.WARNING,"Tier kit save failed",error);return;}
        m.drafts.remove(p.getUniqueId());
        say(p,"§aKit "+draft.number+" saved.");report(p,result);
        UUID id=p.getUniqueId();
        m.kits.put(id,result.kit());m.kitNames.put(id,"Kit "+draft.number);
        if(m.kits.size()==2){m.stage=Stage.BIOME;openBoth(m);}else updateChoices(p,m);
    }
    private void resumeNow(Player p,Match m){
        if(!current(m)||m.backedUp)return;
        Resume saved=suspendedMenus.remove(p.getUniqueId());
        if(saved!=null&&saved.match()==m&&saved.stage()==m.stage&&saved.action()!=null)saved.action().run();
        else openStage(p,m);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void closeSetup(InventoryCloseEvent event){
        if(!(event.getPlayer() instanceof Player p)||!(event.getInventory().getHolder() instanceof Menu menu))return;
        UUID id=p.getUniqueId();Match m=matches.get(id);
        if(openingMenus.contains(id)||m==null||!current(m)||m.backedUp||menu.stage==null)return;
        suspendedMenus.put(id,new Resume(m,menu.stage,menu.resume,System.currentTimeMillis()+750));
        p.sendActionBar(Component.text("Tier test paused · Move or click to continue",LIGHT));
        p.sendMessage(Component.text("[Continue Tier Test]",LIGHT).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/tiertest")));
    }
    private boolean scheduleResume(Player p){
        UUID id=p.getUniqueId();Resume saved=suspendedMenus.get(id);
        if(saved==null||saved.after()>System.currentTimeMillis()||!current(saved.match())||saved.match().backedUp)return false;
        if(p.getOpenInventory().getTopInventory().getType()!=InventoryType.CRAFTING)return false;
        if(pendingReopen.add(id))Bukkit.getScheduler().runTask(core(),()->{
            pendingReopen.remove(id);
            if(p.isOnline()&&suspendedMenus.get(id)==saved&&p.getOpenInventory().getTopInventory().getType()==InventoryType.CRAFTING)resumeNow(p,saved.match());
        });
        return true;
    }
    @EventHandler public void resumeInput(PlayerInputEvent event){
        var input=event.getInput();
        if(input.isForward()||input.isBackward()||input.isLeft()||input.isRight()||input.isJump()||input.isSneak())scheduleResume(event.getPlayer());
    }
    @EventHandler public void resumeSwing(PlayerAnimationEvent event){scheduleResume(event.getPlayer());}
    private void report(Player p,TierKitRules.Result result){
        if(result.changes().isEmpty()){say(p,"§aKit passed all configured checks.");return;}
        say(p,"§eKit corrections: "+result.changes().size()+" (saved kit unchanged).");
        for(String change:result.changes())p.sendMessage("§7"+change);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void menuClick(InventoryClickEvent event){
        if(!(event.getWhoClicked() instanceof Player p))return;
        if(event.getView().getTopInventory().getHolder() instanceof Menu menu){
            event.setCancelled(true);
            if(!menu.owner.equals(p.getUniqueId())||event.getRawSlot()<0||event.getRawSlot()>=menu.inventory.getSize())return;
            Runnable action=menu.actions.get(event.getRawSlot());if(action!=null)Bukkit.getScheduler().runTask(core(),()->{if(p.isOnline()&&p.getOpenInventory().getTopInventory()==menu.inventory)action.run();});
            return;
        }
        Match m=matches.get(p.getUniqueId());
        if(m==null||!m.backedUp){if(recovery.contains(p.getUniqueId()))event.setCancelled(true);return;}
        if(m.stage!=Stage.ACTIVE){event.setCancelled(true);return;}
        Inventory top=event.getView().getTopInventory();
        
        if(regearItem(event.getCursor())||regearItem(event.getCurrentItem())){
            if(top.getType()!=InventoryType.CRAFTING){event.setCancelled(true);return;}
        }
        if(top.getType()==InventoryType.CRAFTING){
            if(event.getRawSlot()>=0&&event.getRawSlot()<top.getSize())event.setCancelled(true);
        }else if(top.getType()==InventoryType.ENDER_CHEST&&m.type==TierKitRules.Type.ABOVE){
        }else if(top.getHolder() instanceof ShulkerBox box&&p.getUniqueId().equals(m.containers.get(box.getLocation()))){
            if(event.getClick()==ClickType.DOUBLE_CLICK||event.getClick()==ClickType.NUMBER_KEY||event.getClick()==ClickType.SWAP_OFFHAND){event.setCancelled(true);return;}
            if((event.getRawSlot()>=0&&event.getRawSlot()<top.getSize()&&!rules(m.type).allowsShulkerItem(event.getCursor()))
                    ||(event.isShiftClick()&&event.getRawSlot()>=top.getSize()&&!rules(m.type).allowsShulkerItem(event.getCurrentItem())))event.setCancelled(true);
        }else event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void menuDrag(InventoryDragEvent event){
        if(!(event.getWhoClicked() instanceof Player p))return;
        Inventory top=event.getView().getTopInventory();
        if(top.getHolder() instanceof Menu){event.setCancelled(true);return;}
        Match m=matches.get(p.getUniqueId());
        if(m==null||!m.backedUp){if(recovery.contains(p.getUniqueId()))event.setCancelled(true);return;}
        if(m.stage!=Stage.ACTIVE){event.setCancelled(true);return;}
        if(event.getRawSlots().stream().noneMatch(i->i<top.getSize()))return;
        if(regearItem(event.getOldCursor())){event.setCancelled(true);return;}
        if(top.getType()==InventoryType.ENDER_CHEST&&m.type==TierKitRules.Type.ABOVE)return;
        if(top.getHolder() instanceof ShulkerBox box&&p.getUniqueId().equals(m.containers.get(box.getLocation()))&&rules(m.type).allowsShulkerItem(event.getOldCursor()))return;
        event.setCancelled(true);
    }

    private void beginMatch(Match m){
        if(!current(m)||m.backedUp)return;
        for(UUID id:m.ids())suspendedMenus.remove(id);
        String step="validate players";
        try{
            for(UUID id:m.ids()){
                Player p=Objects.requireNonNull(Bukkit.getPlayer(id),"Player disconnected");
                p.closeInventory();
                if(p.isDead()||p.getGameMode()!=GameMode.SURVIVAL)throw new IllegalStateException(p.getName()+" must be alive and in survival mode");
                ItemStack cursor=p.getItemOnCursor();
                if(cursor!=null&&!cursor.getType().isAir()&&cursor.getAmount()>0)throw new IllegalStateException(p.getName()+" still has an item on the cursor");
                m.origins.put(id,p.getLocation().clone());
            }
            for(UUID id:m.ids()){
                Player p=Objects.requireNonNull(Bukkit.getPlayer(id));
                step="save inventory backup for "+p.getName();
                saveBackup(p);
            }
            step="initialize round score";
            m.score=new TierRoundScore(m.a,m.b,wins(m.type));
            m.backedUp=true;
            step="search arena in "+m.biome;
            startRound(m);
        }catch(Exception error){failMatch(m,step,error);}
    }
    private void failMatch(Match m,String step,Throwable error){
        String detail=error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();
        getLogger().log(java.util.logging.Level.WARNING,"[TierTest] "+step+" failed for "+name(m.a)+" vs "+name(m.b)+": "+detail,error);
        finish(m,null,"§cTier test failed during "+step+". "+detail);
    }
    private void createBoard(Match m,Player p){
        Scoreboard previous=originalBoards.getOrDefault(p.getUniqueId(),p.getScoreboard());
        Scoreboard board=Bukkit.getScoreboardManager().getNewScoreboard();
        for(Team old:previous.getTeams()){
            try{
                Team team=board.registerNewTeam(old.getName());
                if(old.prefix()!=null)team.prefix(old.prefix());
                if(old.suffix()!=null)team.suffix(old.suffix());
                if(old.color()!=null)team.color(net.kyori.adventure.text.format.NamedTextColor.nearestTo(old.color()));
                team.setAllowFriendlyFire(old.allowFriendlyFire());team.setCanSeeFriendlyInvisibles(old.canSeeFriendlyInvisibles());
                for(Team.Option option:Team.Option.values()){Team.OptionStatus status=old.getOption(option);if(status!=null)team.setOption(option,status);}
                for(String entry:old.getEntries())team.addEntry(entry);
            }catch(RuntimeException error){getLogger().log(java.util.logging.Level.WARNING,"[TierTest] Could not copy scoreboard team "+old.getName(),error);}
        }
        Objective objective=board.registerNewObjective("mn_tiertest",Criteria.DUMMY,legacy("§9§lMineNight"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);m.boards.put(p.getUniqueId(),objective);p.setScoreboard(board);updateBoard(m);
    }
    private void updateBoard(Match m){
        if(m.score==null)return;
        String[] lines={"§7Tier Test","§b"+typeName(m.type),"§0","§f"+name(m.a)+": §b"+m.score.score().first(),"§f"+name(m.b)+": §b"+m.score.score().second(),"§1","§7First to: §f"+wins(m.type),"§7Biome: §f"+biomeName(m.biome),"§2","§9minenight.de"};
        for(Objective objective:m.boards.values()){
            Scoreboard board=objective.getScoreboard();if(board==null)continue;
            Set<String> keep=new HashSet<>(Arrays.asList(lines));
            for(String entry:new HashSet<>(board.getEntries()))if(!keep.contains(entry)&&objective.getScore(entry).isScoreSet())objective.getScore(entry).resetScore();
            for(int i=0;i<lines.length;i++)objective.getScore(lines[i]).setScore(lines.length-i);
        }
    }
    private int radius(){return setting("arena-radius",150,32,512);}
    private boolean inside(Match m,Location l){return m.center!=null&&Objects.equals(m.center.getWorld(),l.getWorld())&&Math.abs(l.getX()-m.center.getX())<=radius()&&Math.abs(l.getZ()-m.center.getZ())<=radius();}
    private Match area(Location l){for(Match m:matches.values())if(m.playing()&&inside(m,l))return m;return null;}
    private boolean free(Match m,MineNightRTP.TierPair pair){
        Location center=pair.first().clone().add(pair.second().toVector().subtract(pair.first().toVector()).multiply(.5));
        for(Match other:new HashSet<>(matches.values()))if(other!=m&&other.center!=null&&Objects.equals(other.center.getWorld(),center.getWorld())&&Math.abs(other.center.getX()-center.getX())<=radius()*2+32&&Math.abs(other.center.getZ()-center.getZ())<=radius()*2+32)return false;
        for(Player p:center.getWorld().getPlayers())if(!m.ids().contains(p.getUniqueId())&&Math.abs(p.getLocation().getX()-center.getX())<=radius()+32&&Math.abs(p.getLocation().getZ()-center.getZ())<=radius()+32)return false;
        return true;
    }
    private void startRound(Match m){
        if(!current(m)||!m.absent.isEmpty())return;
        m.stage=Stage.SEARCHING;long epoch=++m.epoch;
        m.rerunBy=null;m.rerunUntil=0;m.dead.clear();m.deathScheduled=false;m.resultShown=false;m.rerunUsed.clear();clearContainers(m);
        for(UUID id:m.ids()){
            Player p=Bukkit.getPlayer(id);
            if(p==null){pause(m,id);return;}
            p.closeInventory();
            if(p.isDead()){p.spigot().respawn();Bukkit.getScheduler().runTaskLater(core(),()->{if(current(m)&&m.epoch==epoch)startRound(m);},3);return;}
        }
        if(!(core().module("rtp") instanceof MineNightRTP rtp)){finish(m,null,"RTP module unavailable.");return;}
        tell(m,"Finding a safe arena...");
        rtp.findTierPair(m.biome,setting("spawn-distance",15,1,64),pair->free(m,pair),()->current(m)&&m.epoch==epoch).whenComplete((pair,error)->{
            if(!current(m)||m.epoch!=epoch)return;
            if(error!=null||pair==null){failMatch(m,"arena search in "+m.biome,error==null?new IllegalStateException("No safe location found"):error);return;}
            String step="teleport to arena";
            try{
                m.center=pair.first().clone().add(pair.second().toVector().subtract(pair.first().toVector()).multiply(.5));
                Player a=Bukkit.getPlayer(m.a),b=Bukkit.getPlayer(m.b);
                if(a==null||b==null){finish(m,null,"A player disconnected.");return;}
                internalTeleport.add(m.a);internalTeleport.add(m.b);
                boolean first,second;
                try{
                    first=a.teleport(pair.first(),PlayerTeleportEvent.TeleportCause.COMMAND);
                    second=first&&b.teleport(pair.second(),PlayerTeleportEvent.TeleportCause.COMMAND);
                }finally{internalTeleport.remove(m.a);internalTeleport.remove(m.b);}
                if(!first||!second)throw new IllegalStateException("Teleport cancelled (first="+first+", second="+second+")");
                for(Player p:List.of(a,b)){
                    step="apply kit for "+p.getName();
                    apply(p,m.kits.get(p.getUniqueId()));stats(p);p.setLevel(0);p.setExp(0);p.setTotalExperience(0);
                    step="set view distance for "+p.getName();
                    p.addScoreboardTag("minenight_tiertest");
                    p.setViewDistance(setting("render-distance",12,2,32));
                    step="initialize scoreboard for "+p.getName();
                    if(!m.boards.containsKey(p.getUniqueId()))createBoard(m,p);
                }
                m.enteredArena=true;
                m.stage=Stage.COUNTDOWN;countdown(m,epoch,setting("countdown-seconds",3,0,10));
            }catch(Exception failure){failMatch(m,step,failure);}
        });
        Bukkit.getScheduler().runTaskLater(core(),()->{if(current(m)&&m.epoch==epoch&&m.stage==Stage.SEARCHING)finish(m,null,"Arena search timed out.");},20L*30);
    }
    private void countdown(Match m,long epoch,int seconds){
        if(!current(m)||m.epoch!=epoch||m.stage!=Stage.COUNTDOWN)return;
        if(seconds==0){m.token=m.score.begin();m.stage=Stage.ACTIVE;for(UUID id:m.ids()){Player p=Bukkit.getPlayer(id);if(p!=null)p.sendTitle("§aFIGHT!","",0,15,5);}return;}
        for(UUID id:m.ids()){Player p=Bukkit.getPlayer(id);if(p!=null)p.sendTitle("§b"+seconds,"Get ready",0,20,0);}
        Bukkit.getScheduler().runTaskLater(core(),()->countdown(m,epoch,seconds-1),20);
    }

    @EventHandler(priority=EventPriority.LOWEST) public void death(PlayerDeathEvent event){
        Player p=event.getEntity();Match m=matches.get(p.getUniqueId());
        if(m==null||!m.backedUp)return;
        event.setKeepInventory(true);event.getDrops().clear();event.setKeepLevel(true);event.setDroppedExp(0);
        if(m.stage==Stage.ACTIVE){
            m.dead.add(p.getUniqueId());
            if(!m.deathScheduled){m.deathScheduled=true;long epoch=m.epoch;Bukkit.getScheduler().runTaskLater(core(),()->resolve(m,epoch),2);}
        }
        Bukkit.getScheduler().runTaskLater(core(),()->{if(p.isOnline()&&p.isDead())p.spigot().respawn();},2);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void respawn(PlayerRespawnEvent event){
        Match m=matches.get(event.getPlayer().getUniqueId());
        if(m!=null&&m.backedUp){
            Location spawn=practiceSpawn();
            if(spawn!=null)event.setRespawnLocation(spawn);
            else if(m.center!=null)event.setRespawnLocation(m.center.clone());
            Bukkit.getScheduler().runTaskLater(core(),()->{Player player=event.getPlayer();if(current(m)&&m.stage==Stage.REVIEW&&m.score.winner()!=null&&!m.score.winner().equals(player.getUniqueId()))player.sendTitle("§cYOU LOST!",m.score.score().first()+" - "+m.score.score().second(),0,60,10);},1);
        }
        else if(recovery.contains(event.getPlayer().getUniqueId()))Bukkit.getScheduler().runTaskLater(core(),()->{Player player=event.getPlayer();Location origin=YamlConfiguration.loadConfiguration(backup(player.getUniqueId())).getLocation("location");if(recover(player))returnToOrigin(player,origin);},1);
    }
    private void resolve(Match m,long epoch){
        if(!current(m)||m.epoch!=epoch||m.stage!=Stage.ACTIVE)return;
        if(!m.score.resolve(m.token,m.dead.contains(m.a),m.dead.contains(m.b)))return;
        m.stage=Stage.REVIEW;updateBoard(m);clearContainers(m);
        UUID winner=m.score.winner();
        if(winner!=null){
            announceResult(m,winner);
            m.resultShown=true;
            tell(m,"Final score: "+m.score.score().first()+" - "+m.score.score().second()+". /rerun this round to request a rerun.");
            scheduleReview(m,epoch,setting("final-review-seconds",8,1,30));
        }else{
            tell(m,"Score: "+m.score.score().first()+" - "+m.score.score().second()+". Next round in "+setting("round-review-seconds",3,1,30)+" seconds.");
            for(UUID id:m.dead){Player p=Bukkit.getPlayer(id);if(p!=null&&!p.isDead())waitAtSpawn(m,p);}
            scheduleReview(m,epoch,setting("round-review-seconds",3,1,30));
        }
    }
    private void scheduleReview(Match m,long epoch,int delay){
        Bukkit.getScheduler().runTaskLater(core(),()->{
            if(!current(m)||m.epoch!=epoch||m.stage!=Stage.REVIEW)return;
            long remain=m.rerunUntil-System.currentTimeMillis();
            if(m.rerunBy!=null&&remain>0){scheduleReview(m,epoch,(int)Math.max(1,(remain+999)/1000));return;}
            if(m.score.finalizeRound(m.token)){UUID winner=m.score.winner();if(winner!=null)finish(m,winner,null);else startRound(m);}
        },20L*delay);
    }
    private void requestRerun(Player p,Match m){
        if(m==null||!(m.stage==Stage.ACTIVE||m.stage==Stage.REVIEW)){say(p,"There is no round to rerun.");return;}
        UUID id=p.getUniqueId();long now=System.currentTimeMillis();
        if(m.rerunBy!=null&&m.rerunUntil>=now){
            if(m.rerunBy.equals(id)){say(p,"Rerun already requested.");return;}
            boolean done=m.stage==Stage.ACTIVE?m.score.restartActive(m.token):m.score.rerun(m.token);
            if(done){tell(m,"Rerun accepted.");updateBoard(m);startRound(m);}return;
        }
        if(!m.rerunUsed.add(id)){say(p,"You already requested a rerun this round.");return;}
        m.rerunBy=id;m.rerunUntil=now+1000L*setting("rerun-seconds",8,2,30);
        tell(m,p.getName()+" requested a rerun. Opponent: /rerun this round to accept.");
    }
    private void pause(Match m,UUID id){
        if(!current(m))return;
        if(!m.backedUp){finish(m,null,"Player disconnected.");return;}
        if(m.stage!=Stage.PAUSED){
            if(m.score.phase()==TierRoundScore.Phase.ACTIVE)m.score.restartActive(m.token);
            else if(m.score.phase()==TierRoundScore.Phase.REVIEW)m.score.rerun(m.token);
            m.stage=Stage.PAUSED;++m.epoch;clearContainers(m);updateBoard(m);
        }
        m.absent.add(id);
        int seconds=setting("reconnect-seconds",30,5,120);
        tell(m,name(id)+" disconnected. Waiting "+seconds+" seconds.");
        long epoch=m.epoch;
        Bukkit.getScheduler().runTaskLater(core(),()->{
            if(!current(m)||m.epoch!=epoch||m.stage!=Stage.PAUSED||!m.absent.contains(id))return;
            finish(m,m.absent.contains(m.other(id))?null:m.other(id),"Reconnect time expired.");
        },20L*seconds);
    }
    @EventHandler public void quit(PlayerQuitEvent event){
        Player p=event.getPlayer();UUID id=p.getUniqueId();invites.values().removeIf(i->i.from.equals(id)||i.to.equals(id));
        Match m=matches.get(id);if(m!=null){pause(m,id);if(m.backedUp&&recovery.contains(id))p.setViewDistance(Math.max(2,Math.min(32,YamlConfiguration.loadConfiguration(backup(id)).getInt("view-distance",8))));}
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void join(PlayerJoinEvent event){
        Player p=event.getPlayer();UUID id=p.getUniqueId();Match m=matches.get(id);
        if(m!=null&&m.stage==Stage.PAUSED&&m.absent.remove(id)){
            p.setViewDistance(setting("render-distance",12,2,32));createBoard(m,p);
            if(m.absent.isEmpty())Bukkit.getScheduler().runTaskLater(core(),()->{if(current(m)&&m.stage==Stage.PAUSED&&m.absent.isEmpty())startRound(m);},2);
        }else if(recovery.contains(id)){recover(p);}
    }
    private Location practiceSpawn(){return core().module("spawn") instanceof MineNightPracticeSpawn spawn?spawn.spawn():null;}
    private void waitAtSpawn(Match m,Player p){
        Location spawn=practiceSpawn();
        if(spawn==null){getLogger().warning("[TierTest] Practice spawn unavailable.");return;}
        UUID id=p.getUniqueId();
        if(Objects.equals(p.getWorld(),spawn.getWorld())&&p.getLocation().distanceSquared(spawn)<1)return;
        internalTeleport.add(id);
        try{if(!p.teleport(spawn,PlayerTeleportEvent.TeleportCause.COMMAND))getLogger().warning("[TierTest] Waiting spawn teleport rejected for "+p.getName());}
        finally{internalTeleport.remove(id);}
    }
    private void announceResult(Match m,UUID winner){
        String score=m.score==null?"":m.score.score().first()+" - "+m.score.score().second();
        for(UUID id:m.ids()){
            Player p=Bukkit.getPlayer(id);if(p==null||p.isDead())continue;
            boolean won=winner.equals(id);p.sendTitle(won?"§aYOU WON!":"§cYOU LOST!",score,0,60,10);
            if(won)try{p.playSound(p.getLocation(),Sound.valueOf(getConfig().getString("sounds.winner","UI_TOAST_CHALLENGE_COMPLETE")),1,1);}catch(IllegalArgumentException ignored){}
        }
    }
    private void sendSpawn(Player p){if(core().module("spawn") instanceof MineNightPracticeSpawn spawn)spawn.sendSpawn(p);}
    private void finish(Match m,UUID winner,String reason){
        if(m.stage==Stage.END)return;
        m.stage=Stage.END;++m.epoch;clearContainers(m);
        if(winner!=null&&!m.resultShown)announceResult(m,winner);
        for(UUID id:m.ids()){
            matches.remove(id,m);suspendedMenus.remove(id);pendingReopen.remove(id);Player p=Bukkit.getPlayer(id);
            if(p==null)continue;
            p.closeInventory();
            if(reason!=null)say(p,reason);
            if(recovery.contains(id)){
                if(p.isDead()){if(!stopping)Bukkit.getScheduler().runTask(core(),()->{if(p.isOnline()&&p.isDead())p.spigot().respawn();});}
                else if(recover(p)&&!stopping){
                    if(winner!=null&&m.enteredArena)sendSpawn(p);
                    else returnToOrigin(p,m.origins.get(id));
                }
            }
        }
        m.boards.clear();
    }
    private void returnToOrigin(Player p,Location origin){
        if(origin==null||origin.getWorld()==null)return;
        Location here=p.getLocation();
        if(Objects.equals(here.getWorld(),origin.getWorld())&&here.distanceSquared(origin)<1.0)return;
        p.teleportAsync(origin,PlayerTeleportEvent.TeleportCause.COMMAND).whenComplete((ok,error)->{
            if(error!=null||!Boolean.TRUE.equals(ok))getLogger().warning("[TierTest] Could not return "+p.getName()+" to the original location: "+error);
        });
    }
    private void clearContainers(Match m){
        for(Location l:new ArrayList<>(m.containers.keySet())){
            Block block=l.getBlock();if(block.getState() instanceof ShulkerBox||block.getType()==Material.ENDER_CHEST)block.setType(Material.AIR,false);
        }
        m.containers.clear();m.regearBoxes.clear();
        for(UUID id:new ArrayList<>(m.droppedBy.keySet())){
            Entity item=Bukkit.getEntity(id);if(item!=null)item.remove();roundDrops.remove(id,m);
        }
        m.droppedBy.clear();
    }
    @Override public void onDisable(){
        stopping=true;
        for(Match m:new HashSet<>(matches.values()))finish(m,null,"Tier test stopped.");
        for(Player p:Bukkit.getOnlinePlayers())if(recovery.contains(p.getUniqueId())&&!p.isDead())recover(p);
        invites.clear();matches.clear();roundDrops.clear();originalBoards.clear();suspendedMenus.clear();openingMenus.clear();pendingReopen.clear();
    }

    private boolean frozen(Player p){Match m=matches.get(p.getUniqueId());return (m!=null&&m.backedUp&&m.stage!=Stage.ACTIVE)||(m==null&&recovery.contains(p.getUniqueId()));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void commandGuard(PlayerCommandPreprocessEvent event){
        if(!locked(event.getPlayer()))return;
        String command=event.getMessage().substring(1).split(" ",2)[0].toLowerCase(Locale.ROOT);int colon=command.indexOf(':');if(colon>=0)command=command.substring(colon+1);
        if(Set.of("tiertest","rerun","msg","tell","w","r","reply").contains(command))return;
        if(command.equals("regear")&&isActive(event.getPlayer()))return;
        event.setCancelled(true);say(event.getPlayer(),msg("blocked","Action unavailable during a tier test."));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void teleportGuard(PlayerTeleportEvent event){
        Player p=event.getPlayer();UUID id=p.getUniqueId();if(internalTeleport.contains(id))return;
        Match m=matches.get(id);
        if(m!=null&&m.backedUp){
            if(m.stage==Stage.ACTIVE&&event.getCause()==PlayerTeleportEvent.TeleportCause.ENDER_PEARL&&event.getTo()!=null&&inside(m,event.getTo()))return;
            event.setCancelled(true);return;
        }
        if(recovery.contains(id)&&!restoring.contains(id)){event.setCancelled(true);return;}
        if(event.getTo()!=null&&area(event.getTo())!=null)event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void move(PlayerMoveEvent event){
        if(event instanceof PlayerTeleportEvent||event.getTo()==null||!event.hasChangedPosition())return;
        Player p=event.getPlayer();Match m=matches.get(p.getUniqueId());
        if(frozen(p)||(m!=null&&m.playing()&&!inside(m,event.getTo()))||((m==null||!m.playing())&&area(event.getTo())!=null)){
            Location from=event.getFrom().clone();from.setYaw(event.getTo().getYaw());from.setPitch(event.getTo().getPitch());event.setTo(from);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageEvent event){
        if(!(event.getEntity() instanceof Player p))return;
        if(frozen(p)){event.setCancelled(true);return;}
        Match target=matches.get(p.getUniqueId());
        Entity causing=event.getDamageSource().getCausingEntity();
        if(causing instanceof Player source){
            Match attacker=matches.get(source.getUniqueId());
            if(attacker!=target&&((attacker!=null&&attacker.playing())||(target!=null&&target.playing())))event.setCancelled(true);
        }
    }
    private Player actor(Entity entity){
        if(entity instanceof Player p)return p;
        if(entity instanceof Projectile projectile&&projectile.getShooter() instanceof Player p)return p;
        if(entity instanceof TNTPrimed tnt&&tnt.getSource() instanceof Player p)return p;
        return null;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damageBy(EntityDamageByEntityEvent event){
        Player source=actor(event.getDamager());
        Player target=event.getEntity() instanceof Player p?p:null;
        Match a=source==null?null:matches.get(source.getUniqueId()),b=target==null?null:matches.get(target.getUniqueId());
        if(a!=null&&a.playing()&&(a.stage!=Stage.ACTIVE||(target!=null&&a!=b))){event.setCancelled(true);return;}
        if(b!=null&&b.playing()&&(b.stage!=Stage.ACTIVE||(source!=null&&a!=b)))event.setCancelled(true);
        if(source!=null&&a==null&&area(event.getEntity().getLocation())!=null)event.setCancelled(true);
    }
    public boolean isActive(Player player){
        Match m=matches.get(player.getUniqueId());
        return m!=null&&current(m)&&m.stage==Stage.ACTIVE&&!m.deathScheduled;
    }
    private boolean regearItem(ItemStack item){
        return item!=null&&item.getType()==Material.PURPLE_SHULKER_BOX&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().has(regearKey,org.bukkit.persistence.PersistentDataType.STRING);
    }
    private String regearToken(Match m,UUID id){return m.regearId+":"+m.epoch+":"+id;}
    private boolean validRegearItem(ItemStack item,Match m,UUID id){
        return regearItem(item)&&regearToken(m,id).equals(item.getItemMeta().getPersistentDataContainer().get(regearKey,org.bukkit.persistence.PersistentDataType.STRING));
    }
    public boolean regear(Player p){
        Match m=matches.get(p.getUniqueId());UUID id=p.getUniqueId();
        if(!isActive(p)){say(p,"§cRegear is available during an active round only.");return true;}
        for(ItemStack item:p.getInventory().getContents())if(validRegearItem(item,m,id)){say(p,"You already have a regear shulker.");return true;}
        if(m.regearBoxes.containsValue(id)){say(p,"You already placed a regear shulker.");return true;}
        if(p.getInventory().firstEmpty()<0){say(p,"Free an inventory slot first.");return true;}
        ItemStack item=icon(Material.PURPLE_SHULKER_BOX,"§bRegear Shulker","Single-use","Place after the normal combat delay","Restocks your validated tier test kit");
        ItemMeta meta=item.getItemMeta();meta.getPersistentDataContainer().set(regearKey,org.bukkit.persistence.PersistentDataType.STRING,regearToken(m,id));item.setItemMeta(meta);
        p.getInventory().addItem(item);say(p,"Regear shulker added.");return true;
    }
    private boolean regearReady(Player p){
        long wait=((KitModule)core().module("kits")).regearWaitMillis(p);
        if(wait<=0)return true;
        say(p,"§cWait "+((wait+999)/1000)+" seconds after taking damage.");return false;
    }
    private void collectExternal(ItemStack item,List<ItemStack> result){
        if(item==null||item.getType().isAir())return;
        result.add(item);
        if(item.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta meta&&meta.getBlockState() instanceof ShulkerBox box)
            for(ItemStack nested:box.getSnapshotInventory().getContents())collectExternal(nested,result);
    }
    private void openRegear(Player p,Match m){
        Menu menu=menu(p,27,"Regear");footer(menu,18,26);
        put(menu,13,icon(Material.LIME_DYE,"§a§lRegear Now","Restock the existing kitroom whitelist","Only items from your validated kit"),()->{
            if(!isActive(p)||matches.get(p.getUniqueId())!=m||!regearReady(p))return;
            List<ItemStack> external=new ArrayList<>();
            for(ItemStack item:p.getEnderChest().getContents())collectExternal(item,external);
            for(ItemStack item:p.getInventory().getContents())if(item!=null&&item.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta meta&&meta.getBlockState() instanceof ShulkerBox box)
                for(ItemStack nested:box.getSnapshotInventory().getContents())collectExternal(nested,external);
            for(var entry:m.containers.entrySet())if(entry.getValue().equals(p.getUniqueId())&&entry.getKey().getBlock().getState() instanceof ShulkerBox box)
                for(ItemStack item:box.getSnapshotInventory().getContents())collectExternal(item,external);
            for(var entry:m.droppedBy.entrySet())if(entry.getValue().equals(p.getUniqueId())&&Bukkit.getEntity(entry.getKey()) instanceof Item item&&item.isValid())collectExternal(item.getItemStack(),external);
            ((KitModule)core().module("kits")).refillTierPlayer(p,m.kits.get(p.getUniqueId()),external);p.closeInventory();
        });
        display(p,menu);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pickup(EntityPickupItemEvent event){
        Item item=event.getItem();Match dropped=roundDrops.get(item.getUniqueId());
        if(event.getEntity() instanceof Player p){
            Match m=matches.get(p.getUniqueId());
            if(dropped!=null){
                if(m!=dropped||!isActive(p)||!p.getUniqueId().equals(dropped.droppedBy.get(item.getUniqueId()))||!inside(m,item.getLocation()))event.setCancelled(true);
                return;
            }
            if(locked(p)||area(item.getLocation())!=null)event.setCancelled(true);
        }else if(dropped!=null)event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void drop(PlayerDropItemEvent event){
        Player p=event.getPlayer();Match m=matches.get(p.getUniqueId());
        if(locked(p)&&(!isActive(p)||regearItem(event.getItemDrop().getItemStack())||!inside(m,event.getItemDrop().getLocation())))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void trackDrop(PlayerDropItemEvent event){
        Player p=event.getPlayer();Match m=matches.get(p.getUniqueId());
        if(m!=null&&isActive(p)){
            UUID item=event.getItemDrop().getUniqueId();
            roundDrops.put(item,m);m.droppedBy.put(item,p.getUniqueId());
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void mergeDrop(ItemMergeEvent event){
        if(roundDrops.containsKey(event.getEntity().getUniqueId())||roundDrops.containsKey(event.getTarget().getUniqueId()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hopperDrop(InventoryPickupItemEvent event){
        if(roundDrops.containsKey(event.getItem().getUniqueId()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void craft(CraftItemEvent event){
        if(event.getWhoClicked() instanceof Player p&&locked(p))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void interact(PlayerInteractEvent event){
        Player p=event.getPlayer();Match m=matches.get(p.getUniqueId());Block block=event.getClickedBlock();
        if(scheduleResume(p)){event.setCancelled(true);return;}
        if(frozen(p)){event.setCancelled(true);return;}
        if(block!=null){
            if(m!=null&&m.regearBoxes.containsKey(block.getLocation())){
                event.setCancelled(true);
                if(event.getAction()==Action.RIGHT_CLICK_BLOCK&&event.getHand()==EquipmentSlot.HAND&&isActive(p)&&p.getUniqueId().equals(m.regearBoxes.get(block.getLocation()))&&regearReady(p)){
                    m.regearBoxes.remove(block.getLocation());m.containers.remove(block.getLocation());block.setType(Material.AIR,false);openRegear(p,m);
                }
                return;
            }
            Match region=area(block.getLocation());
            if(region!=null&&(region!=m||!m.playing())){event.setCancelled(true);return;}
            if(m!=null&&m.playing()&&block.getState() instanceof org.bukkit.block.Container&&!(block.getState() instanceof ShulkerBox&&p.getUniqueId().equals(m.containers.get(block.getLocation())))){event.setCancelled(true);return;}
            if(m!=null&&m.playing()&&block.getType()==Material.ENDER_CHEST&&(m.type!=TierKitRules.Type.ABOVE||!m.containers.containsKey(block.getLocation())))event.setCancelled(true);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void open(InventoryOpenEvent event){
        if(!(event.getPlayer() instanceof Player p)||event.getInventory().getHolder() instanceof Menu)return;
        Match m=matches.get(p.getUniqueId());Inventory inv=event.getInventory();
        if(m==null||!m.backedUp){if(recovery.contains(p.getUniqueId()))event.setCancelled(true);return;}
        if(m.stage!=Stage.ACTIVE){event.setCancelled(true);return;}
        if(inv.getType()==InventoryType.ENDER_CHEST&&m.type==TierKitRules.Type.ABOVE)return;
        if(inv.getHolder() instanceof ShulkerBox box&&p.getUniqueId().equals(m.containers.get(box.getLocation())))return;
        event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void place(BlockPlaceEvent event){
        Player p=event.getPlayer();Match m=matches.get(p.getUniqueId());Match region=area(event.getBlockPlaced().getLocation());
        if(frozen(p)||(region!=null&&(region!=m||!m.playing()))){event.setCancelled(true);return;}
        if(m==null||!m.playing())return;
        if(m.stage!=Stage.ACTIVE||region!=m){event.setCancelled(true);return;}
        Block block=event.getBlockPlaced();boolean shulker=block.getState() instanceof ShulkerBox;
        if(shulker&&regearItem(event.getItemInHand())){
            if(!validRegearItem(event.getItemInHand(),m,p.getUniqueId())||!regearReady(p)||m.regearBoxes.containsValue(p.getUniqueId()))event.setCancelled(true);
            return;
        }
        if(shulker||block.getType()==Material.ENDER_CHEST){
            if(m.type!=TierKitRules.Type.ABOVE){event.setCancelled(true);return;}
        }else if(block.getState() instanceof org.bukkit.block.Container){event.setCancelled(true);}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void placed(BlockPlaceEvent event){
        Match m=matches.get(event.getPlayer().getUniqueId());Block block=event.getBlockPlaced();
        if(m!=null&&m.stage==Stage.ACTIVE&&(block.getState() instanceof ShulkerBox||block.getType()==Material.ENDER_CHEST)){
            m.containers.put(block.getLocation(),event.getPlayer().getUniqueId());
            if(validRegearItem(event.getItemInHand(),m,event.getPlayer().getUniqueId()))m.regearBoxes.put(block.getLocation(),event.getPlayer().getUniqueId());
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void breakBlock(BlockBreakEvent event){
        Player p=event.getPlayer();Match m=matches.get(p.getUniqueId());Block block=event.getBlock();Match region=area(block.getLocation());
        if(frozen(p)||(region!=null&&(region!=m||!m.playing()))){event.setCancelled(true);return;}
        if(m==null||!m.playing())return;
        if(m.stage!=Stage.ACTIVE||region!=m){event.setCancelled(true);return;}
        if(m.containers.containsKey(block.getLocation())){
            event.setCancelled(true);
            if(!p.getUniqueId().equals(m.containers.get(block.getLocation())))return;
            if(m.regearBoxes.remove(block.getLocation())!=null){m.containers.remove(block.getLocation());block.setType(Material.AIR,false);return;}
            if(p.getInventory().firstEmpty()<0){say(p,"Free an inventory slot first.");return;}
            ItemStack item=new ItemStack(block.getType());
            if(block.getState() instanceof ShulkerBox box&&item.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta meta){meta.setBlockState(box);item.setItemMeta(meta);}
            p.getInventory().addItem(item);m.containers.remove(block.getLocation());block.setType(Material.AIR,false);return;
        }
        if(block.getState() instanceof org.bukkit.block.Container||block.getType()==Material.ENDER_CHEST){event.setCancelled(true);return;}
        event.setDropItems(false);event.setExpToDrop(0);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hopper(InventoryMoveItemEvent event){
        for(Inventory inv:List.of(event.getSource(),event.getDestination()))if(inv.getLocation()!=null&&area(inv.getLocation())!=null){event.setCancelled(true);return;}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityExplosion(EntityExplodeEvent event){protectExplosion(event.getLocation(),event.blockList());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void blockExplosion(BlockExplodeEvent event){protectExplosion(event.getBlock().getLocation(),event.blockList());}
    private void protectExplosion(Location source,List<Block> blocks){
        Match origin=area(source);
        blocks.removeIf(block->{Match region=area(block.getLocation());return region!=null&&(region!=origin||region.containers.containsKey(block.getLocation())||block.getState() instanceof org.bukkit.block.Container);});
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void lingering(AreaEffectCloudApplyEvent event){
        Match source=event.getEntity().getSource() instanceof Player p?matches.get(p.getUniqueId()):null;
        event.getAffectedEntities().removeIf(entity->{
            if(!(entity instanceof Player p))return false;
            Match target=matches.get(p.getUniqueId());
            return frozen(p)||(source!=target&&((source!=null&&source.playing())||(target!=null&&target.playing())));
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void splash(PotionSplashEvent event){
        Player source=actor(event.getPotion());Match a=source==null?null:matches.get(source.getUniqueId());
        for(LivingEntity entity:event.getAffectedEntities())if(entity instanceof Player p){Match b=matches.get(p.getUniqueId());if(a!=b&&(a!=null&&a.playing()||b!=null&&b.playing())||frozen(p))event.setIntensity(entity,0);}
    }
}

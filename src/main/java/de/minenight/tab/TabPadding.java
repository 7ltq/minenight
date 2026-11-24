package de.minenight.tab;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

final class TabPadding {
    private final Map<UUID, Integer> counts = new HashMap<>();
    private final List<UUID> ids = new ArrayList<>();
    private Class<?> packetType;
    private Class<?> actionType;
    private Constructor<?> entryConstructor;
    private Constructor<?> packetConstructor;
    private Constructor<?> removeConstructor;
    private Constructor<?> profileConstructor;
    private Method literal;
    private Object spectator;
    private Field entries;
    private boolean failed;

    TabPadding() {
        for (int i = 0; i < 80; i++) ids.add(UUID.nameUUIDFromBytes(("minenight-tab-padding-" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private void initialize() throws ReflectiveOperationException {
        if (packetType != null) return;
        packetType = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
        actionType = Class.forName(packetType.getName() + "$Action");
        Class<?> entry = Class.forName(packetType.getName() + "$Entry");
        Class<?> profile = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> component = Class.forName("net.minecraft.network.chat.Component");
        Class<?> gameType = Class.forName("net.minecraft.world.level.GameType");
        profileConstructor = profile.getConstructor(UUID.class, String.class);
        literal = component.getMethod("literal", String.class);
        spectator = gameType.getField("SPECTATOR").get(null);
        entryConstructor = entry.getDeclaredConstructors()[0];
        entryConstructor.setAccessible(true);
        for (Constructor<?> candidate : packetType.getDeclaredConstructors()) {
            Class<?>[] types = candidate.getParameterTypes();
            if (types.length == 2 && types[0] == EnumSet.class && Collection.class.isAssignableFrom(types[1])) {
                packetConstructor = candidate;
                if (types[1] == List.class) break;
            }
        }
        if (packetConstructor == null) throw new NoSuchMethodException("Player info update constructor");
        packetConstructor.setAccessible(true);
        entries = packetType.getDeclaredField("entries");
        entries.setAccessible(true);
        removeConstructor = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket").getConstructor(List.class);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object additions(int from, int to) throws ReflectiveOperationException {
        EnumSet actions = EnumSet.noneOf((Class) actionType);
        for (String name : List.of("ADD_PLAYER", "UPDATE_LISTED", "UPDATE_GAME_MODE", "UPDATE_LATENCY", "UPDATE_DISPLAY_NAME", "UPDATE_LIST_ORDER", "UPDATE_HAT")) {
            actions.add(Enum.valueOf((Class) actionType, name));
        }
        List<Object> values = new ArrayList<>();
        for (int i = from; i < to; i++) {
            UUID id = ids.get(i);
            Object profile = profileConstructor.newInstance(id, String.format(java.util.Locale.ROOT, "mn_blank_%02d", i));
            Object display = literal.invoke(null, "                        ");
            Class<?>[] types = entryConstructor.getParameterTypes();
            Object[] args = new Object[types.length];
            int boolIndex = 0;
            int intIndex = 0;
            for (int j = 0; j < types.length; j++) {
                Class<?> type = types[j];
                if (type == UUID.class) args[j] = id;
                else if (type.isInstance(profile)) args[j] = profile;
                else if (type.isInstance(spectator)) args[j] = spectator;
                else if (type.isInstance(display)) args[j] = display;
                else if (type == boolean.class) args[j] = boolIndex++ == 0;
                else if (type == int.class) args[j] = intIndex++ == 0 ? -1 : 0;
                else if (type.isPrimitive()) throw new IllegalStateException("Unsupported player info field " + type);
            }
            values.add(entryConstructor.newInstance(args));
        }
        Object packet = packetConstructor.newInstance(actions, List.of());
        entries.set(packet, List.copyOf(values));
        return packet;
    }

    private void send(Player viewer, Object packet) throws ReflectiveOperationException {
        Object handle = viewer.getClass().getMethod("getHandle").invoke(viewer);
        Object connection = handle.getClass().getField("connection").get(handle);
        for (Method method : connection.getClass().getMethods()) {
            if (method.getName().equals("send") && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isInstance(packet)) {
                method.invoke(connection, packet);
                return;
            }
        }
        throw new NoSuchMethodException("Player connection send");
    }

    void update(Player viewer, boolean enabled) {
        if (failed) return;
        int visible = 0;
        if (enabled) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (viewer.canSee(target) && viewer.isListed(target)) visible++;
            }
        }
        int desired = enabled ? Math.max(0, 80 - visible) : 0;
        UUID id = viewer.getUniqueId();
        int current = counts.getOrDefault(id, 0);
        if (current == desired) return;
        try {
            initialize();
            if (desired > current) send(viewer, additions(current, desired));
            else send(viewer, removeConstructor.newInstance(List.copyOf(ids.subList(desired, current))));
            if (desired == 0) counts.remove(id);
            else counts.put(id, desired);
        } catch (ReflectiveOperationException | RuntimeException error) {
            failed = true;
            Bukkit.getLogger().warning("MineNight tab padding unavailable: " + error);
        }
    }

    void forget(Player viewer) {
        counts.remove(viewer.getUniqueId());
    }

    void close() {
        if (removeConstructor != null) {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                int count = counts.getOrDefault(viewer.getUniqueId(), 0);
                if (count == 0) continue;
                try {
                    send(viewer, removeConstructor.newInstance(List.copyOf(ids.subList(0, count))));
                } catch (ReflectiveOperationException | RuntimeException error) {
                    Bukkit.getLogger().warning("MineNight tab padding cleanup failed: " + error);
                }
            }
        }
        counts.clear();
    }
}

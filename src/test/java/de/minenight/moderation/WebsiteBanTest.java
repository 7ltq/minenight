package de.minenight.moderation;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.bukkit.command.CommandSender;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WebsiteBanTest {
    private final UUID id = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

    @SuppressWarnings("unchecked")
    private <T> T field(MineNightModeration module, String name) throws Exception {
        var field = MineNightModeration.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(module);
    }

    private JsonObject job(String fingerprint) {
        var job = new JsonObject();
        job.addProperty("uuid", id.toString());
        job.addProperty("fingerprint", fingerprint);
        return job;
    }

    @Test void onlyCurrentNamedBansArePublished() throws Exception {
        var module = new MineNightModeration();
        Map<UUID, MineNightModeration.Sanction> bans = field(module, "bans");
        Map<UUID, String> names = field(module, "names");
        bans.put(id, new MineNightModeration.Sanction("Cheating", 0, "Staff"));
        assertTrue(module.websiteBans().isEmpty());
        names.put(id, "ExamplePlayer");
        var published = module.websiteBans();
        assertEquals(1, published.size());
        assertEquals(64, published.get(0).getAsJsonObject().get("fingerprint").getAsString().length());
        bans.put(id, new MineNightModeration.Sanction("Cheating", 1, "Staff"));
        assertTrue(module.websiteBans().isEmpty());
    }

    @Test void changedBanCannotBeRemovedByAnOldAppeal() throws Exception {
        var module = new MineNightModeration();
        Map<UUID, MineNightModeration.Sanction> bans = field(module, "bans");
        Map<UUID, String> names = field(module, "names");
        names.put(id, "ExamplePlayer");
        bans.put(id, new MineNightModeration.Sanction("Cheating", 0, "Staff"));
        String fingerprint = module.websiteBans().get(0).getAsJsonObject().get("fingerprint").getAsString();
        for (var changed : List.of(new MineNightModeration.Sanction("Griefing", 0, "Staff"),
                new MineNightModeration.Sanction("Cheating", Long.MAX_VALUE, "Staff"),
                new MineNightModeration.Sanction("Cheating", 0, "Another staff member"))) {
            bans.put(id, changed);
            assertFalse(module.websiteUnban(job(fingerprint)));
            assertEquals(changed, bans.get(id));
        }
    }

    @Test void acknowledgementRetryDoesNotCreateAnotherPunishment() {
        var module = new MineNightModeration();
        assertTrue(module.websiteUnban(job("0".repeat(64))));
        assertTrue(module.websiteUnban(job("0".repeat(64))));
    }

    @Test void existingCommandRejectsUnauthorizedSendersBeforeResolvingTargets() {
        var messages = new ArrayList<String>();
        var sender = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(), new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("hasPermission")) return false;
                    if (method.getName().equals("sendMessage")) messages.add(String.valueOf(args[0]));
                    return null;
                });
        assertTrue(new MineNightModeration().onCommand(sender, null, "unban", new String[]{"ExamplePlayer", "appeal_accepted"}));
        assertEquals(List.of("You do not have permission."), messages);
    }
}

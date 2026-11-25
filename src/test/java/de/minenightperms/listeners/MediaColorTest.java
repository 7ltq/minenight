package de.minenightperms.listeners;

import de.minenightperms.model.Group;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MediaColorTest {
    @Test void mediaMatchesTablistInheritanceAndExplicitNameColors() {
        var group = new Group("media");
        group.setRoleGradient("light_purple");
        group.setNameGradient("white");
        var manager = new NameTagManager(null);
        assertEquals(NamedTextColor.nearestTo(net.kyori.adventure.text.format.TextColor.color(0xE52DBB)), manager.nameColorForGroup(group, "ExamplePlayer"));
        group.setNameGradient("aqua");
        assertEquals(NamedTextColor.AQUA, manager.nameColorForGroup(group, "ExamplePlayer"));
        group.setNameGradient("#ffffff");
        assertEquals(NamedTextColor.WHITE, manager.nameColorForGroup(group, "ExamplePlayer"));
        assertEquals("light_purple", group.getRoleGradient());
    }

    @Test void otherRanksKeepExistingColorFallback() {
        var group = new Group("vip");
        group.setRoleGradient("gold");
        group.setNameGradient("white");
        assertEquals(NamedTextColor.GOLD, new NameTagManager(null).nameColorForGroup(group, "ExamplePlayer"));
    }
}

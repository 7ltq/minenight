package de.minenightperms.model;

import java.util.UUID;

public class PlayerData {

    private final UUID uuid;
    private volatile String groupName = "default";

    private final java.util.function.UnaryOperator<String> effectiveGroup;

    public PlayerData(UUID uuid) { this(uuid, java.util.function.UnaryOperator.identity()); }

    public PlayerData(UUID uuid, java.util.function.UnaryOperator<String> effectiveGroup) {
        this.uuid = uuid;
        this.effectiveGroup = effectiveGroup;
    }

    public UUID getUuid() { return uuid; }
    public String getGroupName() { return effectiveGroup.apply(groupName); }
    public String getAdministrativeGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
}

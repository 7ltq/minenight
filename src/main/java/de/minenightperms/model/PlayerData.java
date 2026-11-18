package de.minenightperms.model;

import java.util.UUID;

public class PlayerData {

    private final UUID uuid;
    private String groupName = "default";

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() { return uuid; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
}

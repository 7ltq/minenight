package de.minenight.moderation;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;

public final class MineNightVoiceBridge implements VoicechatPlugin {
    private final MineNightModeration moderation;
    public MineNightVoiceBridge(MineNightModeration moderation) { this.moderation = moderation; }
    @Override public String getPluginId() { return "minenight-moderation"; }
    @Override public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, event -> {
            var connection = event.getSenderConnection();
            if (connection != null && connection.getPlayer() != null && moderation.isMuted(connection.getPlayer().getUuid())) event.cancel();
        });
    }
}

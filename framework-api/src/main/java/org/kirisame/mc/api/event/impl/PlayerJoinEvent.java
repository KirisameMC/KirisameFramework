package org.kirisame.mc.api.event.impl;

import org.kirisame.mc.api.event.Event;

/**
 * Fired when a player joins the server.
 * The player object is the actual Minecraft ServerPlayer instance (accessed via reflection).
 */
public class PlayerJoinEvent extends Event {

    private final Object player;
    private String playerName;

    public PlayerJoinEvent(Object player) {
        this.player = player;
        try {
            var method = player.getClass().getMethod("getDisplayName");
            Object result = method.invoke(player);
            this.playerName = result != null ? result.toString() : "unknown";
        } catch (Exception e) {
            try {
                var method = player.getClass().getMethod("getName");
                Object result = method.invoke(player);
                this.playerName = result != null ? result.toString() : "unknown";
            } catch (Exception ex) {
                this.playerName = "unknown";
            }
        }
    }

    /** Returns the raw Minecraft ServerPlayer object. */
    public Object getPlayer() {
        return player;
    }

    /** Returns the player's display name. */
    public String getPlayerName() {
        return playerName;
    }
}

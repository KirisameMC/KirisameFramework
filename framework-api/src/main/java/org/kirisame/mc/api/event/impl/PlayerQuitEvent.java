package org.kirisame.mc.api.event.impl;

import org.kirisame.mc.api.event.Event;

/**
 * Fired when a player leaves the server.
 */
public class PlayerQuitEvent extends Event {

    private final Object player;
    private String playerName;

    public PlayerQuitEvent(Object player) {
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

    public Object getPlayer() {
        return player;
    }

    public String getPlayerName() {
        return playerName;
    }
}

package org.kirisame.mc.api.event.impl;

import org.kirisame.mc.api.event.Event;

/**
 * Fired when a player sends a chat message.
 */
public class PlayerChatEvent extends Event {

    private final Object player;
    private final String message;
    private String playerName;

    public PlayerChatEvent(Object player, String message) {
        this.player = player;
        this.message = message;
        if (player != null) {
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
        } else {
            this.playerName = "unknown";
        }
    }

    public Object getPlayer() {
        return player;
    }

    public String getMessage() {
        return message;
    }

    public String getPlayerName() {
        return playerName;
    }
}

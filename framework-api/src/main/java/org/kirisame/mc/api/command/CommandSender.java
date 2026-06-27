package org.kirisame.mc.api.command;

/**
 * A command sender (can be console or a player).
 */
public interface CommandSender {
    void sendMessage(String message);
}

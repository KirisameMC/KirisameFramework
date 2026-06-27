package org.kirisame.mc.core.console.message;

/**
 * A parsed Minecraft server log line.
 */
public record ParsedMessage(
        String timestamp,
        String thread,
        String level,
        String content
) {}

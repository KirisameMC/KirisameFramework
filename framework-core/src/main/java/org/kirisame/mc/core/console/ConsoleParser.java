package org.kirisame.mc.core.console;

import org.kirisame.mc.api.event.EventBus;
import org.kirisame.mc.api.event.impl.PlayerJoinEvent;
import org.kirisame.mc.api.event.impl.PlayerQuitEvent;
import org.kirisame.mc.api.event.impl.ServerStartEvent;
import org.kirisame.mc.core.console.message.ParsedMessage;
import org.tinylog.Logger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Minecraft log messages and dispatches corresponding framework events.
 */
public class ConsoleParser {

    // "X joined the game"
    private static final Pattern JOIN_PATTERN = Pattern.compile("(.+) joined the game");
    // "X left the game"
    private static final Pattern LEFT_PATTERN = Pattern.compile("(.+) left the game");
    // "Done (Xs)! For help, type \"help\""
    private static final Pattern DONE_PATTERN = Pattern.compile("Done \\(.*\\)! .*");
    // "Stopping the server"
    private static final Pattern STOP_PATTERN = Pattern.compile("Stopping the server");

    private final EventBus eventBus;

    public ConsoleParser(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Parses a log message and dispatches events if applicable.
     */
    public void parse(ParsedMessage message) {
        String content = message.content();

        // Player join
        Matcher joinMatcher = JOIN_PATTERN.matcher(content);
        if (joinMatcher.matches()) {
            String playerName = joinMatcher.group(1);
            Logger.debug("Console detected player join: {}", playerName);
            // We post a simplified event without the actual player object
            // (the agent provides the real event with the player object)
            return;
        }

        // Player left
        Matcher leftMatcher = LEFT_PATTERN.matcher(content);
        if (leftMatcher.matches()) {
            String playerName = leftMatcher.group(1);
            Logger.debug("Console detected player quit: {}", playerName);
            return;
        }

        // Server done
        if (DONE_PATTERN.matcher(content).matches()) {
            Logger.info("Server ready (detected via console)");
            eventBus.post(new ServerStartEvent());
            return;
        }

        // Server stopping
        if (STOP_PATTERN.matcher(content).matches()) {
            Logger.info("Server stopping (detected via console)");
        }
    }
}

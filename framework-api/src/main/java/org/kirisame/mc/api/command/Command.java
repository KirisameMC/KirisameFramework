package org.kirisame.mc.api.command;

/**
 * A command that can be executed via the console.
 */
@FunctionalInterface
public interface Command {
    void execute(CommandSender sender, String[] args);
}

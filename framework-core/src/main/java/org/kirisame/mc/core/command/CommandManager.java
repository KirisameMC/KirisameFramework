package org.kirisame.mc.core.command;

import org.kirisame.mc.api.command.Command;
import org.kirisame.mc.api.command.CommandInfo;
import org.kirisame.mc.api.command.CommandSender;
import org.tinylog.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages command registration and dispatch.
 */
public class CommandManager {

    private final Map<String, CommandEntry> commands = new ConcurrentHashMap<>();

    /**
     * Registers a command. Reads @CommandInfo for name/aliases.
     */
    public void registerCommand(Command command) {
        CommandInfo info = command.getClass().getAnnotation(CommandInfo.class);
        if (info == null) {
            Logger.warn("Command class {} has no @CommandInfo annotation", command.getClass().getSimpleName());
            return;
        }

        CommandEntry entry = new CommandEntry(command, info);
        commands.put(info.name().toLowerCase(), entry);
        for (String alias : info.aliases()) {
            commands.put(alias.toLowerCase(), entry);
        }

        Logger.debug("Registered command: {} (aliases: {})", info.name(), Arrays.toString(info.aliases()));
    }

    /**
     * Registers a built-in command directly by name (for anonymous/inline commands).
     */
    public void registerCommand(String name, String description, Command command) {
        CommandEntry entry = new CommandEntry(command, null);
        commands.put(name.toLowerCase(), entry);
        Logger.debug("Registered built-in command: {}", name);
    }

    /**
     * Dispatches a console command (without the !! prefix).
     *
     * @param input the command string, e.g. "stop" or "mycommand arg1 arg2"
     * @param sender the command sender
     * @return true if the command was found and executed
     */
    public boolean dispatch(String input, CommandSender sender) {
        String[] parts = input.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return false;

        String commandName = parts[0].toLowerCase();
        String[] args = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];

        CommandEntry entry = commands.get(commandName);
        if (entry == null) {
            sender.sendMessage("Unknown command: " + commandName);
            return false;
        }

        try {
            entry.command().execute(sender, args);
            return true;
        } catch (Exception e) {
            sender.sendMessage("Error executing command '" + commandName + "': " + e.getMessage());
            Logger.error(e, "Error executing command: {}", commandName);
            return true;
        }
    }

    public Set<String> getCommandNames() {
        return Collections.unmodifiableSet(commands.keySet());
    }

    private record CommandEntry(Command command, CommandInfo info) {}
}

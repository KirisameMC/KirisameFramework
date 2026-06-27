package org.kirisame.mc.core.plugin;

import org.kirisame.mc.api.exception.DependencyException;
import org.kirisame.mc.api.plugin.PluginState;
import org.tinylog.Logger;

import java.util.*;

/**
 * Resolves plugin dependencies using Kahn's algorithm for topological sorting.
 * Detects circular dependencies and missing required dependencies.
 */
public class DependencyResolver {

    /**
     * Resolves the given list of discovered plugins: validates dependencies,
     * detects cycles, and returns plugins in topological load order.
     *
     * @param discovered plugins in discovery order
     * @return plugins in topological order (dependencies first)
     * @throws DependencyException on circular or missing required dependencies
     */
    public List<PluginInfo> resolve(List<PluginInfo> discovered) {
        // 1. Build name → PluginInfo map
        Map<String, PluginInfo> byName = new LinkedHashMap<>();
        for (PluginInfo info : discovered) {
            byName.put(info.getName(), info);
        }

        // 2. Validate required dependencies exist
        for (PluginInfo info : discovered) {
            for (String required : info.getRequiredDeps()) {
                if (!byName.containsKey(required)) {
                    throw new DependencyException(
                            "Plugin '" + info.getName() + "' requires '" + required
                                    + "' which is not found in plugins directory");
                }
            }
        }

        // 3. Mark optional dependencies (log warnings for missing)
        for (PluginInfo info : discovered) {
            for (String optional : info.getOptionalDeps()) {
                if (byName.containsKey(optional)) {
                    info.addResolvedDep(optional);
                } else {
                    Logger.warn("Plugin '{}' has optional dependency '{}' which is not available, skipping",
                            info.getName(), optional);
                }
            }
            // All required deps are guaranteed to exist at this point
            for (String required : info.getRequiredDeps()) {
                info.addResolvedDep(required);
            }
        }

        // 4. Kahn's algorithm for topological sort
        // Build in-degree map (only counting resolved dependencies)
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, List<String>> dependents = new LinkedHashMap<>(); // dep → plugins that depend on it

        for (PluginInfo info : discovered) {
            inDegree.putIfAbsent(info.getName(), 0);
            dependents.putIfAbsent(info.getName(), new ArrayList<>());
            for (String dep : info.getResolvedDeps()) {
                inDegree.merge(info.getName(), 0, (a, b) -> a); // ensure entry
                dependents.putIfAbsent(dep, new ArrayList<>());
                dependents.get(dep).add(info.getName());
                inDegree.merge(info.getName(), 1, Integer::sum);
            }
        }

        // Seed the queue with plugins that have no dependencies
        Queue<String> queue = new LinkedList<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<PluginInfo> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String name = queue.poll();
            sorted.add(byName.get(name));
            for (String dependent : dependents.getOrDefault(name, List.of())) {
                int newDegree = inDegree.merge(dependent, -1, Integer::sum);
                if (newDegree == 0) {
                    queue.add(dependent);
                }
            }
        }

        // 5. Check for circular dependencies
        if (sorted.size() != discovered.size()) {
            Set<String> sortedNames = new HashSet<>();
            for (PluginInfo p : sorted) sortedNames.add(p.getName());
            Set<String> involved = new LinkedHashSet<>();
            for (PluginInfo p : discovered) {
                if (!sortedNames.contains(p.getName())) {
                    involved.add(p.getName());
                }
            }
            throw new DependencyException(
                    "Circular dependency detected among plugins: " + involved);
        }

        // Update states
        for (PluginInfo info : sorted) {
            info.setState(PluginState.DEPS_RESOLVED);
        }

        Logger.info("Plugin dependency resolution complete, load order: {}",
                sorted.stream().map(PluginInfo::getName).toList());

        return sorted;
    }
}

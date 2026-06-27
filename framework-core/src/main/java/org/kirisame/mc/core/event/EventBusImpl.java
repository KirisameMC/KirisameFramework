package org.kirisame.mc.core.event;

import org.kirisame.mc.api.event.Cancellable;
import org.kirisame.mc.api.event.Event;
import org.kirisame.mc.api.event.EventBus;
import org.kirisame.mc.api.event.EventHandler;
import org.kirisame.mc.api.event.EventPriority;
import org.tinylog.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * High-performance event bus using MethodHandle for dispatch.
 * Thread-safe for registration and posting.
 */
public class EventBusImpl implements EventBus {

    private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<Registration>> handlers = new ConcurrentHashMap<>();

    @Override
    public void register(Object listener) {
        Class<?> clazz = listener.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            EventHandler annotation = method.getAnnotation(EventHandler.class);
            if (annotation == null) continue;

            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1 || !Event.class.isAssignableFrom(params[0])) {
                Logger.warn("Invalid @EventHandler method: {}.{} — must have exactly one Event parameter",
                        clazz.getSimpleName(), method.getName());
                continue;
            }

            Class<? extends Event> eventType = params[0].asSubclass(Event.class);
            method.setAccessible(true);

            try {
                MethodHandle handle = MethodHandles.lookup().unreflect(method);
                Registration reg = new Registration(listener, handle, annotation.priority(), annotation.ignoreCancelled());

                handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(reg);

                // Also register for parent event classes
                Class<?> superClass = eventType.getSuperclass();
                while (superClass != null && Event.class.isAssignableFrom(superClass)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends Event> superEventType = (Class<? extends Event>) superClass;
                    handlers.computeIfAbsent(superEventType, k -> new CopyOnWriteArrayList<>()).add(reg);
                    superClass = superClass.getSuperclass();
                }
            } catch (IllegalAccessException e) {
                Logger.error(e, "Failed to register event handler: {}.{}", clazz.getSimpleName(), method.getName());
            }
        }
    }

    @Override
    public void unregister(Object listener) {
        for (var entry : handlers.entrySet()) {
            entry.getValue().removeIf(reg -> reg.target == listener);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Event> T post(T event) {
        List<Registration> regs = handlers.get(event.getClass());
        if (regs == null || regs.isEmpty()) {
            return event;
        }

        // Sort by priority (copy to avoid concurrent modification during sort)
        List<Registration> sorted = new ArrayList<>(regs);
        sorted.sort(Comparator.comparingInt(r -> r.priority.ordinal()));

        for (Registration reg : sorted) {
            // Check cancellation
            if (reg.ignoreCancelled && event instanceof Cancellable c && c.isCancelled()) {
                continue;
            }

            try {
                reg.handle.invoke(event);
            } catch (Throwable e) {
                Logger.error(e, "Error dispatching event {} to handler in {}",
                        event.getClass().getSimpleName(), reg.target.getClass().getSimpleName());
            }
        }

        return event;
    }

    private record Registration(
            Object target,
            MethodHandle handle,
            EventPriority priority,
            boolean ignoreCancelled
    ) {}
}

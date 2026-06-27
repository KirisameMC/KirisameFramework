package org.kirisame.mc.core.service;

import org.kirisame.mc.api.service.ServiceRegistry;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple service registry for inter-plugin service discovery.
 */
public class ServiceRegistryImpl implements ServiceRegistry {

    private final ConcurrentHashMap<Class<?>, Object> services = new ConcurrentHashMap<>();

    @Override
    public <T> void register(Class<T> serviceType, T provider) {
        Object existing = services.putIfAbsent(serviceType, provider);
        if (existing != null) {
            throw new IllegalArgumentException(
                    "Service already registered: " + serviceType.getName());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> serviceType) {
        Object provider = services.get(serviceType);
        if (provider == null) {
            throw new NoSuchElementException(
                    "No service registered for: " + serviceType.getName());
        }
        return (T) provider;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> tryGet(Class<T> serviceType) {
        return Optional.ofNullable((T) services.get(serviceType));
    }
}

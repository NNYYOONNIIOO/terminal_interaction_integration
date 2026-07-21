package nyonio.terminal_interaction_integration.api;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import net.minecraft.item.ItemStack;
import nyonio.terminal_interaction_integration.ae2.ResourceFakeMonitor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class TerminalInteractionRegistry {
    private static final Map<String, IResourceProvider> providers = new ConcurrentHashMap<>();
    private static final Map<Class<? extends IStorageChannel<?>>, IResourceProvider> channelToProvider = new ConcurrentHashMap<>();
    private static final Map<String, List<ResourceFakeMonitor>> resourceMonitors = new ConcurrentHashMap<>();
    
    private static volatile boolean initialized = false;
    
    public static void registerResourceProvider(IResourceProvider provider) {
        if (provider == null) return;
        
        String name = provider.getName();
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Resource provider name cannot be null or empty");
        }
        
        IResourceProvider existing = providers.get(name);
        if (existing != null && existing.getPriority() >= provider.getPriority()) {
            return;
        }
        
        providers.put(name, provider);
        
        IStorageChannel<? extends IAEStack<?>> channel = provider.getStorageChannel();
        if (channel != null) {
            channelToProvider.put((Class<? extends IStorageChannel<?>>) channel.getClass(), provider);
        }
    }
    
    public static void unregisterResourceProvider(String name) {
        if (name == null) return;
        
        IResourceProvider removed = providers.remove(name);
        if (removed != null) {
            IStorageChannel<? extends IAEStack<?>> channel = removed.getStorageChannel();
            if (channel != null) {
                channelToProvider.remove(channel.getClass());
            }
        }
    }
    
    public static IResourceProvider getProvider(String name) {
        return providers.get(name);
    }
    
    public static IResourceProvider getProviderByChannel(IStorageChannel<?> channel) {
        if (channel == null) return null;
        return channelToProvider.get(channel.getClass());
    }
    
    public static IResourceProvider getProviderByChannelClass(Class<? extends IStorageChannel<?>> channelClass) {
        return channelToProvider.get(channelClass);
    }
    
    public static List<IResourceProvider> getAllProviders() {
        List<IResourceProvider> sorted = new ArrayList<>(providers.values());
        sorted.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        return sorted;
    }
    
    public static IPacketType getPacketType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        
        for (IResourceProvider provider : getAllProviders()) {
            IPacketType type = provider.getPacketType();
            if (type != null && type.isPacket(stack)) {
                return type;
            }
        }
        return null;
    }
    
    public static IPacketType getPacketTypeByName(String name) {
        if (name == null || name.isEmpty()) return null;
        
        for (IResourceProvider provider : getAllProviders()) {
            IPacketType type = provider.getPacketType();
            if (type != null && type.getName().equals(name)) {
                return type;
            }
        }
        return null;
    }
    
    public static IContainerHandler getContainerHandler(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        
        for (IResourceProvider provider : getAllProviders()) {
            IContainerHandler handler = provider.getContainerHandler();
            if (handler != null && handler.canHandle(stack)) {
                return handler;
            }
        }
        return null;
    }
    
    public static IResourceProvider getProviderForContainer(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        
        for (IResourceProvider provider : getAllProviders()) {
            IContainerHandler handler = provider.getContainerHandler();
            if (handler != null && handler.canHandle(stack)) {
                return provider;
            }
        }
        return null;
    }
    
    public static boolean isPacket(ItemStack stack) {
        return getPacketType(stack) != null;
    }
    
    public static boolean hasContainerHandler(ItemStack stack) {
        return getContainerHandler(stack) != null;
    }
    
    public static List<IPacketType> getPacketTypes() {
        List<IPacketType> types = new ArrayList<>();
        for (IResourceProvider provider : getAllProviders()) {
            IPacketType type = provider.getPacketType();
            if (type != null) {
                types.add(type);
            }
        }
        return types;
    }
    
    public static List<IStorageChannel<? extends IAEStack<?>>> getStorageChannels() {
        List<IStorageChannel<? extends IAEStack<?>>> channels = new ArrayList<>();
        for (IResourceProvider provider : getAllProviders()) {
            IStorageChannel<? extends IAEStack<?>> channel = provider.getStorageChannel();
            if (channel != null) {
                channels.add(channel);
            }
        }
        return channels;
    }
    
    public static void setInitialized(boolean init) {
        initialized = init;
    }
    
    public static boolean isInitialized() {
        return initialized;
    }
    
    public static void registerResourceMonitor(String providerName, ResourceFakeMonitor monitor) {
        if (providerName == null || monitor == null) return;
        resourceMonitors.computeIfAbsent(providerName, k -> new ArrayList<>()).add(monitor);
    }
    
    public static List<ResourceFakeMonitor> getResourceMonitors(String providerName) {
        if (providerName == null) return null;
        return resourceMonitors.get(providerName);
    }
    
    public static void clear() {
        providers.clear();
        channelToProvider.clear();
        resourceMonitors.clear();
        initialized = false;
    }
}

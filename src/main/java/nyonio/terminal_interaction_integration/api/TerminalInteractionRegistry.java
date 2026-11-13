package nyonio.terminal_interaction_integration.api;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEStack;
import net.minecraft.item.ItemStack;
import nyonio.terminal_interaction_integration.ae2.FluidTerminalIntegration;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Shared registration point. Register the same providers on both logical sides. */
public final class TerminalInteractionRegistry {
    private static final Map<String, IResourceProvider> PROVIDERS = new ConcurrentHashMap<>();
    private static volatile boolean initialized;

    private TerminalInteractionRegistry() {
    }

    public static void registerResourceProvider(IResourceProvider provider) {
        if (provider == null || provider.getName() == null || provider.getName().isEmpty()) {
            throw new IllegalArgumentException("A resource provider must have a name");
        }
        if (provider.getStorageChannel() == null || provider.getPacketType() == null) {
            throw new IllegalArgumentException("A provider must have a storage channel and packet type");
        }
        if (provider.getStorageChannel() instanceof IItemStorageChannel) {
            throw new IllegalArgumentException("A virtual resource cannot use the item storage channel");
        }
        PROVIDERS.compute(provider.getName(), (name, old) ->
                old == null || old.getPriority() < provider.getPriority() ? provider : old);
    }

    public static void unregisterResourceProvider(String name) {
        if (name != null) PROVIDERS.remove(name);
    }

    @Nullable
    public static IResourceProvider getProvider(String name) {
        return name == null ? null : PROVIDERS.get(name);
    }

    public static List<IResourceProvider> getAllProviders() {
        List<IResourceProvider> providers = new ArrayList<>(PROVIDERS.values());
        providers.sort(Comparator.comparingInt(IResourceProvider::getPriority).reversed()
                .thenComparing(IResourceProvider::getName));
        return providers;
    }

    @Nullable
    public static IResourceProvider getProviderByChannel(IStorageChannel<?> channel) {
        for (IResourceProvider provider : getAllProviders()) {
            if (provider.getStorageChannel() == channel) return provider;
        }
        return null;
    }

    @Nullable
    public static IResourceProvider getProviderByChannelClass(Class<? extends IStorageChannel<?>> type) {
        for (IResourceProvider provider : getAllProviders()) {
            if (provider.getStorageChannel().getClass() == type) return provider;
        }
        return null;
    }

    @Nullable
    public static IResourceProvider getProviderForPacket(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (IResourceProvider provider : getAllProviders()) {
            if (provider.getPacketType().isPacket(stack)) return provider;
        }
        return FluidTerminalIntegration.forPacket(stack);
    }

    @Nullable
    public static IResourceProvider getProviderForContainer(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (IResourceProvider provider : getAllProviders()) {
            IContainerHandler handler = provider.getContainerHandler();
            if (handler != null && handler.canHandle(stack)) return provider;
        }
        return FluidTerminalIntegration.forContainer(stack);
    }

    @Nullable
    public static IPacketType getPacketType(ItemStack stack) {
        IResourceProvider provider = getProviderForPacket(stack);
        return provider == null ? null : provider.getPacketType();
    }

    @Nullable
    public static IPacketType getPacketTypeByName(String name) {
        for (IResourceProvider provider : getAllProviders()) {
            if (provider.getPacketType().getName().equals(name)) return provider.getPacketType();
        }
        return null;
    }

    @Nullable
    public static IContainerHandler getContainerHandler(ItemStack stack) {
        IResourceProvider provider = getProviderForContainer(stack);
        return provider == null ? null : provider.getContainerHandler();
    }

    public static boolean isPacket(ItemStack stack) {
        return FluidTerminalIntegration.isFluidPacket(stack) || getPacketType(stack) != null;
    }
    public static boolean hasContainerHandler(ItemStack stack) { return getContainerHandler(stack) != null; }

    public static List<IPacketType> getPacketTypes() {
        List<IPacketType> result = new ArrayList<>();
        for (IResourceProvider provider : getAllProviders()) result.add(provider.getPacketType());
        return result;
    }

    public static List<IStorageChannel<? extends IAEStack<?>>> getStorageChannels() {
        List<IStorageChannel<? extends IAEStack<?>>> result = new ArrayList<>();
        for (IResourceProvider provider : getAllProviders()) result.add(provider.getStorageChannel());
        return result;
    }

    public static void setInitialized(boolean value) { initialized = value; }
    public static boolean isInitialized() { return initialized; }
    public static void clear() { PROVIDERS.clear(); initialized = false; }
}

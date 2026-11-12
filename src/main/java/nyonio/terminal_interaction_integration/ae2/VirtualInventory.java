package nyonio.terminal_interaction_integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import net.minecraft.entity.player.PlayerEntity;
import nyonio.terminal_interaction_integration.api.IResourceProvider;

import javax.annotation.Nonnull;
import java.util.Optional;

/** Converts virtual item operations to operations on the provider's real channel. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class VirtualInventory {
    private VirtualInventory() {
    }

    public static IMEMonitor monitor(IStorageGrid grid, IResourceProvider provider) {
        return grid == null ? null : grid.getInventory((appeng.api.storage.IStorageChannel) provider.getStorageChannel());
    }

    public static long extract(IStorageGrid grid, IResourceProvider provider, long amount,
                               Actionable mode, IActionSource source) {
        if (amount <= 0) return 0;
        IMEMonitor monitor = monitor(grid, provider);
        IAEStack request = provider.getPacketType().createResourceStack(amount);
        if (monitor == null || request == null) return 0;
        request.setStackSize(amount);
        IAEStack result = (IAEStack) monitor.extractItems(request, mode, new VirtualSource(source));
        return result == null ? 0 : Math.max(0, Math.min(amount, result.getStackSize()));
    }

    public static long insert(IStorageGrid grid, IResourceProvider provider, long amount,
                              Actionable mode, IActionSource source) {
        if (amount <= 0) return 0;
        IMEMonitor monitor = monitor(grid, provider);
        IAEStack request = provider.getPacketType().createResourceStack(amount);
        if (monitor == null || request == null) return 0;
        request.setStackSize(amount);
        IAEStack remaining = (IAEStack) monitor.injectItems(request, mode, new VirtualSource(source));
        long remainder = remaining == null ? 0 : Math.max(0, Math.min(amount, remaining.getStackSize()));
        return amount - remainder;
    }

    public static IAEItemStack insertItem(IStorageGrid grid, IResourceProvider provider,
                                          IAEItemStack stack, Actionable mode, IActionSource source) {
        long size = stack.getStackSize();
        long inserted = insert(grid, provider, size, mode, source);
        return inserted >= size ? null : provider.getPacketType().createAEStack(size - inserted);
    }

    /** Marker prevents the bridge from recursively intercepting its own resource operations. */
    public static final class VirtualSource implements IActionSource {
        private final IActionSource source;
        public VirtualSource(IActionSource source) { this.source = source; }
        @Override @Nonnull public Optional<PlayerEntity> player() { return source.player(); }
        @Override @Nonnull public Optional<IActionHost> machine() { return source.machine(); }
        @Override @Nonnull public <T> Optional<T> context(@Nonnull Class<T> key) { return source.context(key); }
    }
}

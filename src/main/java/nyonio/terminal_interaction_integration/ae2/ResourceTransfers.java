package nyonio.terminal_interaction_integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import net.minecraft.item.ItemStack;
import nyonio.terminal_interaction_integration.api.IContainerHandler;
import nyonio.terminal_interaction_integration.api.IResourceProvider;
import nyonio.terminal_interaction_integration.compat.crazyae.CrazyAEIntegration;

/** Terminal-only routing. Does not replace AE2 inventories or move stored balances. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class ResourceTransfers {
    private ResourceTransfers() { }

    public static long insert(IStorageGrid grid, IResourceProvider provider, long amount,
                              Actionable mode, IActionSource source) {
        return transfer(grid, provider, amount, mode, source, true);
    }

    public static long extract(IStorageGrid grid, IResourceProvider provider, long amount,
                               Actionable mode, IActionSource source) {
        return transfer(grid, provider, amount, mode, source, false);
    }

    /** Prepare a filled copy before debiting storage, including containers with per-call transfer limits. */
    public static ItemStack fillMappedContainer(IStorageGrid grid, IResourceProvider provider, ItemStack container,
                                                long requested, IActionSource source) {
        IContainerHandler handler = provider.getContainerHandler();
        long available = extract(grid, provider, requested, Actionable.SIMULATE, source);
        if (available <= 0) return null;
        ItemStack prepared = container.copy();
        long accepted = Math.max(0, Math.min(available, handler.inject(prepared, available, source)));
        if (accepted <= 0) return null;
        long extracted = extract(grid, provider, accepted, Actionable.MODULATE, source);
        if (extracted == accepted) return prepared;
        if (extracted <= 0) return null;
        // If another storage callback changed availability, fill only the actual debit.
        long inserted = handler.inject(container, extracted, source);
        if (inserted < extracted) insert(grid, provider, extracted - inserted, Actionable.MODULATE, source);
        return inserted > 0 ? container : null;
    }

    private static long transfer(IStorageGrid grid, IResourceProvider provider, long amount,
                                 Actionable mode, IActionSource source, boolean insert) {
        if (grid == null || provider == null || amount <= 0) return 0;
        Source[] sources = CrazyAEIntegration.sources(provider);
        if (sources == null) {
            IAEStack<?> key = provider.getPacketType().createResourceStack(1);
            if (key == null || provider.getStorageChannel() == null) return 0;
            sources = new Source[] { new Source(provider.getStorageChannel(), key) };
        }
        return transferSources(grid, sources, amount, mode, source, insert);
    }

    public static long transferSources(IStorageGrid grid, Source[] sources, long amount,
                                        Actionable mode, IActionSource source, boolean insert) {
        if (amount <= 0) return 0;
        long moved = 0;
        for (Source target : sources) {
            if (moved >= amount) break;
            IMEMonitor monitor = grid.getInventory((IStorageChannel) target.channel);
            if (monitor == null) continue;
            long requested = amount - moved;
            IAEStack request = target.key.copy();
            request.setStackSize(requested);
            IAEStack result = insert ? monitor.injectItems(request, mode, source)
                    : monitor.extractItems(request, mode, source);
            long resultAmount = result == null ? 0 : Math.max(0, Math.min(requested, result.getStackSize()));
            moved += insert ? requested - resultAmount : resultAmount;
        }
        return moved;
    }

    public static final class Source {
        public final IStorageChannel<?> channel;
        public final IAEStack<?> key;

        public Source(IStorageChannel<?> channel, IAEStack<?> key) {
            this.channel = channel;
            this.key = key.copy();
            this.key.setStackSize(1);
        }
    }
}

package nyonio.terminal_interaction_integration.ae2;

import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.helpers.BaseActionSource;
import nyonio.terminal_interaction_integration.api.IResourceProvider;

import java.util.function.Consumer;

/**
 * One resource view belongs to one ME grid. The resource monitor owns the
 * listener for the lifetime of that grid; there is no global grid cache.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class VirtualResourceView implements IMEMonitorHandlerReceiver {
    private static final IActionSource SYSTEM_SOURCE = new BaseActionSource();
    private final IResourceProvider provider;
    private final IMEMonitor monitor;
    private final Consumer<IActionSource> changed;

    public VirtualResourceView(IResourceProvider provider, IMEMonitor monitor, Consumer<IActionSource> changed) {
        this.provider = provider;
        this.monitor = monitor;
        this.changed = changed;
        monitor.addListener(this, this);
    }

    public IResourceProvider getProvider() { return provider; }

    /** Read the exact resource represented by this provider, not every stack in its channel. */
    public long getAmount() {
        IAEStack key = provider.getPacketType().createResourceStack(1);
        if (key == null) return 0;
        IAEStack stored = (IAEStack) monitor.getStorageList().findPrecise(key);
        return stored == null ? 0 : Math.max(0, stored.getStackSize());
    }

    public void addTo(IItemList<IAEItemStack> items) {
        IAEItemStack display = provider.getPacketType().createAEStack(1);
        if (display == null) return;
        long amount = getAmount();
        IAEItemStack existing = items.findPrecise(display);
        if (existing != null) {
            existing.setStackSize(amount);
        } else if (amount > 0) {
            display.setStackSize(amount);
            items.addStorage(display);
        }
    }

    @Override
    public boolean isValid(Object token) { return token == this; }

    @Override
    public void postChange(IBaseMonitor sourceMonitor, Iterable changes, IActionSource source) {
        changed.accept(source == null ? SYSTEM_SOURCE : source);
    }

    @Override
    public void onListUpdate() { changed.accept(SYSTEM_SOURCE); }
}

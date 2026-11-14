package nyonio.terminal_interaction_integration.compat.crazyae;

import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.GridStorageCache;
import nyonio.terminal_interaction_integration.ae2.ResourceTransfers.Source;

/** At most two exact-resource listeners per entry, owned only by the current grid. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class CrazyAEGridView implements IMEMonitorHandlerReceiver {
    private final CrazyAEIntegration.Mapping mapping;
    private final IMEMonitor[] monitors;
    private final long[] amounts;
    private final boolean[] dirty;
    private long visibleAmount;
    private boolean pending;
    private int ticks;

    public CrazyAEGridView(GridStorageCache grid, CrazyAEIntegration.Mapping mapping) {
        this.mapping = mapping;
        this.monitors = new IMEMonitor[mapping.sources.length];
        this.amounts = new long[monitors.length];
        this.dirty = new boolean[monitors.length];
        for (int i = 0; i < monitors.length; i++) {
            monitors[i] = grid.getInventory((IStorageChannel) mapping.sources[i].channel);
            if (monitors[i] != null) monitors[i].addListener(this, this);
            amounts[i] = read(i);
        }
        visibleAmount = total();
    }

    private long read(int index) {
        if (monitors[index] == null) return 0;
        IAEStack stored = (IAEStack) monitors[index].getStorageList().findPrecise(mapping.sources[index].key);
        return stored == null ? 0 : Math.max(0, stored.getStackSize());
    }

    private long total() {
        long total = 0;
        for (long amount : amounts) total += Math.min(amount, Long.MAX_VALUE - total);
        return total;
    }

    public void addTo(IItemList<IAEItemStack> out) {
        IAEItemStack display = mapping.provider.getPacketType().createAEStack(1);
        if (display == null) return;
        IAEItemStack existing = out.findPrecise(display);
        if (existing != null) existing.setStackSize(visibleAmount);
        else if (visibleAmount > 0) {
            display.setStackSize(visibleAmount);
            out.addStorage(display);
        }
    }

    public IAEItemStack tick(int interval) {
        if (!pending) { ticks = 0; return null; }
        if (++ticks < interval) return null;
        ticks = 0;
        pending = false;
        for (int i = 0; i < dirty.length; i++) {
            if (dirty[i]) { amounts[i] = read(i); dirty[i] = false; }
        }
        long next = total();
        long delta = next - visibleAmount;
        visibleAmount = next;
        if (delta == 0) return null;
        IAEItemStack marker = mapping.provider.getPacketType().createAEStack(1);
        if (marker != null) marker.setStackSize(delta);
        return marker;
    }

    @Override public boolean isValid(Object token) { return token == this; }

    @Override public void postChange(IBaseMonitor monitor, Iterable changes, IActionSource source) {
        for (int i = 0; i < monitors.length; i++) {
            if (monitor != monitors[i]) continue;
            Source target = mapping.sources[i];
            for (Object change : changes) {
                if (target.key.equals(change)) { dirty[i] = true; pending = true; break; }
            }
        }
    }

    @Override public void onListUpdate() {
        for (int i = 0; i < dirty.length; i++) dirty[i] = true;
        pending = true;
    }
}

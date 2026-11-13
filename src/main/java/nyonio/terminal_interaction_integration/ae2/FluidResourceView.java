package nyonio.terminal_interaction_integration.ae2;

import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.helpers.BaseActionSource;
import nyonio.terminal_interaction_integration.TIIConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One listener per grid; enumerate fluids only for a rebuild or full-list invalidation. */
public final class FluidResourceView implements IMEMonitorHandlerReceiver<IAEFluidStack> {
    private static final IActionSource SYSTEM_SOURCE = new BaseActionSource();
    private final IMEMonitor<IAEFluidStack> monitor;
    private final Map<IAEFluidStack, IAEItemStack> displayed = new LinkedHashMap<>();
    private final Set<IAEFluidStack> pending = new LinkedHashSet<>();
    private boolean enabled = TIIConfig.isFluidTerminalIntegrationEnabled();
    private boolean fullUpdate = true;
    private boolean initialized;
    private IActionSource source = SYSTEM_SOURCE;

    public FluidResourceView(IMEMonitor<IAEFluidStack> monitor) {
        this.monitor = monitor;
        monitor.addListener(this, this);
    }

    public void addTo(IItemList<IAEItemStack> items) {
        if (!initialized) {
            rebuild();
            initialized = true;
            fullUpdate = false;
        }
        // Ordinary item changes reuse the snapshot instead of rescanning the fluid monitor.
        for (IAEItemStack display : displayed.values()) {
            IAEItemStack existing = items.findPrecise(display);
            long amount = TIIConfig.isFluidTerminalIntegrationEnabled() ? display.getStackSize() : 0;
            if (existing != null) existing.setStackSize(amount);
            else if (amount > 0) items.addStorage(display.copy());
        }
    }

    private void rebuild() {
        displayed.clear();
        if (!enabled) return;
        Set<IAEFluidStack> addonFluids = FluidTerminalIntegration.addonFluids();
        for (IAEFluidStack fluid : monitor.getStorageList()) update(fluid, fluid, addonFluids);
    }

    private void update(IAEFluidStack key, IAEFluidStack stored, Set<IAEFluidStack> addonFluids) {
        if (stored == null || stored.getStackSize() <= 0 || addonFluids.contains(key)) {
            displayed.remove(key);
            return;
        }
        IAEItemStack display = FluidTerminalIntegration.display(stored, stored.getStackSize());
        if (display != null) displayed.put(identity(key), display);
    }

    public boolean hasChanges() {
        boolean now = TIIConfig.isFluidTerminalIntegrationEnabled();
        if (now != enabled) {
            enabled = now;
            fullUpdate = true;
        }
        return fullUpdate || !pending.isEmpty();
    }

    public IActionSource takeSource() {
        IActionSource result = source;
        source = SYSTEM_SOURCE;
        return result;
    }

    public List<IAEItemStack> takeChanges() {
        Set<IAEFluidStack> changed = new LinkedHashSet<>(pending);
        pending.clear();
        if (fullUpdate) {
            // Include old identities so removals are sent even if an unrelated item rebuild occurred.
            changed.addAll(displayed.keySet());
            rebuild();
            changed.addAll(displayed.keySet());
            initialized = true;
            fullUpdate = false;
        } else if (!changed.isEmpty() && enabled) {
            IItemList<IAEFluidStack> storage = monitor.getStorageList();
            Set<IAEFluidStack> addonFluids = FluidTerminalIntegration.addonFluids();
            for (IAEFluidStack key : changed) update(key, storage.findPrecise(key), addonFluids);
        }
        List<IAEItemStack> result = new ArrayList<>(changed.size());
        for (IAEFluidStack fluid : changed) {
            IAEItemStack marker = FluidTerminalIntegration.display(fluid, 1);
            if (marker != null) result.add(marker);
        }
        return result;
    }

    private static IAEFluidStack identity(IAEFluidStack fluid) {
        IAEFluidStack copy = fluid.copy();
        copy.setStackSize(1);
        return copy;
    }

    @Override public boolean isValid(Object token) { return token == this; }

    @Override
    public void postChange(IBaseMonitor<IAEFluidStack> monitor, Iterable<IAEFluidStack> changes, IActionSource source) {
        if (!TIIConfig.isFluidTerminalIntegrationEnabled()) return;
        for (IAEFluidStack fluid : changes) pending.add(identity(fluid));
        if (source != null) this.source = source;
    }

    @Override
    public void onListUpdate() {
        if (TIIConfig.isFluidTerminalIntegrationEnabled()) fullUpdate = true;
    }
}

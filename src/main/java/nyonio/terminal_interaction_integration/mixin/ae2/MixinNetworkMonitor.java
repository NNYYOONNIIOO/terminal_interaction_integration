package nyonio.terminal_interaction_integration.mixin.ae2;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.GridStorageCache;
import appeng.me.cache.NetworkMonitor;
import nyonio.terminal_interaction_integration.TIIConfig;
import nyonio.terminal_interaction_integration.TerminalInteractionIntegration;
import nyonio.terminal_interaction_integration.ae2.FluidResourceView;
import nyonio.terminal_interaction_integration.ae2.FluidTerminalIntegration;
import nyonio.terminal_interaction_integration.ae2.VirtualResourceView;
import nyonio.terminal_interaction_integration.api.IResourceProvider;
import nyonio.terminal_interaction_integration.api.TerminalInteractionRegistry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mixin(value = NetworkMonitor.class, remap = false)
public abstract class MixinNetworkMonitor<T extends IAEStack<T>> {
    @Shadow @Final private IStorageChannel<T> myChannel;
    @Shadow @Final private GridStorageCache myGridCache;
    @Shadow protected abstract void postChange(boolean add, Iterable<T> changes, IActionSource source);

    @Unique private final Map<String, VirtualResourceView> tii$views = new LinkedHashMap<>();
    @Unique private final Map<String, IActionSource> tii$pending = new LinkedHashMap<>();
    @Unique private int tii$ticks;
    @Unique private FluidResourceView tii$fluids;
    @Unique private boolean tii$queried;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Unique
    private void tii$ensureViews() {
        if (!(myChannel instanceof IItemStorageChannel)) return;
        if (tii$fluids == null && TIIConfig.isFluidTerminalIntegrationEnabled()) {
            tii$fluids = new FluidResourceView(myGridCache.getInventory(FluidTerminalIntegration.channel()));
        }
        for (IResourceProvider provider : TerminalInteractionRegistry.getAllProviders()) {
            if (tii$views.containsKey(provider.getName())) continue;
            IMEMonitor monitor = myGridCache.getInventory((IStorageChannel) provider.getStorageChannel());
            if (monitor != null) {
                String name = provider.getName();
                tii$views.put(name, new VirtualResourceView(provider, monitor,
                        source -> tii$pending.put(name, source)));
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "getAvailableItems", at = @At("RETURN"))
    private void tii$addVirtualItems(IItemList<T> out, CallbackInfoReturnable<IItemList<T>> ci) {
        if (!(myChannel instanceof IItemStorageChannel)) return;
        tii$queried = true;
        tii$ensureViews();
        IItemList<IAEItemStack> items = (IItemList) ci.getReturnValue();
        if (tii$fluids != null) tii$fluids.addTo(items);
        for (VirtualResourceView view : tii$views.values()) {
            try {
                view.addTo(items);
            } catch (RuntimeException e) {
                TerminalInteractionIntegration.LOGGER.error("Unable to update virtual resource {}",
                        view.getProvider().getName(), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "onTick", at = @At("HEAD"))
    private void tii$notifyChanges(CallbackInfo ci) {
        if (!(myChannel instanceof IItemStorageChannel)) return;
        if (tii$queried && tii$fluids == null && TIIConfig.isFluidTerminalIntegrationEnabled()) {
            tii$ensureViews();
        }
        boolean fluidsChanged = tii$fluids != null && tii$fluids.hasChanges();
        if (tii$pending.isEmpty() && !fluidsChanged) {
            tii$ticks = 0;
            return;
        }
        if (++tii$ticks < TIIConfig.getVirtualResourceUpdateIntervalTicks()) return;
        tii$ticks = 0;

        Map<String, IActionSource> pending = new LinkedHashMap<>(tii$pending);
        tii$pending.clear();
        for (Map.Entry<String, IActionSource> entry : pending.entrySet()) {
            VirtualResourceView view = tii$views.get(entry.getKey());
            if (view == null) continue;
            IAEItemStack marker = view.getProvider().getPacketType().createAEStack(1);
            if (marker == null) continue;
            ArrayList<T> changes = new ArrayList<>(1);
            changes.add((T) marker);
            postChange(true, changes, entry.getValue());
        }
        if (fluidsChanged) {
            IActionSource fluidSource = tii$fluids.takeSource();
            List<IAEItemStack> markers = tii$fluids.takeChanges();
            if (!markers.isEmpty()) postChange(true, (Iterable<T>) (Iterable<?>) markers, fluidSource);
        }
    }
}

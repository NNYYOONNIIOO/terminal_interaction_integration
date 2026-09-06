package nyonio.terminal_interaction_integration.mixin.ae2;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.GridStorageCache;
import appeng.me.cache.NetworkMonitor;
import nyonio.terminal_interaction_integration.TIIConfig;
import nyonio.terminal_interaction_integration.TerminalInteractionIntegration;
import nyonio.terminal_interaction_integration.api.TerminalInteractionRegistry;
import nyonio.terminal_interaction_integration.api.IResourceNetworkMonitor;
import nyonio.terminal_interaction_integration.api.IResourceProvider;
import nyonio.terminal_interaction_integration.ae2.ResourceFakeMonitor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = NetworkMonitor.class, remap = false)
public abstract class MixinNetworkMonitor<T extends IAEStack<T>> implements IResourceNetworkMonitor<T> {

    @Shadow
    @Final
    @Nonnull
    private IStorageChannel<?> myChannel;

    @Shadow
    @Final
    @Nonnull
    private GridStorageCache myGridCache;

    @Shadow
    protected abstract void postChange(boolean add, Iterable<T> changes, IActionSource src);

    @Shadow
    @Final
    private IItemList<T> cachedList;

    @Unique
    private final Map<String, ResourceFakeMonitor> resourceMonitors = new ConcurrentHashMap<>();

    @Unique
    private boolean tii$inForceUpdate;

    @Unique
    private final List<Boolean> tii$pendingAdds = new ArrayList<>();

    @Unique
    private final List<List<IAEItemStack>> tii$pendingChanges = new ArrayList<>();

    @Unique
    private final List<IActionSource> tii$pendingSources = new ArrayList<>();

    @Unique
    private int tii$resourceUpdateTickCounter;

    @Override
    public void initResourceMonitor(GridStorageCache gridCache, IStorageChannel<?> resourceChannel) {
        if (this.myChannel instanceof IItemStorageChannel) {
            IResourceProvider provider = TerminalInteractionRegistry.getProviderByChannel(resourceChannel);
            if (provider == null) return;
            
            String providerName = provider.getName();
            if (!resourceMonitors.containsKey(providerName)) {
                ResourceFakeMonitor monitor = new ResourceFakeMonitor(gridCache, resourceChannel);
                resourceMonitors.put(providerName, monitor);
                TerminalInteractionRegistry.registerResourceMonitor(providerName, monitor);
                @SuppressWarnings({"rawtypes", "unchecked"})
                final IItemList<IAEItemStack> cachedItems = (IItemList) this.cachedList;
                monitor.getAvailableItems(cachedItems);
            }
        }
    }

    @Override
    public void resourcePostChange(boolean add, Iterable<T> changes, IActionSource src) {
        resourcePostChange(null, add, changes, src);
    }

    @Override
    public void resourcePostChange(String providerName, boolean add, Iterable<T> changes, IActionSource src) {
        if (!resourceMonitors.isEmpty() && this.myChannel instanceof IItemStorageChannel) {
            final List<T> changeList = new ArrayList<>();
            if (changes != null) {
                for (T change : changes) {
                    changeList.add(change);
                }
            }

            final ResourceFakeMonitor monitor = providerName == null
                ? null : resourceMonitors.get(providerName);
            if (monitor != null) {
                final List<IAEItemStack> itemChanges = new ArrayList<>();
                for (T change : changeList) {
                    if (change instanceof IAEItemStack && change.getStackSize() != 0) {
                        itemChanges.add((IAEItemStack) change);
                    }
                }
                if (itemChanges.isEmpty()) return;

                monitor.applyAvailableItemsChanges(add, itemChanges);
                this.tii$pendingAdds.add(add);
                this.tii$pendingChanges.add(itemChanges);
                this.tii$pendingSources.add(src);

                if (TIIConfig.getVirtualResourceUpdateIntervalTicks() <= 1) {
                    this.tii$flushPendingResourceChanges();
                }
            } else {
                for (ResourceFakeMonitor resourceMonitor : resourceMonitors.values()) {
                    resourceMonitor.invalidateAvailableItems();
                }

                this.tii$postVirtualChange(add, changeList, src);
            }
        }
    }

    @Inject(
        method = "forceUpdate",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IItemList;resetStatus()V",
            shift = At.Shift.AFTER
        )
    )
    private void tii$restoreVirtualResourcesAfterReset(final CallbackInfo ci) {
        if (resourceMonitors.isEmpty() || !(this.myChannel instanceof IItemStorageChannel)) return;

        @SuppressWarnings({"rawtypes", "unchecked"})
        final IItemList<IAEItemStack> cachedItems = (IItemList) this.cachedList;
        for (ResourceFakeMonitor monitor : resourceMonitors.values()) {
            monitor.restoreAvailableItems(cachedItems);
        }
    }

    @Inject(method = "forceUpdate", at = @At("HEAD"))
    private void tii$beginForceUpdate(final CallbackInfo ci) {
        this.tii$flushPendingResourceChanges();
        this.tii$resourceUpdateTickCounter = 0;
        this.tii$inForceUpdate = true;
    }

    @Inject(method = "forceUpdate", at = @At("RETURN"))
    private void tii$endForceUpdate(final CallbackInfo ci) {
        this.tii$inForceUpdate = false;
    }

    @Inject(method = "onTick", at = @At("HEAD"))
    private void tii$updateVirtualResources(final CallbackInfo ci) {
        if (this.tii$pendingChanges.isEmpty()) {
            this.tii$resourceUpdateTickCounter = 0;
            return;
        }

        final int interval = TIIConfig.getVirtualResourceUpdateIntervalTicks();
        if (interval <= 1 || ++this.tii$resourceUpdateTickCounter >= interval) {
            this.tii$resourceUpdateTickCounter = 0;
            this.tii$flushPendingResourceChanges();
        }
    }

    @Unique
    private void tii$flushPendingResourceChanges() {
        if (this.tii$pendingChanges.isEmpty()) return;

        final List<IAEItemStack> displayChanges = new ArrayList<>();
        final IActionSource source = this.tii$pendingSources.get(0);

        for (int i = 0; i < this.tii$pendingChanges.size(); i++) {
            final boolean add = this.tii$pendingAdds.get(i);
            for (IAEItemStack change : this.tii$pendingChanges.get(i)) {
                if (change == null || change.getStackSize() == 0) continue;

                final IAEItemStack effectiveChange = change.copy();
                long delta = effectiveChange.getStackSize();
                if (!add) delta = -delta;
                effectiveChange.setStackSize(delta);
                displayChanges.add(effectiveChange);
            }
        }

        this.tii$pendingAdds.clear();
        this.tii$pendingChanges.clear();
        this.tii$pendingSources.clear();

        if (!displayChanges.isEmpty()) {
            this.tii$postVirtualChange(true, displayChanges, source);
        }
    }

    @Unique
    private void tii$postVirtualChange(boolean add, Iterable<? extends IAEStack<?>> changes, IActionSource src) {
        final List<T> typedChanges = new ArrayList<>();
        for (IAEStack<?> change : changes) {
            @SuppressWarnings("unchecked")
            final T typedChange = (T) change;
            typedChanges.add(typedChange);
        }

        ResourceFakeMonitor.FakeMonitorSource virtualSource =
            ResourceFakeMonitor.FakeMonitorSource.release(src);
        try {
            this.postChange(add, typedChanges, virtualSource);
        } finally {
            virtualSource.recycle();
        }
    }

    @Inject(method = "getAvailableItems", at = @At("TAIL"))
    public void getAvailableItems(final IItemList<T> out, final CallbackInfoReturnable<IItemList<T>> cir) {
        if (!resourceMonitors.isEmpty() && this.myChannel instanceof IItemStorageChannel
            && !this.tii$inForceUpdate) {
            try {
                @SuppressWarnings("unchecked")
                IItemList<IAEItemStack> itemOut = (IItemList<IAEItemStack>) out;
                for (ResourceFakeMonitor monitor : resourceMonitors.values()) {
                    monitor.getAvailableItems(itemOut);
                }
            } catch (Exception e) {
                TerminalInteractionIntegration.getLogger().error(
                    "[TII] Failed to add resources to terminal display", e
                );
            }
        }
    }
    
    @Override
    public void addToAvailableItems(IItemList<IAEItemStack> out) {
        if (out == null) return;
        
        for (ResourceFakeMonitor monitor : resourceMonitors.values()) {
            try {
                monitor.getAvailableItems(out);
            } catch (Exception e) {
                TerminalInteractionIntegration.getLogger().error(
                    "[TII] Failed to add resources to available items", e
                );
            }
        }
    }
}

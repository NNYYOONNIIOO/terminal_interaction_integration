package nyonio.terminal_interaction_integration.mixin.ae2;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.GridStorageCache;
import appeng.me.cache.NetworkMonitor;
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
                @SuppressWarnings("unchecked")
                final Iterable<IAEItemStack> itemChanges = (Iterable<IAEItemStack>) (Iterable<?>) changeList;
                monitor.applyAvailableItemsChanges(add, itemChanges);
            } else {
                for (ResourceFakeMonitor resourceMonitor : resourceMonitors.values()) {
                    resourceMonitor.invalidateAvailableItems();
                }
            }

            // Keep the virtual item update independent from the originating
            // resource update. Reusing the source can make AE2 classify this as
            // a nested update and schedule an expensive full forceUpdate().
            ResourceFakeMonitor.FakeMonitorSource virtualSource =
                ResourceFakeMonitor.FakeMonitorSource.release(src);
            try {
                this.postChange(add, changeList, virtualSource);
            } finally {
                virtualSource.recycle();
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
        this.tii$inForceUpdate = true;
    }

    @Inject(method = "forceUpdate", at = @At("RETURN"))
    private void tii$endForceUpdate(final CallbackInfo ci) {
        this.tii$inForceUpdate = false;
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

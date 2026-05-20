package nyonio.terminal_interaction_integration.coremod.mixin.ae2;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.GridStorageCache;
import appeng.me.cache.NetworkMonitor;
import nyonio.terminal_interaction_integration.api.TerminalInteractionRegistry;
import nyonio.terminal_interaction_integration.api.IPacketType;
import nyonio.terminal_interaction_integration.api.IResourceNetworkMonitor;
import nyonio.terminal_interaction_integration.api.IResourceProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mixin(value = GridStorageCache.class, remap = false)
public abstract class MixinGridStorageCache {

    @Shadow
    @Final
    private Map<IStorageChannel<? extends IAEStack>, NetworkMonitor<?>> storageMonitors;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(final IGrid g, final CallbackInfo ci) {
        try {
            NetworkMonitor<?> itemMonitor = this.storageMonitors.get(
                appeng.api.AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)
            );
            if (itemMonitor instanceof IResourceNetworkMonitor) {
                IResourceNetworkMonitor<?> monitor = (IResourceNetworkMonitor<?>) itemMonitor;
                
                for (IResourceProvider provider : TerminalInteractionRegistry.getAllProviders()) {
                    IStorageChannel<?> channel = provider.getStorageChannel();
                    if (channel != null) {
                        monitor.initResourceMonitor((GridStorageCache) (Object) this, channel);
                    }
                }
                
                TerminalInteractionRegistry.setInitialized(true);
            }
        } catch (Exception e) {
        }
    }

    @Inject(method = "postAlterationOfStoredItems", at = @At("TAIL"))
    public void postAlterationOfStoredItems(final IStorageChannel<?> chan, final Iterable<? extends IAEStack<?>> input, final IActionSource src, final CallbackInfo ci) {
        IResourceProvider provider = TerminalInteractionRegistry.getProviderByChannel(chan);
        if (provider == null) return;
        
        IPacketType packetType = provider.getPacketType();
        if (packetType == null) return;
        
        try {
            List<IAEItemStack> changes = new ArrayList<>();
            
            for (IAEStack<?> stack : input) {
                long amount = stack.getStackSize();
                
                if (amount != 0) {
                    IAEItemStack display = packetType.createAEStack(Math.abs(amount));
                    if (display != null) {
                        display.setStackSize(amount);
                        changes.add(display);
                    }
                }
            }
            
            if (!changes.isEmpty()) {
                NetworkMonitor<?> itemMonitor = this.storageMonitors.get(
                    appeng.api.AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)
                );
                
                if (itemMonitor instanceof IResourceNetworkMonitor) {
                    ((IResourceNetworkMonitor<IAEItemStack>) itemMonitor).resourcePostChange(true, changes, src);
                }
            }
        } catch (Exception e) {
        }
    }

    @Inject(method = "postChangesToNetwork", at = @At("TAIL"))
    private <T extends IAEStack<T>, C extends IStorageChannel<T>> void postChangesToNetwork(
            final C chan, 
            final int upOrDown, 
            final IItemList<T> availableItems, 
            final IActionSource src, 
            final CallbackInfo ci
    ) {
        IResourceProvider provider = TerminalInteractionRegistry.getProviderByChannel(chan);
        if (provider == null) return;
        
        IPacketType packetType = provider.getPacketType();
        if (packetType == null) return;
        
        try {
            List<IAEItemStack> changes = new ArrayList<>();
            
            for (IAEStack<?> stack : availableItems) {
                long amount = stack.getStackSize();
                if (amount != 0) {
                    IAEItemStack display = packetType.createAEStack(Math.abs(amount));
                    if (display != null) {
                        display.setStackSize(amount);
                        changes.add(display);
                    }
                }
            }
            
            if (!changes.isEmpty()) {
                NetworkMonitor<?> itemMonitor = this.storageMonitors.get(
                    appeng.api.AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)
                );
                
                if (itemMonitor instanceof IResourceNetworkMonitor) {
                    ((IResourceNetworkMonitor<IAEItemStack>) itemMonitor).resourcePostChange(upOrDown > 0, changes, src);
                }
            }
        } catch (Exception e) {
        }
    }
}

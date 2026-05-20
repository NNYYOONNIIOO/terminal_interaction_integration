package nyonio.terminal_interaction_integration.coremod.mixin.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.me.cache.GridStorageCache;
import appeng.me.cache.NetworkMonitor;
import appeng.me.cache.SecurityCache;
import appeng.me.storage.NetworkInventoryHandler;
import net.minecraft.item.ItemStack;
import nyonio.terminal_interaction_integration.TerminalInteractionIntegration;
import nyonio.terminal_interaction_integration.api.IPacketType;
import nyonio.terminal_interaction_integration.api.IResourceProvider;
import nyonio.terminal_interaction_integration.api.TerminalInteractionRegistry;
import nyonio.terminal_interaction_integration.ae2.ResourceFakeMonitor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings({"unchecked", "rawtypes"})
@Mixin(value = NetworkInventoryHandler.class, remap = false, priority = 500)
public abstract class MixinNetworkInventoryHandler<T extends IAEStack<T>> {

    private final Map<String, ResourceFakeMonitor> tii$resourceMonitors = new HashMap<>();
    private GridStorageCache tii$storageCache;

    @Shadow
    protected abstract void surface(NetworkInventoryHandler<T> networkInventoryHandler, Actionable type);

    @Shadow
    protected abstract boolean diveList(NetworkInventoryHandler<T> networkInventoryHandler, Actionable type);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(final IStorageChannel<?> chan, final SecurityCache security, final CallbackInfo ci) {
        if (chan instanceof IItemStorageChannel) {
            try {
                tii$storageCache = (GridStorageCache) security.getGrid().getCache(GridStorageCache.class);
                
                for (IResourceProvider provider : TerminalInteractionRegistry.getAllProviders()) {
                    IStorageChannel<?> resourceChannel = provider.getStorageChannel();
                    if (resourceChannel != null) {
                        ResourceFakeMonitor monitor = new ResourceFakeMonitor(tii$storageCache, resourceChannel);
                        tii$resourceMonitors.put(provider.getName(), monitor);
                    }
                }
            } catch (Exception e) {
                TerminalInteractionIntegration.getLogger().error("[TII] Failed to initialize resource monitors", e);
            }
        }
    }

    @Inject(method = "injectItems", at = @At(value = "FIELD", target = "Lappeng/me/storage/NetworkInventoryHandler;priorityInventory:Ljava/util/NavigableMap;", opcode = Opcodes.GETFIELD), cancellable = true)
    private void onInjectItems(final T input, final Actionable mode, final IActionSource src, final CallbackInfoReturnable<T> cir) {
        if (input == null) return;
        if (!(input instanceof IAEItemStack)) return;
        if (src instanceof ResourceFakeMonitor.FakeMonitorSource) return;

        IAEItemStack itemStack = (IAEItemStack) input;
        ItemStack itemRep = itemStack.asItemStackRepresentation();

        for (Map.Entry<String, ResourceFakeMonitor> entry : tii$resourceMonitors.entrySet()) {
            String providerName = entry.getKey();
            ResourceFakeMonitor monitor = entry.getValue();
            
            IResourceProvider provider = TerminalInteractionRegistry.getProvider(providerName);
            if (provider == null) continue;
            
            IPacketType packetType = provider.getPacketType();
            if (packetType != null && packetType.isPacket(itemRep)) {
                IAEItemStack result = monitor.injectItems(itemStack, mode, src);
                this.surface(null, mode);
                cir.setReturnValue((T) result);
                return;
            }
        }
    }

    @Inject(method = "extractItems", at = @At(value = "FIELD", target = "Lappeng/me/storage/NetworkInventoryHandler;priorityInventory:Ljava/util/NavigableMap;", opcode = Opcodes.GETFIELD), cancellable = true)
    private void onExtractItems(final T request, final Actionable mode, final IActionSource src, final CallbackInfoReturnable<T> cir) {
        if (request == null) return;
        if (!(request instanceof IAEItemStack)) return;
        if (src instanceof ResourceFakeMonitor.FakeMonitorSource) return;

        IAEItemStack itemStack = (IAEItemStack) request;
        ItemStack itemRep = itemStack.asItemStackRepresentation();

        for (Map.Entry<String, ResourceFakeMonitor> entry : tii$resourceMonitors.entrySet()) {
            String providerName = entry.getKey();
            ResourceFakeMonitor monitor = entry.getValue();
            
            IResourceProvider provider = TerminalInteractionRegistry.getProvider(providerName);
            if (provider == null) continue;
            
            IPacketType packetType = provider.getPacketType();
            if (packetType != null && packetType.isPacket(itemRep)) {
                IAEItemStack result = monitor.extractItems(itemStack, mode, src);
                this.surface(null, mode);
                cir.setReturnValue((T) result);
                return;
            }
        }
    }
}

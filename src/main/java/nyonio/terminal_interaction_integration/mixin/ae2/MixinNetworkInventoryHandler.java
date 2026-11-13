package nyonio.terminal_interaction_integration.mixin.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.me.cache.SecurityCache;
import appeng.me.storage.NetworkInventoryHandler;
import nyonio.terminal_interaction_integration.ae2.FluidTerminalIntegration;
import nyonio.terminal_interaction_integration.ae2.VirtualInventory;
import nyonio.terminal_interaction_integration.api.IResourceProvider;
import nyonio.terminal_interaction_integration.api.TerminalInteractionRegistry;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = NetworkInventoryHandler.class, remap = false)
public abstract class MixinNetworkInventoryHandler<T extends IAEStack<T>> {
    @Shadow @Final private IStorageChannel<T> myChannel;
    @Shadow @Final private SecurityCache security;
    @Shadow protected abstract void surface(NetworkInventoryHandler<T> handler, Actionable mode);

    @SuppressWarnings("unchecked")
    @Inject(method = "injectItems", at = @At(value = "FIELD",
            target = "Lappeng/me/storage/NetworkInventoryHandler;priorityInventory:Ljava/util/NavigableMap;",
            opcode = Opcodes.GETFIELD, ordinal = 0), cancellable = true)
    private void tii$insert(T input, Actionable mode, IActionSource source, CallbackInfoReturnable<T> ci) {
        if (!(myChannel instanceof IItemStorageChannel) || !(input instanceof IAEItemStack)
                || source instanceof VirtualInventory.VirtualSource) return;
        // Built-in fluid markers are display-only; never convert fabricated item stacks to fluid.
        if (FluidTerminalIntegration.isFluidPacket(input.asItemStackRepresentation())) {
            try {
                ci.setReturnValue(input);
            } finally {
                surface((NetworkInventoryHandler<T>) (Object) this, mode);
            }
            return;
        }
        IResourceProvider provider = TerminalInteractionRegistry.getProviderForPacket(input.asItemStackRepresentation());
        if (provider == null) return;
        try {
            IStorageGrid grid = security.getGrid().getCache(IStorageGrid.class);
            IAEItemStack result = VirtualInventory.insertItem(grid, provider, (IAEItemStack) input, mode, source);
            ci.setReturnValue((T) result);
        } finally {
            surface((NetworkInventoryHandler<T>) (Object) this, mode);
        }
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "extractItems", at = @At(value = "FIELD",
            target = "Lappeng/me/storage/NetworkInventoryHandler;priorityInventory:Ljava/util/NavigableMap;",
            opcode = Opcodes.GETFIELD, ordinal = 0), cancellable = true)
    private void tii$extract(T input, Actionable mode, IActionSource source, CallbackInfoReturnable<T> ci) {
        if (!(myChannel instanceof IItemStorageChannel) || !(input instanceof IAEItemStack)) return;
        if (!TerminalInteractionRegistry.isPacket(input.asItemStackRepresentation())) return;
        try {
            // Virtual packet stacks are display-only; extraction happens through terminal interaction packets.
            ci.setReturnValue(null);
        } finally {
            surface((NetworkInventoryHandler<T>) (Object) this, mode);
        }
    }
}

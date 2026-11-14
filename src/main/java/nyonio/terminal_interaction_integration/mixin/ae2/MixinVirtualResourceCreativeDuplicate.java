package nyonio.terminal_interaction_integration.mixin.ae2;

import appeng.api.storage.data.IAEItemStack;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.helpers.InventoryAction;
import net.minecraft.entity.player.EntityPlayerMP;
import nyonio.terminal_interaction_integration.compat.crazyae.CrazyAEIntegration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AEBaseContainer.class, remap = false)
public abstract class MixinVirtualResourceCreativeDuplicate {
    @Shadow private IAEItemStack clientRequestedTargetItem;

    @Inject(method = "doAction", at = @At("HEAD"), cancellable = true)
    private void tii$blockVirtualCreativeDuplicate(EntityPlayerMP player, InventoryAction action,
                                                    int slot, long id, CallbackInfo ci) {
        if (action == InventoryAction.CREATIVE_DUPLICATE
                && player.openContainer instanceof ContainerMEMonitorable
                && (slot < 0 || slot >= player.openContainer.inventorySlots.size())
                && clientRequestedTargetItem != null
                && CrazyAEIntegration.isVirtualPacket(clientRequestedTargetItem.asItemStackRepresentation())) {
            ci.cancel();
        }
    }
}

package nyonio.terminal_interaction_integration.mixin.ae2;

import appeng.api.storage.data.IAEItemStack;
import appeng.container.me.items.ItemTerminalContainer;
import appeng.helpers.InventoryAction;
import net.minecraft.entity.player.ServerPlayerEntity;
import nyonio.terminal_interaction_integration.api.TerminalInteractionRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemTerminalContainer.class, remap = false)
public abstract class MixinItemTerminalContainer {
    @Inject(method = "handleNetworkInteraction(Lnet/minecraft/entity/player/ServerPlayerEntity;"
            + "Lappeng/api/storage/data/IAEItemStack;Lappeng/helpers/InventoryAction;)V",
            at = @At("HEAD"), cancellable = true)
    private void tii$preventVirtualDuplication(ServerPlayerEntity player, IAEItemStack stack,
                                             InventoryAction action, CallbackInfo ci) {
        // AE2's creative duplication creates an item directly, without asking network storage.
        if (action == InventoryAction.CREATIVE_DUPLICATE && stack != null
                && TerminalInteractionRegistry.isPacket(stack.asItemStackRepresentation())) {
            ci.cancel();
        }
    }
}

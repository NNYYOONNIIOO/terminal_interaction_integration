package nyonio.terminal_interaction_integration.mixin.ae2;

import appeng.parts.automation.UpgradeInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import nyonio.terminal_interaction_integration.api.IUpgradeModuleHost;
import nyonio.terminal_interaction_integration.api.UpgradeModuleRegistration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "appeng.parts.automation.UpgradeInventory$UpgradeInvFilter", remap = false)
public class MixinUpgradeInvFilter {
    @Shadow(remap = false)
    private UpgradeInventory this$0;

    @Inject(method = "allowInsert", at = @At("HEAD"), cancellable = true, remap = false)
    private void terminal_interaction_integration$allow(IItemHandler inventory, int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!UpgradeModuleRegistration.isRegistered(stack)) return;

        IUpgradeModuleHost host = (IUpgradeModuleHost) (Object) this$0;
        if (!host.terminalInteractionIntegration$isInterfaceDevice()) {
            cir.setReturnValue(false);
            return;
        }

        int installed = host.terminalInteractionIntegration$getInstalled(stack.getItem());
        int maximum = UpgradeModuleRegistration.getMaxInstalled(stack.getItem());
        cir.setReturnValue(installed < maximum);
    }
}

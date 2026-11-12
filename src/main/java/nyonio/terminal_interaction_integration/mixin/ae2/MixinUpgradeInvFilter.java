package nyonio.terminal_interaction_integration.mixin.ae2;

import appeng.parts.automation.UpgradeInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import nyonio.terminal_interaction_integration.api.IUpgradeModuleHost;
import nyonio.terminal_interaction_integration.api.UpgradeModuleRegistration;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "appeng.parts.automation.UpgradeInventory$UpgradeInvFilter", remap = false)
public abstract class MixinUpgradeInvFilter {
    @Shadow @Final private UpgradeInventory this$0;

    @Inject(method = "allowInsert", at = @At("HEAD"), cancellable = true)
    private void tii$allow(IItemHandler inventory, int slot, ItemStack stack,
                           CallbackInfoReturnable<Boolean> ci) {
        if (!UpgradeModuleRegistration.isRegistered(stack)) return;
        IUpgradeModuleHost host = (IUpgradeModuleHost) this$0;
        int count = host.terminalInteractionIntegration$getInstalled(stack.getItem());
        ci.setReturnValue(host.terminalInteractionIntegration$isInterfaceDevice()
                && count < UpgradeModuleRegistration.getMaxInstalled(stack.getItem()));
    }
}

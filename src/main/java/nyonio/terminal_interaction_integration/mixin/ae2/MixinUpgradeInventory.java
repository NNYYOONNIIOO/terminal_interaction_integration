package nyonio.terminal_interaction_integration.mixin.ae2;

import appeng.parts.automation.UpgradeInventory;
import appeng.util.inv.IAEAppEngInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import nyonio.terminal_interaction_integration.api.IUpgradeModuleHost;
import nyonio.terminal_interaction_integration.api.UpgradeModuleRegistration;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = UpgradeInventory.class, remap = false)
public abstract class MixinUpgradeInventory implements IUpgradeModuleHost {
    @Shadow @Final private IAEAppEngInventory parent;

    // Registered custom cards do not represent any of AE2's built-in upgrade
    // enum values. Skip them while AE2 calculates its own upgrade counters.
    @ModifyVariable(method = "updateUpgradeInfo", at = @At("STORE"), index = 2)
    private ItemStack tii$skipCustomUpgrade(ItemStack stack) {
        return UpgradeModuleRegistration.isRegistered(stack) ? ItemStack.EMPTY : stack;
    }

    @Override @Unique
    public int terminalInteractionIntegration$getInstalled(Item item) {
        return UpgradeModuleRegistration.countInstalled((UpgradeInventory) (Object) this, item);
    }

    @Override @Unique
    public boolean terminalInteractionIntegration$isInterfaceDevice() {
        if (parent == null) return false;
        String name = parent.getClass().getName();
        return name.contains("Interface") || name.contains("Duality") || name.contains("GasInterface");
    }
}

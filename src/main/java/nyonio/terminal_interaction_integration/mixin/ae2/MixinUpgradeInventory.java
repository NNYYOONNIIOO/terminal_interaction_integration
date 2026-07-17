package nyonio.terminal_interaction_integration.mixin.ae2;

import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.parts.automation.UpgradeInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import nyonio.terminal_interaction_integration.api.IUpgradeModuleHost;
import nyonio.terminal_interaction_integration.api.UpgradeModuleRegistration;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UpgradeInventory.class, remap = false)
public abstract class MixinUpgradeInventory implements IUpgradeModuleHost {
    @Shadow(remap = false)
    @Final
    private appeng.util.inv.IAEAppEngInventory parent;

    @Inject(method = "updateUpgradeInfo", at = @At("HEAD"))
    private void terminal_interaction_integration$clear(CallbackInfo ci) {
    }

    @Redirect(method = "updateUpgradeInfo", at = @At(value = "INVOKE", target = "Lappeng/api/implementations/items/IUpgradeModule;getType(Lnet/minecraft/item/ItemStack;)Lappeng/api/config/Upgrades;"), remap = false)
    private Upgrades terminal_interaction_integration$redirectType(IUpgradeModule module, ItemStack stack) {
        if (UpgradeModuleRegistration.isRegistered(stack)) return Upgrades.STICKY;
        return module.getType(stack);
    }

    @Override
    @Unique
    public int terminalInteractionIntegration$getInstalled(Item item) {
        UpgradeInventory self = (UpgradeInventory) (Object) this;
        return UpgradeModuleRegistration.countInstalled(self, item);
    }

    @Override
    @Unique
    public boolean terminalInteractionIntegration$isInterfaceDevice() {
        if (parent == null) return false;
        String name = parent.getClass().getName();
        return name.contains("Interface") || name.contains("Duality") || name.contains("GasInterface");
    }
}

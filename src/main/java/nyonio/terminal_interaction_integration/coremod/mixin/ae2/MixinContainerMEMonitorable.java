package nyonio.terminal_interaction_integration.coremod.mixin.ae2;

import appeng.api.storage.data.IAEItemStack;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.container.slot.SlotFake;
import appeng.container.slot.SlotInaccessible;
import appeng.util.Platform;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import nyonio.terminal_interaction_integration.TerminalInteractionIntegration;
import nyonio.terminal_interaction_integration.api.IContainerHandler;
import nyonio.terminal_interaction_integration.api.IPacketType;
import nyonio.terminal_interaction_integration.api.TerminalInteractionRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ContainerMEMonitorable.class, remap = false)
public abstract class MixinContainerMEMonitorable {
    
    @Inject(method = "detectAndSendChanges", at = @At("HEAD"))
    private void onDetectAndSendChanges(CallbackInfo ci) {
    }
    
    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true, remap = false)
    private void onSlotClick(Slot slot, int slotId, int mouseButton, int clickType, EntityPlayer player, CallbackInfo ci) {
        if (slot == null) return;
        
        ItemStack heldItem = player.inventory.getItemStack();
        if (heldItem.isEmpty()) return;
        
        IContainerHandler handler = TerminalInteractionRegistry.getContainerHandler(heldItem);
        if (handler == null) return;
        
        ItemStack slotStack = slot.getStack();
        if (slotStack.isEmpty()) return;
        
        IPacketType packetType = TerminalInteractionRegistry.getPacketType(slotStack);
        if (packetType == null) return;
        
        ci.cancel();
    }
}

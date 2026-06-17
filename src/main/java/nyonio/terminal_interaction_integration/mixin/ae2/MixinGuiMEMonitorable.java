package nyonio.terminal_interaction_integration.mixin.ae2;

import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.AEBaseMEGui;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.me.SlotME;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import nyonio.terminal_interaction_integration.TerminalInteractionIntegration;
import nyonio.terminal_interaction_integration.api.IContainerHandler;
import nyonio.terminal_interaction_integration.api.IPacketType;
import nyonio.terminal_interaction_integration.api.TerminalInteractionRegistry;
import nyonio.terminal_interaction_integration.network.CPacketMEMonitorableAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiMEMonitorable.class, remap = false, priority = 100)
public abstract class MixinGuiMEMonitorable extends AEBaseMEGui {

    public MixinGuiMEMonitorable(final Container container) {
        super(container);
    }

    @Inject(method = "handleMouseClick", at = @At("HEAD"), cancellable = true, require = 0)
    private void tii$onHandleMouseClick(final Slot slot, final int slotIdx, final int mouseButton, final ClickType clickType, CallbackInfo ci) {
        if (slot instanceof SlotME) {
            SlotME s = (SlotME) slot;
            ItemStack heldItem = this.mc.player.inventory.getItemStack();
            
            if (mouseButton == 0 && clickType == ClickType.PICKUP) {
                if (!heldItem.isEmpty()) {
                    if (handleCustomContainer(s, heldItem)) {
                        ci.cancel();
                        return;
                    }
                }
            }
            
            if (s.getAEStack() != null) {
                if (handleVirtualPacketOperate(s, mouseButton)) {
                    ci.cancel();
                    return;
                }
            }
        }
    }
    
    @Unique
    private boolean handleCustomContainer(SlotME s, ItemStack h) {
        IContainerHandler handler = TerminalInteractionRegistry.getContainerHandler(h);
        if (handler == null) return false;
        
        IAEItemStack aeStack = s.getAEStack();
        boolean isVirtualPacket = false;
        IPacketType packetType = null;
        
        if (aeStack != null) {
            ItemStack packetStack = aeStack.asItemStackRepresentation();
            packetType = TerminalInteractionRegistry.getPacketType(packetStack);
            isVirtualPacket = packetType != null;
        }
        
        long currentAmount = handler.getStoredAmount(h);
        
        if (isVirtualPacket && packetType != null) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("type", packetType.getName());
            tag.setLong("amount", aeStack.getStackSize());
            tag.setBoolean("hasContent", currentAmount > 0);
            
            TerminalInteractionIntegration.getNetwork().sendToServer(
                new CPacketMEMonitorableAction(CPacketMEMonitorableAction.CUSTOM_INTERACT, tag)
            );
            
            return true;
        }
        
        if (currentAmount > 0) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("type", "deposit");
            tag.setLong("amount", currentAmount);
            
            TerminalInteractionIntegration.getNetwork().sendToServer(
                new CPacketMEMonitorableAction(CPacketMEMonitorableAction.CUSTOM_DEPOSIT, tag)
            );
            
            return true;
        }
        
        return false;
    }
    
    @Unique
    private boolean handleVirtualPacketOperate(SlotME s, int mouseButton) {
        IAEItemStack aeStack = s.getAEStack();
        if (aeStack == null) return false;
        
        ItemStack packetStack = aeStack.asItemStackRepresentation();
        IPacketType packetType = TerminalInteractionRegistry.getPacketType(packetStack);
        
        if (packetType != null) {
            if (mouseButton != 2 && (!aeStack.isCraftable()
                || !(mouseButton == 0 && (aeStack.getStackSize() == 0 || isAltKeyDown())))) {
                
                NBTTagCompound tag = aeStack.getDefinition().writeToNBT(new NBTTagCompound());
                tag.setBoolean("shift", isShiftKeyDown());
                tag.setString("packetType", packetType.getName());
                
                TerminalInteractionIntegration.getNetwork().sendToServer(
                    new CPacketMEMonitorableAction(CPacketMEMonitorableAction.CUSTOM_OPERATE, tag)
                );
                
                return true;
            }
        }
        
        return false;
    }
}

package nyonio.terminal_interaction_integration.client;

import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.me.SlotME;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import nyonio.terminal_interaction_integration.api.IContainerHandler;
import nyonio.terminal_interaction_integration.api.IPacketType;
import nyonio.terminal_interaction_integration.api.TerminalInteractionRegistry;
import nyonio.terminal_interaction_integration.network.CPacketResourceAction;
import nyonio.terminal_interaction_integration.util.UtilClient;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class TerminalInteractionHandler {
    
    private static TerminalInteractionHandler instance;
    
    public static void init() {
        if (instance == null) {
            instance = new TerminalInteractionHandler();
            MinecraftForge.EVENT_BUS.register(instance);
        }
    }
    
    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.getGui() instanceof GuiMEMonitorable)) {
            return;
        }
        
        GuiMEMonitorable gui = (GuiMEMonitorable) event.getGui();
        
        try {
            Slot slot = getSlotUnderMouse(gui);
            if (slot instanceof SlotME) {
                SlotME slotME = (SlotME) slot;
                ItemStack heldItem = UtilClient.getMouseItem();
                
                if (slotME.getAEStack() != null) {
                    ItemStack packetStack = slotME.getAEStack().asItemStackRepresentation();
                    IPacketType packetType = TerminalInteractionRegistry.getPacketType(packetStack);
                    
                    if (packetType != null) {
                        IContainerHandler handler = TerminalInteractionRegistry.getContainerHandler(heldItem);
                        
                        if (handler != null) {
                            long currentAmount = handler.getStoredAmount(heldItem);
                            
                            String resourceName = packetType.getDisplayName();
                            String containerName = heldItem.getDisplayName();
                            String depositText = I18n.format("terminal_interaction_integration.action.deposit");
                            String separator = " : ";
                            
                            String actionText;
                            if (currentAmount > 0) {
                                actionText = I18n.format("terminal_interaction_integration.action.fill");
                            } else {
                                actionText = I18n.format("terminal_interaction_integration.action.extract");
                            }
                            
                            List<String> tooltip = new ArrayList<>();
                            tooltip.add(TextFormatting.DARK_GRAY + GameSettings.getKeyDisplayString(-100) + separator + TextFormatting.RESET + actionText + " " + resourceName);
                            tooltip.add(TextFormatting.DARK_GRAY + GameSettings.getKeyDisplayString(-99) + separator + TextFormatting.RESET + depositText + " " + containerName);
                            
                            gui.drawHoveringText(tooltip, event.getMouseX(), event.getMouseY());
                        }
                    }
                }
            }
        } catch (Exception e) {
            nyonio.terminal_interaction_integration.TerminalInteractionIntegration.getLogger()
                .error("[TII] Error in onDrawScreenPost", e);
        }
    }
    
    @SubscribeEvent
    public void onMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.getGui() instanceof GuiMEMonitorable)) {
            return;
        }
        
        if (!Mouse.getEventButtonState() || Mouse.getEventButton() != 0) {
            return;
        }
        
        GuiMEMonitorable gui = (GuiMEMonitorable) event.getGui();
        
        try {
            Slot slot = getSlotUnderMouse(gui);
            if (slot instanceof SlotME) {
                SlotME slotME = (SlotME) slot;
                ItemStack heldItem = UtilClient.getMouseItem();
                
                if (slotME.getAEStack() != null) {
                    ItemStack packetStack = slotME.getAEStack().asItemStackRepresentation();
                    IPacketType packetType = TerminalInteractionRegistry.getPacketType(packetStack);
                    
                    if (packetType != null) {
                        IContainerHandler handler = TerminalInteractionRegistry.getContainerHandler(heldItem);
                        
                        if (handler != null) {
                            long currentAmount = handler.getStoredAmount(heldItem);
                            long packetAmount = slotME.getAEStack().getStackSize();
                            
                            nyonio.terminal_interaction_integration.TerminalInteractionIntegration.getNetwork().sendToServer(
                                new CPacketResourceAction(packetType.getName(), currentAmount, packetAmount, currentAmount > 0)
                            );
                            
                            event.setCanceled(true);
                        }
                    }
                }
            }
        } catch (Exception e) {
            nyonio.terminal_interaction_integration.TerminalInteractionIntegration.getLogger()
                .error("[TII] Error in onMouseInput", e);
        }
    }
    
    private Slot getSlotUnderMouse(GuiContainer gui) {
        try {
            Method method = GuiContainer.class.getDeclaredMethod("getSlotUnderMouse");
            method.setAccessible(true);
            return (Slot) method.invoke(gui);
        } catch (Exception e) {
            try {
                Method method = GuiScreen.class.getDeclaredMethod("getSlotUnderMouse");
                method.setAccessible(true);
                return (Slot) method.invoke(gui);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}

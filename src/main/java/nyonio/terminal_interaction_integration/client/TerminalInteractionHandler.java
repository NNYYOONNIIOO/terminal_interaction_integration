package nyonio.terminal_interaction_integration.client;

import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.me.SlotME;
import appeng.container.AEBaseContainer;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.InventoryAction;
import com.glodblock.github.integration.mek.FCGasItems;
import com.glodblock.github.loader.FCItems;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import nyonio.terminal_interaction_integration.TerminalInteractionIntegration;
import nyonio.terminal_interaction_integration.api.IContainerHandler;
import nyonio.terminal_interaction_integration.api.IPacketType;
import nyonio.terminal_interaction_integration.api.IResourceProvider;
import nyonio.terminal_interaction_integration.api.TerminalInteractionRegistry;
import nyonio.terminal_interaction_integration.network.CPacketMEMonitorableAction;
import nyonio.terminal_interaction_integration.util.UtilClient;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class TerminalInteractionHandler {
    
    private static TerminalInteractionHandler instance;
    private static boolean lastButtonState = false;
    private static boolean isProcessingInteraction = false;
    private static boolean ae2fcLoaded = false;
    private static boolean mekanismLoaded = false;
    private static boolean checkedMods = false;
    
    public static void init() {
        if (instance == null) {
            instance = new TerminalInteractionHandler();
            MinecraftForge.EVENT_BUS.register(instance);
            checkMods();
        }
    }
    
    private static void checkMods() {
        if (checkedMods) return;
        checkedMods = true;
        ae2fcLoaded = Loader.isModLoaded("ae2fc");
        mekanismLoaded = Loader.isModLoaded("mekanism");
    }
    
    private static boolean isFluidContainer(ItemStack h) {
        if (h.isEmpty()) return false;
        return h.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
    }
    
    private static boolean isGasContainer(ItemStack h) {
        if (h.isEmpty() || !mekanismLoaded) return false;
        return h.getItem() instanceof IGasItem;
    }
    
    private static boolean isAE2FCVirtualPacket(IAEItemStack aeStack) {
        if (aeStack == null || !ae2fcLoaded) return false;
        Item item = aeStack.getItem();
        if (item == FCItems.FLUID_DROP) return true;
        if (mekanismLoaded && item == FCGasItems.GAS_DROP) return true;
        return false;
    }
    
    private static boolean isOurVirtualPacket(IAEItemStack aeStack) {
        if (aeStack == null) return false;
        ItemStack packetStack = aeStack.asItemStackRepresentation();
        return TerminalInteractionRegistry.getPacketType(packetStack) != null;
    }
    
    private static void sendAEDeposit(GuiMEMonitorable gui, IAEItemStack aeStack) {
        AEBaseContainer baseContainer = (AEBaseContainer) gui.inventorySlots;
        if (aeStack != null) {
            baseContainer.setTargetStack(aeStack);
        }
        PacketInventoryAction p = new PacketInventoryAction(
            InventoryAction.SPLIT_OR_PLACE_SINGLE,
            baseContainer.inventorySlots.size(),
            0
        );
        NetworkHandler.instance().sendToServer(p);
    }
    
    private static void sendAEPickup(GuiMEMonitorable gui, IAEItemStack aeStack) {
        AEBaseContainer baseContainer = (AEBaseContainer) gui.inventorySlots;
        if (aeStack != null) {
            baseContainer.setTargetStack(aeStack);
        }
        PacketInventoryAction p = new PacketInventoryAction(
            InventoryAction.PICKUP_OR_SET_DOWN,
            baseContainer.inventorySlots.size(),
            0
        );
        NetworkHandler.instance().sendToServer(p);
    }
    
    private static boolean hasFluidContent(ItemStack h) {
        if (h.isEmpty()) return false;
        try {
            net.minecraftforge.fluids.capability.IFluidHandlerItem fh = FluidUtil.getFluidHandler(h);
            if (fh != null) {
                net.minecraftforge.fluids.FluidStack fluid = fh.drain(Integer.MAX_VALUE, false);
                return fluid != null && fluid.amount > 0;
            }
        } catch (Exception e) {
        }
        return false;
    }
    
    private static boolean hasGasContent(ItemStack h) {
        if (h.isEmpty() || !mekanismLoaded) return false;
        if (h.getItem() instanceof IGasItem) {
            GasStack gas = ((IGasItem) h.getItem()).getGas(h);
            return gas != null && gas.amount > 0;
        }
        return false;
    }
    
    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.getGui() instanceof GuiMEMonitorable)) return;
        
        GuiMEMonitorable gui = (GuiMEMonitorable) event.getGui();
        
        try {
            Slot slot = getSlotUnderMouse(gui);
            if (!(slot instanceof SlotME)) return;
            
            SlotME slotME = (SlotME) slot;
            ItemStack heldItem = UtilClient.getMouseItem();
            IAEItemStack aeStack = slotME.getAEStack();
            
            if (heldItem.isEmpty()) return;
            
            boolean isAE2FCVirtual = isAE2FCVirtualPacket(aeStack);
            boolean isFluidCont = isFluidContainer(heldItem);
            boolean isGasCont = isGasContainer(heldItem);
            boolean isAE2FCContainer = isFluidCont || isGasCont;
            IContainerHandler customHandler = TerminalInteractionRegistry.getContainerHandler(heldItem);
            
            if (isAE2FCVirtual && isAE2FCContainer) {
                boolean hasContent = isFluidCont ? hasFluidContent(heldItem) : hasGasContent(heldItem);
                if (hasContent) return;
                
                String resourceName = aeStack != null ? aeStack.asItemStackRepresentation().getDisplayName() : "";
                
                String separator = " : ";
                List<String> tooltip = new ArrayList<>();
                
                String extractText = I18n.format("terminal_interaction_integration.action.extract");
                tooltip.add(TextFormatting.DARK_GRAY + GameSettings.getKeyDisplayString(-100) + separator + TextFormatting.RESET + extractText + " " + resourceName);
                
                String depositText = I18n.format("terminal_interaction_integration.action.deposit");
                String itemName = heldItem.getDisplayName();
                tooltip.add(TextFormatting.DARK_GRAY + GameSettings.getKeyDisplayString(-99) + separator + TextFormatting.RESET + depositText + " " + itemName);
                
                gui.drawHoveringText(tooltip, event.getMouseX(), event.getMouseY());
                return;
            }
            
            if (customHandler == null) return;
            
            long currentAmount = customHandler.getStoredAmount(heldItem);
            boolean hasContent = currentAmount > 0;
            
            IPacketType hoverPacketType = null;
            if (aeStack != null) {
                ItemStack packetStack = aeStack.asItemStackRepresentation();
                hoverPacketType = TerminalInteractionRegistry.getPacketType(packetStack);
            }
            boolean isVirtualPacket = hoverPacketType != null;
            
            if (!hasContent && !isVirtualPacket) return;
            
            String separator = " : ";
            List<String> tooltip = new ArrayList<>();
            
            IResourceProvider containerProvider = TerminalInteractionRegistry.getProviderForContainer(heldItem);
            IPacketType displayPacketType = isVirtualPacket ? hoverPacketType : 
                (containerProvider != null ? containerProvider.getPacketType() : null);
            
            if (displayPacketType != null) {
                String resourceName = displayPacketType.getDisplayName();
                if (hasContent) {
                    String fillText = I18n.format("terminal_interaction_integration.action.fill");
                    tooltip.add(TextFormatting.DARK_GRAY + GameSettings.getKeyDisplayString(-100) + separator + TextFormatting.RESET + fillText + " " + resourceName);
                } else {
                    String extractText = I18n.format("terminal_interaction_integration.action.extract");
                    tooltip.add(TextFormatting.DARK_GRAY + GameSettings.getKeyDisplayString(-100) + separator + TextFormatting.RESET + extractText + " " + resourceName);
                }
            }
            
            String depositText = I18n.format("terminal_interaction_integration.action.deposit");
            String itemName = heldItem.getDisplayName();
            tooltip.add(TextFormatting.DARK_GRAY + GameSettings.getKeyDisplayString(-99) + separator + TextFormatting.RESET + depositText + " " + itemName);
            
            gui.drawHoveringText(tooltip, event.getMouseX(), event.getMouseY());
        } catch (Exception e) {
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.getGui() instanceof GuiMEMonitorable)) return;
        
        int eventButton = Mouse.getEventButton();
        boolean currentButtonState = Mouse.getEventButtonState();
        
        if (eventButton != 0 && eventButton != 1) return;
        
        if (!currentButtonState) {
            if (isProcessingInteraction) {
                lastButtonState = false;
                isProcessingInteraction = false;
                event.setCanceled(true);
                return;
            }
            lastButtonState = false;
            return;
        }
        
        if (lastButtonState) return;
        lastButtonState = true;
        
        GuiMEMonitorable gui = (GuiMEMonitorable) event.getGui();
        
        try {
            Slot slot = getSlotUnderMouse(gui);
            if (!(slot instanceof SlotME)) return;
            
            SlotME slotME = (SlotME) slot;
            ItemStack heldItem = Minecraft.getMinecraft().player.inventory.getItemStack();
            boolean isRightClick = eventButton == 1;
            
            IAEItemStack aeStack = slotME.getAEStack();
            boolean isAE2FCVirtual = isAE2FCVirtualPacket(aeStack);
            boolean isOurVirtual = isOurVirtualPacket(aeStack);
            
            IContainerHandler customHandler = TerminalInteractionRegistry.getContainerHandler(heldItem);
            boolean isFluidCont = isFluidContainer(heldItem);
            boolean isGasCont = isGasContainer(heldItem);
            boolean isAE2FCContainer = !heldItem.isEmpty() && (isFluidCont || isGasCont);
            
            if (heldItem.isEmpty()) {
                if (isOurVirtual && !isRightClick) {
                    if (aeStack != null && !aeStack.isCraftable()) {
                        IPacketType packetType = TerminalInteractionRegistry.getPacketType(aeStack.asItemStackRepresentation());
                        if (packetType != null) {
                            NBTTagCompound tag = aeStack.getDefinition().writeToNBT(new NBTTagCompound());
                            tag.setBoolean("shift", GuiScreen.isShiftKeyDown());
                            tag.setString("packetType", packetType.getName());
                            
                            TerminalInteractionIntegration.getNetwork().sendToServer(
                                new CPacketMEMonitorableAction(CPacketMEMonitorableAction.CUSTOM_OPERATE, tag)
                            );
                            
                            isProcessingInteraction = true;
                            event.setCanceled(true);
                            return;
                        }
                    }
                }
                return;
            }
            
            if (isAE2FCVirtual) {
                if (isAE2FCContainer && !isRightClick) {
                    return;
                }
                if (isRightClick) {
                    sendAEDeposit(gui, aeStack);
                    isProcessingInteraction = true;
                    event.setCanceled(true);
                    return;
                }
                return;
            }
            
            if (isOurVirtual && customHandler != null) {
                long currentAmount = customHandler.getStoredAmount(heldItem);
                boolean hasContent = currentAmount > 0;
                
                IPacketType packetType = TerminalInteractionRegistry.getPacketType(aeStack.asItemStackRepresentation());
                
                if (isRightClick) {
                    sendAEDeposit(gui, aeStack);
                    isProcessingInteraction = true;
                    event.setCanceled(true);
                    return;
                }
                
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("type", packetType.getName());
                tag.setLong("amount", aeStack.getStackSize());
                tag.setBoolean("hasContent", hasContent);
                
                TerminalInteractionIntegration.getNetwork().sendToServer(
                    new CPacketMEMonitorableAction(CPacketMEMonitorableAction.CUSTOM_INTERACT, tag)
                );
                
                isProcessingInteraction = true;
                event.setCanceled(true);
                return;
            }
            
            if (isAE2FCContainer && !isOurVirtual) {
                if (isRightClick) {
                    sendAEDeposit(gui, aeStack);
                    isProcessingInteraction = true;
                    event.setCanceled(true);
                    return;
                }
                return;
            }
            
            if (customHandler == null) return;
            
            long currentAmount = customHandler.getStoredAmount(heldItem);
            boolean hasContent = currentAmount > 0;
            
            IPacketType hoverPacketType = null;
            if (aeStack != null) {
                ItemStack packetStack = aeStack.asItemStackRepresentation();
                hoverPacketType = TerminalInteractionRegistry.getPacketType(packetStack);
            }
            boolean isVirtualPacket = hoverPacketType != null;
            
            if (isRightClick) {
                sendAEDeposit(gui, aeStack);
                isProcessingInteraction = true;
                event.setCanceled(true);
                return;
            }
            
            if (!hasContent && !isVirtualPacket) {
                sendAEPickup(gui, aeStack);
                isProcessingInteraction = true;
                event.setCanceled(true);
                return;
            }
            
            IResourceProvider containerProvider = TerminalInteractionRegistry.getProviderForContainer(heldItem);
            if (containerProvider == null) return;
            
            if (hasContent) {
                IPacketType interactPacketType = isVirtualPacket ? hoverPacketType : containerProvider.getPacketType();
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("type", interactPacketType.getName());
                tag.setLong("amount", isVirtualPacket ? aeStack.getStackSize() : currentAmount);
                tag.setBoolean("hasContent", true);
                
                TerminalInteractionIntegration.getNetwork().sendToServer(
                    new CPacketMEMonitorableAction(CPacketMEMonitorableAction.CUSTOM_INTERACT, tag)
                );
                
                isProcessingInteraction = true;
                event.setCanceled(true);
                return;
            }
            
            if (isVirtualPacket) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("type", hoverPacketType.getName());
                tag.setLong("amount", aeStack.getStackSize());
                tag.setBoolean("hasContent", false);
                
                TerminalInteractionIntegration.getNetwork().sendToServer(
                    new CPacketMEMonitorableAction(CPacketMEMonitorableAction.CUSTOM_INTERACT, tag)
                );
                
                isProcessingInteraction = true;
                event.setCanceled(true);
                return;
            }
        } catch (Exception e) {
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

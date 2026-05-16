package nyonio.terminal_interaction_integration.client;

import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.me.SlotME;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import nyonio.terminal_interaction_integration.TerminalInteractionIntegration;
import nyonio.terminal_interaction_integration.api.IContainerHandler;
import nyonio.terminal_interaction_integration.api.IPacketType;
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
    private static Item ae2fcFluidDrop = null;
    private static boolean checkedAE2FC = false;
    
    public static void init() {
        if (instance == null) {
            instance = new TerminalInteractionHandler();
            MinecraftForge.EVENT_BUS.register(instance);
            checkAE2FC();
        }
    }
    
    private static void checkAE2FC() {
        if (checkedAE2FC) return;
        checkedAE2FC = true;
        ae2fcLoaded = Loader.isModLoaded("ae2fc");
        TerminalInteractionIntegration.getLogger()
            .info("[TII] Checking AE2FC: loaded={}", ae2fcLoaded);
        if (ae2fcLoaded) {
            try {
                Class<?> fcItemsClass = Class.forName("com.glodblock.github.loader.FCItems");
                java.lang.reflect.Field fluidDropField = fcItemsClass.getField("FLUID_DROP");
                ae2fcFluidDrop = (Item) fluidDropField.get(null);
                TerminalInteractionIntegration.getLogger()
                    .info("[TII] AE2FC FLUID_DROP item: {}", ae2fcFluidDrop);
            } catch (Exception e) {
                TerminalInteractionIntegration.getLogger()
                    .error("[TII] Failed to get AE2FC FLUID_DROP item", e);
            }
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
            TerminalInteractionIntegration.getLogger()
                .error("[TII] Error in onDrawScreenPost", e);
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.getGui() instanceof GuiMEMonitorable)) {
            return;
        }
        
        int eventButton = Mouse.getEventButton();
        boolean currentButtonState = Mouse.getEventButtonState();
        
        TerminalInteractionIntegration.getLogger()
            .info("[TII] onMouseInput: button={}, state={}, lastState={}", eventButton, currentButtonState, lastButtonState);
        
        if (eventButton != 0) {
            return;
        }
        
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
        
        if (lastButtonState) {
            return;
        }
        
        lastButtonState = true;
        
        GuiMEMonitorable gui = (GuiMEMonitorable) event.getGui();
        
        try {
            Slot slot = getSlotUnderMouse(gui);
            
            TerminalInteractionIntegration.getLogger()
                .info("[TII] Slot under mouse: {}", slot != null ? slot.getClass().getSimpleName() : "null");
            
            if (slot instanceof SlotME) {
                SlotME slotME = (SlotME) slot;
                ItemStack heldItem = Minecraft.getMinecraft().player.inventory.getItemStack();
                
                TerminalInteractionIntegration.getLogger()
                    .info("[TII] SlotME detected, heldItem: {}", heldItem != null ? heldItem.getDisplayName() : "empty");
                
                if (handleFluidContainer(slotME, heldItem)) {
                    isProcessingInteraction = true;
                    event.setCanceled(true);
                    return;
                }
                
                if (handleCustomContainer(slotME, heldItem)) {
                    isProcessingInteraction = true;
                    event.setCanceled(true);
                    return;
                }
            }
            
            if (slot instanceof SlotME) {
                SlotME slotME = (SlotME) slot;
                
                if (slotME.getAEStack() != null) {
                    if (handleVirtualPacketOperate(slotME)) {
                        isProcessingInteraction = true;
                        event.setCanceled(true);
                        return;
                    }
                    
                    if (handleFluidOperate(slotME)) {
                        isProcessingInteraction = true;
                        event.setCanceled(true);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            TerminalInteractionIntegration.getLogger()
                .error("[TII] Error in onMouseInput", e);
        }
    }
    
    private boolean handleCustomContainer(SlotME s, ItemStack h) {
        IContainerHandler handler = TerminalInteractionRegistry.getContainerHandler(h);
        if (handler == null) return false;
        
        appeng.api.storage.data.IAEItemStack aeStack = s.getAEStack();
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
            
            TerminalInteractionIntegration.getLogger()
                .info("[TII] Custom container interact with virtual packet: type={}", packetType.getName());
            
            return true;
        }
        
        if (currentAmount > 0) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("type", "deposit");
            tag.setLong("amount", currentAmount);
            
            TerminalInteractionIntegration.getNetwork().sendToServer(
                new CPacketMEMonitorableAction(CPacketMEMonitorableAction.CUSTOM_DEPOSIT, tag)
            );
            
            TerminalInteractionIntegration.getLogger()
                .info("[TII] Custom container deposit to network: amount={}", currentAmount);
            
            return true;
        }
        
        return false;
    }
    
    private boolean handleVirtualPacketOperate(SlotME s) {
        appeng.api.storage.data.IAEItemStack aeStack = s.getAEStack();
        if (aeStack == null) return false;
        
        ItemStack packetStack = aeStack.asItemStackRepresentation();
        IPacketType packetType = TerminalInteractionRegistry.getPacketType(packetStack);
        
        if (packetType != null) {
            NBTTagCompound tag = aeStack.getDefinition().writeToNBT(new NBTTagCompound());
            tag.setBoolean("shift", GuiScreen.isShiftKeyDown());
            tag.setString("packetType", packetType.getName());
            
            TerminalInteractionIntegration.getNetwork().sendToServer(
                new CPacketMEMonitorableAction(CPacketMEMonitorableAction.CUSTOM_OPERATE, tag)
            );
            
            TerminalInteractionIntegration.getLogger()
                .info("[TII] Virtual packet operate: type={}", packetType.getName());
            
            return true;
        }
        
        return false;
    }
    
    private boolean handleFluidContainer(SlotME s, ItemStack h) {
        if (h.isEmpty()) {
            TerminalInteractionIntegration.getLogger().info("[TII] handleFluidContainer: held item is empty");
            return false;
        }
        
        boolean hasFluidCapability = h.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
        IContainerHandler customHandler = TerminalInteractionRegistry.getContainerHandler(h);
        
        TerminalInteractionIntegration.getLogger()
            .info("[TII] handleFluidContainer: item={}, hasFluidCapability={}, hasCustomHandler={}", 
                h.getDisplayName(), hasFluidCapability, customHandler != null);
        
        appeng.api.storage.data.IAEItemStack aeStack = s.getAEStack();
        boolean isFluidDrop = ae2fcLoaded && ae2fcFluidDrop != null && aeStack != null && aeStack.getItem() == ae2fcFluidDrop;
        
        FluidStack fluidFromItem = null;
        boolean hasFluid = false;
        
        if (hasFluidCapability) {
            fluidFromItem = getFluidFromItem(h);
            hasFluid = fluidFromItem != null && fluidFromItem.amount > 0;
        }
        
        TerminalInteractionIntegration.getLogger()
            .info("[TII] handleFluidContainer: isFluidDrop={}, hasFluid={}, fluidFromItem={}", 
                isFluidDrop, hasFluid, fluidFromItem != null ? fluidFromItem.getFluid().getName() : "null");
        
        if (hasFluidCapability && hasFluid) {
            NBTTagCompound tag = new NBTTagCompound();
            
            TerminalInteractionIntegration.getNetwork().sendToServer(
                new CPacketMEMonitorableAction(CPacketMEMonitorableAction.FLUID, tag)
            );
            
            TerminalInteractionIntegration.getLogger()
                .info("[TII] Fluid container interaction (deposit), hasFluid={}, isFluidDrop={}", hasFluid, isFluidDrop);
            
            return true;
        }
        
        if (hasFluidCapability && isFluidDrop && !hasFluid) {
            NBTTagCompound tag = new NBTTagCompound();
            FluidStack fluid = getFluidFromAEStack(aeStack);
            if (fluid != null) {
                fluid.writeToNBT(tag);
            }
            
            TerminalInteractionIntegration.getNetwork().sendToServer(
                new CPacketMEMonitorableAction(CPacketMEMonitorableAction.FLUID, tag)
            );
            
            TerminalInteractionIntegration.getLogger()
                .info("[TII] Fluid container interaction (extract), isFluidDrop={}", isFluidDrop);
            
            return true;
        }
        
        if (customHandler != null) {
            TerminalInteractionIntegration.getLogger().info("[TII] handleFluidContainer: using custom handler");
            return false;
        }
        
        TerminalInteractionIntegration.getLogger().info("[TII] handleFluidContainer: no handler available");
        return false;
    }
    
    private boolean handleFluidOperate(SlotME s) {
        if (!ae2fcLoaded || ae2fcFluidDrop == null) return false;
        
        appeng.api.storage.data.IAEItemStack aeStack = s.getAEStack();
        if (aeStack == null) return false;
        
        if (aeStack.getItem() == ae2fcFluidDrop) {
            NBTTagCompound shift = aeStack.getDefinition().writeToNBT(new NBTTagCompound());
            shift.setBoolean("shift", GuiScreen.isShiftKeyDown());
            
            TerminalInteractionIntegration.getNetwork().sendToServer(
                new CPacketMEMonitorableAction(CPacketMEMonitorableAction.FLUID_OPERATE, shift)
            );
            
            TerminalInteractionIntegration.getLogger()
                .info("[TII] Fluid operate");
            
            return true;
        }
        
        return false;
    }
    
    private FluidStack getFluidFromItem(ItemStack stack) {
        if (ae2fcLoaded) {
            try {
                Class<?> utilClass = Class.forName("com.glodblock.github.util.Util");
                java.lang.reflect.Method method = utilClass.getMethod("getFluidFromItem", ItemStack.class);
                FluidStack fluid = (FluidStack) method.invoke(null, stack);
                if (fluid != null && fluid.amount > 0) {
                    return fluid;
                }
            } catch (Exception e) {
                // ignore
            }
        }
        
        if (stack.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
            net.minecraftforge.fluids.capability.IFluidHandlerItem handler = 
                stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
            if (handler != null) {
                FluidStack fluid = handler.drain(Integer.MAX_VALUE, false);
                if (fluid != null && fluid.amount > 0) {
                    return fluid;
                }
            }
        }
        
        return null;
    }
    
    private FluidStack getFluidFromAEStack(appeng.api.storage.data.IAEItemStack aeStack) {
        try {
            Class<?> fakeItemRegisterClass = Class.forName("com.glodblock.github.common.item.fake.FakeItemRegister");
            java.lang.reflect.Method method = fakeItemRegisterClass.getMethod("getStack", appeng.api.storage.data.IAEItemStack.class);
            return (FluidStack) method.invoke(null, aeStack);
        } catch (Exception e) {
            return null;
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

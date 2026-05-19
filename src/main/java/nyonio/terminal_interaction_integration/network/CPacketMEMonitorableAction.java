package nyonio.terminal_interaction_integration.network;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InventoryAction;
import appeng.me.helpers.PlayerSource;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import nyonio.terminal_interaction_integration.TerminalInteractionIntegration;
import nyonio.terminal_interaction_integration.api.IContainerHandler;
import nyonio.terminal_interaction_integration.api.IPacketType;
import nyonio.terminal_interaction_integration.api.IResourceProvider;
import nyonio.terminal_interaction_integration.api.TerminalInteractionRegistry;

import java.io.IOException;

public class CPacketMEMonitorableAction implements IMessage {

    public static final byte FLUID = 0;
    public static final byte FLUID_OPERATE = 2;
    public static final byte CUSTOM_INTERACT = 10;
    public static final byte CUSTOM_OPERATE = 11;
    public static final byte CUSTOM_DEPOSIT = 12;

    private byte type;
    private NBTTagCompound obj;

    public CPacketMEMonitorableAction() {}

    public CPacketMEMonitorableAction(final byte b, final NBTTagCompound s) {
        type = b;
        obj = s;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        type = buf.readByte();
        obj = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeByte(type);
        ByteBufUtils.writeTag(buf, obj);
    }

    public static class Handler implements IMessageHandler<CPacketMEMonitorableAction, IMessage> {

        @Override
        public IMessage onMessage(final CPacketMEMonitorableAction message, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            
            TerminalInteractionIntegration.getLogger()
                .info("[TII] Received packet on server: type={}, tagSize={}", message.type, message.obj != null ? message.obj.getSize() : -1);
            
            player.getServer().addScheduledTask(() -> {
                switch (message.type) {
                    case FLUID:
                        handleFluid(message, player);
                        break;
                    case FLUID_OPERATE:
                        handleFluidOperate(message, player);
                        break;
                    case CUSTOM_INTERACT:
                        handleCustomInteract(message, player);
                        break;
                    case CUSTOM_OPERATE:
                        handleCustomOperate(message, player);
                        break;
                    case CUSTOM_DEPOSIT:
                        handleCustomDeposit(message, player);
                        break;
                }
            });
            return null;
        }

        private static void handleFluid(final CPacketMEMonitorableAction message, final EntityPlayerMP player) {
            final Container c = player.openContainer;
            if (!(c instanceof ContainerMEMonitorable)) return;
            
            final IStorageGrid grid;
            final IActionSource source;
            try {
                ContainerMEMonitorable cme = (ContainerMEMonitorable) c;
                grid = cme.getNetworkNode().getGrid().getCache(IStorageGrid.class);
                source = new PlayerSource(player, (IActionHost) cme.getTarget());
            } catch (Exception e) {
                return;
            }

            final ItemStack h = player.inventory.getItemStack();
            if (h.isEmpty()) return;

            TerminalInteractionIntegration.getLogger()
                .info("[TII] handleFluid: heldItem={}, tagSize={}", h.getDisplayName(), message.obj.getSize());

            try {
                Class<?> packetClass = Class.forName("com.glodblock.github.network.CpacketMEMonitorableAction");
                java.lang.reflect.Constructor<?> constructor = packetClass.getConstructor(byte.class, NBTTagCompound.class);
                Object ae2fcPacket = constructor.newInstance(message.type, message.obj);
                
                Class<?> handlerClass = Class.forName("com.glodblock.github.network.CpacketMEMonitorableAction$Handler");
                java.lang.reflect.Method fluidWorkMethod = handlerClass.getDeclaredMethod("fluidWork", packetClass, ItemStack.class, IStorageGrid.class, IActionSource.class, EntityPlayerMP.class);
                fluidWorkMethod.setAccessible(true);
                
                ItemStack ch = h.copy();
                ch.setCount(1);
                
                TerminalInteractionIntegration.getLogger()
                    .info("[TII] Before fluidWork: ch={}, ch.hasCapability={}", 
                        ch.getDisplayName(), 
                        ch.hasCapability(net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null));
                
                fluidWorkMethod.invoke(null, ae2fcPacket, ch, grid, source, player);
                
                TerminalInteractionIntegration.getLogger()
                    .info("[TII] Called AE2FC fluidWork successfully");
            } catch (Exception e) {
                TerminalInteractionIntegration.getLogger()
                    .error("[TII] Failed to call AE2FC fluidWork", e);
            }
        }

        private static void handleFluidOperate(final CPacketMEMonitorableAction message, final EntityPlayerMP player) {
            final Container c = player.openContainer;
            if (!(c instanceof ContainerMEMonitorable)) return;
            
            final IStorageGrid grid;
            final IActionSource source;
            try {
                ContainerMEMonitorable cme = (ContainerMEMonitorable) c;
                grid = cme.getNetworkNode().getGrid().getCache(IStorageGrid.class);
                source = new PlayerSource(player, (IActionHost) cme.getTarget());
            } catch (Exception e) {
                return;
            }

            if (!player.inventory.getItemStack().isEmpty()) return;

            try {
                Class<?> packetClass = Class.forName("com.glodblock.github.network.CpacketMEMonitorableAction");
                java.lang.reflect.Constructor<?> constructor = packetClass.getConstructor(byte.class, NBTTagCompound.class);
                Object ae2fcPacket = constructor.newInstance(message.type, message.obj);
                
                Class<?> handlerClass = Class.forName("com.glodblock.github.network.CpacketMEMonitorableAction$Handler");
                java.lang.reflect.Method fluidOperateWorkMethod = handlerClass.getDeclaredMethod("fluidOperateWork", packetClass, IStorageGrid.class, IActionSource.class, EntityPlayerMP.class);
                fluidOperateWorkMethod.setAccessible(true);
                
                fluidOperateWorkMethod.invoke(null, ae2fcPacket, grid, source, player);
                
                TerminalInteractionIntegration.getLogger()
                    .info("[TII] Called AE2FC fluidOperateWork successfully");
            } catch (Exception e) {
                TerminalInteractionIntegration.getLogger()
                    .error("[TII] Failed to call AE2FC fluidOperateWork", e);
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static long injectToNetworkResource(IStorageGrid grid, IResourceProvider provider,
                long amount, IActionSource source) {
            if (amount <= 0) return 0;
            IStorageChannel<?> channel = provider.getStorageChannel();
            if (channel == null) return 0;
            IMEMonitor monitor = grid.getInventory(channel);
            if (monitor == null) return 0;
            try {
                IAEStack stack = (IAEStack) channel.createStack(amount);
                if (stack == null) return 0;
                IAEStack result = (IAEStack) monitor.injectItems(stack, Actionable.MODULATE, source);
                return result == null ? amount : amount - result.getStackSize();
            } catch (Exception e) {
                TerminalInteractionIntegration.getLogger().error("[TII] Failed to inject to network resource channel", e);
                return 0;
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static long extractFromNetworkResource(IStorageGrid grid, IResourceProvider provider,
                long amount, IActionSource source) {
            if (amount <= 0) return 0;
            IStorageChannel<?> channel = provider.getStorageChannel();
            if (channel == null) return 0;
            IMEMonitor monitor = grid.getInventory(channel);
            if (monitor == null) return 0;
            try {
                IAEStack stack = (IAEStack) channel.createStack(amount);
                if (stack == null) return 0;
                IAEStack result = (IAEStack) monitor.extractItems(stack, Actionable.MODULATE, source);
                return result == null ? 0 : result.getStackSize();
            } catch (Exception e) {
                TerminalInteractionIntegration.getLogger().error("[TII] Failed to extract from network resource channel", e);
                return 0;
            }
        }

        private static void handleCustomInteract(final CPacketMEMonitorableAction message, final EntityPlayerMP player) {
            final Container c = player.openContainer;
            if (!(c instanceof ContainerMEMonitorable)) return;
            
            final IStorageGrid grid;
            final IActionSource source;
            try {
                ContainerMEMonitorable cme = (ContainerMEMonitorable) c;
                grid = cme.getNetworkNode().getGrid().getCache(IStorageGrid.class);
                source = new PlayerSource(player, (IActionHost) cme.getTarget());
            } catch (Exception e) {
                return;
            }

            ItemStack h = player.inventory.getItemStack();
            if (h.isEmpty()) return;

            String typeName = message.obj.getString("type");
            long amount = message.obj.getLong("amount");
            boolean hasContent = message.obj.getBoolean("hasContent");

            IPacketType packetType = TerminalInteractionRegistry.getPacketTypeByName(typeName);
            if (packetType == null) return;

            IResourceProvider provider = TerminalInteractionRegistry.getProvider(typeName);
            if (provider == null) return;

            IContainerHandler handler = provider.getContainerHandler();
            if (handler == null) return;

            if (!handler.canHandle(h)) return;

            long currentAmount = handler.getStoredAmount(h);

            if (hasContent) {
                long extracted = handler.extract(h, currentAmount, source);
                TerminalInteractionIntegration.getLogger()
                    .info("[TII] Extracted {} from container to virtual packet", extracted);
                if (extracted > 0) {
                    long injected = injectToNetworkResource(grid, provider, extracted, source);
                    TerminalInteractionIntegration.getLogger()
                        .info("[TII] Injected {} to network resource channel", injected);
                }
            } else {
                long networkAmount = extractFromNetworkResource(grid, provider, amount, source);
                TerminalInteractionIntegration.getLogger()
                    .info("[TII] Extracted {} from network resource channel", networkAmount);
                if (networkAmount > 0) {
                    long injected = handler.inject(h, networkAmount, source);
                    TerminalInteractionIntegration.getLogger()
                        .info("[TII] Injected {} to container from network", injected);
                }
            }

            if (h.getCount() > 1) {
                h.shrink(1);
                ItemStack result = handler.getEmptyContainer();
                if (result != null && !result.isEmpty()) {
                    result.setCount(1);
                    player.inventory.placeItemBackInInventory(player.world, result);
                }
            } else {
                updateHeld(player);
            }
        }

        private static void handleCustomOperate(final CPacketMEMonitorableAction message, final EntityPlayerMP player) {
            final Container c = player.openContainer;
            if (!(c instanceof ContainerMEMonitorable)) return;
            
            final IStorageGrid grid;
            final IActionSource source;
            try {
                ContainerMEMonitorable cme = (ContainerMEMonitorable) c;
                grid = cme.getNetworkNode().getGrid().getCache(IStorageGrid.class);
                source = new PlayerSource(player, (IActionHost) cme.getTarget());
            } catch (Exception e) {
                return;
            }

            if (!player.inventory.getItemStack().isEmpty()) return;

            String packetTypeName = message.obj.getString("packetType");
            boolean shift = message.obj.getBoolean("shift");

            IPacketType packetType = TerminalInteractionRegistry.getPacketTypeByName(packetTypeName);
            if (packetType == null) return;

            IResourceProvider provider = TerminalInteractionRegistry.getProvider(packetTypeName);
            if (provider == null) return;

            IContainerHandler handler = provider.getContainerHandler();
            if (handler == null) return;

            ItemStack emptyContainer = handler.getEmptyContainer();
            if (emptyContainer == null || emptyContainer.isEmpty()) return;

            long amount = 1000;
            long networkAmount = extractFromNetworkResource(grid, provider, amount, source);
            if (networkAmount <= 0) return;

            long injected = handler.inject(emptyContainer, networkAmount, source);

            if (injected > 0) {
                if (shift) {
                    int slot = player.inventory.getFirstEmptyStack();
                    if (slot != -1) {
                        player.inventory.setInventorySlotContents(slot, emptyContainer);
                    }
                } else {
                    player.inventory.setItemStack(emptyContainer);
                    updateHeld(player);
                }
            }

            TerminalInteractionIntegration.getLogger()
                .info("[TII] Custom operate: extracted container with {} {}", injected, packetType.getDisplayName());
        }

        private static void handleCustomDeposit(final CPacketMEMonitorableAction message, final EntityPlayerMP player) {
            final Container c = player.openContainer;
            if (!(c instanceof ContainerMEMonitorable)) return;
            
            final IStorageGrid grid;
            final IActionSource source;
            try {
                ContainerMEMonitorable cme = (ContainerMEMonitorable) c;
                grid = cme.getNetworkNode().getGrid().getCache(IStorageGrid.class);
                source = new PlayerSource(player, (IActionHost) cme.getTarget());
            } catch (Exception e) {
                return;
            }

            ItemStack h = player.inventory.getItemStack();
            if (h.isEmpty()) return;

            IContainerHandler handler = TerminalInteractionRegistry.getContainerHandler(h);
            if (handler == null) return;

            IPacketType packetType = TerminalInteractionRegistry.getPacketType(h);
            if (packetType == null) return;

            IResourceProvider provider = TerminalInteractionRegistry.getProvider(packetType.getName());
            if (provider == null) return;

            long currentAmount = handler.getStoredAmount(h);
            if (currentAmount <= 0) return;

            long extracted = handler.extract(h, currentAmount, source);

            TerminalInteractionIntegration.getLogger()
                .info("[TII] Custom deposit: extracted {} from container", extracted);

            if (extracted > 0) {
                long injected = injectToNetworkResource(grid, provider, extracted, source);
                TerminalInteractionIntegration.getLogger()
                    .info("[TII] Custom deposit: injected {} to network resource channel", injected);
            }

            if (h.getCount() > 1) {
                h.shrink(1);
                ItemStack result = handler.getEmptyContainer();
                if (result != null && !result.isEmpty()) {
                    result.setCount(1);
                    player.inventory.placeItemBackInInventory(player.world, result);
                }
            } else {
                updateHeld(player);
            }
        }

        private static void updateHeld(final EntityPlayerMP p) {
            if (Platform.isServer()) {
                try {
                    NetworkHandler.instance().sendTo(
                        new PacketInventoryAction(
                            InventoryAction.UPDATE_HAND, 
                            0, 
                            AEItemStack.fromItemStack(p.inventory.getItemStack())
                        ), 
                        p
                    );
                } catch (final IOException e) {
                    AELog.debug(e);
                }
            }
        }
    }
}

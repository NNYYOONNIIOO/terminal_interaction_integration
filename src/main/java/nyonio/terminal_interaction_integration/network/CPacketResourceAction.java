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
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import nyonio.terminal_interaction_integration.api.TerminalInteractionRegistry;
import nyonio.terminal_interaction_integration.api.IContainerHandler;
import nyonio.terminal_interaction_integration.api.IResourceProvider;
import nyonio.terminal_interaction_integration.TerminalInteractionIntegration;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class CPacketResourceAction implements IMessage {
    private String packetTypeName;
    private long currentAmount;
    private long packetAmount;
    private boolean isFilling;
    private boolean extractFromNetwork;
    private long packetId;
    
    private static long nextPacketId = 0;
    
    public CPacketResourceAction() {}
    
    public CPacketResourceAction(String packetTypeName, long currentAmount, long packetAmount, boolean isFilling) {
        this.packetTypeName = packetTypeName;
        this.currentAmount = currentAmount;
        this.packetAmount = packetAmount;
        this.isFilling = isFilling;
        this.extractFromNetwork = false;
        this.packetId = nextPacketId++;
    }
    
    public void setExtractFromNetwork(boolean extractFromNetwork) {
        this.extractFromNetwork = extractFromNetwork;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int nameLength = buf.readInt();
        byte[] nameBytes = new byte[nameLength];
        buf.readBytes(nameBytes);
        this.packetTypeName = new String(nameBytes, java.nio.charset.StandardCharsets.UTF_8);
        this.currentAmount = buf.readLong();
        this.packetAmount = buf.readLong();
        this.isFilling = buf.readBoolean();
        this.extractFromNetwork = buf.readBoolean();
        this.packetId = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        byte[] nameBytes = this.packetTypeName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeInt(nameBytes.length);
        buf.writeBytes(nameBytes);
        buf.writeLong(this.currentAmount);
        buf.writeLong(this.packetAmount);
        buf.writeBoolean(this.isFilling);
        buf.writeBoolean(this.extractFromNetwork);
        buf.writeLong(this.packetId);
    }

    public static class Handler implements IMessageHandler<CPacketResourceAction, IMessage> {
        @Override
        public IMessage onMessage(CPacketResourceAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            
            TerminalInteractionIntegration.getLogger()
                .info("[TII] Received packet ID: {}, type={}, amount={}, filling={}, extractFromNetwork={}", 
                    message.packetId, message.packetTypeName, message.packetAmount, message.isFilling, message.extractFromNetwork);
            
            player.getServerWorld().addScheduledTask(() -> {
                if (message.extractFromNetwork) {
                    handleExtractFromNetwork(message, player);
                } else {
                    handleContainerInteraction(message, player);
                }
            });
            return null;
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
        
        private static void handleExtractFromNetwork(CPacketResourceAction message, EntityPlayerMP player) {
            IResourceProvider provider = TerminalInteractionRegistry.getProvider(message.packetTypeName);
            if (provider == null) {
                TerminalInteractionIntegration.getLogger()
                    .warn("[TII] No provider found for: " + message.packetTypeName);
                return;
            }
            
            IContainerHandler handler = provider.getContainerHandler();
            if (handler == null) {
                TerminalInteractionIntegration.getLogger()
                    .warn("[TII] No container handler for: " + message.packetTypeName);
                return;
            }
            
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
            
            long networkAmount = extractFromNetworkResource(grid, provider, message.packetAmount, source);
            if (networkAmount <= 0) {
                TerminalInteractionIntegration.getLogger()
                    .warn("[TII] No resources available in network for: " + message.packetTypeName);
                return;
            }
            
            ItemStack container = handler.getEmptyContainer();
            if (container == null || container.isEmpty()) {
                TerminalInteractionIntegration.getLogger()
                    .warn("[TII] No empty container available for: " + message.packetTypeName);
                return;
            }
            
            long injected = handler.inject(container, networkAmount, source);
            TerminalInteractionIntegration.getLogger()
                .info("[TII] Injected {} to container from network", injected);
            
            player.inventory.setItemStack(container);
            updateHeld(player);
            
            TerminalInteractionIntegration.getLogger()
                .info("[TII] Extracted container from network: type={}, amount={}", 
                    message.packetTypeName, injected);
        }
        
        private static void handleContainerInteraction(CPacketResourceAction message, EntityPlayerMP player) {
            IResourceProvider provider = TerminalInteractionRegistry.getProvider(message.packetTypeName);
            if (provider == null) {
                TerminalInteractionIntegration.getLogger()
                    .warn("[TII] No provider found for: " + message.packetTypeName);
                return;
            }
            
            IContainerHandler handler = provider.getContainerHandler();
            if (handler == null) {
                TerminalInteractionIntegration.getLogger()
                    .warn("[TII] No container handler for: " + message.packetTypeName);
                return;
            }
            
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
            
            ItemStack heldItem = player.inventory.getItemStack();
            TerminalInteractionIntegration.getLogger()
                .info("[TII] Processing resource action: type={}, amount={}, filling={}, heldItem={}",
                    message.packetTypeName, message.packetAmount, message.isFilling, 
                    heldItem != null ? heldItem.getDisplayName() : "null");
            
            if (heldItem == null || heldItem.isEmpty()) {
                TerminalInteractionIntegration.getLogger()
                    .warn("[TII] No item in cursor");
                return;
            }
            
            if (!handler.canHandle(heldItem)) {
                TerminalInteractionIntegration.getLogger()
                    .warn("[TII] Handler cannot handle item: " + heldItem.getDisplayName());
                return;
            }
            
            if (message.isFilling) {
                long extracted = handler.extract(heldItem, message.currentAmount, source);
                TerminalInteractionIntegration.getLogger()
                    .info("[TII] Extracted {} from container", extracted);
                if (extracted > 0) {
                    long injected = injectToNetworkResource(grid, provider, extracted, source);
                    TerminalInteractionIntegration.getLogger()
                        .info("[TII] Injected {} to network resource channel", injected);
                }
            } else {
                long networkAmount = extractFromNetworkResource(grid, provider, message.packetAmount, source);
                TerminalInteractionIntegration.getLogger()
                    .info("[TII] Extracted {} from network resource channel", networkAmount);
                if (networkAmount > 0) {
                    long injected = handler.inject(heldItem, networkAmount, source);
                    TerminalInteractionIntegration.getLogger()
                        .info("[TII] Injected {} to container from network", injected);
                }
            }
            
            long newAmount = handler.getStoredAmount(heldItem);
            TerminalInteractionIntegration.getLogger()
                .info("[TII] New amount: {}", newAmount);
            
            updateHeld(player);
            
            byte[] tagBytes = null;
            NBTTagCompound tag = heldItem.getTagCompound();
            if (tag != null) {
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    DataOutputStream dos = new DataOutputStream(baos);
                    net.minecraft.nbt.CompressedStreamTools.write(tag, dos);
                    tagBytes = baos.toByteArray();
                } catch (Exception e) {
                    TerminalInteractionIntegration.getLogger()
                        .error("[TII] Failed to serialize NBT tag", e);
                }
            }
            
            TerminalInteractionIntegration.getNetwork().sendTo(
                new SPacketResourceUpdate(message.packetTypeName, newAmount, tagBytes), 
                player
            );
        }
        
        private static void updateHeld(EntityPlayerMP player) {
            if (Platform.isServer()) {
                try {
                    NetworkHandler.instance().sendTo(
                        new PacketInventoryAction(
                            InventoryAction.UPDATE_HAND, 
                            0, 
                            AEItemStack.fromItemStack(player.inventory.getItemStack())
                        ), 
                        player
                    );
                    TerminalInteractionIntegration.getLogger()
                        .info("[TII] Sent UPDATE_HAND packet to client");
                } catch (IOException e) {
                    AELog.debug(e);
                    TerminalInteractionIntegration.getLogger()
                        .error("[TII] Failed to send UPDATE_HAND packet", e);
                }
            }
        }
    }
}

package nyonio.terminal_interaction_integration.network;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.container.me.common.MEMonitorableContainer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import nyonio.terminal_interaction_integration.TerminalInteractionIntegration;
import nyonio.terminal_interaction_integration.ae2.VirtualInventory;
import nyonio.terminal_interaction_integration.api.IContainerHandler;
import nyonio.terminal_interaction_integration.api.IResourceProvider;
import nyonio.terminal_interaction_integration.api.TerminalInteractionRegistry;
import nyonio.terminal_interaction_integration.mixin.ae2.BaseContainerAccess;
import nyonio.terminal_interaction_integration.mixin.ae2.TerminalContainerAccess;

import java.util.function.Supplier;

public final class TerminalInteractionPacket {
    private final int windowId;
    private final long serial;
    private final boolean shift;

    public TerminalInteractionPacket(int windowId, long serial, boolean shift) {
        this.windowId = windowId;
        this.serial = serial;
        this.shift = shift;
    }

    public static void encode(TerminalInteractionPacket packet, PacketBuffer buffer) {
        buffer.writeVarInt(packet.windowId);
        buffer.writeVarLong(packet.serial);
        buffer.writeBoolean(packet.shift);
    }

    public static TerminalInteractionPacket decode(PacketBuffer buffer) {
        return new TerminalInteractionPacket(buffer.readVarInt(), buffer.readVarLong(), buffer.readBoolean());
    }

    public static void handle(TerminalInteractionPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> packet.execute(ctx.getSender()));
        ctx.setPacketHandled(true);
    }

    private void execute(ServerPlayerEntity player) {
        if (player == null || !(player.openContainer instanceof MEMonitorableContainer)) return;
        MEMonitorableContainer<?> container = (MEMonitorableContainer<?>) player.openContainer;
        if (container.windowId != windowId || !container.isPowered()
                || !(container.getStorageChannel() instanceof IItemStorageChannel)) return;
        ItemStack cursor = player.inventory.getItemStack();
        IAEStack<?> selected = serial == -1 ? null
                : ((TerminalContainerAccess) container).tii$getStackBySerial(serial);
        if (serial != -1 && !(selected instanceof IAEItemStack)) return;
        IResourceProvider provider = selected == null ? TerminalInteractionRegistry.getProviderForContainer(cursor)
                : TerminalInteractionRegistry.getProviderForPacket(selected.asItemStackRepresentation());
        if (provider == null || provider.getContainerHandler() == null) return;
        if (selected == null && (cursor.isEmpty()
                || provider.getContainerHandler().getStoredAmount(cursor) <= 0)) return;

        IGridNode node = container.getNetworkNode();
        if (node == null && container.getTarget() instanceof IActionHost) {
            node = ((IActionHost) container.getTarget()).getActionableNode();
        }
        IGrid network = node == null ? null : node.getGrid();
        if (network == null) return;
        IStorageGrid grid = network.getCache(IStorageGrid.class);
        IActionSource source = container.getActionSource();
        if (grid == null || source == null) return;

        IContainerHandler handler = provider.getContainerHandler();
        if (cursor.isEmpty()) {
            ItemStack empty = handler.getEmptyContainer();
            if (empty == null || empty.isEmpty()) return;
            ItemStack one = empty.copy();
            one.setCount(1);
            if (!handler.canHandle(one) || fill(grid, provider, handler, one, source, 1000) <= 0) return;
            if (shift) {
                if (!player.inventory.addItemStackToInventory(one)) player.dropItem(one, false);
            } else {
                player.inventory.setItemStack(one);
            }
        } else {
            ItemStack one = cursor.copy();
            one.setCount(1);
            if (!handler.canHandle(one)) return;
            long moved = handler.getStoredAmount(one) > 0
                    ? drain(grid, provider, handler, one, source)
                    : fill(grid, provider, handler, one, source, Long.MAX_VALUE);
            if (moved <= 0) return;
            if (cursor.getCount() == 1) {
                player.inventory.setItemStack(one);
            } else {
                ItemStack remaining = cursor.copy();
                remaining.shrink(1);
                player.inventory.setItemStack(remaining);
                if (!one.isEmpty() && !player.inventory.addItemStackToInventory(one)) {
                    player.dropItem(one, false);
                }
            }
        }
        ((BaseContainerAccess) container).tii$updateHeld(player);
        container.detectAndSendChanges();
    }

    static long fill(IStorageGrid grid, IResourceProvider provider, IContainerHandler handler,
                             ItemStack item, IActionSource source, long limit) {
        long capacity = Math.max(0, handler.getMaxCapacity(item) - handler.getStoredAmount(item));
        long available = VirtualInventory.extract(grid, provider, Math.min(capacity, limit),
                Actionable.SIMULATE, source);
        if (available <= 0) return 0;
        ItemStack probe = item.copy();
        long canFill = Math.min(available, Math.max(0, handler.inject(probe, available, source)));
        if (canFill <= 0) return 0;
        long extracted = VirtualInventory.extract(grid, provider, canFill, Actionable.MODULATE, source);
        if (extracted <= 0) return 0;
        long filled = Math.min(extracted, Math.max(0, handler.inject(item, extracted, source)));
        if (filled < extracted) restore(grid, provider, extracted - filled, source);
        return filled;
    }

    static long drain(IStorageGrid grid, IResourceProvider provider, IContainerHandler handler,
                              ItemStack item, IActionSource source) {
        long available = Math.max(0, handler.getStoredAmount(item));
        long accepted = VirtualInventory.insert(grid, provider, available, Actionable.SIMULATE, source);
        if (accepted <= 0) return 0;
        long removed = Math.min(accepted, Math.max(0, handler.extract(item, accepted, source)));
        if (removed <= 0) return 0;
        long inserted = VirtualInventory.insert(grid, provider, removed, Actionable.MODULATE, source);
        if (inserted < removed) {
            long restored = handler.inject(item, removed - inserted, source);
            if (restored < removed - inserted) {
                TerminalInteractionIntegration.LOGGER.error("Container handler failed to restore {} units",
                        removed - inserted - restored);
            }
        }
        return inserted;
    }

    private static void restore(IStorageGrid grid, IResourceProvider provider, long amount, IActionSource source) {
        long restored = VirtualInventory.insert(grid, provider, amount, Actionable.MODULATE, source);
        if (restored < amount) {
            TerminalInteractionIntegration.LOGGER.error("Could not return {} units to resource {}",
                    amount - restored, provider.getName());
        }
    }
}

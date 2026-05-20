package nyonio.terminal_interaction_integration.network;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.fluids.util.AEFluidStack;
import appeng.helpers.InventoryAction;
import appeng.me.helpers.PlayerSource;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
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

        private static IAEItemStack bucketStack;

        @Override
        public IMessage onMessage(final CPacketMEMonitorableAction message, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            
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

        private static IStorageGrid getGrid(EntityPlayerMP player) {
            Container c = player.openContainer;
            if (!(c instanceof ContainerMEMonitorable)) return null;
            try {
                ContainerMEMonitorable cme = (ContainerMEMonitorable) c;
                return cme.getNetworkNode().getGrid().getCache(IStorageGrid.class);
            } catch (Exception e) {
                return null;
            }
        }

        private static IActionSource getSource(EntityPlayerMP player) {
            Container c = player.openContainer;
            ContainerMEMonitorable cme = (ContainerMEMonitorable) c;
            return new PlayerSource(player, (IActionHost) cme.getTarget());
        }

        private static void handleFluid(final CPacketMEMonitorableAction message, final EntityPlayerMP player) {
            IStorageGrid grid = getGrid(player);
            if (grid == null) return;
            IActionSource source = getSource(player);

            final ItemStack h = player.inventory.getItemStack();
            if (h.isEmpty()) return;

            final ItemStack ch = h.copy();
            ch.setCount(1);

            if (!ItemStack.areItemsEqual(ch, h) || !ItemStack.areItemStackTagsEqual(ch, h)) return;

            IFluidHandlerItem fh = FluidUtil.getFluidHandler(ch);
            if (fh == null) return;

            FluidStack allFluid = fh.drain(Integer.MAX_VALUE, false);
            boolean drain = false;
            FluidStack fluid = null;

            if (!message.obj.hasNoTags()) {
                fluid = FluidStack.loadFluidStackFromNBT(message.obj);
                if (fluid != null) {
                    if (allFluid != null && allFluid.amount > 0) {
                        if (!allFluid.isFluidEqual(fluid)) drain = true;
                    }
                } else {
                    drain = true;
                }
            } else {
                drain = true;
            }

            IStorageChannel<IAEFluidStack> fluidChannel = appeng.api.AEApi.instance().storage()
                .getStorageChannel(IFluidStorageChannel.class);
            IMEMonitor<IAEFluidStack> fluidStorage = grid.getInventory(fluidChannel);

            if (drain) {
                AEFluidStack allAEFluid = AEFluidStack.fromFluidStack(allFluid);
                if (allAEFluid == null) return;
                IAEFluidStack simResult = fluidStorage.injectItems(allAEFluid, Actionable.SIMULATE, source);
                long size = allAEFluid.getStackSize() - (simResult == null ? 0 : simResult.getStackSize());
                fluidStorage.injectItems(allAEFluid.setStackSize(size), Actionable.MODULATE, source);
                fh.drain((int) size, true);
            } else {
                AEFluidStack allAEFluid = AEFluidStack.fromFluidStack(fluid);
                IAEFluidStack simResult = fluidStorage.extractItems(allAEFluid, Actionable.SIMULATE, source);
                if (simResult == null) return;
                int size = fh.fill(simResult.getFluidStack(), false);
                fluidStorage.extractItems(allAEFluid.setStackSize(size), Actionable.MODULATE, source);
                fh.fill(simResult.getFluidStack(), true);
            }

            if (h.getCount() > 1) {
                h.shrink(1);
                ItemStack cc = fh.getContainer();
                cc.setCount(1);
                player.inventory.placeItemBackInInventory(player.world, cc);
            } else {
                player.inventory.setItemStack(fh.getContainer());
            }
            updateHeld(player);
        }

        private static void handleFluidOperate(final CPacketMEMonitorableAction message, final EntityPlayerMP player) {
            IStorageGrid grid = getGrid(player);
            if (grid == null) return;
            IActionSource source = getSource(player);

            if (!player.inventory.getItemStack().isEmpty()) return;

            FluidStack fluid;
            if (!message.obj.hasNoTags()) {
                ItemStack fakeItem = new ItemStack(message.obj);
                fluid = com.glodblock.github.common.item.fake.FakeItemRegister.getStack(fakeItem);
                if (fluid == null) return;
                fluid.amount = 1000;
            } else return;

            boolean shift = message.obj.getBoolean("shift");

            IStorageChannel<IAEItemStack> itemChannel = appeng.api.AEApi.instance().storage()
                .getStorageChannel(IItemStorageChannel.class);
            IStorageChannel<IAEFluidStack> fluidChannel = appeng.api.AEApi.instance().storage()
                .getStorageChannel(IFluidStorageChannel.class);

            IMEMonitor<IAEItemStack> itemStorage = grid.getInventory(itemChannel);
            IMEMonitor<IAEFluidStack> fluidStorage = grid.getInventory(fluidChannel);

            if (bucketStack == null) {
                bucketStack = AEItemStack.fromItemStack(new ItemStack(Items.BUCKET));
            }

            IAEItemStack b = itemStorage.extractItems(bucketStack, Actionable.SIMULATE, source);
            if (b == null) return;

            IAEFluidStack aeFluid = fluidStorage.extractItems(AEFluidStack.fromFluidStack(fluid), Actionable.SIMULATE, source);
            if (aeFluid == null || aeFluid.getStackSize() < 1000) return;

            IFluidHandlerItem fh = FluidUtil.getFluidHandler(b.createItemStack());
            if (fh == null) return;

            int s = fh.fill(aeFluid.getFluidStack(), true);
            if (s != 1000) return;

            ItemStack out = fh.getContainer();
            if (shift) {
                int slot = player.inventory.getFirstEmptyStack();
                if (slot == -1) return;
                player.inventory.setInventorySlotContents(slot, out);
            } else {
                player.inventory.setItemStack(out);
                updateHeld(player);
            }

            itemStorage.extractItems(bucketStack, Actionable.MODULATE, source);
            fluidStorage.extractItems(aeFluid, Actionable.MODULATE, source);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static long injectToNetworkResource(IStorageGrid grid, IResourceProvider provider,
                long amount, IActionSource source) {
            if (amount <= 0) return 0;
            IPacketType packetType = provider.getPacketType();
            if (packetType == null) return 0;
            
            IStorageChannel resourceChannel = provider.getStorageChannel();
            if (resourceChannel == null) return 0;
            
            IMEMonitor resourceMonitor = grid.getInventory(resourceChannel);
            if (resourceMonitor == null) return 0;
            
            IAEStack toInject = packetType.createResourceStack(amount);
            if (toInject == null) return 0;
            
            IAEStack result = (IAEStack) resourceMonitor.injectItems(toInject, Actionable.MODULATE, source);
            long injected = result == null ? amount : amount - result.getStackSize();
            return injected;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static long extractFromNetworkResource(IStorageGrid grid, IResourceProvider provider,
                long amount, IActionSource source) {
            if (amount <= 0) return 0;
            IPacketType packetType = provider.getPacketType();
            if (packetType == null) return 0;
            
            IStorageChannel resourceChannel = provider.getStorageChannel();
            if (resourceChannel == null) return 0;
            
            IMEMonitor resourceMonitor = grid.getInventory(resourceChannel);
            if (resourceMonitor == null) return 0;
            
            IAEStack toExtract = packetType.createResourceStack(amount);
            if (toExtract == null) return 0;
            
            IAEStack result = (IAEStack) resourceMonitor.extractItems(toExtract, Actionable.MODULATE, source);
            if (result == null) return 0;
            return result.getStackSize();
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static long injectToNetworkResourceSimulate(IStorageGrid grid, IResourceProvider provider,
                long amount, IActionSource source) {
            if (amount <= 0) return 0;
            IPacketType packetType = provider.getPacketType();
            if (packetType == null) return 0;
            
            IStorageChannel resourceChannel = provider.getStorageChannel();
            if (resourceChannel == null) return 0;
            
            IMEMonitor resourceMonitor = grid.getInventory(resourceChannel);
            if (resourceMonitor == null) return 0;
            
            IAEStack toInject = packetType.createResourceStack(amount);
            if (toInject == null) return 0;
            
            IAEStack result = (IAEStack) resourceMonitor.injectItems(toInject, Actionable.SIMULATE, source);
            long canInject = result == null ? amount : amount - result.getStackSize();
            return canInject;
        }

        private static void handleCustomInteract(final CPacketMEMonitorableAction message, final EntityPlayerMP player) {
            IStorageGrid grid = getGrid(player);
            if (grid == null) return;
            IActionSource source = getSource(player);

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

            if (hasContent && currentAmount > 0) {
                long toExtract = currentAmount;
                
                long simulatedInject = injectToNetworkResourceSimulate(grid, provider, toExtract, source);
                if (simulatedInject <= 0) return;
                
                long extracted = handler.extract(h, toExtract, source);
                if (extracted > 0) {
                    injectToNetworkResource(grid, provider, extracted, source);
                }
            } else if (!hasContent) {
                long maxCapacity = handler.getMaxCapacity(h);
                long availableSpace = maxCapacity - currentAmount;
                if (availableSpace <= 0) return;
                
                long toRequest = Math.min(amount, availableSpace);
                
                long networkAmount = extractFromNetworkResource(grid, provider, toRequest, source);
                if (networkAmount > 0) {
                    handler.inject(h, networkAmount, source);
                }
            }

            updateHeld(player);
        }

        private static void handleCustomOperate(final CPacketMEMonitorableAction message, final EntityPlayerMP player) {
            IStorageGrid grid = getGrid(player);
            if (grid == null) return;
            IActionSource source = getSource(player);

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
        }

        private static void handleCustomDeposit(final CPacketMEMonitorableAction message, final EntityPlayerMP player) {
            final Container c = player.openContainer;
            if (!(c instanceof ContainerMEMonitorable)) return;
            
            ContainerMEMonitorable cme = (ContainerMEMonitorable) c;
            
            ItemStack h = player.inventory.getItemStack();
            if (h.isEmpty()) return;

            appeng.container.AEBaseContainer baseContainer = (appeng.container.AEBaseContainer) cme;
            IEnergySource powerSource = baseContainer.getPowerSource();
            IMEInventoryHandler<IAEItemStack> cellInventory = baseContainer.getCellInventory();
            
            if (powerSource == null || cellInventory == null) return;

            IAEItemStack aeStack = appeng.api.AEApi.instance().storage()
                .getStorageChannel(IItemStorageChannel.class)
                .createStack(h);
            if (aeStack == null) return;
            
            IActionSource source = new PlayerSource(player, (IActionHost) cme.getTarget());
            
            IAEItemStack result = Platform.poweredInsert(powerSource, cellInventory, aeStack, source);
            
            if (result == null) {
                player.inventory.setItemStack(ItemStack.EMPTY);
            } else {
                player.inventory.setItemStack(result.createItemStack());
            }
            updateHeld(player);
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

package nyonio.terminal_interaction_integration.network;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import nyonio.terminal_interaction_integration.ae2.FluidContainerHandler;
import nyonio.terminal_interaction_integration.api.IContainerHandler;
import nyonio.terminal_interaction_integration.api.IPacketType;
import nyonio.terminal_interaction_integration.api.IResourceProvider;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public class ContainerTransferTest {
    @Test
    public void unacceptedExtractedResourceIsReturnedToTheNetwork() {
        Fixture f = new Fixture(100);
        ItemStack container = mock(ItemStack.class);
        ItemStack probe = mock(ItemStack.class);
        IContainerHandler handler = mock(IContainerHandler.class);
        when(container.copy()).thenReturn(probe);
        when(handler.getMaxCapacity(container)).thenReturn(100L);
        when(handler.getStoredAmount(container)).thenReturn(0L);
        when(handler.inject(probe, 100, f.source)).thenReturn(80L);
        // Model a container that accepts less on the real operation.
        when(handler.inject(container, 80, f.source)).thenReturn(60L);

        long moved = TerminalInteractionPacket.fill(f.grid, f.provider, handler, container, f.source, 100);
        assertEquals(60, moved);
        assertEquals(40, f.amount.get());
        assertEquals(100, moved + f.amount.get());
    }

    @Test
    public void drainingDoesNotRemoveMoreThanTheNetworkCanAccept() {
        Fixture f = new Fixture(90);
        f.capacity = 150;
        ItemStack container = mock(ItemStack.class);
        IContainerHandler handler = mock(IContainerHandler.class);
        when(handler.getStoredAmount(container)).thenReturn(100L);
        when(handler.extract(container, 60, f.source)).thenReturn(60L);

        long moved = TerminalInteractionPacket.drain(f.grid, f.provider, handler, container, f.source);
        assertEquals(60, moved);
        assertEquals(150, f.amount.get());
        verify(handler).extract(container, 60, f.source);
    }

    @Test
    public void fluidTankFillsToCapacityDespiteA64000PerCallLimit() {
        Fixture f = new Fixture(1000000);
        try (RateLimitedContainer tank = new RateLimitedContainer(256000, 64000, 0)) {
            assertEquals(256000, tank.handler.getMaxCapacity(tank.item));
            long moved = TerminalInteractionPacket.fill(f.grid, f.provider, tank.handler,
                    tank.item, f.source, Long.MAX_VALUE);

            assertEquals(256000, moved);
            assertEquals(256000, tank.stored());
            assertEquals(744000, f.amount.get());
            assertEquals(1000000, tank.stored() + f.amount.get());
        }
    }

    @Test
    public void fluidTankOnlyTakesWhatTheNetworkHas() {
        Fixture f = new Fixture(100000);
        try (RateLimitedContainer tank = new RateLimitedContainer(256000, 64000, 0)) {
            long moved = TerminalInteractionPacket.fill(f.grid, f.provider, tank.handler,
                    tank.item, f.source, Long.MAX_VALUE);

            assertEquals(100000, moved);
            assertEquals(100000, tank.stored());
            assertEquals(0, f.amount.get());
        }
    }

    @Test
    public void fluidTankDrainsAllItsContentsDespiteAPerCallLimit() {
        Fixture f = new Fixture(0);
        try (RateLimitedContainer tank = new RateLimitedContainer(256000, 64000, 256000)) {
            assertEquals(256000, tank.handler.getStoredAmount(tank.item));
            long moved = TerminalInteractionPacket.drain(f.grid, f.provider, tank.handler, tank.item, f.source);

            assertEquals(256000, moved);
            assertEquals(0, tank.stored());
            assertEquals(256000, f.amount.get());
        }
    }

    @Test
    public void refusingFluidContainerDoesNotConsumeNetworkResources() {
        Fixture f = new Fixture(1000000);
        try (RateLimitedContainer tank = new RateLimitedContainer(256000, 0, 0)) {
            assertEquals(0, TerminalInteractionPacket.fill(f.grid, f.provider, tank.handler,
                    tank.item, f.source, Long.MAX_VALUE));
            assertEquals(0, tank.stored());
            assertEquals(1000000, f.amount.get());
            verify(f.monitor, never()).extractItems(any(), eq(Actionable.MODULATE), any());
        }
    }

    /** Forge capability double with separate contents for every ItemStack copy. */
    private static final class RateLimitedContainer implements AutoCloseable {
        final ItemStack item = mock(ItemStack.class);
        final Map<ItemStack, AtomicLong> contents = new IdentityHashMap<>();
        final Map<ItemStack, IFluidHandlerItem> capabilities = new IdentityHashMap<>();
        final MockedStatic<FluidUtil> util;
        final FluidContainerHandler handler;
        final int capacity;
        final int rate;

        RateLimitedContainer(int capacity, int rate, int initialAmount) {
            this.capacity = capacity;
            this.rate = rate;
            util = mockStatic(FluidUtil.class);
            util.when(() -> FluidUtil.getFluidHandler(any(ItemStack.class))).thenAnswer(invocation -> {
                IFluidHandlerItem capability = capabilities.get(invocation.getArgument(0));
                return capability == null ? LazyOptional.empty() : LazyOptional.of(() -> capability);
            });
            initialize(item, initialAmount);
            handler = new FluidContainerHandler(fluid(1));
        }

        private void initialize(ItemStack stack, long amount) {
            AtomicLong stored = new AtomicLong(amount);
            contents.put(stack, stored);
            IFluidHandlerItem capability = mock(IFluidHandlerItem.class);
            capabilities.put(stack, capability);
            when(stack.copy()).thenAnswer(invocation -> {
                ItemStack copy = mock(ItemStack.class);
                initialize(copy, stored.get());
                return copy;
            });
            when(capability.getContainer()).thenReturn(stack);
            when(capability.getTanks()).thenReturn(1);
            when(capability.getTankCapacity(0)).thenReturn(capacity);
            when(capability.getFluidInTank(0)).thenAnswer(invocation -> fluid((int) stored.get()));
            when(capability.isFluidValid(eq(0), any(FluidStack.class))).thenReturn(true);
            when(capability.fill(any(FluidStack.class), any(FluidAction.class))).thenAnswer(invocation -> {
                FluidStack request = invocation.getArgument(0);
                FluidAction action = invocation.getArgument(1);
                int accepted = (int) Math.min(rate, Math.min(request.getAmount(), capacity - stored.get()));
                if (action.execute()) stored.addAndGet(accepted);
                return accepted;
            });
            when(capability.drain(any(FluidStack.class), any(FluidAction.class))).thenAnswer(invocation -> {
                FluidStack request = invocation.getArgument(0);
                FluidAction action = invocation.getArgument(1);
                int removed = (int) Math.min(rate, Math.min(request.getAmount(), stored.get()));
                if (action.execute()) stored.addAndGet(-removed);
                return fluid(removed);
            });
        }

        private FluidStack fluid(int amount) {
            FluidStack fluid = mock(FluidStack.class);
            AtomicLong size = new AtomicLong(amount);
            when(fluid.getAmount()).thenAnswer(invocation -> (int) size.get());
            when(fluid.isEmpty()).thenAnswer(invocation -> size.get() <= 0);
            when(fluid.copy()).thenAnswer(invocation -> fluid((int) size.get()));
            doAnswer(invocation -> { size.set((int) invocation.getArgument(0)); return null; })
                    .when(fluid).setAmount(anyInt());
            when(fluid.isFluidEqual(any(FluidStack.class)))
                    .thenAnswer(invocation -> !((FluidStack) invocation.getArgument(0)).isEmpty());
            return fluid;
        }

        long stored() { return contents.get(item).get(); }
        @Override public void close() { util.close(); }
    }

    private static final class Fixture {
        final AtomicLong amount;
        long capacity = Long.MAX_VALUE;
        final IStorageGrid grid = mock(IStorageGrid.class);
        final IStorageChannel channel = mock(IStorageChannel.class);
        final IMEMonitor monitor = mock(IMEMonitor.class);
        final IResourceProvider provider = mock(IResourceProvider.class);
        final IPacketType packet = mock(IPacketType.class);
        final IActionSource source = mock(IActionSource.class);

        Fixture(long initialAmount) {
            amount = new AtomicLong(initialAmount);
            when(grid.getInventory(channel)).thenReturn(monitor);
            when(provider.getStorageChannel()).thenReturn(channel);
            when(provider.getPacketType()).thenReturn(packet);
            when(packet.createResourceStack(anyLong())).thenAnswer(invocation ->
                    stack(invocation.getArgument(0)));
            when(monitor.extractItems(any(IAEStack.class), any(Actionable.class), any(IActionSource.class)))
                    .thenAnswer(invocation -> {
                        IAEStack request = invocation.getArgument(0);
                        Actionable mode = invocation.getArgument(1);
                        long taken = Math.min(amount.get(), request.getStackSize());
                        if (mode == Actionable.MODULATE) amount.addAndGet(-taken);
                        return taken == 0 ? null : stack(taken);
                    });
            when(monitor.injectItems(any(IAEStack.class), any(Actionable.class), any(IActionSource.class)))
                    .thenAnswer(invocation -> {
                        IAEStack request = invocation.getArgument(0);
                        Actionable mode = invocation.getArgument(1);
                        long inserted = Math.min(capacity - amount.get(), request.getStackSize());
                        if (mode == Actionable.MODULATE) amount.addAndGet(inserted);
                        long remaining = request.getStackSize() - inserted;
                        return remaining == 0 ? null : stack(remaining);
                    });
        }

        private IAEStack stack(long amount) {
            IAEStack stack = mock(IAEStack.class);
            when(stack.getStackSize()).thenReturn(amount);
            return stack;
        }
    }
}

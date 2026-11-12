package nyonio.terminal_interaction_integration.network;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import net.minecraft.item.ItemStack;
import nyonio.terminal_interaction_integration.api.IContainerHandler;
import nyonio.terminal_interaction_integration.api.IPacketType;
import nyonio.terminal_interaction_integration.api.IResourceProvider;
import org.junit.Test;

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

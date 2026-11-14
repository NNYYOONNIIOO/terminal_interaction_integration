package nyonio.terminal_interaction_integration.compat.crazyae;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import net.minecraft.item.ItemStack;
import nyonio.terminal_interaction_integration.ae2.ResourceTransfers;
import nyonio.terminal_interaction_integration.ae2.ResourceTransfers.Source;
import nyonio.terminal_interaction_integration.api.IContainerHandler;
import nyonio.terminal_interaction_integration.api.IPacketType;
import nyonio.terminal_interaction_integration.api.IResourceProvider;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static nyonio.terminal_interaction_integration.testsupport.TestStacks.stack;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public class MappedContainerTransferTest {
    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private final IStorageGrid grid = mock(IStorageGrid.class);
    private final IStorageChannel channel = mock(IStorageChannel.class);
    private final IMEMonitor monitor = mock(IMEMonitor.class);
    private final IActionSource playerSource = mock(IActionSource.class);
    private final IResourceProvider provider = mock(IResourceProvider.class);
    private final IContainerHandler handler = mock(IContainerHandler.class);

    public MappedContainerTransferTest() {
        IPacketType packet = mock(IPacketType.class);
        when(provider.getName()).thenReturn("fill_test_" + NEXT_ID.incrementAndGet());
        when(provider.getContainerHandler()).thenReturn(handler);
        when(provider.getPacketType()).thenReturn(packet);
        when(grid.getInventory(channel)).thenReturn(monitor);
        CrazyAEIntegration.register(provider, new Source(channel, stack("crazy_fe", 1)), false);
    }

    // The handler is stubbed, so EMPTY is just a token for a disposable container copy.
    // No Minecraft registry, capability provider or game world is initialized.
    @Test public void debitsOnlyWhatRateLimitedContainerAccepted() {
        when(monitor.extractItems(any(), eq(Actionable.SIMULATE), same(playerSource)))
                .thenReturn(stack("crazy_fe", 100));
        when(handler.inject(any(), eq(100L), same(playerSource))).thenReturn(30L);
        when(monitor.extractItems(any(), eq(Actionable.MODULATE), same(playerSource))).thenAnswer(call -> {
            IAEStack request = call.getArgument(0);
            assertEquals(30, request.getStackSize());
            return request.copy();
        });
        assertNotNull(ResourceTransfers.fillMappedContainer(grid, provider, ItemStack.EMPTY, 100, playerSource));
        verify(monitor, never()).injectItems(any(), any(), any());
    }

    @Test public void refusingContainerNeverDebitsNetwork() {
        when(monitor.extractItems(any(), eq(Actionable.SIMULATE), same(playerSource)))
                .thenReturn(stack("crazy_fe", 100));
        when(handler.inject(any(), anyLong(), any())).thenReturn(0L);
        assertNull(ResourceTransfers.fillMappedContainer(grid, provider, ItemStack.EMPTY, 100, playerSource));
        verify(monitor, never()).extractItems(any(), eq(Actionable.MODULATE), any());
    }

    @Test public void availabilityChangeFillsOnlyActualDebit() {
        when(monitor.extractItems(any(), eq(Actionable.SIMULATE), same(playerSource)))
                .thenReturn(stack("crazy_fe", 100));
        when(handler.inject(any(), eq(100L), same(playerSource))).thenReturn(30L);
        when(monitor.extractItems(any(), eq(Actionable.MODULATE), same(playerSource)))
                .thenReturn(stack("crazy_fe", 10));
        when(handler.inject(any(), eq(10L), same(playerSource))).thenReturn(10L);
        assertNotNull(ResourceTransfers.fillMappedContainer(grid, provider, ItemStack.EMPTY, 100, playerSource));
        verify(handler).inject(any(), eq(10L), same(playerSource));
        verify(monitor, never()).injectItems(any(), any(), any());
    }

    @Test public void emptiedNetworkDiscardsPreparedContainer() {
        when(monitor.extractItems(any(), eq(Actionable.SIMULATE), same(playerSource)))
                .thenReturn(stack("crazy_fe", 100));
        when(handler.inject(any(), eq(100L), same(playerSource))).thenReturn(30L);
        when(monitor.extractItems(any(), eq(Actionable.MODULATE), same(playerSource))).thenReturn(null);
        assertNull(ResourceTransfers.fillMappedContainer(grid, provider, ItemStack.EMPTY, 100, playerSource));
    }
}

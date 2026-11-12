package nyonio.terminal_interaction_integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import nyonio.terminal_interaction_integration.api.IPacketType;
import nyonio.terminal_interaction_integration.api.IResourceProvider;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public class VirtualInventoryTest {
    @Test
    public void simulateExtractionUsesTheRealChannelAndPreservesTheSource() {
        IStorageGrid grid = mock(IStorageGrid.class);
        IStorageChannel channel = mock(IStorageChannel.class);
        IMEMonitor monitor = mock(IMEMonitor.class);
        IResourceProvider provider = mock(IResourceProvider.class);
        IPacketType packet = mock(IPacketType.class);
        IAEStack request = mock(IAEStack.class);
        IAEStack result = mock(IAEStack.class);
        IActionSource source = mock(IActionSource.class);
        when(source.player()).thenReturn(Optional.empty());
        when(provider.getStorageChannel()).thenReturn(channel);
        when(provider.getPacketType()).thenReturn(packet);
        when(packet.createResourceStack(100)).thenReturn(request);
        when(grid.getInventory(channel)).thenReturn(monitor);
        when(monitor.extractItems(eq(request), eq(Actionable.SIMULATE), any(IActionSource.class)))
                .thenReturn(result);
        when(result.getStackSize()).thenReturn(75L);

        assertEquals(75, VirtualInventory.extract(grid, provider, 100, Actionable.SIMULATE, source));
        ArgumentCaptor<IActionSource> captured = ArgumentCaptor.forClass(IActionSource.class);
        verify(monitor).extractItems(eq(request), eq(Actionable.SIMULATE), captured.capture());
        assertFalse(captured.getValue().player().isPresent());
        verify(request).setStackSize(100);
        verify(monitor, never()).extractItems(any(), eq(Actionable.MODULATE), any());
    }

    @Test
    public void insertionReturnsOnlyWhatTheNetworkAccepted() {
        IStorageGrid grid = mock(IStorageGrid.class);
        IStorageChannel channel = mock(IStorageChannel.class);
        IMEMonitor monitor = mock(IMEMonitor.class);
        IResourceProvider provider = mock(IResourceProvider.class);
        IPacketType packet = mock(IPacketType.class);
        IAEStack request = mock(IAEStack.class);
        IAEStack remainder = mock(IAEStack.class);
        IActionSource source = mock(IActionSource.class);
        when(provider.getStorageChannel()).thenReturn(channel);
        when(provider.getPacketType()).thenReturn(packet);
        when(packet.createResourceStack(100)).thenReturn(request);
        when(grid.getInventory(channel)).thenReturn(monitor);
        when(monitor.injectItems(eq(request), eq(Actionable.MODULATE), any(IActionSource.class)))
                .thenReturn(remainder);
        when(remainder.getStackSize()).thenReturn(30L);

        assertEquals(70, VirtualInventory.insert(grid, provider, 100, Actionable.MODULATE, source));
    }
}

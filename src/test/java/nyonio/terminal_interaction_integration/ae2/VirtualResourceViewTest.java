package nyonio.terminal_interaction_integration.ae2;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import nyonio.terminal_interaction_integration.api.IPacketType;
import nyonio.terminal_interaction_integration.api.IResourceProvider;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public class VirtualResourceViewTest {
    @Test
    public void readsOnlyTheResourceSelectedByTheProvider() {
        IResourceProvider provider = mock(IResourceProvider.class);
        IPacketType packet = mock(IPacketType.class);
        IMEMonitor monitor = mock(IMEMonitor.class);
        IItemList list = mock(IItemList.class);
        IAEStack key = mock(IAEStack.class);
        IAEStack stored = mock(IAEStack.class);
        when(provider.getPacketType()).thenReturn(packet);
        when(packet.createResourceStack(1)).thenReturn(key);
        when(monitor.getStorageList()).thenReturn(list);
        when(list.findPrecise(key)).thenReturn(stored);
        when(stored.getStackSize()).thenReturn(231L);

        VirtualResourceView view = new VirtualResourceView(provider, monitor, ignored -> { });
        assertEquals(231L, view.getAmount());
        verify(list).findPrecise(key);
        verify(list, never()).iterator();
        verify(monitor).addListener(view, view);
    }

    @Test
    public void missingResourceDoesNotUseAnotherChannelEntry() {
        IResourceProvider provider = mock(IResourceProvider.class);
        IPacketType packet = mock(IPacketType.class);
        IMEMonitor monitor = mock(IMEMonitor.class);
        IItemList list = mock(IItemList.class);
        IAEStack key = mock(IAEStack.class);
        when(provider.getPacketType()).thenReturn(packet);
        when(packet.createResourceStack(1)).thenReturn(key);
        when(monitor.getStorageList()).thenReturn(list);
        assertEquals(0L, new VirtualResourceView(provider, monitor, ignored -> { }).getAmount());
        verify(list, never()).iterator();
    }

    @Test
    public void resourceChangesAreReportedToTheOwningGrid() {
        IResourceProvider provider = mock(IResourceProvider.class);
        IMEMonitor monitor = mock(IMEMonitor.class);
        IActionSource source = mock(IActionSource.class);
        AtomicReference<IActionSource> received = new AtomicReference<>();
        VirtualResourceView view = new VirtualResourceView(provider, monitor, received::set);
        view.postChange(monitor, java.util.Collections.emptyList(), source);
        assertSame(source, received.get());
    }
}

package nyonio.terminal_interaction_integration.compat.crazyae;

import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.GridStorageCache;
import nyonio.terminal_interaction_integration.ae2.ResourceTransfers.Source;
import nyonio.terminal_interaction_integration.api.IPacketType;
import nyonio.terminal_interaction_integration.api.IResourceProvider;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static nyonio.terminal_interaction_integration.testsupport.TestStacks.stack;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public class CrazyAEGridViewTest {
    private final GridStorageCache grid = mock(GridStorageCache.class);
    private final IStorageChannel primary = mock(IStorageChannel.class);
    private final IStorageChannel legacy = mock(IStorageChannel.class);
    private final IMEMonitor primaryMonitor = mock(IMEMonitor.class);
    private final IMEMonitor legacyMonitor = mock(IMEMonitor.class);
    private final IItemList primaryList = mock(IItemList.class);
    private final IItemList legacyList = mock(IItemList.class);
    private final IResourceProvider provider = mock(IResourceProvider.class);
    private final IPacketType packet = mock(IPacketType.class);
    private final Map<String, Long> amounts = new HashMap<>();
    private final Source addonSource = new Source(primary, stack("addon_fe", 1));
    private final Source crazySource = new Source(legacy, stack("crazy_fe", 1));

    public CrazyAEGridViewTest() {
        when(grid.getInventory(primary)).thenReturn(primaryMonitor);
        when(grid.getInventory(legacy)).thenReturn(legacyMonitor);
        when(primaryMonitor.getStorageList()).thenReturn(primaryList);
        when(legacyMonitor.getStorageList()).thenReturn(legacyList);
        when(primaryList.findPrecise(eq(addonSource.key))).thenAnswer(call -> stack("addon_fe", amounts.getOrDefault("addon", 0L)));
        when(legacyList.findPrecise(eq(crazySource.key))).thenAnswer(call -> stack("crazy_fe", amounts.getOrDefault("crazy", 0L)));
        when(provider.getPacketType()).thenReturn(packet);
        // Models an addon packet factory which cannot represent a negative delta itself.
        when(packet.createAEStack(anyLong())).thenAnswer(call -> stack("display_fe", Math.max(1, (long) call.getArgument(0))));
    }

    private CrazyAEGridView view(boolean addon) {
        Source[] sources = addon ? new Source[] { addonSource, crazySource } : new Source[] { crazySource };
        return new CrazyAEGridView(grid, new CrazyAEIntegration.Mapping(provider, sources, crazySource));
    }

    @Test public void repeatedDisplayReadsNeverDoubleCount() {
        amounts.put("addon", 50L);
        amounts.put("crazy", 70L);
        CrazyAEGridView view = view(true);
        IItemList<IAEItemStack> out = mock(IItemList.class);
        IAEItemStack existing = stack("display_fe", 999);
        when(out.findPrecise(any())).thenReturn(existing);
        view.addTo(out);
        view.addTo(out);
        assertEquals(120, existing.getStackSize());
        verify(out, never()).addStorage(any());
    }

    @Test public void unrelatedEnergyDoesNotDirtyOrContributeToFE() {
        amounts.put("crazy", 100L);
        CrazyAEGridView view = view(false);
        clearInvocations(legacyMonitor, legacyList);
        view.postChange(legacyMonitor, Collections.singletonList(stack("crazy_eu", 500000)), null);
        for (int i = 0; i < 30; i++) assertNull(view.tick(10));
        verifyNoInteractions(legacyList);
        verify(legacyMonitor, never()).getStorageList();
    }

    @Test public void manyEventsCoalesceAndRemovalPublishesNegativeDelta() {
        amounts.put("crazy", 100L);
        CrazyAEGridView view = view(false);
        clearInvocations(legacyList);
        amounts.put("crazy", 0L);
        for (int i = 0; i < 100; i++) {
            view.postChange(legacyMonitor, Collections.singletonList(stack("crazy_fe", -1)), null);
        }
        for (int i = 0; i < 9; i++) assertNull(view.tick(10));
        assertEquals(-100, view.tick(10).getStackSize());
        verify(legacyList, times(1)).findPrecise(eq(crazySource.key));
        assertNull(view.tick(10));
    }

    @Test public void refreshReadsOnlyDirtySource() {
        amounts.put("addon", 20L);
        amounts.put("crazy", 30L);
        CrazyAEGridView view = view(true);
        clearInvocations(primaryList, legacyList);
        amounts.put("crazy", 50L);
        view.postChange(legacyMonitor, Collections.singletonList(stack("crazy_fe", 20)), null);
        assertEquals(20, view.tick(1).getStackSize());
        verifyNoInteractions(primaryList);
        verify(legacyList, times(1)).findPrecise(eq(crazySource.key));
    }

    @Test public void fullListChangeRefreshesBothSourcesAndSaturatesSafely() {
        CrazyAEGridView view = view(true);
        amounts.put("addon", Long.MAX_VALUE);
        amounts.put("crazy", Long.MAX_VALUE);
        view.onListUpdate();
        assertEquals(Long.MAX_VALUE, view.tick(1).getStackSize());
        amounts.put("addon", 0L);
        amounts.put("crazy", 0L);
        view.onListUpdate();
        assertEquals(-Long.MAX_VALUE, view.tick(1).getStackSize());
    }
}

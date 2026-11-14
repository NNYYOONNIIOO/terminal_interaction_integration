package nyonio.terminal_interaction_integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import nyonio.terminal_interaction_integration.ae2.ResourceTransfers.Source;
import org.junit.Test;

import static nyonio.terminal_interaction_integration.testsupport.TestStacks.stack;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public class ResourceTransfersTest {
    private final IStorageGrid grid = mock(IStorageGrid.class);
    private final IActionSource playerSource = mock(IActionSource.class);
    private final IStorageChannel primary = mock(IStorageChannel.class);
    private final IStorageChannel legacy = mock(IStorageChannel.class);
    private final IMEMonitor primaryMonitor = mock(IMEMonitor.class);
    private final IMEMonitor legacyMonitor = mock(IMEMonitor.class);
    private final Source[] sources = {
            new Source(primary, stack("addon_fe", 999)),
            new Source(legacy, stack("crazy_fe", 999))
    };

    public ResourceTransfersTest() {
        when(grid.getInventory(primary)).thenReturn(primaryMonitor);
        when(grid.getInventory(legacy)).thenReturn(legacyMonitor);
    }

    @Test public void extractionRequestsOnlyRemainingAmountFromSecondSource() {
        long[] balances = { 30, 90 };
        when(primaryMonitor.extractItems(any(), eq(Actionable.MODULATE), same(playerSource))).thenAnswer(call -> {
            IAEStack request = call.getArgument(0);
            assertEquals(stack("addon_fe", 1), request);
            assertEquals(80, request.getStackSize());
            balances[0] -= 30;
            return request.copy().setStackSize(30);
        });
        when(legacyMonitor.extractItems(any(), eq(Actionable.MODULATE), same(playerSource))).thenAnswer(call -> {
            IAEStack request = call.getArgument(0);
            assertEquals(stack("crazy_fe", 1), request);
            assertEquals(50, request.getStackSize());
            balances[1] -= request.getStackSize();
            return request.copy();
        });
        assertEquals(80, ResourceTransfers.transferSources(grid, sources, 80,
                Actionable.MODULATE, playerSource, false));
        assertArrayEquals(new long[] { 0, 40 }, balances);
        assertEquals(1, sources[0].key.getStackSize());
        assertEquals(1, sources[1].key.getStackSize());
    }

    @Test public void simulationReachesBothSourcesWithoutModulating() {
        when(primaryMonitor.extractItems(any(), eq(Actionable.SIMULATE), same(playerSource)))
                .thenReturn(stack("addon_fe", 20));
        when(legacyMonitor.extractItems(any(), eq(Actionable.SIMULATE), same(playerSource)))
                .thenReturn(stack("crazy_fe", 35));
        assertEquals(55, ResourceTransfers.transferSources(grid, sources, 70,
                Actionable.SIMULATE, playerSource, false));
        verify(primaryMonitor, never()).extractItems(any(), eq(Actionable.MODULATE), any());
        verify(legacyMonitor, never()).extractItems(any(), eq(Actionable.MODULATE), any());
    }

    @Test public void insertionFallsBackWithOnlyUnacceptedRemainder() {
        when(primaryMonitor.injectItems(any(), eq(Actionable.MODULATE), same(playerSource)))
                .thenReturn(stack("addon_fe", 40));
        when(legacyMonitor.injectItems(any(), eq(Actionable.MODULATE), same(playerSource))).thenAnswer(call -> {
            IAEStack request = call.getArgument(0);
            assertEquals(40, request.getStackSize());
            assertEquals(stack("crazy_fe", 1), request);
            return request.copy().setStackSize(10);
        });
        assertEquals(90, ResourceTransfers.transferSources(grid, sources, 100,
                Actionable.MODULATE, playerSource, true));
    }

    @Test public void satisfiedFirstSourceNeverTouchesSecondMonitor() {
        when(primaryMonitor.extractItems(any(), any(), same(playerSource))).thenReturn(stack("addon_fe", 100));
        assertEquals(100, ResourceTransfers.transferSources(grid, sources, 100,
                Actionable.MODULATE, playerSource, false));
        verifyNoInteractions(legacyMonitor);
    }

    @Test public void missingSourceDoesNotReportSuccessfulTransfer() {
        when(grid.getInventory(primary)).thenReturn(null);
        when(grid.getInventory(legacy)).thenReturn(null);
        assertEquals(0, ResourceTransfers.transferSources(grid, sources, 100,
                Actionable.MODULATE, playerSource, true));
        assertEquals(0, ResourceTransfers.transferSources(grid, sources, -100,
                Actionable.MODULATE, playerSource, false));
    }
}

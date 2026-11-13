package nyonio.terminal_interaction_integration.ae2;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import nyonio.terminal_interaction_integration.TIIConfig;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercise monitor invalidation without launching Minecraft. */
@SuppressWarnings("unchecked")
public class FluidResourceViewTest {
    @Test
    public void ordinaryItemRebuildsReuseTheFluidSnapshot() {
        try (Fixture f = new Fixture()) {
            FluidResourceView view = f.view();
            IItemList<IAEItemStack> first = mock(IItemList.class);
            view.addTo(first);
            when(f.fluid.getStackSize()).thenReturn(500L);
            view.postChange(f.monitor, Collections.singletonList(f.fluid), f.source);

            IItemList<IAEItemStack> beforeUpdate = mock(IItemList.class);
            view.addTo(beforeUpdate);
            assertEquals(1500, addedAmount(beforeUpdate));
            verify(f.monitor, times(1)).getStorageList();
            assertTrue(view.hasChanges());

            assertEquals(1, view.takeChanges().size());
            IItemList<IAEItemStack> afterUpdate = mock(IItemList.class);
            view.addTo(afterUpdate);
            assertEquals(500, addedAmount(afterUpdate));
            assertFalse(view.hasChanges());
            verify(f.monitor, times(2)).getStorageList();
        }
    }

    @Test
    public void fullListRemovalSurvivesAnOrdinaryItemRebuildBeforeFlush() {
        try (Fixture f = new Fixture()) {
            FluidResourceView view = f.view();
            view.addTo(mock(IItemList.class));
            when(f.storage.iterator()).thenAnswer(invocation -> Collections.<IAEFluidStack>emptyList().iterator());
            view.onListUpdate();
            view.addTo(mock(IItemList.class));

            assertEquals("Clients must receive the identity of the removed fluid", 1, view.takeChanges().size());
            IItemList<IAEItemStack> empty = mock(IItemList.class);
            view.addTo(empty);
            verify(empty, never()).addStorage(any());
            assertFalse(view.hasChanges());
        }
    }

    @Test
    public void disablingClearsExistingEntriesAndReenablingRescansOnce() {
        try (Fixture f = new Fixture()) {
            FluidResourceView view = f.view();
            view.addTo(mock(IItemList.class));
            f.enabled.set(false);
            assertTrue(view.hasChanges());
            assertEquals(1, view.takeChanges().size());
            IItemList<IAEItemStack> disabled = mock(IItemList.class);
            view.addTo(disabled);
            verify(disabled, never()).addStorage(any());
            verify(f.monitor, times(1)).getStorageList();

            f.enabled.set(true);
            assertTrue(view.hasChanges());
            assertEquals(1, view.takeChanges().size());
            IItemList<IAEItemStack> enabled = mock(IItemList.class);
            view.addTo(enabled);
            assertEquals(1500, addedAmount(enabled));
            verify(f.monitor, times(2)).getStorageList();
        }
    }

    @Test
    public void addonsKeepTheirOwnFluidRepresentation() {
        try (Fixture f = new Fixture()) {
            f.bridge.when(FluidTerminalIntegration::addonFluids).thenReturn(Collections.singleton(f.fluid));
            FluidResourceView view = f.view();
            IItemList<IAEItemStack> out = mock(IItemList.class);
            view.addTo(out);
            verify(out, never()).addStorage(any());
        }
    }

    private static long addedAmount(IItemList<IAEItemStack> list) {
        ArgumentCaptor<IAEItemStack> result = ArgumentCaptor.forClass(IAEItemStack.class);
        verify(list).addStorage(result.capture());
        return result.getValue().getStackSize();
    }

    private static final class Fixture implements AutoCloseable {
        final AtomicBoolean enabled = new AtomicBoolean(true);
        final MockedStatic<TIIConfig> config = mockStatic(TIIConfig.class);
        final MockedStatic<FluidTerminalIntegration> bridge = mockStatic(FluidTerminalIntegration.class);
        final IMEMonitor<IAEFluidStack> monitor = mock(IMEMonitor.class);
        final IItemList<IAEFluidStack> storage = mock(IItemList.class);
        final IAEFluidStack fluid = mock(IAEFluidStack.class);
        final IActionSource source = mock(IActionSource.class);

        Fixture() {
            config.when(TIIConfig::isFluidTerminalIntegrationEnabled).thenAnswer(invocation -> enabled.get());
            bridge.when(FluidTerminalIntegration::addonFluids).thenReturn(Collections.emptySet());
            bridge.when(() -> FluidTerminalIntegration.display(any(IAEFluidStack.class), anyLong()))
                    .thenAnswer(invocation -> {
                        long amount = invocation.getArgument(1);
                        IAEItemStack result = mock(IAEItemStack.class);
                        when(result.getStackSize()).thenReturn(amount);
                        when(result.copy()).thenReturn(result);
                        return result;
                    });
            when(fluid.copy()).thenReturn(fluid);
            when(fluid.getStackSize()).thenReturn(1500L);
            when(storage.iterator()).thenAnswer(invocation -> Collections.singletonList(fluid).iterator());
            when(storage.findPrecise(fluid)).thenReturn(fluid);
            when(monitor.getStorageList()).thenReturn(storage);
        }

        FluidResourceView view() {
            FluidResourceView view = new FluidResourceView(monitor);
            verify(monitor).addListener(view, view);
            return view;
        }

        @Override public void close() { bridge.close(); config.close(); }
    }
}

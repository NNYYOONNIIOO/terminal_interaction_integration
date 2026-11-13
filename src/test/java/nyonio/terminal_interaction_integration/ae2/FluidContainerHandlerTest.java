package nyonio.terminal_interaction_integration.ae2;

import appeng.api.networking.security.IActionSource;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class FluidContainerHandlerTest {
    @Test
    public void fillingKeepsTheReplacementWhenTheOriginalCapabilityIsInvalidated() {
        ItemStack empty = mock(ItemStack.class);
        ItemStack filled = mock(ItemStack.class);
        FluidStack fluid = fluid();
        IFluidHandlerItem capability = mock(IFluidHandlerItem.class);
        try (MockedStatic<FluidUtil> util = mockStatic(FluidUtil.class)) {
            util.when(() -> FluidUtil.getFluidHandler(empty)).thenReturn(LazyOptional.of(() -> capability));
            when(capability.fill(any(FluidStack.class), eq(FluidAction.EXECUTE))).thenReturn(1000);
            when(capability.getContainer()).thenReturn(filled);
            FluidContainerHandler handler = new FluidContainerHandler(fluid);

            assertEquals(1000, handler.inject(empty, 1000, mock(IActionSource.class)));
            util.when(() -> FluidUtil.getFluidHandler(empty)).thenReturn(LazyOptional.empty());
            assertSame(filled, handler.getResultContainer(empty));
        }
    }

    @Test
    public void aRefundUsesTheReplacementContainerAfterDraining() {
        ItemStack filled = mock(ItemStack.class);
        ItemStack empty = mock(ItemStack.class);
        ItemStack refunded = mock(ItemStack.class);
        FluidStack fluid = fluid();
        FluidStack drained = mock(FluidStack.class);
        when(fluid.isFluidEqual(drained)).thenReturn(true);
        when(drained.getAmount()).thenReturn(1000);
        IFluidHandlerItem fullCapability = mock(IFluidHandlerItem.class);
        IFluidHandlerItem emptyCapability = mock(IFluidHandlerItem.class);
        try (MockedStatic<FluidUtil> util = mockStatic(FluidUtil.class)) {
            util.when(() -> FluidUtil.getFluidHandler(filled)).thenReturn(LazyOptional.of(() -> fullCapability));
            util.when(() -> FluidUtil.getFluidHandler(empty)).thenReturn(LazyOptional.of(() -> emptyCapability));
            when(fullCapability.drain(any(FluidStack.class), any(FluidAction.class))).thenReturn(drained);
            when(fullCapability.getContainer()).thenReturn(empty);
            when(emptyCapability.fill(any(FluidStack.class), eq(FluidAction.EXECUTE))).thenReturn(1000);
            when(emptyCapability.getContainer()).thenReturn(refunded);
            FluidContainerHandler handler = new FluidContainerHandler(fluid);
            IActionSource source = mock(IActionSource.class);

            assertEquals(1000, handler.extract(filled, 1000, source));
            assertSame(empty, handler.getResultContainer(filled));
            util.when(() -> FluidUtil.getFluidHandler(filled)).thenReturn(LazyOptional.empty());
            assertEquals(1000, handler.inject(filled, 1000, source));
            assertSame(refunded, handler.getResultContainer(filled));
            verify(emptyCapability).fill(any(FluidStack.class), eq(FluidAction.EXECUTE));
        }
    }

    private static FluidStack fluid() {
        FluidStack fluid = mock(FluidStack.class);
        when(fluid.copy()).thenReturn(fluid);
        return fluid;
    }
}

package nyonio.terminal_interaction_integration.ae2;

import appeng.api.networking.security.IActionSource;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import nyonio.terminal_interaction_integration.api.IContainerHandler;

/** Uses the Forge item capability, including replacement items returned by buckets. */
public final class FluidContainerHandler implements IContainerHandler {
    private final FluidStack fluid;
    private ItemStack lastInput;
    private ItemStack lastResult;

    public FluidContainerHandler(FluidStack fluid) {
        this.fluid = fluid.copy();
        this.fluid.setAmount(1);
    }

    private FluidStack amount(long amount) {
        FluidStack request = fluid.copy();
        request.setAmount((int) Math.max(0, Math.min(Integer.MAX_VALUE, amount)));
        return request;
    }

    private IFluidHandlerItem handler(ItemStack stack) {
        return stack.isEmpty() ? null : FluidUtil.getFluidHandler(stack).orElse(null);
    }

    private IFluidHandlerItem mutableHandler(ItemStack stack) {
        return handler(stack == lastInput ? lastResult : stack);
    }

    private void rememberResult(ItemStack input, IFluidHandlerItem handler) {
        lastInput = input;
        lastResult = handler.getContainer();
    }

    private IFluidHandlerItem probe(ItemStack stack) {
        ItemStack one = stack.copy();
        one.setCount(1);
        return handler(one);
    }

    @Override
    public boolean canHandle(ItemStack container) {
        IFluidHandlerItem handler = probe(container);
        return handler != null && (!handler.drain(amount(Integer.MAX_VALUE), FluidAction.SIMULATE).isEmpty()
                || handler.fill(amount(Integer.MAX_VALUE), FluidAction.SIMULATE) > 0);
    }

    @Override
    public long getStoredAmount(ItemStack container) {
        IFluidHandlerItem handler = probe(container);
        if (handler == null) return 0;
        FluidStack drained = handler.drain(amount(Integer.MAX_VALUE), FluidAction.SIMULATE);
        return fluid.isFluidEqual(drained) ? drained.getAmount() : 0;
    }

    @Override
    public long getMaxCapacity(ItemStack container) {
        IFluidHandlerItem handler = probe(container);
        return handler == null ? 0 : getStoredAmount(container)
                + Math.max(0, handler.fill(amount(Integer.MAX_VALUE), FluidAction.SIMULATE));
    }

    @Override
    public long extract(ItemStack container, long amount, IActionSource source) {
        IFluidHandlerItem handler = mutableHandler(container);
        if (handler == null || amount <= 0) return 0;
        FluidStack possible = handler.drain(amount(amount), FluidAction.SIMULATE);
        if (possible.isEmpty() || !fluid.isFluidEqual(possible)) return 0;
        FluidStack drained = handler.drain(possible, FluidAction.EXECUTE);
        rememberResult(container, handler);
        return fluid.isFluidEqual(drained) ? drained.getAmount() : 0;
    }

    @Override
    public long inject(ItemStack container, long amount, IActionSource source) {
        IFluidHandlerItem handler = mutableHandler(container);
        if (handler == null || amount <= 0) return 0;
        int filled = handler.fill(amount(amount), FluidAction.EXECUTE);
        rememberResult(container, handler);
        return filled;
    }

    @Override public String getContainerDisplayName(ItemStack container) { return container.getDisplayName().getString(); }
    @Override public ItemStack getEmptyContainer() { return ItemStack.EMPTY; }

    @Override
    public ItemStack getResultContainer(ItemStack container) {
        return container == lastInput ? lastResult : container;
    }
}

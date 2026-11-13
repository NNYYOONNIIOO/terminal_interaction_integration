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
    // Bound work for capabilities that accept only tiny amounts per call.
    private static final int MAX_TRANSFER_STEPS = 4096;
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
        long stored = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack contents = handler.getFluidInTank(tank);
            if (fluid.isFluidEqual(contents)) stored += Math.max(0, contents.getAmount());
        }
        return stored;
    }

    @Override
    public long getMaxCapacity(ItemStack container) {
        IFluidHandlerItem handler = probe(container);
        if (handler == null) return 0;
        long capacity = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack contents = handler.getFluidInTank(tank);
            if (!contents.isEmpty() && !fluid.isFluidEqual(contents)) continue;
            int stored = contents.isEmpty() ? 0 : Math.max(0, contents.getAmount());
            capacity += handler.isFluidValid(tank, fluid)
                    ? Math.max(stored, handler.getTankCapacity(tank)) : stored;
        }
        return capacity;
    }

    @Override
    public long extract(ItemStack container, long amount, IActionSource source) {
        long removed = 0;
        for (int step = 0; removed < amount && step < MAX_TRANSFER_STEPS; step++) {
            IFluidHandlerItem handler = mutableHandler(container);
            if (handler == null) break;
            FluidStack possible = handler.drain(amount(amount - removed), FluidAction.SIMULATE);
            if (possible.isEmpty() || !fluid.isFluidEqual(possible)) break;
            FluidStack drained = handler.drain(possible, FluidAction.EXECUTE);
            rememberResult(container, handler);
            if (!fluid.isFluidEqual(drained) || drained.getAmount() <= 0) break;
            removed += Math.min(amount - removed, drained.getAmount());
        }
        return removed;
    }

    @Override
    public long inject(ItemStack container, long amount, IActionSource source) {
        long filled = 0;
        for (int step = 0; filled < amount && step < MAX_TRANSFER_STEPS; step++) {
            IFluidHandlerItem handler = mutableHandler(container);
            if (handler == null) break;
            int accepted = handler.fill(amount(amount - filled), FluidAction.EXECUTE);
            rememberResult(container, handler);
            if (accepted <= 0) break;
            filled += Math.min(amount - filled, accepted);
        }
        return filled;
    }

    @Override public String getContainerDisplayName(ItemStack container) { return container.getDisplayName().getString(); }
    @Override public ItemStack getEmptyContainer() { return ItemStack.EMPTY; }

    @Override
    public ItemStack getResultContainer(ItemStack container) {
        return container == lastInput ? lastResult : container;
    }
}

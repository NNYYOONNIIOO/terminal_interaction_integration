package nyonio.terminal_interaction_integration.compat.crazyae;

import appeng.api.networking.security.IActionSource;
import net.minecraft.item.ItemStack;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import nyonio.terminal_interaction_integration.api.IContainerHandler;

public final class ForgeEnergyContainerHandler implements IContainerHandler {
    private IEnergyStorage energy(ItemStack stack) {
        return stack.isEmpty() ? null : stack.getCapability(CapabilityEnergy.ENERGY, null);
    }
    @Override public boolean canHandle(ItemStack stack) { return energy(stack) != null; }
    @Override public long getStoredAmount(ItemStack stack) {
        IEnergyStorage energy = energy(stack);
        return energy == null ? 0 : Math.max(0, energy.getEnergyStored());
    }
    @Override public long getMaxCapacity(ItemStack stack) {
        IEnergyStorage energy = energy(stack);
        return energy == null ? 0 : Math.max(0, energy.getMaxEnergyStored());
    }
    @Override public long extract(ItemStack stack, long amount, IActionSource source) { return transfer(stack, amount, false); }
    @Override public long inject(ItemStack stack, long amount, IActionSource source) { return transfer(stack, amount, true); }

    private long transfer(ItemStack stack, long amount, boolean receive) {
        IEnergyStorage energy = energy(stack);
        if (energy == null || amount <= 0 || (receive ? !energy.canReceive() : !energy.canExtract())) return 0;
        long available = receive ? Math.max(0, (long) energy.getMaxEnergyStored() - energy.getEnergyStored())
                : Math.max(0, energy.getEnergyStored());
        long limit = Math.min(amount, available);
        long moved = 0;
        for (int step = 0; moved < limit && step < 4096; step++) {
            int request = (int) Math.min(Integer.MAX_VALUE, limit - moved);
            int transferred = receive ? energy.receiveEnergy(request, false) : energy.extractEnergy(request, false);
            if (transferred <= 0) break;
            moved += Math.min(request, transferred);
        }
        return moved;
    }
    @Override public String getContainerDisplayName(ItemStack stack) { return stack.getDisplayName(); }
    @Override public ItemStack getEmptyContainer() { return ItemStack.EMPTY; }
}

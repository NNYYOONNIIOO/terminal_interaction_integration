package nyonio.terminal_interaction_integration.compat.crazyae;

import appeng.api.networking.security.IActionSource;
import net.minecraft.item.ItemStack;
import nyonio.terminal_interaction_integration.api.IContainerHandler;
import vazkii.botania.api.mana.IManaItem;

/** Only instantiated when Botania and CrazyAE's mana feature are available. */
public final class CrazyManaContainerHandler implements IContainerHandler {
    @Override public boolean canHandle(ItemStack stack) { return !stack.isEmpty() && stack.getItem() instanceof IManaItem; }
    @Override public long getStoredAmount(ItemStack stack) {
        return canHandle(stack) ? Math.max(0, ((IManaItem) stack.getItem()).getMana(stack)) : 0;
    }
    @Override public long getMaxCapacity(ItemStack stack) {
        return canHandle(stack) ? Math.max(0, ((IManaItem) stack.getItem()).getMaxMana(stack)) : 0;
    }
    @Override public long extract(ItemStack stack, long amount, IActionSource source) {
        if (!canHandle(stack) || amount <= 0) return 0;
        IManaItem item = (IManaItem) stack.getItem();
        if (item.isNoExport(stack) || !item.canExportManaToItem(stack, ItemStack.EMPTY)) return 0;
        int before = Math.max(0, item.getMana(stack));
        int removed = (int) Math.min(amount, before);
        item.addMana(stack, -removed);
        return Math.max(0, Math.min(removed, (long) before - item.getMana(stack)));
    }
    @Override public long inject(ItemStack stack, long amount, IActionSource source) {
        if (!canHandle(stack) || amount <= 0) return 0;
        IManaItem item = (IManaItem) stack.getItem();
        if (!item.canReceiveManaFromItem(stack, ItemStack.EMPTY)) return 0;
        int before = Math.max(0, item.getMana(stack));
        int accepted = (int) Math.min(amount, Math.max(0, (long) item.getMaxMana(stack) - before));
        item.addMana(stack, accepted);
        return Math.max(0, Math.min(accepted, (long) item.getMana(stack) - before));
    }
    @Override public String getContainerDisplayName(ItemStack stack) { return stack.getDisplayName(); }
    @Override public ItemStack getEmptyContainer() { return ItemStack.EMPTY; }
}

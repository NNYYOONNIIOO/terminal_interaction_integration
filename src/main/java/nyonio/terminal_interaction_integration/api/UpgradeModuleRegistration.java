package nyonio.terminal_interaction_integration.api;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

public final class UpgradeModuleRegistration {
    private static final Map<Item, Integer> MODULES = new IdentityHashMap<>();

    private UpgradeModuleRegistration() {
    }

    public static void register(Item item, int maxInstalled) {
        if (item != null && maxInstalled > 0) MODULES.put(item, maxInstalled);
    }

    public static boolean isRegistered(ItemStack stack) {
        return stack != null && !stack.isEmpty() && MODULES.containsKey(stack.getItem());
    }

    public static int getMaxInstalled(Item item) {
        Integer max = MODULES.get(item);
        return max == null ? 0 : max;
    }

    public static int countInstalled(Iterable<ItemStack> inventory, Item item) {
        int count = 0;
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty() && stack.getItem() == item) count += stack.getCount();
        }
        return count;
    }

    public static Map<Item, Integer> getRegisteredModules() {
        return Collections.unmodifiableMap(MODULES);
    }
}

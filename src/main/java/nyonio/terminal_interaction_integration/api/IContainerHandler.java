package nyonio.terminal_interaction_integration.api;

import appeng.api.networking.security.IActionSource;
import net.minecraft.item.ItemStack;

/**
 * Reads and mutates one container stack. The library passes one-item copies
 * when interacting with a stack. Mutations must only affect that stack: a
 * copy may be used to determine the amount a container will accept.
 */
public interface IContainerHandler {
    boolean canHandle(ItemStack container);
    
    long getStoredAmount(ItemStack container);
    
    long getMaxCapacity(ItemStack container);
    
    long extract(ItemStack container, long amount, IActionSource source);
    
    long inject(ItemStack container, long amount, IActionSource source);
    
    String getContainerDisplayName(ItemStack container);
    
    ItemStack getEmptyContainer();

    /**
     * Result of the last mutation on this one-item stack. Override when an
     * operation changes the item itself (for example, filling an empty bucket).
     * The default preserves existing addon handlers.
     */
    default ItemStack getResultContainer(ItemStack container) {
        return container;
    }
}

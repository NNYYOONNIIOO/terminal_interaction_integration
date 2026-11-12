package nyonio.terminal_interaction_integration.api;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import net.minecraft.item.ItemStack;

/**
 * Describes one virtual item and the exact channel resource it represents.
 * Both stack factories must return a fresh stack whose identity does not
 * depend on the requested quantity. Quantities use the provider's units.
 */
public interface IPacketType {
    String getName();
    
    String getDisplayName();
    
    boolean isPacket(ItemStack stack);
    
    long getAmount(ItemStack stack);
    
    IAEItemStack createAEStack(long amount);
    
    ItemStack createItemStack(long amount);
    
    IAEStack<?> createResourceStack(long amount);
}

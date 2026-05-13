package nyonio.terminal_interaction_integration.api;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import net.minecraft.item.ItemStack;

public interface IPacketType {
    String getName();
    
    String getDisplayName();
    
    boolean isPacket(ItemStack stack);
    
    long getAmount(ItemStack stack);
    
    IAEItemStack createAEStack(long amount);
    
    ItemStack createItemStack(long amount);
}

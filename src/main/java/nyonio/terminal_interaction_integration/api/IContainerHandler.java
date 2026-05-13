package nyonio.terminal_interaction_integration.api;

import appeng.api.networking.security.IActionSource;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;

public interface IContainerHandler {
    boolean canHandle(ItemStack container);
    
    long getStoredAmount(ItemStack container);
    
    long getMaxCapacity(ItemStack container);
    
    long extract(ItemStack container, long amount, IActionSource source);
    
    long inject(ItemStack container, long amount, IActionSource source);
    
    String getContainerDisplayName(ItemStack container);
}

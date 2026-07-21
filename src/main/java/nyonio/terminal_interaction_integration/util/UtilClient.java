package nyonio.terminal_interaction_integration.util;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class UtilClient {
    
    public static ItemStack getMouseItem() {
        if (Minecraft.getMinecraft().player == null) {
            return ItemStack.EMPTY;
        }
        return Minecraft.getMinecraft().player.inventory.getItemStack();
    }
}

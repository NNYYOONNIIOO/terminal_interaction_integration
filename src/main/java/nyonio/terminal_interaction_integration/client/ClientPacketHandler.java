package nyonio.terminal_interaction_integration.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import nyonio.terminal_interaction_integration.TerminalInteractionIntegration;
import nyonio.terminal_interaction_integration.network.SPacketResourceUpdate;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

@SideOnly(Side.CLIENT)
public class ClientPacketHandler {

    public static void handleResourceUpdate(SPacketResourceUpdate message) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            EntityPlayer player = Minecraft.getMinecraft().player;
            if (player == null) return;

            ItemStack heldItem = player.inventory.getItemStack();
            if (heldItem != null && !heldItem.isEmpty()) {
                if (message.tagBytes != null && message.tagBytes.length > 0) {
                    try {
                        ByteArrayInputStream bais = new ByteArrayInputStream(message.tagBytes);
                        DataInputStream dis = new DataInputStream(bais);
                        NBTTagCompound tag = net.minecraft.nbt.CompressedStreamTools.read(dis);
                        heldItem.setTagCompound(tag);
                    } catch (Exception e) {
                        TerminalInteractionIntegration.getLogger()
                            .error("[TII] Failed to deserialize NBT tag", e);
                    }
                }
            }

            if (player.openContainer != null) {
                player.openContainer.detectAndSendChanges();
            }

            if (Minecraft.getMinecraft().currentScreen != null) {
                Minecraft.getMinecraft().currentScreen.updateScreen();
            }

            TerminalInteractionIntegration.getLogger()
                .info("[TII] Client received update for {}: new amount = {}",
                    message.providerName, message.newAmount);
        });
    }
}

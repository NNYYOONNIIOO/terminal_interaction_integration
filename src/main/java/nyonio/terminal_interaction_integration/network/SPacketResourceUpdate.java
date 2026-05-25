package nyonio.terminal_interaction_integration.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

public class SPacketResourceUpdate implements IMessage {
    private String providerName;
    private long newAmount;
    private byte[] tagBytes;

    public SPacketResourceUpdate() {}

    public SPacketResourceUpdate(String providerName, long newAmount, byte[] tagBytes) {
        this.providerName = providerName;
        this.newAmount = newAmount;
        this.tagBytes = tagBytes;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int nameLength = buf.readInt();
        byte[] nameBytes = new byte[nameLength];
        buf.readBytes(nameBytes);
        this.providerName = new String(nameBytes, java.nio.charset.StandardCharsets.UTF_8);
        this.newAmount = buf.readLong();
        
        int tagLength = buf.readInt();
        if (tagLength > 0) {
            this.tagBytes = new byte[tagLength];
            buf.readBytes(this.tagBytes);
        } else {
            this.tagBytes = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        byte[] nameBytes = this.providerName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeInt(nameBytes.length);
        buf.writeBytes(nameBytes);
        buf.writeLong(this.newAmount);
        
        if (this.tagBytes != null && this.tagBytes.length > 0) {
            buf.writeInt(this.tagBytes.length);
            buf.writeBytes(this.tagBytes);
        } else {
            buf.writeInt(0);
        }
    }

    public static class Handler implements IMessageHandler<SPacketResourceUpdate, IMessage> {
        @Override
        public IMessage onMessage(SPacketResourceUpdate message, MessageContext ctx) {
            nyonio.terminal_interaction_integration.client.ClientPacketHandler.handleResourceUpdate(message);
            return null;
        }
    }
}

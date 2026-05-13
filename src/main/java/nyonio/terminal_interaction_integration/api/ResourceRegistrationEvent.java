package nyonio.terminal_interaction_integration.api;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import net.minecraftforge.fml.common.eventhandler.Event;

public class ResourceRegistrationEvent extends Event {
    
    public void register(IResourceProvider provider) {
        TerminalInteractionRegistry.registerResourceProvider(provider);
    }
    
    public void register(String name, IStorageChannel<? extends IAEStack<?>> storageChannel,
                         IPacketType packetType, IContainerHandler containerHandler) {
        register(new SimpleResourceProvider(name, storageChannel, packetType, containerHandler));
    }
    
    public void register(String name, IStorageChannel<? extends IAEStack<?>> storageChannel,
                         IPacketType packetType, IContainerHandler containerHandler, int priority) {
        register(new SimpleResourceProvider(name, storageChannel, packetType, containerHandler, priority));
    }
}

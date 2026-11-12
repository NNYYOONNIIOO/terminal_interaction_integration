package nyonio.terminal_interaction_integration.api;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.lifecycle.IModBusEvent;

/** Posted on each mod's lifecycle bus during common setup on both sides. */
public class ResourceRegistrationEvent extends Event implements IModBusEvent {
    
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

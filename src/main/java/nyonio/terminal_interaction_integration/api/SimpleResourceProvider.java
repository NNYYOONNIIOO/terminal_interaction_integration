package nyonio.terminal_interaction_integration.api;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;

public class SimpleResourceProvider implements IResourceProvider {
    
    private final String name;
    private final IStorageChannel<? extends IAEStack<?>> storageChannel;
    private final IPacketType packetType;
    private final IContainerHandler containerHandler;
    private final int priority;
    
    public SimpleResourceProvider(String name, IStorageChannel<? extends IAEStack<?>> storageChannel, 
                                   IPacketType packetType, IContainerHandler containerHandler, int priority) {
        this.name = name;
        this.storageChannel = storageChannel;
        this.packetType = packetType;
        this.containerHandler = containerHandler;
        this.priority = priority;
    }
    
    public SimpleResourceProvider(String name, IStorageChannel<? extends IAEStack<?>> storageChannel, 
                                   IPacketType packetType, IContainerHandler containerHandler) {
        this(name, storageChannel, packetType, containerHandler, 0);
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public IStorageChannel<? extends IAEStack<?>> getStorageChannel() {
        return storageChannel;
    }
    
    @Override
    public IPacketType getPacketType() {
        return packetType;
    }
    
    @Override
    public IContainerHandler getContainerHandler() {
        return containerHandler;
    }
    
    @Override
    public int getPriority() {
        return priority;
    }
    
    public void register() {
        TerminalInteractionRegistry.registerResourceProvider(this);
    }
}

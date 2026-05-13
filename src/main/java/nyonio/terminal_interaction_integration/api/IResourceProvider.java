package nyonio.terminal_interaction_integration.api;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;

public interface IResourceProvider {
    String getName();
    
    IStorageChannel<? extends IAEStack<?>> getStorageChannel();
    
    IPacketType getPacketType();
    
    IContainerHandler getContainerHandler();
    
    int getPriority();
}

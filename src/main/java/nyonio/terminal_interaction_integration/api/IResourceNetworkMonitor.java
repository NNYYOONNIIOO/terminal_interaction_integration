package nyonio.terminal_interaction_integration.api;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.me.cache.GridStorageCache;

public interface IResourceNetworkMonitor<T extends IAEStack<T>> {
    void initResourceMonitor(GridStorageCache gridCache, IStorageChannel<?> resourceChannel);
    
    void resourcePostChange(boolean add, Iterable<T> changes, IActionSource src);
    
    void addToAvailableItems(appeng.api.storage.data.IItemList<IAEItemStack> out);
}

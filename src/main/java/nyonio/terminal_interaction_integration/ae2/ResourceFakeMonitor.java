package nyonio.terminal_interaction_integration.ae2;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.GridStorageCache;
import appeng.me.cache.NetworkMonitor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import nyonio.terminal_interaction_integration.api.TerminalInteractionRegistry;
import nyonio.terminal_interaction_integration.api.IPacketType;
import nyonio.terminal_interaction_integration.api.IResourceProvider;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.Deque;

@SuppressWarnings({"unchecked", "rawtypes"})
public class ResourceFakeMonitor implements IMEMonitor<IAEItemStack> {

    private final NetworkMonitor monitor;
    private final IResourceProvider provider;
    private final IStorageChannel<?> resourceChannel;
    private final GridStorageCache gridCache;

    public ResourceFakeMonitor(final GridStorageCache grid, final IStorageChannel<?> resourceChannel) {
        this.gridCache = grid;
        this.resourceChannel = resourceChannel;
        this.monitor = (NetworkMonitor) grid.getInventory(resourceChannel);
        this.provider = TerminalInteractionRegistry.getProviderByChannel(resourceChannel);
    }

    @Override
    public IAEItemStack injectItems(final IAEItemStack stack, final Actionable actionable, final IActionSource source) {
        if (stack == null) return null;
        if (provider == null) return stack;

        IPacketType packetType = provider.getPacketType();
        if (packetType == null) return stack;

        ItemStack itemRep = stack.asItemStackRepresentation();
        if (!packetType.isPacket(itemRep)) {
            return stack;
        }

        long amount = stack.getStackSize();
        if (amount <= 0) {
            return null;
        }

        if (monitor == null) {
            return stack;
        }

        IAEStack toInject = packetType.createResourceStack(amount);
        if (toInject == null) {
            return stack;
        }
        
        FakeMonitorSource fakeSource = FakeMonitorSource.release(source);
        IAEStack result = (IAEStack) monitor.injectItems(toInject, actionable, fakeSource);
        fakeSource.recycle();

        if (result == null || result.getStackSize() <= 0) {
            return null;
        } else {
            return packetType.createAEStack(result.getStackSize());
        }
    }

    @Override
    public IAEItemStack extractItems(final IAEItemStack stack, final Actionable actionable, final IActionSource source) {
        if (stack == null) return null;
        if (provider == null) return null;

        IPacketType packetType = provider.getPacketType();
        if (packetType == null) return null;

        ItemStack itemRep = stack.asItemStackRepresentation();
        if (!packetType.isPacket(itemRep)) {
            return null;
        }

        long requestedAmount = stack.getStackSize();
        if (requestedAmount <= 0) {
            return null;
        }

        if (monitor == null) {
            return null;
        }

        IAEStack toExtract = packetType.createResourceStack(requestedAmount);
        if (toExtract == null) {
            return null;
        }
        
        FakeMonitorSource fakeSource = FakeMonitorSource.release(source);
        IAEStack result = (IAEStack) monitor.extractItems(toExtract, actionable, fakeSource);
        fakeSource.recycle();

        if (result == null || result.getStackSize() <= 0) {
            return null;
        } else {
            return packetType.createAEStack(result.getStackSize());
        }
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> list) {
        if (list == null) return list;
        if (provider == null) return list;
        
        IPacketType packetType = provider.getPacketType();
        if (packetType == null) return list;
        
        try {
            IAEItemStack dropStack = packetType.createAEStack(1);
            if (dropStack != null) {
                list.findFuzzy(dropStack, appeng.api.config.FuzzyMode.IGNORE_ALL)
                    .forEach(i -> i.setStackSize(0));
            }
            
            IItemList storageList = monitor.getStorageList();
            
            if (storageList != null && !storageList.isEmpty()) {
                for (Object obj : storageList) {
                    if (obj instanceof IAEStack) {
                        IAEStack rs = (IAEStack) obj;
                        long amount = rs.getStackSize();
                        if (amount > 0) {
                            IAEItemStack display = packetType.createAEStack(amount);
                            if (display != null) {
                                display.setStackSize(amount);
                                list.addStorage(display);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            nyonio.terminal_interaction_integration.TerminalInteractionIntegration.getLogger().error(
                "[TII] Error in getAvailableItems for: " + provider.getName(), e
            );
        }

        return list;
    }

    @Override
    public IStorageChannel<IAEItemStack> getChannel() {
        return null;
    }

    @Override
    public IItemList<IAEItemStack> getStorageList() {
        return null;
    }

    @Override
    public void addListener(IMEMonitorHandlerReceiver<IAEItemStack> receiver, Object verificationToken) {
    }

    @Override
    public void removeListener(IMEMonitorHandlerReceiver<IAEItemStack> receiver) {
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(IAEItemStack stack) {
        if (stack == null) return false;
        if (provider == null) return false;
        
        IPacketType packetType = provider.getPacketType();
        if (packetType == null) return false;
        
        ItemStack itemRep = stack.asItemStackRepresentation();
        return packetType.isPacket(itemRep);
    }

    @Override
    public boolean canAccept(IAEItemStack stack) {
        if (stack == null) return false;
        if (provider == null) return false;
        
        IPacketType packetType = provider.getPacketType();
        if (packetType == null) return false;
        
        ItemStack itemRep = stack.asItemStackRepresentation();
        return packetType.isPacket(itemRep);
    }

    @Override
    public int getPriority() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int getSlot() {
        return monitor != null ? monitor.getSlot() : 0;
    }

    @Override
    public boolean validForPass(int i) {
        return i == 2;
    }

    public static class FakeMonitorSource implements IActionSource {

        private static final Deque<FakeMonitorSource> POOL = new ArrayDeque<>(100);
        private IActionSource source;

        public static FakeMonitorSource release(IActionSource source) {
            synchronized (POOL) {
                FakeMonitorSource s = POOL.poll();
                if (s != null) {
                    s.source = source;
                    return s;
                }
            }
            return new FakeMonitorSource(source);
        }

        private FakeMonitorSource(IActionSource source) {
            this.source = source;
        }

        public IActionSource getSource() {
            return source;
        }

        public void recycle() {
            synchronized (POOL) {
                if (POOL.size() < 100) POOL.add(this);
            }
        }

        @Nonnull
        @Override
        public final java.util.Optional<EntityPlayer> player() {
            return source.player();
        }

        @Nonnull
        @Override
        public final java.util.Optional<IActionHost> machine() {
            return source.machine();
        }

        @Nonnull
        @Override
        public final <T> java.util.Optional<T> context(@Nonnull Class<T> aClass) {
            return source.context(aClass);
        }
    }
}

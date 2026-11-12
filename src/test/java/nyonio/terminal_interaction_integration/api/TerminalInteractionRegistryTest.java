package nyonio.terminal_interaction_integration.api;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import net.minecraft.item.ItemStack;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public class TerminalInteractionRegistryTest {
    @After
    public void cleanup() {
        TerminalInteractionRegistry.clear();
    }

    @Test
    public void multipleProvidersCanUseTheSameResourceChannel() {
        IStorageChannel channel = mock(IStorageChannel.class);
        IResourceProvider first = provider("addon:first", channel, 1);
        IResourceProvider second = provider("addon:second", channel, 2);
        TerminalInteractionRegistry.registerResourceProvider(first);
        TerminalInteractionRegistry.registerResourceProvider(second);

        assertEquals(2, TerminalInteractionRegistry.getAllProviders().size());
        assertSame(second, TerminalInteractionRegistry.getProviderByChannel(channel));
        assertSame(first, TerminalInteractionRegistry.getProvider("addon:first"));
        assertSame(second, TerminalInteractionRegistry.getProvider("addon:second"));
    }

    @Test
    public void higherPriorityProviderRecognizesTheVirtualItem() {
        IStorageChannel channel = mock(IStorageChannel.class);
        IResourceProvider low = provider("addon:low", channel, 0);
        IResourceProvider high = provider("addon:high", channel, 10);
        ItemStack item = mock(ItemStack.class);
        when(low.getPacketType().isPacket(item)).thenReturn(true);
        when(high.getPacketType().isPacket(item)).thenReturn(true);
        TerminalInteractionRegistry.registerResourceProvider(low);
        TerminalInteractionRegistry.registerResourceProvider(high);

        assertSame(high, TerminalInteractionRegistry.getProviderForPacket(item));
    }

    @Test(expected = IllegalArgumentException.class)
    public void itemChannelCannotBeUsedForTheVirtualResource() {
        TerminalInteractionRegistry.registerResourceProvider(
                provider("addon:invalid", mock(IItemStorageChannel.class), 0));
    }

    private static IResourceProvider provider(String name, IStorageChannel channel, int priority) {
        IResourceProvider result = mock(IResourceProvider.class);
        when(result.getName()).thenReturn(name);
        when(result.getStorageChannel()).thenReturn(channel);
        when(result.getPacketType()).thenReturn(mock(IPacketType.class));
        when(result.getPriority()).thenReturn(priority);
        return result;
    }
}

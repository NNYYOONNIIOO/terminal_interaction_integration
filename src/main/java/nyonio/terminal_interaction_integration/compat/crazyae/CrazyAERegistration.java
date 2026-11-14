package nyonio.terminal_interaction_integration.compat.crazyae;

import appeng.api.AEApi;
import appeng.api.definitions.IItemDefinition;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import dev.beecube31.crazyae2.core.CrazyAE;
import dev.beecube31.crazyae2.core.api.storage.IManaStorageChannel;
import dev.beecube31.crazyae2.core.api.storage.energy.IEnergyStorageChannel;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import nyonio.terminal_interaction_integration.TerminalInteractionIntegration;
import nyonio.terminal_interaction_integration.ae2.ResourceTransfers.Source;
import nyonio.terminal_interaction_integration.api.IContainerHandler;
import nyonio.terminal_interaction_integration.api.IPacketType;
import nyonio.terminal_interaction_integration.api.IResourceProvider;
import nyonio.terminal_interaction_integration.api.SimpleResourceProvider;
import nyonio.terminal_interaction_integration.api.TerminalInteractionRegistry;

/** Loaded only when CrazyAE is installed, after all addons have registered their providers. */
public final class CrazyAERegistration {
    private CrazyAERegistration() { }

    public static void register() {
        register("crazyae_fe", "flux_applied", "com.flux_applied.ae2.FluxStorageChannel",
                findChannel(IEnergyStorageChannel.class),
                icon(CrazyAE.definitions().items().FEEnergyAsAeStack()),
                new ForgeEnergyContainerHandler());
        if (Loader.isModLoaded("botania")) {
            ItemStack mana = icon(CrazyAE.definitions().items().manaAsAEStack());
            if (!mana.isEmpty()) {
                register("crazyae_mana", "botania_applie", "nyonio.ae2.ManaStorageChannel",
                        findChannel(IManaStorageChannel.class),
                        mana, new CrazyManaContainerHandler());
            }
        }
    }

    private static ItemStack icon(IItemDefinition definition) {
        return definition == null ? ItemStack.EMPTY : definition.maybeStack(1).orElse(ItemStack.EMPTY);
    }

    private static <C extends IStorageChannel<IAEItemStack>> C findChannel(Class<C> type) {
        for (IStorageChannel<?> channel : AEApi.instance().storage().storageChannels()) {
            if (type.isInstance(channel)) return type.cast(channel);
        }
        return null; // CrazyAE allows individual storage features to be disabled.
    }

    private static void register(String name, String addon, String addonChannel,
                                  IStorageChannel<IAEItemStack> channel, ItemStack icon, IContainerHandler handler) {
        if (channel == null || icon.isEmpty()) return;
        IResourceProvider provider = null;
        if (Loader.isModLoaded(addon)) {
            for (IResourceProvider candidate : TerminalInteractionRegistry.getAllProviders()) {
                if (candidate.getStorageChannel() != null
                        && candidate.getStorageChannel().getClass().getName().equals(addonChannel)) {
                    provider = candidate;
                    break;
                }
            }
        }
        boolean mapped = provider != null;
        if (!mapped) {
            if (Loader.isModLoaded(addon)) {
                TerminalInteractionIntegration.getLogger().warn(
                        "[TII] {} has no registered terminal provider; using the CrazyAE icon for {}", addon, name);
            }
            provider = new SimpleResourceProvider(name, channel, new Packet(name, icon), handler, -100);
            TerminalInteractionRegistry.registerResourceProvider(provider);
        }
        CrazyAEIntegration.register(provider, new Source(channel, AEItemStack.fromItemStack(icon)), mapped);
        TerminalInteractionIntegration.getLogger().info("[TII] CrazyAE {} terminal display: {}", name, provider.getName());
    }

    private static final class Packet implements IPacketType {
        private final String name;
        private final ItemStack icon;

        private Packet(String name, ItemStack icon) {
            this.name = name;
            this.icon = icon.copy();
            this.icon.setCount(1);
        }
        @Override public String getName() { return name; }
        @Override public String getDisplayName() { return icon.getDisplayName(); }
        @Override public boolean isPacket(ItemStack stack) {
            return !stack.isEmpty() && ItemStack.areItemsEqual(icon, stack)
                    && ItemStack.areItemStackTagsEqual(icon, stack);
        }
        @Override public long getAmount(ItemStack stack) { return isPacket(stack) ? stack.getCount() : 0; }
        @Override public ItemStack createItemStack(long amount) { return icon.copy(); }
        @Override public IAEItemStack createAEStack(long amount) {
            IAEItemStack stack = AEItemStack.fromItemStack(icon);
            return stack == null ? null : stack.setStackSize(amount);
        }
        @Override public IAEStack<?> createResourceStack(long amount) { return createAEStack(amount); }
    }
}

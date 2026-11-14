package nyonio.terminal_interaction_integration.compat.crazyae;

import appeng.api.storage.data.IAEStack;
import net.minecraft.item.ItemStack;
import nyonio.terminal_interaction_integration.ae2.ResourceTransfers.Source;
import nyonio.terminal_interaction_integration.api.IResourceProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable after post-init. Holds resource definitions only, never grids or monitors. */
public final class CrazyAEIntegration {
    private static final Map<String, Mapping> BY_PROVIDER = new HashMap<>();
    private static final List<Mapping> MAPPINGS = new ArrayList<>(2);

    private CrazyAEIntegration() { }

    static void register(IResourceProvider provider, Source legacy, boolean mappedToAddon) {
        IAEStack<?> primaryKey = provider.getPacketType().createResourceStack(1);
        if (mappedToAddon && (primaryKey == null || provider.getStorageChannel() == null)) {
            throw new IllegalArgumentException("Mapped resource provider has no resource key or channel: " + provider.getName());
        }
        Source[] sources = mappedToAddon
                ? new Source[] { new Source(provider.getStorageChannel(), primaryKey), legacy }
                : new Source[] { legacy };
        Mapping mapping = new Mapping(provider, sources, legacy);
        BY_PROVIDER.put(provider.getName(), mapping);
        MAPPINGS.add(mapping);
    }

    public static boolean manages(IResourceProvider provider) {
        return provider != null && BY_PROVIDER.containsKey(provider.getName());
    }

    public static Source[] sources(IResourceProvider provider) {
        Mapping mapping = provider == null ? null : BY_PROVIDER.get(provider.getName());
        return mapping == null ? null : mapping.sources;
    }

    public static Mapping[] mappings() { return MAPPINGS.toArray(new Mapping[0]); }

    public static boolean isVirtualPacket(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (Mapping mapping : MAPPINGS) {
            if (mapping.provider.getPacketType().isPacket(stack)) return true;
            ItemStack legacy = mapping.legacyIcon;
            if (ItemStack.areItemsEqual(stack, legacy) && ItemStack.areItemStackTagsEqual(stack, legacy)) return true;
        }
        return false;
    }

    public static final class Mapping {
        public final IResourceProvider provider;
        public final Source[] sources;
        public final Source legacy;
        private final ItemStack legacyIcon;

        Mapping(IResourceProvider provider, Source[] sources, Source legacy) {
            this.provider = provider;
            this.sources = sources;
            this.legacy = legacy;
            this.legacyIcon = legacy.key.asItemStackRepresentation();
        }
    }
}

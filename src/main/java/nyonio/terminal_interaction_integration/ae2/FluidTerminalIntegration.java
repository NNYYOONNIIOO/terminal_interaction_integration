package nyonio.terminal_interaction_integration.ae2;

import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.core.Api;
import appeng.fluids.items.FluidDummyItem;
import appeng.fluids.util.AEFluidStack;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import nyonio.terminal_interaction_integration.TIIConfig;
import nyonio.terminal_interaction_integration.api.IPacketType;
import nyonio.terminal_interaction_integration.api.IResourceProvider;
import nyonio.terminal_interaction_integration.api.SimpleResourceProvider;
import nyonio.terminal_interaction_integration.api.TerminalInteractionRegistry;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/** Dynamic, exact-fluid providers; no global fluid or grid cache is needed. */
public final class FluidTerminalIntegration {
    private FluidTerminalIntegration() {
    }

    public static IFluidStorageChannel channel() {
        return Api.instance().storage().getStorageChannel(IFluidStorageChannel.class);
    }

    /** Recognize even stale markers while the feature is disabled. */
    public static boolean isFluidPacket(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof FluidDummyItem;
    }

    public static boolean isBuiltin(IResourceProvider provider) {
        return provider != null && provider.getPacketType() instanceof FluidPacketType;
    }

    @Nullable
    public static IResourceProvider forPacket(ItemStack stack) {
        if (!isFluidPacket(stack)) return null;
        return forFluid(((FluidDummyItem) stack.getItem()).getFluidStack(stack));
    }

    @Nullable
    public static IResourceProvider forContainer(ItemStack stack) {
        if (!TIIConfig.isFluidTerminalIntegrationEnabled() || stack == null || stack.isEmpty()) return null;
        ItemStack one = stack.copy();
        one.setCount(1);
        IFluidHandlerItem handler = FluidUtil.getFluidHandler(one).orElse(null);
        return handler == null ? null : forFluid(handler.drain(Integer.MAX_VALUE, FluidAction.SIMULATE));
    }

    @Nullable
    public static IResourceProvider forFluid(FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) return null;
        FluidPacketType packet = new FluidPacketType(fluid);
        return new SimpleResourceProvider(packet.getName(), channel(), packet,
                new FluidContainerHandler(fluid), Integer.MIN_VALUE);
    }

    /** Avoid a second built-in entry for fluids already represented by an addon. */
    public static Set<IAEFluidStack> addonFluids() {
        Set<IAEFluidStack> result = new HashSet<>();
        for (IResourceProvider provider : TerminalInteractionRegistry.getAllProviders()) {
            if (provider.getStorageChannel() != channel()) continue;
            IAEStack<?> key = provider.getPacketType().createResourceStack(1);
            if (key instanceof IAEFluidStack) result.add((IAEFluidStack) key);
        }
        return result;
    }

    public static IAEItemStack display(IAEFluidStack fluid, long amount) {
        IAEFluidStack identity = fluid.copy();
        identity.setStackSize(1);
        IAEItemStack display = Api.instance().storage().getStorageChannel(IItemStorageChannel.class)
                .createStack(identity.asItemStackRepresentation());
        if (display != null) display.setStackSize(Math.max(0, amount));
        return display;
    }

    private static final class FluidPacketType implements IPacketType {
        private final FluidStack fluid;

        private FluidPacketType(FluidStack fluid) {
            this.fluid = fluid.copy();
            this.fluid.setAmount(1);
        }

        @Override public String getName() { return "terminal_interaction_integration:fluid/" + fluid.getFluid().getRegistryName(); }
        @Override public String getDisplayName() { return fluid.getDisplayName().getString(); }
        @Override public boolean isPacket(ItemStack stack) {
            return isFluidPacket(stack) && fluid.isFluidEqual(((FluidDummyItem) stack.getItem()).getFluidStack(stack));
        }
        @Override public long getAmount(ItemStack stack) {
            return isPacket(stack) ? ((FluidDummyItem) stack.getItem()).getFluidStack(stack).getAmount() : 0;
        }
        @Override public IAEItemStack createAEStack(long amount) {
            return display(AEFluidStack.fromFluidStack(fluid), amount);
        }
        @Override public ItemStack createItemStack(long amount) {
            // Quantity belongs to the AE stack, never to the marker's identity NBT.
            return AEFluidStack.fromFluidStack(fluid).asItemStackRepresentation();
        }
        @Override public IAEStack<?> createResourceStack(long amount) {
            IAEFluidStack stack = AEFluidStack.fromFluidStack(fluid);
            stack.setStackSize(amount);
            return stack;
        }
    }
}

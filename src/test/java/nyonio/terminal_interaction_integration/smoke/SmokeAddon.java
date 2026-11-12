package nyonio.terminal_interaction_integration.smoke;

import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.core.Api;
import appeng.fluids.util.AEFluidStack;
import appeng.util.item.AEItemStack;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import nyonio.terminal_interaction_integration.TerminalInteractionIntegration;
import nyonio.terminal_interaction_integration.api.IPacketType;
import nyonio.terminal_interaction_integration.api.ResourceRegistrationEvent;
import nyonio.terminal_interaction_integration.api.TerminalInteractionRegistry;

import java.util.Arrays;

/** Dev-only example addon; never packaged in the library jar. */
@Mod("tii_smoketest")
public final class SmokeAddon {
    public SmokeAddon() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::registerResources);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::verifyAtStartup);
    }

    private void registerResources(ResourceRegistrationEvent event) {
        TerminalInteractionIntegration.LOGGER.info("TII_SMOKE_REGISTER_EVENT");
        IFluidStorageChannel channel = Api.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        event.register("tii_smoketest:water", channel, new TestPacket("water", Items.PAPER, Fluids.WATER), null);
        event.register("tii_smoketest:lava", channel, new TestPacket("lava", Items.CLAY_BALL, Fluids.LAVA), null);
    }

    private void verifyAtStartup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            TerminalInteractionIntegration.LOGGER.info("TII_SMOKE_VERIFY_START");
            if (TerminalInteractionRegistry.getProvider("tii_smoketest:water") == null
                    || TerminalInteractionRegistry.getProvider("tii_smoketest:lava") == null) {
                throw new AssertionError("Resource registration event was not delivered");
            }
            try {
                Class<?> monitor = Class.forName("appeng.me.cache.NetworkMonitor");
                Class<?> inventory = Class.forName("appeng.me.storage.NetworkInventoryHandler");
                monitor.getDeclaredField("tii$views");
                if (Arrays.stream(inventory.getDeclaredMethods())
                        .noneMatch(method -> method.getName().contains("tii$insert"))) {
                    throw new AssertionError("AE2 inventory mixin was not applied");
                }
                TerminalInteractionIntegration.LOGGER.info("TII_SMOKE_TEST_PASSED: providers and AE2 mixins loaded");
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("AE2 storage mixins were not applied", e);
            }
        }).whenComplete((ignored, failure) -> {
            if (failure != null) {
                TerminalInteractionIntegration.LOGGER.error("TII_SMOKE_TEST_FAILED", failure);
            }
        });
    }

    private static final class TestPacket implements IPacketType {
        private final String name;
        private final Item item;
        private final Fluid fluid;

        private TestPacket(String name, Item item, Fluid fluid) {
            this.name = name;
            this.item = item;
            this.fluid = fluid;
        }

        @Override public String getName() { return "tii_smoketest:" + name; }
        @Override public String getDisplayName() { return name; }
        @Override public boolean isPacket(ItemStack stack) { return !stack.isEmpty() && stack.getItem() == item; }
        @Override public long getAmount(ItemStack stack) { return stack.getCount(); }
        @Override public ItemStack createItemStack(long amount) { return new ItemStack(item); }
        @Override public IAEItemStack createAEStack(long amount) {
            IAEItemStack stack = AEItemStack.fromItemStack(createItemStack(amount));
            return stack == null ? null : stack.setStackSize(amount);
        }
        @Override public IAEStack<?> createResourceStack(long amount) {
            IAEStack<?> stack = AEFluidStack.fromFluidStack(new FluidStack(fluid, 1));
            return stack == null ? null : stack.setStackSize(amount);
        }
    }
}

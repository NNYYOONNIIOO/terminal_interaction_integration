package nyonio.terminal_interaction_integration;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import nyonio.terminal_interaction_integration.api.ResourceRegistrationEvent;
import nyonio.terminal_interaction_integration.api.TerminalInteractionRegistry;
import nyonio.terminal_interaction_integration.network.TerminalInteractionPacket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

@Mod(TerminalInteractionIntegration.MODID)
public final class TerminalInteractionIntegration {
    public static final String MODID = "terminal_interaction_integration";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    private static final String PROTOCOL = "1";
    public static final SimpleChannel NETWORK = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public TerminalInteractionIntegration() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TIIConfig.SPEC);
        NETWORK.registerMessage(0, TerminalInteractionPacket.class,
                TerminalInteractionPacket::encode, TerminalInteractionPacket::decode,
                TerminalInteractionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModLoader.get().postEvent(new ResourceRegistrationEvent());
            TerminalInteractionRegistry.setInitialized(true);
        }).whenComplete((ignored, failure) -> {
            if (failure != null) {
                LOGGER.error("Failed to register terminal resource providers", failure);
            }
        });
    }

    public static Logger getLogger() { return LOGGER; }
    public static SimpleChannel getNetwork() { return NETWORK; }
}

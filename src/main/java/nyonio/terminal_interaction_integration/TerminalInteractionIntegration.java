package nyonio.terminal_interaction_integration;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.Logger;
import nyonio.terminal_interaction_integration.api.ResourceRegistrationEvent;
import nyonio.terminal_interaction_integration.api.TerminalInteractionRegistry;
import nyonio.terminal_interaction_integration.network.CPacketResourceAction;
import nyonio.terminal_interaction_integration.network.SPacketResourceUpdate;

@Mod(modid = TerminalInteractionIntegration.MODID, name = TerminalInteractionIntegration.NAME, version = TerminalInteractionIntegration.VERSION, dependencies = "required-after:appliedenergistics2")
public class TerminalInteractionIntegration
{
    public static final String MODID = "terminal_interaction_integration";
    public static final String NAME = "Terminal Interaction Integration";
    public static final String VERSION = "1.0";

    @Mod.Instance(MODID)
    public static TerminalInteractionIntegration instance;

    private static Logger logger;
    private static SimpleNetworkWrapper network;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        logger = event.getModLog();
        
        network = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        network.registerMessage(CPacketResourceAction.Handler.class, CPacketResourceAction.class, 0, Side.SERVER);
        network.registerMessage(SPacketResourceUpdate.Handler.class, SPacketResourceUpdate.class, 1, Side.CLIENT);
        
        logger.info("[TII] Pre-initialization complete");
    }

    @EventHandler
    public void init(FMLInitializationEvent event)
    {
        ResourceRegistrationEvent registrationEvent = new ResourceRegistrationEvent();
        MinecraftForge.EVENT_BUS.post(registrationEvent);
        logger.info("[TII] Resource registration event posted");
        
        if (event.getSide().isClient()) {
            initClient();
        }
        
        logger.info("[TII] {} initialized with {} resource providers", 
            NAME, TerminalInteractionRegistry.getAllProviders().size());
    }
    
    private void initClient() {
        try {
            Class<?> handlerClass = Class.forName("nyonio.terminal_interaction_integration.client.TerminalInteractionHandler");
            java.lang.reflect.Method initMethod = handlerClass.getMethod("init");
            initMethod.invoke(null);
            logger.info("[TII] TerminalInteractionHandler initialized");
        } catch (Exception e) {
            logger.error("[TII] Failed to initialize TerminalInteractionHandler", e);
        }
    }
    
    public static Logger getLogger() {
        return logger;
    }
    
    public static SimpleNetworkWrapper getNetwork() {
        return network;
    }
}

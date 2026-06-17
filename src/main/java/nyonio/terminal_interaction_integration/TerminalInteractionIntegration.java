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
import nyonio.terminal_interaction_integration.network.CPacketMEMonitorableAction;
import nyonio.terminal_interaction_integration.network.CPacketResourceAction;
import nyonio.terminal_interaction_integration.network.SPacketResourceUpdate;

@Mod(modid = TerminalInteractionIntegration.MODID, name = TerminalInteractionIntegration.NAME, version = TerminalInteractionIntegration.VERSION, dependencies = "required-after:appliedenergistics2")
public class TerminalInteractionIntegration
{
    public static final String MODID = "terminal_interaction_integration";
    public static final String NAME = "Terminal Interaction Integration";
    public static final String VERSION = "1.0";
    public static final String CHANNEL = "tii";

    @Mod.Instance(MODID)
    public static TerminalInteractionIntegration instance;

    private static Logger logger;
    private static SimpleNetworkWrapper network;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        logger = event.getModLog();
        
        network = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
        network.registerMessage(CPacketResourceAction.Handler.class, CPacketResourceAction.class, 0, Side.SERVER);
        network.registerMessage(SPacketResourceUpdate.Handler.class, SPacketResourceUpdate.class, 1, Side.CLIENT);
        network.registerMessage(CPacketMEMonitorableAction.Handler.class, CPacketMEMonitorableAction.class, 2, Side.SERVER);
    }

    @EventHandler
    public void init(FMLInitializationEvent event)
    {
        ResourceRegistrationEvent registrationEvent = new ResourceRegistrationEvent();
        MinecraftForge.EVENT_BUS.post(registrationEvent);
        
        if (event.getSide().isClient()) {
            initClient();
        }
    }
    
    private void initClient() {
        nyonio.terminal_interaction_integration.client.ClientInit.init();
    }
    
    public static Logger getLogger() {
        return logger;
    }
    
    public static SimpleNetworkWrapper getNetwork() {
        return network;
    }
}

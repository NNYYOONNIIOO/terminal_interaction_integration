package nyonio.terminal_interaction_integration.client;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientInit {

    public static void init() {
        TerminalInteractionHandler.init();
    }
}

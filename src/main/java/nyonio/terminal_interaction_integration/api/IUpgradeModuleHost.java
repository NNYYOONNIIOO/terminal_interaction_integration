package nyonio.terminal_interaction_integration.api;

import net.minecraft.item.Item;

public interface IUpgradeModuleHost {
    int terminalInteractionIntegration$getInstalled(Item item);
    boolean terminalInteractionIntegration$isInterfaceDevice();
}

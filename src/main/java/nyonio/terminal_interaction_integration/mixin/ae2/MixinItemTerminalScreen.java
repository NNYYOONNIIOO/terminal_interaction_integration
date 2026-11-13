package nyonio.terminal_interaction_integration.mixin.ae2;

import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.me.items.ItemTerminalScreen;
import appeng.container.me.common.GridInventoryEntry;
import appeng.container.me.common.MEMonitorableContainer;
import appeng.helpers.InventoryAction;
import net.minecraft.client.Minecraft;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.item.ItemStack;
import nyonio.terminal_interaction_integration.TerminalInteractionIntegration;
import nyonio.terminal_interaction_integration.TIIConfig;
import nyonio.terminal_interaction_integration.ae2.FluidTerminalIntegration;
import nyonio.terminal_interaction_integration.api.IContainerHandler;
import nyonio.terminal_interaction_integration.api.IResourceProvider;
import nyonio.terminal_interaction_integration.api.TerminalInteractionRegistry;
import nyonio.terminal_interaction_integration.network.TerminalInteractionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemTerminalScreen.class, remap = false)
public abstract class MixinItemTerminalScreen {
    @Inject(method = "handleGridInventoryEntryMouseClick", at = @At("HEAD"), cancellable = true)
    private void tii$interact(GridInventoryEntry<IAEItemStack> entry, int button,
                              ClickType clickType, CallbackInfo ci) {
        if (button != 1
                || (clickType != ClickType.PICKUP && clickType != ClickType.QUICK_MOVE)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        IResourceProvider provider = entry == null || entry.getStack() == null ? null
                : TerminalInteractionRegistry.getProviderForPacket(entry.getStack().asItemStackRepresentation());
        if (FluidTerminalIntegration.isBuiltin(provider) && !TIIConfig.isFluidTerminalIntegrationEnabled()) {
            ci.cancel();
            return;
        }
        ItemStack held = minecraft.player.inventory.getItemStack();
        IContainerHandler heldHandler = TerminalInteractionRegistry.getContainerHandler(held);
        if (provider == null) {
            if (heldHandler == null) return;
            if (heldHandler.getStoredAmount(held) <= 0) {
                if (minecraft.player.openContainer instanceof MEMonitorableContainer) {
                    ((MEMonitorableContainer<?>) minecraft.player.openContainer).handleInteraction(
                            entry == null ? -1 : entry.getSerial(), InventoryAction.PICKUP_OR_SET_DOWN);
                    ci.cancel();
                }
                return;
            }
        } else if (held.isEmpty()) {
            if (FluidTerminalIntegration.isBuiltin(provider)) {
                ci.cancel();
                return;
            }
            if (provider.getContainerHandler() == null) return;
        } else {
            IContainerHandler handler = provider.getContainerHandler();
            if (handler == null || !handler.canHandle(held)) {
                ci.cancel();
                return;
            }
        }
        TerminalInteractionIntegration.NETWORK.sendToServer(new TerminalInteractionPacket(
                minecraft.player.openContainer.windowId,
                provider == null ? -1 : entry.getSerial(), clickType == ClickType.QUICK_MOVE));
        ci.cancel();
    }
}

package nyonio.terminal_interaction_integration.mixin.ae2;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.items.ItemTerminalScreen;
import appeng.client.gui.me.items.RepoSlot;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import nyonio.terminal_interaction_integration.TIIConfig;
import nyonio.terminal_interaction_integration.ae2.FluidTerminalIntegration;
import nyonio.terminal_interaction_integration.api.IContainerHandler;
import nyonio.terminal_interaction_integration.api.IResourceProvider;
import nyonio.terminal_interaction_integration.api.TerminalInteractionRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Mixin(value = AEBaseScreen.class, remap = false)
public abstract class MixinTerminalTooltip<C extends Container> extends ContainerScreen<C> {
    protected MixinTerminalTooltip(C container, PlayerInventory inventory, ITextComponent title) {
        super(container, inventory, title);
    }

    @Inject(method = "renderTooltips", at = @At("RETURN"))
    private void tii$renderHints(MatrixStack matrices, int mouseX, int mouseY, CallbackInfo ci) {
        if (!((Object) this instanceof ItemTerminalScreen) || minecraft == null || minecraft.player == null) return;
        Slot slot = getSlotUnderMouse();
        if (!(slot instanceof RepoSlot)) return;
        ItemStack held = minecraft.player.inventory.getItemStack();
        IResourceProvider selected = TerminalInteractionRegistry.getProviderForPacket(slot.getStack());
        boolean builtinFluid = FluidTerminalIntegration.isBuiltin(selected);
        if (builtinFluid && !TIIConfig.isFluidTerminalIntegrationEnabled()) return;
        IContainerHandler handler = selected == null ? TerminalInteractionRegistry.getContainerHandler(held)
                : selected.getContainerHandler();
        List<ITextComponent> lines = new ArrayList<>();
        if (builtinFluid) {
            lines.add(new StringTextComponent(selected.getPacketType().getDisplayName()));
            lines.add(new TranslationTextComponent("terminal_interaction_integration.fluid.amount",
                    String.format(Locale.ROOT, "%,d", ((RepoSlot<?>) slot).getStoredAmount())));
        }
        if (handler == null || held.isEmpty() || !handler.canHandle(held)) {
            if (!lines.isEmpty()) renderWrappedToolTip(matrices, lines, mouseX, mouseY, font);
            return;
        }
        if (selected == null && handler.getStoredAmount(held) <= 0) return;
        IResourceProvider provider = selected == null
                ? TerminalInteractionRegistry.getProviderForContainer(held) : selected;
        if (provider == null) return;

        lines.add(new StringTextComponent(new TranslationTextComponent("key.mouse.left").getString() + " : "
                + new TranslationTextComponent("terminal_interaction_integration.action.deposit").getString()
                + " " + held.getDisplayName().getString()));
        String action = handler.getStoredAmount(held) > 0 ? "fill" : "extract";
        lines.add(new StringTextComponent(new TranslationTextComponent("key.mouse.right").getString() + " : "
                + new TranslationTextComponent("terminal_interaction_integration.action." + action).getString()
                + " " + provider.getPacketType().getDisplayName()));
        renderWrappedToolTip(matrices, lines, mouseX, mouseY, font);
    }
}

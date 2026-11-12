package nyonio.terminal_interaction_integration.mixin.ae2;

import appeng.container.AEBaseContainer;
import net.minecraft.entity.player.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = AEBaseContainer.class, remap = false)
public interface BaseContainerAccess {
    @Invoker("updateHeld")
    void tii$updateHeld(ServerPlayerEntity player);
}

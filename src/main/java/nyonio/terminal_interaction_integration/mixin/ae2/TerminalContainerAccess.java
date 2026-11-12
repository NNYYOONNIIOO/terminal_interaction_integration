package nyonio.terminal_interaction_integration.mixin.ae2;

import appeng.api.storage.data.IAEStack;
import appeng.container.me.common.MEMonitorableContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = MEMonitorableContainer.class, remap = false)
public interface TerminalContainerAccess {
    @Invoker("getStackBySerial")
    IAEStack<?> tii$getStackBySerial(long serial);
}

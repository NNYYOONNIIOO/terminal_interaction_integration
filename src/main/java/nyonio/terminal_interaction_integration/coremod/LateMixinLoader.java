package nyonio.terminal_interaction_integration.coremod;

import zone.rong.mixinbooter.ILateMixinLoader;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("unused")
public class LateMixinLoader implements ILateMixinLoader {
    
    @Override
    @Nonnull
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.terminal_interaction_integration_late.json");
    }
}

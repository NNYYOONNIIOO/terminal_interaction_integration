package nyonio.terminal_interaction_integration;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

/** Configuration shared by the library and resource-provider addons. */
public final class TIIConfig {
    private static final String CATEGORY_TERMINAL = "terminal";
    private static final int DEFAULT_VIRTUAL_RESOURCE_UPDATE_INTERVAL_TICKS = 5;

    private static int virtualResourceUpdateIntervalTicks =
        DEFAULT_VIRTUAL_RESOURCE_UPDATE_INTERVAL_TICKS;

    private TIIConfig() {
    }

    public static void load(final File file) {
        final Configuration configuration = new Configuration(file);
        configuration.addCustomCategoryComment(
            CATEGORY_TERMINAL,
            "Settings for virtual resources displayed in ME terminals."
        );
        virtualResourceUpdateIntervalTicks = configuration.getInt(
            "virtualResourceUpdateIntervalTicks",
            CATEGORY_TERMINAL,
            DEFAULT_VIRTUAL_RESOURCE_UPDATE_INTERVAL_TICKS,
            1,
            Integer.MAX_VALUE,
            "How many server ticks may pass before virtual resource amounts are refreshed. "
                + "The default is 5 ticks (0.25 seconds)."
        );
        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    public static int getVirtualResourceUpdateIntervalTicks() {
        return virtualResourceUpdateIntervalTicks;
    }
}

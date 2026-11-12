package nyonio.terminal_interaction_integration;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-side settings shared with addons. */
public final class TIIConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.IntValue UPDATE_INTERVAL;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("terminal");
        UPDATE_INTERVAL = builder.comment("Server ticks between virtual resource updates (default: 10).")
                .defineInRange("virtualResourceUpdateIntervalTicks", 10, 1, Integer.MAX_VALUE);
        builder.pop();
        SPEC = builder.build();
    }

    private TIIConfig() {
    }

    public static int getVirtualResourceUpdateIntervalTicks() {
        return UPDATE_INTERVAL.get();
    }
}

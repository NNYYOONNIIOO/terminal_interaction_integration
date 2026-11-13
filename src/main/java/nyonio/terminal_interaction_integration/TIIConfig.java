package nyonio.terminal_interaction_integration;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-side settings shared with addons. */
public final class TIIConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec SERVER_SPEC;
    private static final ForgeConfigSpec.IntValue UPDATE_INTERVAL;
    private static final ForgeConfigSpec.BooleanValue FLUID_TERMINAL_INTEGRATION;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("terminal");
        UPDATE_INTERVAL = builder.comment("Server ticks between virtual resource updates (default: 10).")
                .defineInRange("virtualResourceUpdateIntervalTicks", 10, 1, Integer.MAX_VALUE);
        builder.pop();
        SPEC = builder.build();

        ForgeConfigSpec.Builder server = new ForgeConfigSpec.Builder();
        server.push("terminal");
        FLUID_TERMINAL_INTEGRATION = server.comment(
                "Show stored fluids in ME item terminals and allow fluid-container interaction.",
                "Synced from the server. Does not disable resources registered by addons.")
                .define("enableFluidTerminalIntegration", true);
        server.pop();
        SERVER_SPEC = server.build();
    }

    private TIIConfig() {
    }

    public static int getVirtualResourceUpdateIntervalTicks() {
        return UPDATE_INTERVAL.get();
    }

    public static boolean isFluidTerminalIntegrationEnabled() {
        return FLUID_TERMINAL_INTEGRATION.get();
    }
}

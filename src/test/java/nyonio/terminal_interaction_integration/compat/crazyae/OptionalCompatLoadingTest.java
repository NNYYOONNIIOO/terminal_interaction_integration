package nyonio.terminal_interaction_integration.compat.crazyae;

import org.junit.Test;

import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.Assert.*;

public class OptionalCompatLoadingTest {
    @Test public void commonPathsLoadWhenCrazyAEAndBotaniaAreAbsent() throws Exception {
        URL classes = CrazyAEIntegration.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader isolated = new URLClassLoader(new URL[] { classes }, getClass().getClassLoader()) {
            @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("dev.beecube31.crazyae2.") || name.startsWith("vazkii.botania.")) {
                    throw new ClassNotFoundException("Optional mod deliberately absent: " + name);
                }
                if (name.startsWith("nyonio.terminal_interaction_integration.")) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) loaded = findClass(name);
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
                return super.loadClass(name, resolve);
            }
        }) {
            String[] commonClasses = {
                    "nyonio.terminal_interaction_integration.TerminalInteractionIntegration",
                    "nyonio.terminal_interaction_integration.ae2.ResourceTransfers",
                    "nyonio.terminal_interaction_integration.compat.crazyae.CrazyAEIntegration",
                    "nyonio.terminal_interaction_integration.compat.crazyae.CrazyAEGridView",
                    "nyonio.terminal_interaction_integration.compat.crazyae.ForgeEnergyContainerHandler"
            };
            for (String name : commonClasses) {
                Class<?> type = Class.forName(name, true, isolated);
                assertSame(isolated, type.getClassLoader());
                type.getDeclaredMethods(); // Resolve method types as well as the class itself.
                type.getDeclaredConstructors();
            }
            assertThrows(ClassNotFoundException.class,
                    () -> Class.forName("dev.beecube31.crazyae2.core.CrazyAE", false, isolated));
            assertThrows(ClassNotFoundException.class,
                    () -> Class.forName("vazkii.botania.api.mana.IManaItem", false, isolated));
        }
    }
}

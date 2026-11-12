package nyonio.terminal_interaction_integration.ae2;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.*;

/** Detect incompatible AE2 internal changes before packaging a release. */
public class AE2MixinTargetsTest {
    @Test
    public void networkHooksExistInTheSelectedAe2Version() throws IOException {
        ClassNode monitor = read("appeng/me/cache/NetworkMonitor");
        method(monitor, "getAvailableItems");
        method(monitor, "postChange");
        method(monitor, "onTick");
        assertTrue(monitor.fields.stream().anyMatch(field -> field.name.equals("myChannel")));
        assertTrue(monitor.fields.stream().anyMatch(field -> field.name.equals("myGridCache")));

        ClassNode inventory = read("appeng/me/storage/NetworkInventoryHandler");
        assertTrue(hasPriorityInventoryRead(method(inventory, "injectItems")));
        assertTrue(hasPriorityInventoryRead(method(inventory, "extractItems")));
        method(inventory, "surface");
    }

    @Test
    public void terminalAndUpgradeHooksExistInTheSelectedAe2Version() throws IOException {
        method(read("appeng/client/gui/me/items/ItemTerminalScreen"), "handleGridInventoryEntryMouseClick");
        method(read("appeng/client/gui/AEBaseScreen"), "renderTooltips");
        method(read("appeng/container/me/common/MEMonitorableContainer"), "getStackBySerial");
        method(read("appeng/container/AEBaseContainer"), "updateHeld");
        method(read("appeng/parts/automation/UpgradeInventory$UpgradeInvFilter"), "allowInsert");

        MethodNode update = method(read("appeng/parts/automation/UpgradeInventory"), "updateUpgradeInfo");
        boolean storesStackAtSlotTwo = false;
        for (AbstractInsnNode instruction : update.instructions) {
            if (instruction instanceof VarInsnNode && instruction.getOpcode() == Opcodes.ASTORE
                    && ((VarInsnNode) instruction).var == 2) storesStackAtSlotTwo = true;
        }
        assertTrue("Upgrade inventory's stack local changed", storesStackAtSlotTwo);
    }

    private static boolean hasPriorityInventoryRead(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode && instruction.getOpcode() == Opcodes.GETFIELD
                    && ((FieldInsnNode) instruction).name.equals("priorityInventory")) return true;
        }
        return false;
    }

    private static ClassNode read(String name) throws IOException {
        try (InputStream input = AE2MixinTargetsTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull("Missing AE2 class " + name, input);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(ClassNode owner, String name) {
        return owner.methods.stream().filter(candidate -> candidate.name.equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing AE2 method " + owner.name + "." + name));
    }
}

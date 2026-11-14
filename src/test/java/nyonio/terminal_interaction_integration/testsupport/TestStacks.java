package nyonio.terminal_interaction_integration.testsupport;

import appeng.api.storage.data.IAEItemStack;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Resource identity and quantity only; does not bootstrap Minecraft or any mod. */
public final class TestStacks implements InvocationHandler {
    private final String identity;
    private long amount;

    private TestStacks(String identity, long amount) {
        this.identity = identity;
        this.amount = amount;
    }

    public static IAEItemStack stack(String identity, long amount) {
        return (IAEItemStack) Proxy.newProxyInstance(IAEItemStack.class.getClassLoader(),
                new Class<?>[] { IAEItemStack.class }, new TestStacks(identity, amount));
    }

    @Override public Object invoke(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "getStackSize": return amount;
            case "setStackSize": amount = (long) args[0]; return proxy;
            case "incStackSize": amount += (long) args[0]; return null;
            case "copy": return stack(identity, amount);
            case "equals":
            case "isSameType":
                return args[0] != null && Proxy.isProxyClass(args[0].getClass())
                        && Proxy.getInvocationHandler(args[0]) instanceof TestStacks
                        && identity.equals(((TestStacks) Proxy.getInvocationHandler(args[0])).identity);
            case "hashCode": return identity.hashCode();
            case "toString": return identity + ":" + amount;
            case "isItem": return true;
            case "isCraftable":
            case "isFluid": return false;
            default: return null;
        }
    }
}

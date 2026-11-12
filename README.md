# Terminal Interaction Integration — 1.16.5

这是一个 Forge 1.16.5 / AE2 8.4.x 的库模组。本模组不注册 FE、魔力或其他具体资源；附属模组注册资源提供者后，网络里的资源会以虚拟物品显示在 ME 物品终端中。

## 注册虚拟资源

附属模组需要在客户端和服务端都注册相同的提供者，并确保对应的 AE2 存储通道已经注册。最简单的方法是在模组构造时监听本模组的生命周期事件总线：

```java
public MyAddon() {
    FMLJavaModLoadingContext.get().getModEventBus().addListener(MyAddon::registerTerminalResources);
}

private static void registerTerminalResources(ResourceRegistrationEvent event) {
    event.register(
        "myaddon:mana",            // 唯一的提供者名称
        MyChannels.MANA,           // 已注册的 AE2 资源通道
        new ManaPacketType(),      // IPacketType：虚拟物品和资源之间的对应关系
        new ManaContainerHandler() // IContainerHandler；不需要容器交互时可传 null
    );
}
```

也可以实现 `IResourceProvider` 后调用 `event.register(provider)`，或使用 `SimpleResourceProvider`。注册事件在 common setup 阶段发布，不能只在客户端注册。可选的优先级参数用于决定多个提供者识别同一物品时的顺序。

`IPacketType` 定义虚拟物品的识别方式、显示名称及两个工厂：

- `createAEStack(amount)` 返回用于终端显示的 AE2 物品栈；`createItemStack(amount)` 返回其物品表示。
- `createResourceStack(amount)` 返回对应资源在真实存储通道中的栈。栈的资源类型必须固定，数量由参数决定。每次调用应返回新的栈。
- `getName()` 和提供者名称应使用稳定、唯一的标识。显示物品不应与网络中需要当作普通物品存储的真实物品共用同一标识。

同一非物品通道可以注册多个提供者；每个提供者只读取自己 `createResourceStack` 指定的资源类型，不会把通道内其他资源相加。不要使用 AE2 物品通道作为资源通道。

`IContainerHandler` 负责识别容器、读取容量与已存数量，以及在所给物品栈上执行存取。终端每次处理一个容器，方法需要只修改传入的栈：库可能先对副本执行一次写入以确定可接受数量。多个容器堆叠在鼠标上时，处理后的容器进入背包；背包放不下时掉落。最后一个容器保留在鼠标上。

## 终端交互

在 ME 物品终端中，手持已注册容器时，左键将容器物品存入网络，右键与虚拟资源交互。右键普通条目时，有内容的已注册容器也可以将资源存入网络。普通流体终端仍使用 AE2 自身的流体交互。

虚拟资源条目只用于显示和容器交互，不能通过普通提取、Shift 提取、滚轮提取或创造模式中键复制获得实体物品；物品通道也不会将虚拟条目导出为实体物品。资源交换通过对应的真实资源通道完成。

可以通过 `UpgradeModuleRegistration.register(item, maxInstalled)` 注册可放入接口设备的自定义升级模块，并通过 `IUpgradeModuleHost` 查询安装数量。

## 配置和构建

公共配置中的 `terminal.virtualResourceUpdateIntervalTicks` 控制虚拟资源数量的同步间隔，默认为 10 tick，最小为 1 tick。数量以服务端对应资源的实际储量为准。

使用 Java 8 构建：

```sh
./gradlew build
```

1.12.2 分支的附属模组需要为 1.16.5 重新编译并适配 Forge、Minecraft 和 AE2 的类型；不能直接将旧版 jar 放入 1.16.5。

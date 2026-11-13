# Terminal Interaction Integration — 1.16.5

这是一个 Forge 1.16.5 / AE2 8.4.x 的库模组。本模组内置可关闭的普通流体支持，不注册 FE、魔力等附属资源；附属模组注册资源提供者后，网络里的资源会以虚拟物品显示在 ME 物品终端中。

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

如果存取会改变容器物品类型，应重写 `getResultContainer(container)` 返回操作后的物品。默认实现返回原栈，与现有附属二进制兼容；内置流体处理器使用 Forge `IFluidHandlerItem.getContainer()` 取得桶等容器的结果。

## 终端交互

在 ME 物品终端中，手持已注册容器时，左键将容器物品存入网络，右键与虚拟资源交互。右键普通条目时，有内容的已注册容器也可以将资源存入网络。普通流体终端仍使用 AE2 自身的流体交互。

默认情况下，网络流体也显示在 ME 物品终端中，数量单位为 mB（1000 mB = 1 桶），悬停提示提供完整储量。手持兼容的空容器，右键流体条目将其装入容器；手持装有该流体的容器，右键将流体存入网络。装有流体的容器也可右键普通条目或空槽来存入流体。每次处理一个容器，空手不能提取流体或获得免费容器，容量不足一整桶时不会部分填充普通桶。支持 Forge 流体物品能力，保留流体 NBT 区分；附属已注册的同一流体优先显示其自定义条目，避免重复。

流体数量通过网络监视器的变化通知刷新，与其他虚拟资源共用更新间隔；不会每 tick 扫描所有流体，也不保存全局网络缓存。

虚拟资源条目只用于显示和容器交互，不能通过普通提取、Shift 提取、滚轮提取或创造模式中键复制获得实体物品；物品通道也不会将虚拟条目导出为实体物品。资源交换通过对应的真实资源通道完成。

可以通过 `UpgradeModuleRegistration.register(item, maxInstalled)` 注册可放入接口设备的自定义升级模块，并通过 `IUpgradeModuleHost` 查询安装数量。升级槽使用 AE2 8.4.x 的接口类型识别设备，不依赖旧版气体接口的类名。

本分支不包含 1.12 专用的 CraftTweaker JAR 或 1.12.2 启动脚本；附属模组应通过上述 Java API 注册资源。

终端交互提示支持简体中文、英语和俄语，跟随游戏语言切换。具体资源和容器的名称由附属模组提供。

## 配置和构建

公共配置中的 `terminal.virtualResourceUpdateIntervalTicks` 控制虚拟资源数量的同步间隔，默认为 10 tick，最小为 1 tick。数量以服务端对应资源的实际储量为准。

世界目录 `serverconfig/terminal_interaction_integration-server.toml` 中的 `terminal.enableFluidTerminalIntegration` 控制内置流体显示与容器交互，默认 `true`。设为 `false` 可关闭；服务端配置同步到客户端。已有虚拟条目仍受实体提取保护，附属自行注册的 FE、魔力等资源不受该开关影响。新世界的默认配置可放入实例的 `defaultconfigs` 目录。

使用 Java 8 构建：

```sh
./gradlew build
```

1.12.2 分支的附属模组需要为 1.16.5 重新编译并适配 Forge、Minecraft 和 AE2 的类型；不能直接将旧版 jar 放入 1.16.5。

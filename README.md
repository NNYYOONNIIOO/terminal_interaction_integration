This mod provides the ability to interact with the ME terminal and display virtual fluids, allowing developers to use its API to register virtual fluids and interact with them, making the game feel more modern.

## CrazyAE integration (Minecraft 1.12.2)

CrazyAE is optional at runtime. When installed, its FE and Botania mana can be
displayed and exchanged through containers in the ME item terminal. The two
resources choose their display independently:

| Installed addons | FE display | Mana display |
| --- | --- | --- |
| Neither addon | CrazyAE FE icon | CrazyAE mana icon |
| Flux Applied only | Flux Applied FE icon | CrazyAE mana icon |
| Botania Applie only | CrazyAE FE icon | Botania Applie mana icon |
| Both addons | Flux Applied FE icon | Botania Applie mana icon |

Mana requires Botania and CrazyAE's mana storage feature. Disabled CrazyAE storage
features are skipped. CrazyAE's own item models supply `crazyae:item/energy/fe`
and `crazyae:item/mana`; the integration does not duplicate their textures.

With an addon installed, the displayed quantity is the sum of its original
storage channel and CrazyAE's matching resource. Terminal transfers try the
addon's storage first and then CrazyAE for the remaining amount. CrazyAE's other
energy types, such as EU, are never counted as FE. Balances remain in their
original storage: this is terminal display and container interaction integration,
not a replacement for other mods' buses, machines or storage channels.

Left-click keeps the existing container-item deposit action. Right-click exchanges
the selected resource with one container. With stacked containers, the completed
container goes to the inventory (or drops if full); the last container stays on
the cursor. Display icons cannot be extracted as physical items through the ME
item storage path or the terminal's creative duplicate action.

Each grid subscribes to at most two raw monitors per resource. Changes to the
exact FE/mana keys mark those sources dirty. Only dirty balances are looked up,
using AE2's precise-key cache lookup, at the configured virtual resource update
interval (default 10 ticks). There is no per-tick full inventory scan, global
monitor replacement, balance migration or bidirectional monitor forwarding.

Do not enable the old CrazyAE-Nyonification integration alongside this one: its
global monitor replacement conflicts with this terminal-only routing and retains
its own overhead. No game runtime performance percentage is claimed by the build
or the unit checks.

Build with Java 8 using `gradlew.bat build`. The unit checks cover exact resource
filtering, event coalescing, removal deltas, aggregate saturation, and transfers
across two independent storage sources; they do not start Minecraft.

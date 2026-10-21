# Modules, settings and profiles

[Complete module and settings reference](../MODULES.md) · [Install](INSTALLATION.md) · [Back to README](../README.md)

## Find a module

Press **Right Shift** in game or choose **Modules** on the custom Home screen. Search by name or browse the categories. **Studio** uses top tabs and expandable cards. **Click GUI** shows free-floating category panels over the game: left-click a module to toggle it, right-click it for its settings, drag a panel's header to move it and right-click the header to collapse it. **Compact** is a shader board of draggable category columns. All three expose the same module registry and saved values.

A module's switch enables or disables its behavior. Expanding its settings is independent of the switch. Gameplay automation is off on a fresh install; visual defaults and the interface have their own defaults. The [catalogue](../MODULES.md) shows the exact state of every module on a fresh configuration.

## Read the settings reference

Each module's catalogue entry includes its description, stable module ID and a table of settings:

| Setting type | Meaning |
| --- | --- |
| Boolean | An On/Off toggle |
| Number | A slider with a minimum, maximum and step |
| Choice | One of the listed values |
| Text | A constrained text value or a local file selected through its browser |

Defaults describe a fresh configuration, not your currently saved values. Some controls appear only when their parent mode is active; the catalogue includes them so all available choices remain discoverable. Numeric units are included in the control label where defined. Range generally limits work to nearby loaded game data; a larger value does not reveal data the server has not sent.

For precise numeric input, click the displayed value or right-click the slider row, then apply the value. **Tab / Shift+Tab** moves focus; **Enter / Space** activates focused controls. Assign a module keybind in its settings; **Backspace / Delete** clears the binding and **Escape** cancels the edit.

## Appearance and HUD

Open **HUD Studio** for the **Studio / Click GUI / Compact** layouts, **Glass / Solid** menu material, **Glass / Classic / Minimal / Neon** themes and **Rose / Mint / Violet / Gold** accents. Menu and HUD themes are independent. Classic is a theme; retired Workspace and Classic layout values migrate to Studio when loaded.

Choose **Edit HUD** to drag widgets and adjust scale or snapping. Right-click toggles a widget. **Watermark → Logo** selects exactly one of the Analyzer wordmark or A monogram. [Surface rendering](../LIQUID-GLASS.md) and [Home controls](../HOME-SCREEN.md) explain the related visual options.

## Profiles and saved data

Profiles save and load module states, settings and keybinds. Loading one replaces your active configuration, including any gameplay modules it enables. For example, the included `hvh` profile enables combat modules. Loading a profile never starts an Automation task.

Changes save under `config/analyzer/` in your current instance. Keep that directory when upgrading. Older settings retain explicit choices where supported; missing fields receive their current defaults. [Locations and updates](INSTALLATION.md#updating-an-existing-instance)

## Tasks and cosmetics

**Automation** accepts supported local requests such as `get me 600 sand`, shows the compiled stages and material requirements, and waits for **Start reviewed plan**. Existing inventory counts toward a requested final total. Unknown materials, unsupported acquisition and unsafe routes can require attention. Start waits for the menu click and held action controls to release; fresh manual input during a task pauses it. [Automation details](../AUTOMATION.md)

**Cosmetics → Wings** selects Angel, Dragon, Bat or Parrot, using one shared articulated model with different textures. Controls change visual animation, scale, height, back offset and equipment handling. These wings render on your own third-person model; they do not grant flight or synchronize to other players. [Full wing controls](../WINGS.md)

## Behavior and compatibility

Module descriptions state the implemented scope. Related names from other clients do not promise identical settings or server behavior. ESP/finders inspect loaded received data, combat uses its documented reach and timing controls, and server rules can reject client actions. Specialized guides explain the important interactions:

- [Combat, debug ESP and action ownership](../COMBAT-ESP.md)
- [Finder families, history and candidate interpretation](../FINDERS.md)
- [Schematic formats, placement states and fluid construction](../SCHEMATIC-CONSTRUCTION.md)
- [Mob protection and recovery limits](../SAFETY.md)
- [Server Privacy settings and compatibility opt-outs](../SERVER-PRIVACY.md)
- [Observed validation and remaining test scope](../VALIDATION.md)

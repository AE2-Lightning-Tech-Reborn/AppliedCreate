---
navigation:
  title: ME Blueprint Cannon
  icon: appliedcreate:me_blueprint_cannon
  parent: appliedcreate:index.md
  position: 50
item_ids:
  - appliedcreate:me_blueprint_cannon
---

# ME Blueprint Cannon

<ItemImage id="appliedcreate:me_blueprint_cannon" scale="4" />

Build a deployed Create schematic using materials from an ME network. Connect a powered network with an available channel, insert the schematic, and press Play. Each shot costs 5 AE (subject to AE2's power multiplier), plus 2 AE/t idle power. Gunpowder is not required.

<Recipe id="appliedcreate:me_blueprint_cannon" />

Up to four acceleration cards reduce the shot delay. A crafting card requests missing craftable materials and waits for actual delivery. Missing materials, lost ME power, unloaded targets and a full output slot pause construction automatically. The Pause button requires Play to resume. Stop resets the scan; already launched blocks still land. Progress and in-flight blocks survive saves.

The material button shows remaining requirements and ME availability, including blank patterns and upgrade cards. Scroll or use the arrows to turn pages; refresh recalculates requirements. Asterisks indicate tool durability. The warning marker indicates unloaded targets or invalid item data.

## ME blocks and parts

Cable hosts include their cable, side parts and facades. Rotation and mirroring change attachment directions. Settings follow the device's memory-card export/import interface: filters, priority and supported modes are restored. Filters are ghost configuration and consume no filter items. Each restored encoded pattern costs one blank pattern, and upgrades cost their card types and counts. A pre-encoded matching pattern is not required. Facades require a facade representing the correct block.

Only memory-card settings and accounted-for patterns/upgrades are reconstructed. Ordinary inventories, storage-cell contents, provider return buffers, stored energy and grid ownership are not copied. Existing matching blocks are skipped; the cannon does not merge attachments into an existing cable host. Enable block-entity replacement only when intentionally replacing a different block.

## Schematic data validation

Before any material is consumed, every restored pattern must decode successfully and every restored pattern/upgrade is checked for modified data components. Changes to `minecraft:*` components are rejected except `minecraft:custom_data`; default components supplied by the item are unaffected. Third-party component namespaces and `custom_data` are preserved for addon compatibility. This policy does not validate every addon's custom data semantics.

For example, a pattern carrying `minecraft:food` with `using_converts_to` is rejected. The policy applies to blueprint printing; normal memory-card interactions are unchanged. A rejected schematic is retained in the input slot so it can be corrected.

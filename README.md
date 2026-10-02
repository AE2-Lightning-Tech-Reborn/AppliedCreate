# Applied Create

> **Repository notice:** This is a separate copy of Applied Create, not the original mod repository. The original mod author is **loliball**. The original repository is private and requires authorized access.

A bridge between [Create](https://modrinth.com/mod/create) and [Applied Energistics 2](https://modrinth.com/mod/ae2) for Minecraft.

Store rotational stress in ME networks, transmit it through P2P tunnels, and automate mechanical crafting with pattern providers.

[中文说明](README_CN.md)

---

## Features

### Andesite Pattern Provider / Brass Pattern Provider

Pattern providers designed for Create's Mechanical Crafters. Place one adjacent to a connected Mechanical Crafter array, insert AE2 processing patterns, and the provider will automatically push ingredients into the correct crafter grid positions based on the recipe layout.

- **Andesite Pattern Provider** — 9 pattern slots (standard)
- **Brass Pattern Provider** — 36 pattern slots (extended), upgradeable from Andesite variant
- Available as both full blocks and cable-bus parts

### ME Blueprint Cannon

Build deployed Create schematics using ME materials, with acceleration and crafting cards, missing-material waits, a paged material checklist, and saved construction progress. Rebuilds ME cables, parts and facades, restoring memory-card filters and settings. Encoded patterns cost one blank pattern each; upgrades cost their item type and count. Blueprint pattern and upgrade components are validated before payment; ordinary inventories and return buffers are never copied.

### Stress P2P Tunnel

Transmit Create's rotational stress (speed + torque) through AE2 P2P tunnels.

- **Stress P2P Tunnel** — an AE2 P2P tunnel part that attaches to cables

Supports attunement via Create's mechanical items (cogwheels, shafts, etc.).

### ME Gearbox

A bidirectional converter between ME network energy storage and Create's rotational stress.

- **Export Mode** (ME → Kinetic): Reads stored stress from the ME network and outputs it as rotation
- **Import Mode** (Kinetic → ME): Accepts rotational stress from the kinetic network and stores it in ME storage

Right-click to open the GUI. Export mode needs only an output RPM; import mode collects automatically. All gearboxes on a kinetic network share an allocation: native generators satisfy machine load first, exporters pay only the deficit, and importers collect only native surplus. Empty networks rotate at zero SU cost. Shared storage and repeated updates cannot duplicate stress.

### Kinetic Energy Acceptor

Accepts Create's rotational stress and converts it into AE2 grid power (AE). Connect it to a kinetic network to power your ME system mechanically. Scroll to adjust the conversion multiplier (1x–256x).

### Creative Stress Cell

A creative-mode infinite stress source for testing and building. Provides unlimited stress capacity without requiring any kinetic input. Craftable by converting from a Creative Motor, and can be converted back.

### Stress Storage

A full tier system for storing rotational stress values in ME networks, mirroring AE2's item/fluid cell architecture:

| Tier | Component | Cell |
|------|-----------|------|
| 1k–256k | Andesite-style assemblies | Andesite housing cells |
| 1M–256M | Brass-style assemblies | Brass housing cells |

Includes custom processors (Andesite/Brass Stress Processor) and circuit boards craftable via AE2 inscriber or circuit cutter. Cells can be disassembled back into housing and components.

---

## Supported Versions

| Branch | Minecraft | Mod Loader | Status |
|--------|-----------|------------|--------|
| [main](https://github.com/loliball/AppliedCreate/tree/main) | 1.20.1 | Forge | Active |
| [1.21.1-neoforge](https://github.com/loliball/AppliedCreate/tree/1.21.1-neoforge) | 1.21.1 | NeoForge | Active |

## Dependencies (1.21.1 NeoForge)

| Mod | Version | Required |
|-----|---------|----------|
| [NeoForge](https://neoforged.net/) | 21.1.x (1.21.1) | Yes |
| [Create](https://modrinth.com/mod/create) | 6.0+ | Yes |
| [Applied Energistics 2](https://modrinth.com/mod/ae2) | 19.0+ | Yes |
| [Kotlin for Forge](https://modrinth.com/mod/kotlin-for-forge) | 5.0+ | Yes |

## Installation

1. Install NeoForge for Minecraft 1.21.1
2. Install all required dependencies listed above
3. Place `appliedcreate-x.x.x.jar` into your `mods/` folder
4. Launch the game

## Links

- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/applied-create)
- [Modrinth](https://modrinth.com/mod/applied-create)

## License

All Rights Reserved

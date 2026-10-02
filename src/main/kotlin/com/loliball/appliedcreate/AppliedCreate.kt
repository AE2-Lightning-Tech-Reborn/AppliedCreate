package com.loliball.appliedcreate

// Blueprint Cannon is available; Spatial Assembler remains hidden from creative tab/recipes.
import com.loliball.appliedcreate.cannon.MEBlueprintCannonBlock
import com.loliball.appliedcreate.cannon.MEBlueprintCannonBlockEntity
import com.loliball.appliedcreate.cannon.MEBlueprintCannonMenu
import com.loliball.appliedcreate.cannon.ConfigureMECannonPayload
import com.loliball.appliedcreate.cannon.CannonMaterialsPayload
import com.loliball.appliedcreate.cannon.RequestCannonMaterialsPayload
import com.loliball.appliedcreate.config.ACConfig
import com.loliball.appliedcreate.spatial.SpatialAssemblerBlock
import com.loliball.appliedcreate.spatial.SpatialAssemblerBlockEntity
import com.loliball.appliedcreate.spatial.SpatialAssemblerMenu
import com.loliball.appliedcreate.energy.KineticEnergyAcceptorBlock
import com.loliball.appliedcreate.energy.KineticEnergyAcceptorBlockEntity
import com.loliball.appliedcreate.energy.MEGearboxBlock
import com.loliball.appliedcreate.energy.MEGearboxBlockEntity
import com.loliball.appliedcreate.energy.MEGearboxMenu
import com.loliball.appliedcreate.energy.StressNetworkController
import com.loliball.appliedcreate.misc.BlockFumo
import com.loliball.appliedcreate.patternprovider.AndesitePatternProviderBlock
import com.loliball.appliedcreate.patternprovider.BrassPatternProviderBlock
import com.loliball.appliedcreate.patternprovider.AndesitePatternProviderBlockEntity
import com.loliball.appliedcreate.patternprovider.BrassPatternProviderBlockEntity
import com.loliball.appliedcreate.patternprovider.AndesitePatternProviderMenu
import com.loliball.appliedcreate.patternprovider.BrassPatternProviderMenu
import com.loliball.appliedcreate.patternprovider.BrassPatternProviderUpgradeItem
import com.loliball.appliedcreate.patternprovider.MechanicalCraftingPartItem
import com.loliball.appliedcreate.p2p.StressP2PPartItem
import com.loliball.appliedcreate.patternprovider.AndesitePatternProviderPart
import com.loliball.appliedcreate.patternprovider.BrassPatternProviderPart
import com.loliball.appliedcreate.p2p.StressP2PTunnelPart
import com.loliball.appliedcreate.p2p.KineticBridgeRegistry
import com.loliball.appliedcreate.storage.StressKeyType
import com.loliball.appliedcreate.storage.StressStorageCell
import com.loliball.appliedcreate.storage.CreativeStressCell
import appeng.api.storage.StorageCells
import appeng.api.AECapabilities
import appeng.api.features.P2PTunnelAttunement
import appeng.api.stacks.AEKeyType
import appeng.api.stacks.AEKeyTypes
import appeng.blockentity.AEBaseBlockEntity
import appeng.helpers.patternprovider.PatternProviderLogicHost
import appeng.core.definitions.AEItems
import appeng.menu.AEBaseMenu
import appeng.api.parts.PartModels
import appeng.api.upgrades.Upgrades
import appeng.integration.modules.igtooltip.TooltipProviders
import appeng.menu.MenuOpener
import appeng.menu.locator.MenuHostLocator
import appeng.menu.locator.MenuLocators
import com.simibubi.create.api.stress.BlockStressValues
import com.simibubi.create.foundation.data.CreateRegistrate
import com.tterrag.registrate.util.entry.BlockEntry
import com.tterrag.registrate.util.entry.BlockEntityEntry
import com.tterrag.registrate.util.entry.ItemEntry
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.state.BlockBehaviour
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.RegisterEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

@Mod(AppliedCreate.MOD_ID)
class AppliedCreate {
    companion object {
        const val MOD_ID = "appliedcreate"
        val LOGGER: Logger = LogManager.getLogger(MOD_ID)

        // ═══════════════════════════════════════════════════════════════
        //  Registrate instance — replaces DeferredRegister for Blocks, Items, BlockEntities
        // ═══════════════════════════════════════════════════════════════
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        val REGISTRATE: CreateRegistrate = CreateRegistrate.create(MOD_ID)
            .defaultCreativeTab(null as ResourceKey<CreativeModeTab>?)

        // ═══════════════════════════════════════════════════════════════
        //  Menus — kept as DeferredRegister (AE2 MenuLocators need custom wiring)
        // ═══════════════════════════════════════════════════════════════
        val MENU_TYPES: DeferredRegister<MenuType<*>> = DeferredRegister.create(Registries.MENU, MOD_ID)

        // ═══════════════════════════════════════════════════════════════
        //  CreativeTab — kept as DeferredRegister (custom item ordering)
        // ═══════════════════════════════════════════════════════════════
        val CREATIVE_TABS: DeferredRegister<CreativeModeTab> = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID)

        // ──────────────────────────────────────────────────────────────
        //  Andesite Pattern Provider
        // ──────────────────────────────────────────────────────────────
        val ANDESITE_PATTERN_PROVIDER_BLOCK: BlockEntry<AndesitePatternProviderBlock> = REGISTRATE
            .block("andesite_pattern_provider") { _ -> AndesitePatternProviderBlock() }
            .simpleItem()
            .register()

        val ANDESITE_PATTERN_PROVIDER_BE: BlockEntityEntry<AndesitePatternProviderBlockEntity> = REGISTRATE
            .blockEntity("andesite_pattern_provider") { _, pos, state ->
                AndesitePatternProviderBlockEntity(pos, state)
            }
            .validBlocks(ANDESITE_PATTERN_PROVIDER_BLOCK)
            .register()

        // ──────────────────────────────────────────────────────────────
        //  Brass Pattern Provider
        // ──────────────────────────────────────────────────────────────
        val BRASS_PATTERN_PROVIDER_BLOCK: BlockEntry<BrassPatternProviderBlock> = REGISTRATE
            .block("brass_pattern_provider") { _ -> BrassPatternProviderBlock() }
            .simpleItem()
            .register()

        val BRASS_PATTERN_PROVIDER_BE: BlockEntityEntry<BrassPatternProviderBlockEntity> = REGISTRATE
            .blockEntity("brass_pattern_provider") { _, pos, state ->
                BrassPatternProviderBlockEntity(pos, state)
            }
            .validBlocks(BRASS_PATTERN_PROVIDER_BLOCK)
            .register()

        val BRASS_PATTERN_PROVIDER_MENU: DeferredHolder<MenuType<*>, MenuType<BrassPatternProviderMenu>> =
            MENU_TYPES.register("brass_pattern_provider") { ->
                createPatternProviderMenuType { menuType, windowId, inv, host ->
                    BrassPatternProviderMenu(menuType, windowId, inv, host)
                }
            }

        val ANDESITE_PATTERN_PROVIDER_MENU: DeferredHolder<MenuType<*>, MenuType<AndesitePatternProviderMenu>> =
            MENU_TYPES.register("andesite_pattern_provider") { ->
                createPatternProviderMenuType { menuType, windowId, inv, host ->
                    AndesitePatternProviderMenu(menuType, windowId, inv, host)
                }
            }

        // ──────────────────────────────────────────────────────────────
        //  Items — Pattern Provider Parts, P2P, Upgrade
        // ──────────────────────────────────────────────────────────────
        val BRASS_PATTERN_PROVIDER_UPGRADE_ITEM: ItemEntry<BrassPatternProviderUpgradeItem> = REGISTRATE
            .item("brass_pattern_provider_upgrade") { _ -> BrassPatternProviderUpgradeItem() }
            .register()

        val ANDESITE_PATTERN_PROVIDER_PART_ITEM: ItemEntry<MechanicalCraftingPartItem<AndesitePatternProviderPart>> = REGISTRATE
            .item("andesite_pattern_provider_part") { props ->
                MechanicalCraftingPartItem(
                    props,
                    AndesitePatternProviderPart::class.java
                ) { partItem -> AndesitePatternProviderPart(partItem) }
            }
            .register()

        val BRASS_PATTERN_PROVIDER_PART_ITEM: ItemEntry<MechanicalCraftingPartItem<BrassPatternProviderPart>> = REGISTRATE
            .item("brass_pattern_provider_part") { props ->
                MechanicalCraftingPartItem(
                    props,
                    BrassPatternProviderPart::class.java
                ) { partItem -> BrassPatternProviderPart(partItem) }
            }
            .register()

        // ──────────────────────────────────────────────────────────────
        //  Stress P2P Tunnel
        // ──────────────────────────────────────────────────────────────
        val STRESS_P2P_TUNNEL_PART_ITEM: ItemEntry<StressP2PPartItem> = REGISTRATE
            .item("stress_p2p_tunnel") { props ->
                StressP2PPartItem(
                    props,
                    StressP2PTunnelPart::class.java
                ) { partItem -> StressP2PTunnelPart(partItem) }
            }
            .register()

        // ──────────────────────────────────────────────────────────────
        //  ME Blueprint Cannon
        // ──────────────────────────────────────────────────────────────
        val ME_BLUEPRINT_CANNON_BLOCK: BlockEntry<MEBlueprintCannonBlock> = REGISTRATE
            .block("me_blueprint_cannon") { props ->
                MEBlueprintCannonBlock(props.strength(3.5f).noOcclusion())
            }
            .simpleItem()
            .register()

        val ME_BLUEPRINT_CANNON_BE: BlockEntityEntry<MEBlueprintCannonBlockEntity> = REGISTRATE
            .blockEntity("me_blueprint_cannon") { type, pos, state ->
                MEBlueprintCannonBlockEntity(type, pos, state)
            }
            .validBlocks(ME_BLUEPRINT_CANNON_BLOCK)
            .register()

        val ME_BLUEPRINT_CANNON_MENU: DeferredHolder<MenuType<*>, MenuType<MEBlueprintCannonMenu>> =
            MENU_TYPES.register("me_blueprint_cannon") { ->
                IMenuTypeExtension.create { windowId, inv, buf ->
                    MEBlueprintCannonMenu(ME_BLUEPRINT_CANNON_MENU.get(), windowId, inv, buf)
                }
            }

        // ──────────────────────────────────────────────────────────────
        //  Kinetic Energy Acceptor
        // ──────────────────────────────────────────────────────────────
        val KINETIC_ENERGY_ACCEPTOR_BLOCK: BlockEntry<KineticEnergyAcceptorBlock> = REGISTRATE
            .block("kinetic_energy_acceptor") { _ -> KineticEnergyAcceptorBlock() }
            .simpleItem()
            .register()

        val KINETIC_ENERGY_ACCEPTOR_BE: BlockEntityEntry<KineticEnergyAcceptorBlockEntity> = REGISTRATE
            .blockEntity("kinetic_energy_acceptor") { type, pos, state ->
                KineticEnergyAcceptorBlockEntity(type, pos, state)
            }
            .validBlocks(KINETIC_ENERGY_ACCEPTOR_BLOCK)
            .register()

        // ──────────────────────────────────────────────────────────────
        //  ME Gearbox
        // ──────────────────────────────────────────────────────────────
        val ME_GEARBOX_BLOCK: BlockEntry<MEGearboxBlock> = REGISTRATE
            .block("me_gearbox") { _ -> MEGearboxBlock() }
            .simpleItem()
            .register()

        val ME_GEARBOX_BE: BlockEntityEntry<MEGearboxBlockEntity> = REGISTRATE
            .blockEntity("me_gearbox") { type, pos, state ->
                MEGearboxBlockEntity(type, pos, state)
            }
            .validBlocks(ME_GEARBOX_BLOCK)
            .register()

        val ME_GEARBOX_MENU: DeferredHolder<MenuType<*>, MenuType<MEGearboxMenu>> =
            MENU_TYPES.register("me_gearbox") { ->
                createBlockEntityMenuType(MEGearboxBlockEntity::class.java) { _, windowId, inv, host ->
                    MEGearboxMenu(ME_GEARBOX_MENU.get(), windowId, inv, host)
                }
            }

        // ──────────────────────────────────────────────────────────────
        //  小萝卜 (Fumo Doll)
        // ──────────────────────────────────────────────────────────────
        val WHICHBALL_SKIN_DOLL_BLOCK: BlockEntry<BlockFumo> = REGISTRATE
            .block("whichball_skin_doll") { _ -> BlockFumo() }
            .simpleItem()
            .register()

        // ──────────────────────────────────────────────────────────────
        //  Stress Storage Cells
        // ──────────────────────────────────────────────────────────────
        val STRESS_CELL_1K: ItemEntry<StressStorageCell> = REGISTRATE
            .item("stress_storage_cell_1k") { _ -> StressStorageCell(Item.Properties(), 0.5, 1, 8, 1, { ANDESITE_STRESS_CELL_HOUSING.get() }, { STRESS_COMPONENT_1K.get() }) }
            .register()
        val STRESS_CELL_4K: ItemEntry<StressStorageCell> = REGISTRATE
            .item("stress_storage_cell_4k") { _ -> StressStorageCell(Item.Properties(), 1.0, 4, 8, 1, { ANDESITE_STRESS_CELL_HOUSING.get() }, { STRESS_COMPONENT_4K.get() }) }
            .register()
        val STRESS_CELL_16K: ItemEntry<StressStorageCell> = REGISTRATE
            .item("stress_storage_cell_16k") { _ -> StressStorageCell(Item.Properties(), 1.5, 16, 8, 1, { ANDESITE_STRESS_CELL_HOUSING.get() }, { STRESS_COMPONENT_16K.get() }) }
            .register()
        val STRESS_CELL_64K: ItemEntry<StressStorageCell> = REGISTRATE
            .item("stress_storage_cell_64k") { _ -> StressStorageCell(Item.Properties(), 2.0, 64, 8, 1, { ANDESITE_STRESS_CELL_HOUSING.get() }, { STRESS_COMPONENT_64K.get() }) }
            .register()
        val STRESS_CELL_256K: ItemEntry<StressStorageCell> = REGISTRATE
            .item("stress_storage_cell_256k") { _ -> StressStorageCell(Item.Properties(), 2.5, 256, 8, 1, { ANDESITE_STRESS_CELL_HOUSING.get() }, { STRESS_COMPONENT_256K.get() }) }
            .register()
        val STRESS_CELL_1M: ItemEntry<StressStorageCell> = REGISTRATE
            .item("stress_storage_cell_1m") { _ -> StressStorageCell(Item.Properties(), 3.0, 1024, 8, 1, { BRASS_STRESS_CELL_HOUSING.get() }, { STRESS_COMPONENT_1M.get() }) }
            .register()
        val STRESS_CELL_4M: ItemEntry<StressStorageCell> = REGISTRATE
            .item("stress_storage_cell_4m") { _ -> StressStorageCell(Item.Properties(), 3.5, 4096, 8, 1, { BRASS_STRESS_CELL_HOUSING.get() }, { STRESS_COMPONENT_4M.get() }) }
            .register()
        val STRESS_CELL_16M: ItemEntry<StressStorageCell> = REGISTRATE
            .item("stress_storage_cell_16m") { _ -> StressStorageCell(Item.Properties(), 4.0, 16384, 8, 1, { BRASS_STRESS_CELL_HOUSING.get() }, { STRESS_COMPONENT_16M.get() }) }
            .register()
        val STRESS_CELL_64M: ItemEntry<StressStorageCell> = REGISTRATE
            .item("stress_storage_cell_64m") { _ -> StressStorageCell(Item.Properties(), 4.5, 65536, 8, 1, { BRASS_STRESS_CELL_HOUSING.get() }, { STRESS_COMPONENT_64M.get() }) }
            .register()
        val STRESS_CELL_256M: ItemEntry<StressStorageCell> = REGISTRATE
            .item("stress_storage_cell_256m") { _ -> StressStorageCell(Item.Properties(), 5.0, 262144, 8, 1, { BRASS_STRESS_CELL_HOUSING.get() }, { STRESS_COMPONENT_256M.get() }) }
            .register()
        val CREATIVE_STRESS_CELL: ItemEntry<CreativeStressCell> = REGISTRATE
            .item("creative_stress_cell") { _ -> CreativeStressCell(Item.Properties()) }
            .register()

        // ──────────────────────────────────────────────────────────────
        //  Spatial Assembler  [HIDDEN] — registered but not in creative tab/recipes
        // ──────────────────────────────────────────────────────────────
        val SPATIAL_ASSEMBLER_BLOCK: BlockEntry<SpatialAssemblerBlock> = REGISTRATE
            .block("spatial_assembler") { props ->
                SpatialAssemblerBlock(props.strength(3.5f).noOcclusion())
            }
            .simpleItem()
            .register()

        val SPATIAL_ASSEMBLER_BE: BlockEntityEntry<SpatialAssemblerBlockEntity> = REGISTRATE
            .blockEntity("spatial_assembler") { type, pos, state ->
                SpatialAssemblerBlockEntity(type, pos, state)
            }
            .validBlocks(SPATIAL_ASSEMBLER_BLOCK)
            .register()

        val SPATIAL_ASSEMBLER_MENU: DeferredHolder<MenuType<*>, MenuType<SpatialAssemblerMenu>> =
            MENU_TYPES.register("spatial_assembler") { ->
                IMenuTypeExtension.create { windowId, inv, buf ->
                    SpatialAssemblerMenu(SPATIAL_ASSEMBLER_MENU.get(), windowId, inv, buf)
                }
            }

        val STRESS_CELLS: List<ItemEntry<StressStorageCell>> by lazy {
            listOf(
                STRESS_CELL_1K, STRESS_CELL_4K, STRESS_CELL_16K, STRESS_CELL_64K, STRESS_CELL_256K,
                STRESS_CELL_1M, STRESS_CELL_4M, STRESS_CELL_16M, STRESS_CELL_64M, STRESS_CELL_256M
            )
        }

        // ──────────────────────────────────────────────────────────────
        //  Stress Crafting Items
        // ──────────────────────────────────────────────────────────────
        // Circuit boards (inscribed)
        val STRESS_CIRCUIT_BOARD: ItemEntry<Item> = REGISTRATE
            .item("stress_circuit_board") { props -> Item(props) }
            .register()
        val ADVANCED_STRESS_CIRCUIT_BOARD: ItemEntry<Item> = REGISTRATE
            .item("advanced_stress_circuit_board") { props -> Item(props) }
            .register()

        // Processors (assembled from circuit board + silicon + redstone)
        val STRESS_PROCESSOR: ItemEntry<Item> = REGISTRATE
            .item("stress_processor") { props -> Item(props) }
            .register()
        val ADVANCED_STRESS_PROCESSOR: ItemEntry<Item> = REGISTRATE
            .item("advanced_stress_processor") { props -> Item(props) }
            .register()

        // Storage components (crafted with processors, following AE2 component pattern)
        val STRESS_COMPONENT_1K: ItemEntry<Item> = REGISTRATE
            .item("stress_storage_component_1k") { props -> Item(props) }
            .register()
        val STRESS_COMPONENT_4K: ItemEntry<Item> = REGISTRATE
            .item("stress_storage_component_4k") { props -> Item(props) }
            .register()
        val STRESS_COMPONENT_16K: ItemEntry<Item> = REGISTRATE
            .item("stress_storage_component_16k") { props -> Item(props) }
            .register()
        val STRESS_COMPONENT_64K: ItemEntry<Item> = REGISTRATE
            .item("stress_storage_component_64k") { props -> Item(props) }
            .register()
        val STRESS_COMPONENT_256K: ItemEntry<Item> = REGISTRATE
            .item("stress_storage_component_256k") { props -> Item(props) }
            .register()
        val STRESS_COMPONENT_1M: ItemEntry<Item> = REGISTRATE
            .item("stress_storage_component_1m") { props -> Item(props) }
            .register()
        val STRESS_COMPONENT_4M: ItemEntry<Item> = REGISTRATE
            .item("stress_storage_component_4m") { props -> Item(props) }
            .register()
        val STRESS_COMPONENT_16M: ItemEntry<Item> = REGISTRATE
            .item("stress_storage_component_16m") { props -> Item(props) }
            .register()
        val STRESS_COMPONENT_64M: ItemEntry<Item> = REGISTRATE
            .item("stress_storage_component_64m") { props -> Item(props) }
            .register()
        val STRESS_COMPONENT_256M: ItemEntry<Item> = REGISTRATE
            .item("stress_storage_component_256m") { props -> Item(props) }
            .register()

        // Cell housings
        val ANDESITE_STRESS_CELL_HOUSING: ItemEntry<Item> = REGISTRATE
            .item("andesite_stress_cell_housing") { props -> Item(props) }
            .register()
        val BRASS_STRESS_CELL_HOUSING: ItemEntry<Item> = REGISTRATE
            .item("brass_stress_cell_housing") { props -> Item(props) }
            .register()

        val STRESS_COMPONENTS: List<ItemEntry<Item>> by lazy {
            listOf(
                STRESS_COMPONENT_1K, STRESS_COMPONENT_4K, STRESS_COMPONENT_16K, STRESS_COMPONENT_64K, STRESS_COMPONENT_256K,
                STRESS_COMPONENT_1M, STRESS_COMPONENT_4M, STRESS_COMPONENT_16M, STRESS_COMPONENT_64M, STRESS_COMPONENT_256M
            )
        }

        // ──────────────────────────────────────────────────────────────
        //  Creative Tab
        // ──────────────────────────────────────────────────────────────
        @Suppress("unused")
        val CREATIVE_TAB: DeferredHolder<CreativeModeTab, CreativeModeTab> = CREATIVE_TABS.register("main") { ->
            CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.$MOD_ID"))
                .icon { ANDESITE_PATTERN_PROVIDER_BLOCK.asItem().defaultInstance }
                .displayItems { _, output ->
                    output.accept(ANDESITE_PATTERN_PROVIDER_BLOCK.asStack())
                    output.accept(BRASS_PATTERN_PROVIDER_BLOCK.asStack())
                    output.accept(ANDESITE_PATTERN_PROVIDER_PART_ITEM.get())
                    output.accept(BRASS_PATTERN_PROVIDER_PART_ITEM.get())
                    output.accept(BRASS_PATTERN_PROVIDER_UPGRADE_ITEM.get())
                    output.accept(STRESS_P2P_TUNNEL_PART_ITEM.get())
                    output.accept(ME_BLUEPRINT_CANNON_BLOCK.asStack())
                    output.accept(KINETIC_ENERGY_ACCEPTOR_BLOCK.asStack())
                    output.accept(ME_GEARBOX_BLOCK.asStack())
                    output.accept(WHICHBALL_SKIN_DOLL_BLOCK.asStack())
                    // Crafting items
                    output.accept(STRESS_CIRCUIT_BOARD.get())
                    output.accept(ADVANCED_STRESS_CIRCUIT_BOARD.get())
                    output.accept(STRESS_PROCESSOR.get())
                    output.accept(ADVANCED_STRESS_PROCESSOR.get())
                    output.accept(ANDESITE_STRESS_CELL_HOUSING.get())
                    output.accept(BRASS_STRESS_CELL_HOUSING.get())
                    // Storage components
                    STRESS_COMPONENTS.forEach { output.accept(it.get()) }
                    // Storage cells
                    STRESS_CELLS.forEach { output.accept(it.get()) }
                    output.accept(CREATIVE_STRESS_CELL.get())
                    // output.accept(SPATIAL_ASSEMBLER_BLOCK.asStack())  // [HIDDEN]
                }
                .build()
        }

        // ──────────────────────────────────────────────────────────────
        //  Helpers
        // ──────────────────────────────────────────────────────────────
        @Suppress("UNCHECKED_CAST")
        private fun <T : AEBaseMenu> createPatternProviderMenuType(
            factory: (MenuType<T>, Int, Inventory, PatternProviderLogicHost) -> T
        ): MenuType<T> {
            var menuTypeHolder: MenuType<T>? = null
            val menuType = IMenuTypeExtension.create { windowId, inv, buf ->
                val locator = MenuLocators.readFromPacket(buf)
                val host = locator.locate(inv.player, PatternProviderLogicHost::class.java)
                    ?: throw IllegalStateException("Could not find PatternProviderLogicHost")
                val menu = factory(menuTypeHolder!!, windowId, inv, host)
                menu.setLocator(locator)
                menu.setReturnedFromSubScreen(buf.readBoolean())
                menu
            }
            menuTypeHolder = menuType as MenuType<T>
            return menuType
        }

        private fun <T : AEBaseMenu> registerPatternProviderOpener(
            menuType: MenuType<T>,
            menuFactory: (Int, Inventory, PatternProviderLogicHost) -> T
        ) {
            MenuOpener.addOpener(menuType) { player: Player, locator: MenuHostLocator, fromSubMenu: Boolean ->
                if (player !is ServerPlayer) return@addOpener false
                val host = locator.locate(player, PatternProviderLogicHost::class.java) ?: return@addOpener false
                val title = Component.empty()
                val menuProvider = SimpleMenuProvider({ wnd, p, _ ->
                    val m = menuFactory(wnd, p, host)
                    m.setLocator(locator)
                    m
                }, title)
                player.openMenu(menuProvider) { buffer ->
                    MenuLocators.writeToPacket(buffer, locator)
                    buffer.writeBoolean(fromSubMenu)
                }
                true
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun <H, T : AEBaseMenu> createBlockEntityMenuType(
            hostClass: Class<H>,
            factory: (MenuType<T>, Int, Inventory, H) -> T
        ): MenuType<T> {
            var menuTypeHolder: MenuType<T>? = null
            val menuType = IMenuTypeExtension.create { windowId, inv, buf ->
                val locator = MenuLocators.readFromPacket(buf)
                val host = locator.locate(inv.player, hostClass)
                    ?: throw IllegalStateException("Could not find host of type ${hostClass.simpleName}")
                val menu = factory(menuTypeHolder!!, windowId, inv, host)
                menu.setLocator(locator)
                menu.setReturnedFromSubScreen(buf.readBoolean())
                menu
            }
            menuTypeHolder = menuType as MenuType<T>
            return menuType
        }

        private fun <H, T : AEBaseMenu> registerBlockEntityMenuOpener(
            menuType: MenuType<T>,
            hostClass: Class<H>,
            menuFactory: (Int, Inventory, H) -> T
        ) {
            MenuOpener.addOpener(menuType) { player: Player, locator: MenuHostLocator, fromSubMenu: Boolean ->
                if (player !is ServerPlayer) return@addOpener false
                val host = locator.locate(player, hostClass) ?: return@addOpener false
                val title = Component.empty()
                val menuProvider = SimpleMenuProvider({ wnd, p, _ ->
                    val m = menuFactory(wnd, p, host)
                    m.setLocator(locator)
                    m
                }, title)
                player.openMenu(menuProvider) { buffer ->
                    MenuLocators.writeToPacket(buffer, locator)
                    buffer.writeBoolean(fromSubMenu)
                }
                true
            }
        }
    }

    init {
        val bus: IEventBus = MOD_BUS

        ModLoadingContext.get().getActiveContainer().registerConfig(
            ModConfig.Type.SERVER,
            ACConfig.SPEC
        )

        // Registrate handles block/item/blockentity registration via its own event listeners
        REGISTRATE.registerEventListeners(bus)

        // Menu and CreativeTab still use DeferredRegister
        MENU_TYPES.register(bus)
        CREATIVE_TABS.register(bus)

        bus.addListener { event: RegisterEvent ->
            event.register(AEKeyType.REGISTRY_KEY) {
                AEKeyTypes.register(StressKeyType.TYPE)
            }
        }
        // Blueprint Cannon network payload
        bus.addListener { event: RegisterPayloadHandlersEvent ->
            val registrar = event.registrar(MOD_ID)
            registrar.playToServer(
                ConfigureMECannonPayload.TYPE,
                ConfigureMECannonPayload.STREAM_CODEC,
                ConfigureMECannonPayload::handle
            )
            registrar.playToServer(RequestCannonMaterialsPayload.TYPE, RequestCannonMaterialsPayload.STREAM_CODEC, RequestCannonMaterialsPayload::handle)
            registrar.playToClient(CannonMaterialsPayload.TYPE, CannonMaterialsPayload.STREAM_CODEC, CannonMaterialsPayload::handle)
        }

        // Register part models manually since Kotlin companion object @PartModels annotations
        // are not discoverable by AE2's Java reflection-based PartModelsHelper scanner
        PartModels.registerModels(
            AndesitePatternProviderPart.ANDESITE_MODEL_BASE
        )
        PartModels.registerModels(
            *AndesitePatternProviderPart.ANDESITE_MODELS_OFF.models.toTypedArray()
        )
        PartModels.registerModels(
            *AndesitePatternProviderPart.ANDESITE_MODELS_ON.models.toTypedArray()
        )
        PartModels.registerModels(
            *AndesitePatternProviderPart.ANDESITE_MODELS_HAS_CHANNEL.models.toTypedArray()
        )
        PartModels.registerModels(
            BrassPatternProviderPart.BRASS_MODEL_BASE
        )
        PartModels.registerModels(
            *BrassPatternProviderPart.BRASS_MODELS_OFF.models.toTypedArray()
        )
        PartModels.registerModels(
            *BrassPatternProviderPart.BRASS_MODELS_ON.models.toTypedArray()
        )
        PartModels.registerModels(
            *BrassPatternProviderPart.BRASS_MODELS_HAS_CHANNEL.models.toTypedArray()
        )

        // Register Stress P2P Tunnel part models
        for (model in StressP2PTunnelPart.getModels()) {
            PartModels.registerModels(*model.models.toTypedArray())
        }

        bus.addListener(::onCommonSetup)
        bus.addListener(::onRegisterCapabilities)

        if (FMLEnvironment.dist.isClient) {
            ClientSetup.register(bus)
        }

        // Clear kinetic bridge registry on server shutdown to prevent stale data
        NeoForge.EVENT_BUS.addListener { _: ServerStoppingEvent ->
            KineticBridgeRegistry.clear()
            StressNetworkController.clear()
            LOGGER.debug("Cleared KineticBridgeRegistry on server stop")
        }
        NeoForge.EVENT_BUS.addListener { event: ServerTickEvent.Pre ->
            StressNetworkController.beginTick(event.server)
        }
        NeoForge.EVENT_BUS.addListener { event: ServerTickEvent.Post ->
            StressNetworkController.endTick(event.server)
        }

        // Reset shutdown flag when a new server starts (integrated server restart)
        NeoForge.EVENT_BUS.addListener { _: net.neoforged.neoforge.event.server.ServerStartingEvent ->
            KineticBridgeRegistry.resetShutdownFlag()
        }

        LOGGER.info("Applied Create loaded")
    }

    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        // Register representative items so AE2 network status and Jade show our devices correctly
        AEBaseBlockEntity.registerBlockEntityItem(
            ANDESITE_PATTERN_PROVIDER_BE.get(),
            ANDESITE_PATTERN_PROVIDER_BLOCK.asItem()
        )
        AEBaseBlockEntity.registerBlockEntityItem(
            BRASS_PATTERN_PROVIDER_BE.get(),
            BRASS_PATTERN_PROVIDER_BLOCK.asItem()
        )
        // Blueprint Cannon
        AEBaseBlockEntity.registerBlockEntityItem(
            ME_BLUEPRINT_CANNON_BE.get(),
            ME_BLUEPRINT_CANNON_BLOCK.asItem()
        )
        AEBaseBlockEntity.registerBlockEntityItem(
            KINETIC_ENERGY_ACCEPTOR_BE.get(),
            KINETIC_ENERGY_ACCEPTOR_BLOCK.asItem()
        )
        AEBaseBlockEntity.registerBlockEntityItem(
            ME_GEARBOX_BE.get(),
            ME_GEARBOX_BLOCK.asItem()
        )
        // [HIDDEN] Spatial Assembler
        AEBaseBlockEntity.registerBlockEntityItem(
            SPATIAL_ASSEMBLER_BE.get(),
            SPATIAL_ASSEMBLER_BLOCK.asItem()
        )

        // Reload AE2's igtooltip ServiceLoader to ensure our AppliedCreateTooltipProvider
        // is discovered (NeoForge module layers may cache ServiceLoader before our mod loads)
        try {
            TooltipProviders.LOADER.reload()
            LOGGER.debug("Reloaded AE2 igtooltip ServiceLoader")
        } catch (e: Exception) {
            LOGGER.warn("Failed to reload AE2 igtooltip ServiceLoader: {}", e.message)
        }

        event.enqueueWork {
            // Register P2P attunement tag — now works because StressP2PPartItem extends PartItem
            P2PTunnelAttunement.registerAttunementTag(STRESS_P2P_TUNNEL_PART_ITEM.get())

            registerPatternProviderOpener(BRASS_PATTERN_PROVIDER_MENU.get()) { wnd, inv, host ->
                BrassPatternProviderMenu(
                    BRASS_PATTERN_PROVIDER_MENU.get(),
                    wnd, inv, host
                )
            }

            registerPatternProviderOpener(ANDESITE_PATTERN_PROVIDER_MENU.get()) { wnd, inv, host ->
                AndesitePatternProviderMenu(
                    ANDESITE_PATTERN_PROVIDER_MENU.get(),
                    wnd, inv, host
                )
            }

            registerBlockEntityMenuOpener(ME_GEARBOX_MENU.get(), MEGearboxBlockEntity::class.java) { wnd, inv, host ->
                MEGearboxMenu(ME_GEARBOX_MENU.get(), wnd, inv, host)
            }

            // Register stress value for kinetic energy acceptor
            BlockStressValues.IMPACTS.register(
                KINETIC_ENERGY_ACCEPTOR_BLOCK.get(), { KineticEnergyAcceptorBlockEntity.getBaseStressSu() / 256.0 }
            )

            // Register stress values for ME Gearbox
            BlockStressValues.IMPACTS.register(
                ME_GEARBOX_BLOCK.get(), { 0.0 }
            )
            BlockStressValues.CAPACITIES.register(
                ME_GEARBOX_BLOCK.get(), { 0.0 }
            )

            // Blueprint Cannon upgrade cards
            Upgrades.add(AEItems.SPEED_CARD, ME_BLUEPRINT_CANNON_BLOCK.asItem(), 4)
            Upgrades.add(AEItems.CRAFTING_CARD, ME_BLUEPRINT_CANNON_BLOCK.asItem(), 1)

            // Register creative stress cell handler
            StorageCells.addCellHandler(CreativeStressCell.Handler)
        }
    }

    private fun onRegisterCapabilities(event: RegisterCapabilitiesEvent) {
        // Register IN_WORLD_GRID_NODE_HOST so AE2 cables recognize our blocks as grid devices
        event.registerBlockEntity(
            AECapabilities.IN_WORLD_GRID_NODE_HOST,
            ANDESITE_PATTERN_PROVIDER_BE.get()
        ) { be, _ -> be }
        event.registerBlockEntity(
            AECapabilities.IN_WORLD_GRID_NODE_HOST,
            BRASS_PATTERN_PROVIDER_BE.get()
        ) { be, _ -> be }

        // Blueprint Cannon
        event.registerBlockEntity(
            AECapabilities.IN_WORLD_GRID_NODE_HOST,
            ME_BLUEPRINT_CANNON_BE.get()
        ) { be, _ -> be }

        event.registerBlockEntity(
            AECapabilities.IN_WORLD_GRID_NODE_HOST,
            KINETIC_ENERGY_ACCEPTOR_BE.get()
        ) { be, _ -> be }

        event.registerBlockEntity(
            AECapabilities.IN_WORLD_GRID_NODE_HOST,
            ME_GEARBOX_BE.get()
        ) { be, _ -> be }

        // [HIDDEN] Spatial Assembler
        event.registerBlockEntity(
            AECapabilities.IN_WORLD_GRID_NODE_HOST,
            SPATIAL_ASSEMBLER_BE.get()
        ) { be, _ -> be }

        event.registerBlockEntity(
            AECapabilities.GENERIC_INTERNAL_INV,
            ANDESITE_PATTERN_PROVIDER_BE.get()
        ) { be, _ -> be.logic.returnInv }
        event.registerBlockEntity(
            AECapabilities.GENERIC_INTERNAL_INV,
            BRASS_PATTERN_PROVIDER_BE.get()
        ) { be, _ -> be.logic.returnInv }
    }
}

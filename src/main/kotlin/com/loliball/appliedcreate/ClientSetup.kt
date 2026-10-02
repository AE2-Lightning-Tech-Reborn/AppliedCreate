package com.loliball.appliedcreate

import com.loliball.appliedcreate.storage.StressKeyRenderHandler
import com.loliball.appliedcreate.patternprovider.BrassPatternProviderMenu
import com.loliball.appliedcreate.patternprovider.AndesitePatternProviderMenu
// Blueprint Cannon and Spatial Assembler client registrations.
import com.loliball.appliedcreate.cannon.MEBlueprintCannonMenu
import com.loliball.appliedcreate.cannon.MEBlueprintCannonRenderer
import com.loliball.appliedcreate.cannon.MEBlueprintCannonScreen
import com.loliball.appliedcreate.spatial.SpatialAssemblerScreen
import com.loliball.appliedcreate.energy.KineticEnergyAcceptorRenderer
import com.loliball.appliedcreate.energy.MEGearboxMenu
import com.loliball.appliedcreate.energy.MEGearboxRenderer
import com.loliball.appliedcreate.energy.MEGearboxScreen
import com.loliball.appliedcreate.storage.StressStorageCell

import appeng.client.gui.style.ScreenStyle
import appeng.client.gui.implementations.PatternProviderScreen
import appeng.init.client.InitScreens

import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.world.entity.player.Inventory
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.event.AddPackFindersEvent
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent
@OnlyIn(Dist.CLIENT)
object ClientSetup {

    fun register(bus: IEventBus) {
        bus.addListener(::onAddPackFinders)
        bus.addListener(::onRegisterMenuScreens)
        bus.addListener(::onRegisterItemColors)
        bus.addListener(::onRegisterRenderers)
    }

    private fun onAddPackFinders(event: AddPackFindersEvent) {
        event.addPackFinders(
            ResourceLocation.fromNamespaceAndPath(
                AppliedCreate.MOD_ID,
                "resourcepacks/appliedcreate"
            ),
            PackType.CLIENT_RESOURCES,
            Component.translatable("resourcePack.appliedcreate.builtin"),
            PackSource.BUILT_IN,
            false,
            Pack.Position.TOP
        )
    }

    private fun onRegisterMenuScreens(event: RegisterMenuScreensEvent) {
        InitScreens.register(
            event,
            AppliedCreate.BRASS_PATTERN_PROVIDER_MENU.get(),
            { menu: BrassPatternProviderMenu, inv: Inventory, title: Component, style: ScreenStyle ->
                PatternProviderScreen(menu, inv, title, style)
            },
            "/screens/appliedcreate/brass_pattern_provider.json"
        )
        InitScreens.register(
            event,
            AppliedCreate.ANDESITE_PATTERN_PROVIDER_MENU.get(),
            { menu: AndesitePatternProviderMenu, inv: Inventory, title: Component, style: ScreenStyle ->
                PatternProviderScreen(menu, inv, title, style)
            },
            "/screens/appliedcreate/andesite_pattern_provider.json"
        )
        // Blueprint Cannon and Spatial Assembler screens
        event.register(
            AppliedCreate.ME_BLUEPRINT_CANNON_MENU.get(),
            ::MEBlueprintCannonScreen
        )
        event.register(
            AppliedCreate.SPATIAL_ASSEMBLER_MENU.get(),
            ::SpatialAssemblerScreen
        )

        InitScreens.register(
            event,
            AppliedCreate.ME_GEARBOX_MENU.get(),
            { menu: MEGearboxMenu, inv: Inventory, title: Component, style: ScreenStyle ->
                MEGearboxScreen(menu, inv, title, style)
            },
            "/screens/appliedcreate/me_gearbox.json"
        )

        // Register StressKey render handler for ME terminal display
        StressKeyRenderHandler.register()
    }

    private fun onRegisterItemColors(event: RegisterColorHandlersEvent.Item) {
        // Register color handler for stress storage cells (LED tint layer)
        val cellItems = AppliedCreate.STRESS_CELLS.map { it.get() }.toTypedArray()
        event.register(
            { stack, tintIndex -> StressStorageCell.getColor(stack, tintIndex) },
            *cellItems
        )
    }

    private fun onRegisterRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerBlockEntityRenderer(AppliedCreate.ME_BLUEPRINT_CANNON_BE.get(), ::MEBlueprintCannonRenderer)
        event.registerBlockEntityRenderer(AppliedCreate.KINETIC_ENERGY_ACCEPTOR_BE.get(), ::KineticEnergyAcceptorRenderer)
        event.registerBlockEntityRenderer(AppliedCreate.ME_GEARBOX_BE.get(), ::MEGearboxRenderer)
    }
}

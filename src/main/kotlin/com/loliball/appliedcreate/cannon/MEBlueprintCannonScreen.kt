package com.loliball.appliedcreate.cannon

import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity
import com.simibubi.create.foundation.gui.AllGuiTextures
import com.simibubi.create.foundation.gui.AllIcons
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen
import com.simibubi.create.foundation.gui.widget.IconButton
import com.simibubi.create.foundation.gui.widget.Indicator
import com.simibubi.create.foundation.item.TooltipHelper
import com.simibubi.create.foundation.utility.CreateLang
import net.createmod.catnip.lang.FontHelper
import net.createmod.catnip.gui.element.GuiGameElement
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.renderer.Rect2i
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.neoforged.neoforge.network.PacketDistributor
import java.util.*

class MEBlueprintCannonScreen(menu: MEBlueprintCannonMenu, inventory: Inventory, title: Component) :
    AbstractSimiContainerScreen<MEBlueprintCannonMenu>(menu, inventory, title) {

    companion object {
        private val BG_TOP = AllGuiTextures.SCHEMATICANNON_TOP
        private val BG_BOTTOM = AllGuiTextures.SCHEMATICANNON_BOTTOM
    }

    private val _showSettings = "gui.schematicannon.showOptions"
    private val _slotSchematic = "gui.schematicannon.slot.schematic"
    private val _slotUpgrades = "gui.appliedcreate.slot.upgrades"

    private val optionEnabled = CreateLang.translateDirect("gui.schematicannon.optionEnabled")
    private val optionDisabled = CreateLang.translateDirect("gui.schematicannon.optionDisabled")

    private var replaceLevelIndicators = Vector<Indicator>()
    private var replaceLevelButtons = Vector<IconButton>()

    private lateinit var skipMissingButton: IconButton
    private lateinit var skipMissingIndicator: Indicator
    private lateinit var skipBlockEntitiesButton: IconButton
    private lateinit var skipBlockEntitiesIndicator: Indicator

    private lateinit var playButton: IconButton
    private lateinit var playIndicator: Indicator
    private lateinit var pauseButton: IconButton
    private lateinit var pauseIndicator: Indicator
    private lateinit var resetButton: IconButton
    private lateinit var resetIndicator: Indicator

    private lateinit var confirmButton: IconButton
    private lateinit var showSettingsButton: IconButton
    private lateinit var showSettingsIndicator: Indicator
    private lateinit var viewMaterialsButton: IconButton

    private var placementSettingWidgets = ArrayList<AbstractWidget>()
    private var extraAreas: List<Rect2i> = Collections.emptyList()


    private val UPGRADE_SLOT_SIZE = 18
    private val UPGRADE_PADDING = 7
    private val UPGRADE_SLOTS = 5


    private var showingMaterials = false

    override fun init() {
        setWindowSize(BG_TOP.width, BG_TOP.height + BG_BOTTOM.height + 2 + AllGuiTextures.PLAYER_INVENTORY.height)
        setWindowOffset(-11, 0)
        super.init()

        val x = leftPos
        val y = topPos


        playButton = IconButton(x + 75, y + 85, AllIcons.I_PLAY)
        playButton.withCallback<IconButton> { sendOptionUpdate(ConfigureMECannonPayload.Option.PLAY, true) }
        playIndicator = Indicator(x + 75, y + 79, CommonComponents.EMPTY)

        pauseButton = IconButton(x + 93, y + 85, AllIcons.I_PAUSE)
        pauseButton.withCallback<IconButton> { sendOptionUpdate(ConfigureMECannonPayload.Option.PAUSE, true) }
        pauseIndicator = Indicator(x + 93, y + 79, CommonComponents.EMPTY)

        resetButton = IconButton(x + 111, y + 85, AllIcons.I_STOP)
        resetButton.withCallback<IconButton> { sendOptionUpdate(ConfigureMECannonPayload.Option.STOP, true) }
        resetIndicator = Indicator(x + 111, y + 79, CommonComponents.EMPTY)
        resetIndicator.state = Indicator.State.RED

        addRenderableWidgets(playButton, playIndicator, pauseButton, pauseIndicator, resetButton, resetIndicator)


        confirmButton = IconButton(x + 180, y + 111, AllIcons.I_CONFIRM)
        confirmButton.withCallback<IconButton> { minecraft!!.player!!.closeContainer() }
        addRenderableWidget(confirmButton)


        showSettingsButton = IconButton(x + 8, y + 111, AllIcons.I_PLACEMENT_SETTINGS)
        showSettingsButton.withCallback<IconButton> {
            showSettingsIndicator.state = if (placementSettingsHidden()) Indicator.State.GREEN else Indicator.State.OFF
            initPlacementSettings()
        }
        showSettingsButton.setToolTip(CreateLang.translateDirect(_showSettings))
        addRenderableWidget(showSettingsButton)

        showSettingsIndicator = Indicator(x + 9, y + 111, CommonComponents.EMPTY)


        viewMaterialsButton = IconButton(x + 154, y + 19, AllIcons.I_VIEW_SCHEDULE)
        viewMaterialsButton.withCallback<IconButton> {
            showingMaterials = !showingMaterials
            if (showingMaterials) {
                PacketDistributor.sendToServer(RequestCannonMaterialsPayload(0, true))
            }
        }
        viewMaterialsButton.setToolTip(Component.translatable("gui.appliedcreate.me_blueprint_cannon.viewMaterials"))
        addRenderableWidget(viewMaterialsButton)


        val upgradePanelX = x + BG_TOP.width
        val upgradePanelY = y
        val upgradePanelW = UPGRADE_PADDING * 2 + UPGRADE_SLOT_SIZE
        val upgradePanelH = UPGRADE_PADDING * 2 + UPGRADE_SLOTS * UPGRADE_SLOT_SIZE
        extraAreas = listOf(
            Rect2i(upgradePanelX, upgradePanelY, upgradePanelW, upgradePanelH)
        )
        tick()
    }

    private fun initPlacementSettings() {
        removeWidgets(placementSettingWidgets)
        placementSettingWidgets.clear()

        if (placementSettingsHidden()) return

        val x = leftPos
        val y = topPos


        replaceLevelButtons = Vector(4)
        replaceLevelIndicators = Vector(4)
        val icons = listOf(AllIcons.I_DONT_REPLACE, AllIcons.I_REPLACE_SOLID, AllIcons.I_REPLACE_ANY, AllIcons.I_REPLACE_EMPTY)
        val toolTips = listOf(
            CreateLang.translateDirect("gui.schematicannon.option.dontReplaceSolid"),
            CreateLang.translateDirect("gui.schematicannon.option.replaceWithSolid"),
            CreateLang.translateDirect("gui.schematicannon.option.replaceWithAny"),
            CreateLang.translateDirect("gui.schematicannon.option.replaceWithEmpty")
        )

        val options = listOf(
            ConfigureMECannonPayload.Option.DONT_REPLACE,
            ConfigureMECannonPayload.Option.REPLACE_SOLID,
            ConfigureMECannonPayload.Option.REPLACE_ANY,
            ConfigureMECannonPayload.Option.REPLACE_EMPTY
        )

        for (i in 0..3) {
            replaceLevelIndicators.add(Indicator(x + 33 + i * 18, y + 111, CommonComponents.EMPTY))
            val replaceLevelButton = IconButton(x + 33 + i * 18, y + 111, icons[i])
            val replaceMode = i
            replaceLevelButton.withCallback<IconButton> {
                if (menu.contentHolder.replaceMode != replaceMode) {
                    sendOptionUpdate(options[replaceMode], true)
                }
            }
            replaceLevelButton.setToolTip(toolTips[i])
            replaceLevelButtons.add(replaceLevelButton)
        }
        placementSettingWidgets.addAll(replaceLevelButtons)


        skipMissingButton = IconButton(x + 111, y + 111, AllIcons.I_SKIP_MISSING)
        skipMissingButton.withCallback<IconButton> {
            sendOptionUpdate(ConfigureMECannonPayload.Option.SKIP_MISSING, !menu.contentHolder.skipMissing)
        }
        skipMissingButton.setToolTip(CreateLang.translateDirect("gui.schematicannon.option.skipMissing"))
        skipMissingIndicator = Indicator(x + 111, y + 111, CommonComponents.EMPTY)
        placementSettingWidgets.add(skipMissingButton)


        skipBlockEntitiesButton = IconButton(x + 135, y + 111, AllIcons.I_SKIP_BLOCK_ENTITIES)
        skipBlockEntitiesButton.withCallback<IconButton> {
            sendOptionUpdate(ConfigureMECannonPayload.Option.SKIP_BLOCK_ENTITIES, !menu.contentHolder.replaceBlockEntities)
        }
        skipBlockEntitiesButton.setToolTip(CreateLang.translateDirect("gui.schematicannon.option.skipBlockEntities"))
        skipBlockEntitiesIndicator = Indicator(x + 129, y + 111, CommonComponents.EMPTY)
        placementSettingWidgets.add(skipBlockEntitiesButton)

        addRenderableWidgets(placementSettingWidgets)
    }

    private fun placementSettingsHidden(): Boolean {
        return showSettingsIndicator.state == Indicator.State.OFF
    }

    override fun containerTick() {
        super.containerTick()

        val be = menu.contentHolder

        if (!placementSettingsHidden()) {
            for (replaceMode in 0 until replaceLevelButtons.size) {
                replaceLevelButtons[replaceMode].green = replaceMode == be.replaceMode
                replaceLevelIndicators[replaceMode].state = if (replaceMode == be.replaceMode) Indicator.State.ON else Indicator.State.OFF
            }
            skipMissingButton.green = be.skipMissing
            skipBlockEntitiesButton.green = !be.replaceBlockEntities
        }

        playIndicator.state = Indicator.State.OFF
        pauseIndicator.state = Indicator.State.OFF
        resetIndicator.state = Indicator.State.OFF

        when (be.state) {
            SchematicannonBlockEntity.State.PAUSED -> {
                pauseIndicator.state = Indicator.State.YELLOW
                playButton.active = true
                pauseButton.active = false
                resetButton.active = true
            }
            SchematicannonBlockEntity.State.RUNNING -> {
                playIndicator.state = Indicator.State.GREEN
                playButton.active = false
                pauseButton.active = true
                resetButton.active = true
            }
            SchematicannonBlockEntity.State.STOPPED -> {
                resetIndicator.state = Indicator.State.RED
                playButton.active = true
                pauseButton.active = false
                resetButton.active = false
            }
        }

        handleTooltips()
    }

    private fun handleTooltips() {
        if (placementSettingsHidden()) return

        for (w in placementSettingWidgets) {
            if (w is IconButton) {
                if (!w.toolTip.isEmpty()) {
                    val first = w.toolTip[0]
                    w.toolTip.clear()
                    w.toolTip.add(first)
                    w.toolTip.add(TooltipHelper.holdShift(FontHelper.Palette.BLUE, hasShiftDown()))
                }
            }
        }

        if (hasShiftDown()) {
            fillToolTip(skipMissingButton, skipMissingIndicator, "skipMissing")
            fillToolTip(skipBlockEntitiesButton, skipBlockEntitiesIndicator, "skipBlockEntities")
            fillToolTip(replaceLevelButtons[0], replaceLevelIndicators[0], "dontReplaceSolid")
            fillToolTip(replaceLevelButtons[1], replaceLevelIndicators[1], "replaceWithSolid")
            fillToolTip(replaceLevelButtons[2], replaceLevelIndicators[2], "replaceWithAny")
            fillToolTip(replaceLevelButtons[3], replaceLevelIndicators[3], "replaceWithEmpty")
        }
    }

    private fun fillToolTip(button: IconButton, indicator: Indicator, tooltipKey: String) {
        if (!button.isHovered) return
        val enabled = button.green
        val tip = button.toolTip
        tip.add((if (enabled) optionEnabled else optionDisabled).plainCopy().withStyle(if (enabled) ChatFormatting.DARK_GREEN else ChatFormatting.RED))
        tip.addAll(TooltipHelper.cutTextComponent(CreateLang.translateDirect("gui.schematicannon.option.$tooltipKey.description"), FontHelper.Palette.ALL_GRAY))
    }

    override fun renderBg(graphics: GuiGraphics, partialTicks: Float, mouseX: Int, mouseY: Int) {
        val invX = getLeftOfCentered(AllGuiTextures.PLAYER_INVENTORY.width)
        val invY = topPos + BG_TOP.height + BG_BOTTOM.height + 2
        renderPlayerInventory(graphics, invX, invY)

        val x = leftPos
        val y = topPos


        BG_TOP.render(graphics, x, y)
        BG_BOTTOM.render(graphics, x, y + BG_TOP.height)


        val bgColor = 0xFFd3d3d3.toInt()
        graphics.fill(x + 13, y + 17, x + 33, y + 37, bgColor)
        graphics.fill(x + 34, y + 17, x + 85, y + 37, bgColor)


        graphics.fill(x + 112, y + 17, x + 153, y + 37, bgColor)



        val be = menu.contentHolder


        renderPrintingProgress(graphics, x, y, be.schematicProgress)


        if (!be.inventory.getStackInSlot(0).isEmpty) {
            renderBlueprintHighlight(graphics, x, y)
        }


        renderUpgradePanel(graphics, x + BG_TOP.width, y)


        graphics.drawString(font, title, x + (BG_TOP.width - 8 - font.width(title)) / 2, y + 2, 0x505050, false)


        val msg = if (be.statusMsg in setOf("noAEPower", "crafting", "outputFull", "unsafeData", "finishing"))
            Component.translatable("gui.appliedcreate.me_blueprint_cannon.status.${be.statusMsg}")
        else CreateLang.translateDirect("schematicannon.status." + be.statusMsg)
        var stringWidth = font.width(msg)

        if (be.missingItem != null) {
            stringWidth += 16
            GuiGameElement.of(be.missingItem!!)
                .at<GuiGameElement.GuiRenderBuilder>((x + 128).toFloat(), (y + 49).toFloat(), 100f)
                .scale(1.0)
                .render(graphics)
        }

        graphics.drawString(font, msg, x + 103 - stringWidth / 2, y + 53, 0xDDEEFF)

        if ("schematicErrored" == be.statusMsg) {
            val errorMsg = CreateLang.translateDirect("schematicannon.status.schematicErroredCheckLogs")
            graphics.drawString(font, errorMsg, x + 103 - font.width(errorMsg) / 2, y + 65, 0xDDEEFF)
        }


    }

    private fun renderUpgradePanel(graphics: GuiGraphics, panelX: Int, panelY: Int) {
        val w = UPGRADE_PADDING * 2 + UPGRADE_SLOT_SIZE
        val h = UPGRADE_PADDING * 2 + UPGRADE_SLOTS * UPGRADE_SLOT_SIZE

        // Panel background (MC standard grey)
        graphics.fill(panelX, panelY, panelX + w, panelY + h, 0xFFC6C6C6.toInt())

        // Outer dark border
        graphics.fill(panelX, panelY, panelX + w, panelY + 1, 0xFF373737.toInt())
        graphics.fill(panelX, panelY, panelX + 1, panelY + h, 0xFF373737.toInt())
        graphics.fill(panelX + w - 1, panelY, panelX + w, panelY + h, 0xFF373737.toInt())
        graphics.fill(panelX, panelY + h - 1, panelX + w, panelY + h, 0xFF373737.toInt())

        // 3D inner border
        graphics.fill(panelX + 1, panelY + 1, panelX + w - 1, panelY + 2, 0xFFFFFFFF.toInt())
        graphics.fill(panelX + 1, panelY + 1, panelX + 2, panelY + h - 1, 0xFFFFFFFF.toInt())
        graphics.fill(panelX + w - 2, panelY + 1, panelX + w - 1, panelY + h - 1, 0xFF555555.toInt())
        graphics.fill(panelX + 1, panelY + h - 2, panelX + w - 1, panelY + h - 1, 0xFF555555.toInt())

        // Slot backgrounds
        for (i in 0 until UPGRADE_SLOTS) {
            val slotX = panelX + UPGRADE_PADDING
            val slotY = panelY + UPGRADE_PADDING + i * UPGRADE_SLOT_SIZE
            graphics.fill(slotX, slotY, slotX + UPGRADE_SLOT_SIZE, slotY + UPGRADE_SLOT_SIZE, 0xFF8B8B8B.toInt())
            graphics.fill(slotX, slotY, slotX + UPGRADE_SLOT_SIZE, slotY + 1, 0xFF373737.toInt())
            graphics.fill(slotX, slotY, slotX + 1, slotY + UPGRADE_SLOT_SIZE, 0xFF373737.toInt())
            graphics.fill(slotX, slotY + UPGRADE_SLOT_SIZE - 1, slotX + UPGRADE_SLOT_SIZE, slotY + UPGRADE_SLOT_SIZE, 0xFFFFFFFF.toInt())
            graphics.fill(slotX + UPGRADE_SLOT_SIZE - 1, slotY, slotX + UPGRADE_SLOT_SIZE, slotY + UPGRADE_SLOT_SIZE, 0xFFFFFFFF.toInt())
            // Fix corners
            graphics.fill(slotX + 1, slotY + UPGRADE_SLOT_SIZE - 1, slotX + 2, slotY + UPGRADE_SLOT_SIZE, 0xFF8B8B8B.toInt())
            graphics.fill(slotX + UPGRADE_SLOT_SIZE - 1, slotY + 1, slotX + UPGRADE_SLOT_SIZE, slotY + 2, 0xFF8B8B8B.toInt())
        }
    }

    private fun renderMaterialChecklist(graphics: GuiGraphics, guiX: Int, guiY: Int) {
        val snapshot = menu.materialSnapshot
        val x = guiX + 5
        val y = guiY + 15
        graphics.fill(x, y, x + 204, y + 127, 0xFA20242B.toInt())
        graphics.drawString(font, Component.translatable("gui.appliedcreate.me_blueprint_cannon.materials"), x + 4, y + 4, 0xFFFFFF)
        graphics.drawString(font, "×", x + 193, y + 4, 0xFFFFFF)
        if (snapshot == null || snapshot.loading) {
            graphics.drawString(font, Component.translatable("gui.appliedcreate.me_blueprint_cannon.loading"), x + 4, y + 24, 0xAAAAAA)
            return
        }
        for ((index, row) in snapshot.rows.withIndex()) {
            val rowY = y + 16 + index * 13
            graphics.pose().pushPose()
            graphics.pose().translate((x + 4).toFloat(), rowY.toFloat(), 0f)
            graphics.pose().scale(0.75f, 0.75f, 1f)
            graphics.renderItem(row.stack, 0, 0)
            graphics.pose().popPose()
            val amount = "${row.available}/${row.required}" + if (row.damage) "*" else ""
            val text = font.plainSubstrByWidth(row.stack.hoverName.string, 166 - font.width(amount))
            graphics.drawString(font, text, x + 18, rowY + 2, 0xDDDDDD)
            graphics.drawString(font, amount, x + 198 - font.width(amount), rowY + 2,
                if (row.available >= row.required) 0x55FF55 else 0xFF7777)
        }
        if (snapshot.rows.isEmpty()) graphics.drawString(font,
            Component.translatable("gui.appliedcreate.me_blueprint_cannon.noMaterials"), x + 4, y + 25, 0xAAAAAA)
        val footer = "‹  ${snapshot.page + 1}/${snapshot.pages}  ›"
        graphics.drawString(font, footer, x + 76, y + 114, 0xFFFFFF)
        graphics.drawString(font, "↻", x + 187, y + 114, 0xFFFFFF)
        if (snapshot.incomplete) graphics.drawString(font, "!", x + 4, y + 114, 0xFFAA00)
    }

    private fun renderBlueprintHighlight(graphics: GuiGraphics, x: Int, y: Int) {
        AllGuiTextures.SCHEMATICANNON_HIGHLIGHT.render(graphics, x + 10, y + 60)
    }

    private fun renderPrintingProgress(graphics: GuiGraphics, x: Int, y: Int, progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        val sprite = AllGuiTextures.SCHEMATICANNON_PROGRESS
        graphics.blit(sprite.location, x + 44, y + 64, sprite.startX, sprite.startY, (sprite.width * p).toInt(), sprite.height)
    }

    override fun renderForeground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        val be = menu.contentHolder
        if (showingMaterials) {
            graphics.pose().pushPose()
            graphics.pose().translate(0f, 0f, 400f)
            renderMaterialChecklist(graphics, leftPos, topPos)
            val row = (mouseY - (topPos + 31)) / 13
            val snapshot = menu.materialSnapshot
            if (mouseX in leftPos + 9..leftPos + 203 && mouseY >= topPos + 31 && row in 0..6) {
                snapshot?.rows?.getOrNull(row)?.let { graphics.renderTooltip(font, it.stack, mouseX, mouseY) }
            }
            if (mouseY in topPos + 124..topPos + 142 && mouseX in leftPos + 5..leftPos + 72) {
                graphics.renderTooltip(font, Component.translatable("gui.appliedcreate.me_blueprint_cannon.checklistHelp"), mouseX, mouseY)
            }
            graphics.pose().popPose()
            return
        }

        if (hoveredSlot != null && !hoveredSlot!!.hasItem()) {
            if (hoveredSlot!!.index == 0)
                graphics.renderComponentTooltip(font, TooltipHelper.cutTextComponent(CreateLang.translateDirect(_slotSchematic), FontHelper.Palette.GRAY_AND_BLUE), mouseX, mouseY)
            if (hoveredSlot!!.index in 2..6)
                graphics.renderComponentTooltip(font, TooltipHelper.cutTextComponent(Component.translatable(_slotUpgrades), FontHelper.Palette.GRAY_AND_BLUE), mouseX, mouseY)
        }

        if (be.missingItem != null) {
            val x = leftPos
            val y = topPos
            val missingBlockX = x + 128
            val missingBlockY = y + 49
            if (mouseX >= missingBlockX && mouseY >= missingBlockY && mouseX <= missingBlockX + 16 && mouseY <= missingBlockY + 16) {
                graphics.renderTooltip(font, be.missingItem!!, mouseX, mouseY)
            }
        }

        super.renderForeground(graphics, mouseX, mouseY, partialTicks)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (showingMaterials) {
            if (button == 0) {
                val x = mouseX - leftPos - 5
                val y = mouseY - topPos - 15
                if (x !in 0.0..204.0 || y !in 0.0..127.0 || (x >= 188 && y < 16)) showingMaterials = false
                else if (y >= 109) {
                    val snapshot = menu.materialSnapshot
                    when {
                        x >= 181 -> PacketDistributor.sendToServer(RequestCannonMaterialsPayload(snapshot?.page ?: 0, true))
                        x < 99 -> changeMaterialPage(-1)
                        else -> changeMaterialPage(1)
                    }
                }
            }
            return true // The checklist must never click through into inventory slots or build controls.
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (!showingMaterials) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        if (scrollY != 0.0) changeMaterialPage(if (scrollY > 0) -1 else 1)
        return true
    }

    private fun changeMaterialPage(delta: Int) {
        val snapshot = menu.materialSnapshot ?: return
        val page = (snapshot.page + delta).coerceIn(0, snapshot.pages - 1)
        if (page != snapshot.page) PacketDistributor.sendToServer(RequestCannonMaterialsPayload(page, false))
    }

    private fun sendOptionUpdate(option: ConfigureMECannonPayload.Option, set: Boolean) {
        PacketDistributor.sendToServer(ConfigureMECannonPayload(option, set))
    }

    override fun getExtraAreas(): List<Rect2i> {
        return extraAreas
    }
}

package com.loliball.appliedcreate.cannon

import appeng.api.config.Actionable
import appeng.api.networking.*
import appeng.api.networking.crafting.*
import appeng.api.orientation.BlockOrientation
import appeng.api.stacks.AEItemKey
import appeng.api.stacks.AEKey
import appeng.api.storage.StorageHelper
import appeng.api.upgrades.UpgradeInventories
import appeng.api.util.AECableType
import appeng.core.definitions.AEItems
import appeng.me.helpers.BlockEntityNodeListener
import appeng.me.helpers.IGridConnectedBlockEntity
import appeng.me.helpers.MachineSource
import com.google.common.collect.ImmutableSet
import com.loliball.appliedcreate.AppliedCreate
import com.simibubi.create.AllBlocks
import com.simibubi.create.AllDataComponents
import com.simibubi.create.AllItems
import com.simibubi.create.AllSoundEvents
import com.simibubi.create.content.kinetics.belt.BeltBlock
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity
import com.simibubi.create.content.kinetics.belt.BeltPart
import com.simibubi.create.content.schematics.SchematicPrinter
import com.simibubi.create.content.schematics.cannon.LaunchedItem
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity
import com.simibubi.create.content.schematics.requirement.ItemRequirement
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import com.simibubi.create.infrastructure.config.AllConfigs
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.world.Containers
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.piston.PistonHeadBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BedPart
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.neoforged.neoforge.items.ItemStackHandler
import java.util.EnumSet
import java.util.concurrent.Future

class MEBlueprintCannonBlockEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState) :
    SmartBlockEntity(type, pos, state), MenuProvider, IGridConnectedBlockEntity, ICraftingRequester {
    companion object {
        const val AE_POWER_PER_SHOT = 5.0
        private const val SEARCH_BUDGET = 256
    }

    @Suppress("UNCHECKED_CAST")
    private val mainNode = GridHelper.createManagedNode(
        this, BlockEntityNodeListener.INSTANCE as IGridNodeListener<IGridConnectedBlockEntity>
    ).setVisualRepresentation(AppliedCreate.ME_BLUEPRINT_CANNON_BLOCK.asItem())
        .setInWorldNode(true).setTagName("proxy").setIdlePowerUsage(2.0)
        .setFlags(GridFlags.REQUIRE_CHANNEL).setExposedOnSides(EnumSet.allOf(Direction::class.java))
    private val actionSource = MachineSource(this)
    val upgradeInventory: appeng.api.upgrades.IUpgradeInventory = UpgradeInventories.forMachine(AppliedCreate.ME_BLUEPRINT_CANNON_BLOCK.asItem(), 5) {
        setChanged()
    }
    val inventory = object : ItemStackHandler(2) {
        override fun onContentsChanged(slot: Int) { setChanged(); sendUpdate = true }
        override fun getSlotLimit(slot: Int) = if (slot == 0) 1 else 64
        override fun isItemValid(slot: Int, stack: ItemStack) = slot == 0 && AllItems.SCHEMATIC.isIn(stack)
    }

    val printer = SchematicPrinter()
    var state = SchematicannonBlockEntity.State.STOPPED
    var statusMsg = "idle"
    val flyingBlocks = mutableListOf<LaunchedItem>()
    var missingItem: ItemStack? = null
    var sendUpdate = false
    var previousTarget: BlockPos? = null
    var schematicProgress = 0f
    var blocksPlaced = 0
    var blocksToPlace = 0
    var replaceMode = 2
    var skipMissing = false
    var replaceBlockEntities = false
    var firstRenderTick = false
    var defaultYaw = 0f
    private var runRequested = false
    private var targetPending = false
    private var finishing = false
    private var cooldown = 0
    private var loadedBlueprint = ItemStack.EMPTY
    private var savedPrinter: CompoundTag? = null
    private val returnBuffer = mutableListOf<ItemStack>()
    private var craftingFuture: Future<ICraftingPlan>? = null
    private var craftingLink: ICraftingLink? = null
    private var craftingKey: AEItemKey? = null
    private var craftingRemaining = 0L
    private var craftingRetry = 0

    // Checklist is built incrementally only when requested by an open menu.
    data class Material(val stack: ItemStack, val required: Long, val available: Long, val damage: Boolean)
    var materials: List<Material> = emptyList()
        private set
    var checklistLoading = false
        private set
    var checklistIncomplete = false
        private set
    private var checklistPrinter: SchematicPrinter? = null
    private val checklistCounts = linkedMapOf<Pair<AEItemKey, Boolean>, Long>()
    private val checklistRequirements = linkedMapOf<Pair<AEItemKey, Boolean>, ItemRequirement.StackRequirement>()
    private var checklistTargets = 0
    private var checklistBasePlaced = 0

    init { mainNode.addService(ICraftingRequester::class.java, this) }
    override fun getMainNode(): IManagedGridNode = mainNode
    override fun getGridConnectableSides(orientation: BlockOrientation): Set<Direction> = EnumSet.allOf(Direction::class.java)
    override fun saveChanges() = setChanged()
    override fun onMainNodeStateChanged(reason: IGridNodeListener.State) { sendUpdate = true }
    override fun getCableConnectionType(dir: Direction) = AECableType.SMART
    override fun getActionableNode(): IGridNode? = mainNode.node
    override fun getGridNode(dir: Direction): IGridNode? = mainNode.node
    override fun addBehaviours(behaviours: MutableList<BlockEntityBehaviour>) {}
    override fun initialize() { super.initialize(); if (level?.isClientSide == false && mainNode.node == null) mainNode.create(level, worldPosition) }
    override fun onChunkUnloaded() {
        craftingFuture?.cancel(true); craftingFuture = null
        mainNode.destroy(); super.onChunkUnloaded()
    }
    override fun remove() { mainNode.destroy(); super.remove() }

    override fun getRequestedJobs(): ImmutableSet<ICraftingLink> = craftingLink?.let { ImmutableSet.of(it) } ?: ImmutableSet.of()
    override fun insertCraftedItems(link: ICraftingLink, what: AEKey, amount: Long, mode: Actionable): Long {
        if (link !== craftingLink || what != craftingKey || what !is AEItemKey || amount <= 0) return 0
        val accepted = minOf(amount, craftingRemaining)
        if (mode == Actionable.MODULATE && accepted > 0) {
            addBuffered(what, accepted)
            craftingRemaining -= accepted
            setChanged()
        }
        return accepted
    }
    override fun jobStateChange(link: ICraftingLink) {
        if (craftingLink === link && (link.isDone || link.isCanceled)) {
            craftingLink = null; craftingKey = null; craftingRemaining = 0
        }
        setChanged()
    }
    private fun cancelCrafting() {
        craftingFuture?.cancel(true); craftingFuture = null
        craftingLink?.cancel(); craftingLink = null
        craftingKey = null; craftingRemaining = 0; craftingRetry = 0
        setChanged()
    }
    private fun requestCrafting(missing: ItemStack) {
        if (upgradeInventory.getInstalledUpgrades(AEItems.CRAFTING_CARD) == 0) return
        val grid = mainNode.grid ?: return
        val key = AEItemKey.of(missing) ?: return
        if (craftingKey != null && craftingKey != key) cancelCrafting()
        if (craftingLink?.let { it.isDone || it.isCanceled } == true) {
            craftingLink = null; craftingKey = null
        }
        if (craftingLink != null) { setStatus("crafting", SchematicannonBlockEntity.State.PAUSED); return }
        val future = craftingFuture
        if (future != null) {
            if (!future.isDone) { setStatus("crafting", SchematicannonBlockEntity.State.PAUSED); return }
            try {
                val result = grid.craftingService.submitJob(future.get(), this, null, false, actionSource)
                craftingFuture = null
                if (result.successful()) {
                    craftingLink = result.link()
                    setStatus("crafting", SchematicannonBlockEntity.State.PAUSED)
                } else { craftingKey = null; craftingRetry = 40 }
            } catch (e: Exception) {
                if (e is InterruptedException) Thread.currentThread().interrupt()
                craftingFuture = null; craftingKey = null; craftingRetry = 40
            }
            setChanged()
        } else if (craftingRetry-- <= 0 && grid.craftingService.isCraftable(key)) {
            craftingKey = key; craftingRemaining = missing.count.toLong()
            craftingFuture = grid.craftingService.beginCraftingCalculation(level, { actionSource }, key,
                craftingRemaining, CalculationStrategy.CRAFT_LESS)
            setStatus("crafting", SchematicannonBlockEntity.State.PAUSED)
        }
    }

    fun startPrinting() {
        runRequested = true; cooldown = 0
        setStatus("running", SchematicannonBlockEntity.State.RUNNING)
    }
    fun pausePrinting() { runRequested = false; setStatus("paused", SchematicannonBlockEntity.State.PAUSED) }
    fun stopPrinting() {
        runRequested = false; cancelCrafting(); resetPrinter()
        setStatus("stopped", SchematicannonBlockEntity.State.STOPPED)
    }
    private fun setStatus(message: String, newState: SchematicannonBlockEntity.State = state) {
        if (message != statusMsg || newState != state) { statusMsg = message; state = newState; sendUpdate = true; setChanged() }
    }

    override fun tick() {
        super.tick()
        val world = level ?: return
        firstRenderTick = true
        previousTarget = printer.currentTarget
        val iterator = flyingBlocks.iterator()
        while (iterator.hasNext()) {
            val shot = iterator.next()
            // An unloaded target must never be force-loaded or placed into by a projectile.
            if (!world.isClientSide && !world.isLoaded(shot.target)) continue
            if (shot.update(world)) { iterator.remove(); sendUpdate = true }
            if (!world.isClientSide) setChanged()
        }
        if (world.isClientSide) return
        if (upgradeInventory.getInstalledUpgrades(AEItems.CRAFTING_CARD) == 0 && (craftingFuture != null || craftingLink != null)) cancelCrafting()
        tickChecklist()
        val blueprint = inventory.getStackInSlot(0)
        if (!ItemStack.matches(loadedBlueprint, blueprint)) {
            val requested = runRequested
            cancelCrafting(); resetPrinter()
            loadedBlueprint = blueprint.copy()
            runRequested = requested
            if (blueprint.isEmpty) { runRequested = false; setStatus("idle", SchematicannonBlockEntity.State.STOPPED) }
            else initializePrinter()
        } else if (!blueprint.isEmpty && !printer.isLoaded && (savedPrinter != null || runRequested)) {
            initializePrinter()
        }
        if (!runRequested || !printer.isLoaded) {
            flushBuffer()
        } else if (cooldown > 0) {
            cooldown--
        } else {
            for (ignored in 0 until SEARCH_BUDGET) if (!tickPrinter()) break
        }
        schematicProgress = if (statusMsg == "finished") 1f else if (blocksToPlace > 0)
            (blocksPlaced.toFloat() / blocksToPlace).coerceIn(0f, 1f) else 0f
        if (sendUpdate) { sendUpdate = false; notifyUpdate() }
    }

    /** true means only a target was skipped, allowing a bounded search for the next target this tick. */
    private fun tickPrinter(): Boolean {
        val world = level ?: return false
        if (finishing) {
            if (flyingBlocks.isNotEmpty()) { setStatus("finishing"); return false }
            finishPrinting(); return false
        }
        if (!mainNode.isActive) { setStatus("noAEPower", SchematicannonBlockEntity.State.PAUSED); cooldown = 10; return false }
        val output = inventory.getStackInSlot(1)
        if (!output.isEmpty && (!AllItems.EMPTY_SCHEMATIC.isIn(output) || output.count >= output.maxStackSize)) {
            setStatus("outputFull", SchematicannonBlockEntity.State.PAUSED); cooldown = 10; return false
        }
        if (!targetPending) {
            if (!printer.advanceCurrentPos()) { finishing = true; setChanged(); return false }
            targetPending = true
            setChanged()
        }
        val target = printer.currentTarget
        if (!world.isLoaded(target)) { setStatus("targetNotLoaded", SchematicannonBlockEntity.State.PAUSED); cooldown = 10; return false }
        if (!printer.shouldPlaceCurrent(world, ::shouldPlace)) { targetPending = false; return true }

        var prepared: SafeAe2Schematic.Prepared? = null
        var blockData: CompoundTag? = null
        var entityRequirement: ItemRequirement? = null
        val requirement = try {
            printer.handleCurrentTarget({ _, blockState, be ->
                prepared = prepareAe(blockState, be)
                blockData = prepared?.data() ?: if (!AllBlocks.BELT.has(blockState))
                    CreateSchematicCompat.prepare(world, blockState, be) else null
            }, { _, entity -> entityRequirement = CannonEntityPreparation.prepare(entity) })
            prepared?.requirement() ?: entityRequirement ?: printer.currentRequirement
        } catch (e: RuntimeException) {
            // Invalid/unknown item components cannot turn into items in the target world.
            setStatus("unsafeData", SchematicannonBlockEntity.State.PAUSED)
            runRequested = false; return false
        }
        if (requirement.isInvalid) { targetPending = false; return true }
        val grid = mainNode.grid ?: return false
        val payment = CannonMaterialTransaction.pay(requirement, grid.storageService.inventory,
            grid.energyService, actionSource, returnBuffer, AE_POWER_PER_SHOT)
        setChanged()
        if (!payment.paid()) {
            if (payment.powerMissing()) {
                setStatus("noAEPower", SchematicannonBlockEntity.State.PAUSED)
            } else if (skipMissing) {
                cancelCrafting(); missingItem = null; targetPending = false
                setStatus("skipping", SchematicannonBlockEntity.State.RUNNING)
                return true
            } else {
                missingItem = payment.missing().copy()
                setStatus("missingBlock", SchematicannonBlockEntity.State.PAUSED)
                requestCrafting(payment.missing())
            }
            cooldown = 10; sendUpdate = true; return false
        }
        cancelCrafting()
        val icon = requirement.requiredItems.firstOrNull()?.stack ?: ItemStack.EMPTY
        printer.handleCurrentTarget({ pos, blockState, be ->
            launchBlockOrBelt(pos, icon, blockState, be, blockData)
            setStatus(if (blockState.isAir) "clearing" else "placing", SchematicannonBlockEntity.State.RUNNING)
        }, { pos, entity ->
            flyingBlocks.add(LaunchedItem.ForEntity(blockPos, pos, icon.copy(), entity)); blocksPlaced++
            setStatus("placing", SchematicannonBlockEntity.State.RUNNING)
        })
        AllSoundEvents.SCHEMATICANNON_LAUNCH_BLOCK.playOnServer(world, worldPosition)
        targetPending = false; missingItem = null; sendUpdate = true
        cooldown = maxOf(0, AllConfigs.server().schematics.schematicannonDelay.get() /
            (1 shl upgradeInventory.getInstalledUpgrades(AEItems.SPEED_CARD)) - 1)
        flushBuffer()
        return false
    }

    private fun prepareAe(blockState: BlockState, be: BlockEntity?): SafeAe2Schematic.Prepared? =
        SafeAe2Schematic.prepare(blockState, be, loadedBlueprint.getOrDefault(AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE),
            loadedBlueprint.getOrDefault(AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE))

    private fun initializePrinter() {
        val blueprint = inventory.getStackInSlot(0)
        val error = when {
            !blueprint.has(AllDataComponents.SCHEMATIC_ANCHOR) -> "schematicInvalid"
            !blueprint.getOrDefault(AllDataComponents.SCHEMATIC_DEPLOYED, false) -> "schematicNotPlaced"
            !blueprint.get(AllDataComponents.SCHEMATIC_ANCHOR)!!.closerThan(blockPos, SchematicannonBlockEntity.MAX_ANCHOR_DISTANCE.toDouble()) -> "targetOutsideRange"
            else -> null
        }
        if (error != null) { runRequested = false; setStatus(error, SchematicannonBlockEntity.State.STOPPED); return }
        try {
            printer.loadSchematic(blueprint, level, true)
            if (printer.isErrored || printer.isWorldEmpty) {
                val message = if (printer.isErrored) "schematicErrored" else "schematicExpired"
                printer.resetSchematic(); runRequested = false; setStatus(message, SchematicannonBlockEntity.State.STOPPED); return
            }
            savedPrinter?.let { printer.fromTag(it, false) }
            savedPrinter = null
            if (!runRequested) setStatus("ready", SchematicannonBlockEntity.State.PAUSED)
            requestChecklist()
            sendUpdate = true
        } catch (e: RuntimeException) {
            printer.resetSchematic(); savedPrinter = null; runRequested = false
            setStatus("unsafeData", SchematicannonBlockEntity.State.STOPPED)
        }
    }

    private fun shouldPlace(pos: BlockPos, desired: BlockState, be: BlockEntity?, existing: BlockState,
                            other: BlockState?, solid: Boolean): Boolean {
        if (pos.closerThan(blockPos, 2.0) || level!!.isOutsideBuildHeight(pos)) return false
        if (!replaceBlockEntities && (existing.hasBlockEntity() || other?.hasBlockEntity() == true)) return false
        if (desired.`is`(Blocks.STRUCTURE_VOID)) return false
        if (desired.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && desired.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return false
        if (desired.hasProperty(BlockStateProperties.BED_PART) && desired.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) return false
        if (desired.block is PistonHeadBlock) return false
        if (AllBlocks.BELT.has(desired) && desired.getValue(BeltBlock.PART) == BeltPart.MIDDLE) return false
        val clear = !existing.isRedstoneConductor(level!!, pos) && (other == null || !other.isRedstoneConductor(level!!, pos))
        return when (replaceMode) { 3 -> true; 2 -> !desired.isAir; 1 -> !desired.isAir && (solid || clear); else -> !desired.isAir && clear }
    }

    private fun launchBlockOrBelt(target: BlockPos, icon: ItemStack, original: BlockState, be: BlockEntity?, data: CompoundTag?) {
        if (AllBlocks.BELT.has(original)) {
            val stripped = SchematicannonBlockEntity.stripBeltIfNotLast(original)
            if (be is BeltBlockEntity && AllBlocks.BELT.has(stripped)) {
                val casings = Array(be.beltLength) { BeltBlockEntity.CasingType.NONE }
                var cursor = target
                for (i in casings.indices) {
                    val segment = be.level!!.getBlockState(cursor)
                    val segmentBe = be.level!!.getBlockEntity(cursor) as? BeltBlockEntity ?: break
                    if (!AllBlocks.BELT.has(segment)) break
                    casings[i] = segmentBe.casing
                    cursor = BeltBlock.nextSegmentPosition(segment, cursor, stripped.getValue(BeltBlock.PART) != BeltPart.END) ?: break
                }
                flyingBlocks.add(LaunchedItem.ForBelt(blockPos, target, AllItems.BELT_CONNECTOR.asStack(), stripped, casings))
                blocksPlaced++
            } else if (!stripped.isAir) {
                flyingBlocks.add(LaunchedItem.ForBlockState(blockPos, target, icon.copy(), stripped, null)); blocksPlaced++
            }
        } else {
            flyingBlocks.add(LaunchedItem.ForBlockState(blockPos, target, icon.copy(), original, data))
            blocksPlaced++
        }
    }
    private fun finishPrinting() {
        val output = inventory.getStackInSlot(1)
        if (!output.isEmpty && (!AllItems.EMPTY_SCHEMATIC.isIn(output) || output.count >= output.maxStackSize)) {
            setStatus("outputFull", SchematicannonBlockEntity.State.PAUSED); return
        }
        if (replaceMode == 3) printer.sendBlockUpdates(level)
        inventory.setStackInSlot(0, ItemStack.EMPTY)
        inventory.setStackInSlot(1, AllItems.EMPTY_SCHEMATIC.asStack(output.count + 1))
        loadedBlueprint = ItemStack.EMPTY
        runRequested = false; resetPrinter()
        setStatus("finished", SchematicannonBlockEntity.State.STOPPED)
        AllSoundEvents.SCHEMATICANNON_FINISH.playOnServer(level, worldPosition)
    }
    private fun resetPrinter() {
        printer.resetSchematic(); savedPrinter = null; missingItem = null
        targetPending = false; finishing = false; cooldown = 0
        blocksPlaced = 0; blocksToPlace = 0; schematicProgress = 0f
        checklistPrinter = null; checklistLoading = false; materials = emptyList(); checklistCounts.clear(); checklistRequirements.clear()
        sendUpdate = true; setChanged()
    }

    fun requestChecklist() {
        if (level?.isClientSide != false || inventory.getStackInSlot(0).isEmpty) return
        if (checklistLoading) return
        try {
            val scanner = SchematicPrinter()
            scanner.loadSchematic(inventory.getStackInSlot(0), level, true)
            if (!scanner.isLoaded || scanner.isErrored || scanner.isWorldEmpty) return
            checklistCounts.clear(); checklistRequirements.clear(); checklistTargets = 0; checklistBasePlaced = blocksPlaced
            checklistIncomplete = false; checklistLoading = true; checklistPrinter = scanner
        } catch (e: RuntimeException) { checklistIncomplete = true; checklistLoading = false }
    }
    private fun tickChecklist() {
        val scanner = checklistPrinter ?: return
        for (i in 0 until SEARCH_BUDGET) {
            if (!scanner.advanceCurrentPos()) {
                materials = checklistCounts.map { (keyAndUse, count) ->
                    val (key, damage) = keyAndUse
                    Material(key.toStack(), count, 0, damage)
                }
                blocksToPlace = maxOf(blocksToPlace, checklistBasePlaced + checklistTargets)
                checklistLoading = false; checklistPrinter = null; sendUpdate = true; return
            }
            if (!level!!.isLoaded(scanner.currentTarget)) { checklistIncomplete = true; continue }
            if (!scanner.shouldPlaceCurrent(level, ::shouldPlace) || flyingBlocks.any { it.target == scanner.currentTarget }) continue
            try {
                var prepared: SafeAe2Schematic.Prepared? = null
                var entityRequirement: ItemRequirement? = null
                scanner.handleCurrentTarget({ _, state, be -> prepared = prepareAe(state, be) }, { _, entity -> entityRequirement = CannonEntityPreparation.prepare(entity) })
                val requirement = prepared?.requirement() ?: entityRequirement ?: scanner.currentRequirement
                if (requirement.isInvalid) continue
                checklistTargets++
                for (required in requirement.requiredItems) {
                    val key = AEItemKey.of(required.stack) ?: continue
                    val index = key to (required.usage == ItemRequirement.ItemUseType.DAMAGE)
                    checklistCounts[index] = (checklistCounts[index] ?: 0) + required.stack.count
                    checklistRequirements.putIfAbsent(index, required)
                }
            } catch (e: RuntimeException) { checklistIncomplete = true }
        }
    }

    fun materialPage(page: Int): List<Material> {
        val storage = mainNode.grid?.storageService?.inventory
        val available = storage?.availableStacks
        return materials.drop(page * 7).take(7).map { row ->
            val key = AEItemKey.of(row.stack)!!
            val request = checklistRequirements[key to row.damage]
            var count = 0L
            fun collect(stack: ItemStack, amount: Long) {
                if (!(request?.matches(stack) ?: key.matches(stack))) return
                val units = if (row.damage) (stack.maxDamage - stack.damageValue).coerceAtLeast(0).toLong() else 1L
                count += amount * units
            }
            available?.forEach { entry ->
                val candidate = entry.key as? AEItemKey ?: return@forEach
                val stack = candidate.toStack()
                if (request?.matches(stack) ?: key.matches(stack))
                    collect(stack, storage.extract(candidate, entry.longValue, Actionable.SIMULATE, actionSource))
            }
            returnBuffer.forEach { collect(it, it.count.toLong()) }
            row.copy(available = count)
        }
    }

    private fun addBuffered(key: AEItemKey, count: Long) {
        var left = count
        while (left > 0) { val n = minOf(left, key.maxStackSize.toLong()).toInt(); returnBuffer.add(key.toStack(n)); left -= n }
    }
    private fun flushBuffer() {
        if (!mainNode.isActive) return
        val storage = mainNode.grid?.storageService?.inventory ?: return
        val iterator = returnBuffer.iterator()
        while (iterator.hasNext()) {
            val stack = iterator.next()
            val key = AEItemKey.of(stack)
            if (key == null) { iterator.remove(); continue }
            val accepted = storage.insert(key, stack.count.toLong(), Actionable.MODULATE, actionSource).toInt()
            if (accepted > 0) { stack.shrink(accepted); setChanged() }
            if (stack.isEmpty) iterator.remove()
        }
    }
    fun dropBufferedItems() {
        cancelCrafting()
        for (stack in returnBuffer) Containers.dropItemStack(level!!, blockPos.x.toDouble(), blockPos.y.toDouble(), blockPos.z.toDouble(), stack)
        returnBuffer.clear()
    }

    override fun sendToMenu(buffer: RegistryFriendlyByteBuf) { buffer.writeBlockPos(blockPos); buffer.writeNbt(getUpdateTag(buffer.registryAccess())) }
    override fun createMenu(id: Int, inv: Inventory, player: Player): AbstractContainerMenu = MEBlueprintCannonMenu(id, inv, this)
    override fun getDisplayName(): Component = Component.translatable("block.appliedcreate.me_blueprint_cannon")

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.write(tag, registries, clientPacket)
        if (!clientPacket) mainNode.saveToNBT(tag)
        tag.put("Inventory", inventory.serializeNBT(registries)); upgradeInventory.writeToNBT(tag, "Upgrades", registries)
        tag.putString("Status", statusMsg); tag.putString("State", state.name); tag.putFloat("Progress", schematicProgress)
        tag.putInt("AmountPlaced", blocksPlaced); tag.putInt("AmountToPlace", blocksToPlace)
        tag.putBoolean("RunRequested", runRequested); tag.putBoolean("TargetPending", targetPending); tag.putBoolean("Finishing", finishing)
        tag.putInt("Cooldown", cooldown); tag.putFloat("DefaultYaw", defaultYaw)
        missingItem?.let { tag.put("MissingItem", it.saveOptional(registries)) }
        val options = CompoundTag(); options.putInt("ReplaceMode", replaceMode); options.putBoolean("SkipMissing", skipMissing)
        options.putBoolean("ReplaceTileEntities", replaceBlockEntities); tag.put("Options", options)
        val data = savedPrinter?.copy() ?: CompoundTag().also { printer.write(it) }; tag.put("Printer", data)
        val shots = ListTag(); flyingBlocks.forEach { shots.add(it.serializeNBT(registries)) }; tag.put("FlyingBlocks", shots)
        if (!clientPacket) {
            val returns = ListTag(); returnBuffer.forEach { returns.add(it.saveOptional(registries)) }; tag.put("Returns", returns)
            craftingLink?.let { val link = CompoundTag(); it.writeToNBT(link); tag.put("CraftingLink", link) }
            craftingKey?.let { tag.put("CraftingKey", it.toTag(registries)) }; tag.putLong("CraftingRemaining", craftingRemaining)
        }
    }
    override fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.read(tag, registries, clientPacket)
        if (!clientPacket) mainNode.loadFromNBT(tag)
        inventory.deserializeNBT(registries, tag.getCompound("Inventory")); upgradeInventory.readFromNBT(tag, "Upgrades", registries)
        statusMsg = tag.getString("Status").ifEmpty { "idle" }
        state = SchematicannonBlockEntity.State.entries.find { it.name == tag.getString("State") } ?: SchematicannonBlockEntity.State.STOPPED
        schematicProgress = tag.getFloat("Progress"); blocksPlaced = tag.getInt("AmountPlaced"); blocksToPlace = tag.getInt("AmountToPlace")
        runRequested = if (tag.contains("RunRequested")) tag.getBoolean("RunRequested") else state == SchematicannonBlockEntity.State.RUNNING
        targetPending = tag.getBoolean("TargetPending"); finishing = tag.getBoolean("Finishing"); cooldown = tag.getInt("Cooldown")
        defaultYaw = tag.getFloat("DefaultYaw")
        missingItem = ItemStack.parseOptional(registries, tag.getCompound("MissingItem")).takeUnless { it.isEmpty }
        val options = tag.getCompound("Options")
        replaceMode = if (options.contains("ReplaceMode")) options.getInt("ReplaceMode").coerceIn(0, 3) else 2
        skipMissing = options.getBoolean("SkipMissing"); replaceBlockEntities = options.getBoolean("ReplaceTileEntities")
        printer.resetSchematic()
        val data = tag.getCompound("Printer")
        if (data.contains("PrintStage")) {
            if (clientPacket) printer.fromTag(data, true)
            else if (data.contains("Anchor")) savedPrinter = data.copy()
        }
        loadedBlueprint = inventory.getStackInSlot(0).copy()
        // Full snapshots replace the animation list; repeated packets may never duplicate a paid projectile.
        flyingBlocks.clear()
        for (shot in tag.getList("FlyingBlocks", 10)) flyingBlocks.add(LaunchedItem.fromNBT(shot as CompoundTag, registries, registries.lookupOrThrow(Registries.BLOCK)))
        if (!clientPacket) {
            returnBuffer.clear()
            for (entry in tag.getList("Returns", 10)) ItemStack.parse(registries, entry as CompoundTag).ifPresent { returnBuffer.add(it) }
            craftingKey = if (tag.contains("CraftingKey")) AEItemKey.fromTag(registries, tag.getCompound("CraftingKey")) else null
            craftingRemaining = tag.getLong("CraftingRemaining").coerceAtLeast(0)
            craftingLink = if (tag.contains("CraftingLink")) StorageHelper.loadCraftingLink(tag.getCompound("CraftingLink"), this) else null
        }
    }
}

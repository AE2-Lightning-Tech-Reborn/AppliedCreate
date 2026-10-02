package com.loliball.appliedcreate.cannon

import com.loliball.appliedcreate.AppliedCreate
import com.simibubi.create.AllShapes
import com.simibubi.create.foundation.block.IBE
import com.simibubi.create.foundation.item.ItemHelper
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth

import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

class MEBlueprintCannonBlock(properties: Properties) : Block(properties), IBE<MEBlueprintCannonBlockEntity> {

    init {
        registerDefaultState(defaultBlockState())
    }

    override fun getShape(state: BlockState, worldIn: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return AllShapes.SCHEMATICANNON_SHAPE
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, entity: LivingEntity?, stack: ItemStack) {
        if (entity != null) {
            withBlockEntityDo(level, pos) { be ->
                be.defaultYaw = (-Mth.floor((entity.yRot + (if (entity.isShiftKeyDown) 180.0f else 0.0f)) * 16.0f / 360.0f + 0.5f) and 15) * 360.0f / 16.0f
            }
        }
    }

    override fun useWithoutItem(state: BlockState, worldIn: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        if (worldIn.isClientSide) {
            return InteractionResult.SUCCESS
        }
        withBlockEntityDo(worldIn, pos) { be ->
            (player as ServerPlayer).openMenu(be) { buf -> be.sendToMenu(buf) }
        }
        return InteractionResult.SUCCESS
    }

    override fun onRemove(state: BlockState, worldIn: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        if (!state.hasBlockEntity() || state.block == newState.block) {
            return
        }

        withBlockEntityDo(worldIn, pos) { be ->
            if (worldIn.isClientSide) return@withBlockEntityDo
            // Drop main inventory
            ItemHelper.dropContents(worldIn, pos, be.inventory)
            // Drop upgrades
            ItemHelper.dropContents(worldIn, pos, be.upgradeInventory.toItemHandler())
            be.dropBufferedItems()
        }
        worldIn.removeBlockEntity(pos)
    }

    override fun getBlockEntityClass(): Class<MEBlueprintCannonBlockEntity> {
        return MEBlueprintCannonBlockEntity::class.java
    }

    override fun getBlockEntityType(): BlockEntityType<out MEBlueprintCannonBlockEntity> {
        return AppliedCreate.ME_BLUEPRINT_CANNON_BE.get()
    }
}

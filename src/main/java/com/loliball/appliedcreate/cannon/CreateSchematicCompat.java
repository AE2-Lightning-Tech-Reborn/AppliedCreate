package com.loliball.appliedcreate.cannon;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.simibubi.create.foundation.utility.BlockHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Create 6.0.10 added a Level argument to its schematic data sanitizer. */
public final class CreateSchematicCompat {
    private CreateSchematicCompat() {}

    private static final Method PREPARE = findPrepare();

    private static Method findPrepare() {
        try {
            return BlockHelper.class.getMethod("prepareBlockEntityData", Level.class, BlockState.class, BlockEntity.class);
        } catch (NoSuchMethodException modernMissing) {
            try {
                return BlockHelper.class.getMethod("prepareBlockEntityData", BlockState.class, BlockEntity.class);
            } catch (NoSuchMethodException legacyMissing) {
                throw new ExceptionInInitializerError(legacyMissing);
            }
        }
    }

    public static CompoundTag prepare(Level level, BlockState state, BlockEntity entity) {
        try {
            return (CompoundTag) (PREPARE.getParameterCount() == 3
                    ? PREPARE.invoke(null, level, state, entity) : PREPARE.invoke(null, state, entity));
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException("Create rejected schematic block entity data", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot invoke Create schematic data sanitizer", e);
        }
    }
}

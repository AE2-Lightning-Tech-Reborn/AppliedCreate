package com.loliball.appliedcreate.cannon;

import java.util.ArrayList;

import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import net.createmod.catnip.components.ComponentProcessors;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The entity's actual equipment and its charged equipment must be identical. */
public final class CannonEntityPreparation {
    private CannonEntityPreparation() {}
    private static final EquipmentSlot[] ARMOR_STAND_SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

    public static ItemRequirement prepare(Entity entity) {
        var costs = new ArrayList<ItemRequirement.StackRequirement>();
        if (entity instanceof ItemFrame frame) {
            costs.add(new ItemRequirement.StackRequirement(new ItemStack(frame.getType() == net.minecraft.world.entity.EntityType.GLOW_ITEM_FRAME
                    ? Items.GLOW_ITEM_FRAME : Items.ITEM_FRAME), ItemRequirement.ItemUseType.CONSUME));
            frame.setItem(displayItem(frame.getItem()), false);
            costs.add(new ItemRequirement.StrictNbtStackRequirement(frame.getItem().copy(), ItemRequirement.ItemUseType.CONSUME));
        } else if (entity instanceof ArmorStand stand) {
            costs.add(new ItemRequirement.StackRequirement(new ItemStack(Items.ARMOR_STAND), ItemRequirement.ItemUseType.CONSUME));
            for (var slot : ARMOR_STAND_SLOTS) {
                var stack = displayItem(stand.getItemBySlot(slot));
                stand.setItemSlot(slot, stack);
                costs.add(new ItemRequirement.StrictNbtStackRequirement(stack.copy(), ItemRequirement.ItemUseType.CONSUME));
            }
        } else return ItemRequirement.of(entity);
        return new ItemRequirement(costs);
    }

    private static ItemStack displayItem(ItemStack source) {
        if (source.isEmpty()) return ItemStack.EMPTY;
        // Keep Create's allowed display components, rebased on the item's defaults. Older Catnip
        // sanitizers also removed default stack-size/rarity components, making ordinary items unmatchable.
        var result = new ItemStack(source.getItem(), source.getCount());
        for (var component : source.getComponents()) {
            if (!ComponentProcessors.isUnsafeItemComponent(component.type())) copy(result, component);
        }
        for (var entry : source.getComponentsPatch().entrySet()) {
            if (entry.getValue().isEmpty() && !ComponentProcessors.isUnsafeItemComponent(entry.getKey())) result.remove(entry.getKey());
        }
        return result;
    }

    private static <T> void copy(ItemStack result, TypedDataComponent<T> component) {
        result.set(component.type(), component.value());
    }
}

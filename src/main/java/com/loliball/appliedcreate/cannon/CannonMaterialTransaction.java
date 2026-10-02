package com.loliball.appliedcreate.cannon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import net.minecraft.world.item.ItemStack;

/** A shot is paid as a whole. Failed extraction is kept in the cannon, never silently discarded. */
public final class CannonMaterialTransaction {
    private CannonMaterialTransaction() {}

    public record Result(boolean paid, boolean powerMissing, ItemStack missing, List<ItemStack> consumed) {}
    private record Debit(AEItemKey key, int count, boolean damage) {}

    public static Result pay(ItemRequirement requirement, MEStorage storage, IEnergyService energy,
            IActionSource source, List<ItemStack> buffer, double power) {
        var available = new LinkedHashMap<AEItemKey, Long>();
        for (var entry : storage.getAvailableStacks()) {
            if (entry.getKey() instanceof AEItemKey key && requirement.getRequiredItems().stream()
                    .anyMatch(request -> request.matches(key.toStack()))) {
                available.put(key, storage.extract(key, entry.getLongValue(), Actionable.SIMULATE, source));
            }
        }
        for (var stack : buffer) {
            if (!stack.isEmpty()) available.merge(AEItemKey.of(stack), (long) stack.getCount(), Long::sum);
        }

        var debits = new ArrayList<Debit>();
        // Strict requests go first, so an ordinary item request cannot steal a required component variant.
        var requirements = new ArrayList<>(requirement.getRequiredItems());
        requirements.sort((a, b) -> Boolean.compare(
                b instanceof ItemRequirement.StrictNbtStackRequirement,
                a instanceof ItemRequirement.StrictNbtStackRequirement));
        for (var required : requirements) {
            if (required.stack.isEmpty()) continue;
            boolean damage = required.usage == ItemRequirement.ItemUseType.DAMAGE;
            int left = damage ? 1 : required.stack.getCount();
            for (var entry : available.entrySet()) {
                ItemStack candidate = entry.getKey().toStack();
                if (entry.getValue() <= 0 || !required.matches(candidate)) continue;
                if (damage && (!candidate.isDamageableItem() || candidate.getDamageValue() >= candidate.getMaxDamage())) continue;
                int take = (int) Math.min(left, entry.getValue());
                debits.add(new Debit(entry.getKey(), take, damage));
                entry.setValue(entry.getValue() - take);
                left -= take;
                if (left == 0) break;
            }
            if (left > 0) return new Result(false, false, required.stack.copyWithCount(left), List.of());
        }
        if (energy.extractAEPower(power, Actionable.SIMULATE, PowerMultiplier.CONFIG) + 1e-6 < power) {
            return new Result(false, true, ItemStack.EMPTY, List.of());
        }

        var withdrawn = new ArrayList<ItemStack>();
        for (var debit : debits) {
            int left = debit.count;
            for (var stack : buffer) {
                if (!debit.key.matches(stack)) continue;
                int take = Math.min(left, stack.getCount());
                withdrawn.add(stack.copyWithCount(take));
                stack.shrink(take);
                left -= take;
                if (left == 0) break;
            }
            buffer.removeIf(ItemStack::isEmpty);
            if (left > 0) {
                int actual = (int) storage.extract(debit.key, left, Actionable.MODULATE, source);
                if (actual > 0) withdrawn.add(debit.key.toStack(actual));
                if (actual != left) {
                    buffer.addAll(withdrawn);
                    return new Result(false, false, debit.key.toStack(left - actual), List.of());
                }
            }
        }
        double paid = energy.extractAEPower(power, Actionable.MODULATE, PowerMultiplier.CONFIG);
        if (paid + 1e-6 < power) {
            energy.injectPower(PowerMultiplier.CONFIG.multiply(paid), Actionable.MODULATE);
            buffer.addAll(withdrawn);
            return new Result(false, true, ItemStack.EMPTY, List.of());
        }

        // Damage tools, and return buckets/other crafting remainders only after the entire payment succeeds.
        var consumed = new ArrayList<ItemStack>();
        for (var debit : debits) {
            ItemStack stack = debit.key.toStack(debit.count);
            if (debit.damage) {
                stack.setDamageValue(stack.getDamageValue() + 1);
                if (stack.getDamageValue() < stack.getMaxDamage()) buffer.add(stack);
            } else {
                consumed.add(stack);
                if (stack.hasCraftingRemainingItem()) {
                    for (int i = 0; i < debit.count; i++) buffer.add(stack.getCraftingRemainingItem());
                }
            }
        }
        return new Result(true, false, ItemStack.EMPTY, List.copyOf(consumed));
    }
}

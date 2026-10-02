package com.loliball.appliedcreate.cannon;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.parts.IPart;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.facade.FacadePart;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.util.SettingsFrom;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Never transplant an AE machine's serialized inventory or node from a schematic.
 * Build a fresh machine, import the memory-card configuration allowlist, then account for
 * every physical part/upgrade and the blank patterns required by memory-card restoration.
 */
public final class SafeAe2Schematic {
    private SafeAe2Schematic() {}

    private static final Set<DataComponentType<?>> SETTINGS = Set.of(
            AEComponents.EXPORTED_CUSTOM_NAME, AEComponents.EXPORTED_SETTINGS,
            AEComponents.EXPORTED_PRIORITY, AEComponents.EXPORTED_CONFIG_INV,
            AEComponents.EXPORTED_LEVEL_EMITTER_VALUE, AEComponents.EXPORTED_PUSH_DIRECTION,
            AEComponents.EXPORTED_P2P_FREQUENCY);

    public record Prepared(ItemRequirement requirement, CompoundTag data) {}

    public static Prepared prepare(BlockState state, BlockEntity source, Rotation rotation, Mirror mirror) {
        if (!(source instanceof AEBaseBlockEntity original)) return null;
        if (!(state.getBlock() instanceof EntityBlock factory) || original.getLevel() == null) {
            throw new IllegalArgumentException("Missing AE block factory or schematic world");
        }
        // Import callbacks may update block states. Give them an isolated virtual world, never the real level.
        var scratch = new SchematicLevel(BlockPos.ZERO, original.getLevel());
        scratch.setBlock(original.getBlockPos(), state, 0);
        var fresh = factory.newBlockEntity(original.getBlockPos(), state);
        if (!(fresh instanceof AEBaseBlockEntity destination)) throw new IllegalArgumentException("Invalid AE block");
        destination.setLevel(scratch);
        var cost = new ArrayList<ItemRequirement.StackRequirement>();

        if (original instanceof CableBusBlockEntity from && destination instanceof CableBusBlockEntity to) {
            copyPart(from, to, null, null, cost);
            for (var side : Direction.values()) {
                Direction transformed = rotation.rotate(mirror.mirror(side));
                copyPart(from, to, side, transformed, cost);
                var facade = from.getFacadeContainer().getFacade(side);
                if (facade != null) {
                    var copied = new FacadePart(facade.getBlockState().mirror(mirror).rotate(rotation), transformed);
                    if (!to.getFacadeContainer().addFacade(copied)) throw new IllegalArgumentException("Invalid facade");
                    // A facade's represented block is its material identity, not unrelated item metadata.
                    cost.add(new ItemRequirement.StackRequirement(copied.getItemStack(), ItemRequirement.ItemUseType.CONSUME) {
                        @Override public boolean matches(ItemStack other) {
                            return super.matches(other) && java.util.Objects.equals(stack.get(AEComponents.FACADE_ITEM), other.get(AEComponents.FACADE_ITEM));
                        }
                    });
                }
            }
            if (cost.isEmpty()) throw new IllegalArgumentException("Empty cable bus");
        } else {
            require(cost, new ItemStack(state.getBlock()));
            copySettings(original, destination, cost);
        }

        // Serialize only the freshly reconstructed state. Source root NBT, components, cells,
        // return buffers, crafting jobs, stored power and grid ownership never enter this result.
        return new Prepared(new ItemRequirement(cost), destination.saveWithFullMetadata(scratch.registryAccess()));
    }

    private static void copyPart(CableBusBlockEntity from, CableBusBlockEntity to, Direction side,
            Direction transformed, List<ItemRequirement.StackRequirement> cost) {
        var source = from.getPart(side);
        if (source == null) return;
        var destination = to.addPart(source.getPartItem(), transformed, null);
        if (destination == null) throw new IllegalArgumentException("Incompatible cable bus parts");
        require(cost, new ItemStack(source.getPartItem()));
        copySettings(source, destination, cost);
    }

    public static DataComponentMap configurationOnly(DataComponentMap exported) {
        var safe = DataComponentMap.builder();
        for (var component : exported) {
            var id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type());
            // Only the settings explicitly exported by the object's memory-card implementation reach here.
            // Inventory-bearing AE components are handled and paid separately below.
            if (SETTINGS.contains(component.type()) || (id != null && !id.getNamespace().equals("minecraft")
                    && !id.getNamespace().equals("ae2"))) copyComponent(safe, component);
        }
        return safe.build();
    }

    private static <T> void copyComponent(DataComponentMap.Builder builder, net.minecraft.core.component.TypedDataComponent<T> component) {
        builder.set(component.type(), component.value());
    }

    private static void copySettings(Object source, Object destination, List<ItemRequirement.StackRequirement> cost) {
        var builder = DataComponentMap.builder();
        if (source instanceof IPart part) part.exportSettings(SettingsFrom.MEMORY_CARD, builder);
        else ((AEBaseBlockEntity) source).exportSettings(SettingsFrom.MEMORY_CARD, builder, null);
        var exported = builder.build();
        var safe = configurationOnly(exported);
        if (destination instanceof IPart part) part.importSettings(SettingsFrom.MEMORY_CARD, safe, null);
        else ((AEBaseBlockEntity) destination).importSettings(SettingsFrom.MEMORY_CARD, safe, null);

        // The memory-card API copies upgrade wishes, not paid inventory. Validate and account explicitly.
        var upgradeData = exported.get(AEComponents.EXPORTED_UPGRADES);
        if (upgradeData != null && !upgradeData.upgrades().isEmpty()) {
            if (!(destination instanceof IUpgradeableObject upgradable)) throw new IllegalArgumentException("Unexpected upgrades");
            for (var stack : upgradeData.upgrades()) {
                if (stack.isEmpty()) continue;
                validateComponents(stack);
                if (!upgradable.getUpgrades().addItems(stack.copy()).isEmpty()) {
                    throw new IllegalArgumentException("Invalid upgrade type/count");
                }
                require(cost, stack);
            }
        }

        var patterns = exported.get(AEComponents.EXPORTED_PATTERNS);
        if (patterns != null) {
            if (!(destination instanceof PatternProviderLogicHost host)) throw new IllegalArgumentException("Unexpected pattern inventory");
            var inv = host.getLogic().getPatternInv();
            var contents = patterns.stream().toList();
            for (int slot = 0; slot < contents.size(); slot++) {
                var pattern = contents.get(slot);
                if (pattern.isEmpty()) continue;
                if (slot >= inv.size() || pattern.getCount() != 1) throw new IllegalArgumentException("Invalid pattern slot/count");
                var restored = restorePattern(pattern, host.getBlockEntity());
                inv.setItemDirect(slot, restored);
                require(cost, AEItems.BLANK_PATTERN.stack());
            }
        }
    }

    public static ItemStack restorePattern(ItemStack stack, BlockEntity host) {
        // Validate BEFORE decoding: some decoders preserve the entire original item in their definition.
        validateComponents(stack);
        var details = PatternDetailsHelper.isEncodedPattern(stack) ? PatternDetailsHelper.decodePattern(stack, host.getLevel()) : null;
        if (details == null) {
            throw new IllegalArgumentException("Invalid encoded pattern");
        }
        var result = details.getDefinition().toStack();
        validateComponents(result);
        if (!PatternDetailsHelper.isEncodedPattern(result)) throw new IllegalArgumentException("Decoder returned a non-pattern item");
        return result;
    }

    public static void validateComponents(ItemStack stack) {
        // The policy applies to changes from the item's defaults, not inherent rarity/stack size/etc.
        // custom_data and mod-owned components are deliberately preserved for addon compatibility.
        // This is a namespace policy, not a claim that arbitrary addon data is universally safe.
        for (var entry : stack.getComponentsPatch().entrySet()) {
            var type = entry.getKey();
            var id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            if (id == null || (id.getNamespace().equals("minecraft") && type != DataComponents.CUSTOM_DATA)) {
                throw new IllegalArgumentException("Blocked item component: " + id);
            }
        }
    }

    private static void require(List<ItemRequirement.StackRequirement> cost, ItemStack stack) {
        if (stack.isEmpty() || stack.getCount() > stack.getMaxStackSize()) throw new IllegalArgumentException("Invalid material count");
        cost.add(new ItemRequirement.StackRequirement(stack.copy(), ItemRequirement.ItemUseType.CONSUME));
    }
}

package com.loliball.appliedcreate.cannon;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.*;
import appeng.facade.FacadePart;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.parts.automation.ExportBusPart;
import com.loliball.appliedcreate.AppliedCreate;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.schematics.SchematicItem;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("appliedcreate_cannon")
@PrefixGameTestTemplate(false)
public final class BlueprintCannonGameTests {
    private static final BlockPos CANNON = new BlockPos(2, 2, 2);
    private static final BlockPos TARGET = new BlockPos(6, 2, 2);
    private static final BlockPos SOURCE = new BlockPos(6, 2, 6);
    private static void check(boolean ok, String message) { if (!ok) throw new GameTestAssertException(message); }
    private static MEBlueprintCannonBlockEntity rig(GameTestHelper h, ItemStack... contents) {
        h.setBlock(CANNON.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(CANNON.east(), AEBlocks.DRIVE.block());
        h.setBlock(CANNON, AppliedCreate.Companion.getME_BLUEPRINT_CANNON_BLOCK().get());
        MEBlueprintCannonBlockEntity cannon = h.getBlockEntity(CANNON);
        ItemStack cell = AEItems.ITEM_CELL_64K.stack();
        var inv = StorageCells.getCellInventory(cell, null);
        for (var stack : contents) check(inv.insert(AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE,
                IActionSource.ofMachine(cannon)) == stack.getCount(), "cell seed failed");
        inv.persist();
        DriveBlockEntity drive = h.getBlockEntity(CANNON.east());
        drive.getInternalInventory().setItemDirect(0, cell);
        return cannon;
    }
    private static long count(MEBlueprintCannonBlockEntity be, ItemStack stack) {
        return be.getMainNode().getGrid().getStorageService().getInventory().extract(AEItemKey.of(stack), Long.MAX_VALUE,
                Actionable.SIMULATE, IActionSource.ofMachine(be));
    }
    private static void insert(MEBlueprintCannonBlockEntity be, ItemStack stack) {
        check(be.getMainNode().getGrid().getStorageService().getInventory().insert(AEItemKey.of(stack), stack.getCount(),
                Actionable.MODULATE, IActionSource.ofMachine(be)) == stack.getCount(), "network insert failed");
    }
    private static void blueprint(GameTestHelper h, MEBlueprintCannonBlockEntity cannon, Vec3i size, Rotation rotation) {
        try {
            var structure = new StructureTemplate();
            structure.fillFromWorld(h.getLevel(), h.absolutePos(SOURCE), size, true, Blocks.STRUCTURE_VOID);
            String filename = UUID.randomUUID() + ".nbt";
            Path dir = Path.of("schematics", "uploaded", "appliedcreate_cannon_tests");
            Files.createDirectories(dir);
            NbtIo.writeCompressed(structure.save(new CompoundTag()), dir.resolve(filename));
            ItemStack blueprint = SchematicItem.create(h.getLevel(), filename, "appliedcreate_cannon_tests");
            blueprint.set(AllDataComponents.SCHEMATIC_ANCHOR, h.absolutePos(TARGET));
            blueprint.set(AllDataComponents.SCHEMATIC_DEPLOYED, true);
            blueprint.set(AllDataComponents.SCHEMATIC_ROTATION, rotation);
            cannon.getInventory().setStackInSlot(0, blueprint);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private static void blueprint(GameTestHelper h, MEBlueprintCannonBlockEntity cannon, BlockState... states) {
        for (int i = 0; i < states.length; i++) h.setBlock(SOURCE.east(i), states[i]);
        blueprint(h, cannon, new Vec3i(states.length, 1, 1), Rotation.NONE);
    }
    private static void finished(MEBlueprintCannonBlockEntity cannon) {
        check(cannon.getInventory().getStackInSlot(0).isEmpty(), "blueprint not completed: " + cannon.getStatusMsg());
        check(AllItems.EMPTY_SCHEMATIC.isIn(cannon.getInventory().getStackInSlot(1)), "missing output blueprint");
    }
    private static ItemStack pattern(Item in, Item out) {
        return PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(in), 1)),
                List.of(new GenericStack(AEItemKey.of(out), 1)));
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void ordinaryBlocksConsumeExactCounts(GameTestHelper h) {
        var be = rig(h, new ItemStack(Items.STONE, 3));
        blueprint(h, be, Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState());
        h.runAfterDelay(40, be::startPrinting);
        h.runAfterDelay(130, () -> {
            finished(be);
            for (int i = 0; i < 3; i++) h.assertBlockPresent(Blocks.STONE, TARGET.east(i));
            check(count(be, new ItemStack(Items.STONE)) == 0, "wrong material debit");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void missingMaterialPauseIsExplicitAndResumesSameTarget(GameTestHelper h) {
        var be = rig(h);
        blueprint(h, be, Blocks.STONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState());
        h.runAfterDelay(40, be::startPrinting);
        h.runAfterDelay(65, () -> {
            check(be.getMissingItem() != null, "missing material was not detected");
            h.assertBlockPresent(Blocks.AIR, TARGET);
            be.pausePrinting(); insert(be, new ItemStack(Items.STONE)); insert(be, new ItemStack(Items.COBBLESTONE));
        });
        h.runAfterDelay(90, () -> {
            h.assertBlockPresent(Blocks.AIR, TARGET);
            check(count(be, new ItemStack(Items.STONE)) == 1, "manual pause consumed material");
            be.startPrinting();
        });
        h.runAfterDelay(150, () -> { finished(be); h.assertBlockPresent(Blocks.STONE, TARGET);
            h.assertBlockPresent(Blocks.COBBLESTONE, TARGET.east()); h.succeed(); });
    }

    @GameTest(template = "empty", timeoutTicks = 170)
    public static void fullOutputWaitsWithoutDeletingBlueprints(GameTestHelper h) {
        var be = rig(h, new ItemStack(Items.STONE));
        blueprint(h, be, Blocks.STONE.defaultBlockState());
        be.getInventory().setStackInSlot(1, AllItems.EMPTY_SCHEMATIC.asStack(AllItems.EMPTY_SCHEMATIC.asStack().getMaxStackSize()));
        h.runAfterDelay(40, be::startPrinting);
        h.runAfterDelay(70, () -> {
            check(be.getStatusMsg().equals("outputFull"), "full output was ignored");
            check(count(be, new ItemStack(Items.STONE)) == 1, "full output consumed material");
            be.getInventory().extractItem(1, 1, false);
        });
        h.runAfterDelay(135, () -> { finished(be); check(be.getInventory().getStackInSlot(1).getCount() == AllItems.EMPTY_SCHEMATIC.asStack().getMaxStackSize(), "output count changed"); h.succeed(); });
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void restartPreservesCursorAndFlyingBlocks(GameTestHelper h) {
        final var holder = new MEBlueprintCannonBlockEntity[]{rig(h, new ItemStack(Items.STONE, 3))};
        blueprint(h, holder[0], Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState());
        h.runAfterDelay(40, holder[0]::startPrinting);
        h.runAfterDelay(44, () -> {
            var old = holder[0];
            check(!old.getFlyingBlocks().isEmpty(), "fixture did not capture an in-flight shot");
            var tag = old.saveWithFullMetadata(h.getLevel().registryAccess());
            var restored = (MEBlueprintCannonBlockEntity) BlockEntity.loadStatic(old.getBlockPos(), old.getBlockState(), tag, h.getLevel().registryAccess());
            check(restored != null, "block entity could not load before a level was assigned");
            check(restored.getFlyingBlocks().size() == old.getFlyingBlocks().size(), "projectile count changed on load");
            restored.loadWithComponents(tag, h.getLevel().registryAccess());
            check(restored.getFlyingBlocks().size() == old.getFlyingBlocks().size(), "repeated load duplicated projectiles");
            h.getLevel().removeBlockEntity(old.getBlockPos());
            h.getLevel().setBlockEntity(restored); restored.initialize(); holder[0] = restored;
        });
        h.runAfterDelay(145, () -> {
            finished(holder[0]); check(count(holder[0], new ItemStack(Items.STONE)) == 0, "restart changed total debit");
            for (int i = 0; i < 3; i++) h.assertBlockPresent(Blocks.STONE, TARGET.east(i)); h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 170)
    public static void doorHalvesCostOneItem(GameTestHelper h) {
        var be = rig(h, new ItemStack(Items.OAK_DOOR));
        h.setBlock(SOURCE, Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        h.setBlock(SOURCE.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        h.setBlock(TARGET.below(), Blocks.STONE);
        blueprint(h, be, new Vec3i(1, 2, 1), Rotation.NONE);
        h.runAfterDelay(40, be::startPrinting);
        h.runAfterDelay(135, () -> { finished(be); h.assertBlockPresent(Blocks.OAK_DOOR, TARGET);
            h.assertBlockPresent(Blocks.OAK_DOOR, TARGET.above()); check(count(be, new ItemStack(Items.OAK_DOOR)) == 0, "door debit"); h.succeed(); });
    }

    @GameTest(template = "empty", timeoutTicks = 170)
    public static void bedHalvesCostOneItem(GameTestHelper h) {
        var be = rig(h, new ItemStack(Items.RED_BED));
        var foot = Blocks.RED_BED.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                .setValue(BlockStateProperties.BED_PART, BedPart.FOOT);
        h.setBlock(SOURCE, foot); h.setBlock(SOURCE.east(), foot.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
        h.setBlock(TARGET.below(), Blocks.STONE); h.setBlock(TARGET.east().below(), Blocks.STONE);
        blueprint(h, be, new Vec3i(2, 1, 1), Rotation.NONE);
        h.runAfterDelay(40, be::startPrinting);
        h.runAfterDelay(135, () -> { finished(be); h.assertBlockPresent(Blocks.RED_BED, TARGET);
            h.assertBlockPresent(Blocks.RED_BED, TARGET.east()); check(count(be, new ItemStack(Items.RED_BED)) == 0, "bed debit"); h.succeed(); });
    }

    @GameTest(template = "empty", timeoutTicks = 170)
    public static void beltChainRebuildsWithCasings(GameTestHelper h) {
        var be = rig(h, com.simibubi.create.AllBlocks.SHAFT.asStack(2), AllItems.BELT_CONNECTOR.asStack());
        var belt = com.simibubi.create.AllBlocks.BELT.getDefaultState()
                .setValue(com.simibubi.create.content.kinetics.belt.BeltBlock.HORIZONTAL_FACING, Direction.EAST)
                .setValue(com.simibubi.create.content.kinetics.belt.BeltBlock.SLOPE, com.simibubi.create.content.kinetics.belt.BeltSlope.HORIZONTAL);
        for (int i = 0; i < 3; i++) {
            var part = i == 0 ? com.simibubi.create.content.kinetics.belt.BeltPart.START :
                i == 2 ? com.simibubi.create.content.kinetics.belt.BeltPart.END : com.simibubi.create.content.kinetics.belt.BeltPart.MIDDLE;
            h.setBlock(SOURCE.east(i), belt.setValue(com.simibubi.create.content.kinetics.belt.BeltBlock.PART, part));
        }
        for (int i = 0; i < 3; i++) {
            com.simibubi.create.content.kinetics.belt.BeltBlockEntity segment = h.getBlockEntity(SOURCE.east(i));
            segment.setController(h.absolutePos(SOURCE)); segment.beltLength = 3; segment.index = i;
            segment.setCasingType(com.simibubi.create.content.kinetics.belt.BeltBlockEntity.CasingType.BRASS);
        }
        blueprint(h, be, new Vec3i(3, 1, 1), Rotation.NONE);
        h.runAfterDelay(40, be::startPrinting);
        h.runAfterDelay(140, () -> {
            finished(be);
            for (int i = 0; i < 3; i++) {
                h.assertBlockPresent(com.simibubi.create.AllBlocks.BELT.get(), TARGET.east(i));
                com.simibubi.create.content.kinetics.belt.BeltBlockEntity segment = h.getBlockEntity(TARGET.east(i));
                check(segment.casing == com.simibubi.create.content.kinetics.belt.BeltBlockEntity.CasingType.BRASS, "belt casing lost");
            }
            check(count(be, com.simibubi.create.AllBlocks.SHAFT.asStack()) == 0 && count(be, AllItems.BELT_CONNECTOR.asStack()) == 0, "belt material debit"); h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 170)
    public static void entityEquipmentMatchesSanitizedMaterialCost(GameTestHelper h) {
        var be = rig(h, new ItemStack(Items.ARMOR_STAND), new ItemStack(Items.STONE, 2));
        var armor = new net.minecraft.world.entity.decoration.ArmorStand(h.getLevel(), h.absolutePos(SOURCE).getX() + .5,
                h.absolutePos(SOURCE).getY(), h.absolutePos(SOURCE).getZ() + .5);
        var poisoned = new ItemStack(Items.STONE);
        poisoned.set(DataComponents.FOOD, new net.minecraft.world.food.FoodProperties.Builder().nutrition(1).usingConvertsTo(Items.COMMAND_BLOCK).build());
        armor.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, poisoned);
        h.getLevel().addFreshEntity(armor);
        h.setBlock(SOURCE.above(2), Blocks.STONE); // keep a non-empty schematic
        blueprint(h, be, new Vec3i(1, 3, 1), Rotation.NONE);
        h.runAfterDelay(40, be::startPrinting);
        h.runAfterDelay(140, () -> {
            finished(be);
            var placed = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class,
                    new net.minecraft.world.phys.AABB(h.absolutePos(TARGET)).inflate(.5));
            check(placed.size() == 1, "armor stand not placed exactly once");
            check(!placed.getFirst().getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).has(DataComponents.FOOD), "unpaid unsafe entity item component survived");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void providerRestoresValidatedPatternsFromPaidBlanks(GameTestHelper h) {
        var wanted = pattern(Items.COBBLESTONE, Items.STONE);
        var different = pattern(Items.COBBLESTONE, Items.DIAMOND);
        var be = rig(h, AEBlocks.PATTERN_PROVIDER.stack(), different);
        h.setBlock(SOURCE, AEBlocks.PATTERN_PROVIDER.block());
        PatternProviderBlockEntity source = h.getBlockEntity(SOURCE);
        source.getLogic().getPatternInv().setItemDirect(3, wanted.copy());
        source.getLogic().getReturnInv().setStack(0, new GenericStack(AEItemKey.of(Items.NETHERITE_BLOCK), 64));
        blueprint(h, be, new Vec3i(1, 1, 1), Rotation.NONE);
        h.runAfterDelay(40, be::startPrinting);
        h.runAfterDelay(80, () -> {
            h.assertBlockPresent(Blocks.AIR, TARGET);
            check(count(be, different) == 1 && count(be, AEBlocks.PATTERN_PROVIDER.stack()) == 1, "different NBT was consumed or payment was partial");
            check(be.getMissingItem() != null && be.getMissingItem().is(AEItems.BLANK_PATTERN.asItem()), "must request blank pattern");
            insert(be, AEItems.BLANK_PATTERN.stack());
        });
        h.runAfterDelay(160, () -> {
            finished(be);
            PatternProviderBlockEntity placed = h.getBlockEntity(TARGET);
            check(ItemStack.isSameItemSameComponents(placed.getLogic().getPatternInv().getStackInSlot(3), wanted), "encoded pattern/slot not restored");
            check(placed.getLogic().getReturnInv().getStack(0) == null, "unpaid return buffer copied from schematic");
            check(count(be, wanted) == 0 && count(be, different) == 1 && count(be, AEItems.BLANK_PATTERN.stack()) == 0, "memory-card restoration charged incorrect materials");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void maliciousPatternComponentsCannotCreateItems(GameTestHelper h) {
        var poisoned = pattern(Items.COBBLESTONE, Items.STONE);
        poisoned.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.NETHERITE_BLOCK, 64))));
        var be = rig(h, AEBlocks.PATTERN_PROVIDER.stack(), AEItems.BLANK_PATTERN.stack());
        h.setBlock(SOURCE, AEBlocks.PATTERN_PROVIDER.block());
        PatternProviderBlockEntity source = h.getBlockEntity(SOURCE);
        source.getLogic().getPatternInv().setItemDirect(0, poisoned.copy());
        blueprint(h, be, new Vec3i(1, 1, 1), Rotation.NONE);
        h.runAfterDelay(40, be::startPrinting);
        h.runAfterDelay(100, () -> {
            h.assertBlockPresent(Blocks.AIR, TARGET);
            check(be.getStatusMsg().equals("unsafeData"), "extra container component not rejected: " + be.getStatusMsg());
            check(count(be, AEItems.BLANK_PATTERN.stack()) == 1 && count(be, AEBlocks.PATTERN_PROVIDER.stack()) == 1, "unsafe data consumed materials");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 170)
    public static void cablePartsFacadesFiltersAndRotation(GameTestHelper h) {
        var cableItem = AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT);
        var wanted = pattern(Items.COBBLESTONE, Items.STONE);
        var facade = new FacadePart(Blocks.STONE.defaultBlockState(), Direction.WEST);
        var be = rig(h, new ItemStack(cableItem), AEParts.EXPORT_BUS.stack(), AEParts.PATTERN_PROVIDER.stack(),
                AEItems.SPEED_CARD.stack(), AEItems.BLANK_PATTERN.stack(), facade.getItemStack());
        h.setBlock(SOURCE, AEBlocks.CABLE_BUS.block());
        CableBusBlockEntity source = h.getBlockEntity(SOURCE);
        check(source.addPart(cableItem, null, null) != null, "source cable not added");
        ExportBusPart bus = source.addPart(AEParts.EXPORT_BUS.get(), Direction.EAST, null);
        bus.getConfig().setStack(0, new GenericStack(AEItemKey.of(Items.DIAMOND), 1));
        bus.getUpgrades().addItems(AEItems.SPEED_CARD.stack());
        var provider = source.addPart(AEParts.PATTERN_PROVIDER.get(), Direction.NORTH, null);
        provider.getLogic().getPatternInv().setItemDirect(2, wanted.copy());
        source.getFacadeContainer().addFacade(facade);
        blueprint(h, be, new Vec3i(1, 1, 1), Rotation.CLOCKWISE_90);
        h.runAfterDelay(40, be::startPrinting);
        h.runAfterDelay(135, () -> {
            finished(be);
            CableBusBlockEntity placed = h.getBlockEntity(TARGET);
            check(placed.getPart(null) != null, "cable missing");
            check(placed.getPart(Direction.SOUTH) instanceof ExportBusPart, "rotated export bus missing");
            var resultBus = (ExportBusPart) placed.getPart(Direction.SOUTH);
            check(resultBus.getConfig().getStack(0).what().equals(AEItemKey.of(Items.DIAMOND)), "memory-card filter not restored");
            check(resultBus.getUpgrades().getInstalledUpgrades(AEItems.SPEED_CARD) == 1, "upgrade not restored");
            check(placed.getPart(Direction.EAST) instanceof PatternProviderLogicHost, "rotated provider missing");
            var resultProvider = (PatternProviderLogicHost) placed.getPart(Direction.EAST);
            check(ItemStack.isSameItemSameComponents(resultProvider.getLogic().getPatternInv().getStackInSlot(2), wanted), "part pattern lost");
            check(placed.getFacadeContainer().getFacade(Direction.NORTH) != null, "rotated facade missing");
            check(count(be, AEParts.EXPORT_BUS.stack()) == 0 && count(be, AEItems.BLANK_PATTERN.stack()) == 0 && count(be, AEItems.SPEED_CARD.stack()) == 0,
                    "parts or internal items were not paid");
            check(count(be, new ItemStack(Items.DIAMOND)) == 0, "ghost filter became a physical item");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 170)
    public static void chestInventoryIsNeverCopied(GameTestHelper h) {
        var be = rig(h, new ItemStack(Items.CHEST));
        h.setBlock(SOURCE, Blocks.CHEST);
        net.minecraft.world.level.block.entity.ChestBlockEntity source = h.getBlockEntity(SOURCE);
        source.setItem(0, new ItemStack(Items.NETHERITE_BLOCK, 64));
        blueprint(h, be, new Vec3i(1, 1, 1), Rotation.NONE);
        h.runAfterDelay(40, be::startPrinting);
        h.runAfterDelay(135, () -> {
            finished(be); net.minecraft.world.level.block.entity.ChestBlockEntity target = h.getBlockEntity(TARGET);
            check(target.isEmpty(), "unpaid chest contents copied"); h.succeed();
        });
    }

    private static ItemStack foodPayload(GameTestHelper h) {
        ItemStack[] input = new ItemStack[9]; Arrays.fill(input, ItemStack.EMPTY); input[0] = new ItemStack(Items.OAK_LOG);
        var recipe = h.getLevel().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.withDefaultNamespace("oak_planks")).orElseThrow();
        @SuppressWarnings("unchecked") var craftingRecipe = (net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>) (Object) recipe;
        ItemStack stack = PatternDetailsHelper.encodeCraftingPattern(craftingRecipe, input, new ItemStack(Items.OAK_PLANKS, 4), false, true);
        check(PatternDetailsHelper.decodePattern(stack, h.getLevel()) != null, "fixture requires a valid native crafting pattern");
        stack.set(DataComponents.FOOD, new net.minecraft.world.food.FoodProperties.Builder().nutrition(1).saturationModifier(1)
                .usingConvertsTo(Items.COMMAND_BLOCK).build());
        return stack;
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void foodConvertsToCommandBlockPayloadIsRejected(GameTestHelper h) {
        var poisoned = foodPayload(h);
        var be = rig(h, AEBlocks.PATTERN_PROVIDER.stack(), AEItems.BLANK_PATTERN.stack());
        h.setBlock(SOURCE, AEBlocks.PATTERN_PROVIDER.block());
        PatternProviderBlockEntity provider = h.getBlockEntity(SOURCE);
        provider.getLogic().getPatternInv().setItemDirect(0, poisoned);
        blueprint(h, be, new Vec3i(1, 1, 1), Rotation.NONE);
        h.runAfterDelay(40, be::startPrinting);
        h.runAfterDelay(100, () -> {
            check(be.getStatusMsg().equals("unsafeData"), "food/using_converts_to command block payload was accepted");
            h.assertBlockPresent(Blocks.AIR, TARGET);
            check(count(be, AEItems.BLANK_PATTERN.stack()) == 1 && count(be, new ItemStack(Items.COMMAND_BLOCK)) == 0, "payload generated or consumed an item");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void craftingCardWaitsForActualAssemblerOutput(GameTestHelper h) {
        var be = rig(h, new ItemStack(Items.OAK_LOG));
        be.getUpgradeInventory().addItems(AEItems.CRAFTING_CARD.stack());
        h.setBlock(CANNON.east(2), AEBlocks.CRAFTING_STORAGE_1K.block());
        h.setBlock(CANNON.east().south(), AEBlocks.PATTERN_PROVIDER.block());
        h.setBlock(CANNON.east().south(2), AEBlocks.MOLECULAR_ASSEMBLER.block());
        PatternProviderBlockEntity provider = h.getBlockEntity(CANNON.east().south());
        ItemStack recipePattern = foodPayload(h); recipePattern.remove(DataComponents.FOOD);
        provider.getLogic().getPatternInv().setItemDirect(0, recipePattern);
        blueprint(h, be, Blocks.OAK_PLANKS.defaultBlockState());
        h.runAfterDelay(50, () -> {
            provider.getLogic().updatePatterns();
            check(be.getMainNode().getGrid().getCraftingService().isCraftable(AEItemKey.of(Items.OAK_PLANKS)), "crafting fixture did not publish pattern");
            be.startPrinting();
        });
        h.runAfterDelay(52, () -> {
            check(be.getFlyingBlocks().isEmpty(), "craftable item was treated as already paid");
            h.assertBlockPresent(Blocks.AIR, TARGET);
        });
        h.succeedWhen(() -> {
            finished(be);
            h.assertBlockPresent(Blocks.OAK_PLANKS, TARGET);
            check(count(be, new ItemStack(Items.OAK_LOG)) == 0, "crafting input not consumed");
            check(count(be, new ItemStack(Items.OAK_PLANKS)) == 3, "crafting result lost or duplicated");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 170)
    public static void customDataAndModComponentsSurviveBlankPatternRestoration(GameTestHelper h) {
        var wanted = pattern(Items.COBBLESTONE, Items.STONE);
        var custom = new CompoundTag(); custom.putBoolean("eap_allow_scaling", true); custom.putInt("eap_multiplier_limit", 16);
        wanted.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(custom));
        wanted.set(AEComponents.EXPORTED_PRIORITY, 12); // registered non-vanilla component: deliberately preserved
        var eapId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("extendedae_plus", "smart_doubling");
        if (net.neoforged.fml.ModList.get().isLoaded("extendedae_plus")) {
            check(net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(eapId), "EAEP component missing from compatibility fixture");
            @SuppressWarnings("unchecked") var component = (net.minecraft.core.component.DataComponentType<Boolean>) net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.get(eapId);
            wanted.set(component, true);
        }
        var be = rig(h, AEBlocks.PATTERN_PROVIDER.stack(), AEItems.BLANK_PATTERN.stack());
        h.setBlock(SOURCE, AEBlocks.PATTERN_PROVIDER.block());
        PatternProviderBlockEntity provider = h.getBlockEntity(SOURCE);
        provider.getLogic().getPatternInv().setItemDirect(1, wanted.copy());
        blueprint(h, be, new Vec3i(1, 1, 1), Rotation.NONE);
        h.runAfterDelay(40, be::startPrinting);
        h.runAfterDelay(135, () -> {
            finished(be);
            PatternProviderBlockEntity result = h.getBlockEntity(TARGET);
            check(ItemStack.isSameItemSameComponents(wanted, result.getLogic().getPatternInv().getStackInSlot(1)), "allowed addon/custom components were dropped");
            check(count(be, AEItems.BLANK_PATTERN.stack()) == 0, "blank not consumed"); h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void vanillaComponentPatchesAreRejectedExceptCustomData(GameTestHelper h) {
        var pattern = pattern(Items.COBBLESTONE, Items.STONE);
        ItemStack defaults = pattern.copy(); SafeAe2Schematic.validateComponents(defaults);
        pattern.set(DataComponents.CUSTOM_NAME, Component.literal("payload"));
        boolean rejected = false;
        try { SafeAe2Schematic.validateComponents(pattern); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "non-custom vanilla patch permitted");
        pattern = defaults.copy(); pattern.remove(DataComponents.MAX_STACK_SIZE);
        rejected = false;
        try { SafeAe2Schematic.validateComponents(pattern); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "vanilla removal patch permitted");
        h.succeed();
    }

    private static class FakeStorage implements MEStorage {
        final Map<AEItemKey, Long> items = new LinkedHashMap<>();
        boolean shortActual;
        FakeStorage(ItemStack... contents) { for (var stack : contents) items.merge(AEItemKey.of(stack), (long) stack.getCount(), Long::sum); }
        @Override public Component getDescription() { return Component.literal("test"); }
        @Override public void getAvailableStacks(KeyCounter out) { items.forEach(out::add); }
        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long count = Math.min(items.getOrDefault(key, 0L), amount);
            if (mode == Actionable.MODULATE) { if (shortActual) count = Math.max(0, count - 1); items.put((AEItemKey) key, items.get(key) - count); }
            return count;
        }
    }
    private static IEnergyService energy(double[] value) {
        return (IEnergyService) Proxy.newProxyInstance(IEnergyService.class.getClassLoader(), new Class[]{IEnergyService.class}, (o, m, args) -> {
            if (m.getName().equals("extractAEPower")) {
                double got = Math.min((double) args[0], value[0]);
                if (args[1] == Actionable.MODULATE) value[0] -= got;
                return got;
            }
            if (m.getName().equals("injectPower")) { value[0] += (double) args[0]; return 0.0; }
            throw new UnsupportedOperationException(m.getName());
        });
    }
    private static ItemRequirement req(ItemStack... stacks) {
        return new ItemRequirement(Arrays.stream(stacks).map(s -> (ItemRequirement.StackRequirement)
                new ItemRequirement.StrictNbtStackRequirement(s, ItemRequirement.ItemUseType.CONSUME)).toList());
    }
    @GameTest(template = "empty")
    public static void duplicateRequirementsCannotDoubleSpend(GameTestHelper h) {
        var storage = new FakeStorage(new ItemStack(Items.STONE)); var power = new double[]{100}; var buffer = new ArrayList<ItemStack>();
        var result = CannonMaterialTransaction.pay(req(new ItemStack(Items.STONE), new ItemStack(Items.STONE)), storage,
                energy(power), IActionSource.empty(), buffer, 5);
        check(!result.paid() && power[0] == 100 && storage.items.get(AEItemKey.of(Items.STONE)) == 1 && buffer.isEmpty(), "partial/double payment"); h.succeed();
    }
    @GameTest(template = "empty")
    public static void shortRealExtractionIsRetainedAndDoesNotShoot(GameTestHelper h) {
        var storage = new FakeStorage(new ItemStack(Items.STONE, 2)); storage.shortActual = true;
        var power = new double[]{100}; var buffer = new ArrayList<ItemStack>();
        var result = CannonMaterialTransaction.pay(req(new ItemStack(Items.STONE, 2)), storage, energy(power), IActionSource.empty(), buffer, 5);
        check(!result.paid() && power[0] == 100 && buffer.stream().mapToInt(ItemStack::getCount).sum() == 1
                && storage.items.get(AEItemKey.of(Items.STONE)) == 1, "short extraction lost/duplicated items"); h.succeed();
    }
    @GameTest(template = "empty")
    public static void insufficientEnergyDoesNotConsumeItems(GameTestHelper h) {
        var storage = new FakeStorage(new ItemStack(Items.STONE)); var power = new double[]{4};
        var result = CannonMaterialTransaction.pay(req(new ItemStack(Items.STONE)), storage, energy(power), IActionSource.empty(), new ArrayList<>(), 5);
        check(!result.paid() && result.powerMissing() && power[0] == 4 && storage.items.get(AEItemKey.of(Items.STONE)) == 1, "unpowered shot debited"); h.succeed();
    }
    @GameTest(template = "empty")
    public static void toolsLoseDurabilityAndBucketsReturn(GameTestHelper h) {
        ItemStack tool = new ItemStack(Items.SHEARS); tool.setDamageValue(10);
        var storage = new FakeStorage(tool, new ItemStack(Items.WATER_BUCKET)); var power = new double[]{100}; var buffer = new ArrayList<ItemStack>();
        var requirement = new ItemRequirement(List.of(new ItemRequirement.StackRequirement(new ItemStack(Items.SHEARS), ItemRequirement.ItemUseType.DAMAGE),
                new ItemRequirement.StackRequirement(new ItemStack(Items.WATER_BUCKET), ItemRequirement.ItemUseType.CONSUME)));
        var result = CannonMaterialTransaction.pay(requirement, storage, energy(power), IActionSource.empty(), buffer, 5);
        check(result.paid() && power[0] == 95, "shot not paid exactly once");
        check(buffer.stream().anyMatch(s -> s.is(Items.SHEARS) && s.getDamageValue() == 11), "tool consumed instead of damaged");
        check(buffer.stream().anyMatch(s -> s.is(Items.BUCKET)), "bucket voided"); h.succeed();
    }
    @GameTest(template = "empty")
    public static void strictComponentsIncludeUnknownNestedData(GameTestHelper h) {
        ItemStack wanted = pattern(Items.COBBLESTONE, Items.STONE);
        ItemStack variant = wanted.copy();
        variant.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 64))));
        var storage = new FakeStorage(variant); var power = new double[]{100};
        var result = CannonMaterialTransaction.pay(req(wanted), storage, energy(power), IActionSource.empty(), new ArrayList<>(), 5);
        check(!result.paid() && power[0] == 100 && storage.items.get(AEItemKey.of(variant)) == 1, "unknown components ignored by matching"); h.succeed();
    }
}

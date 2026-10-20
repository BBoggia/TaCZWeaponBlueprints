package com.gamergaming.taczweaponblueprints.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.block.BlueprintRecyclerBlock;
import com.gamergaming.taczweaponblueprints.init.ModBlocks;
import com.gamergaming.taczweaponblueprints.init.ModItems;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerMenu;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerTier;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Opt-in real-server station assertions and native renderer evidence in a marked disposable world. */
@EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID, value = Dist.CLIENT)
public final class AnalyzerStationProbe {
    private static final String OUTPUT = System.getProperty("tacz.analyzerStationsQA.output");
    private static final boolean TRANSACTIONS = Boolean.getBoolean("tacz.analyzerTransactionsQA.enabled");
    private static final String WORLD = Boolean.getBoolean("tacz.researchCurrencyQA.enabled") ? "Research Currency QA" : TRANSACTIONS ? "Analyzer A4 QA" : "Analyzer A3 QA";
    private static final String MARKER = TRANSACTIONS ? ".analyzer-transactions-copy" : ".analyzer-stations-copy";
    private static final AtomicInteger SAVED = new AtomicInteger();
    private static final AtomicInteger CHECKS = new AtomicInteger();
    private static boolean opened, dispatched, ready, failed, finished;
    private static int step, ticks, totalTicks, worldWarmup;
    private static BlockPos stationPos;
    private AnalyzerStationProbe() { }

    private static List<BlueprintRecyclerBlock> blocks() {
        return List.of((BlueprintRecyclerBlock) ModBlocks.BLUEPRINT_RECYCLER.get(),
                ModBlocks.ADVANCED_BLUEPRINT_ANALYZER.get(), ModBlocks.EXPERIMENTAL_BLUEPRINT_ANALYZER.get());
    }

    @SubscribeEvent public static void render(ScreenEvent.Render.Post event) {
        if (Boolean.getBoolean("tacz.analyzerScreenQA.enabled") || OUTPUT == null || failed || finished) return;
        var mc = Minecraft.getInstance();
        try {
            if (!opened && event.getScreen() instanceof TitleScreen && mc.getOverlay() == null) {
                requireMarker(mc.gameDirectory.toPath().resolve("saves").resolve(WORLD));
                opened = true;
                mc.createWorldOpenFlows().openWorld(WORLD, () -> mc.setScreen(event.getScreen()));
                return;
            }
            if (!ready || ticks < 35 || mc.getOverlay() != null) return;
            if (step < 3) {
                if (!(event.getScreen() instanceof BlueprintRecyclerScreen screen)
                        || screen.getMenu().preview().stateToken() == 0) return;
                check(screen.getMenu().analyzerTier().orElseThrow() == AnalyzerTier.fromLevel(step + 1), "client tier");
                check(screen.getTitle().getString().equals(blocks().get(step).menuTitle().getString()), "client title");
            } else if (!(event.getScreen() instanceof Showcase)) return;
            event.getGuiGraphics().flush();
            Path root = Path.of(OUTPUT); Files.createDirectories(root);
            String file = step < 3 ? "tier-" + (step + 1) + "-menu.png" : "station-models.png";
            AnalyzerNativeCapture.grab(root, file, () -> SAVED.incrementAndGet(), AnalyzerStationProbe::fail);
            ready = false; dispatched = false; ticks = 0; step++;
        } catch (Exception error) { fail(error); }
    }

    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if (Boolean.getBoolean("tacz.analyzerScreenQA.enabled") || OUTPUT == null || failed || finished || !opened) return;
        var mc = Minecraft.getInstance();
        try {
            if (++totalTicks > 2400) throw new IllegalStateException("Station QA timed out");
            var server = mc.getSingleplayerServer();
            if (mc.player == null || server == null) return;
            requireMarker(server.getWorldPath(LevelResource.ROOT));
            // Let chunk entity sections finish joining before asserting spawned drops.
            if (step == 0 && !dispatched && worldWarmup++ < 60) return;
            if (step == 4) {
                if (SAVED.get() != 4) return;
                var result = new JsonObject();
                result.addProperty("assertions", CHECKS.get()); result.addProperty("captures", SAVED.get());
                result.addProperty("world", WORLD); result.addProperty("passed", true);
                Files.writeString(Path.of(OUTPUT).resolve("complete.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
                finished = true; if(Boolean.getBoolean("tacz.researchDataScreenQA.enabled")) ResearchDataScreenProbe.start(Path.of(OUTPUT)); else mc.stop(); return;
            }
            if (ready) {
                ticks++;
                if (ticks == 10) org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 2, 2);
                return;
            }
            if (dispatched) return;
            dispatched = true;
            var uuid = mc.player.getUUID();
            int scenario = step;
            server.execute(() -> {
                try {
                    var player = server.getPlayerList().getPlayer(uuid);
                    check(player != null, "QA player connected");
                    if (scenario == 0) {
                        verifyStations(player);
                        if (Boolean.getBoolean("tacz.researchCurrencyQA.enabled")) ResearchCurrencyProbe.verify(player, stationPos, Path.of(OUTPUT));
                        else if (TRANSACTIONS) AnalyzerTransactionProbe.verify(player, stationPos, Path.of(OUTPUT));
                    }
                    player.closeContainer();
                    if (scenario < 3) {
                        var block = blocks().get(scenario);
                        placeFixture(player.serverLevel(), stationPos, block.defaultBlockState());
                        open(player, block, stationPos);
                        var menu = (BlueprintRecyclerMenu) player.containerMenu;
                        menu.getSlot(0).set(new ItemStack(ModItems.RESEARCH_NOTE.get()));
                        menu.broadcastChanges(); menu.refreshAuthoritativePreview(player);
                        mc.execute(() -> ready = true);
                    } else mc.execute(() -> {
                        try { verifyModels(); mc.setScreen(new Showcase()); ready = true; }
                        catch (Exception error) { fail(error); }
                    });
                } catch (Exception error) { mc.execute(() -> fail(error)); }
            });
        } catch (Exception error) { fail(error); }
    }

    private static void verifyStations(ServerPlayer player) throws Exception {
        var config=com.gamergaming.taczweaponblueprints.init.ModConfigs.BLUEPRINT;
        var kind=config.researchCurrencyKind.get();var mode=config.researchDataBehavior.get();
        try {
            config.researchCurrencyKind.validateAndSet(com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy.Kind.BANKED_RP);
            config.researchDataBehavior.validateAndSet(com.gamergaming.taczweaponblueprints.resource.research.ResearchDataPolicy.Behavior.LEGACY_CURRENCY);
            config.initializeLoadedSnapshots();verifyLegacyStations(player);
        } finally {config.researchCurrencyKind.validateAndSet(kind);config.researchDataBehavior.validateAndSet(mode);config.initializeLoadedSnapshots();}
    }
    private static void verifyLegacyStations(ServerPlayer player) throws Exception {
        var level = player.serverLevel();
        player.closeContainer(); player.getInventory().clearContent();
        stationPos = player.blockPosition().offset(3, 0, 0);
        var pos = stationPos;
        for (BlockPos clear : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 2, 1))) {
            level.removeBlock(clear, false);
        }
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        for (int tier = 1; tier <= 3; tier++) {
            var block = blocks().get(tier - 1);
            check(block.tier().level() == tier, "registered tier " + tier);
            var id = BuiltInRegistries.BLOCK.getKey(block);
            var recipe = (CraftingRecipe) level.getRecipeManager().byKey(id).orElseThrow().value();
            var grid = new TransientCraftingContainer(new CraftingMenu(1, player.getInventory()), 3, 3);
            for (int i = 0; i < recipe.getIngredients().size(); i++) {
                var options = recipe.getIngredients().get(i).getItems();
                grid.setItem(i, options.length == 0 ? ItemStack.EMPTY : options[0].copy());
            }
            check(recipe.matches(grid.asCraftInput(), level), "loaded crafting recipe " + id);
            check(recipe.assemble(grid.asCraftInput(), level.registryAccess()).is(block.asItem()), "recipe output " + id);
            grid.setItem(0, ItemStack.EMPTY);
            check(!recipe.matches(grid.asCraftInput(), level), "incomplete recipe rejected " + id);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                player.closeContainer(); level.removeBlock(pos, false);
                player.setYRot(facing.toYRot());
                var stack = new ItemStack(block.asItem());
                var hit = new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, 0.5, 0), Direction.UP, pos.below(), false);
                check(((BlockItem) stack.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit))
                        .consumesAction(), "item places " + id + " " + facing);
                var state = level.getBlockState(pos);
                check(state.is(block) && state.getValue(BlueprintRecyclerBlock.FACING) == facing.getOpposite(), "placement facing");
                check(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), NbtUtils.writeBlockState(state)).equals(state), "palette save round trip");
                check(state.rotate(Rotation.CLOCKWISE_90).getValue(BlueprintRecyclerBlock.FACING) == facing.getOpposite().getClockWise(), "rotation");
                check(state.mirror(Mirror.FRONT_BACK).mirror(Mirror.FRONT_BACK).equals(state), "mirror round trip");
                check(BlueprintRecyclerBlock.isComplete(level, pos), "complete two-block placement");
                var extensionPos = pos.relative(state.getValue(BlueprintRecyclerBlock.FACING).getClockWise());
                var extension = level.getBlockState(extensionPos);
                check(extension.getValue(BlueprintRecyclerBlock.EXTENSION), "extension state");
                check(BlueprintRecyclerBlock.rootPosition(extensionPos, extension).equals(pos), "extension resolves root");
                check(state.getPistonPushReaction() == net.minecraft.world.level.material.PushReaction.BLOCK, "piston protection");
                check(Block.getDrops(extension, level, extensionPos, null, player,
                        new ItemStack(Items.IRON_PICKAXE)).isEmpty(), "extension has no independent loot");
                var bounds = state.getShape(level, pos).bounds();
                check(bounds.minX == 0 && bounds.minZ == 0 && bounds.maxX == 1 && bounds.maxZ == 1
                        && bounds.maxY > 1 && bounds.maxY < 2, "per-half equipment collision");
                check(level.getBlockEntity(pos) == null, "no block entity");
                check(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(state), "iron pickaxe harvest");
                check(!new ItemStack(Items.IRON_AXE).isCorrectToolForDrops(state), "axe cannot harvest");
                var drops = Block.getDrops(state, level, pos, null, player, new ItemStack(Items.IRON_PICKAXE));
                check(drops.size() == 1 && drops.get(0).is(block.asItem()) && drops.get(0).getCount() == 1, "matching single drop");
                open(player, block, extensionPos);
                var menu = (BlueprintRecyclerMenu) player.containerMenu;
                check(menu.isRecyclerMenuValid(player) && menu.analyzerTier().orElseThrow() == block.tier(), "server opens tier");
                check(menu.slots.size() == 38 && !menu.getSlot(1).mayPlace(new ItemStack(Items.STONE)), "shared slots and output guard");
                Vec3 before = player.position(); player.setPos(before.add(30, 0, 0));
                check(!menu.stillValid(player) && menu.analyzerTier().isEmpty(), "out of reach invalid");
                player.setPos(before); check(menu.stillValid(player), "return within reach valid");
                menu.invalidateStation(level, pos.above()); check(menu.stillValid(player), "other station untouched");
                level.setBlockAndUpdate(pos, state.rotate(Rotation.CLOCKWISE_90));
                check(!menu.stillValid(player), "mismatched rotation invalidates structure");
                level.setBlockAndUpdate(pos, state);
                check(menu.stillValid(player), "matching halves restore structural validity");
                menu.getSlot(0).set(new ItemStack(Items.DIRT, 3)); menu.getSlot(1).set(new ItemStack(Items.STONE, 2));
                long token = menu.preview().stateToken();
                level.removeBlock(pos, false);
                placeFixture(level, pos, state);
                check(!menu.stillValid(player) && menu.analyzerTier().isEmpty(), "same tier replacement cannot revive session");
                var result = menu.handleRecyclerAction(player, BlueprintRecyclerActionContract.Action.REDEEM,
                        ResourceLocation.parse("minecraft:dirt"), 3, token);
                check(result.code() == BlueprintRecyclerActionContract.ResultCode.INVALID_INPUT, "old action rejected");
                check(menu.inputStack().getCount() == 3 && menu.outputStack().getCount() == 2, "rejected action preserves both slots");
                int dirt = player.getInventory().countItem(Items.DIRT), stone = player.getInventory().countItem(Items.STONE);
                player.closeContainer();
                check(player.getInventory().countItem(Items.DIRT) == dirt + 3
                        && player.getInventory().countItem(Items.STONE) == stone + 2, "closing returns both slots once");
            }
            open(player, block, pos);
            var active = (BlueprintRecyclerMenu) player.containerMenu;
            var data = com.gamergaming.taczweaponblueprints.init.ModAttachments.recipeData(player).orElseThrow();
            check(data.setResearchPoints(0), "reset disposable RP fixture");
            active.getSlot(0).set(new ItemStack(ModItems.RESEARCH_NOTE.get()));
            active.refreshAuthoritativePreview(player);
            int award = active.preview().pointValue();
            check(award > 0, "research data preview on tier " + tier);
            var redeemed = active.handleRecyclerAction(player, BlueprintRecyclerActionContract.Action.REDEEM,
                    BuiltInRegistries.ITEM.getKey(ModItems.RESEARCH_NOTE.get()), 1, active.preview().stateToken());
            check(redeemed.successful() && active.inputStack().isEmpty() && data.getResearchPoints() == award,
                    "deterministic redemption on tier " + tier);
            var mutable = pos.mutable();
            var bound = BlueprintRecyclerMenu.server(1, player.getInventory(), level, mutable);
            mutable.move(100, 0, 0);
            check(bound.stillValid(player), "opening position copied immutably");
            var old = (BlueprintRecyclerMenu) player.containerMenu;
            level.setBlockAndUpdate(pos, blocks().get(tier % 3).defaultBlockState());
            check(!old.stillValid(player), "different tier replacement invalid");
            player.closeContainer();
            placeFixture(level, pos, block.defaultBlockState());
            level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1)).forEach(ItemEntity::discard);
            check(level.destroyBlock(pos, true, player), "break station");
            var spawned = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1));
            check(spawned.stream().filter(item -> !item.isRemoved() && item.getItem().is(block.asItem()))
                    .mapToInt(item -> item.getItem().getCount()).sum() == 1,
                    "actual break drops station item " + id + ": " + spawned.stream().map(item -> item.getItem().toString()).toList()
                            + " drops=" + level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS)
                            + " restoring=" + level.restoringBlockSnapshots + " pos=" + pos);
            spawned.forEach(ItemEntity::discard);
            check(level.isEmptyBlock(pos.east()), "root break removes extension");
            placeFixture(level, pos, block.defaultBlockState());
            open(player, block, pos.east());
            var extensionMenu = (BlueprintRecyclerMenu) player.containerMenu;
            check(level.destroyBlock(pos.east(), true, player), "break extension");
            check(level.isEmptyBlock(pos) && !extensionMenu.stillValid(player), "extension break removes root and closes session");
            var extensionDrops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
            check(extensionDrops.stream().filter(item -> !item.isRemoved() && item.getItem().is(block.asItem()))
                    .mapToInt(item -> item.getItem().getCount()).sum() == 1, "extension break drops exactly one item");
            extensionDrops.forEach(ItemEntity::discard);
            player.closeContainer();
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            for (boolean correctTool : new boolean[]{false, true}) {
                placeFixture(level, pos, block.defaultBlockState());
                player.setItemInHand(InteractionHand.MAIN_HAND, correctTool ? new ItemStack(Items.IRON_PICKAXE) : ItemStack.EMPTY);
                var half = level.getBlockState(pos.east());
                block.playerWillDestroy(level, pos.east(), half, player);
                check(level.isEmptyBlock(pos) && level.isEmptyBlock(pos.east()), "player break removes both halves");
                var playerDrops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
                check(playerDrops.stream().filter(item -> !item.isRemoved() && item.getItem().is(block.asItem()))
                        .mapToInt(item -> item.getItem().getCount()).sum() == (correctTool ? 1 : 0),
                        "extension respects harvest tool requirement");
                playerDrops.forEach(ItemEntity::discard);
            }
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            placeFixture(level, pos, block.defaultBlockState());
            block.playerWillDestroy(level, pos.east(), level.getBlockState(pos.east()), player);
            check(level.isEmptyBlock(pos) && level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).isEmpty(),
                    "creative extension break has no drops");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            level.setBlockAndUpdate(pos.east(), Blocks.STONE.defaultBlockState());
            player.setYRot(Direction.SOUTH.toYRot());
            var blockedStack = new ItemStack(block.asItem());
            var blockedHit = new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, .5, 0), Direction.UP, pos.below(), false);
            check(!((BlockItem) blockedStack.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
                    blockedStack, blockedHit)).consumesAction(), "occupied extension rejects placement");
            check(blockedStack.getCount() == 1 && level.isEmptyBlock(pos) && level.getBlockState(pos.east()).is(Blocks.STONE),
                    "blocked placement preserves item and neighbor");
            level.setBlockAndUpdate(pos, block.defaultBlockState());
            open(player, block, pos);
            check(!BlueprintRecyclerBlock.isComplete(level, pos) && level.getBlockState(pos.east()).is(Blocks.STONE),
                    "legacy migration preserves occupied neighbor");
            level.removeBlock(pos.east(), false);
            open(player, block, pos);
            check(BlueprintRecyclerBlock.isComplete(level, pos) && player.containerMenu instanceof BlueprintRecyclerMenu,
                    "legacy station expands into empty space on use");
            player.closeContainer();
            level.removeBlock(pos, false);
        }
        var invalid = BlueprintRecyclerMenu.server(1, player.getInventory(), level, pos);
        check(!invalid.stillValid(player) && invalid.analyzerTier().isEmpty(), "non station cannot open valid menu");
        var unloadedPos = new BlockPos(10_000_000, 64, 10_000_000);
        check(!level.hasChunkAt(unloadedPos), "unloaded station fixture");
        var unloaded = BlueprintRecyclerMenu.server(1, player.getInventory(), level, unloadedPos);
        check(!unloaded.stillValid(player) && !level.hasChunkAt(unloadedPos), "invalid lookup does not load chunk");
        player.getInventory().clearContent();
        Path output = Path.of(OUTPUT); Files.createDirectories(output);
        Files.writeString(output.resolve("server-checks.json"), "{\"assertions\":" + CHECKS.get() + ",\"passed\":true}\n");
    }

    private static void placeFixture(net.minecraft.world.level.Level level, BlockPos pos,
                                     net.minecraft.world.level.block.state.BlockState state) {
        level.removeBlock(pos, false);
        check(BlueprintRecyclerBlock.placeCompleteStructure(level, pos, state), "two-block fixture");
    }

    private static void verifyModels() throws Exception {
        var mc = Minecraft.getInstance();
        for (var block : blocks()) {
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            int expectedFaces = switch (block.tier()) { case TIER_1 -> 582; case TIER_2 -> 900; case TIER_3 -> 906; };
            for (var direction : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                var state = block.defaultBlockState().setValue(BlueprintRecyclerBlock.FACING, direction);
                var model = mc.getBlockRenderer().getBlockModel(state);
                check(model != mc.getModelManager().getMissingModel(), "baked block model " + id);
                var quads = model.getQuads(state, null, net.minecraft.util.RandomSource.create(1),
                        net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null);
                check(quads.size() == expectedFaces, "all authored faces baked " + id + " got " + quads.size());
                check(quads.stream().noneMatch(quad -> quad.getSprite().contents().name().getPath().contains("missingno")),
                        "resolved block atlas " + id);
                var extension = state.setValue(BlueprintRecyclerBlock.EXTENSION, true);
                check(mc.getBlockRenderer().getBlockModel(extension).getQuads(extension, null,
                        net.minecraft.util.RandomSource.create(1)).isEmpty(), "extension does not duplicate model");
            }
            var stack = new ItemStack(block.asItem());
            var itemModel = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
            check(itemModel != mc.getModelManager().getMissingModel(), "baked item model " + id);
            int itemFaces = 0;
            for (var pass : itemModel.getRenderPasses(stack, true)) {
                var quads = pass.getQuads(null, null, net.minecraft.util.RandomSource.create(1),
                        net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null);
                itemFaces += quads.size();
                check(quads.stream().noneMatch(quad -> quad.getSprite().contents().name().getPath().contains("missingno")),
                        "resolved item atlas " + id);
            }
            check(itemFaces == expectedFaces, "complete inventory geometry " + id);
        }
    }

    private static void open(ServerPlayer player, BlueprintRecyclerBlock block, BlockPos pos) {
        player.serverLevel().getBlockState(pos).useWithoutItem(player.serverLevel(), player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
    }

    private static void requireMarker(Path root) {
        check(root.toAbsolutePath().normalize().getFileName().toString().equals(WORLD)
                && Files.isRegularFile(root.resolve(MARKER)), "marked disposable world");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
        // The count describes behavioral assertions, not recurring safety checks.
        if (!message.equals("marked disposable world")) CHECKS.incrementAndGet();
    }
    private static void fail(Exception error) {
        failed = true; error.printStackTrace();
        try { Files.createDirectories(Path.of(OUTPUT)); Files.writeString(Path.of(OUTPUT).resolve("error.txt"), error.toString()); }
        catch (Exception ignored) { }
        Minecraft.getInstance().stop();
    }

    private static final class Showcase extends Screen {
        Showcase() { super(Component.literal("Blueprint Analyzer stations")); }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF101923);
            graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
            String[] faces = {"North", "East", "South", "West"};
            var directions = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
            float cellWidth = width / 3.0F, rowHeight = (height - 77) / 4.0F;
            float scale = Math.min(cellWidth * 0.55F, rowHeight * 0.39F);
            for (int tier = 0; tier < 3; tier++) {
                var block = blocks().get(tier);
                var lines = font.split(block.menuTitle(), (int) cellWidth - 12);
                for (int line = 0; line < lines.size(); line++) {
                    graphics.drawCenteredString(font, lines.get(line), (int)(cellWidth * (tier + .5)), 30 + line * 10, 0xD7E6F5);
                }
                for (int row = 0; row < 4; row++) {
                    float x = cellWidth * (tier + .5F), y = 65 + rowHeight * (row + .7F);
                    var pose = graphics.pose(); pose.pushPose();
                    pose.translate(x, y, 150); pose.scale(scale, -scale, scale);
                    pose.mulPose(Axis.XP.rotationDegrees(25)); pose.mulPose(Axis.YP.rotationDegrees(225));
                    var extensionDirection = directions[row].getClockWise();
                    pose.translate(-.5 - extensionDirection.getStepX() * .5, 0,
                            -.5 - extensionDirection.getStepZ() * .5);
                    Lighting.setupFor3DItems();
                    minecraft.getBlockRenderer().renderSingleBlock(block.defaultBlockState().setValue(BlueprintRecyclerBlock.FACING, directions[row]),
                            pose, graphics.bufferSource(), 15728880, OverlayTexture.NO_OVERLAY);
                    graphics.flush(); pose.popPose(); Lighting.setupForFlatItems();
                    graphics.drawCenteredString(font, faces[row], (int)x, (int)(y + 6), 0x8FA6BB);
                }
            }
        }
        @Override public boolean isPauseScreen() { return false; }
    }
}

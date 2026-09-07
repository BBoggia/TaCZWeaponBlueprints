package com.gamergaming.taczweaponblueprints.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.ModBlocks;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModItems;
import com.gamergaming.taczweaponblueprints.item.BlueprintFragmentItem;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.item.PhysicalWeaponProvenance;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerMenu;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressValueMutation;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

/** Opt-in native screenshots of real server previews; never loaded in release jars. */
@Mod.EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID, value = Dist.CLIENT)
public final class AnalyzerBaselineCapture {
    private static final String OUTPUT = System.getProperty("tacz.analyzerBaseline.output");
    private static final String WORLD = "Analyzer A0 QA";
    private static final String MARKER = ".analyzer-baseline-copy";
    private static final String[] CASES = {"empty", "invalid", "weapon", "fragment", "research-data", "blueprint"};
    private static final ResourceLocation TARGET = new ResourceLocation("tacz:ak47");
    private static final AtomicInteger SAVED = new AtomicInteger();
    private static boolean opened, waiting, failed, finished;
    private static int step, ticks, totalTicks;
    private static int[] originalWindow;
    private static int originalGuiScale;

    private AnalyzerBaselineCapture() { }

    @SubscribeEvent
    public static void rendered(ScreenEvent.Render.Post event) {
        if (OUTPUT == null || failed || finished) return;
        var mc = Minecraft.getInstance();
        try {
            if (!opened && event.getScreen() instanceof TitleScreen && mc.getOverlay() == null) {
                if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve("saves").resolve(WORLD).resolve(MARKER))) {
                    throw new IllegalStateException("Create the marked disposable Analyzer world first");
                }
                opened = true;
                mc.createWorldOpenFlows().loadLevel(event.getScreen(), WORLD);
                return;
            }
            if (!waiting || ticks < 40 || !(event.getScreen() instanceof BlueprintRecyclerScreen screen)
                    || screen.getMenu().preview().stateToken() == 0 || mc.getOverlay() != null) return;
            requireCopy();
            String name = (step < CASES.length ? "normal-" : "small-") + CASES[step % CASES.length];
            Path root = Path.of(OUTPUT);
            Files.createDirectories(root);
            var preview = screen.getMenu().preview();
            var model = BlueprintRecyclerScreenModel.from(preview, false);
            var evidence = new JsonObject();
            evidence.addProperty("fixture", name);
            evidence.addProperty("world", WORLD);
            evidence.addProperty("source", "live server menu; no analysis action executed");
            evidence.addProperty("gui_width", mc.getWindow().getGuiScaledWidth());
            evidence.addProperty("gui_height", mc.getWindow().getGuiScaledHeight());
            evidence.addProperty("framebuffer_width", mc.getWindow().getWidth());
            evidence.addProperty("framebuffer_height", mc.getWindow().getHeight());
            evidence.addProperty("input_kind", preview.inputKind().name());
            evidence.addProperty("status_key", model.statusKey());
            evidence.addProperty("primary_action", model.primaryAction().map(Enum::name).orElse("NONE"));
            evidence.addProperty("secondary_action", model.secondaryAction().map(Enum::name).orElse("NONE"));
            evidence.addProperty("preview", preview.toString());
            Files.writeString(root.resolve(name + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(evidence) + "\n");
            event.getGuiGraphics().flush();
            AnalyzerNativeCapture.grab(root, name + ".png", () -> SAVED.incrementAndGet(), AnalyzerBaselineCapture::fail);
            waiting = false;
            step++;
            ticks = 0;
        } catch (Exception failure) {
            fail(failure);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (OUTPUT == null || failed || finished || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (!opened) return;
        try {
            if (++totalTicks > 2400) throw new IllegalStateException("Native Analyzer capture timed out");
            if (mc.player == null || mc.getSingleplayerServer() == null) return;
            requireCopy();
            if (step == CASES.length * 2) {
                if (SAVED.get() != step) return;
                restoreWindow();
                Files.writeString(Path.of(OUTPUT).resolve("complete.json"), "{\"captures\":" + step + ",\"settings_restored\":true}\n");
                finished = true;
                mc.stop();
                return;
            }
            if (waiting) {
                ticks++;
                if (ticks == 10) GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 2, 2);
                return;
            }
            waiting = true;
            ticks = 0;
            if (step == 0 || step == CASES.length) {
                if (originalWindow == null) {
                    int[] w = new int[1], h = new int[1];
                    GLFW.glfwGetWindowSize(mc.getWindow().getWindow(), w, h);
                    originalWindow = new int[]{w[0], h[0]};
                    originalGuiScale = mc.options.guiScale().get();
                }
                int[] logicalWidth = new int[1], logicalHeight = new int[1];
                GLFW.glfwGetWindowSize(mc.getWindow().getWindow(), logicalWidth, logicalHeight);
                int density = Math.max(1, Math.round((float) mc.getWindow().getWidth() / logicalWidth[0]));
                GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), step == 0 ? 1280 : 960, 720);
                mc.options.guiScale().set((step == 0 ? 2 : 3) * density);
                mc.resizeDisplay();
            }
            int scenario = step % CASES.length;
            var playerId = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                try {
                    var server = mc.getSingleplayerServer();
                    var player = server.getPlayerList().getPlayer(playerId);
                    if (player == null) throw new IllegalStateException("QA player disconnected");
                    player.closeContainer();
                    player.getInventory().clearContent();
                    var data = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve().orElseThrow();
                    data.replaceProgression(scenario == 5 ? Set.of(TARGET.toString()) : Set.of(), Set.of(TARGET.toString()), 125);
                    data.clearArchivedBlueprintFragments();
                    data.applyArchivedFragmentMutation(PlayerProgressValueMutation.Request.commit(TARGET.toString(), 0, 4));
                    var pos = player.blockPosition().below();
                    player.serverLevel().setBlockAndUpdate(pos, ModBlocks.BLUEPRINT_RECYCLER.get().defaultBlockState());
                    NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inventory, ignored) ->
                            BlueprintRecyclerMenu.server(id, inventory, player.serverLevel(), pos),
                            Component.translatable("container.taczweaponblueprints.blueprint_recycler")), buffer -> buffer.writeBlockPos(pos));
                    var menu = (BlueprintRecyclerMenu) player.containerMenu;
                    menu.getSlot(BlueprintRecyclerMenu.INPUT_SLOT).set(input(scenario));
                    menu.broadcastChanges();
                    menu.refreshAuthoritativePreview(player);
                } catch (Exception failure) {
                    mc.execute(() -> fail(failure));
                }
            });
        } catch (Exception failure) { fail(failure); }
    }

    private static ItemStack input(int scenario) {
        return switch (scenario) {
            case 0 -> ItemStack.EMPTY;
            case 1 -> new ItemStack(Items.DIRT);
            case 2 -> {
                var item = ForgeRegistries.ITEMS.getValues().stream().filter(value -> value instanceof IGun).findFirst().orElseThrow();
                var stack = new ItemStack(item);
                ((IGun) item).setGunId(stack, TARGET);
                PhysicalWeaponProvenance.stampLootGenerated(stack, new ResourceLocation("minecraft:chests/simple_dungeon"));
                yield stack;
            }
            case 3 -> { var stack = BlueprintFragmentItem.create(TARGET); stack.setCount(3); yield stack; }
            case 4 -> new ItemStack(ModItems.RESEARCH_NOTE.get(), 4);
            case 5 -> BlueprintItem.createBlueprint(TARGET.toString());
            default -> throw new IllegalArgumentException("Unknown fixture");
        };
    }

    private static void requireCopy() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) throw new IllegalStateException("Single-player QA only");
        var root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if (!root.getFileName().toString().equals(WORLD) || !Files.isRegularFile(root.resolve(MARKER))) {
            throw new IllegalStateException("Refusing to alter an unmarked world");
        }
    }

    private static void restoreWindow() {
        if (originalWindow == null) return;
        var mc = Minecraft.getInstance();
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), originalWindow[0], originalWindow[1]);
        mc.options.guiScale().set(originalGuiScale);
        originalWindow = null;
        mc.resizeDisplay();
    }

    private static void fail(Exception failure) {
        failed = true;
        restoreWindow();
        failure.printStackTrace();
        try {
            Files.createDirectories(Path.of(OUTPUT));
            Files.writeString(Path.of(OUTPUT).resolve("error.txt"), failure.toString());
        } catch (Exception ignored) { }
        Minecraft.getInstance().stop();
    }
}

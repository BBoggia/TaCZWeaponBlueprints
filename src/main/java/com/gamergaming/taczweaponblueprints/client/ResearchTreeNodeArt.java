package com.gamergaming.taczweaponblueprints.client;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.mojang.blaze3d.platform.NativeImage;
import com.tacz.guns.api.TimelessAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Slot artwork for UI nodes only. Physical blueprint item rendering remains independent. */
final class ResearchTreeNodeArt {
    record Art(ResourceLocation texture, int width, int height, int left, int top, int cropWidth, int cropHeight) { }
    private static volatile long reload;
    private final Map<ResourceLocation, Optional<Art>> textures = new HashMap<>();
    private final Map<ResourceLocation, Optional<Art>> nodes = new HashMap<>();
    private final Map<ResourceLocation, Optional<ResourceLocation>> bindings = new HashMap<>();
    private final Map<ResourceLocation, net.minecraft.world.item.ItemStack> fallbackItems = new HashMap<>();
    private long generation = -1;

    static void reloaded() { reload++; }
    static long reloadGeneration() { return reload; }
    void clearPresentation() { nodes.clear(); bindings.clear(); fallbackItems.clear(); }

    void prepare(ResearchTechTreeProjection projection) {
        if (generation != reload) { textures.clear(); generation = reload; }
        clearPresentation();
        var usedTextures = new java.util.HashSet<ResourceLocation>();
        for (var node : projection.graph().nodes()) {
            if (!node.visibility().revealsIcon()) continue;
            ResourceLocation texture = null;
            try {
                texture = switch (projection.domain()) {
                    case WEAPONS -> TimelessAPI.getGunDisplay(node.displaySlotId(), node.blueprintId())
                            .map(d -> d.getSlotTexture()).orElse(null);
                    case AMMO -> TimelessAPI.getClientAmmoIndex(node.blueprintId()).map(d -> d.getSlotTextureLocation()).orElse(null);
                    case ATTACHMENTS -> TimelessAPI.getClientAttachmentIndex(node.blueprintId()).map(d -> d.getSlotTexture()).orElse(null);
                };
            } catch (RuntimeException unavailable) { /* Missing or reloading pack: use the blueprint fallback. */ }
            if (texture != null) usedTextures.add(texture);
            bindings.put(node.blueprintId(), Optional.ofNullable(texture));
        }
        textures.keySet().retainAll(usedTextures);
    }

    private Optional<Art> read(ResourceLocation texture) {
        var resource = Minecraft.getInstance().getResourceManager().getResource(texture);
        if (resource.isEmpty()) return Optional.empty();
        try (var stream = resource.orElseThrow().open()) {
            byte[] bytes = stream.readNBytes(8 * 1024 * 1024 + 1);
            if (bytes.length > 8 * 1024 * 1024 || bytes.length < 24) return Optional.empty();
            // Slot art is PNG. Check its header dimensions before allocating the decoded image.
            var header = java.nio.ByteBuffer.wrap(bytes);
            if (header.getLong(0) != 0x89504e470d0a1a0aL) return Optional.empty();
            int width = header.getInt(16), height = header.getInt(20);
            if (width < 1 || height < 1 || (long) width * height > 4_194_304) return Optional.empty();
            try (var image = NativeImage.read(new ByteArrayInputStream(bytes))) {
                int left = width, top = height, right = -1, bottom = -1;
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    if ((image.getPixelRGBA(x, y) >>> 24) > 8) {
                        left = Math.min(left, x); right = Math.max(right, x); top = Math.min(top, y); bottom = Math.max(bottom, y);
                    }
                }
                if (right < left) return Optional.empty();
                return Optional.of(new Art(texture, width, height, left, top, right - left + 1, bottom - top + 1));
            }
        } catch (Exception unavailable) { return Optional.empty(); }
    }

    void render(GuiGraphics g, ResearchTreeGraph.Node node, ResearchTreeScene.Bounds box) {
        if (!node.visibility().revealsIcon()) return;
        // Decode only artwork that is actually drawn. State-only frames reuse the result.
        var art = nodes.computeIfAbsent(node.blueprintId(), id -> bindings.getOrDefault(id, Optional.empty())
                .flatMap(texture -> textures.computeIfAbsent(texture, this::read)));
        if (art.isPresent()) {
            var a = art.orElseThrow();
            var fit = ResearchTreeSceneView.fitArt(a.cropWidth(), a.cropHeight(), box);
            g.blit(a.texture(), (int) fit.x(), (int) fit.y(), Math.max(1, (int) fit.width()), Math.max(1, (int) fit.height()),
                    (float) a.left(), (float) a.top(), a.cropWidth(), a.cropHeight(), a.width(), a.height());
        } else {
            g.pose().pushPose();
            var fit = ResearchTreeSceneView.fitArt(16, 16, box);
            g.pose().translate(fit.x(), fit.y(), 0); g.pose().scale((float) fit.width() / 16, (float) fit.height() / 16, 1);
            g.renderItem(fallbackItems.computeIfAbsent(node.blueprintId(), id -> BlueprintItem.createBlueprint(id.toString())), 0, 0);
            g.flush(); g.pose().popPose();
        }
    }

    long resolved() { return nodes.values().stream().filter(Optional::isPresent).count(); }
    Map<ResourceLocation, Optional<Art>> snapshot() { return Map.copyOf(nodes); }
}

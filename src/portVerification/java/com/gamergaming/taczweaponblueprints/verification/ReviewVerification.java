package com.gamergaming.taczweaponblueprints.verification;

import com.gamergaming.taczweaponblueprints.research.designer.*;
import com.gamergaming.taczweaponblueprints.research.designer.io.*;
import com.gamergaming.taczweaponblueprints.research.designer.server.ResearchDesignerCaptureService;
import com.gamergaming.taczweaponblueprints.villager.*;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import static com.gamergaming.taczweaponblueprints.verification.CoreVerification.require;

/** Focused regressions against loaded production mixins, menus, codecs and export bytes. */
final class ReviewVerification {
    static boolean enabled() { return Boolean.getBoolean("wrb.verifyReviewFixes"); }
    static MerchantOffer offer(ItemStack secondary) {
        var offer = VillagerTradeOffers.create(ResourceLocation.parse("wrb_core_verification:price"),
                new ItemStack(Items.EMERALD, 20), secondary, new ItemStack(Items.DIAMOND), 8, 1, 0.05f);
        offer.setSpecialPriceDiff(-19);
        return offer;
    }
    static void run(MinecraftServer server) {
        try {
            pricing(server);
            missingItemCounts(server);
            export(server);
            CoreVerification.write(true, null);
            CoreVerification.LOG.info("WRB_REVIEW SERVER SUCCESS {} checks", CoreVerification.checks.size());
        } catch (Throwable failure) {
            CoreVerification.LOG.error("WRB_REVIEW SERVER FAILURE", failure);
            CoreVerification.write(false, failure.toString());
        } finally {
            CoreVerification.finished = true;
            CoreVerification.delay = 1;
        }
    }
    private static void missingItemCounts(MinecraftServer server) {
        var slot = ResourceLocation.parse("wrb_core_verification:missing_item");
        var offer = VillagerTradeOffers.create(slot, new ItemStack(Items.EMERALD), new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.DIAMOND), 8, 1, 0);
        var entry = new com.gamergaming.taczweaponblueprints.villager.stock.StockPlan.Entry(slot, slot, slot,
                ResourceLocation.parse("minecraft:diamond"),
                com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.Kind.CURRENCY, 1, 1,
                com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.Role.CORE,
                com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.Selection.NONE, List.of());
        var binding = new com.gamergaming.taczweaponblueprints.villager.stock.StockOfferBinding(entry, 1, 1);
        ((OwnedVillagerOffer) offer).taczweaponblueprints$binding(binding);
        offer.increaseUses();
        for (String name : List.of("buy", "buyB", "sell")) {
            var raw = OwnedOfferSerialization.save(offer, server.registryAccess());
            String originalId = raw.getCompound(name).getString("id");
            var stack = raw.getCompound(name);
            stack.putString("id", "removed:trade_item"); stack.remove("count");
            var data = new net.minecraft.nbt.CompoundTag(); data.putString("review_identity", name);
            var components = new net.minecraft.nbt.CompoundTag(); components.put("minecraft:custom_data", data);
            stack.put("components", components);
            var original = stack.copy();
            var suspended = OwnedOfferSerialization.read(raw, server.registryAccess());
            require(suspended.isOutOfStock() && !suspended.satisfiedBy(new ItemStack(Items.BARRIER, 64), new ItemStack(Items.BARRIER, 64))
                    && binding.equals(((OwnedVillagerOffer) suspended).taczweaponblueprints$binding()),
                    "implicit-count missing item keeps bound trade suspended: " + name);
            var saved = OwnedOfferSerialization.save(suspended, server.registryAccess());
            require(saved.getCompound(name).equals(original) && suspended.getUses() == 1,
                    "suspended save retains absent count, raw item/components and spent uses: " + name);
            require(OwnedOfferSerialization.save(suspended.copy(), server.registryAccess()).getCompound(name).equals(original),
                    "copied suspended offer preserves implicit-count raw item: " + name);
            var again = OwnedOfferSerialization.read(saved, server.registryAccess());
            require(again.isOutOfStock() && OwnedOfferSerialization.save(again, server.registryAccess()).getCompound(name).equals(original),
                    "suspended offer survives a second save/load: " + name);
            // Simulate the serialized item resolving again. Availability still belongs to the live stock refresh.
            saved.getCompound(name).putString("id", originalId);
            var restored = OwnedOfferSerialization.read(saved, server.registryAccess());
            ItemStack resolved = switch (name) { case "buy" -> restored.getBaseCostA(); case "buyB" -> restored.getCostB(); default -> restored.getResult(); };
            require(!resolved.is(Items.BARRIER) && resolved.getCount() == 1 && restored.getUses() == 1
                    && binding.equals(((OwnedVillagerOffer) restored).taczweaponblueprints$binding()),
                    "resolving the item restores its native one-item stack without resetting stock: " + name);
        }
    }
    private static void pricing(MinecraftServer server) {
        var offer = offer(ItemStack.EMPTY);
        require(offer.getCostA().getCount() == 15, "discounted owned price keeps 75 percent floor");
        for (int count : new int[]{1, 14}) {
            var payment = new ItemStack(Items.EMERALD, count);
            require(!offer.satisfiedBy(payment, ItemStack.EMPTY), "underpayment rejected: " + count);
            require(!offer.take(payment, ItemStack.EMPTY) && payment.getCount() == count,
                    "rejected payment untouched: " + count);
        }
        var payment = new ItemStack(Items.EMERALD, 20);
        require(offer.take(payment, ItemStack.EMPTY) && payment.getCount() == 5,
                "successful payment consumes displayed amount");
        require(!offer.satisfiedBy(new ItemStack(Items.DIAMOND, 64), ItemStack.EMPTY), "wrong currency rejected");
        require(offer.copy().getCostA().getCount() == 15 && !offer.copy().satisfiedBy(new ItemStack(Items.EMERALD), ItemStack.EMPTY),
                "copied owned offer keeps payment floor");
        var restored = OwnedOfferSerialization.read(OwnedOfferSerialization.save(offer, server.registryAccess()), server.registryAccess());
        require(restored.getCostA().getCount() == 15 && !restored.satisfiedBy(new ItemStack(Items.EMERALD), ItemStack.EMPTY),
                "saved owned offer keeps payment floor");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        try {
            MerchantOffer.STREAM_CODEC.encode(buffer, offer);
            var clientOffer = MerchantOffer.STREAM_CODEC.decode(buffer);
            require(clientOffer.getCostA().getCount() == 15 && !clientOffer.satisfiedBy(new ItemStack(Items.EMERALD), ItemStack.EMPTY),
                    "vanilla network offer matches server payment price");
        } finally { buffer.release(); }
        var vanilla = new MerchantOffer(new ItemCost(Items.EMERALD, 20), new ItemStack(Items.DIAMOND), 8, 1, 0.05f);
        vanilla.setSpecialPriceDiff(-19);
        require(vanilla.getCostA().getCount() == 1 && vanilla.satisfiedBy(new ItemStack(Items.EMERALD), ItemStack.EMPTY),
                "unowned vanilla discounts remain unchanged");
        var two = offer(new ItemStack(Items.IRON_INGOT, 3));
        require(!two.satisfiedBy(new ItemStack(Items.EMERALD, 15), new ItemStack(Items.IRON_INGOT, 2)), "secondary cost still required");
        var terms = ((OwnedVillagerOffer) offer).taczweaponblueprints$terms();
        ((OwnedVillagerOffer) offer).taczweaponblueprints$terms(new VillagerOfferTerms(terms.slot(), terms.minimumPrimary(), false, terms.revision()));
        require(!offer.satisfiedBy(new ItemStack(Items.EMERALD, 64), ItemStack.EMPTY) && offer.isOutOfStock(), "suspended owned offer still blocked");

        // Real vanilla menu result generation and shift-click, including reversed inputs.
        // An ordinary villager isolates the price hook from stock-publication authorization.
        var player = FakePlayerFactory.getMinecraft(server.overworld());
        for (boolean swap : List.of(false, true)) {
            player.getInventory().clearContent();
            var merchant = new Villager(EntityType.VILLAGER, server.overworld());
            var trade = offer(new ItemStack(Items.IRON_INGOT, 3));
            var offers = new MerchantOffers(); offers.add(trade); merchant.setOffers(offers); merchant.setTradingPlayer(player);
            var menu = new MerchantMenu(71, player.getInventory(), merchant);
            int first = swap ? 1 : 0, second = swap ? 0 : 1;
            menu.getSlot(first).set(new ItemStack(Items.EMERALD, 1));
            menu.getSlot(second).set(new ItemStack(Items.IRON_INGOT, 3));
            require(menu.getSlot(2).getItem().isEmpty() && menu.quickMoveStack(player, 2).isEmpty()
                    && menu.getSlot(first).getItem().getCount() == 1 && trade.getUses() == 0,
                    "native menu rejects underpaid shift-click, swapped=" + swap);
            menu.getSlot(first).set(new ItemStack(Items.EMERALD, 15));
            require(menu.getSlot(2).getItem().is(Items.DIAMOND), "native menu accepts exact payment, swapped=" + swap);
            menu.quickMoveStack(player, 2);
            require(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty()
                    && player.getInventory().countItem(Items.DIAMOND) == 1 && trade.getUses() == 1,
                    "native menu pays and delivers once, swapped=" + swap);
        }
        player.getInventory().clearContent();
    }
    private static void export(MinecraftServer server) throws Exception {
        var capture = ResearchDesignerCaptureService.capture(server, UUID.randomUUID());
        var recipes = capture.json().getAsJsonObject("registry_facts").getAsJsonObject("recipes");
        boolean largeBatch = false;
        for (var entry : recipes.entrySet()) {
            var record = entry.getValue().getAsJsonObject();
            var source = server.getRecipeManager().byKey(ResourceLocation.parse(entry.getKey())).orElseThrow();
            var output = ((com.tacz.guns.crafting.GunSmithTableRecipe) source.value()).getOutput();
            if (output.getCount() <= 99) continue;
            largeBatch = true;
            var stored = net.minecraft.nbt.TagParser.parseTag(record.get("output_nbt").getAsString());
            var identity = ItemStack.parseOptional(server.registryAccess(), stored);
            require(record.get("output_count").getAsInt() == output.getCount() && identity.getCount() == 1
                    && ItemStack.isSameItemSameComponents(output, identity),
                    "large recipe batch retains quantity and item components: " + entry.getKey());
        }
        require(largeBatch, "stock catalog exercises recipe quantities beyond the stack codec limit");
        var project = ResearchDesignerConversion.createCustom(capture, "review_export", "Export regression").project();
        var prepared = ResearchDesignerExport.prepare(new ResearchDesignerSession(project).checkpoint(), Optional.of(capture));
        var files = DesignerArchive.read(prepared.datapack());
        var metadata = DesignerJson.parse(files.get("pack.mcmeta"), 4096).getAsJsonObject("pack");
        var review = DesignerJson.parse(prepared.review(), DesignerArchive.MAX_ENTRY);
        require(metadata.get("pack_format").getAsInt() == 48 && review.get("pack_format").getAsInt() == 48,
                "exported pack and review declare 1.21.1 format 48");
        require(review.get("minecraft").getAsString().equals("1.21.1") && prepared.instructions().contains("Minecraft 1.21.1 (pack format 48)"),
                "review and install instructions declare the running version");
        require(!ResearchDesignerExport.resources(files).isEmpty(), "exported definitions round-trip through importer");
        require(ResearchDesignerExport.readProject(prepared.projectBundle()).checkpoint().project().equals(project),
                "editable project bundle retains its existing format and contents");
        var altered = new TreeMap<>(files);
        metadata.addProperty("pack_format", 15);
        var meta = new com.google.gson.JsonObject(); meta.add("pack", metadata);
        altered.put("pack.mcmeta", DesignerJson.bytes(meta, 4096));
        boolean rejected = false;
        try { ResearchDesignerExport.resources(altered); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "old datapack format rejected by exported-resource validation");
        // Make actual files available for visual/export inspection without activating a world pack.
        Files.write(Path.of("review-datapack.zip"), prepared.datapack());
        Files.write(Path.of("review-project.zip"), prepared.projectBundle());
        Files.write(Path.of("review-export.json"), prepared.review());
        Files.writeString(Path.of("review-INSTALL.txt"), prepared.instructions());
    }
}

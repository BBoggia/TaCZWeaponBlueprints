package com.gamergaming.taczweaponblueprints.testing;

import com.gamergaming.taczweaponblueprints.item.BlueprintFragmentItem;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;

/** Registers real item fixtures without starting a client or loading mod configuration. */
public final class AnalysisTestItems {
    public static BlueprintFragmentItem fragment;
    public static BlueprintItem blueprint;
    public static TestGun gun;
    private AnalysisTestItems() { }
    public static synchronized void initialize() {
        if (fragment != null) return;
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var registry = (MappedRegistry<Item>) BuiltInRegistries.ITEM;
        var forge = (ForgeRegistry<Item>) ForgeRegistries.ITEMS;
        registry.unfreeze(); forge.unfreeze();
        try {
            fragment = new BlueprintFragmentItem(new Item.Properties());
            forge.register(new ResourceLocation("analysis_test:fragment"), fragment);
            gun = new TestGun();
            forge.register(new ResourceLocation("analysis_test:gun"), gun);
            blueprint = new BlueprintItem(new Item.Properties());
            forge.register(new ResourceLocation("analysis_test:blueprint"), blueprint);
        } catch (RuntimeException exception) {
            try { registry.freeze(); } catch (RuntimeException freeze) { exception.addSuppressed(freeze); }
            forge.freeze(); throw exception;
        }
        registry.freeze(); forge.freeze();
    }
    /** Uses TaCZ NBT defaults; no rendering, Lua runtime, or add-on classes are needed. */
    public static final class TestGun extends Item implements com.tacz.guns.api.item.nbt.GunItemDataAccessor {
        TestGun() { super(new Item.Properties().stacksTo(1)); }
        public int getLevel(int arg0) { throw new AssertionError("unexpected gun access: getLevel"); }
        public int getExp(int arg0) { throw new AssertionError("unexpected gun access: getExp"); }
        public int getMaxLevel() { throw new AssertionError("unexpected gun access: getMaxLevel"); }
        public void dropAllAmmo(net.minecraft.world.entity.player.Player arg0, net.minecraft.world.item.ItemStack arg1) { throw new AssertionError("unexpected gun access: dropAllAmmo"); }
        public boolean allowAttachment(net.minecraft.world.item.ItemStack arg0, net.minecraft.world.item.ItemStack arg1) { throw new AssertionError("unexpected gun access: allowAttachment"); }
        public boolean allowAttachmentType(net.minecraft.world.item.ItemStack arg0, com.tacz.guns.api.item.attachment.AttachmentType arg1) { throw new AssertionError("unexpected gun access: allowAttachmentType"); }
        public boolean useInventoryAmmo(net.minecraft.world.item.ItemStack arg0) { throw new AssertionError("unexpected gun access: useInventoryAmmo"); }
        public boolean hasInventoryAmmo(net.minecraft.world.entity.LivingEntity arg0, net.minecraft.world.item.ItemStack arg1, boolean arg2) { throw new AssertionError("unexpected gun access: hasInventoryAmmo"); }
        public int getRPM(net.minecraft.world.item.ItemStack arg0) { throw new AssertionError("unexpected gun access: getRPM"); }
        public boolean isCanCrawl(net.minecraft.world.item.ItemStack arg0) { throw new AssertionError("unexpected gun access: isCanCrawl"); }
    }
}

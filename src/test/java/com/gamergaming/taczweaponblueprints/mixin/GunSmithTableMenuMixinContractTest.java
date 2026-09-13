package com.gamergaming.taczweaponblueprints.mixin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/** Pins the third-party TaCZ bytecode surface used to mark crafted guns. */
class GunSmithTableMenuMixinContractTest {
    @Test
    void namespaceScaffoldOnlyFeedsTheNativeFilterWidgetConstructor() throws Exception {
        var handler = GunSmithTableScreenMixin.class.getDeclaredMethod("taczweaponblueprints$magazineNamespace", java.util.Map.class);
        var modify = handler.getAnnotation(org.spongepowered.asm.mixin.injection.ModifyArg.class);
        assertArrayEquals(new String[] {"init"}, modify.method());
        assertTrue(modify.remap(), "init is a Minecraft override");
        assertFalse(modify.at().remap());
        assertEquals(6, modify.index());
        assertEquals(1, modify.require());
        assertEquals(1, modify.allow());
        var constructor = com.tacz.guns.client.gui.components.GunPackList.class.getConstructor(
                net.minecraft.client.Minecraft.class, int.class, int.class, int.class, int.class, int.class,
                java.util.Map.class, com.tacz.guns.client.gui.GunSmithTableScreen.class);
        assertEquals("Lcom/tacz/guns/client/gui/components/GunPackList;<init>"
                + org.objectweb.asm.Type.getConstructorDescriptor(constructor), modify.at().target());
    }

    @Test
    void nativeMagazineTabMembershipRunsInsideRecipeLookupAfterTheCraftAuthority() throws Exception {
        String name = "lambda$getRecipe$2";
        String descriptor = "(Lcom/tacz/guns/crafting/GunSmithTableRecipe;Lcom/tacz/guns/resource/index/CommonBlockIndex;)Ljava/lang/Boolean;";
        var handler = GunSmithTableMenuMixin.class.getDeclaredMethod("taczweaponblueprints$acceptMagazineTab",
                GunSmithTableRecipe.class, com.tacz.guns.resource.index.CommonBlockIndex.class, CallbackInfoReturnable.class);
        var inject = handler.getAnnotation(Inject.class);
        assertArrayEquals(new String[] {name + descriptor}, inject.method());
        assertEquals("RETURN", inject.at()[0].value());
        assertTrue(inject.cancellable());
        assertFalse(inject.remap());
        assertEquals(1, inject.require());
        assertEquals(1, inject.allow());
        var predicate = GunSmithTableMenu.class.getDeclaredMethod(name,
                GunSmithTableRecipe.class, com.tacz.guns.resource.index.CommonBlockIndex.class);
        assertEquals(Boolean.class, predicate.getReturnType());
        assertTrue(java.lang.reflect.Modifier.isStatic(predicate.getModifiers()));
        assertTrue(relatedMethodCalls(GunSmithTableMenu.class, "doCraft").stream()
                .anyMatch(call -> call.endsWith("GunSmithTableMenu.getRecipe")));
        assertTrue(relatedMethodCalls(GunSmithTableMenuMixin.class, "taczweaponblueprints$acceptMagazineTab").stream()
                .anyMatch(call -> call.endsWith("MagazineCraftingAccess.hasMagazineTab")));
    }

    @Test
    void magazineTabRedirectMatchesTheSingleNativeBlockTabRead() throws Exception {
        String name = "lambda$classifyRecipes$0";
        String descriptor = "(Lnet/minecraft/resources/ResourceLocation;Ljava/util/Map;Ljava/util/Map;Lcom/tacz/guns/resource/index/CommonBlockIndex;)V";
        var handler = GunSmithTableScreenMixin.class.getDeclaredMethod("taczweaponblueprints$magazineTabs",
                com.tacz.guns.resource.pojo.data.block.BlockData.class, ResourceLocation.class,
                java.util.Map.class, java.util.Map.class, com.tacz.guns.resource.index.CommonBlockIndex.class);
        var redirect = handler.getAnnotation(Redirect.class);
        assertArrayEquals(new String[] {name + descriptor}, redirect.method());
        assertFalse(redirect.remap());
        assertFalse(redirect.at().remap());
        assertEquals(1, redirect.require());
        assertEquals(1, redirect.allow());
        AtomicInteger reads = new AtomicInteger();
        try (var bytes = com.tacz.guns.client.gui.GunSmithTableScreen.class.getResourceAsStream(
                "/com/tacz/guns/client/gui/GunSmithTableScreen.class")) {
            assertNotNull(bytes);
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String method, String desc, String signature, String[] exceptions) {
                    if (!name.equals(method) || !descriptor.equals(desc)) return null;
                    assertTrue((access & Opcodes.ACC_STATIC) != 0);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) {
                            if ("com/tacz/guns/resource/pojo/data/block/BlockData".equals(owner)
                                    && "getTabs".equals(method) && "()Ljava/util/List;".equals(desc)) reads.incrementAndGet();
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertEquals(1, reads.get(), "TaCZ tab assembly callback changed");
        var filter = GunSmithTableScreenMixin.class.getDeclaredMethod("filterRecipesByLearnedBlueprints", CallbackInfo.class);
        var inject = filter.getAnnotation(Inject.class);
        assertArrayEquals(new String[] {"updateSelectedRecipeAfterFiltering"}, inject.method());
        assertEquals("HEAD", inject.at()[0].value());
        var calls = relatedMethodCalls(com.tacz.guns.client.gui.GunSmithTableScreen.class, "init");
        int classification = indexEndingWith(calls, "GunSmithTableScreen.classifyRecipes");
        int selection = indexEndingWith(calls, "GunSmithTableScreen.updateSelectedRecipeAfterFiltering");
        int buttons = indexEndingWith(calls, "GunSmithTableScreen.addTypeButtons");
        assertTrue(classification >= 0 && selection > classification && buttons > selection,
                "access filtering must follow all classification callbacks and precede native tab buttons");
    }

    private static final String CRAFT_LAMBDA = "lambda$doCraft$3";
    private static final String CRAFT_LAMBDA_DESCRIPTOR =
            "(Lnet/minecraft/world/entity/player/Player;"
                    + "Lcom/tacz/guns/crafting/GunSmithTableRecipe;"
                    + "Lnet/minecraftforge/items/IItemHandler;)V";
    private static final String COPY_DESCRIPTOR =
            "()Lnet/minecraft/world/item/ItemStack;";

    @Test
    void redirectMatchesThePinnedTaCZCraftingLambdaAndItsSingleCopyCall()
            throws ReflectiveOperationException, IOException {
        Method handler = GunSmithTableMenuMixin.class.getDeclaredMethod(
                "markSurvivalCraftedOutput",
                ItemStack.class,
                Player.class,
                GunSmithTableRecipe.class,
                IItemHandler.class);
        Redirect redirect = handler.getAnnotation(Redirect.class);
        assertNotNull(redirect);
        assertArrayEquals(
                new String[] {CRAFT_LAMBDA + CRAFT_LAMBDA_DESCRIPTOR},
                redirect.method());
        assertEquals(1, redirect.require());
        assertEquals(1, redirect.allow());
        assertFalse(redirect.remap(), "the third-party synthetic method name must stay literal");
        assertEquals(
                "Lnet/minecraft/world/item/ItemStack;copy()"
                        + "Lnet/minecraft/world/item/ItemStack;",
                redirect.at().target());
        assertTrue(redirect.at().remap());

        AtomicBoolean lambdaFound = new AtomicBoolean();
        AtomicInteger lambdaCopies = new AtomicInteger();
        AtomicInteger outerMethodCopies = new AtomicInteger();
        try (InputStream bytecode = GunSmithTableMenu.class.getResourceAsStream(
                "/com/tacz/guns/inventory/GunSmithTableMenu.class")) {
            assertNotNull(bytecode);
            new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions) {
                    boolean craftLambda = CRAFT_LAMBDA.equals(name)
                            && CRAFT_LAMBDA_DESCRIPTOR.equals(descriptor);
                    boolean outerCraft = "doCraft".equals(name);
                    lambdaFound.compareAndSet(false, craftLambda);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String invokedName,
                                String invokedDescriptor,
                                boolean isInterface) {
                            if ("net/minecraft/world/item/ItemStack".equals(owner)
                                    && "copy".equals(invokedName)
                                    && COPY_DESCRIPTOR.equals(invokedDescriptor)) {
                                if (craftLambda) {
                                    lambdaCopies.incrementAndGet();
                                }
                                if (outerCraft) {
                                    outerMethodCopies.incrementAndGet();
                                }
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertTrue(lambdaFound.get(), "TaCZ crafting lambda descriptor changed");
        assertEquals(1, lambdaCopies.get(), "TaCZ crafting lambda copy call changed");
        assertEquals(0, outerMethodCopies.get(), "copy unexpectedly moved back into doCraft");
    }

    @Test
    void serverAuthorizationStillGuardsTheNativeCraftEntryPoint()
            throws ReflectiveOperationException, IOException {
        Method handler = GunSmithTableMenuMixin.class.getDeclaredMethod(
                "requireLearnedRecipe",
                ResourceLocation.class,
                Player.class,
                CallbackInfo.class);
        Inject inject = handler.getAnnotation(Inject.class);
        assertNotNull(inject);
        assertArrayEquals(new String[] {"doCraft"}, inject.method());
        assertEquals(1, inject.at().length);
        assertEquals("HEAD", inject.at()[0].value());
        assertTrue(inject.cancellable());
        assertFalse(inject.remap());

        List<String> calls = relatedMethodCalls(
                GunSmithTableMenuMixin.class,
                "requireLearnedRecipe");
        assertTrue(calls.stream().anyMatch(call -> call.endsWith(
                "CraftingEligibilityService.evaluate")));
        assertTrue(calls.stream().anyMatch(call -> call.endsWith(
                "CallbackInfo.cancel")));
        assertTrue(calls.stream().anyMatch(call -> call.endsWith(
                "Player.displayClientMessage")));
    }

    @Test
    void nativeValidityIsReplacedByPhysicalWorkbenchAuthority()
            throws ReflectiveOperationException {
        Method handler = GunSmithTableMenuMixin.class.getDeclaredMethod(
                "validatePhysicalWorkbench",
                Player.class,
                CallbackInfoReturnable.class);
        Inject inject = handler.getAnnotation(Inject.class);

        assertNotNull(inject);
        assertArrayEquals(new String[] {"stillValid"}, inject.method());
        assertEquals("HEAD", inject.at()[0].value());
        assertTrue(inject.cancellable());
        assertTrue(inject.remap(), "Minecraft overrides must map to production names");
    }

    @Test
    void menuProviderOverridePinsBothNamespacesAndExactlyOneTarget() throws ReflectiveOperationException {
        var handler = GunSmithTableBlockEntityMixin.class.getDeclaredMethod("attachPhysicalWorkbench",
                int.class, net.minecraft.world.entity.player.Inventory.class, Player.class, CallbackInfoReturnable.class);
        var inject = handler.getAnnotation(Inject.class);
        String descriptor = "(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/inventory/AbstractContainerMenu;";
        assertArrayEquals(new String[] {"createMenu" + descriptor, "m_7208_" + descriptor}, inject.method());
        assertEquals(1, inject.require()); assertEquals(1, inject.allow());
        assertFalse(inject.remap());
    }

    @Test
    void screenInitializationUsesTheMinecraftOverrideMapping() throws ReflectiveOperationException {
        var handler = GunSmithTableScreenMixin.class.getDeclaredMethod("requestCraftingAccess", CallbackInfo.class);
        var inject = handler.getAnnotation(Inject.class);
        assertArrayEquals(new String[] {"init"}, inject.method());
        assertTrue(inject.remap());
    }

    @Test
    void craftingAccessAcceptsOnlyOneBoundedRetryPerMenuSession() {
        GunSmithTableMenuMixin mixin = new GunSmithTableMenuMixin() {
        };

        assertTrue(mixin.taczweaponblueprints$acceptCraftingAccessRequest(41L));
        assertEquals(41L, mixin.taczweaponblueprints$craftingAccessRequestId());
        assertTrue(mixin.taczweaponblueprints$acceptCraftingAccessRequest(41L));
        assertFalse(mixin.taczweaponblueprints$acceptCraftingAccessRequest(41L));
        assertFalse(mixin.taczweaponblueprints$acceptCraftingAccessRequest(42L));
        assertFalse(mixin.taczweaponblueprints$acceptCraftingAccessRequest(0L));
        assertEquals(41L, mixin.taczweaponblueprints$craftingAccessRequestId());
    }

    @Test
    void craftingAccessSnapshotIdsAdvanceWithoutWrapping() throws Exception {
        GunSmithTableMenuMixin mixin = new GunSmithTableMenuMixin() {
        };

        assertEquals(1L, mixin.taczweaponblueprints$nextCraftingAccessSnapshotId());
        assertEquals(2L, mixin.taczweaponblueprints$nextCraftingAccessSnapshotId());
        var field = GunSmithTableMenuMixin.class.getDeclaredField(
                "taczweaponblueprints$craftingAccessSnapshotId");
        field.setAccessible(true);
        field.setLong(mixin, Long.MAX_VALUE);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                mixin::taczweaponblueprints$nextCraftingAccessSnapshotId);
    }

    @Test
    void craftedOutputIsCopiedBeforeOriginStamping() throws IOException {
        List<String> calls = relatedMethodCalls(
                GunSmithTableMenuMixin.class,
                "markSurvivalCraftedOutput");
        int copy = indexEndingWith(calls, "ItemStack.copy");
        int stamp = indexEndingWith(calls, "PhysicalWeaponProvenance.stampCrafted");

        assertTrue(copy >= 0, "crafted output copy call is missing");
        assertTrue(stamp > copy, "crafted origin must be stamped onto the copied output");
    }

    private static List<String> relatedMethodCalls(Class<?> type, String methodName)
            throws IOException {
        List<String> calls = new ArrayList<>();
        try (InputStream bytecode = type.getResourceAsStream(
                "/" + type.getName().replace('.', '/') + ".class")) {
            assertNotNull(bytecode);
            new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions) {
                    if (!methodName.equals(name)
                            && !name.startsWith("lambda$" + methodName + "$")) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String invokedName,
                                String invokedDescriptor,
                                boolean isInterface) {
                            int slash = owner.lastIndexOf('/');
                            calls.add(owner.substring(slash + 1) + "." + invokedName);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return calls;
    }

    private static int indexEndingWith(List<String> calls, String suffix) {
        for (int index = 0; index < calls.size(); index++) {
            if (calls.get(index).endsWith(suffix)) {
                return index;
            }
        }
        return -1;
    }
}

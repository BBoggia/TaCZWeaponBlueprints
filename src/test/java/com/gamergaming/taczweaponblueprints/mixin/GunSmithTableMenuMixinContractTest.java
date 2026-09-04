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
        assertFalse(inject.remap());
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

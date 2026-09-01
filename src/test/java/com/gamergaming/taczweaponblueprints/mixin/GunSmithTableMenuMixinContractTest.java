package com.gamergaming.taczweaponblueprints.mixin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;

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
}

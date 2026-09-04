package com.gamergaming.taczweaponblueprints.compat.fzzy_config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Guards config editors whose widget signatures reference client-only classes. */
class ConfigClientBoundaryContractTest {
    private static final String ONLY_IN_DESCRIPTOR =
            "Lnet/minecraftforge/api/distmarker/OnlyIn;";

    @Test
    void autocompleteWidgetIsMarkedClientOnly() throws IOException {
        assertWidgetEntryIsClientOnly(BlueprintIdAutocompleteString.class.getName());
    }

    @Test
    void preservingChoiceWidgetIsMarkedClientOnly() throws IOException {
        assertWidgetEntryIsClientOnly(PreservingStringChoiceList.class.getName());
    }

    @Test
    void clientConfigCallbacksCannotRebuildServerCraftingAuthority() throws IOException {
        String configClass = BlueprintConfig.class.getName();
        assertTrue(methodInvokes(configClass, "onUpdateClient", "normalizeAndPublish"));
        assertTrue(methodInvokes(configClass, "onSyncClient", "publishProgressionSnapshot"));
        assertFalse(methodInvokes(configClass, "onUpdateClient", "rebuildProgressionPolicy"));
        assertFalse(methodInvokes(configClass, "onSyncClient", "rebuildProgressionPolicy"));
        assertTrue(methodInvokes(configClass, "onUpdateServer", "rebuildProgressionPolicy"));
        assertTrue(methodInvokes(configClass, "onUpdateServer", "refreshOnlinePlayers"));
    }

    private static void assertWidgetEntryIsClientOnly(String className) throws IOException {
        AtomicBoolean foundWidgetEntry = new AtomicBoolean();
        AtomicBoolean foundClientBoundary = new AtomicBoolean();
        String resourcePath = className.replace('.', '/') + ".class";
        ClassLoader loader = ConfigClientBoundaryContractTest.class.getClassLoader();

        try (InputStream stream = loader.getResourceAsStream(resourcePath)) {
            assertNotNull(stream, resourcePath);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions) {
                    if (!"widgetEntry".equals(name)) {
                        return null;
                    }
                    foundWidgetEntry.set(true);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(
                                String annotationDescriptor,
                                boolean visible) {
                            if (ONLY_IN_DESCRIPTOR.equals(annotationDescriptor)) {
                                foundClientBoundary.set(true);
                            }
                            return null;
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        assertTrue(foundWidgetEntry.get(), className + " must retain its custom widgetEntry");
        assertTrue(
                foundClientBoundary.get(),
                className + " widgetEntry must be stripped on dedicated servers");
    }

    private static boolean methodInvokes(
            String className,
            String methodName,
            String invokedMethodName) throws IOException {
        AtomicBoolean foundMethod = new AtomicBoolean();
        AtomicBoolean foundInvocation = new AtomicBoolean();
        String resourcePath = className.replace('.', '/') + ".class";
        ClassLoader loader = ConfigClientBoundaryContractTest.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(resourcePath)) {
            assertNotNull(stream, resourcePath);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions) {
                    if (!methodName.equals(name)) {
                        return null;
                    }
                    foundMethod.set(true);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String name,
                                String descriptor,
                                boolean isInterface) {
                            if (invokedMethodName.equals(name)) {
                                foundInvocation.set(true);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertTrue(foundMethod.get(), className + " must retain " + methodName);
        return foundInvocation.get();
    }
}

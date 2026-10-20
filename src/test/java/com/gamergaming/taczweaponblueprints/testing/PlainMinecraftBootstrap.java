package com.gamergaming.taczweaponblueprints.testing;

import java.util.List;
import java.util.Map;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/** Supplies the empty discovery context required by NeoForge's vanilla registry bootstrap.
 * No mod entry points, mixins, or game worlds run in the plain domain/bytecode suite. */
public final class PlainMinecraftBootstrap implements LauncherSessionListener {
    @Override public void launcherSessionOpened(LauncherSession session) {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
}

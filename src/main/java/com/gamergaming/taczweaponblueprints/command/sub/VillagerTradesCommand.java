package com.gamergaming.taczweaponblueprints.command.sub;

import java.io.IOException;
import java.nio.file.*;
import com.gamergaming.taczweaponblueprints.villager.trade.*;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

/** Inherits /gg's operator permission gate. Exports only public definitions and global eligibility. */
public final class VillagerTradesCommand {
    private VillagerTradesCommand() {}
    public static LiteralArgumentBuilder<CommandSourceStack> get() {
        return Commands.literal("villager_trades")
                .then(Commands.literal("status").executes(c -> status(c.getSource())))
                .then(Commands.literal("inspect").then(Commands.argument("slot", ResourceLocationArgument.id())
                        .executes(c -> {
                            var slot = ResourceLocationArgument.getId(c, "slot");
                            var row = TradeRuntime.inspect().resolution().slots().get(slot);
                            if (row == null) { c.getSource().sendFailure(Component.translatable("commands.taczweaponblueprints.villager.missing", slot)); return 0; }
                            c.getSource().sendSuccess(() -> Component.literal(TradeDiagnostics.row(row).toString()), false); return 1;
                        })))
                .then(Commands.literal("export").executes(c -> export(c.getSource())));
    }
    private static int status(CommandSourceStack source) {
        var report = TradeRuntime.inspect(); var result = report.resolution();
        source.sendSuccess(() -> Component.translatable("commands.taczweaponblueprints.villager.status",
                report.definitionsRevision(), report.catalogRevision(), result.currency(),
                result.slots().values().stream().filter(TradeResolver.Row::ready).count(), result.slots().size()), false);
        result.acquisition().forEach((profession, reason) -> source.sendSuccess(() -> Component.literal(profession + ": " + reason), false));
        report.reloadFailure().ifPresent(reason -> source.sendFailure(Component.translatable("commands.taczweaponblueprints.villager.failure", reason)));
        return 1;
    }
    private static int export(CommandSourceStack source) {
        Path temporary = null;
        try {
            Path directory = source.getServer().getWorldPath(LevelResource.ROOT).resolve("reports");
            Files.createDirectories(directory);
            Path destination = directory.resolve("villager-trades.json");
            temporary = Files.createTempFile(directory, "villager-trades-", ".tmp");
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(TradeDiagnostics.json(TradeRuntime.inspect())) + "\n");
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
            source.sendSuccess(() -> Component.translatable("commands.taczweaponblueprints.villager.exported", destination.toString()), false);
            return 1;
        } catch (IOException e) {
            source.sendFailure(Component.translatable("commands.taczweaponblueprints.villager.export_failed", e.getMessage())); return 0;
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }
}

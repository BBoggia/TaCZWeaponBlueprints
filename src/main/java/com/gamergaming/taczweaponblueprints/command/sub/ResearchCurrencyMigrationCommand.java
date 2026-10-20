package com.gamergaming.taczweaponblueprints.command.sub;

import java.util.UUID;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.progression.currency.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

/** Explicit operator-only online-player migration. Every confirmation consumes one preview. */
public final class ResearchCurrencyMigrationCommand {
    public static final ResearchCurrencyWithdrawals PENDING = new ResearchCurrencyWithdrawals();
    private ResearchCurrencyMigrationCommand() { }
    public static LiteralArgumentBuilder<CommandSourceStack> addTo(LiteralArgumentBuilder<CommandSourceStack> node) {
        return node.requires(s -> s.hasPermission(2))
            .then(Commands.literal("inspect").then(Commands.argument("player", EntityArgument.player()).executes(ResearchCurrencyMigrationCommand::inspect)))
            .then(Commands.literal("legacy").then(Commands.literal("apply").executes(c -> {
                boolean persisted = ModConfigs.BLUEPRINT.applyLegacyEconomy(c.getSource().getServer());
                c.getSource().sendSuccess(() -> text(persisted ? "legacy" : "legacy_save_failed"), true);
                PENDING.clear();
                return persisted ? 1 : 0;
            })))
            .then(Commands.literal("withdraw")
                .then(Commands.literal("preview").then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1, 2304)).executes(ResearchCurrencyMigrationCommand::preview))))
                .then(Commands.literal("confirm").then(Commands.argument("token", StringArgumentType.word()).executes(ResearchCurrencyMigrationCommand::confirm))));
    }
    private static String owner(CommandSourceStack source) {
        return source.getEntity() == null ? source.getTextName() + ":" + source.getLevel().dimension().location() + ":" + source.getPosition()
                : source.getEntity().getUUID().toString();
    }
    private static long tick(CommandSourceStack source) { return source.getServer().overworld().getGameTime(); }
    private static int inspect(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var player = EntityArgument.getPlayer(c, "player");
        var data = ModAttachments.recipeData(player).orElse(null);
        if (data == null) { c.getSource().sendFailure(text("unavailable")); return 0; }
        var live = ResearchCurrencyTransaction.guarded(PlayerResearchCurrencyContext.inventory(player, data),
                () -> player.server.getPlayerList().getPlayer(player.getUUID()) == player);
        c.getSource().sendSuccess(() -> text("inspect", player.getDisplayName(), ResearchCurrencyText.name(live.policy()),
                ResearchCurrencyPayments.balance(live.policy(), player.getInventory().items, data), data.getResearchPoints(),
                ModConfigs.BLUEPRINT.dataSettings().behavior().name()), false);
        return 1;
    }
    private static int preview(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var player = EntityArgument.getPlayer(c, "player");
        var data = ModAttachments.recipeData(player).orElse(null);
        if (data == null) { c.getSource().sendFailure(text("unavailable")); return 0; }
        var live = ResearchCurrencyTransaction.guarded(PlayerResearchCurrencyContext.inventory(player, data),
                () -> player.server.getPlayerList().getPlayer(player.getUUID()) == player);
        var plan = ResearchCurrencyPlanner.prepareWithdrawal(live.policy(), live.read(), IntegerArgumentType.getInteger(c, "amount"));
        if (!live.valid()) { c.getSource().sendFailure(text("unavailable")); return 0; }
        if (!plan.quote().ready()) { c.getSource().sendFailure(text("blocked." + plan.quote().status().name().toLowerCase(java.util.Locale.ROOT))); return 0; }
        var token = PENDING.preview(owner(c.getSource()), player.getUUID(), tick(c.getSource()), plan, live);
        c.getSource().sendSuccess(() -> text("preview", player.getDisplayName(), plan.quote().deliveredReward(),
                ResearchCurrencyText.name(plan.quote().policy()), plan.before().bankedPoints(), plan.after().bankedPoints(), token.toString()), false);
        return 1;
    }
    private static int confirm(CommandContext<CommandSourceStack> c) {
        UUID token;
        try { token = UUID.fromString(StringArgumentType.getString(c, "token")); }
        catch (IllegalArgumentException e) { c.getSource().sendFailure(text("expired")); return 0; }
        var pending = PENDING.take(token, owner(c.getSource()), tick(c.getSource())).orElse(null);
        if (pending == null) { c.getSource().sendFailure(text("expired")); return 0; }
        var result = ResearchCurrencyTransaction.commit(pending.plan(), pending.context(), ResearchCurrencyTransaction.NONE);
        TaCZWeaponBlueprints.LOGGER.info("RP withdrawal {} by {} for {}: {}, requested {}, retained RP {}", token,
                pending.owner(), pending.player(), result.status(), pending.plan().quote().requestedReward(), pending.context().read().bankedPoints());
        if (!result.successful()) { c.getSource().sendFailure(text("failed." + result.status().name().toLowerCase(java.util.Locale.ROOT))); return 0; }
        var player = c.getSource().getServer().getPlayerList().getPlayer(pending.player());
        if (player != null) { player.inventoryMenu.broadcastChanges(); NetworkHandler.syncPlayerPointBalance(player); }
        c.getSource().sendSuccess(() -> text("complete", pending.player().toString(), result.deliveredUnits(),
                ResearchCurrencyText.name(result.quote().policy()), pending.plan().after().bankedPoints()), true);
        return 1;
    }
    private static Component text(String key, Object... args) { return Component.translatable("commands.taczweaponblueprints.currency_migration." + key, args); }
}

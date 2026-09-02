package com.gamergaming.taczweaponblueprints.progression;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.CombatFacts;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.CreditType;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.Difficulty;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.SpawnProvenance;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;

/** Captures persistent spawn facts and normalizes one authoritative death. */
public final class ResearchPointCombatTracker {
    private static final String DATA_PREFIX = TaCZWeaponBlueprints.MODID + ":research_point_combat/";
    private static final String BORN_AT_KEY = DATA_PREFIX + "born_at";
    private static final String PROVENANCE_KEY = DATA_PREFIX + "spawn_provenance";
    private static final Set<ResourceLocation> COMMON_BOSS_TAGS = Set.of(
            new ResourceLocation("forge", "bosses"),
            new ResourceLocation("forge", "boss"),
            new ResourceLocation("c", "bosses"),
            new ResourceLocation("c", "boss"));
    private static final TagKey<net.minecraft.world.damagesource.DamageType> TACZ_BULLETS =
            TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("tacz", "bullets"));
    private static final ResearchPointCombatRecentEvents RECENT_DEATHS =
            new ResearchPointCombatRecentEvents();

    private ResearchPointCombatTracker() {
    }

    public static void recordSpawn(Mob mob, MobSpawnType spawnType, long gameTime) {
        if (mob == null || spawnType == null || gameTime < 0L) {
            return;
        }
        CompoundTag data = mob.getPersistentData();
        data.putLong(BORN_AT_KEY, gameTime);
        data.putString(PROVENANCE_KEY, classifySpawnType(spawnType).name());
    }

    /** Records a conservative lifetime origin without inventing missing provenance. */
    public static void recordFirstServerJoin(LivingEntity entity, long gameTime) {
        if (entity == null || gameTime < 0L) {
            return;
        }
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(BORN_AT_KEY, Tag.TAG_LONG)) {
            data.putLong(BORN_AT_KEY, gameTime);
        }
    }

    public static Optional<CombatEvent> capture(
            LivingEntity victim,
            DamageSource source,
            long gameTime) {
        try {
            return captureValidated(victim, source, gameTime);
        } catch (RuntimeException exception) {
            TaCZWeaponBlueprints.LOGGER.error(
                    "Failed closed while capturing Research Point combat facts",
                    exception);
            return Optional.empty();
        }
    }

    private static Optional<CombatEvent> captureValidated(
            LivingEntity victim,
            DamageSource source,
            long gameTime) {
        if (victim == null || source == null || gameTime < 0L
                || !(victim.level() instanceof ServerLevel level)
                || !RECENT_DEATHS.accept(victim.getUUID(), gameTime)) {
            return Optional.empty();
        }

        CreditedPlayer credit = creditedPlayer(victim, source).orElse(null);
        ResourceLocation targetId = ForgeRegistries.ENTITY_TYPES.getKey(victim.getType());
        if (credit == null || credit.player() == victim || targetId == null) {
            return Optional.empty();
        }

        Set<ResourceLocation> tags = new LinkedHashSet<>();
        victim.getType().builtInRegistryHolder().tags()
                .map(TagKey::location)
                .sorted(java.util.Comparator.comparing(ResourceLocation::toString))
                .forEach(tags::add);

        CompoundTag persistentData = victim.getPersistentData();
        long bornAt = persistentData.contains(BORN_AT_KEY, Tag.TAG_LONG)
                ? persistentData.getLong(BORN_AT_KEY)
                : gameTime;
        long lifetime = gameTime >= bornAt ? gameTime - bornAt : 0L;
        Optional<SpawnProvenance> provenance = readProvenance(persistentData);
        boolean tamed = victim instanceof OwnableEntity ownable
                && ownable.getOwnerUUID() != null;

        CombatFacts facts = new CombatFacts(
                credit.type(),
                credit.player() instanceof FakePlayer,
                credit.type() == CreditType.PET,
                victim instanceof Player,
                victim.isBaby(),
                victim.hasCustomName(),
                tamed,
                provenance,
                lifetime,
                level.dimension().location(),
                difficulty(level.getDifficulty()),
                isBoss(victim, tags));
        return Optional.of(new CombatEvent(
                credit.player(), targetId, Set.copyOf(tags), facts));
    }

    public static void clear() {
        RECENT_DEATHS.clear();
    }

    static SpawnProvenance classifySpawnType(MobSpawnType spawnType) {
        return switch (spawnType) {
            case NATURAL -> SpawnProvenance.NATURAL;
            case CHUNK_GENERATION, STRUCTURE -> SpawnProvenance.STRUCTURE;
            case SPAWNER -> SpawnProvenance.SPAWNER;
            case BREEDING -> SpawnProvenance.BRED;
            case MOB_SUMMONED, TRIGGERED, SPAWN_EGG, COMMAND, DISPENSER ->
                    SpawnProvenance.SUMMONED;
            default -> SpawnProvenance.OTHER;
        };
    }

    private static Optional<CreditedPlayer> creditedPlayer(
            LivingEntity victim,
            DamageSource source) {
        Entity causing = source.getEntity();
        Entity direct = source.getDirectEntity();

        if (causing instanceof ServerPlayer player) {
            CreditType type = direct == null || direct == player
                    ? CreditType.DIRECT
                    : direct instanceof Projectile
                            || source.is(DamageTypeTags.IS_PROJECTILE)
                            || source.is(TACZ_BULLETS)
                            ? CreditType.OWNED_PROJECTILE
                            : CreditType.INDIRECT;
            return Optional.of(new CreditedPlayer(player, type));
        }
        Optional<ServerPlayer> projectileOwner = ownerPlayer(direct instanceof Projectile projectile
                ? projectile.getOwner()
                : null);
        if (projectileOwner.isPresent()) {
            return Optional.of(new CreditedPlayer(
                    projectileOwner.get(), CreditType.OWNED_PROJECTILE));
        }
        Optional<ServerPlayer> owner = ownerPlayer(causing);
        if (owner.isPresent()) {
            return Optional.of(new CreditedPlayer(owner.get(), CreditType.PET));
        }

        LivingEntity killCredit = victim.getKillCredit();
        if (killCredit instanceof ServerPlayer player) {
            return Optional.of(new CreditedPlayer(player, CreditType.INDIRECT));
        }
        Optional<ServerPlayer> killCreditOwner = ownerPlayer(killCredit);
        return killCreditOwner.map(player -> new CreditedPlayer(player, CreditType.PET));
    }

    private static Optional<ServerPlayer> ownerPlayer(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            return Optional.of(player);
        }
        if (entity instanceof OwnableEntity ownable && ownable.getOwner() instanceof ServerPlayer player) {
            return Optional.of(player);
        }
        return Optional.empty();
    }

    private static Optional<SpawnProvenance> readProvenance(CompoundTag data) {
        if (!data.contains(PROVENANCE_KEY, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        try {
            return Optional.of(SpawnProvenance.valueOf(
                    data.getString(PROVENANCE_KEY).toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static Difficulty difficulty(net.minecraft.world.Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> Difficulty.PEACEFUL;
            case EASY -> Difficulty.EASY;
            case NORMAL -> Difficulty.NORMAL;
            case HARD -> Difficulty.HARD;
        };
    }

    private static boolean isBoss(LivingEntity entity, Set<ResourceLocation> tags) {
        return entity instanceof EnderDragon
                || entity instanceof WitherBoss
                || tags.stream().anyMatch(COMMON_BOSS_TAGS::contains);
    }

    private record CreditedPlayer(ServerPlayer player, CreditType type) {
    }

    public record CombatEvent(
            ServerPlayer player,
            ResourceLocation targetId,
            Set<ResourceLocation> targetTags,
            CombatFacts facts) {
        public CombatEvent {
            if (player == null || targetId == null || targetTags == null || facts == null) {
                throw new IllegalArgumentException("invalid Research Point combat event");
            }
            targetTags = Set.copyOf(targetTags);
        }
    }
}

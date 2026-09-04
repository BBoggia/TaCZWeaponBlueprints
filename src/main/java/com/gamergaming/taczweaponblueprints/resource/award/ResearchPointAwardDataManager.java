package com.gamergaming.taczweaponblueprints.resource.award;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.resource.PublicationRevision;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Independent last-known-good datapack publication for configurable RP awards. */
public final class ResearchPointAwardDataManager
        extends SimplePreparableReloadListener<ResearchPointAwardDataManager.Prepared> {
    public static final ResearchPointAwardDataManager INSTANCE = new ResearchPointAwardDataManager();
    static final String DIRECTORY = "taczweaponblueprints/research_point_awards";

    private volatile Publication publication = new Publication(ResearchPointAwardSnapshot.EMPTY, 0L);
    private volatile Optional<Failure> lastFailure = Optional.empty();

    ResearchPointAwardDataManager() {
    }

    @Override
    protected Prepared prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        try {
            Map<ResourceLocation, ResearchPointAwardDefinition> definitions =
                    loadDefinitions(resourceManager);
            try {
                return Prepared.success(ResearchPointAwardSnapshot.create(definitions));
            } catch (IllegalArgumentException exception) {
                throw new AwardDataException(
                        "Invalid Research Point award snapshot: " + exception.getMessage(), exception);
            }
        } catch (AwardDataException exception) {
            return Prepared.failure(exception.getMessage());
        }
    }

    @Override
    protected void apply(Prepared prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        if (prepared == null) {
            throw new IllegalArgumentException("prepared Research Point award data cannot be null");
        }
        if (!prepared.successful()) {
            Failure failure = prepared.failure().orElseThrow();
            lastFailure = Optional.of(failure);
            TaCZWeaponBlueprints.LOGGER.error(
                    "Rejected Research Point award reload; retaining revision {}: {}",
                    publication.revision(),
                    failure.message());
            return;
        }

        Publication previous = publication;
        ResearchPointAwardSnapshot snapshot = prepared.snapshot().orElseThrow();
        publication = new Publication(
                snapshot, PublicationRevision.next(previous.revision()));
        lastFailure = Optional.empty();
        ResearchPointAwardDiagnostics.Summary summary =
                ResearchPointAwardDiagnostics.summarize(snapshot);
        TaCZWeaponBlueprints.LOGGER.info(
                "Applied Research Point award snapshot revision {}: {} definitions, {} enabled, "
                        + "{} groups, {} shared budgets, and {} indexed target bindings",
                publication.revision(),
                summary.definitionCount(),
                summary.enabledDefinitionCount(),
                summary.awardGroupCount(),
                summary.budgetCount(),
                summary.targetBindingCount());
    }

    public ResearchPointAwardSnapshot snapshot() {
        return publication.snapshot();
    }

    public long revision() {
        return publication.revision();
    }

    public Publication publication() {
        return publication;
    }

    public Optional<Failure> lastFailure() {
        return lastFailure;
    }

    /** Clears server-scoped datapack state after the server has fully stopped. */
    public void clear() {
        publication = new Publication(ResearchPointAwardSnapshot.EMPTY, 0L);
        lastFailure = Optional.empty();
    }

    public ResearchPointAwardResolver.Resolution resolve(ResearchPointAwardContext context) {
        Publication stable = publication;
        return ResearchPointAwardResolver.resolve(stable.snapshot(), context);
    }

    static ResourceLocation definitionId(ResourceLocation resourceId) {
        if (resourceId == null) {
            throw new AwardDataException("Research Point award resource ID cannot be null");
        }
        String prefix = DIRECTORY + "/";
        String path = resourceId.getPath();
        if (!path.startsWith(prefix) || !path.endsWith(".json")) {
            throw new AwardDataException("Resource is outside " + DIRECTORY + ": " + resourceId);
        }
        String definitionPath = path.substring(prefix.length(), path.length() - ".json".length());
        ResourceLocation definitionId = ResourceLocation.tryBuild(resourceId.getNamespace(), definitionPath);
        if (definitionId == null
                || definitionId.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            throw new AwardDataException(
                    "Invalid or oversized Research Point award ID derived from " + resourceId);
        }
        return definitionId;
    }

    private static Map<ResourceLocation, ResearchPointAwardDefinition> loadDefinitions(
            ResourceManager resourceManager) {
        if (resourceManager == null) {
            throw new AwardDataException("Research Point award resource manager cannot be null");
        }
        Map<ResourceLocation, ResearchPointAwardDefinition> definitions = new LinkedHashMap<>();
        resourceManager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json"))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    ResourceLocation definitionId = definitionId(entry.getKey());
                    ResearchPointAwardDefinition definition = readDefinition(
                            entry.getKey(), entry.getValue());
                    if (definitions.put(definitionId, definition) != null) {
                        throw new AwardDataException(
                                "Duplicate Research Point award ID " + definitionId);
                    }
                    if (definitions.size()
                            > PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_DEFINITIONS) {
                        throw new AwardDataException(
                                "Too many Research Point award definitions; maximum is "
                                        + PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_DEFINITIONS);
                    }
                });
        return definitions;
    }

    private static ResearchPointAwardDefinition readDefinition(
            ResourceLocation resourceId,
            Resource resource) {
        try (Reader reader = resource.openAsReader()) {
            JsonElement json = parseBoundedJson(reader);
            DataResult<ResearchPointAwardDefinition> result =
                    ResearchPointAwardDefinition.CODEC.parse(JsonOps.INSTANCE, json);
            return result.result().orElseThrow(() -> new AwardDataException(
                    "Invalid Research Point award " + resourceId + " from pack "
                            + resource.sourcePackId() + ": "
                            + result.error().map(DataResult.PartialResult::message)
                                    .orElse("unknown codec error")));
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof AwardDataException dataException) {
                throw dataException;
            }
            throw new AwardDataException(
                    "Failed to read Research Point award " + resourceId
                            + " from pack " + resource.sourcePackId(),
                    exception);
        }
    }

    static JsonElement parseBoundedJson(Reader reader) throws IOException {
        if (reader == null) {
            throw new AwardDataException("Research Point award reader cannot be null");
        }
        StringBuilder json = new StringBuilder(8_192);
        char[] buffer = new char[8_192];
        while (true) {
            int remaining = PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_JSON_CHARACTERS
                    - json.length();
            int read = reader.read(buffer, 0, Math.min(buffer.length, remaining + 1));
            if (read < 0) {
                return JsonParser.parseString(json.toString());
            }
            if (read > remaining) {
                throw new AwardDataException("Research Point award definition exceeds the "
                        + PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_JSON_CHARACTERS
                        + " character limit");
            }
            json.append(buffer, 0, read);
        }
    }

    public record Publication(ResearchPointAwardSnapshot snapshot, long revision) {
        public Publication {
            if (snapshot == null || revision < 0L) {
                throw new IllegalArgumentException("invalid Research Point award publication");
            }
        }
    }

    public record Failure(String message) {
        public Failure {
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("award reload failure message cannot be blank");
            }
        }
    }

    public record Prepared(
            Optional<ResearchPointAwardSnapshot> snapshot,
            Optional<Failure> failure) {
        public Prepared {
            snapshot = snapshot == null ? Optional.empty() : snapshot;
            failure = failure == null ? Optional.empty() : failure;
            if (snapshot.isPresent() == failure.isPresent()) {
                throw new IllegalArgumentException(
                        "prepared award data must contain exactly one snapshot or failure");
            }
        }

        public boolean successful() {
            return snapshot.isPresent();
        }

        private static Prepared success(ResearchPointAwardSnapshot snapshot) {
            return new Prepared(Optional.of(snapshot), Optional.empty());
        }

        private static Prepared failure(String message) {
            return new Prepared(Optional.empty(), Optional.of(new Failure(message)));
        }
    }

    private static final class AwardDataException extends RuntimeException {
        private AwardDataException(String message) {
            super(message);
        }

        private AwardDataException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

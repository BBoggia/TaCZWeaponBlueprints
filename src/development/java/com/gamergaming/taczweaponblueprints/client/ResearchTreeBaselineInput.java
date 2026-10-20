package com.gamergaming.taczweaponblueprints.client;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import net.minecraft.resources.ResourceLocation;

/** Development-only replay format. Contains typed public projections, not player/world data. */
public record ResearchTreeBaselineInput(
        int format,
        String name,
        int viewportWidth,
        ResearchTechTreeLayoutPolicy policy,
        List<ProjectionData> projections) {
    public static final int FORMAT = 1;
    private static final long MAX_BYTES = 16 * 1024 * 1024;
    static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
            .registerTypeAdapter(ResourceLocation.class, new IdentifierAdapter())
            .registerTypeAdapter(Optional.class, new OptionalAdapter()).create();

    public ResearchTreeBaselineInput {
        if (format != FORMAT || name == null || name.isBlank() || name.length() > 160
                || viewportWidth < 1 || policy == null || projections == null
                || projections.size() > 3) {
            throw new IllegalArgumentException("invalid research baseline input");
        }
        projections = List.copyOf(projections);
        if (projections.stream().map(p -> p.presentation().domain()).distinct().count()
                != projections.size()) {
            throw new IllegalArgumentException("duplicate baseline domain");
        }
        projections.forEach(ProjectionData::replay);
    }

    public static ResearchTreeBaselineInput capture(String name, int viewportWidth,
            ResearchTechTreeLayoutPolicy policy, ResearchTechTreeProjectionCatalog catalog) {
        return new ResearchTreeBaselineInput(FORMAT, name, viewportWidth, policy,
                catalog.projections().stream().map(ProjectionData::capture).toList());
    }

    public String toJson() {
        var json = JSON.toJsonTree(this).getAsJsonObject();
        // Optional additions must preserve the identity of pre-branch replay inputs.
        for (var projection : json.getAsJsonArray("projections")) {
            var presentation = projection.getAsJsonObject().getAsJsonObject("presentation");
            if (presentation.getAsJsonArray("branches").isEmpty()) {
                presentation.remove("branches");
            }
        }
        return JSON.toJson(json) + "\n";
    }

    public String sha256() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(toJson().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public void write(Path path) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, toJson(), StandardCharsets.UTF_8);
    }

    public static ResearchTreeBaselineInput read(Path path) throws IOException {
        if (Files.size(path) > MAX_BYTES) {
            throw new IOException("baseline input exceeds development replay byte limit");
        }
        return fromJson(Files.readString(path, StandardCharsets.UTF_8));
    }

    public static ResearchTreeBaselineInput fromJson(String json) {
        ResearchTreeBaselineInput input = JSON.fromJson(json, ResearchTreeBaselineInput.class);
        if (input == null) {
            throw new IllegalArgumentException("missing baseline input");
        }
        return input;
    }

    public record ProjectionData(
            ResearchTechTreePresentation.DomainView presentation,
            List<ResearchTreeGraph.Node> nodes,
            List<ResearchTreeGraph.Edge> edges,
            List<ResearchTreeGraph.RequirementGroup> groups,
            List<ResearchTechTreeProjection.BoundaryLink> boundaryLinks,
            List<ResearchTechTreePresentation.BandLabel> bands,
            int maxNodesPerLayer) {
        public ProjectionData {
            if (presentation == null) {
                throw new IllegalArgumentException("missing baseline domain presentation");
            }
            nodes = List.copyOf(nodes);
            edges = List.copyOf(edges);
            groups = List.copyOf(groups);
            boundaryLinks = List.copyOf(boundaryLinks);
            bands = List.copyOf(bands);
        }

        public static ProjectionData capture(ResearchTechTreeProjection projection) {
            return new ProjectionData(projection.presentation(), projection.graph().nodes(),
                    projection.graph().edges(), projection.graph().requirementGroups(),
                    projection.boundaryLinks(), projection.bands(), projection.maxNodesPerLayer());
        }

        public ResearchTechTreeProjection replay() {
            // Reconstruct through the production constructors, including graph validation.
            var graph = new ResearchTreeGraph(nodes, edges, groups);
            var byId = new LinkedHashMap<ResourceLocation, ResearchTechTreeProjection.Placement>();
            for (var lane : presentation.lanes()) {
                for (var member : lane.members()) {
                    var previous = byId.put(member.nodeId(), new ResearchTechTreeProjection.Placement(
                            member.nodeId(), lane.id(), member.rank(), lane.order(),
                            member.siblingOrder(), member.bandId(), member.origin(),
                            member.rating(), member.automaticBranch()));
                    if (previous != null) {
                        throw new IllegalArgumentException("duplicate baseline member");
                    }
                }
            }
            var ordered = new LinkedHashMap<ResourceLocation, ResearchTechTreeProjection.Placement>();
            graph.nodes().forEach(node -> ordered.put(node.blueprintId(), byId.get(node.blueprintId())));
            return new ResearchTechTreeProjection(presentation.domain(), presentation, graph,
                    ordered, boundaryLinks, bands, maxNodesPerLayer);
        }
    }

    private static final class IdentifierAdapter
            implements JsonSerializer<ResourceLocation>, JsonDeserializer<ResourceLocation> {
        public JsonElement serialize(ResourceLocation value, Type type, JsonSerializationContext context) {
            return new JsonPrimitive(value.toString());
        }

        public ResourceLocation deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
            return ResourceLocation.parse(json.getAsString());
        }
    }

    private static final class OptionalAdapter
            implements JsonSerializer<Optional<?>>, JsonDeserializer<Optional<?>> {
        public JsonElement serialize(Optional<?> value, Type type, JsonSerializationContext context) {
            return value.map(context::serialize).orElse(JsonNull.INSTANCE);
        }

        public Optional<?> deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
            return json.isJsonNull() ? Optional.empty() : Optional.ofNullable(context.deserialize(
                    json, ((ParameterizedType) type).getActualTypeArguments()[0]));
        }
    }
}

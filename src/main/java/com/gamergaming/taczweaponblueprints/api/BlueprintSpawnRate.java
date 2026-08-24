package com.gamergaming.taczweaponblueprints.api;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

public record BlueprintSpawnRate(String name, float score, String id) {
    public BlueprintSpawnRate {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Blueprint spawn-rate name cannot be blank");
        }
        if (!Float.isFinite(score) || score <= 0.0f) {
            throw new IllegalArgumentException("Blueprint spawn-rate score must be finite and greater than zero");
        }
        ResourceLocation parsedId = id == null ? null : ResourceLocation.tryParse(id);
        if (parsedId == null) {
            throw new IllegalArgumentException("Blueprint spawn-rate ID must be a valid resource location: " + id);
        }
        id = parsedId.toString();
    }

    public static BlueprintSpawnRate fromJson(JsonObject obj) {
        if (obj == null) {
            throw new IllegalArgumentException("Blueprint spawn-rate JSON cannot be null");
        }
        try {
            String name = obj.get("name").getAsString();
            float score = obj.get("score").getAsFloat();
            String id = obj.get("id").getAsString();
            return new BlueprintSpawnRate(name, score, id);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Malformed blueprint spawn-rate entry: " + obj, exception);
        }
    }
}

package com.gamergaming.taczweaponblueprints.api;

import com.google.gson.JsonObject;

public record BlueprintSpawnRate(String name, float score, String id) {
    public BlueprintSpawnRate {
        if (name == null) {
            throw new IllegalArgumentException("Name cannot be null");
        }
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
    }

    public static BlueprintSpawnRate fromJson(JsonObject obj) {
        String name = obj.get("name").getAsString();
        float score = obj.get("score").getAsFloat();
        String id = obj.get("id").getAsString();
        return new BlueprintSpawnRate(name, score, id);
    }
}
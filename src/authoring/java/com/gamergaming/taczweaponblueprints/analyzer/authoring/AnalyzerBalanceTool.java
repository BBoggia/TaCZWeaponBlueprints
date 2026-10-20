package com.gamergaming.taczweaponblueprints.analyzer.authoring;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;

/** Offline authoring CLI. Never mutates a catalog, datapack, configuration, or player save. */
public final class AnalyzerBalanceTool {
    private AnalyzerBalanceTool() { }
    public static void main(String[] args) throws Exception {
        var options = new HashMap<String, String>();
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 == args.length || !Set.of("--catalog", "--output", "--target", "--supply", "--supply-budget").contains(args[i])
                    || options.put(args[i], args[i + 1]) != null)
                throw new IllegalArgumentException("Usage: --catalog export.json --output report.json [--target namespace:weapon]");
        }
        if (!options.containsKey("--catalog") || !options.containsKey("--output"))
            throw new IllegalArgumentException("--catalog and --output are required");
        Path input = Path.of(options.get("--catalog")).toRealPath();
        Path output = Path.of(options.get("--output")).toAbsolutePath().normalize();
        if (input.equals(output) || Files.exists(output) && Files.isSameFile(input, output))
            throw new IllegalArgumentException("report must not overwrite its input catalog");
        if (Files.size(input) > 256L * 1024 * 1024) throw new IllegalArgumentException("catalog exceeds 256 MiB input limit");
        JsonObject report;
        try (var reader = Files.newBufferedReader(input)) {
            var catalog = JsonParser.parseReader(reader).getAsJsonObject();
            report = AnalyzerBalanceReport.create(catalog, Optional.ofNullable(options.get("--target")));
            if (options.containsKey("--supply") || options.containsKey("--supply-budget")) {
                var tables = new TreeMap<net.minecraft.resources.ResourceLocation,com.gamergaming.taczweaponblueprints.loot.ScrapSupplyLootModifier.Row>(com.gamergaming.taczweaponblueprints.loot.ScrapSupplyLootModifier.defaults());
                if (options.containsKey("--supply")) {
                    var path = Path.of(options.get("--supply")).toRealPath();
                    if(path.equals(output) || Files.exists(output) && Files.isSameFile(path,output))throw new IllegalArgumentException("report must not overwrite supply input");
                    if(Files.size(path)>1024*1024)throw new IllegalArgumentException("supply exceeds 1 MiB");
                    var rows=JsonParser.parseString(Files.readString(path)).getAsJsonObject().getAsJsonObject("tables");
                    if(rows.size()>1024)throw new IllegalArgumentException("too many supply tables");tables.clear();
                    for(var entry:rows.entrySet())tables.put(net.minecraft.resources.ResourceLocation.parse(entry.getKey()),com.gamergaming.taczweaponblueprints.loot.ScrapSupplyLootModifier.Row.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,entry.getValue()).getOrThrow(IllegalArgumentException::new));
                }
                report.add("research_economy",ResearchEconomyReport.create(catalog,tables,Integer.parseInt(options.getOrDefault("--supply-budget","32"))));
            }
        }
        Files.createDirectories(output.getParent());
        var temporary = Files.createTempFile(output.getParent(), ".analyzer-report-", ".json");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
            try { Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
        System.out.println("Analyzer balance report: " + output);
    }
}

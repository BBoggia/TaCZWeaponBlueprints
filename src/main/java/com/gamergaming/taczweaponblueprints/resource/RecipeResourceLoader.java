package com.gamergaming.taczweaponblueprints.resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.ibm.icu.impl.Pair;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.resource.CommonGunPackLoader;
import com.tacz.guns.resource.VersionChecker;
import com.tacz.guns.resource.pojo.data.recipe.TableRecipe;
import com.tacz.guns.util.TacPathVisitor;

import net.minecraft.resources.ResourceLocation;

public class RecipeResourceLoader {

    private static final Marker MARKER = MarkerManager.getMarker("RecipeLoader");
    private static final Pattern RECIPES_PATTERN = Pattern.compile("^(\\w+)/recipes/([\\w/]+)\\.json$");
    private static final Pattern INDEX_PATTERN = Pattern.compile("^(\\w+)/guns/index/([\\w/]+)\\.json$");

    /**
     * Loads all recipes from the given folder.
     *
     * @return List of GunSmithTableRecipes
     */
    public static Map<ResourceLocation, Triple<GunSmithTableRecipe, String, Pair<String, String>>> loadRecipes() {
        Map<ResourceLocation, Triple<GunSmithTableRecipe, String, Pair<String, String>>> recipes = new HashMap<>();
        File[] files = CommonGunPackLoader.FOLDER.toFile().listFiles((dir, name) -> true);

        if (files != null) {
            recipes = readRecipes(files);
        } else {
            TaCZWeaponBlueprints.LOGGER4J.warn(MARKER, "No files found in the directory: {}", CommonGunPackLoader.FOLDER);
        }

        return recipes;
    }

    /**
     * Reads recipes from the given array of files, handling both zip files and directories.
     *
     * @param files Array of files to read from
     * @return List of GunSmithTableRecipes
     */
    private static Map<ResourceLocation, Triple<GunSmithTableRecipe, String, Pair<String, String>>> readRecipes(File[] files) {
        Map<ResourceLocation, Triple<GunSmithTableRecipe, String, Pair<String, String>>> recipes = new HashMap<>();

        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".zip")) {
                //recipes.addAll(readZipRecipes(file));
                recipes.putAll(readZipRecipes(file));
            } else if (file.isDirectory()) {
                recipes.putAll(readDirRecipes(file));
            } else {
                TaCZWeaponBlueprints.LOGGER4J.warn(MARKER, "Unsupported file type: {}", file.getAbsolutePath());
            }
        }

        return recipes;
    }

    /**
     * Reads recipes from a zip file.
     *
     * @param file Zip file to read from
     * @return Map of ResourceLocation to Pair of GunSmithTableRecipe and nameKey
     */
    private static Map<ResourceLocation, Triple<GunSmithTableRecipe, String, Pair<String, String>>> readZipRecipes(File file) {
        Map<ResourceLocation, Triple<GunSmithTableRecipe, String, Pair<String, String>>> recipes = new HashMap<>();

        try (ZipFile zipFile = new ZipFile(file)) {

            if (!VersionChecker.noneMatch(zipFile, file.toPath())) {
                TaCZWeaponBlueprints.LOGGER4J.warn(MARKER, "Version mismatch in zip file: {}", file.getAbsolutePath());
                return recipes;
            }

            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String path = entry.getName();

                Matcher matcher = RECIPES_PATTERN.matcher(path);
                if (matcher.find()) {
                    Triple<ResourceLocation, GunSmithTableRecipe, Triple<String, String, String>> recipeData = loadRecipeFromZipEntry(zipFile, entry, matcher);
                    if (recipeData != null) {
                        recipes.put(recipeData.getLeft(), Triple.of(recipeData.getMiddle(), recipeData.getRight().getLeft(), Pair.of(recipeData.getRight().getMiddle(), recipeData.getRight().getRight())));
                    }
                }
            }
        } catch (IOException ioException) {
            TaCZWeaponBlueprints.LOGGER4J.error(MARKER, "Error reading zip file: {}", file.getAbsolutePath(), ioException);
        }

        return recipes;
    }

    /**
     * Reads recipes from a directory.
     *
     * @param root Directory to read from
     * @return List of GunSmithTableRecipes
     */
    private static Map<ResourceLocation, Triple<GunSmithTableRecipe, String, Pair<String, String>>> readDirRecipes(File root) {
        Map<ResourceLocation, Triple<GunSmithTableRecipe, String, Pair<String, String>>> recipes = new HashMap<>();
        
    
        // if (!VersionChecker.match(root)) {
        //     TaCZWeaponBlueprints.LOGGER4J.warn(MARKER, "Version mismatch in directory: {}", root.getAbsolutePath());
        //     return recipes;
        // }

        // try {
        //     Files.walk(root.toPath())
        //         .filter(path -> Files.isDirectory(path) && (path.getFileName().toString().equals("guns") || path.getFileName().toString().equals("attachments") || path.getFileName().toString().equals("ammo")))
        //         .forEach(gunsPath -> {
        //             try {
        //                 // Use AttachmentIndexRetriever and TestRecipeLoader
        //                 TaCZWeaponBlueprints.LOGGER.info("Loading attachment index from: {}  -  {}", gunsPath.getParent().toString(), gunsPath.toFile().toString());
        //                 AttachmentIndexRetriever.loadAttachmentIndex(gunsPath.getParent().toFile());
        //                 TestRecipeLoader.load(gunsPath.getParent().toFile());
        //                 TaCZWeaponBlueprints.LOGGER.info("\n\n\n");
        //             } catch (IOException e) {
        //                 TaCZWeaponBlueprints.LOGGER4J.error(MARKER, "Failed to walk file tree: {}", gunsPath, e);
        //             }
        //         });
        // } catch (IOException e) {
        //     TaCZWeaponBlueprints.LOGGER4J.error(MARKER, "!!!!!ONE OF MY TESTS FAILED: {}", root.getAbsolutePath(), e);
        // }
        
        try {
            // Recursively walk through the directory tree starting from 'root'
            Files.walk(root.toPath())
                .filter(path -> Files.isDirectory(path) && path.getFileName().toString().equals("recipes"))
                .forEach(recipesPath -> {
    
                    // Determine the namespace based on the parent directory of 'recipes'
                    Path namespacePath = root.toPath().relativize(recipesPath.getParent());

                    String namespace;
                    if (namespacePath.getNameCount() > 0 && namespacePath.toString().strip().length() > 0) {
                        // Get the last folder name in the path as the namespace
                        namespace = namespacePath.getName(namespacePath.getNameCount() - 1).toString();
                    } else {
                        // If 'recipes' is directly under 'root', use the root directory name as the namespace
                        namespace = root.getName();
                    }
                    TaCZWeaponBlueprints.LOGGER4J.info(MARKER, "Namespace determined as: {}", namespace);
    
                    try {
                        // Process JSON files within the 'recipes' directory
                        Files.walkFileTree(recipesPath, new TacPathVisitor(recipesPath.toFile(), namespace, ".json", (id, file) -> {
                            
                            Triple<ResourceLocation, GunSmithTableRecipe, Triple<String, String, String>> recipe = loadRecipeFromFile(id, file);
                            if (recipe != null) {
                                recipes.put(recipe.getLeft(), Triple.of(recipe.getMiddle(), recipe.getRight().getLeft(), Pair.of(recipe.getRight().getMiddle(), recipe.getRight().getRight())));
                            }
                        }));
                    } catch (IOException e) {
                        TaCZWeaponBlueprints.LOGGER4J.error(MARKER, "Failed to walk file tree: {}", recipesPath, e);
                    }
                });
        } catch (IOException e) {
            TaCZWeaponBlueprints.LOGGER4J.error(MARKER, "Failed to walk directory: {}", root.getAbsolutePath(), e);
        }
    
        return recipes;
    }

    /**
     * Loads a single recipe from a zip entry and retrieves the name key from the corresponding index entry.
     *
     * @param zipFile Zip file containing the entry
     * @param entry   ZipEntry to read from
     * @param matcher Matcher containing the namespace and path
     * @return Triple containing ResourceLocation, GunSmithTableRecipe, and nameKey or null if an error occurs
     */
    private static Triple<ResourceLocation, GunSmithTableRecipe, Triple<String, String, String>> loadRecipeFromZipEntry(ZipFile zipFile, ZipEntry entry, Matcher matcher) {
        String namespace = matcher.group(1);
        String path = matcher.group(2); // This is the path without the .json extension
        ResourceLocation registryName = new ResourceLocation(namespace, path);
        
        try (InputStream stream = zipFile.getInputStream(entry)) {
            String json = IOUtils.toString(stream, StandardCharsets.UTF_8);
            TableRecipe tableRecipe = CommonGunPackLoader.GSON.fromJson(json, TableRecipe.class);

            // Now attempt to load the corresponding index entry
            String indexEntryName = namespace + "/guns/index/" + path + ".json";
            ZipEntry indexEntry = zipFile.getEntry(indexEntryName);

            Triple<String, String, String> indexData = null;
            if (indexEntry != null) {
                indexData = loadIndexFromZipEntry(zipFile, indexEntry);
            } else {
                TaCZWeaponBlueprints.LOGGER4J.warn(MARKER, "Index file not found for recipe: {}", registryName);
                return null;
            }

            String displayEntryName = namespace + "/guns/display/" + indexData.getRight() + ".json";

            ZipEntry displayEntry = zipFile.getEntry(displayEntryName);

            String displayData = null;
            if (displayEntry != null) {
                displayData = loadDisplayFromFile(Path.of(displayEntry.getName()));
            } else {
                TaCZWeaponBlueprints.LOGGER4J.warn(MARKER, "Display file not found for recipe: {}", registryName);
                return null;
            }

            Triple<String, String, String> groupedGunData = Triple.of(indexData.getLeft(), indexData.getMiddle(), displayData);

            return Triple.of(registryName, new GunSmithTableRecipe(registryName, tableRecipe), groupedGunData);
        } catch (IOException | JsonSyntaxException | JsonIOException exception) {
            TaCZWeaponBlueprints.LOGGER4J.error(MARKER, "Failed to read recipe from zip entry: {}", entry.getName(), exception);
        }

        return null;
    }

    /**
     * Loads a single recipe from a file.
     *
     * @param id   ResourceLocation representing the recipe ID
     * @param file Path to the JSON file
     * @return GunSmithTableRecipe or null if an error occurs
     */
    private static Triple<ResourceLocation, GunSmithTableRecipe, Triple<String, String, String>> loadRecipeFromFile(ResourceLocation id, Path file) {
        try (InputStream stream = Files.newInputStream(file)) {
            
            String json = IOUtils.toString(stream, StandardCharsets.UTF_8);
            TableRecipe tableRecipe = CommonGunPackLoader.GSON.fromJson(json, TableRecipe.class);
            // Replace recipes/gun/ with guns/index/ to get the index file
            Path parentDirPath = file.getParent().getParent().getParent();

            JsonObject resultJsonObj = JsonParser.parseString(json).getAsJsonObject();
            String fileName = null;

            if (resultJsonObj.has("result")) {
                if (resultJsonObj.get("result").isJsonObject() && resultJsonObj.get("result").getAsJsonObject().has("id")) {
                    String fullResource = resultJsonObj.get("result").getAsJsonObject().get("id").getAsString();
                    String[] splitParentDir = parentDirPath.toString().split("/");
                    if (fullResource.split(":")[0].equals("tacz") && !splitParentDir[splitParentDir.length - 1].equals("tacz")) {
                        TaCZWeaponBlueprints.LOGGER4J.info(MARKER, "Skipping tacz resource: {}", fullResource);
                        return null;
                    }

                    fileName = fullResource.split(":")[1] + ".json";
                }
            }

            Path indexPath;
            Path displayPath;

            Path fileNamePath = file.getFileName();

            if (fileName != null) {
                // replace the file name in fileNamePath with the fileName while keeping the file extension
                fileNamePath = Path.of(fileNamePath.toString().replace(fileNamePath.getFileName().toString(), fileName));
            }

            if (file.getParent().getFileName().toString().contains("gun")) {
                indexPath = parentDirPath.resolve("guns").resolve("index").resolve(fileNamePath);
            } else {
                indexPath = parentDirPath.resolve(file.getParent().getFileName().toString()).resolve("index").resolve(fileNamePath);
            }

            if (indexPath.toString().contains("/attachment/")) {
                indexPath = Path.of(indexPath.toString().replace("/attachment/", "/attachments/"));
            }

            Triple<String, String, String> indexData = null;

            try {
                indexData = loadIndexFromFile(indexPath);
            } catch (Exception e) {
                if (file.getParent().getFileName().toString().contains("gun")) {
                    indexPath = parentDirPath.resolve("guns").resolve("index").resolve(file.getFileName());
                } else {
                    indexPath = parentDirPath.resolve(file.getParent().getFileName().toString()).resolve("index").resolve(file.getFileName());
                }

                if (indexPath.toString().contains("/attachment/")) {
                    indexPath = Path.of(indexPath.toString().replace("/attachment/", "/attachments/"));
                }

                try {
                    indexData = loadIndexFromFile(indexPath);
                } catch (Exception e2) {
                    TaCZWeaponBlueprints.LOGGER.error("Failed to get name key from index file: {}", indexPath);
                    return null;
                }
            }

            if (indexData == null) {
                TaCZWeaponBlueprints.LOGGER.error("Failed to get name key from index file: {}", indexPath);
                return null;
            }

            // TaCZWeaponBlueprints.LOGGER4J.info(MARKER, "Display file name: {}", indexData.getRight());
            if (file.getParent().getFileName().toString().contains("gun")) {
                displayPath = parentDirPath.resolve("guns").resolve("display").resolve(Path.of(indexData.getRight() + ".json"));
            } else {
                displayPath = parentDirPath.resolve(file.getParent().getFileName().toString()).resolve("display").resolve(Path.of(indexData.getRight() + ".json"));
            }

            if (displayPath.toString().contains("/attachment/")) {
                displayPath = Path.of(displayPath.toString().replace("/attachment/", "/attachments/"));
            }

            String displayData = loadDisplayFromFile(displayPath);

            if (displayData == null) {
                TaCZWeaponBlueprints.LOGGER.error("Failed to get display name from display file: {}", displayPath);
                return null;
            }

            Triple<String, String, String> groupedGunData = Triple.of(indexData.getLeft(), indexData.getMiddle(), displayData);
            

            return Triple.of(id, new GunSmithTableRecipe(id, tableRecipe), groupedGunData);
        } catch (IOException | JsonSyntaxException | JsonIOException exception) {
            TaCZWeaponBlueprints.LOGGER4J.error(MARKER, "Failed to read recipe file: {}", file, exception);
        }

        return null;
    }

    private static Triple<String, String, String> loadIndexFromFile(Path file) {
        // Get a list of all files in the same directory as the file
        // List<Path> files = new ArrayList<>();
        // if (Files.notExists(file)) {
        //     TaCZWeaponBlueprints.LOGGER.error("FIXING!!!Index file does not exist: {}", file);
        //     try {
        //         files = Files.list(file.getParent()).toList();
        //         // Check if any files name in the list contains the parameter files name
        //         for (Path path : files) {
        //             if (path.getFileName().toString().contains(file.getFileName().toString())) {
        //                 file = path;
        //                 break;
        //             }
        //         }
        //     } catch (IOException e) {
        //         TaCZWeaponBlueprints.LOGGER.error("Failed to list files in directory: {}", file.getParent());
        //     }
        // }

        try (InputStream stream = Files.newInputStream(file)) {
            String json = IOUtils.toString(stream, StandardCharsets.UTF_8);
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            // safely try to get the type key
            String type;
            if (jsonObject.has("type")) {
                type = jsonObject.get("type").getAsString();
            } else {
                type = "ammo";
            }

            String name = jsonObject.get("name").getAsString();
            String display = jsonObject.get("display").getAsString();

            if (display != null) {
                display = display.split(":")[1];
            }

            return Triple.of(name, type, display);
        } catch (IOException | JsonSyntaxException exception) {
            // TaCZWeaponBlueprints.LOGGER.error("Failed to read index file: {}. It may be missing or corrupted.", file);
            throw new RuntimeException("Failed to read index file: " + file, exception);
        }
    }

    /**
     * Reads the name key from an index entry in the zip file.
     *
     * @param zipFile   Zip file containing the index entry
     * @param indexEntry ZipEntry representing the index file
     * @return The name key as a String or null if an error occurs
     */
    private static Triple<String, String, String> loadIndexFromZipEntry(ZipFile zipFile, ZipEntry indexEntry) {
        try (InputStream stream = zipFile.getInputStream(indexEntry)) {
            String json = IOUtils.toString(stream, StandardCharsets.UTF_8);
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            String type;
            if (jsonObject.has("type")) {
                type = jsonObject.get("type").getAsString();
            } else {
                type = "ammo";
            }

            String name = jsonObject.get("name").getAsString();
            String display = jsonObject.get("display").getAsString();

            if (display != null) {
                display = display.split(":")[1];
            }

            return Triple.of(name, type, display);
        } catch (IOException | JsonSyntaxException exception) {
            TaCZWeaponBlueprints.LOGGER4J.error(MARKER, "Failed to read index from zip entry: {}", indexEntry.getName(), exception);
            return null;
        }
    }

    /**
     * Reads the display name from a display file in the zip file.
     *
     * @param file File to read from
     * @return The display name as a String or null if an error occurs
     */
    private static String loadDisplayFromFile(Path file) {
        try (InputStream stream = Files.newInputStream(file)) {
            String json = IOUtils.toString(stream, StandardCharsets.UTF_8);
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            return jsonObject.get("slot").getAsString();
        } catch (IOException | JsonSyntaxException exception) {
            TaCZWeaponBlueprints.LOGGER.error("Failed to read display file: {}. It may be missing or corrupted.", file);
            return null;
        }
    }
}
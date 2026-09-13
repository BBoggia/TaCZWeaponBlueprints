package com.gamergaming.taczweaponblueprints.compat.magazines;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.tacz.guns.crafting.GunSmithTableRecipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

/** Reads the optional add-on's published data; never generates recipes or grants permission. */
public final class TaCZMagazinesAdapter {
    public static final String MOD_ID = "taczmagazines";
    public static final String SUPPORTED_VERSION = "0.2.0-Beta";
    public static final ResourceLocation MAGAZINE_TAB = new ResourceLocation(MOD_ID, "magazines");
    static final int MAX_FAMILIES = 8192;
    static final int MAX_LINKS = 65536;
    private final Source source;
    private final Status status;

    private TaCZMagazinesAdapter(Source source, Status status) {
        this.source = source;
        this.status = status;
    }

    static TaCZMagazinesAdapter create(String installedVersion, Supplier<Source> loader) {
        if (installedVersion == null) return new TaCZMagazinesAdapter(null, Status.ABSENT);
        if (!SUPPORTED_VERSION.equals(installedVersion)) {
            return new TaCZMagazinesAdapter(null, Status.UNSUPPORTED_VERSION);
        }
        try {
            Source source = loader.get();
            return new TaCZMagazinesAdapter(source, source == null ? Status.UNAVAILABLE : Status.READY);
        } catch (RuntimeException | LinkageError failure) {
            return new TaCZMagazinesAdapter(null, Status.UNAVAILABLE);
        }
    }

    public Status status() {
        return status;
    }

    public Capture capture(Collection<? extends Recipe<?>> recipes) {
        if (status != Status.READY) return Capture.empty(status);
        try {
            return captureReady(recipes);
        } catch (RuntimeException | LinkageError failure) {
            // An incompatible API or failed discovery invalidates the whole publication.
            return new Capture(Status.UNAVAILABLE, Map.of(), Map.of(),
                    Map.of("integration", failure.getClass().getSimpleName()));
        }
    }

    private Capture captureReady(Collection<? extends Recipe<?>> recipes) {
        Set<String> families = Set.copyOf(source.families());
        if (families.size() > MAX_FAMILIES) throw new IllegalArgumentException("too many magazine families");
        var candidates = new ArrayList<GunSmithTableRecipe>();
        for (Recipe<?> recipe : recipes) {
            if (recipe instanceof GunSmithTableRecipe table && isGeneratedRecipe(table.getId())) {
                candidates.add(table);
                if (candidates.size() > MAX_FAMILIES) throw new IllegalArgumentException("too many magazine recipes");
            }
        }
        candidates.sort(Comparator.comparing(recipe -> recipe.getId().toString()));
        Map<ResourceLocation, MagazineDescriptor> descriptors = new LinkedHashMap<>();
        Map<String, String> rejected = new LinkedHashMap<>();
        Set<ResourceLocation> ambiguous = new HashSet<>();
        int links = 0;
        for (GunSmithTableRecipe recipe : candidates) {
            try {
                ItemStack output = recipe.getOutput().copy();
                if (!MAGAZINE_TAB.equals(recipe.getTab()) || !source.isMagazine(output)) {
                    throw new IllegalArgumentException("unexpected magazine output or tab");
                }
                String family = source.family(output);
                ResourceLocation key = familyKey(family);
                if (!families.contains(family) || !source.recipeEnabled(family)) {
                    throw new IllegalArgumentException("unknown or disabled magazine family");
                }
                Family facts = source.describe(family);
                if (source.ammoCount(output) != 0 || source.capacity(output) != facts.capacity()) {
                    throw new IllegalArgumentException("magazine output is loaded or has a different capacity");
                }
                if (facts.compatibleGuns().size() > MAX_LINKS - links) {
                    throw new IllegalStateException("too many magazine compatibility links");
                }
                links += facts.compatibleGuns().size();
                var ingredients = recipe.getInputs().stream().map(input ->
                        new MagazineDescriptor.IngredientCost(
                                input.getIngredient().toJson().toString(), input.getCount())).toList();
                var descriptor = new MagazineDescriptor(key, family, facts.ammoId(), facts.capacity(),
                        facts.extensionLevel(), facts.compatibleGuns(), recipe.getId(), output, ingredients);
                MagazineDescriptor previous = descriptors.putIfAbsent(key, descriptor);
                if (previous != null || ambiguous.contains(key)) {
                    ambiguous.add(key);
                    descriptors.remove(key);
                    rejected.put(key.toString(), "multiple recipes for magazine family");
                }
            } catch (IllegalArgumentException | NullPointerException invalid) {
                rejected.put(recipe.getId().toString(), "invalid magazine recipe: " + invalid.getClass().getSimpleName());
            }
        }
        Map<ResourceLocation, ResourceLocation> recipeFamilies = new LinkedHashMap<>();
        descriptors.forEach((key, descriptor) -> recipeFamilies.put(descriptor.recipeId(), key));
        return new Capture(Status.READY, descriptors, recipeFamilies, rejected);
    }

    public static boolean isGeneratedRecipe(ResourceLocation id) {
        return id != null && MOD_ID.equals(id.getNamespace()) && id.getPath().startsWith("magazine/");
    }

    /** The ordinal in an upstream recipe ID never becomes part of saved family identity. */
    public static ResourceLocation familyKey(String family) {
        if (family == null || family.isBlank() || family.length() > 200
                || !family.matches("[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("invalid upstream magazine family ID");
        }
        return new ResourceLocation(MOD_ID, "family/" + family);
    }

    public enum Status { ABSENT, WAITING_FOR_RECIPES, UNSUPPORTED_VERSION, UNAVAILABLE, READY }

    public record Capture(Status status, Map<ResourceLocation, MagazineDescriptor> families,
            Map<ResourceLocation, ResourceLocation> familyByRecipe, Map<String, String> rejected) {
        public Capture {
            families = Map.copyOf(families);
            familyByRecipe = Map.copyOf(familyByRecipe);
            rejected = Map.copyOf(rejected);
        }

        static Capture empty(Status status) {
            return new Capture(status, Map.of(), Map.of(), Map.of());
        }
    }

    record Family(ResourceLocation ammoId, int capacity, int extensionLevel,
            Set<ResourceLocation> compatibleGuns) { }

    /** Only this boundary can refer to the optional add-on. Fake sources exercise lifecycle failures. */
    interface Source {
        Set<String> families();
        boolean isMagazine(ItemStack stack);
        String family(ItemStack stack);
        int capacity(ItemStack stack);
        int ammoCount(ItemStack stack);
        boolean recipeEnabled(String family);
        Family describe(String family);
    }
}

package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;

import net.minecraft.resources.ResourceLocation;

/** Optional presentation band assigned to automatic weapons by mechanical score. */
public record AutomaticWeaponProgressionBand(
        ResourceLocation id,
        int maximumScore,
        String title,
        Optional<String> translationKey) {
    public static final int MAX_TITLE_LENGTH = 80;
    public static final int MAX_TRANSLATION_KEY_LENGTH = 160;

    public AutomaticWeaponProgressionBand {
        translationKey = translationKey == null ? Optional.empty() : translationKey;
        if (id == null
                || maximumScore < 0
                || maximumScore > ResearchTechTreeContract.SCORE_MAX
                || !validTitle(title)
                || translationKey.filter(value -> !validTranslationKey(value)).isPresent()) {
            throw new IllegalArgumentException("Automatic weapon progression band is invalid");
        }
    }

    public boolean contains(int mechanicalScore) {
        return mechanicalScore >= 0 && mechanicalScore <= maximumScore;
    }

    private static boolean validTitle(String value) {
        return value != null
                && !value.isBlank()
                && value.equals(value.trim())
                && value.length() <= MAX_TITLE_LENGTH
                && value.chars().noneMatch(Character::isISOControl);
    }

    private static boolean validTranslationKey(String value) {
        return value != null
                && !value.isBlank()
                && value.equals(value.trim())
                && value.length() <= MAX_TRANSLATION_KEY_LENGTH
                && value.chars().noneMatch(character -> Character.isWhitespace(character)
                        || Character.isISOControl(character));
    }
}

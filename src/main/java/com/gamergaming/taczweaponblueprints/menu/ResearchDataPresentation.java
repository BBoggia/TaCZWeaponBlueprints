package com.gamergaming.taczweaponblueprints.menu;

import java.util.*;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalysisRewardClass;

/** Aggregate server facts only: no target, definition, discovery list, or upcoming result. */
public record ResearchDataPresentation(int minimumStation, int configuredTiers, boolean allowLearned,
        boolean allowUndiscovered, boolean partialProgressBias, AnalysisRewardClass reward, List<Station> stations) {
    public ResearchDataPresentation {
        stations = List.copyOf(stations);
        if (minimumStation < 1 || minimumStation > 3 || configuredTiers < 1 || configuredTiers > 7 || reward == null
                || stations.size() != 3) throw new IllegalArgumentException("invalid Data presentation");
        for (int i=0;i<3;i++) if (stations.get(i).tier()!=i+1) throw new IllegalArgumentException("Data station rows must be ordered");
    }
    public Station at(int tier) { if (tier<1||tier>3) throw new IllegalArgumentException("invalid station"); return stations.get(tier-1); }
    public record Station(int tier, int eligibleTiers, double blueprintProbability, boolean blueprintsPossible,
            boolean fragmentsPossible, int minimumFragments, int maximumFragments) {
        public Station {
            if (tier<1||tier>3||eligibleTiers<0||eligibleTiers>7||!Double.isFinite(blueprintProbability)
                    ||blueprintProbability<0||blueprintProbability>1||minimumFragments<0||maximumFragments>64
                    ||maximumFragments<minimumFragments||fragmentsPossible!=(minimumFragments>0&&maximumFragments>0)
                    || !fragmentsPossible && (minimumFragments!=0||maximumFragments!=0)
                    || (eligibleTiers>0)!= (blueprintsPossible||fragmentsPossible)
                    || !blueprintsPossible && blueprintProbability!=0 || !fragmentsPossible && blueprintsPossible && blueprintProbability!=1)
                throw new IllegalArgumentException("invalid Data odds");
        }
        public boolean available() { return eligibleTiers>0; }
    }
}

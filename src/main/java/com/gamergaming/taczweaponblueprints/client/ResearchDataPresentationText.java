package com.gamergaming.taczweaponblueprints.client;

import java.util.*;
import com.gamergaming.taczweaponblueprints.menu.ResearchDataPresentation;
import net.minecraft.network.chat.Component;

/** Pure, identity-free odds labels shared by the screen, narration, and tests. */
public final class ResearchDataPresentationText {
    private ResearchDataPresentationText() { }
    public static String tiers(int mask) {
        if(mask<0||mask>7) throw new IllegalArgumentException("invalid tier mask");
        var values=new ArrayList<String>();for(int i=1;i<=3;i++) if((mask&(1<<(i-1)))!=0)values.add("T"+i);
        return String.join(" / ",values);
    }
    public static String full(ResearchDataPresentation.Station row) {
        return AnalyzerProbabilityText.format(row.blueprintProbability(),row.blueprintsPossible(),row.blueprintsPossible()&&!row.fragmentsPossible());
    }
    public static String fragments(ResearchDataPresentation.Station row) {
        return AnalyzerProbabilityText.format(1-row.blueprintProbability(),row.fragmentsPossible(),row.fragmentsPossible()&&!row.blueprintsPossible());
    }
    public static String quantity(ResearchDataPresentation.Station row) {
        return row.minimumFragments()==row.maximumFragments()?Integer.toString(row.minimumFragments()):row.minimumFragments()+"–"+row.maximumFragments();
    }
    public static List<Component> details(ResearchDataPresentation d,int station) {
        var lines=new ArrayList<Component>(); var row=d.at(station);
        if(row.available()) {
            lines.add(tr("full",full(row))); lines.add(tr("fragments",quantity(row),fragments(row)));
            if(row.eligibleTiers()!=d.configuredTiers())lines.add(tr("eligible_tiers",tiers(row.eligibleTiers())));
        } else lines.add(tr("odds_unavailable"));
        lines.add(tr("station",station,d.minimumStation()));
        lines.add(tr(d.allowLearned()?"duplicates_allowed":"duplicates_excluded"));
        lines.add(tr(d.allowUndiscovered()?"undiscovered_allowed":"discovered_only"));
        lines.add(tr("empty_output"));
        for(var other:d.stations())if(other.tier()!=station)lines.add(other.available()
                ?tr("station_compare",other.tier(),full(other),quantity(other)):tr("station_unavailable",other.tier()));
        if(d.partialProgressBias())lines.add(tr("partial_bias"));
        lines.add(ClientResearchCurrency.text("gui.taczweaponblueprints.analyzer."+(d.reward().recyclable()?"recyclable":"protected")));
        lines.add(Component.translatable("gui.taczweaponblueprints.analyzer.learning."+(d.reward().learningMode()==com.gamergaming.taczweaponblueprints.progression.PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES?"bypass":"prerequisites")));
        return List.copyOf(lines);
    }
    public static Component tr(String key,Object...values){return Component.translatable("gui.taczweaponblueprints.analyzer.data."+key,values);}
}

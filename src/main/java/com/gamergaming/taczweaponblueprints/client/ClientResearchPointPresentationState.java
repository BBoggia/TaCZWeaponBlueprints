package com.gamergaming.taczweaponblueprints.client;

import java.util.LinkedHashSet;
import java.util.List;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService.Feedback;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService.HelpEntry;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService.HelpSnapshot;

/** Connection-local disclosure-filtered RP help and notification aggregation. */
public final class ClientResearchPointPresentationState {
    public static final long AGGREGATION_WINDOW_MILLIS = 2_000L;

    private static HelpSnapshot help = HelpSnapshot.EMPTY;
    private static FeedbackView feedback = FeedbackView.EMPTY;
    private static long lastFeedbackMillis = Long.MIN_VALUE;

    private ClientResearchPointPresentationState() {
    }

    public static synchronized HelpSnapshot help() {
        return help;
    }

    public static synchronized void acceptHelp(HelpSnapshot snapshot) {
        if (snapshot != null && snapshot.revision() >= help.revision()) {
            help = snapshot;
        }
    }

    public static synchronized FeedbackView acceptFeedback(Feedback next, long nowMillis) {
        if (next == null || !next.present() || nowMillis < 0L) {
            return feedback;
        }
        boolean aggregate = lastFeedbackMillis != Long.MIN_VALUE
                && nowMillis >= lastFeedbackMillis
                && nowMillis - lastFeedbackMillis <= AGGREGATION_WINDOW_MILLIS;
        LinkedHashSet<String> names = new LinkedHashSet<>(
                aggregate ? feedback.namedAwards() : List.of());
        for (String name : next.namedAwards()) {
            if (names.size() >= PlayerProgressionLimits.MAX_RESEARCH_POINT_FEEDBACK_NAMES) {
                break;
            }
            names.add(name);
        }
        int points = aggregate
                ? saturatedAdd(feedback.awardedPoints(), next.awardedPoints())
                : next.awardedPoints();
        int generic = aggregate
                ? Math.min(
                        PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_GROUPS_PER_EVENT,
                        feedback.genericAwardCount() + next.genericAwardCount())
                : next.genericAwardCount();
        feedback = new FeedbackView(
                points,
                generic,
                (aggregate && feedback.claimedAtCap()) || next.claimedAtCap(),
                List.copyOf(names));
        lastFeedbackMillis = nowMillis;
        return feedback;
    }

    public static synchronized void clear() {
        help = HelpSnapshot.EMPTY;
        feedback = FeedbackView.EMPTY;
        lastFeedbackMillis = Long.MIN_VALUE;
    }

    private static int saturatedAdd(int left, int right) {
        return (int) Math.min(
                PlayerProgressionLimits.MAX_RESEARCH_POINTS,
                Math.max(0L, (long) left + right));
    }

    public record FeedbackView(
            int awardedPoints,
            int genericAwardCount,
            boolean claimedAtCap,
            List<String> namedAwards) {
        private static final FeedbackView EMPTY = new FeedbackView(0, 0, false, List.of());

        public FeedbackView {
            namedAwards = namedAwards == null ? List.of() : List.copyOf(namedAwards);
        }
    }
}

package de.minenight.tiertest;

import java.util.UUID;

public final class TierRoundScoreTest {
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Tier round score regression");
    }

    public static void main(String[] args) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        TierRoundScore match = new TierRoundScore(a, b, 3);
        long one = match.begin();
        check(match.resolve(one, false, true));
        check(!match.resolve(one, false, true));
        check(match.score().first() == 1);
        check(match.finalizeRound(one));
        long two = match.begin();
        check(!match.resolve(one, false, true));
        check(match.resolve(two, true, true));
        check(match.finalizeRound(two));
        check(match.round() == 2 && match.score().first() == 1);
        long three = match.begin();
        check(match.resolve(three, false, true));
        check(match.finalizeRound(three));
        long last = match.begin();
        check(match.resolve(last, false, true));
        check(a.equals(match.winner()));
        check(match.rerun(last));
        check(match.score().first() == 2 && match.winner() == null);
        long repeat = match.begin();
        check(!match.rerun(last));
        check(match.resolve(repeat, false, true));
        check(match.finalizeRound(repeat));
        check(match.phase() == TierRoundScore.Phase.FINISHED);
        check(a.equals(match.winner()));
        check(!match.rerun(repeat));
        System.out.println("TierRoundScore regression checks passed");
    }
}

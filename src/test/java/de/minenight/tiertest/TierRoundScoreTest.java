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
        TierRoundScore saved=TierRoundScore.restored(a,b,4,2,1,4);
        check(saved.score().first()==2&&saved.score().second()==1&&saved.target()==4);
        long resumed=saved.begin();
        check(saved.round()==4&&saved.resolve(resumed,false,true));
        check(saved.nextWaitingRound()==5&&saved.finalizeRound(resumed));
        check(saved.score().first()==3&&saved.score().second()==1);
        System.out.println("TierRoundScore regression checks passed");
    }
}

package de.minenight.tiertest;

import java.util.Objects;
import java.util.UUID;

public final class TierRoundScore {
    public enum Phase { WAITING, ACTIVE, REVIEW, FINISHED }
    public record Score(int first, int second) {}

    private final UUID firstPlayer;
    private final UUID secondPlayer;
    private final int target;
    private int first;
    private int second;
    private long round;
    private long attempt;
    private Score before = new Score(0, 0);
    private Phase phase = Phase.WAITING;

    public TierRoundScore(UUID firstPlayer, UUID secondPlayer, int target) {
        this.firstPlayer = Objects.requireNonNull(firstPlayer);
        this.secondPlayer = Objects.requireNonNull(secondPlayer);
        if (firstPlayer.equals(secondPlayer) || target < 1) throw new IllegalArgumentException("Invalid match");
        this.target = target;
    }

    public long begin() {
        if (phase != Phase.WAITING) throw new IllegalStateException("Round is not waiting");
        if (round == 0) round = 1;
        before = score();
        phase = Phase.ACTIVE;
        return ++attempt;
    }

    public boolean resolve(long token, boolean firstDied, boolean secondDied) {
        if (phase != Phase.ACTIVE || token != attempt || (!firstDied && !secondDied)) return false;
        if (firstDied && !secondDied) second++;
        if (secondDied && !firstDied) first++;
        phase = Phase.REVIEW;
        return true;
    }

    public boolean rerun(long token) {
        if (phase != Phase.REVIEW || token != attempt) return false;
        first = before.first();
        second = before.second();
        phase = Phase.WAITING;
        return true;
    }

    public boolean finalizeRound(long token) {
        if (phase != Phase.REVIEW || token != attempt) return false;
        if (winner() != null) {
            phase = Phase.FINISHED;
        } else {
            if (!score().equals(before)) round++;
            phase = Phase.WAITING;
        }
        return true;
    }

    public UUID winner() {
        if (first >= target) return firstPlayer;
        if (second >= target) return secondPlayer;
        return null;
    }

    public Score score() { return new Score(first, second); }
    public Score before() { return before; }
    public long round() { return round; }
    public Phase phase() { return phase; }
}

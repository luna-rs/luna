package io.luna.game.action;

import io.luna.game.model.mob.Mob;

public  class CountdownTimer extends Action<Mob> {
    private long start;
    private long remaining;
// todo nee ds testing!!
    public CountdownTimer(Mob mob, long start) {
        super(mob, ActionType.SOFT);
        this.start = start;
        remaining = start;
    }

    public void onComplete() {

    }

    public void onCountdown() {

    }

    @Override
    public boolean run() {
        long result = --remaining;
        if (result <= 0) {
            onComplete();
            return true;
        }
        onCountdown();
        return false;
    }

    public void setStart(long start) {
        this.start = start;
        if(start < remaining) {
            remaining = start;
        }
    }

    public long getStart() {
        return start;
    }

    public void setRemaining(long remaining) {
        this.remaining = remaining;
    }

    public long getRemaining() {
        return remaining;
    }
}

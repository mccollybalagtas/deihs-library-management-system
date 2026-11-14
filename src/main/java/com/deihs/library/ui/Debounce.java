package com.deihs.library.ui;

import javafx.animation.PauseTransition;
import javafx.util.Duration;

/**
 * Coalesces rapid changes into a single action.
 *
 * <p>Typing in a search box fires a change on every key. Waiting a short
 * moment before querying keeps the database from running a search for each
 * letter while still feeling immediate.</p>
 */
public final class Debounce {

    private final PauseTransition pause;

    public Debounce(Duration delay, Runnable action) {
        pause = new PauseTransition(delay);
        pause.setOnFinished(e -> action.run());
    }

    /** Restarts the timer; the action runs once the changes stop. */
    public void trigger() {
        pause.playFromStart();
    }
}

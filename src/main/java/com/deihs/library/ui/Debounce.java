package com.deihs.library.ui;

import javafx.animation.PauseTransition;
import javafx.util.Duration;

/**
 * Coalesces rapid changes into a single action.
 *
 * <p>Typing in a search box fires a change on every key. Waiting a short
 * moment before querying keeps the database from running a search for each
 * letter while still feeling immediate.</p>
 *
 * <p>Part of: the shared widget layer. It wraps a JavaFX timer so callers do
 * not have to manage timelines themselves.</p>
 * <p>Used by: the search and filter boxes in the catalogue, circulation,
 * collection, loans, users and dashboard screens.</p>
 * <p>Key rules: every trigger restarts the timer, so the action runs once,
 * after the changes stop, never once per keystroke.</p>
 */
public final class Debounce {

    // The single timer. It is restarted, not recreated, on every change.
    private final PauseTransition pause;

    // WHAT: stores the delay and the action to run when it expires.
    // WHY: the delay belongs to the caller (search feels different from
    // saving), while the coalescing logic stays the same.
    // CALLED BY: views that own a text field or filter control.
    public Debounce(Duration delay, Runnable action) {
        pause = new PauseTransition(delay);
        // The action runs only when the timer completes, i.e. after the last
        // trigger plus the full delay.
        pause.setOnFinished(e -> action.run());
    }

    // WHAT: restarts the countdown. WHY: each new keystroke should cancel the
    // pending search. CALLED BY: change listeners on the input controls.
    // The action runs once the changes stop.
    public void trigger() {
        // playFromStart both restarts and replays, which is exactly the
        // "cancel the previous wait" behaviour a debounce needs.
        pause.playFromStart();
    }
}

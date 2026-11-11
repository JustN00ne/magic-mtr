package org.justnoone.jme.util;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Objects;

/**
 * What linking an axle counter as a crossing controller's start or end node does, given the counters already linked.
 * A controller can have several start and end nodes (eg one per track); the single-counter rule is kept for callers
 * that only have one. Pure logic (no Minecraft types) so it can be unit tested.
 */
public final class CrossingLinkRules {

    public enum CounterLinkResult {
        /**
         * The role was empty and now holds the counter.
         */
        LINKED,
        /**
         * The role held another counter, which the new one replaces.
         */
        REPLACED,
        /**
         * The counter already held this role; linking it again removes it.
         */
        UNLINKED,
        /**
         * The counter is already the controller's other node; a counter cannot be both start and end.
         */
        REJECTED_OTHER_ROLE,
        /**
         * The role already holds the maximum number of counters.
         */
        REJECTED_FULL,
    }

    private CrossingLinkRules() {
    }

    /**
     * @param counter      the counter being linked
     * @param currentRole  the counter currently in the role being linked, or {@code null}
     * @param currentOther the counter currently in the other role, or {@code null}
     */
    public static <T> CounterLinkResult linkCounter(T counter, @Nullable T currentRole, @Nullable T currentOther) {
        Objects.requireNonNull(counter);
        if (counter.equals(currentRole)) {
            return CounterLinkResult.UNLINKED;
        }
        if (counter.equals(currentOther)) {
            return CounterLinkResult.REJECTED_OTHER_ROLE;
        }
        return currentRole == null ? CounterLinkResult.LINKED : CounterLinkResult.REPLACED;
    }

    /**
     * Linking a counter to a role that holds several counters: it is added, or removed if it is already there.
     *
     * @param counter      the counter being linked
     * @param currentRole  the counters currently in the role being linked
     * @param currentOther the counters currently in the other role
     * @param maxPerRole   how many counters a role can hold
     */
    public static <T> CounterLinkResult toggleCounter(T counter, Collection<T> currentRole, Collection<T> currentOther, int maxPerRole) {
        Objects.requireNonNull(counter);
        if (currentRole.contains(counter)) {
            return CounterLinkResult.UNLINKED;
        }
        if (currentOther.contains(counter)) {
            return CounterLinkResult.REJECTED_OTHER_ROLE;
        }
        return currentRole.size() >= maxPerRole ? CounterLinkResult.REJECTED_FULL : CounterLinkResult.LINKED;
    }
}

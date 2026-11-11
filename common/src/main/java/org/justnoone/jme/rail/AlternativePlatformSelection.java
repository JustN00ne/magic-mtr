package org.justnoone.jme.rail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Platform preference order used when dispatching a train to a stop that has alternative platforms.
 */
public final class AlternativePlatformSelection {

    private AlternativePlatformSelection() {
    }

    /**
     * Orders candidate platforms by how busy they are (lower score first). On equal scores the primary platform comes
     * first, then the remaining ties are spread deterministically using {@code seed}.
     *
     * @param candidateIds candidate platform ids (including the primary platform)
     * @param scores       busy score per platform (reservations, trains approaching, ...); missing means 0
     */
    public static List<Long> orderCandidates(Collection<Long> candidateIds, long primaryPlatformId, Map<Long, Integer> scores, long seed) {
        final List<Long> ordered = new ArrayList<>(candidateIds);
        ordered.sort(Comparator
                .comparingInt((Long candidateId) -> scores.getOrDefault(candidateId, 0))
                .thenComparingInt(candidateId -> candidateId == primaryPlatformId ? 0 : 1)
                .thenComparingLong(candidateId -> mix64(seed ^ candidateId))
        );
        return ordered;
    }

    static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }
}

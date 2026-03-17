package org.justnoone.jme.util;

import org.mtr.core.data.PathData;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PathCache {

    private static final Object LOCK = new Object();
    private static final LinkedHashMap<Key, Entry> CACHE = new LinkedHashMap<>(64, 0.75F, true);
    // Alternative-platform reroutes can involve many platform pairs (think 100+ platforms).
    // Keep this reasonably large so we don't thrash the cache during bursts of deployments.
    private static final int MAX_ENTRIES = 2048;
    private static final long TTL_MILLIS = 5 * 60 * 1000;

    private PathCache() {
    }

    public static ObjectArrayList<PathData> getCopy(long startId, long endId, int stopIndex, long cruisingAltitude, long nowMillis) {
        final Key key = new Key(startId, endId, stopIndex, cruisingAltitude);
        synchronized (LOCK) {
            final Entry cached = CACHE.get(key);
            if (cached == null) {
                return null;
            }
            if (nowMillis - cached.cachedAtMillis > TTL_MILLIS) {
                CACHE.remove(key);
                return null;
            }
            return new ObjectArrayList<>(cached.path);
        }
    }

    public static void putCopy(long startId, long endId, int stopIndex, long cruisingAltitude, ObjectArrayList<PathData> path, long nowMillis) {
        if (path == null || path.isEmpty()) {
            return;
        }

        final Key key = new Key(startId, endId, stopIndex, cruisingAltitude);
        synchronized (LOCK) {
            pruneLocked(nowMillis);
            CACHE.put(key, new Entry(new ObjectArrayList<>(path), nowMillis));
            while (CACHE.size() > MAX_ENTRIES) {
                final Iterator<Map.Entry<Key, Entry>> iterator = CACHE.entrySet().iterator();
                if (!iterator.hasNext()) {
                    break;
                }
                iterator.next();
                iterator.remove();
            }
        }
    }

    private static void pruneLocked(long nowMillis) {
        CACHE.entrySet().removeIf(entry -> nowMillis - entry.getValue().cachedAtMillis > TTL_MILLIS);
    }

    private static final class Key {

        private final long startId;
        private final long endId;
        private final int stopIndex;
        private final long cruisingAltitude;

        private Key(long startId, long endId, int stopIndex, long cruisingAltitude) {
            this.startId = startId;
            this.endId = endId;
            this.stopIndex = stopIndex;
            this.cruisingAltitude = cruisingAltitude;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key)) {
                return false;
            }
            final Key other = (Key) o;
            return startId == other.startId
                    && endId == other.endId
                    && stopIndex == other.stopIndex
                    && cruisingAltitude == other.cruisingAltitude;
        }

        @Override
        public int hashCode() {
            int result = (int) (startId ^ (startId >>> 32));
            result = 31 * result + (int) (endId ^ (endId >>> 32));
            result = 31 * result + stopIndex;
            result = 31 * result + (int) (cruisingAltitude ^ (cruisingAltitude >>> 32));
            return result;
        }
    }

    private static final class Entry {

        private final ObjectArrayList<PathData> path;
        private final long cachedAtMillis;

        private Entry(ObjectArrayList<PathData> path, long cachedAtMillis) {
            this.path = path;
            this.cachedAtMillis = cachedAtMillis;
        }
    }
}

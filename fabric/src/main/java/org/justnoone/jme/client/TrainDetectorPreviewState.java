package org.justnoone.jme.client;

import java.util.Collections;
import java.util.List;

/**
 * Client-only render state used by {@code WidgetMap} mixins to draw the train detector preview range.
 */
public final class TrainDetectorPreviewState {

    private static volatile List<Object[]> previewEdges = Collections.emptyList();

    private TrainDetectorPreviewState() {
    }

    public static List<Object[]> getPreviewEdges() {
        return previewEdges;
    }

    public static void setPreviewEdges(List<Object[]> edges) {
        previewEdges = edges == null ? Collections.emptyList() : edges;
    }

    public static void clear() {
        previewEdges = Collections.emptyList();
    }
}


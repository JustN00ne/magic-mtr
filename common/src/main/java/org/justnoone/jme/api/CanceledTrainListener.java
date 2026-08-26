package org.justnoone.jme.api;

import java.util.EventListener;

/**
 * Receives an event whenever a train is cancelled.
 */
public interface CanceledTrainListener extends EventListener {

    void onTrainCancelled(CanceledTrainInfo canceledTrain);
}

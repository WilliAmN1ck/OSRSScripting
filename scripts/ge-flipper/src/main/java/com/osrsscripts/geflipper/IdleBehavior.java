package com.osrsscripts.geflipper;

import java.time.Instant;

/** Invoked once per tick in which the flipper had nothing to do. */
public interface IdleBehavior {

    void onIdle(Instant now);
}

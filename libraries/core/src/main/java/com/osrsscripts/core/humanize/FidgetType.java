package com.osrsscripts.core.humanize;

/**
 * A kind of small, side-effect-free idle action the flipper can perform to look human. The mapping
 * from a type to an actual game action is SDK-coupled and lives in the script; this enum keeps the
 * selection policy ({@link FidgetSelector}) testable without a client.
 */
public enum FidgetType {

    /** Drift the camera a few degrees. */
    CAMERA,

    /** Glance at a harmless side tab, then return to a home tab. */
    TAB_GLANCE,

    /** Drift the mouse idly within the game canvas. */
    MOUSE_DRIFT
}

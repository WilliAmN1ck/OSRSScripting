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

    /** Drift the mouse idly toward a screen edge. */
    MOUSE_DRIFT,

    /** Briefly open the world map, then close it. */
    WORLD_MAP,

    /** Hover (without clicking) over a GE slot or inventory item. */
    HOVER
}

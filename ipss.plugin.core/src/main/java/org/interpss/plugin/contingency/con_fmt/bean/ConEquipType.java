package org.interpss.plugin.contingency.con_fmt.bean;

/**
 * The type of equipment targeted by an equipment-level contingency event.
 *
 * <p>Used by {@link ConEquipEvent} (REMOVE / ADD / BLOCK actions) and by
 * {@link ConEquipMoveEvent} (MOVE action).
 */
public enum ConEquipType {
    /** Generating machine — PSS/E keywords {@code MACHINE} / {@code UNIT}. */
    MACHINE,

    /** Constant-power load ({@code LOAD}). */
    LOAD,

    /** Active (real-power) component of a load ({@code ACTIVE LOAD}). */
    ACTIVE_LOAD,

    /** Reactive (imaginary-power) component of a load ({@code REACTIVE LOAD}). */
    REACTIVE_LOAD,

    /** Fixed shunt ({@code SHUNT}). */
    SHUNT,

    /** Switched shunt ({@code SWSHUNT}). */
    SWSHUNT,

    /** Generation quantity ({@code GENERATION}). */
    GENERATION,

    /** Two-terminal DC transmission line ({@code TWOTERMDC}). */
    DC_LINE
}

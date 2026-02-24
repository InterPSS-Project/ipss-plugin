package org.interpss.plugin.contingency.con_format.bean;

/**
 * The action performed on a bus-attached piece of equipment in a contingency event.
 *
 * <ul>
 *   <li>{@link #REMOVE} — trip / remove a unit
 *       ({@code REMOVE MACHINE|UNIT|LOAD|SHUNT|SWSHUNT}).</li>
 *   <li>{@link #ADD}    — restore a unit
 *       ({@code ADD MACHINE|UNIT|LOAD|SHUNT|SWSHUNT}).</li>
 *   <li>{@link #BLOCK}  — block a two-terminal DC line
 *       ({@code BLOCK TWOTERMDC}).</li>
 * </ul>
 */
public enum ConEquipAction {
    REMOVE,
    ADD,
    BLOCK
}

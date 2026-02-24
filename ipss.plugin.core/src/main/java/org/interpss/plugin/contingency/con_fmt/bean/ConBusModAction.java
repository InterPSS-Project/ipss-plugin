package org.interpss.plugin.contingency.con_fmt.bean;

/**
 * The modification verb for a bus-level quantity event.
 *
 * <ul>
 *   <li>{@link #SET}      — {@code SET BUS n [attr] TO r [MW|PERCENT]}</li>
 *   <li>{@link #CHANGE}   — {@code CHANGE BUS n [attr] BY r [MW|PERCENT]}</li>
 *   <li>{@link #INCREASE} — {@code INCREASE BUS n [attr] BY r [MW|PERCENT]}</li>
 *   <li>{@link #DECREASE} — {@code DECREASE BUS n [attr] BY r [MW|PERCENT]}
 *       (also covers the {@code REDUCE} keyword synonym)</li>
 * </ul>
 */
public enum ConBusModAction {
    SET,
    CHANGE,
    INCREASE,
    DECREASE
}

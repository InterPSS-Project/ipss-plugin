package org.interpss.plugin.contingency.con_frmt.bean;

/**
 * The action performed on a branch or transformer in a contingency event.
 *
 * <ul>
 *   <li>{@link #DISCONNECT} — open a 2-terminal branch, or open <em>all</em> windings of a
 *       3W transformer ({@code DISCONNECT/OPEN/TRIP BRANCH ... [TO BUS k]}).
 *       Whether the target is 2-terminal or 3W is determined by
 *       {@link ConBranchEvent#isThreeWinding()}.</li>
 *   <li>{@link #CLOSE} — restore a 2-terminal branch, or restore all windings of a
 *       3W transformer ({@code CLOSE BRANCH ... [TO BUS k]}).</li>
 *   <li>{@link #DISCONNECT_3W_WINDING} — open a <em>single</em> winding of a 3W transformer
 *       ({@code DISCONNECT THREEWINDING AT BUS i TO BUS j TO BUS k}).</li>
 * </ul>
 */
public enum ConBranchAction {
    /** Open: 2-terminal branch or all windings of a 3W transformer. */
    DISCONNECT,

    /** Restore: 2-terminal branch or all windings of a 3W transformer. */
    CLOSE,

    /** Open a single winding of a 3W transformer. */
    DISCONNECT_3W_WINDING
}

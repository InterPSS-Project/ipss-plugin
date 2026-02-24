package org.interpss.plugin.contingency.con_format.bean;

/**
 * An event record that acts on a two-terminal branch/transformer or on a single
 * winding of a three-winding transformer.
 *
 * <p>For two-terminal events {@link #thirdBusNum} is {@code null}.
 * For three-winding events {@link #thirdBusNum} holds the bus number of the star (third) terminal.
 *
 * <p>Example lines that map to this record:
 * <pre>
 *   DISCONNECT BRANCH FROM BUS 524 TO BUS 523 CIRCUIT 1
 *   OPEN LINE FROM BUS 524 TO BUS 523 CKT 1
 *   CLOSE BRANCH FROM BUS 524 TO BUS 523 CIRCUIT 1
 *   DISCONNECT BRANCH FROM BUS 1 TO BUS 2 TO BUS 3 CIRCUIT 1
 *   DISCONNECT THREEWINDING AT BUS 1 TO BUS 2 TO BUS 3 CIRCUIT 1
 * </pre>
 */
public class ConBranchEvent {

    /** Action performed on the branch or transformer. */
    private ConBranchAction action;

    /** integer bus number of the from-terminal. */
    private int fromBusNum;

    /** integer bus number of the to-terminal. */
    private int toBusNum;

    /**
     * integer bus number of the third terminal (star bus) for
     * three-winding transformer events; {@code 0} for two-terminal events
     * (valid bus numbers are always &gt;= 1).
     */
    private int thirdBusNum;

    /** Circuit identifier (e.g. {@code "1"}, {@code "BK"}). */
    private String ckt;

    // ---- constructors ----

    public ConBranchEvent() {}

    /** Two-terminal constructor. */
    public ConBranchEvent(ConBranchAction action, int fromBusNum, int toBusNum, String ckt) {
        this.action     = action;
        this.fromBusNum = fromBusNum;
        this.toBusNum   = toBusNum;
        this.ckt        = ckt;
    }

    /** Three-winding constructor. */
    public ConBranchEvent(ConBranchAction action, int fromBusNum, int toBusNum, int thirdBusNum, String ckt) {
        this(action, fromBusNum, toBusNum, ckt);
        this.thirdBusNum = thirdBusNum;
    }

    // ---- accessors ----

    public ConBranchAction getAction() { return action;  }
    public int    getFromBusNum()       { return fromBusNum; }
    public int    getToBusNum()       { return toBusNum; }
    public int    getThirdBusNum()       { return thirdBusNum; }
    public String getCkt()           { return ckt;     }
    public boolean isThreeWinding()  { return thirdBusNum != 0; }

    public void setAction(ConBranchAction a)  { this.action  = a;       }
    public void setFromBusNum(int v)             { this.fromBusNum = v;       }
    public void setToBusNum(int v)             { this.toBusNum = v;       }
    public void setThirdBusNum(int v)             { this.thirdBusNum = v;       }
    public void setCkt(String v)              { this.ckt     = v;       }

    @Override
    public String toString() {
        if (thirdBusNum != 0) {
            return String.format("%s BUS %d TO BUS %d TO BUS %d CKT %s",
                    action, fromBusNum, toBusNum, thirdBusNum, ckt);
        }
        return String.format("%s BUS %d TO BUS %d CKT %s", action, fromBusNum, toBusNum, ckt);
    }
}

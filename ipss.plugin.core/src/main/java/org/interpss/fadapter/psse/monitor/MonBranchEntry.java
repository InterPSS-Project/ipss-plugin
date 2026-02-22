package org.interpss.fadapter.psse.monitor;

/**
 * A single branch entry inside a MONITOR INTERFACE block.
 *
 * <pre>
 * MONITOR BRANCH FROM BUS 524 TO BUS 523 CKT 1 '
 * </pre>
 *
 * JSON:
 * <pre>
 * { "fromBusNum": 524, "toBusNum": 523, "ckt": "1", "comment": "'circuit 1'" }
 * </pre>
 */
public class MonBranchEntry {

    private int    fromBusNum;
    private int    toBusNum;
    private String ckt;
    /** Optional comment extracted from the '/' tail of the line (bus names). */
    private String comment;

    public MonBranchEntry() {}

    public MonBranchEntry(int fromBusNum, int toBusNum, String ckt, String comment) {
        this.fromBusNum = fromBusNum;
        this.toBusNum   = toBusNum;
        this.ckt        = ckt;
        this.comment    = comment;
    }

    public int    getFromBusNum() { return fromBusNum; }
    public int    getToBusNum()   { return toBusNum;   }
    public String getCkt()        { return ckt;        }
    public String getComment()    { return comment;    }

    public void setFromBusNum(int v)    { this.fromBusNum = v; }
    public void setToBusNum(int v)      { this.toBusNum   = v; }
    public void setCkt(String v)        { this.ckt        = v; }
    public void setComment(String v)    { this.comment    = v; }
}

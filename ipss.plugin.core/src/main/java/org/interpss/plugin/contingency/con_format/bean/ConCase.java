package org.interpss.plugin.contingency.con_format.bean;

import java.util.ArrayList;
import java.util.List;

/**
 * A single contingency case parsed from a PSS/E {@code .con} file.
 *
 * <p>Corresponds to a block delimited by:
 * <pre>
 *   CONTINGENCY 'label' [/'comment'] [CATEGORY cat]
 *     event records …
 *   END
 * </pre>
 *
 * <p>A case may contain a mix of {@link ConBranchEvent}, {@link ConBusEvent},
 * {@link ConEquipEvent}, {@link ConBusModEvent}, and {@link ConEquipMoveEvent} records
 * in the order they appear in the file.  At most 32 event records are allowed by
 * PSS/E, but this class does not enforce the limit.
 */
public class ConCase {

    /** Contingency label (up to 32 characters). */
    private String label;

    /** Optional occurrence-frequency / duration comment retained from the line header (after {@code /}). */
    private String comment;

    /** Optional category string from a {@code CATEGORY} clause on the CONTINGENCY line. */
    private String category;

    /** Branch / transformer event records in file order. */
    private List<ConBranchEvent> branchEvents = new ArrayList<>();

    /** Bus-disconnect event records in file order. */
    private List<ConBusEvent> busEvents = new ArrayList<>();

    /** Equipment REMOVE / ADD event records in file order. */
    private List<ConEquipEvent> equipEvents = new ArrayList<>();

    /** Bus-level quantity modification records (SET/CHANGE/INCREASE/DECREASE BUS) in file order. */
    private List<ConBusModEvent> busModEvents = new ArrayList<>();

    /** Bus-to-bus transfer records (MOVE r PERCENT LOAD FROM BUS i TO BUS j) in file order. */
    private List<ConEquipMoveEvent> equipMoveEvents = new ArrayList<>();

    public ConCase() {}

    public ConCase(String label) {
        this.label = label;
    }

    // ---- accessors ----

    public String getLabel()                   { return label;        }
    public String getComment()                 { return comment;      }
    public String getCategory()                { return category;     }
    public List<ConBranchEvent>   getBranchEvents()    { return branchEvents;    }
    public List<ConBusEvent>      getBusEvents()       { return busEvents;       }
    public List<ConEquipEvent>    getEquipEvents()     { return equipEvents;     }
    public List<ConBusModEvent>   getBusModEvents()    { return busModEvents;    }
    public List<ConEquipMoveEvent> getEquipMoveEvents()  { return equipMoveEvents;  }

    public void setLabel(String label)         { this.label    = label;    }
    public void setComment(String comment)     { this.comment  = comment;  }
    public void setCategory(String category)   { this.category = category; }
    public void setBranchEvents(List<ConBranchEvent> v)      { this.branchEvents   = v; }
    public void setBusEvents(List<ConBusEvent> v)            { this.busEvents      = v; }
    public void setEquipEvents(List<ConEquipEvent> v)        { this.equipEvents    = v; }
    public void setBusModEvents(List<ConBusModEvent> v)      { this.busModEvents   = v; }
    public void setEquipMoveEvents(List<ConEquipMoveEvent> v)  { this.equipMoveEvents = v; }

    public void addBranchEvent(ConBranchEvent e)     { branchEvents.add(e);   }
    public void addBusEvent(ConBusEvent e)            { busEvents.add(e);      }
    public void addEquipEvent(ConEquipEvent e)        { equipEvents.add(e);    }
    public void addBusModEvent(ConBusModEvent e)      { busModEvents.add(e);   }
    public void addEquipMoveEvent(ConEquipMoveEvent e)  { equipMoveEvents.add(e); }

    /** Total number of event records across all types. */
    public int eventCount() {
        return branchEvents.size() + busEvents.size()
             + equipEvents.size() + busModEvents.size() + equipMoveEvents.size();
    }

    @Override
    public String toString() {
        return String.format("ConCase{label='%s', events=%d}", label, eventCount());
    }
}

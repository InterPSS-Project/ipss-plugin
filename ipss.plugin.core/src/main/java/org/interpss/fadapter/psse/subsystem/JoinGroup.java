package org.interpss.fadapter.psse.subsystem;

/**
 * Represents a JOIN [label] ... END block within a PSS/E subsystem definition.
 * All criteria inside a JOIN are logically AND-ed together.
 *
 * Example:
 *   JOIN
 *       AREA 515
 *       KVRANGE 65 999
 *   END
 */
public class JoinGroup {

    private String label;
    private SelectionGroup selection = new SelectionGroup();

    public JoinGroup() {}

    public JoinGroup(String label) {
        this.label = label;
    }

    public void setLabel(String label)         { this.label = label; }
    public void setSelection(SelectionGroup s) { this.selection = s; }

    public String getLabel()             { return label; }
    public SelectionGroup getSelection() { return selection; }
}

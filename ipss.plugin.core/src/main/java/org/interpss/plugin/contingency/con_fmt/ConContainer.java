package org.interpss.plugin.contingency.con_fmt;

import java.util.ArrayList;
import java.util.List;

import org.interpss.plugin.contingency.con_fmt.bean.ConCase;
import org.interpss.plugin.contingency.con_fmt.util.ConFileConverter;

/**
 * Top-level container produced by parsing an entire PSS/E {@code .con} file.
 *
 * <p>JSON structure (written by {@link ConFileConverter}):
 * <pre>
 * {
 *   "sourceFile"  : "/path/to/input.con",
 *   "categories"  : ["P1", "P2", ...],
 *   "cases"       : [
 *     {
 *       "label"        : "80003",
 *       "comment"      : "P45:230:CLEC::BARKERS ...",
 *       "category"     : null,
 *       "branchEvents" : [],
 *       "busEvents"    : [ { "type": "DISCONNECT_BUS", "busNum": 500025 } ]
 *     }, ...
 *   ]
 * }
 * </pre>
 */
public class ConContainer {

    /** Absolute path of the source file that was parsed. */
    private String sourceFile;

    /**
     * Category labels declared in {@code CATEGORY … VALUE … END} blocks.
     * Preserved in declaration order.
     */
    private List<String> categories = new ArrayList<>();

    /** Contingency cases in file order. */
    private List<ConCase> cases = new ArrayList<>();

    public ConContainer() {}

    public ConContainer(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    // ---- accessors ----

    public String         getSourceFile() { return sourceFile; }
    public List<String>   getCategories() { return categories; }
    public List<ConCase>  getCases()      { return cases;      }

    public void setSourceFile(String v)           { this.sourceFile = v; }
    public void setCategories(List<String> v)     { this.categories = v; }
    public void setCases(List<ConCase> v)          { this.cases      = v; }

    public void addCategory(String cat)   { categories.add(cat); }
    public void addCase(ConCase c)        { cases.add(c);        }

    @Override
    public String toString() {
        return String.format("ConContainer{source='%s', categories=%d, cases=%d}",
                sourceFile, categories.size(), cases.size());
    }
}

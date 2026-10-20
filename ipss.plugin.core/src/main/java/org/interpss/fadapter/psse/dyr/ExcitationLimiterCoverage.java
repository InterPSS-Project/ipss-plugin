package org.interpss.fadapter.psse.dyr;

/** Reproducible source-list coverage, deliberately separate from runtime verification status. */
public final class ExcitationLimiterCoverage {
    private ExcitationLimiterCoverage() { }
    public static String markdown() {
        StringBuilder s=new StringBuilder("# Approved excitation-limiter coverage\n\n");
        s.append("Generated from `WeccApprovedDynamicModelCatalog.limiters()` and the visually reviewed May 2026 source PDF, page 2. Source SHA-256: `")
            .append(WeccApprovedDynamicModelCatalog.SOURCE_PDF_SHA256).append("`.\n\n")
            .append("Nine approved source rows include eight native mappings and one explicitly unconverted model. Three rows currently have loadable runtime paths; this is not equation or trajectory verification. A standalone kernel does not increase the loadable count.\n\n")
            .append("| Source | Native identifier | PowerWorld | Category | Runtime path | Notes |\n|---|---|---|---|---|---|\n");
        for(var row:WeccApprovedDynamicModelCatalog.limiters())
            s.append("| ").append(row.catalogName()).append(" | ").append(row.psseModel()).append(" | ")
                .append(row.powerWorldModel()).append(" | ").append(row.category()).append(" | ")
                .append(row.isImplementedExactly()?"loadable, not fully verified":"missing").append(" | ")
                .append(row.comments()).append(" |\n");
        return s.toString();
    }
    public static void main(String[] args){System.out.print(markdown());}
}

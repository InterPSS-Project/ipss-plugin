package org.interpss.fadapter.psse.dyr;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class ExcitationLimiterCoverageTest {
    @Test void allNineApprovedRowsArePresentWithoutCountingKernelsAsRuntimes() {
        var rows=WeccApprovedDynamicModelCatalog.limiters();assertEquals(9,rows.size());
        assertTrue(rows.stream().allMatch(r->r.approvalStatus()==WeccModelApprovalStatus.APPROVED));
        assertEquals(8,rows.stream().filter(WeccModelApproval::hasPsseModel).count());
        assertEquals(4,rows.stream().filter(WeccModelApproval::isImplementedExactly).count());
        assertEquals(6,rows.stream().filter(r->r.category()==DynamicModelCategory.OVER_EXCITATION_LIMITER).count());
        assertEquals(3,rows.stream().filter(r->r.category()==DynamicModelCategory.UNDER_EXCITATION_LIMITER).count());
    }
    @Test void unconvertedRowAndExactWrapperMappingAreNotInventedAliases() {
        var rows=WeccApprovedDynamicModelCatalog.limiters();var oel1=rows.stream().filter(r->r.catalogName().equals("OEL1")).findFirst().orElseThrow();
        assertFalse(oel1.hasPsseModel());assertFalse(oel1.isImplementedExactly());
        assertEquals("Not converted (277)",oel1.psseModel());
        var oel5=rows.stream().filter(r->r.catalogName().equals("OEL5C")).findFirst().orElseThrow();
        assertEquals("OEL5CU1",oel5.psseModel());assertTrue(DynamicModelCatalog.find("OEL5CU1").isEmpty());
        assertEquals(12,DynamicModelCatalog.find("OEL3C").orElseThrow().parameterCount());
    }
    @Test void exactPresentAndMissingSetsStayExplicitAndImmutable() {
        var rows=WeccApprovedDynamicModelCatalog.limiters();assertEquals(Set.of("UEL1","UEL2C","OEL2C","OEL3C"),
            rows.stream().filter(WeccModelApproval::isImplementedExactly).map(WeccModelApproval::catalogName).collect(java.util.stream.Collectors.toSet()));
        assertThrows(UnsupportedOperationException.class,()->rows.clear());
        assertEquals("2026-05-07",rows.stream().filter(r->r.catalogName().equals("BASOEL2")).findFirst().orElseThrow().statusEffective());
    }
    @Test void generatedMarkdownIncludesEverySourceRowAndNoVerificationPromotion() {
        String text=ExcitationLimiterCoverage.markdown();
        for(var row:WeccApprovedDynamicModelCatalog.limiters())assertTrue(text.contains("| "+row.catalogName()+" |"));
        assertTrue(text.contains("Four rows currently have bounded loadable runtime paths"));
        assertTrue(text.contains("kernel does not increase the loadable count"));
        assertTrue(text.contains("Explicit-dialect typed summation runtime; native import and reference-dialect acceptance remain missing."));
        assertTrue(DynamicModelCatalog.find("BASOEL2").isEmpty());
        assertTrue(text.contains(WeccApprovedDynamicModelCatalog.SOURCE_PDF_SHA256));
    }
}

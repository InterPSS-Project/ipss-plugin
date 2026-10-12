package org.interpss.threePhase.state;

import com.google.gson.Gson;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.acsc.PhaseCode;
import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class DStab3PBranchStateTest {
    @ParameterizedTest @CsvSource({"false,false","true,false","false,true","true,true"})
    void restoresPhaseMatricesAndClearsStaleOverrides(boolean explicit, boolean transformer) throws Exception {
        var network=ThreePhaseObjectFactory.create3PhaseDStabNetwork();
        ThreePhaseObjectFactory.create3PDStabBus("B1",network);
        ThreePhaseObjectFactory.create3PDStabBus("B2",network);
        var source=ThreePhaseObjectFactory.create3PBranch("B1","B2","1",network);
        source.setBranchCode(transformer?AclfBranchCode.XFORMER:AclfBranchCode.LINE); source.setPhaseCode(PhaseCode.ABC);
        source.setZ(new Complex(.03,.09)); source.setZ0(new Complex(.09,.27));
        var z=diagonal(new Complex(.04,.12)); z.ab=new Complex(.005,.015); z.ba=z.ab;
        source.setZabc(z); source.setFromShuntYabc(diagonal(new Complex(0,.003)));
        source.setZ(new Complex(.03,.09));
        source.setToShuntYabc(diagonal(new Complex(0,.004))); source.setXfrRatedKVA(1250);
        source.setFromTurnRatioABC(1.01,1.02,1.03); source.setToTurnRatioABC(.99,.98,.97);
        source.setCurrentAbcAtFromSide(new Complex3x1(new Complex(.1,.2),new Complex(.3,.4),new Complex(.5,.6)));
        if(explicit)source.setExplicitYabc(diagonal(new Complex(1,2)),diagonal(new Complex(-1,-2)),diagonal(new Complex(-3,-4)),diagonal(new Complex(3,4)));
        var gson=new Gson(); var json=gson.toJson(new DStab3PBranchState(source));
        var expectedY=source.getYffabc().aa;
        source.getZabc().aa=new Complex(99,99); source.setFromTurnRatioABC(9,9,9);
        var state=gson.fromJson(json,DStab3PBranchState.class);
        var restored=DStab3PBranchState.create(state);
        assertEquals(source.getId(),restored.getId()); assertNotSame(source,restored);
        assertEquals(new Complex(.03,.09),restored.getZ());
        assertEquals(new Complex(.04,.12),restored.getZabc().aa); assertEquals(new Complex(.005,.015),restored.getZabc().ab);
        assertEquals(new Complex(0,.003),restored.getFromShuntYabc().aa);
        assertArrayEquals(new double[]{1.01,1.02,1.03},restored.getFromTurnRatioABC());
        assertArrayEquals(new double[]{.99,.98,.97},restored.getToTurnRatioABC());
        assertEquals(1250,restored.getXfrRatedKVA());
        assertEquals(new Complex(.1,.2),restored.getCurrentAbcAtFromSide().a_0);
        assertEquals(explicit,restored.hasExplicitYabc()); assertEquals(0,expectedY.subtract(restored.getYffabc().aa).abs(),1e-12);
        restored.getZabc().ab=new Complex(77,77);
        assertEquals(new Complex(.005,.015),DStab3PBranchState.create(state).getZabc().ab);
        source.clearPhaseTurnRatios(); source.setExplicitYabc(null,null,null,null);
        var noOverrides=new DStab3PBranchState(source);
        restored.setExplicitYabc(diagonal(Complex.ONE),diagonal(Complex.ONE),diagonal(Complex.ONE),diagonal(Complex.ONE));
        noOverrides.restorePhaseFields(restored);
        assertFalse(restored.hasExplicitYabc()); assertFalse(restored.hasPhaseTurnRatio());
        assertNull(restored.getFromTurnRatioABC()); assertNull(restored.getToTurnRatioABC());
    }
    private static Complex3x3 diagonal(Complex value){var matrix=new Complex3x3(); matrix.aa=value; matrix.bb=value; matrix.cc=value; return matrix;}
}

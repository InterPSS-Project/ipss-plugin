package org.interpss.dstab.algo;
import static org.junit.jupiter.api.Assertions.*;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;
import org.junit.jupiter.api.Test;
/** Backward-compatible, pure accepted-state history selection. */
public class AcceptedProjectionHistoryContractTest {
    private static class Legacy implements StaggeredEulerParticipant {
        int accepted;
        public int staggeredStateCount(){return 3;}
        public void readStaggeredState(double[] v,int o){}
        public void evaluateStaggered(double dt,double[] d,int o){}
        public void applyStaggeredState(double[] v,int o,double dt){}
        public void completeStaggeredStep(double dt){accepted++;}
    }
    @Test void legacyParticipantsKeepAllHistory() {
        var model=new Legacy();double[] history={2,5,7};
        assertFalse(model.hasAcceptedDerivativeHistoryInvalidation());
        for(int i=0;i<3;i++)assertFalse(model.isAcceptedDerivativeHistoryInvalidated(i));
        assertEquals(0,model.accepted);
        PsseStaggeredEulerSolver.completeParticipantStep(model,history,.001,.001);
        assertEquals(1,model.accepted);assertArrayEquals(new double[]{2,5,7},history,0);
    }
    @Test void onlySelectedHistoryChangesAfterSuccessfulAcceptance() {
        var model=new Legacy(){
            public boolean hasAcceptedDerivativeHistoryInvalidation(){return accepted>0;}
            public boolean isAcceptedDerivativeHistoryInvalidated(int i){return i==1;}
        };
        double[] history={2,5,7};assertFalse(model.hasAcceptedDerivativeHistoryInvalidation());
        model.evaluateStaggered(.001,new double[3],0);assertFalse(model.hasAcceptedDerivativeHistoryInvalidation());
        PsseStaggeredEulerSolver.completeParticipantStep(model,history,.001,.001);
        assertArrayEquals(new double[]{2,0,7},history,0);
        history[1]=5;PsseStaggeredEulerSolver.completeParticipantStep(model,history,0,.001);
        assertArrayEquals(new double[]{2,5,7},history,0,"Zero-duration observation must not clear prior slopes");
    }
    @Test void failedAcceptanceDoesNotPartiallyClearSolverHistory() {
        var model=new Legacy(){
            public void completeStaggeredStep(double dt){throw new IllegalStateException("rejected accepted-state projection");}
            public boolean hasAcceptedDerivativeHistoryInvalidation(){return true;}
            public boolean isAcceptedDerivativeHistoryInvalidated(int i){return true;}
        };
        double[] history={2,5,7};
        assertThrows(IllegalStateException.class,()->PsseStaggeredEulerSolver.completeParticipantStep(model,history,.001,.001));
        assertArrayEquals(new double[]{2,5,7},history,0);assertEquals(0,model.accepted);
    }
}

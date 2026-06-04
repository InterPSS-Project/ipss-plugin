package org.interpss.plugin.optadj.algo.util;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.SenAnalysisAlgorithm;
import com.interpss.core.common.ReferenceBusException;
import org.interpss.numeric.datatype.Counter;
import org.interpss.numeric.exp.IpssNumericException;

public class AclfNetSensHelper {
   private AclfNetwork aclfNet;

   public AclfNetSensHelper(AclfNetwork aclfNet) {
      this.aclfNet = aclfNet;
   }

   public float[][] calSen() {
      setNetBusBranchNumber(this.aclfNet);
      float[][] senMatrix = new float[this.aclfNet.getNoActiveBus()][this.aclfNet.getNoActiveBranch()];
      SenAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(this.aclfNet);
      dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);
      this.aclfNet.getBusList().parallelStream().filter((bus) -> bus.isActive()).forEach((bus) -> {
         try {
            double[] dblAry = dclfAlgo.getDclfSolver().getSenPAngle(bus.getId());
            this.aclfNet.getBranchList().stream().filter((branch) -> branch.isActive() && branch.getNumber() != 0L).forEach((branch) -> senMatrix[(int)(bus.getNumber() - 1L)][(int)(branch.getNumber() - 1L)] = this.calSen(dblAry, branch));
         } catch (IpssNumericException | ReferenceBusException | InterpssException e) {
            IpssLogger.getLogger().severe(((Exception)e).toString());
         }

      });
      return senMatrix;
   }

   private float calSen(double[] dblAry, AclfBranch branch) {
      double fAng = branch.getFromAclfBus().isRefBus() ? (double)0.0F : dblAry[branch.getFromAclfBus().getSortNumber()];
      double tAng = branch.getToAclfBus().isRefBus() ? (double)0.0F : dblAry[branch.getToAclfBus().getSortNumber()];
      return (float)(-branch.b1ft() * (fAng - tAng));
   }

   private static void setNetBusBranchNumber(AclfNetwork aclfNet) {
      Counter cnt = new Counter();
      aclfNet.getBusList().stream().filter((bus) -> bus.isActive()).forEach((bus) -> {
         cnt.increment();
         bus.setNumber((long)cnt.getCount());
      });
      cnt.reset();
      aclfNet.getBranchList().stream().filter((branch) -> branch.isActive() && branch.getFromBus() != null && branch.getFromBus().isActive() && branch.getToBus() != null && branch.getToBus().isActive()).forEach((branch) -> {
         cnt.increment();
         branch.setNumber((long)cnt.getCount());
      });
   }
}
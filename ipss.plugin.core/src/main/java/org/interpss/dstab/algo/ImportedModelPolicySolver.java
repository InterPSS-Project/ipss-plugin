package org.interpss.dstab.algo;

import java.util.List;
import com.interpss.dstab.algo.dynamic.DynamicSimulationSolverBase.LocalSubstepPreparation;
import org.interpss.dstab.validation.ParameterValidatingSolver;
import org.interpss.fadapter.psse.PsseDStabSolver.LoadModelAssignment;
import org.interpss.fadapter.psse.PsseGovernorBsgProcessor.Entry;
import org.interpss.plugin.dstab.config.LoadModelThresholds;
import org.interpss.dstab.dynLoad.impl.Cmldznu2Model.InitializationScreen;

/** Imported model policies shared by the ordinary and staggered runtimes. */
@SuppressWarnings("deprecation")
public interface ImportedModelPolicySolver extends ParameterValidatingSolver {
    void setGovernorBlockingPolicy(List<Entry> policy);
    List<Entry> getGovernorBlockingReport();
    void setLoadModelThresholds(LoadModelThresholds thresholds);
    LoadModelThresholds getLoadModelThresholds();
    List<LoadModelAssignment> getLoadModelAssignmentReport();
    List<InitializationScreen> getLoadModelScreeningReport();
    List<LocalSubstepPreparation> getLocalSubstepPreparationReport();
}

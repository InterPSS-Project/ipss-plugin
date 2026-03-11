package org.interpss.plugin.contingency.util;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;

import java.util.List;

import org.interpss.plugin.contingency.definition.BranchContingencyRecord;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

/** 
 * A helper class to create DclfBranchOutage list based on the contingency definitions in BranchContingencyRecord list
 * 
 * @author mzhou
 */
public class DclfContingencyHelper {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DclfContingencyHelper.class);

    // Dclf algorithm object used to get the branch objects for creating DclfBranchOutage
    private ContingencyAnalysisAlgorithm dclfAlgo;
    
    /**
     * Constructor
     * 
     * @param algo the Dclf algorithm object used to get the branch objects for creating DclfBranchOutage
     */
    public DclfContingencyHelper(ContingencyAnalysisAlgorithm algo) {
		this.dclfAlgo = algo;
	}
    
     /**
	 * Create a list of DclfBranchOutage based on the contingency definitions in BranchContingencyRecord list
	 * 
	 * @param contRecs the list of BranchContingencyRecord containing the contingency definitions
	 * @return a list of DclfBranchOutage created based on the contingency definitions in BranchContingencyRecord list
	 * @throws InterpssException if there is an error in creating DclfBranchOutage for any of the contingency records
	 */
    public List<DclfBranchOutage> createDclfContList(List<BranchContingencyRecord> contRecs) throws InterpssException {
		List<DclfBranchOutage> dclfContList = new java.util.ArrayList<>();	
		
		for (BranchContingencyRecord record : contRecs) {
			try {
				// Find the branch based on from_bus and to_bus
				String branchId = record.fromBus + "->" + record.toBus+"("+record.ckt+")";
				if (this.dclfAlgo.getAclfNet().getBranch(branchId) != null) {
					DclfBranchOutage dclfCont = createContingency(record.name);
					
					// Determine outage type based on action type
					ContingencyBranchOutageType outageType;
					switch (record.actionType.toLowerCase()) {
						case "open":
							outageType = ContingencyBranchOutageType.OPEN;
							break;
						case "close":
							outageType = ContingencyBranchOutageType.CLOSE;
							break;
						default:
							outageType = ContingencyBranchOutageType.OPEN; // Default to open
					}
					
					DclfOutageBranch outage = createCaOutageBranch(dclfAlgo.getDclfAlgoBranch(branchId), outageType);
					dclfCont.setOutageEquip(outage);
					dclfContList.add(dclfCont);
				}
			} catch (Exception ex) {
				throw new InterpssException("Warning: Could not create contingency for " + record.name + ": " + ex.getMessage() + "\n");
			}
		}
		return dclfContList;	
    }
}

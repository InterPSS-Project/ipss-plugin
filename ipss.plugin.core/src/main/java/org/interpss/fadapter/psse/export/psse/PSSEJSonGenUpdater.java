package org.interpss.fadapter.psse.export.psse;

import java.util.List;
import java.util.Set;

import org.interpss.fadapter.psse.bean.PSSESchema;

import com.interpss.core.aclf.BaseAclfNetwork;

/**
 * PSSE JSon format data updater
 * 
 * @author mzhou
 *
 */
public class PSSEJSonGenUpdater extends BasePSSEJSonUpdater{	
	// the PSSE gen json object
	private PSSESchema.Generator gen;
	
	/**
	 * Constructor
	 * 
	 * @param fieldDef field name definitions
	 * @param aclfNet  the AclfNetwork object
	 */
	public PSSEJSonGenUpdater(PSSESchema.Generator gen, BaseAclfNetwork<?,?> aclfNet) {
		super(gen.getFields(), aclfNet, (lst) -> {
			// Sparse templates can omit the default field; append it before indexing.
            if (!lst.contains("rmpct")) lst.add("rmpct");
            if (!lst.contains("baslod")) lst.add("baslod");
		});
		this.gen = gen;
	}
	
	/**
	 * filter the data based on the given bus id set
	 * 
	 * @param busIdSet  the bus id set to keep
	 */
	public void filter(Set<String> busIdSet) {
		gen.getData().removeIf(data -> {
	 		   @SuppressWarnings("unchecked")
			   List<Object> lst = (List<Object>)data;
	 		   String id = getBusIdFromDataList(lst, "ibus");
	 		   return !busIdSet.contains(id);
	 		});
	}
	
	/**
	 * update the data list based on the AclfNetwork simulation results 
	 */
	public void update() {
        Integer factorColumn = positionTable.get("rmpct");
        int baseLoadColumn = positionTable.get("baslod");
        Integer machineColumn = positionTable.get("machid");
        for (Object record : gen.getData()) {
            @SuppressWarnings("unchecked")
            List<Object> values = (List<Object>) record;
            var bus = aclfNet.getBus(getBusIdFromDataList(values, "ibus"));
            if (bus == null) continue;
            // A plant participation factor applies to every machine on the bus.
            // BASLOD is a per-machine setting, including on offline machines.
            if (factorColumn != null && bus.getPlantMvarControlPFactor() != null) {
                while (values.size() <= factorColumn) values.add(null);
                values.set(factorColumn, bus.getPlantMvarControlPFactor() * 100.0);
            }
            Object machineId = machineColumn != null && machineColumn < values.size()
                    ? values.get(machineColumn) : null;
            var machine = bus.getContributeGen(machineId == null ? "1" : machineId.toString().trim());
            if (machine != null) {
                while (values.size() <= baseLoadColumn) values.add(null);
                values.set(baseLoadColumn, machine.getBaseLoadFlag());
            }
        }
		/*
        "fields":["ibus", "machid", "pg", "qg", "qt", "qb", "vs", "ireg", "nreg", "mbase", 
        			"zr", "zx", "rt", "xt", "gtap", "stat", "rmpct", "pt", "pb", "baslod", 
        			"o1", "f1", "o2", "f2", "o3", "f3", "o4", "f4", "wmod", "wpf", "droopname", "name"], 
        "data":  [1, "1", 71.64000, 27.05000, 9999.000, -9999.000, 1.040000, 1, 0, 100.0000,
         			0.000000, 0.4000000E-01, 0.000000, 0.000000, 1.000000, 1, 100.0000, 9999.000, 
         			-9999.000, 0, 1, 1.000000, null, null, null, null, null, null, null, null, null, null], 
		 */
	}
}
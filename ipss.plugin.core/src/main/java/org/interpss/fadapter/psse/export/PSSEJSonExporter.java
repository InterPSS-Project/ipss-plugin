package org.interpss.fadapter.psse.export;

import java.util.Set;

import org.ieee.odm.adapter.psse.bean.PSSESchema;
import org.interpss.fadapter.psse.export.psse.PSSEJSonAclineUpdater;
import org.interpss.fadapter.psse.export.psse.PSSEJSonBusUpdater;
import org.interpss.fadapter.psse.export.psse.PSSEJSonDc2TLCCUpdater;
import org.interpss.fadapter.psse.export.psse.PSSEJSonDc2TVSCUpdater;
import org.interpss.fadapter.psse.export.psse.PSSEJSonFactsDeviceUpdater;
import org.interpss.fadapter.psse.export.psse.PSSEJSonFixedShuntUpdater;
import org.interpss.fadapter.psse.export.psse.PSSEJSonGenUpdater;
import org.interpss.fadapter.psse.export.psse.PSSEJSonLoadUpdater;
import org.interpss.fadapter.psse.export.psse.PSSEJSonSwitchedShuntUpdater;
import org.interpss.fadapter.psse.export.psse.PSSEJSonSwitchingDeviceUpdater;
import org.interpss.fadapter.psse.export.psse.PSSEJSonXformerUpdater;
import org.interpss.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.aclf.BaseAclfNetwork;

/**
 * PSSE JSon format data exporter
 * 
 * @author mzhou
 *
 */
public class PSSEJSonExporter {	
	private static final Logger log = LoggerFactory.getLogger(PSSEJSonExporter.class);
	
	// the AclfNetwork object
	protected BaseAclfNetwork<?,?> aclfNet;
	
	// the PSSE json object
	private PSSESchema psseJson;
	
	/**
	 * Constructor
	 * 
	 * @param aclfNet  the AclfNetwork object
	 * @param psseJson the PSSE json object
	 */
	public PSSEJSonExporter(BaseAclfNetwork<?,?> aclfNet, PSSESchema psseJson) {
		this.aclfNet = aclfNet;
		this.psseJson = psseJson;
	}
	
	/**
	 * filter the json data based on the given bus id set and update the data based on the AclfNetwork Loadflow results;
	 * 
	 * @param busIdSet the bus id set to keep
	 */
	public void filterAndUpdate(Set<String> busIdSet) {
		// update the Bus json data based on the busIdSet
		PSSEJSonBusUpdater busUpdater = new PSSEJSonBusUpdater(psseJson.getNetwork().getBus(), aclfNet); 
		busUpdater.filter(busIdSet);
		busUpdater.update();
		log.debug("Bus Data: " + psseJson.getNetwork().getBus().getData());
		
		// update the Gen json data based on the busIdSet
		PSSEJSonGenUpdater genUpdater = new PSSEJSonGenUpdater(psseJson.getNetwork().getGenerator(), aclfNet); 
		genUpdater.filter(busIdSet);
		genUpdater.update();
		log.debug("Gen Data: " + psseJson.getNetwork().getGenerator().getData());
		
		// update the Load json data based on the busIdSet
		PSSEJSonLoadUpdater loadUpdater = new PSSEJSonLoadUpdater(psseJson.getNetwork().getLoad(), aclfNet); 
		loadUpdater.filter(busIdSet);
		loadUpdater.update();
		log.debug("Load Data: " + psseJson.getNetwork().getLoad().getData());
		
		// update the Switched shunt json data based on the busIdSet
		PSSEJSonSwitchedShuntUpdater swshuntUpdater = new PSSEJSonSwitchedShuntUpdater(psseJson.getNetwork().getSwshunt(), aclfNet); 
		swshuntUpdater.filter(busIdSet);
		swshuntUpdater.update();
		log.debug("Switched Shunt Data: " + psseJson.getNetwork().getLoad().getData());
				
		// update the Fixed shunt json data based on the busIdSet
		PSSEJSonFixedShuntUpdater fShuntUpdater = new PSSEJSonFixedShuntUpdater(psseJson.getNetwork().getFixshunt(), aclfNet); 
		fShuntUpdater.filter(busIdSet);
		fShuntUpdater.update();
		log.debug("Fixed shunt Data: " + psseJson.getNetwork().getLoad().getData());
				
		// update the Facts json data based on the busIdSet
		PSSEJSonFactsDeviceUpdater factsUpdater = new PSSEJSonFactsDeviceUpdater(psseJson.getNetwork().getFacts(), aclfNet); 
		factsUpdater.filter(busIdSet);
		factsUpdater.update();
		log.debug("Facts Data: " + psseJson.getNetwork().getLoad().getData());
				
		// update the Acline json data based on the busIdSet
		PSSEJSonAclineUpdater aclineUpdater = new PSSEJSonAclineUpdater(psseJson.getNetwork().getAcline(), aclfNet); 
		aclineUpdater.filter(busIdSet);
		aclineUpdater.update();
		log.debug("Acline Data: " + psseJson.getNetwork().getAcline().getData());
	  	
		// update the Transformer json data based on the busIdSet
		PSSEJSonXformerUpdater xfrUpdater = new PSSEJSonXformerUpdater(psseJson.getNetwork().getTransformer(), aclfNet); 
		xfrUpdater.filter(busIdSet);
		xfrUpdater.update();
		log.debug("Xfr Data: " + psseJson.getNetwork().getTransformer().getData());
		
		// update the Switching device json data based on the busIdSet
		PSSEJSonSwitchingDeviceUpdater switchingUpdater = new PSSEJSonSwitchingDeviceUpdater(psseJson.getNetwork().getSysswd(), aclfNet); 
		switchingUpdater.filter(busIdSet);
		switchingUpdater.update();
		log.debug("Switching Device Data: " + psseJson.getNetwork().getTransformer().getData());
		
		PSSEJSonDc2TLCCUpdater dc2tLccUpdater = new PSSEJSonDc2TLCCUpdater(psseJson.getNetwork().getTwotermdc(), aclfNet);
		dc2tLccUpdater.filter(busIdSet);
		dc2tLccUpdater.update();
		log.debug("DC 2T LCC Data: " + psseJson.getNetwork().getTwotermdc().getData());
		
		PSSEJSonDc2TVSCUpdater dc2tVscUpdater = new PSSEJSonDc2TVSCUpdater(psseJson.getNetwork().getVscdc(), aclfNet);
		dc2tVscUpdater.filter(busIdSet);
		dc2tVscUpdater.update();
		log.debug("DC 2T VSC Data: " + psseJson.getNetwork().getVscdc().getData());
	}
	
	/**
	 * export to the specified file
	 * 
	 * @param filename the output filename
	 */
	public void export(String filename) {
		// write the json data to the output file
		FileUtil.writeText2File(filename, psseJson.toString());
	}
}
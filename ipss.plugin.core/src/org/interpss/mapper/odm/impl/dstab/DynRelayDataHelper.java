package org.interpss.mapper.odm.impl.dstab;

import org.ieee.odm.common.ODMLogger;
import org.ieee.odm.schema.DStabGenDataXmlType;
import org.ieee.odm.schema.DStabLoadDataXmlType;
import org.ieee.odm.schema.DynamicLoadCMPLDWXmlType;
import org.ieee.odm.schema.DynamicLoadSinglePhaseACMotorXmlType;
import org.ieee.odm.schema.GenRelayFRQTPATXmlType;
import org.ieee.odm.schema.GenRelayVTGTPATXmlType;
import org.ieee.odm.schema.GenRelayXmlType;
import org.ieee.odm.schema.LDS3RelayXmlType;
import org.ieee.odm.schema.LVS3RelayXmlType;
import org.ieee.odm.schema.LoadCharacteristicTypeEnumType;
import org.ieee.odm.schema.LoadRelayXmlType;
import org.interpss.dstab.dynLoad.DynLoadCMPLDW;
import org.interpss.dstab.dynLoad.LD1PAC;
import org.interpss.dstab.dynLoad.impl.DynLoadCMPLDWImpl;
import org.interpss.dstab.dynLoad.impl.LD1PACImpl;
import org.interpss.dstab.relay.impl.BusLoadRelayModel;
import org.interpss.dstab.relay.impl.GenUnderOverFreqTripRelayModel;
import org.interpss.dstab.relay.impl.GenUnderOverVoltTripRelayModel;
import org.interpss.dstab.relay.impl.LoadUFShedRelayModel;
import org.interpss.dstab.relay.impl.LoadUVShedRelayModel;
import org.interpss.numeric.datatype.Triplet;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.device.DynamicBusDevice;

public class DynRelayDataHelper {
	
	private BaseDStabBus<?, ?> bus = null;
	private DStabilityNetwork dynNet = null;
	
	public DynRelayDataHelper() {
	
	}
	
	
	public DynRelayDataHelper(DStabilityNetwork dstabNet) {
		this.dynNet = dstabNet;
		
	}
	
	public void createDynGenRelayModel(DStabGenDataXmlType dynGen, BaseDStabBus<?,?> dstabBus,String genId) {
		
		if(dynGen.getGenRelayList()!=null) {
			for(GenRelayXmlType genRelayXml:dynGen.getGenRelayList()) {
				if(genRelayXml instanceof GenRelayFRQTPATXmlType) {
					GenRelayFRQTPATXmlType frqRelyXml = (GenRelayFRQTPATXmlType) genRelayXml;
					
					//if(dstabBus.getDynamicBusDeviceList())
					GenUnderOverFreqTripRelayModel genRelay = new GenUnderOverFreqTripRelayModel(dstabBus,genId,genRelayXml.getMonitorBusId());
					
				    Triplet ftf1 = new Triplet(frqRelyXml.getFL(), frqRelyXml.getTp(),1);
				    genRelay.getRelaySetPoints().add(ftf1);
				    genRelay.setBreakerTime(frqRelyXml.getTb());
				    genRelay.getUnderOverFlagList().add(0); // under-freq
				    
				    Triplet ftf2 = new Triplet(frqRelyXml.getFU(), frqRelyXml.getTp(),1);
				    genRelay.getRelaySetPoints().add(ftf2);
				    //genRelay.setBreakerTime(frqRelyXml.getTb());
				    genRelay.getUnderOverFlagList().add(1); // over-freq
				    
				    
				}
				else if(genRelayXml instanceof GenRelayVTGTPATXmlType) {
					GenRelayVTGTPATXmlType voltRelyXml = (GenRelayVTGTPATXmlType) genRelayXml;
					
					GenUnderOverVoltTripRelayModel genRelay = new GenUnderOverVoltTripRelayModel(dstabBus,genId,genRelayXml.getMonitorBusId());
					
					Triplet vtf1 = new Triplet(voltRelyXml.getVL(), voltRelyXml.getTp(),1);
				    genRelay.getRelaySetPoints().add(vtf1);
				    genRelay.setBreakerTime(voltRelyXml.getTb());
				    genRelay.getUnderOverFlagList().add(0); // under-volt
				    
				    Triplet vtf2 = new Triplet(voltRelyXml.getVU(), voltRelyXml.getTp(),1);
				    genRelay.getRelaySetPoints().add(vtf2);
				    //genRelay.setBreakerTime(frqRelyXml.getTb());
				    genRelay.getUnderOverFlagList().add(1); // over-volt
				}
				
			
			}
		}
		
		
	}
	

	public void createDynLoadRelayModel(DStabLoadDataXmlType dynLoad, BaseDStabBus<?,?> dstabBus,String loadId) {
		BusLoadRelayModel loadRelay = null;
		
		if(dynLoad!=null) {
		
		   if(dynLoad.getLoadRelayList()!=null)	     
		     for(LoadRelayXmlType ldRelayXml:dynLoad.getLoadRelayList()) {
		    	 if(ldRelayXml.getLoadID().equals(loadId)) {
		    		 if(ldRelayXml instanceof LDS3RelayXmlType) {
		    		    loadRelay = new LoadUFShedRelayModel(dstabBus, loadId);
		    		    LDS3RelayXmlType lds3 = (LDS3RelayXmlType) ldRelayXml;
		    		    
		    		
		    		    if(lds3.getF1()>0 && lds3.getFrac1()>0) {
		    		    	//TODO need to neglect the Tb setting for now
		    			    Triplet vtf = new Triplet(lds3.getF1(), lds3.getT1(),lds3.getFrac1());
		    			    loadRelay.getRelaySetPoints().add(vtf);
		    			    loadRelay.setBreakerTime(lds3.getTb1());
		    		    }
					    if(lds3.getF2()>0 && lds3.getFrac2()>0) {
					    	
		    			    Triplet vtf = new Triplet(lds3.getF2(), lds3.getT2(),lds3.getFrac2());
		    			    loadRelay.getRelaySetPoints().add(vtf);    		    	
					    }
						if(lds3.getF3()>0 && lds3.getFrac3()>0) {
						    Triplet vtf = new Triplet(lds3.getF3(), lds3.getT3(),lds3.getFrac3());
		    			    loadRelay.getRelaySetPoints().add(vtf);  
		    		    }
						if(lds3.getF4()>0 && lds3.getFrac4()>0) {
							  Triplet vtf = new Triplet(lds3.getF4(), lds3.getT4(),lds3.getFrac4());
			    			  loadRelay.getRelaySetPoints().add(vtf);  
		    		    }
						if(lds3.getF5()>0 && lds3.getFrac5()>0) {
							  Triplet vtf = new Triplet(lds3.getF5(), lds3.getT5(),lds3.getFrac5());
			    			  loadRelay.getRelaySetPoints().add(vtf);
		    		    }
		    		 }
		    		 else if(ldRelayXml instanceof LVS3RelayXmlType) {
			    		    loadRelay = new LoadUVShedRelayModel(dstabBus, loadId);
			    		    LVS3RelayXmlType lvs3 = (LVS3RelayXmlType) ldRelayXml;
			    		    
			    		
			    		    if(lvs3.getF1()>0 && lvs3.getFrac1()>0) {
			    		    	//TODO need to neglect the Tb setting for now
			    			    Triplet vtf = new Triplet(lvs3.getF1(), lvs3.getT1(),lvs3.getFrac1());
			    			    loadRelay.getRelaySetPoints().add(vtf);
			    			    loadRelay.setBreakerTime(lvs3.getTb1());
			    		    }
						    if(lvs3.getF2()>0 && lvs3.getFrac2()>0) {
						    	//TODO need to neglect the Tb setting for now
			    			    Triplet vtf = new Triplet(lvs3.getF2(), lvs3.getT2(),lvs3.getFrac2());
			    			    loadRelay.getRelaySetPoints().add(vtf);    		    	
						    }
							if(lvs3.getF3()>0 && lvs3.getFrac3()>0) {
							    Triplet vtf = new Triplet(lvs3.getF3(), lvs3.getT3(),lvs3.getFrac3());
			    			    loadRelay.getRelaySetPoints().add(vtf);  
			    		    }
							if(lvs3.getF4()>0 && lvs3.getFrac4()>0) {
								  Triplet vtf = new Triplet(lvs3.getF4(), lvs3.getT4(),lvs3.getFrac4());
				    			  loadRelay.getRelaySetPoints().add(vtf);  
			    		    }
							if(lvs3.getF5()>0 && lvs3.getFrac5()>0) {
								  Triplet vtf = new Triplet(lvs3.getF5(), lvs3.getT5(),lvs3.getFrac5());
				    			  loadRelay.getRelaySetPoints().add(vtf);
			    		    }
							
							//TODO have to neglect the Ttb setting for now (transfer tripping is not implemented yet)
			    		 }
		    		 else {
		    			 ODMLogger.getLogger().severe("LoadRelayXmlType other than LDS3 and LVS3 is not supportted yet!" );
		    		 }
		         }
		     
		     }
			
		}
		

	}

}

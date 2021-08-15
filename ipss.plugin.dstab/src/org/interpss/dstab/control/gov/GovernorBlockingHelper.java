package org.interpss.dstab.control.gov;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.common.visitor.INetVisitor;
import com.interpss.core.net.Bus;
import com.interpss.core.net.Network;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabLoad;
import com.interpss.dstab.mach.Machine;

public class GovernorBlockingHelper implements INetVisitor{
	
	class Record{
		public String machId ="";
		public String busId ="";
		public double baseKV = 0;
		public String busName = "";
		public int status =0; //“0” means that unit responses to governor control and could be moved up and down; “1” means that unit responses to governor control and could be moved down only; “2” means that unit does not respond to governor control.
		
		public Record(String busId, String busName, String machId, double baseKV, int status) {
			this.busId = busId;
			this.busName = busName;
			this.machId = machId;
			this.baseKV = baseKV;
			this.status = status;
		}
		
		public String toString() {
			return this.machId+"@"+this.busId;
		}
	}
	
	private BaseDStabNetwork dsNet = null;
	private List<Record> govBlkRecList = null;
	private Hashtable<String, Record> govBlkRecTable = null;

	public GovernorBlockingHelper(String bsgFile){

		govBlkRecList = new ArrayList<>();
		govBlkRecTable = new Hashtable<>();
		try {
			parseBsgFile(bsgFile);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}
	
	@Override
	public void visit(Network net) {
		for(Record r:govBlkRecList) {
			if(r.status==2) {
				if(net.getBus(r.busId)!=null) {
					BaseDStabBus b = (BaseDStabBus) net.getBus(r.busId);
					Machine m = b.getMachine(r.machId);
					if(m!=null) {
						if(m.hasGovernor()) {
							m.getGovernor().setStatus(false);
						}
					}
				}
			}
		}
		
	}
	
	public void blockRemoveGov(BaseDStabNetwork<? extends BaseDStabBus, ? extends DStabBranch> net) {
		
		for(Bus bus: net.getBusList()) {
			BaseDStabBus<?,DStabLoad> dsBus = (BaseDStabBus) bus;
			if(dsBus.isGen()) {
				if(dsBus.getContributeGenList()!=null) {
					for(AclfGen gen: dsBus.getContributeGenList()){
						DStabGen dsGen = (DStabGen) gen;
					
						if(dsGen.isActive() && dsGen.getDynamicGenDevice() !=null){
							String idx = dsGen.getId()+"@"+dsBus.getId();
							boolean removeGov = false;
							if(govBlkRecTable.containsKey(idx) && govBlkRecTable.get(idx).status==2) {
								removeGov = true;
								//System.out.println("block gov:"+idx);
							}
							else if(!govBlkRecTable.containsKey(idx)) {
								removeGov = true;
								//System.out.println("remove gov:"+idx);
							}
							
							if(removeGov) {
							    Machine m = dsGen.getMach();
								if(m!=null) {
									if(m.hasGovernor()) {
										m.getGovernor().setStatus(false);
									}
								}
							}
					  }
				}
			}
			
		  }
		}
	}
	
	//dsNet object
	
	
	//read in the bsg file to get the governor blocking records, and turn each governor in the records off
	
	private void parseBsgFile(String fileName) throws IOException {
		final File file = new File(fileName);
		final InputStream stream = new FileInputStream(file);
		//ODMLogger.getLogger().info("Parse input file and create the parser object, " + fileName);
		final BufferedReader din = new BufferedReader(new InputStreamReader(stream));
		
		String lineStr = null;
  		int lineNo = 0;
  		
  		do {
  			lineStr = din.readLine();
  			if (lineStr != null) {
  				lineNo++;
  				if(lineStr.startsWith("//")) { // it is a comment line
  					continue;
  				}
  				else if(lineStr.trim().length()>0) {
  					String[] ary= lineStr.trim().split(",");
  					String busId = "Bus"+ary[0].trim();
  					String busName = ary[1].trim();
  					busName = busName.substring(1, busName.length()-1).trim();
  					double busKV = Double.valueOf(ary[2]);
  					String machId = ary[3].trim();
  					machId = machId.substring(1,machId.length()-1).trim();
  					int flag = Integer.valueOf(ary[4].trim());
  					Record r = new Record(busId,busName,machId,busKV, flag);
  					govBlkRecList.add(r);
  					String idx = machId+"@"+busId;
  					govBlkRecTable.put(idx, r);
  				}
  			}
  		} while (lineStr != null);
  		
		din.close();
		stream.close();
	}

}

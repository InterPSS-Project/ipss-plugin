package org.interpss.QA.topology;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.TextArea;
import java.awt.TextComponent;
import java.awt.event.KeyEvent;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JFrame;

import org.eclipse.emf.ecore.change.util.ChangeRecorder;
import org.interpss.algo.TopologyProcesor;
import org.interpss.algo.ZeroZBranchProcesor;
import org.interpss.algo.ZeroZBranchProcesor.BusBasedSeaerchResult;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.Contingency;
import com.interpss.core.aclf.contingency.OutageBranch;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.mxgraph.layout.mxFastOrganicLayout;
import com.mxgraph.layout.mxIGraphLayout;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxStylesheet;

public class TopologyHelper {
	private static final String defaultBranchStyle = "defaultEdge";
	private static final String outBranchStyle = "defaultEdge;strokeColor=red";
	private static final String islandBusStyle = "defaultVertex;fillColor=white";
	private static final String defaultVertexStyle = "defaultVertex";

	private static final String processingBusStyle = "defaultVertex;fillColor=red";
	private static final String parentBusStyle = "defaultVertex;fillColor=orange";
	private static final String childBusStyle = "defaultVertex;fillColor=green";
	
	private static final String protectedBranchStyle = "defaultEdge;strokeColor=black";

	Hashtable<String, Object> addedBusTable = new Hashtable<String, Object>();
	AclfNetwork net = null;
	Topology top = null;
	ZeroZBranchProcesor proc = null;
	List<String> protectedBranches = new ArrayList<String>();
	double x = 0, y = 0;

	enum branchType {
		Line, Transformer, Breaker, ZBR
	};

	String sourceBusId = "";
	boolean displayName = false;
	boolean displayBaseVolt = false;
	double voltageLevel = 1.0;

	public TopologyHelper(AclfNetwork net) throws InterpssException {
		this.net = net;
		this.top = new Topology();
	}

	public TopologyHelper(AclfNetwork net,ZeroZBranchProcesor proc) throws InterpssException{
		this(net);
		this.proc = proc;		
	}
	
	public TopologyHelper(AclfNetwork net,ZeroZBranchProcesor proc, List<Contingency> contList) throws InterpssException{
		this(net, proc);
		for(Contingency cont: contList){
			for(OutageBranch outBranch : cont.getOutageBranches())
				this.protectedBranches.add(outBranch.getBranch().getId());					
		}
				
	}
	
	public void setZeroZBranchProcesor(ZeroZBranchProcesor proc){		
		this.proc = proc;
	}
	public void setDisplayName(boolean displayBusName) {
		this.displayName = displayBusName;
	}

	public void setdisplayBaseVolt(boolean displayBaseVolt) {
		this.displayBaseVolt = displayBaseVolt;
	}

	public void setVoltageLevel(double newVoltLevel) {
		this.voltageLevel = newVoltLevel;
	}

	/**
	 * Requirement - use Java Swing Tabbed Panes to display three panes: Pane-1
	 * branchList + outageBranchList Pane-2 branchList + equivOutageBranchList
	 * Pane-3 text only
	 * 
	 * NOTE use multiple JPanels and a CardLayout to swap them out //CAD
	 * http://docs.oracle.com/javase/tutorial/uiswing/layout/card.html //TAB
	 * http://docs.oracle.com/javase/tutorial/uiswing/components/tabbedpane.html
	 * 
	 * @param branchList
	 * @param text
	 * @param outageBranchList
	 * @param equivOutageBranchList
	 * @param islandBusList
	 * @return
	 * @throws InterpssException
	 */
	public boolean plotMultiTopology(List<String> branchList, String text,
			List<String> outageBranchList, List<String> equivOutageBranchList,
			List<String> islandBusList) throws InterpssException {
		// for storing java component to create a multiTopology instance
		Hashtable<String, Component> compTable = new Hashtable<String, Component>();
		//
		// basecase outage branch list
		addedBusTable.clear();

		top.getTopGraph().getModel().beginUpdate();
		try {

			setBranchToGraph(branchList, outageBranchList, islandBusList);

		} finally {
			top.getTopGraph().getModel().endUpdate();
		}
		compTable.put("Original Outage", top.getGraphComponet());

		//
		// equivalent outage branch list
		addedBusTable.clear();
		top = new Topology();
		top.getTopGraph().getModel().beginUpdate();
		try {

			setBranchToGraph(branchList, equivOutageBranchList, islandBusList);

		} finally {
			top.getTopGraph().getModel().endUpdate();
		}

		compTable.put("Equivalent Outage", top.getGraphComponet());

		//
		// text
		TextComponent textComp = new TextArea(text);
		compTable.put("CA Info", textComp);

		MultiTopolgy mTop = new MultiTopolgy(compTable);

		createAndShowGUI(mTop, "Topology Plotting Tool--InterPSS");

		return true;

	}

	public boolean plotTopology(List<String> branchList,
			List<String> outageBranchList) {
		addedBusTable.clear();

		top.getTopGraph().getModel().beginUpdate();
		try {

			setBranchToGraph(branchList, outageBranchList);

		} finally {
			top.getTopGraph().getModel().endUpdate();
		}
		// initialize the topology and plot the JFrame
		top.initialize();
		return true;

	}

	public boolean plotBusBasedNTP(String processingBus)
			throws Exception {
		// for storing java component to create a multiTopology instance
		Hashtable<String, Component> compTable = new Hashtable<String, Component>();
		//
		// basecase outage branch list
		addedBusTable.clear();

		top.getTopGraph().getModel().beginUpdate();
		String text= "";
		try {

			text = setBranchToGraph(processingBus);

		} finally {
			top.getTopGraph().getModel().endUpdate();
		}
		compTable.put("Bus consolidation", top.getGraphComponet());

		//
		// text
		TextComponent textComp = new TextArea(text);
		compTable.put("Bus Consolidation Info", textComp);

		MultiTopolgy mTop = new MultiTopolgy(compTable);

		createAndShowGUI(mTop, "Topology Plotting Tool--InterPSS");

		return true;

	}
	

	private String setBranchToGraph(String processingBus) throws Exception {
		
		// get all the branches within the same substation as the processingBus
		AclfBus bus = net.getBus(processingBus);
		String branchId = "";
		for( Branch branch: bus.getBranchList()){
			AclfBranch b = (AclfBranch) branch;
			if(b.isActive() && b.getBranchCode().equals(AclfBranchCode.BREAKER)){
				branchId = b.getId();
				break;
			}
		}
		if(branchId.equals(""))
			throw new Exception("There is no breaker in the slelect substation.");
		
		
		TopologyProcesor tp = new TopologyProcesor(net);		
		List<String> branchList = tp.findBranchInSubStation(branchId);
		
		ChangeRecorder recorder = net.bookmark(false);
		
		net.accept(proc); 	
		
		BusBasedSeaerchResult result = proc.getBusBasedSearchResult(processingBus);
		String parentBus = result.getParentBusId();
		List<String> childBusList = result.getChildBusList();	
		
		net.rollback(recorder);
		
		String type ="Line";
		String cirId="";
		//boolean outage =false;
		for(String id: branchList){
			Branch bra = this.net.getBranch(id);
			cirId=bra.getCircuitNumber();
		    boolean protectedBranch =this.protectedBranches.contains(id)?true:false;
			
			AclfBranch acBra=(AclfBranch) bra;
			type = getBranchType(acBra);
			double r = this.roundSixDecimals(acBra.getZ().abs());
			
			//considering consolidation
			AclfBus fromBus = (AclfBus) (bra.getFromBus().isActive()?bra.getFromBus():
                bra.getFromBus().getParent());
			String fromBusId = fromBus.getId();
			String fromBusStyle= this.setBusStyle(fromBusId, processingBus, parentBus, childBusList);	
			
			String genCode = "";
			if(fromBus.isGen())
				genCode = "[G]";
			else if(fromBus.isLoad())
				genCode = "[L]";
							
			fromBusId = fromBusId+genCode;
			
			AclfBus toBus = (AclfBus) (bra.getToBus().isActive()?bra.getToBus():
	               bra.getToBus().getParent());
			String toBusId = toBus.getId();
			String toBusStyle=this.setBusStyle(toBusId, processingBus, parentBus, childBusList);
			
			genCode = "";
			if(toBus.isGen()){
				String pgen = String.valueOf(roundTwoDecimals(toBus.getGenP()));
				genCode = "[G]_"+ pgen;
			}
			else if(toBus.isLoad()){
				String pload = String.valueOf(roundTwoDecimals(toBus.getLoadP()));
				genCode = "[L]_"+pload;
			}
							
			toBusId = toBusId+genCode;

			//toBusId = toBusId +"["+ bra.getToBus().getZone().getId()+"]";
			if(!addedBusTable.containsKey(fromBusId)){
				double x = Math.random();
				double y = Math.random();
			    Object Bus = top.getTopGraph().insertVertex(
						         top.getTopGraph().getDefaultParent(), null,
						         fromBusId, x, y, 80, 40,fromBusStyle);
				
			    addedBusTable.put(fromBusId, Bus);
				
			}
			
			if(!addedBusTable.containsKey(toBusId)){
				double x = Math.random();
				double y = Math.random();
			    Object Bus = top.getTopGraph().insertVertex(
						         top.getTopGraph().getDefaultParent(), null,
						         toBusId, x, y, 80, 40,toBusStyle);
				
			    addedBusTable.put(toBusId, Bus);			    
				
			}		
			
			
			top.getTopGraph().insertEdge(top.getTopGraph().getDefaultParent(), 
					"", type+"("+cirId+")" + "-"+ r, addedBusTable.get(fromBusId), addedBusTable.get(toBusId),
					protectedBranch? protectedBranchStyle: defaultBranchStyle);			
			
		}
		return result.toString();
	}
	
	private double roundTwoDecimals(double d){
		DecimalFormat twoDForm = new DecimalFormat("#.##");
		return Double.valueOf(twoDForm.format(d));		
	}
	
	private double roundSixDecimals(double d){
		DecimalFormat twoDForm = new DecimalFormat("#.######");
		return Double.valueOf(twoDForm.format(d));		
	}
	
	private String setBusStyle(String busId, String processingBus,String parentBus,List<String> childBusList){
		String busStyle= "";
		if(busId.equals(processingBus))
			busStyle= TopologyHelper.processingBusStyle;
		else if(busId.equals(parentBus))
			busStyle = TopologyHelper.parentBusStyle;
		else if(childBusList.contains(busId))
			busStyle = TopologyHelper.childBusStyle;
		else
			busStyle = TopologyHelper.defaultVertexStyle;
		
		return busStyle;
	}

	private String setBranchToGraph(List<String> branchList,
			List<String> outbranchList) {
		String type = "Line";
		String cirId = "";
		boolean outage = false;
		for (String id : branchList) {
			Branch bra = this.net.getBranch(id);
			cirId = bra.getCircuitNumber();
			outage = outbranchList.contains(id) ? true : false;

			AclfBranch acBra = (AclfBranch) bra;
			type = getBranchType(acBra);
			/*
			 * Branch: Bus9689->Bus9702(1) --> Bus9687->Bus9695(1)
			 * bra.getFromBusId() returns Bus9689However bra.getFromBus returns
			 * Bus9687
			 * 
			 * This is due to the fact that the fromBusId is connected to the
			 * fromBus public String getFromBusId() { return
			 * NetUtilFunc.findFromID(this.getId()); }
			 * 
			 * while consolidation only set the bus 'parent-child' relationship,
			 * does not change the branch id
			 */

			// considering consolidation
			String fromBusId = bra.getFromBus().isActive() ? bra.getFromBus()
					.getId() : bra.getFromBus().getParent().getId();
			// fromBusId =
			// fromBusId+"["+bra.getFromBus().getZone().getNumber()+"]";
			String toBusId = bra.getToBus().isActive() ? bra.getToBus().getId()
					: bra.getToBus().getParent().getId();
			// toBusId = toBusId +"["+ bra.getToBus().getZone().getId()+"]";
			if (!addedBusTable.containsKey(fromBusId)) {
				double x = Math.random();
				double y = Math.random();
				Object Bus = top.getTopGraph().insertVertex(
						top.getTopGraph().getDefaultParent(), null, fromBusId,
						x, y, 80, 40);

				addedBusTable.put(fromBusId, Bus);

			}

			if (!addedBusTable.containsKey(toBusId)) {
				double x = Math.random();
				double y = Math.random();
				Object Bus = top.getTopGraph().insertVertex(
						top.getTopGraph().getDefaultParent(), null, toBusId, x,
						y, 80, 40);

				addedBusTable.put(toBusId, Bus);

			}

			top.getTopGraph().insertEdge(top.getTopGraph().getDefaultParent(),
					"", type + "(" + cirId + ")", addedBusTable.get(fromBusId),
					addedBusTable.get(toBusId),
					outage ? outBranchStyle : defaultBranchStyle);
		}
		return type;
	}

	private String setBranchToGraph(List<String> branchList,
			List<String> outbranchList, List<String> islandBusList) {
		String type = "Line";
		String cirId = "";
		boolean outage = false;
		for (String id : branchList) {
			Branch bra = this.net.getBranch(id);
			cirId = bra.getCircuitNumber();
			outage = outbranchList.contains(id) ? true : false;

			AclfBranch acBra = (AclfBranch) bra;
			type = getBranchType(acBra);

			// considering consolidation
			String fromBusId = bra.getFromBus().isActive() ? bra.getFromBus()
					.getId() : bra.getFromBus().getParent().getId();
			String fromBusStyle = islandBusList.contains(fromBusId) ? islandBusStyle
					: defaultVertexStyle;

			// fromBusId =
			// fromBusId+"["+bra.getFromBus().getZone().getNumber()+"]";

			String toBusId = bra.getToBus().isActive() ? bra.getToBus().getId()
					: bra.getToBus().getParent().getId();
			String toBusStyle = islandBusList.contains(toBusId) ? islandBusStyle
					: defaultVertexStyle;

			// toBusId = toBusId +"["+ bra.getToBus().getZone().getId()+"]";
			if (!addedBusTable.containsKey(fromBusId)) {
				double x = Math.random();
				double y = Math.random();
				Object Bus = top.getTopGraph().insertVertex(
						top.getTopGraph().getDefaultParent(), null, fromBusId,
						x, y, 80, 40, fromBusStyle);

				addedBusTable.put(fromBusId, Bus);

			}

			if (!addedBusTable.containsKey(toBusId)) {
				double x = Math.random();
				double y = Math.random();
				Object Bus = top.getTopGraph().insertVertex(
						top.getTopGraph().getDefaultParent(), null, toBusId, x,
						y, 80, 40, toBusStyle);

				addedBusTable.put(toBusId, Bus);

			}

			top.getTopGraph().insertEdge(top.getTopGraph().getDefaultParent(),
					"", type + "(" + cirId + ")", addedBusTable.get(fromBusId),
					addedBusTable.get(toBusId),
					outage ? outBranchStyle : "defaultEdge");
		}
		return type;
	}

	private String getBranchType(AclfBranch acBra) {
		String type = "Line";
		if (acBra.getBranchCode() != null) {
			type = acBra.getBranchCode() == AclfBranchCode.BREAKER ? "Breaker"
					: acBra.getBranchCode() == AclfBranchCode.XFORMER ? "Transformer"
							: acBra.getBranchCode() == AclfBranchCode.PS_XFORMER ? "PSXfr"
									: acBra.getBranchCode() == AclfBranchCode.ZBR ? "ZBR"
											: "Line";
		}
		return type;
	}

	public boolean getToplogyByLength(String startBusId, int length) {
		sourceBusId = startBusId;
		mxGraph mg = top.getTopGraph();
		Bus sourceBus = net.getBus(startBusId);
		// check the input bus id first
		if (this.net.getBus(startBusId) == null) {
			IpssLogger.getLogger().severe(
					"The input starting busId # " + startBusId
							+ "  can not be found in the network!");

			return false;
		}

		// forming the Topology

		// using the begin(end) update to keep track of the topology change.

		mg.getModel().beginUpdate();
		try {
			x = Math.random();
			y = Math.random();
			Object Bus = top.getTopGraph().insertVertex(
					top.getTopGraph().getDefaultParent(), null,
					getDisplayString(startBusId), x, y, 80, 40);

			addedBusTable.put(startBusId, Bus);

			// use Depth first search method

			DFS(startBusId, 0, length);

			// the forward direction
			// DFS(startBusId,0, length,true);

			// the backward direction
			// DFS(startBusId,0, length,false);

		} finally {
			mg.getModel().endUpdate();
		}
		// initialize the topology and plot the JFrame
		top.initialize();
		return true;
	}

	private String getDisplayString(String busId) {
		String str = displayName ? this.net.getBus(busId).getName() : busId;
		str += displayBaseVolt ? " [" + this.net.getBus(busId).getBaseVoltage()
				+ "]" : "";
		return str;
	}

	public boolean getToplogyByArea(String startBusId, double voltageLevel) {
		sourceBusId = startBusId;
		mxGraph mg = top.getTopGraph();
		AclfBus sourceBus = net.getBus(startBusId);
		// check the input bus id first
		if (this.net.getBus(startBusId) == null) {
			IpssLogger.getLogger().severe(
					"The input starting busId # " + startBusId
							+ "  can not be found in the network!");

			return false;
		}
		if (sourceBus.getVoltageMag(UnitType.kV) < voltageLevel) {
			IpssLogger.getLogger().severe(
					"The input starting bus voltage # "
							+ sourceBus.getVoltageMag(UnitType.kV)
							+ "  is less than the filtering level#"
							+ voltageLevel);
		}
		Long areaNum = sourceBus.getArea().getNumber();
		// forming the Topology

		// using the begin(end) update to keep track of the topology change.

		mg.getModel().beginUpdate();
		try {

			areaTopSearch(voltageLevel, areaNum);

		} finally {
			mg.getModel().endUpdate();
		}
		// initialize the topology and plot the JFrame
		top.initialize();
		return true;
	}

	private void areaTopSearch(double voltageLevel, long areaNum) {

		for (Branch bra : net.getBranchList()) {

			if (!bra.isGroundBranch() && bra.isActive()) {

				String fromBusId = bra.getFromBus().getId();
				String toBusId = bra.getToBus().getId();
				System.out.println(fromBusId + "," + toBusId);
				if (this.net.getBus(fromBusId).getVoltageMag(UnitType.kV) >= voltageLevel
						&& this.net.getBus(toBusId).getVoltageMag(UnitType.kV) >= voltageLevel
						&& ((bra.getFromBus().getArea() != null ? bra
								.getFromBus().getArea().getNumber() == areaNum
								: true) || (bra.getToBus().getArea() != null ? bra
								.getToBus().getArea().getNumber() == areaNum
								: true))) {
					if (!addedBusTable.containsKey(fromBusId)) {

						x = Math.random();
						y = Math.random();
						Object nextBus = top.getTopGraph().insertVertex(
								top.getTopGraph().getDefaultParent(), null,
								fromBusId, x, y, 80, 40);

						addedBusTable.put(fromBusId, nextBus);
					}
					if (!addedBusTable.containsKey(toBusId)) {

						x = Math.random();
						y = Math.random();
						Object nextBus = top.getTopGraph().insertVertex(
								top.getTopGraph().getDefaultParent(), null,
								toBusId, x, y, 80, 40);

						addedBusTable.put(toBusId, nextBus);
					}

					top.getTopGraph().insertEdge(
							top.getTopGraph().getDefaultParent(), null,
							branchType.Line, addedBusTable.get(fromBusId),
							addedBusTable.get(toBusId));

				}
			}

		}
	}

	private boolean DFS(String busId, int length, int objLength) {
		boolean isToBus = true;
		int startLength = length;
		String cirId = "";
		// System.out.println(busId);
		Bus source = net.getBus(busId);
		if (length < objLength && source != null) {
			if (source.getBranchList() != null
					&& ((AclfBus) source).getVoltageMag(UnitType.kV) >= voltageLevel)
				for (Branch bra : source.getBranchList()) {

					if (!bra.isGroundBranch()) {
						isToBus = bra.getFromBusId().equals(busId);
						String nextBusId = isToBus ? bra.getToBusId() : bra
								.getFromBusId();
						if (net.getBus(nextBusId) != null
								&& net.getBus(nextBusId).getVoltageMag(
										UnitType.kV) >= voltageLevel)
							if (!nextBusId.equals(sourceBusId)
									&& (displayName ? net.getBus(nextBusId)
											.getName() != null : true)) { // fromBusId-->buId
								if (!addedBusTable.containsKey(nextBusId)) {

									x = Math.random();
									y = Math.random();
									Object nextBus = top
											.getTopGraph()
											.insertVertex(
													top.getTopGraph()
															.getDefaultParent(),
													null,
													getDisplayString(nextBusId),
													x, y, 80, 40);

									addedBusTable.put(nextBusId, nextBus);
								}
								if (isToBus) {
									top.getTopGraph().insertEdge(
											top.getTopGraph()
													.getDefaultParent(), null,
											branchType.Line,
											addedBusTable.get(busId),
											addedBusTable.get(nextBusId));

								} else {
									top.getTopGraph().insertEdge(
											top.getTopGraph()
													.getDefaultParent(), null,
											branchType.Line,
											addedBusTable.get(nextBusId),
											addedBusTable.get(busId));
								}
								// update the visit state
								// net.getBus(nextBusId).setVisited(true);
								// recursive DFS
								DFS(nextBusId, ++length, objLength);

								length = startLength;

							}
					}
				}
		}
		return true;
	}

	private boolean DFS(String busId, double zThreshold) {
		boolean isToBus = true;

		Bus source = net.getBus(busId);

		for (Branch bra : source.getBranchList()) {

			if (!bra.isGroundBranch() && bra.isActive()
					&& ((AclfBranch) bra).getZ().abs() <= zThreshold) {
				isToBus = bra.getFromBusId().equals(busId);
				String nextBusId = isToBus ? bra.getToBusId() : bra
						.getFromBusId();
				if (!nextBusId.equals(sourceBusId)) { // fromBusId-->buId
					if (!addedBusTable.containsKey(nextBusId)) {

						x = Math.random();
						y = Math.random();
						Object nextBus = top.getTopGraph().insertVertex(
								top.getTopGraph().getDefaultParent(), null,
								getDisplayString(nextBusId), x, y, 80, 40);

						addedBusTable.put(nextBusId, nextBus);
					}
					if (isToBus) {
						top.getTopGraph().insertEdge(
								top.getTopGraph().getDefaultParent(), null,
								branchType.Line, addedBusTable.get(busId),
								addedBusTable.get(nextBusId));

					} else {
						top.getTopGraph().insertEdge(
								top.getTopGraph().getDefaultParent(), null,
								branchType.Line, addedBusTable.get(nextBusId),
								addedBusTable.get(busId));
					}
					// update the visit state
					net.getBus(nextBusId).setVisited(true);
					// recursive DFS
					DFS(nextBusId, zThreshold);

				}
			}
		}

		return true;
	}

	private boolean DFS(String busId, int length, int objLength, boolean forward) {
		Bus source = net.getBus(busId);
		if (length < objLength) {
			if (!forward) {// in backward direction
				for (Branch bra : source.getToBranchList()) {
					if (!bra.isGroundBranch()) {
						String fromBusId = bra.getFromBusId();
						if (!fromBusId.equals(sourceBusId)) {
							if (!addedBusTable.containsKey(fromBusId)) {
								x = Math.random();
								y = Math.random();
								Object fromBus = top.getTopGraph()
										.insertVertex(
												top.getTopGraph()
														.getDefaultParent(),
												null,
												getDisplayString(fromBusId), x,
												y, 80, 40);

								top.getTopGraph().insertEdge(
										top.getTopGraph().getDefaultParent(),
										null, branchType.Line, fromBus,
										addedBusTable.get(busId));

								addedBusTable.put(fromBusId, fromBus);
							} else {
								top.getTopGraph().insertEdge(
										top.getTopGraph().getDefaultParent(),
										null, branchType.Line,
										addedBusTable.get(fromBusId),
										addedBusTable.get(busId));
							}
							// update the visit state
							net.getBus(fromBusId).setVisited(true);
							// recursive DFS
							DFS(fromBusId, ++length, objLength, forward);

						}
					}
				}
			} else { // in forward direction
				for (Branch bra : source.getFromBranchList()) {
					if (!bra.isGroundBranch()) {
						String toBusId = bra.getToBusId();
						if (!toBusId.equals(sourceBusId)) {
							if (!addedBusTable.containsKey(toBusId)) {
								x = Math.random();
								y = Math.random();
								// add the tobus vertex
								Object toBus = top.getTopGraph().insertVertex(
										top.getTopGraph().getDefaultParent(),
										null, getDisplayString(toBusId), x, y,
										80, 40);
								// add the edge between source bus and target
								// bus
								top.getTopGraph().insertEdge(
										top.getTopGraph().getDefaultParent(),
										null, branchType.Line,
										addedBusTable.get(busId), toBus);
								// add the busId to the processed bus list
								addedBusTable.put(toBusId, toBus);
								// update the visit state
								net.getBus(toBusId).setVisited(true);
							} else {
								top.getTopGraph().insertEdge(
										top.getTopGraph().getDefaultParent(),
										null, branchType.Line,
										addedBusTable.get(busId),
										addedBusTable.get(toBusId));
							}
							// recursive DFS
							DFS(toBusId, ++length, objLength, forward);
						}
					}
				}
			}
		}
		return true;
	}

	public boolean getBreakerChain(String breakerId) {
		// get fromBus and toBus

		return true;
	}

	private void registStyle() {
		mxStylesheet stylesheet = top.getTopGraph().getStylesheet();
		// active bus style
		Hashtable<String, Object> style = new Hashtable<String, Object>();
		style.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_RECTANGLE);
		style.put(mxConstants.STYLE_OPACITY, 50);
		style.put(mxConstants.STYLE_FONTCOLOR, "#774400");
		stylesheet.putCellStyle("BusActive", style);

		// outage line style-- dash line
		style = new Hashtable<String, Object>();
		style.put(mxConstants.STYLE_EDGE, mxConstants.STYLE_DASHED);
		stylesheet.putCellStyle("OutageLine", style);
	}

	public Hashtable<String, Object> getBusTable() {
		return this.addedBusTable;
	}

	public void setPlotSize(int width, int height) {
		this.top.setSize(width, height);
	}

	public static void createAndShowGUI(Component comp, String name) {

		// Create and set up the window.
		JFrame frame = new JFrame(name);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		// Add content to the window.
		frame.add(comp, BorderLayout.CENTER);

		// Display the window.
		frame.setSize(1500, 1000);
		frame.setVisible(true);
	}

}

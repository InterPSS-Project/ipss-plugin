package org.interpss.QA.topology;

import javax.swing.JFrame;

import com.interpss.common.exp.InterpssException;
import com.mxgraph.layout.mxFastOrganicLayout;
import com.mxgraph.layout.mxIGraphLayout;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.view.mxGraph;
/*
 * 		
		// When using Java objects as user objects, make sure to add the
		// package name containg the class and register a codec for the user
		// object class as follows:
		//
		// mxCodecRegistry.addPackage("com.example"); 
		// mxCodecRegistry.register(new mxObjectCodec(
		//	new com.example.CustomUserObject()));
		
		// Note that the object must have an empty constructor and a setter and
		// getter for each property to be persisted. The object must not have
		// a property called ID as this property is reserved for resolving cell
		// references and will cause problems when used inside the user object.
		//
		
		//TODO the empty constructor provided by Ipss now is not visible
		
		mxCodecRegistry.addPackage("com.interpss.core.aclf.impl");
		mxCodecRegistry.register(new mxObjectCodec();
		
*/

public class Topology extends JFrame{

	/**
	 * 
	 */
	private static final long serialVersionUID = -3073163474631966135L;
	private mxGraph mg = new mxGraph();
	private Object parent = mg.getDefaultParent();
	
	
	public Topology() throws InterpssException{
		super("Network topology");
	}
	public Topology(String name) throws InterpssException{
		super(name);
	}
	
	/**
	 * Run this initialize after forming the topology graph 
	 * to output the topology frame.
	 * @return
	 */
	public boolean initialize(){
		try{
		mxGraphComponent graphComponent = new mxGraphComponent(mg);
	
		getContentPane().add(graphComponent);
		
		mxIGraphLayout layout = new mxFastOrganicLayout(mg);
		layout.execute(parent);
		
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setSize(1000, 500);
		this.setVisible(true);
		}catch(Exception e){
			e.printStackTrace();
			return false;
		}
		return true;
	}
	public mxGraphComponent getGraphComponet(){
		mxGraphComponent graphComponent = new mxGraphComponent(mg);
		//getContentPane().add(graphComponent);
		mxIGraphLayout layout = new mxFastOrganicLayout(mg);
		layout.execute(parent);
		
		return graphComponent;
		
	}
	public mxGraph getTopGraph() {
		return mg;
	}
	public void setTopGraph(mxGraph mg) {
		this.mg = mg;
	}

	
}

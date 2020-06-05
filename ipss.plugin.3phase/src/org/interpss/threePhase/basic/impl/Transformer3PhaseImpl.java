package org.interpss.threePhase.basic.impl;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.Branch3Phase;
import org.interpss.threePhase.basic.Transformer3Phase;

import com.interpss.core.acsc.XfrConnectCode;
import com.interpss.core.acsc.adpter.impl.AcscXformerImpl;

public class Transformer3PhaseImpl extends AcscXformerImpl implements Transformer3Phase{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private Branch3Phase ph3Branch= null;
	
	private Complex y0 =null;
	private Complex y1 =null; // transformer primitive leakage admittance of a phase 
	
	private Complex3x3 LVBusVabc2HVBusVabcMatrix = null;
	private Complex3x3 LVBusIabc2HVBusVabcMatrix = null;
	private Complex3x3 LVBusVabc2HVBusIabcMatrix = null;
	private Complex3x3 LVBusIabc2HVBusIabcMatrix = null;
	private Complex3x3 HVBusVabc2LVBusVabcMatrix = null;
	private Complex3x3 LVBusIabc2LVBusVabcMatrix = null;
	
	
	private Complex3x3 turnRatioMatrix = null;
	
	
	public Transformer3PhaseImpl(Branch3Phase threePhBranch){
		this.ph3Branch =threePhBranch;
		
	}
	
	
	public Transformer3PhaseImpl() {
		
	}
    
	@Override
	public void set3PBranch(Branch3Phase ph3Branch) {
		this.ph3Branch = ph3Branch;
		setBranch(ph3Branch);
		
		this.y1 = this.ph3Branch.getY();
		if(this.ph3Branch.getY0()!=null) 
			y0 = this.ph3Branch.getY0();
		else
			y0 = y1;
		
	}

	@Override
	public void setZabc(Complex3x3 Zabc) {
		this.ph3Branch.setZabc(Zabc);
		
	}

	@Override
	public void setZabc(Complex Za, Complex Zb, Complex Zc) {
		Complex3x3 zabc = new Complex3x3();
		zabc.aa =Za;
		zabc.bb =Zb;
		zabc.cc =Zc;
		this.ph3Branch.setZabc(zabc);
		
	}

	@Override
	public Complex3x3 getZabc() {
		
		return this.ph3Branch.getZabc();
	}

	@Override
	public Complex3x3 getYabc() {
		
		return this.ph3Branch.getBranchYabc();
	}
	
	
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// using look up table to build the Yff, Ytt, yft, ytf for standard connected transformers
	// referred to Selva S. Moorthy, David Hoadley, "A new phase-coordinate transformer modeling for Ybus Anaysks", IEEE Trans. on Power systems, vol.17, No.4, 2002
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	@Override
	public Complex3x3 getYffabc() {
	    Complex3x3 yffabc = null;
		//Yg
		if(this.ph3Branch.getXfrFromConnectCode() == XfrConnectCode.WYE_SOLID_GROUNDED){
			yffabc = getY1().multiply(1/this.ph3Branch.getFromTurnRatio()/this.ph3Branch.getFromTurnRatio());
	
		}
		
		//Y
		else if(this.ph3Branch.getXfrFromConnectCode() == XfrConnectCode.WYE_UNGROUNDED){
			yffabc = getY2().multiply(1/this.ph3Branch.getFromTurnRatio()/this.ph3Branch.getFromTurnRatio());
			
		}
		
		//D
        else if(this.ph3Branch.getXfrFromConnectCode() == XfrConnectCode.DELTA|| this.ph3Branch.getXfrFromConnectCode() == XfrConnectCode.DELTA11){
        	yffabc = getY2().multiply(1/this.ph3Branch.getFromTurnRatio()/this.ph3Branch.getFromTurnRatio());
    		
		} else
			try {
				throw new Exception("Unsupported connection type at the from side!");
			} catch (Exception e) {
				
				e.printStackTrace();
			}
		

		return yffabc;
	}

	@Override
	public Complex3x3 getYttabc() {
		 Complex3x3 yttabc = null;
			//Yg
			if(this.ph3Branch.getXfrToConnectCode() == XfrConnectCode.WYE_SOLID_GROUNDED){
				yttabc = getY1().multiply(1/this.ph3Branch.getToTurnRatio()/this.ph3Branch.getToTurnRatio());
		
			}
			
			//Y
			else if(this.ph3Branch.getXfrToConnectCode() == XfrConnectCode.WYE_UNGROUNDED){
				yttabc = getY2().multiply(1/this.ph3Branch.getToTurnRatio()/this.ph3Branch.getToTurnRatio());
				
			}
			
			//D
	        else if(this.ph3Branch.getXfrToConnectCode() == XfrConnectCode.DELTA || this.ph3Branch.getXfrToConnectCode() == XfrConnectCode.DELTA11){
	        	yttabc = getY2().multiply(1/this.ph3Branch.getToTurnRatio()/this.ph3Branch.getToTurnRatio());
	    		
			} else
				try {
					throw new Exception("Unsupported connection type at the from side!");
				} catch (Exception e) {
					
					e.printStackTrace();
				}
			
			return yttabc;
	}
	
	
	@Override
	public Complex3x3 getYftabc() {
		
		 Complex3x3 yftabc = null;
			//Yg-
		 if(this.ph3Branch.getXfrFromConnectCode() == XfrConnectCode.WYE_SOLID_GROUNDED){
			  //YgYg
			    if(this.ph3Branch.getXfrToConnectCode() == XfrConnectCode.WYE_SOLID_GROUNDED)
				   yftabc = getY1().multiply(-1/this.getFromTurnRatio()/this.getToTurnRatio());
			  //YgY
			    else if (this.ph3Branch.getXfrToConnectCode() == XfrConnectCode.WYE_UNGROUNDED)
			    	yftabc = getY2().multiply(-1/this.getFromTurnRatio()/this.getToTurnRatio());
			   //YgD1 
			    else if (this.ph3Branch.getXfrToConnectCode() == XfrConnectCode.DELTA)
			    	yftabc = getY3().multiply(-1/this.getFromTurnRatio()/this.getToTurnRatio());
			   //YgD11  
			    else if (this.ph3Branch.getXfrToConnectCode() == XfrConnectCode.DELTA11)
			    	yftabc = getY3().transpose().multiply(-1/this.getFromTurnRatio()/this.getToTurnRatio());
			}
			
			//Y-
			else if(this.ph3Branch.getXfrFromConnectCode() == XfrConnectCode.WYE_UNGROUNDED ){
				//Yg or Y
				if(this.ph3Branch.getXfrToConnectCode() == XfrConnectCode.WYE_SOLID_GROUNDED ||
						this.ph3Branch.getXfrToConnectCode() == XfrConnectCode.WYE_UNGROUNDED)
					    // note: y2* = y2
				        yftabc = getY2().multiply(-1/this.getFromTurnRatio()/this.getToTurnRatio());
				// D1
				 else if (this.ph3Branch.getXfrToConnectCode() == XfrConnectCode.DELTA)
				    	yftabc = getY3().multiply(-1/this.getFromTurnRatio()/this.getToTurnRatio());
			   // D11   
				 else if (this.ph3Branch.getXfrToConnectCode() == XfrConnectCode.DELTA11)
				    	yftabc = getY3().transpose().multiply(-1/this.getFromTurnRatio()/this.getToTurnRatio());
				
			}
			
			//D
	        else if(this.ph3Branch.getXfrFromConnectCode() == XfrConnectCode.DELTA){
	        	
	        	//D-Yg or Y
				if(this.ph3Branch.getXfrToConnectCode() == XfrConnectCode.WYE_SOLID_GROUNDED ||
						this.ph3Branch.getXfrToConnectCode() == XfrConnectCode.WYE_UNGROUNDED)
	        	    // Delta side lags 30 degrees related to wye side,  that is why transpose() is required
					// y3 matrix is corresponding to the case where wye on the from side. 
					yftabc = getY3().transpose().multiply(-1/this.getFromTurnRatio()/this.getToTurnRatio());
					
				
				else if(this.ph3Branch.getXfrToConnectCode() == XfrConnectCode.DELTA)
					 yftabc = getY2().multiply(-1/this.getFromTurnRatio()/this.getToTurnRatio());
	    		
			} 
		 
			//D11
	        else if(this.ph3Branch.getXfrFromConnectCode() == XfrConnectCode.DELTA11){
	        	
	        	//D-Yg or Y
				if(this.ph3Branch.getXfrToConnectCode() == XfrConnectCode.WYE_SOLID_GROUNDED ||
						this.ph3Branch.getXfrToConnectCode() == XfrConnectCode.WYE_UNGROUNDED)
	        	    ////TODO original 11/23/2015 
					//yftabc = getY3().transpose().multiply(-1/this.getFromTurnRatio()/this.getToTurnRatio());
				  
					// Delta side leads 30 degrees related to wye side
					yftabc = getY3().multiply(-1/this.getFromTurnRatio()/this.getToTurnRatio());
				
				else if(this.ph3Branch.getXfrToConnectCode() == XfrConnectCode.DELTA || this.ph3Branch.getXfrToConnectCode() == XfrConnectCode.DELTA11)
					 yftabc = getY2().multiply(-1/this.getFromTurnRatio()/this.getToTurnRatio());
	    		
			} 
	        
	        
	        else
				try {
					throw new Exception("Unsupported connection type at the from side!");
				} catch (Exception e) {
					
					e.printStackTrace();
				}
			
			return yftabc;
	}


	@Override
	public Complex3x3 getYtfabc() {
		
		return getYftabc().transpose();
	}
	
	/**
	 * Refer to the paper: M.S.Chen, W.E.Dillon, "Power system modeling," Proc. of IEEE,Vol.62, No.7, 1974
	 * 
	 * AND paper Selva Moorthy et al "a new phase coordinate transformer model for Ybus analysis", IEEE PWRS Vol.17, No.4, Nov.2002
	 * 
	 * Y1 corresponding to self and mutual admittance on the Yn side
	 * It is symmetric with :
	 *     Y1ii = (y0+2y1)/3
	 *     Y1ij = (y0-y1)/3
	 * @return
	 */
	private  Complex3x3  getY1(){

		if(y1 != null){
			if(y0 == null) y0 = y1;
			Complex Y1ii = (y0.add(y1.multiply(2))).divide(3);
			Complex Y1ij = (y0.subtract(y1)).divide(3);
			return new Complex3x3(Y1ii,Y1ij);
		}
		return null;
	}
	
	
	/**
	 * Y2 corresponding to self and mutual admittance on the Y or delta side
	 * It is symmetric with :
	 *     Y1ii = (2y1)/3
	 *     Y1ij = (-y1)/3
	 * @return
	 */
    private  Complex3x3  getY2(){
    	
    	if(y1 != null){
	    	Complex Y1ii = (y1.multiply(2.0d)).divide(3);
			Complex Y1ij = (y1.multiply(-1.0d)).divide(3);
			return new Complex3x3(Y1ii,Y1ij);
	    }
    	return null;
	}
    
    
	/**
	 * Y3 corresponding to mutual admittance of the Y and delta connections
	 * It is  with the structure: 
	 *              [y1 -y1  0]
	 *    1/sqrt(3)*[0 y1, -y1]
	 *              [-y1, 0,y1]
	 * @return
	 */
    private  Complex3x3  getY3(){
    	if(y1 != null)
    	return new Complex3x3(new Complex[][]{
    			{y1.multiply(Math.sqrt(3)/3),     y1.multiply(-1*Math.sqrt(3)/3),        new Complex(0.0, 0.0)},
    			{new Complex(0,0),                y1.multiply(Math.sqrt(3)/3),    y1.multiply(-1*Math.sqrt(3)/3) },
    			{  y1.multiply(-1*Math.sqrt(3)/3),            new Complex(0,0),            y1.multiply(Math.sqrt(3)/3)}});

		return null;
	}
    
    /**
     * ------------------------------------------------------------------------
     *    The following models and matrices are based on W.H.Kersting's book: 
     *    Distribution system modeling and analysis (3rd Ed). 2012
     * 
     * ------------------------------------------------------------------------
     * 
     */

 
	@Override
	public Complex3x3 getLVBusVabc2HVBusVabcMatrix() {
		     //Delta-Delta
		     if(this.isHVDeltaConnectted() && this.isLVDeltaConnectted()){
		    	 //                      at = [W][AV][D] = [W][D]*nt
		    	 this.LVBusVabc2HVBusVabcMatrix =this.getDeltaVLL2VLNMatrix().multiply(
		    			                                      this.getDeltaVLN2VLLMatrix()).multiply(getWindingTurnRatioPU());
		     }
		     // Delta-Grounded Wye step-down
		     else if (this.isHVDeltaConnectted() && !this.isLVDeltaConnectted()){
		    	 this.LVBusVabc2HVBusVabcMatrix =this.getDeltaVLL2VLNMatrix().multiply(this.getVtabc2VLLabcMatrix());
		     }
		    
		     // Grounded Wye - Grounded Wye
		     else if (!this.isHVDeltaConnectted() && !this.isLVDeltaConnectted()){
		    	 this.LVBusVabc2HVBusVabcMatrix = this.getTurnRatioMatrix();
		     }
		     else{
		    	 throw new UnsupportedOperationException("The input transformer connection type is not supported yet! Transformer, fromConnect, toConnect :"+
		    			 this.ph3Branch.getId()+", "+this.ph3Branch.getXfrFromConnectCode()+", "+this.ph3Branch.getXfrToConnectCode());
		     }
		    	 
		return this.LVBusVabc2HVBusVabcMatrix;
	}


	@Override
	public Complex3x3 getLVBusIabc2HVBusVabcMatrix() {
		   //Delta-Delta
	     if(this.isHVDeltaConnectted() && this.isLVDeltaConnectted()){
	    	 this.LVBusIabc2HVBusVabcMatrix = getDeltaDeltaLVIabc2HVVabcMatrix();
	     }
	     // Delta-Grounded Wye step-down
	     else if (this.isHVDeltaConnectted() && !this.isLVDeltaConnectted()){
	    	 this.LVBusIabc2HVBusVabcMatrix = getLVBusVabc2HVBusVabcMatrix().multiply(this.getZabc());
	     }
	    
	     // Grounded Wye - Grounded Wye
	     else if (!this.isHVDeltaConnectted() && !this.isLVDeltaConnectted()){
	    	 this.LVBusIabc2HVBusVabcMatrix = this.getTurnRatioMatrix().multiply(this.getZabc());
	     }
	     else{
	    	 throw new UnsupportedOperationException("The input transformer connection type is not supported yet!");
	     }
		return this.LVBusIabc2HVBusVabcMatrix;
	}


	@Override
	public Complex3x3 getLVBusVabc2HVBusIabcMatrix() {
		  //Delta-Delta
	     if(this.isHVDeltaConnectted() && this.isLVDeltaConnectted()){
	    	 this.LVBusVabc2HVBusIabcMatrix = new Complex3x3();
	     }
	     // Delta-Grounded Wye step-down
	     else if (this.isHVDeltaConnectted() && !this.isLVDeltaConnectted()){
	    	 this.LVBusVabc2HVBusIabcMatrix = new Complex3x3();
	     }
	    
	     // Grounded Wye - Grounded Wye
	     else if (!this.isHVDeltaConnectted() && !this.isLVDeltaConnectted()){
	    	 this.LVBusVabc2HVBusIabcMatrix = new Complex3x3();
	     }
	     else{
	    	 throw new UnsupportedOperationException("The input transformer connection type is not supported yet!");
	     }
		return this.LVBusVabc2HVBusIabcMatrix;
	}


	@Override
	public Complex3x3 getLVBusIabc2HVBusIabcMatrix() {
		 //Delta-Delta
	     if(this.isHVDeltaConnectted() && this.isLVDeltaConnectted()){
	    	 
	    	 this.LVBusIabc2HVBusIabcMatrix = Complex3x3.createUnitMatrix().multiply(1/this.getWindingTurnRatioPU());
	     }
	     // Delta-Grounded Wye step-down
	     else if (this.isHVDeltaConnectted() && !this.isLVDeltaConnectted()){
	    	 
	    	 Complex3x3 dt = new Complex3x3();

	        	dt.aa = new Complex(1);
	        	dt.ab = new Complex(-1);
	        	
	        	dt.bb = new Complex(1);
	        	dt.bc = new Complex(-1);
	        	
	        	dt.ca = new Complex(-1);
	        	dt.cc = new Complex(1);
	        	
	        	dt = dt.multiply(1/this.getWindingTurnRatioPU());
	        	
	    	 this.LVBusIabc2HVBusIabcMatrix = dt;
	     }
	    
	     // Grounded Wye - Grounded Wye
	     else if (!this.isHVDeltaConnectted() && !this.isLVDeltaConnectted()){
	    	 this.LVBusIabc2HVBusIabcMatrix = Complex3x3.createUnitMatrix().multiply(1/this.getWindingTurnRatioPU());
	     }
	     else{
	    	 throw new UnsupportedOperationException("The input transformer connection type is not supported yet!");
	     }
		return  this.LVBusIabc2HVBusIabcMatrix;
	}


	@Override
	public Complex3x3 getHVBusVabc2LVBusVabcMatrix() {
		 //Delta-Delta
	     if(this.isHVDeltaConnectted() && this.isLVDeltaConnectted()){
	    	                           // At  =  [W][AV]^-1[D]
	    	 this.HVBusVabc2LVBusVabcMatrix = this.getDeltaVLL2VLNMatrix().multiply(1/this.getWindingTurnRatioPU()).multiply(this.getDeltaVLN2VLLMatrix());
	     }
	     // Delta-Grounded Wye step-down
	     else if (this.isHVDeltaConnectted() && !this.isLVDeltaConnectted()){
	    		Complex3x3 At = new Complex3x3();

	        	At.aa = new Complex(1);
	        	At.ac = new Complex(-1);
	        	At.ba = new Complex(-1);
	        	At.bb = new Complex(1);
	        	At.cb = new Complex(-1);
	        	At.cc = new Complex(1);
	        	
	        	At = At.multiply(1/this.getWindingTurnRatioPU());
	        	
	    	 this.HVBusVabc2LVBusVabcMatrix = At;
	     }
	    
	     // Grounded Wye - Grounded Wye
	     else if (!this.isHVDeltaConnectted() && !this.isLVDeltaConnectted()){
	    	 this.HVBusVabc2LVBusVabcMatrix = Complex3x3.createUnitMatrix().multiply(1/this.getWindingTurnRatioPU());
	     }
	     else{
	    	 throw new UnsupportedOperationException("The input transformer connection type is not supported yet!");
	     }
		return  this.HVBusVabc2LVBusVabcMatrix;
	}


	@Override
	public Complex3x3 getLVBusIabc2LVBusVabcMatrix() {
		//Delta-Delta
	     if(this.isHVDeltaConnectted() && this.isLVDeltaConnectted()){
	    	 // Bt  =  [W][Zabc][G1]
	    	 Complex3x3 F = new Complex3x3();
	     	F.aa = new Complex(1);
	     	F.ac = new Complex(-1);
	     	F.ba = new Complex(-1);
	     	F.bb = new Complex(1);
	     	F.ca = getZabc().aa;
	     	F.cb = getZabc().bb;
	     	F.cc = getZabc().cc;
	     	
	     	// G = [F]^-1
	     	Complex3x3 G = F.inv();
	     	
	     	Complex3x3 G1 = G;
	     	G1.ac = new Complex(0);
	     	G1.bc = new Complex(0);
	     	G1.cc = new Complex(0);
	     	
	     	this.LVBusIabc2LVBusVabcMatrix = this.getDeltaVLL2VLNMatrix().multiply(this.getZabc()).multiply(G1);
	    	 
	     }
	     // Delta-Grounded Wye step-down
	     else if (this.isHVDeltaConnectted() && !this.isLVDeltaConnectted()){
	    	 this.LVBusIabc2LVBusVabcMatrix = this.getZabc();
	     }
	    
	     // Grounded Wye - Grounded Wye
	     else if (!this.isHVDeltaConnectted() && !this.isLVDeltaConnectted()){
	    	 this.LVBusIabc2LVBusVabcMatrix = this.getZabc();
	     }
	     else{
	    	 throw new UnsupportedOperationException("The input transformer connection type is not supported yet!");
	     }
		return this.LVBusIabc2LVBusVabcMatrix;
	}
	
	/**
	 * Define the turn ratio as winding turn ratio between HV to LV, not VLL(HV)/VLL(LV)
	 * @return
	 */
	private double getWindingTurnRatioPU(){
		boolean isHVOnFromBusSide = true;
		double t = this.ph3Branch.getFromTurnRatio()/this.ph3Branch.getToTurnRatio();
		
		// t = winding (HV)/ winding(LV)
		if(this.ph3Branch.getFromAclfBus().getBaseVoltage() < this.ph3Branch.getToAclfBus().getBaseVoltage()){
			 t = this.ph3Branch.getToTurnRatio()/this.ph3Branch.getFromTurnRatio();
			 isHVOnFromBusSide = false;
		}
		
		if(this.ph3Branch.getXfrFromConnectCode() !=this.ph3Branch.getXfrToConnectCode()){
			
			//Delta Grounded Wye
			if(this.ph3Branch.getXfrFromConnectCode()==XfrConnectCode.DELTA11 || this.ph3Branch.getXfrFromConnectCode()==XfrConnectCode.DELTA){
				if(this.ph3Branch.getXfrToConnectCode()==XfrConnectCode.WYE_SOLID_GROUNDED){
					if(isHVOnFromBusSide) // Delta Grounded Wye step down
					  t= t*Math.sqrt(3);
					else{
						throw new UnsupportedOperationException(" Grounded Wye -Delta  step up transformer is not supported yet");
					}
						
				}
			}
			else if(this.ph3Branch.getXfrFromConnectCode()==XfrConnectCode.WYE_SOLID_GROUNDED){
				if(this.ph3Branch.getXfrToConnectCode()==XfrConnectCode.DELTA11 || this.ph3Branch.getXfrToConnectCode()==XfrConnectCode.DELTA){
					if(!isHVOnFromBusSide) // Delta Grounded Wye step down
					   t= t*Math.sqrt(3);
					else
						throw new UnsupportedOperationException(" Grounded Wye -Delta  step up transformer is not supported yet");
				}
				
			}
			else {
				throw new UnsupportedOperationException(" Grounded Wye -Delta  step up transformer is not supported yet");
			}
				
		}
		return t;
	}
	private Complex3x3 getTurnRatioMatrix(){
		if(turnRatioMatrix ==null){
			
			//Yg-Yg
			if(this.ph3Branch.getXfrToConnectCode() == XfrConnectCode.WYE_SOLID_GROUNDED){
				if(this.ph3Branch.getXfrFromConnectCode() == XfrConnectCode.WYE_SOLID_GROUNDED){
					turnRatioMatrix = Complex3x3.createUnitMatrix().multiply(getWindingTurnRatioPU());
				}
			}
		}
		return turnRatioMatrix; 
	}
	private boolean isHVDeltaConnectted(){
		if(this.ph3Branch.getFromAclfBus().getBaseVoltage() > this.ph3Branch.getToAclfBus().getBaseVoltage()){
			//TODO note: the standard conection for the high voltage side is Delta 11
			if(this.ph3Branch.getXfrFromConnectCode()==XfrConnectCode.DELTA11 ||this.ph3Branch.getXfrFromConnectCode()==XfrConnectCode.DELTA)  
				return true;
		}
		else{
			if(this.ph3Branch.getXfrToConnectCode()==XfrConnectCode.DELTA11 ||this.ph3Branch.getXfrToConnectCode()==XfrConnectCode.DELTA)
				return true;
		}
		return false;
	}
	
    private boolean isLVDeltaConnectted(){
    	if(this.ph3Branch.getFromAclfBus().getBaseVoltage() < this.ph3Branch.getToAclfBus().getBaseVoltage()){
			if(this.ph3Branch.getXfrFromConnectCode()==XfrConnectCode.DELTA || this.ph3Branch.getXfrFromConnectCode()==XfrConnectCode.DELTA11)
				return true;
		}
		else{
			if(this.ph3Branch.getXfrToConnectCode()==XfrConnectCode.DELTA||this.ph3Branch.getXfrToConnectCode()==XfrConnectCode.DELTA11)
				return true;
		}
		return false;
	}
    
    private boolean isHVWindingOnFromBusSide(){
    	if(this.ph3Branch.getFromAclfBus().getBaseVoltage() > this.ph3Branch.getToAclfBus().getBaseVoltage())
    		return true;
    	else
    		return false;
    }
 
   /**
    * W matrix for delta-grounded wye connection
    * @return
    */
    private Complex3x3 getDeltaVLL2VLNMatrix(){
    	Complex3x3 W = new Complex3x3();
    	W.aa = new Complex(2);
    	W.ab = new Complex(1);
    	W.bb = new Complex(2);
    	W.bc = new Complex(1);
    	W.ca = new Complex(1);
    	W.cc = new Complex(2);
    	W=W.multiply(1.0/3.0);
    	
    	return W;
    }
    
    /**
     * D matrix for delta-grounded wye connection
     * @return
     */
     private Complex3x3 getDeltaVLN2VLLMatrix(){
     	Complex3x3 D = new Complex3x3();
     	D.aa = new Complex(1);
     	D.ab = new Complex(-1);
     	D.bb = new Complex(1);
     	D.bc = new Complex(-1);
     	D.ca = new Complex(-1);
     	D.cc = new Complex(1);
     
     	
     	return D;
     }
    
    /*
     * AV matrix for delta-grounded wye connection
     */
    private Complex3x3 getVtabc2VLLabcMatrix(){
    	Complex3x3 AV = new Complex3x3();

    	AV.ab = new Complex(-1);
    	AV.bc = new Complex(-1);
    	AV.ca = new Complex(-1);
    	AV=AV.multiply(this.getWindingTurnRatioPU());
    	
    	return AV;
    }
    
    private Complex3x3 getDeltaDeltaLVIabc2HVVabcMatrix(){
    	
    	// F -> [IOabc] = [F][IDabc]
    	
    	// F = [ 1 0 -1; -1 1 0; Ztab, Ztbc, Ztca]
    	
    	Complex3x3 F = new Complex3x3();
    	F.aa = new Complex(1);
    	F.ac = new Complex(-1);
    	F.ba = new Complex(-1);
    	F.bb = new Complex(1);
    	F.ca = getZabc().aa;
    	F.cb = getZabc().bb;
    	F.cc = getZabc().cc;
    	
    	// G = [F]^-1
    	Complex3x3 G = F.inv();
    	
    	Complex3x3 G1 = G;
    	G1.ac = new Complex(0);
    	G1.bc = new Complex(0);
    	G1.cc = new Complex(0);
    	
    	// bt = [AV][W][Ztabc][G1]
    	
    	
    	Complex3x3 bt = Complex3x3.createUnitMatrix().multiply(this.getWindingTurnRatioPU());
    	bt = bt.multiply(this.getDeltaVLL2VLNMatrix()).multiply(this.getZabc()).multiply(G1);
    	
    	return bt;
    }


}

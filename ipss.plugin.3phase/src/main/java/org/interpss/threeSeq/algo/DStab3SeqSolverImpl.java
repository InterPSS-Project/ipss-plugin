package org.interpss.threeSeq.algo;

import java.util.Hashtable;
import java.util.Map.Entry;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.core.algo.sc.ScBusModelType;
import com.interpss.core.net.Bus;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.algo.defaultImpl.DStabSolverImpl;
import com.interpss.dstab.common.DStabSimuException;

public class DStab3SeqSolverImpl extends DStabSolverImpl {

    private static final Logger log = LoggerFactory.getLogger(DStab3SeqSolverImpl.class);
    private BaseDStabNetwork<?,?> net = null;

    private double negZeroSeqCurrTolerance = 1.0E-3;

    public DStab3SeqSolverImpl(DynamicSimuAlgorithm algo, IPSSMsgHub msg) {
        super(algo, msg);
        this.net = dstabAlgo.getNetwork();
    }

    @Override public boolean initialization() {
        boolean flag = super.initialization();

        // check three-sequence data
        if(this.net.isPositiveSeqDataOnly()){
            log.error("Three-seq data is required, but the network model has only positive sequence data!");
            return false;
        }
        //build the negative and zero sequence Y matrix
        this.net.formScYMatrix(SequenceCode.NEGATIVE, ScBusModelType.DSTAB_SIMU, true);

        this.net.formScYMatrix(SequenceCode.ZERO, ScBusModelType.DSTAB_SIMU, true);


        return flag;

    }

    @Override
    public boolean networkSolutionStep() throws DStabSimuException{
        boolean netSolConverged = true;
         //maxIterationTimes =1;
         for(int i=0;i<maxIterationTimes;i++){

             netSolConverged = true;


            // solve net equation
             boolean netSolvFlag = false;
            // solve net equation

            if ((dstabAlgo.getNetwork() instanceof DStabNetwork3Phase)) {
                netSolvFlag = ((DStabNetwork3Phase)dstabAlgo.getNetwork()).solvePosSeqNetEqn();
            } else {
                netSolvFlag = dstabAlgo.getNetwork().solveNetEqn();
            }

            if(!netSolvFlag) {
                throw new DStabSimuException("Exception in dstabNet.solveNetEqn()");
            }

            for ( Bus busi : dstabAlgo.getNetwork().getBusList() ) {
                BaseDStabBus bus = (BaseDStabBus)busi;
                if(bus.isActive()){
                    if(i>=1){
                        if(!NumericUtil.equals(bus.getVoltage(),voltageRecTable.get(bus.getId()),this.converge_tol)) {
                            netSolConverged =false;
                        }

                    }
                    voltageRecTable.put(bus.getId(), bus.getVoltage());
                    bus.getThreeSeqVoltage().b_1 = bus.getVoltage();
                }
            }

            // check whether the network solution is converged?
            if(i>=1 && netSolConverged) {
                log.debug("SimuTime: "+dstabAlgo.getSimuTime()+"\n Network solution converges with "+(i+1)+" iterations");
                break;
            }

         } // END OF for maxIterationTimes loop
         return netSolConverged;
    }


    @Override public void nextStep(double time, double dt, DynamicSimuMethod method)  throws DStabSimuException {

        super.nextStep(time, dt, method);

       //////////////////////////////////// The following is for the negative and zero sequences /////////////////

        //solve the negative- and zero-sequence networks
        Hashtable<String, Complex3x1> threeSeqCurInjTable = this.net.getCustom3SeqBusCurrInjHashtable();



        Hashtable<String, Complex> negCurTable =  new Hashtable<>();

        Hashtable<String, Complex> zeroCurTable = new Hashtable<>();

        if(threeSeqCurInjTable!=null){
             negCurTable =   getSeqCurInjTable(threeSeqCurInjTable, SequenceCode.NEGATIVE);
             zeroCurTable =  getSeqCurInjTable(threeSeqCurInjTable, SequenceCode.ZERO);

        }

      //TODO add the internal unbalanced bus fault effects to the negCurTable and  zeroCurTable
        addInternalFaultCurrentToCurTable( time,negCurTable,zeroCurTable);

       //solve sequence networks and save bus voltages

       // negative sequence
       if(this.getMaxCurMag(negCurTable)>this.negZeroSeqCurrTolerance){
  		   Hashtable<String,Complex> negVoltTable = this.solveSeqNetwork(net,SequenceCode.NEGATIVE, negCurTable);

  		   for(Entry<String,Complex> e: negVoltTable.entrySet()){
  			   net.getBus(e.getKey()).getThreeSeqVoltage().c_2 =e.getValue();
  		   }
       }

	  // zero sequence

	    if(this.getMaxCurMag(zeroCurTable)>this.negZeroSeqCurrTolerance){
	     Hashtable<String,Complex> zeroVoltTable = this.solveSeqNetwork(net,SequenceCode.ZERO, zeroCurTable);

	     if(zeroVoltTable == null) {
			log.warn("zeroVoltTable == null ");
		}

		    for(Entry<String,Complex> e: zeroVoltTable.entrySet()){
		    	net.getBus(e.getKey()).getThreeSeqVoltage().a_0 =e.getValue();
		    }
	    }



	}


	@Override protected void output(BaseDStabBus bus, double t, boolean plotOutput) throws DStabSimuException {
		super.output(bus, t, plotOutput);

		//TODO output three-sequence bus voltages
	}

	private Hashtable<String, Complex> solveSeqNetwork(BaseDStabNetwork<?,?> subnet, SequenceCode seq,Hashtable<String, Complex> seqCurInjTable){

		 Hashtable<String, Complex>  busVoltResults = new  Hashtable<>();
		// solve the Ymatrix
		switch (seq){

	  	// Positive sequence
		case POSITIVE:
			subnet.setCustomBusCurrInjHashtable(null);

		    ISparseEqnComplex subNetY= subnet.getYMatrix();

		    subNetY.setB2Zero();

		       for(Entry<String,Complex> e: seqCurInjTable.entrySet()){
				   subNetY.setBi(e.getValue(),subnet.getBus(e.getKey()).getSortNumber());
			   }
			   try {
				   // solve network to obtain Vext_injection
				    subNetY.solveEqn();


				} catch (IpssNumericException e1) {
					log.error("Error solving positive sequence network", e1);
					return null;
				}
			   for(BaseDStabBus bus:subnet.getBusList()){
			    	  busVoltResults.put(bus.getId(), subNetY.getX(bus.getSortNumber()));
			   }


		    //TODO extract the current and map them to the buses

		    break;
		case NEGATIVE:
			   ISparseEqnComplex negSeqYMatrix = subnet.getNegSeqYMatrix();
			   if(!negSeqYMatrix.isFactorized()){
				   try {
						negSeqYMatrix.factorization(1.0E-8);
					} catch (IpssNumericException e2) {
						log.error("Error factorizing negative sequence Y matrix", e2);
					}
			   }


			   negSeqYMatrix.setB2Zero();

			   for(Entry<String,Complex> e: seqCurInjTable.entrySet()){
				 negSeqYMatrix.setBi(e.getValue(),subnet.getBus(e.getKey()).getSortNumber());
			   }

			   //System.out.println("\n\n negative Ymatrix =\n"+negSeqYMatrix.toString());

			   try {
				   // solve network to obtain Vext_injection
				   negSeqYMatrix.solveEqn();

			   } catch (IpssNumericException e1) {
					log.error("Error solving negative sequence network", e1);
					return null;
			   }

			   for(BaseDStabBus bus:subnet.getBusList()){
			    	  busVoltResults.put(bus.getId(), negSeqYMatrix.getX(bus.getSortNumber()));
			   }


			break;
		case ZERO:
			   ISparseEqnComplex zeroSeqYMatrix = subnet.getZeroSeqYMatrix();

			   if(!zeroSeqYMatrix.isFactorized()){
				   try {
					     zeroSeqYMatrix.factorization(1.0E-8);
						} catch (IpssNumericException e2) {
							log.error("Error factorizing zero sequence Y matrix", e2);
					}
			   }
			   zeroSeqYMatrix.setB2Zero();

			   for(Entry<String,Complex> e: seqCurInjTable.entrySet()){
				   zeroSeqYMatrix.setBi(e.getValue(),subnet.getBus(e.getKey()).getSortNumber());
			   }
			   try {
				   // solve network to obtain Vext_injection
				   zeroSeqYMatrix.solveEqn();

			   } catch (IpssNumericException e1) {
					log.error("Error solving zero sequence network", e1);
					return null;
			   }

			   for(BaseDStabBus bus:subnet.getBusList()){
			    	  busVoltResults.put(bus.getId(), zeroSeqYMatrix.getX(bus.getSortNumber()));
			   }

			break;
	    }

		// save the seq bus voltage result;

		return busVoltResults;
	}

	private Hashtable<String, Complex> getSeqCurInjTable(Hashtable<String, Complex3x1> curInjTable, SequenceCode seq){

		Hashtable<String, Complex> seqCurInjTable = new Hashtable<>();
		  if(curInjTable !=null){
			  switch(seq){
			  case POSITIVE:
				  for(Entry<String, Complex3x1> nvpair :curInjTable.entrySet()){
					  seqCurInjTable.put(nvpair.getKey(), nvpair.getValue().b_1);
				  }
				  break;
			  case NEGATIVE:
				  for(Entry<String, Complex3x1> nvpair :curInjTable.entrySet()){
					  seqCurInjTable.put(nvpair.getKey(), nvpair.getValue().c_2);
				  }
				  break;
			  case ZERO:
				  for(Entry<String, Complex3x1> nvpair :curInjTable.entrySet()){
					  seqCurInjTable.put(nvpair.getKey(), nvpair.getValue().a_0);
				  }
				  break;

			  }

			  return seqCurInjTable;
		  } else {
			return null;
		}
	}

	private double getMaxCurMag(Hashtable<String, Complex> seqCurInjTable){
		double imax = 0;
		for(Complex i :seqCurInjTable.values()){
			  if(imax <i.abs()) {
				imax = i.abs();
			}
		  }
	    return imax;
	}

	private void addInternalFaultCurrentToCurTable(double t,Hashtable<String, Complex> negCurTable,Hashtable<String, Complex> zeroCurTable){
		//TODO
	}
}
# InterPSS Multi-network Module

This module (together with ipss.plugin.3phase) is maintained by Qiuhua Huang. The distribution system related modeling and algorithms are developed under ipss.plugin.3phase project. Should you have any issue with the codes, please create a new issue or refer to existing issues on github. For other questions such as contributing to the code base or collaboration, please send email to: Qiuhua dot Huang at ASU dot edu.

## 1. Overview 

Main ideas behind the implementation:

- **Multiple (Sub)networks** : InterPSS supports multiple subnetworks in one simulation model
- **Master-slave splitting** :  A large integrated T&D system is split into transmission (as master) and (several to many) distribution systems (as slaves)for power flow  co-simulation.  The resulted subnetworks are also used for T&D dynamic co-simulation where the power flow results are used for initializing dynamic simulation.
- **Multi-Area Thevenin Equivalent (MATE)**:  For T&D dynamic co-simulation, Thevenin Equivalents  for each subnetwork are calculated and shared for calculating the boundary conditions (current flows among the subnetwork boundaries). Details of the approach can be found in the following paper:
  - M. A. Tomim, J. Mart´ı, and L. Wang, “Parallel solution of large power system networks using the multi-Area Thevenin equivalents (MATE) algorithm,” Int. J. Electr. Power Energy Syst., vol. 31, no. 9, pp. 497–503,2009. 
  - The functions in this class are corresponding to key steps of implementing the MATE approach. Notations are based on the paper above.
  - Details of its usage for T&D dynamic co-simulation can be found in the paper [Integrated transmission and distribution system power flow and dynamic simulation using mixed three-sequence/three-phase modeling](<https://ieeexplore.ieee.org/abstract/document/7782366>). We will refer to it as the *TDCoSim* paper hereafter. 
## 2. Power flow

- **TDMultiNetPowerflowAlgorithm.java**

  This class is the implementation of the T&D power flow (TDPF) algorithm with three-sequence  transmission modeling and three-phase distribution system model. It relies on `SequenceNetworkSolver.java` for the negative and zero-sequence network solution for the transmisson system. Details of the algorithm can be found in this paper [Integrated transmission and distribution system power flow and dynamic simulation using mixed three-sequence/three-phase modeling](<https://ieeexplore.ieee.org/abstract/document/7782366>)

  The three-phase power flow and dynamic simulation algorithms for distribution systems are developed in the ` ipss.plugin.3phase ` module 

- **TposSeqD3PhaseMultiNetPowerflowAlgorithm.java**

  This class is another implementation of the T&D power flow (TDPF) algorithm. The main difference from `TDMultiNetPowerflowAlgorithm.java` is that in this class the transmission is modeled by positive-sequence only. This mainly serves as a comparison with the three-sequence/three-phase TDPF algorithm in `TDMultiNetPowerflowAlgorithm.java`,  and it is mainly created for a comparison study in the paper [A comparative study of interface techniques for transmission and distribution dynamic co-simulation](https://ieeexplore.ieee.org/abstract/document/8586046/)

- **SequenceNetworkSolver.java**

  This class is for solving the negative- and zero-sequence network (i.e., [**I**seq]=[**Y**seq]*[**V**seq]) for the transmission system during both power flow and dynamic simulation.

## 3. Dynamic simulation

## 3.1 Classes required for a complete T&D dynamic co-simulation

- Power flow algorithm
- Dynamic simulation algorithm
  - mainly for configuration and setting purpose
- Dynamic simulation solver
  - solving the network solution and integration steps 
- Simulation helper
  - for implementing the key steps of the Multi-Area Thevenin Equivalent (MATE) approach: 
- Network equivalent
  - a class for storing the Thevenin network equivalent information 
- Dynamic event handler/processer
  - for handling events such as faults and applying necessary changes to the network during dynamic simulation 
- State Monitor
  - for caching the system states or observations 

**NOTE: TestTnD_IEEE9_8BusFeeder.java under org.interpss.multiNet.test.trans_dist package provides a concrete example of demonstrating the usage of all these classes.**

## 3.2 Main steps for T&D dynamic co-simulation

**NOTE: _test_IEEE9_8Busfeeder_dynSim()_ in the _TestTnD_IEEE9_8BusFeeder.java_ under org.interpss.multiNet.test.trans_dist package provides a concrete example of demonstrating these steps**

1. import transmission network data, in the format of PSS/E, IEEE or PSD-BPA
2. create the distribution systems to replace the original loads at some selected transmission buses
3. split the T&D networks into subnetworks (In the future, we will support creation of distribution systems as subnetworks, which will make this step unnecessary)
4. perform T&D power flow using TDMultiNetPowerflowAlgorithm 
5. create instances of MultiNet3Seq3PhDStabHelper and DynamicSimuAlgorithm, and config the DynamicSimuAlgorithm.
6. create an instance( or instances) of dynamic event and add to the network
7. create an instance of StateMonitor for caching/storing the states and monitored system variables , and set it as SimuOutputHandler.
8. create an instance of DStabSolver, e.g., new MultiNet3Ph3SeqDStabSolverImpl(), for the DynamicSimuAlgorithm
9. create an instance of dynamic event handler for the DynamicSimuAlgorithm
10. Initialize dynamic network and components
11. perform dynamic co-simulation step by step (running dstabAlgo.solveDEqnStep())
12. save the simulation results

## 3.3 Main classes

### 1) Basic Multi-Network dynamic co-simulation

**_Classes  below are for positive-sequence multi-subnetwork dynamic co-simulation, and they are based on the basic MATE implementation with uniform positive-sequence (single-phase) modeling._** They can provide users a good understanding of the implementation of MATE approach for dynamic simulation. 

- **MultiNetDStabSolverImpl.java**

  - It extends  `DStabSolverImpl.java` to re-implement (override) some key functions (steps) for dynamic simulation, including: 1) initialization() for initializing components, network and preparing Thevenin equivalent and indidence matrix for the MATE approach, 2) solveDEqnStep() for solving beforeStep(), the network solution and integration steps, 3) beforeStep() for processing dynamic events before executing solveDEqnStep() and 4) nextStep() for integration step

- **MultiNetDStabSimuHelper.java** is a "helper" class for multiple-area/subnetwork, positive sequence based Transient Stability simulation.  This is a basic  Dynamic Stability Simulation Helper implementation for multiple networks. For  three-phase and/or three-sequence based multi-network dynamic co-simulation,please refer to the `MultiNet3Ph3SeqDstabSimuHelper.java` class. It includes several functions corresponding to the key steps of **Multi-Area Thevenin Equivalent (MATE)** approach: 

  1. _prepareInterfaceBranchBusIncidenceMatrix()_
     - This function is to prepare the incidence matrix which is used to define the relationship between interface branches and boundary buses in the MATE approach. The interface branches to boundary buses incident matrix Pk_T (Pk transpose) is the mapping from the interface branches to the boundary buses of each subNetwork;If the Thevenin equivalent viewed at the boundary buses is explicitly calculated instead of inverting the whole Yk matrix, then Pk_T can be much smaller, with Pk_T as a m by n integer matrix (m: Num. of interface branches; n: Num of boundary buses),
        For the boundary bus as the interface branch from bus, the corresponding entry is 1;
        For to bus, it becomes -1; with no connections, it will be zero  

  2. _solveSubNetworkAndUpdateEquivSource ()_
     - This is to calculate the Thenvein equivalent impedance and voltage source for each subnetwork without considering the external current injections at the boundary. This is corresponding to the steps 1 and 2 in MATE approach implementation (also see the Figure 3) in our *TDCoSim* paper

  4. _prepareBoundarySubSystemMatrix()_
     - build the boundary subsystem matrix (also known as the Thevenin impedance matrix [Zl]), corresponding to the step 2 in MATE approach implementation (also see the Figure 3) in our *TDCoSim* paper
  5. _solveBoundarySubSystem()_ 
     - Build the link subsystem by connecting the Thévenin equivalents with the link branches, and then solve it to obtain the currents of the link branches, corresponding to the step 3 in MATE approach implementation (also see the Figure 3) in our *TDCoSim* paper
  6. _solveSubNetWithBoundaryCurrInjection()_
     - solve the subNetworks with only current injection at the boundary buses and get the network solution **V**ext_injection. This is corresponding to the step 4 in MATE approach implementation (also see the Figure 3) in our *TDCoSim* paper. This is the second phase of the subNetwork solution. The first phase is completed with performing `calcSubNetworkEquivSource()` function. Then superposition method is used to obtain the final network solution result:  bus voltages **V** = **V**internal + **V**ext_injection
  7. _updateSubNetworkEquivMatrix()_
     - This is to update the  Thenvein equivalent impedance for a subnetwork if there is any change within the subnetwork, for example, when a fault is applied or cleared.  

- **MultiNetDynamicEventProcessor.java**  This is the dynamic event processor/handler for basic multi-network dynamic simulation.

### 2) T&D  Multi-Network dynamic co-simulation

**_Classes  below are for Three-sequence/Three-phase mixed modeling multi-network dynamic co-simulation_** 

- **MultiNet3Ph3SeqDStabSolverImpl.java**: it extends `MultiNetDStabSolverImpl.java` to re-use some of the codes/functions in `MultiNetDStabSolverImpl.java`.  Their main differences lies in the initialization() and nextStep(). It relies on `MultiNet3Ph3SeqDStabSimuHelper.java` to implement the MATE-based dynamic co-simulation. **This is mainly for the transmission system with mixed modeling.**
- **T3seqD3phaseMultiNetDStabSolverImpl.java**: The name implies that is this is specially developed for multiple-networks with the transmission system modeled by three-sequence and distribution systems modeled by three-phase. **This is mainly for the _integrated transmission and distribution system_ with mixed modeling. ** The `TposseqD3phaseMultiNetDStabSolverImpl.java` class is mainly created for a comparison study in the paper [A comparative study of interface techniques for transmission and distribution dynamic co-simulation](https://ieeexplore.ieee.org/abstract/document/8586046/)
- **MultiNet3Ph3SeqDStabSimuHelper.java**: The main functions are the same as  `MultiNetDStabSimuHelper.java` introduced above.    The differences are extending the positive-sequence to three-sequence/three-phase mixed modeling. That is, this is for three-sequence/three-phase mixed modeling as `MultiNetDStabSimuHelper.java` is for positive-sequence modeling.
- **MultiNet3Ph3SeqDynEventProcessor.java** This is  the dynamic event processor/handler for Three-sequence/Three-phase mixed modeling multi-network dynamic co-simulation. 
  - whereas `MultiNet3PhPosSeqDynEventProcessor.java` is for handling events in `TposseqD3phaseMultiNetDStabSolverImpl.java`

- Supportive classes
  - **SubNetworkProcessor.java**
    - Split subnetwork based on i) custom configuration ii) system zone/area information
  - **NetworkEquivalent.java**
    - a class for storing the Thevenin network equivalent information 
  - **NetworkEquivUtil.java**
    - A utility class for defining commonly used functions for calculating equivalents used in the MATE approach 

## 4. Test cases

**Unit test suite: ** MultiSubNetTestSuite.java under _org.interpss.multiNet.test_ package

**Functional test cases:** test cases under _org.interpss.multiNet.test_ package

- 



**T&D co-Simulation** : test cases under _org.interpss.multiNet.test.trans_dist_ package



- There is an error with TestTnD_IEEE9_13busFeeder.java test cases


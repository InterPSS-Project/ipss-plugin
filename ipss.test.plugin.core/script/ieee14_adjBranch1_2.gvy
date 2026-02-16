fromBusId = "Bus1";
toBusId = "Bus2";
circuitId = "1";
r = 0.02;
x = 0.06;

 branch = aclfnet.getBranch(fromBusId, toBusId, circuitId); 
 branch.status = false;
 branch.z = new Complex(r, x);
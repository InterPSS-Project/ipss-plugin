busId = "Bus14";
loadId = "Bus14-L1";
loadP = 0.18;
loadQ = 0.07;

bus = aclfnet.getBus(busId);
load = bus.getContributeLoad(loadId);
load.loadCP = new Complex(loadP, loadQ);
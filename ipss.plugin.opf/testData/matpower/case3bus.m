function mpc = case3bus
%CASE9    Power flow data for 9 bus, 3 generator case.
%   Please see CASEFORMAT for details on the case file format.
%
%   Based on data from Joe H. Chow's book, p. 70.

%   MATPOWER
%   $Id: case9.m,v 1.11 2010/03/10 18:08:14 ray Exp $

%% MATPOWER Case Format : Version 2
mpc.version = '2';

%%-----  Power Flow Data  -----%%
%% system MVA base
mpc.baseMVA = 100;

%% bus data
%	bus_i	type	Pd	Qd	Gs	Bs	area	Vm	Va	baseKV	zone	Vmax	Vmin
mpc.bus = [
	1	3	0	0	0	0	1	1	0	10	1	1.1	0.9;
	2	2	0	0	0	0	1	1	0	10	1	1.1	0.9;
	3	1	150	0	0	0	1	1	0	10	1	1.1	0.9;	
];

%% generator data
%	bus	Pg	Qg	Qmax	Qmin	Vg	mBase	status	Pmax	Pmin	Pc1	Pc2	Qc1min	Qc1max	Qc2min	Qc2max	ramp_agc	ramp_10	ramp_30	ramp_q	apf
mpc.gen = [
	1	0	0	0	0	1	100	1	200	20	0	0	0	0	0	0	0	0	0	0	0;
	2	30	0	0	0	1	100	1	150	10	0	0	0	0	0	0	0	0	0	0	0;	
];

%% branch data
%	fbus	tbus	r	x	b	rateA	rateB	rateC	ratio	angle	status	angmin	angmax
mpc.branch = [
	1	2	0	0.2	0	30	30	30	0	0	1	-360	360;
	1	3	0	0.2	0	150	150	150	0	0	1	-360	360;
	2	3	0	0.2	0	150	150	150	0	0	1	-360	360;	
];

%%-----  OPF Data  -----%%
%% area data
%	area	refbus
mpc.areas = [
	1	5;
];

%% generator cost data
%	1	startup	shutdown	n	x1	y1	...	xn	yn
%	2	startup	shutdown	n	c(n-1)	...	c0
mpc.gencost = [
	1	1500	0	2	20	200	200   2000;
	1	2000	0	2	10	120	150  1800;	
];

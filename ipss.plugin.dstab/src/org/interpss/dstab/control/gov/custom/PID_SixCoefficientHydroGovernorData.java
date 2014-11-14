 /*
  * @(#)IeeeST2GovernorData.java   
  *
  * Copyright (C) 2006 www.interpss.org
  *
  * This program is free software; you can redistribute it and/or
  * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
  * as published by the Free Software Foundation; either version 2.1
  * of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * @Author XuYH
  * @Version 1.0
  * @Date 09/24/2014
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.dstab.control.gov.custom;




import com.interpss.dstab.controller.AbstractGovernor;
import com.interpss.dstab.mach.Machine;

/**
 * 
 * @author Wenlong Zhu,  z.huw@foxmail.com
 *
 */

public class PID_SixCoefficientHydroGovernorData {
	public PID_SixCoefficientHydroGovernorData() {}
	
	private int    optMode = AbstractGovernor.DroopMode;
	/***Hydro Gov data******/
	private double wf=1;
	private double Kp=1.5;
	private double Ki=0.5;
	private double Kd=0.2;
	private double T1v=0.01;
	private double bp=0.04;
	private double K0=1;
	private double Tyb=0.02;
	private double Ty=0.2;
	
	/*****Hydro turbine data*****/
	private double ey=1;
	private double ex=0;
	private double eh=1.5;
	private double eqy=1.0;
	private double eqx=0;
	private double eqh=0.5;
	private double Tw=0.5;
	private String id = "1";
	private String name = "PID_SixCoefficientGovernor";
	
	/*******缁欐ā鍨嬪彉閲忚祴鍊�********/
	
	/*
	public void addgov(Machine machine){
		GovModel.addPID_SixCoefficientGovernor(id, name, name, machine,wf, Kp, Ki, Kd, T1v, bp, K0, Tyb, Ty, ey, ex, eh,eqy,eqx,eqh,Tw);
	}
    */
	public int getOptMode() {
		return optMode;
	}

	public void setOptMode(int optMode) {
		this.optMode = optMode;
	}

	public double getKp() {
		return Kp;
	}

	public void setKp(double kp) {
		Kp = kp;
	}

	public double getKi() {
		return Ki;
	}

	public void setKi(double ki) {
		Ki = ki;
	}

	public double getKd() {
		return Kd;
	}

	public void setKd(double kd) {
		Kd = kd;
	}

	public double getT1v() {
		return T1v;
	}

	public void setT1v(double t1v) {
		T1v = t1v;
	}

	public double getBp() {
		return bp;
	}

	public void setBp(double bp) {
		this.bp = bp;
	}

	public double getK0() {
		return K0;
	}

	public void setK0(double k0) {
		K0 = k0;
	}

	public double getTyb() {
		return Tyb;
	}

	public void setTyb(double tyb) {
		Tyb = tyb;
	}

	public double getTy() {
		return Ty;
	}

	public void setTy(double ty) {
		Ty = ty;
	}

	public double getEy() {
		return ey;
	}

	public void setEy(double ey) {
		this.ey = ey;
	}

	public double getEx() {
		return ex;
	}

	public void setEx(double ex) {
		this.ex = ex;
	}

	public double getEh() {
		return eh;
	}

	public void setEh(double eh) {
		this.eh = eh;
	}

	public double getEqy() {
		return eqy;
	}

	public void setEqy(double eqy) {
		this.eqy = eqy;
	}

	public double getEqx() {
		return eqx;
	}

	public void setEqx(double eqx) {
		this.eqx = eqx;
	}

	public double getEqh() {
		return eqh;
	}

	public void setEqh(double eqh) {
		this.eqh = eqh;
	}

	public double getTw() {
		return Tw;
	}

	public void setTw(double tw) {
		Tw = tw;
	}

	public double getWf() {
		return wf;
	}

	public void setWf(double wf) {
		this.wf = wf;
	}

} // SimpleExcAdapter

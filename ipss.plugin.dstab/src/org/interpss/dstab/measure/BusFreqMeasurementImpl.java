/*
 * @(#)BusFreqMeasurementImpl.java   
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
 * @Author Mike Zhou
 * @Version 1.0
 * @Date 7/30/2007
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.dstab.measure;

import java.util.StringTokenizer;

import org.interpss.dstab.control.cml.block.DelayControlBlock;
import org.interpss.dstab.control.cml.block.WashoutControlBlock;

import com.interpss.common.datatype.Constants;
import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.dstab.controller.block.adapt.CMLControlBlock1stOrderAdapter;

/**
 * An implementation of bus frequency measurement device. This also serves as an
 * exmple for non-CML controller implementation.
 * 
 * @author mzhou
 *
 */
public class BusFreqMeasurementImpl extends CMLControlBlock1stOrderAdapter {
	private static final long serialVersionUID = 1;

	private double baseFreq = 0.0;
	private double angle0 = 0.0;
	private double tf = 0.0;
	private double tw = 0.0;

	private WashoutControlBlock washoutBlock = null;
	private DelayControlBlock delayBlock = null;

	/**
	 * constructor
	 */
	public BusFreqMeasurementImpl() {
		this.tf = 0.01;
		this.tw = 0.01;
	}

	/**
	 * constructor
	 * 
	 * @param tf
	 * @param tw
	 */
	public BusFreqMeasurementImpl(double tf, double tw) {
		this.tf = tf;
		this.tw = tw;
	}

	/**
	 * init the dynamic device state variable
	 * 
	 * @param u0 bus angle at time = 0.0, in Rad
	 */
	@Override public boolean initStateU0(double u0) {
		// this.baseFreq has to be set by calling the setParameter method
		double kWashout = 1.0 / (2.0 * Math.PI * this.baseFreq * tf); // 1.0 / 2*Pai*f0 / Tf
		this.washoutBlock = new WashoutControlBlock(kWashout, tf);
		this.delayBlock = new DelayControlBlock(1.0, tw);

		this.angle0 = u0;
		this.washoutBlock.initStateU0(0.0);
		this.delayBlock.initStateU0(1.0);

		return true;
	}

	@Override public void eulerStep1(double u, double dt) {
		double u2 = 1.0 + this.washoutBlock.getY();
		this.delayBlock.eulerStep1(u2, dt);

		double u1 = u - this.angle0;
		this.washoutBlock.eulerStep1(u1, dt);
	}

	@Override
	public void eulerStep2(double u, double dt) {
		double u2 = 1.0 + this.washoutBlock.getY();
		this.delayBlock.eulerStep2(u2, dt);

		double u1 = u - this.angle0;
		this.washoutBlock.eulerStep2(u1, dt);
	}

	@Override public double getY() {
		return this.delayBlock.getY();
	}

	/**
	 * Set certain parameter
	 * 
	 * @param name parameter name
	 * @param value parameter value
	 */
	@Override public void setParameter(String name, double value) {
		if (name.equals(Constants.Token_NetBaseFreq))
			this.baseFreq = value;
	}

	@Override public String toString() {
		String str = "tf, tw: " + tf + ", " + tw;
		return str;
	}

	public double getTf() {
		return tf;
	}

	public void setTf(double tf) {
		this.tf = tf;
	}

	public double getTw() {
		return tw;
	}

	public void setTw(double tw) {
		this.tw = tw;
	}

	/* To make this controller ready for Grid computing, we need to implement 
	 * 		1) Default Constructor
	 * 		2) serialize() and 
	 * 		3) de-serialize().
	 */

	@Override public String serialize() {
		String name = this.getClass().getName();
		return name + "|" + tf + "," + tw;
	}

	@Override public Object deserialize(String str) throws InterpssRuntimeException {
		String classname = str.substring(0, str.indexOf('|'));
		if (!classname.equals(this.getClass().getName())) {
			throw new InterpssRuntimeException("Programming error, deserialize() of "
							+ this.getClass().getName());
		}
		String params = str.substring(str.indexOf('|') + 1);
		StringTokenizer st = new StringTokenizer(params, ",");
		this.tf = new Double(st.nextToken()).doubleValue();
		this.tw = new Double(st.nextToken()).doubleValue();
		return this;
	}
}

 /*
  * @(#)BaseObjectDSL.java   
  *
  * Copyright (C) 2006-2011 www.interpss.com
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
  * @Date 04/15/2009
  * 
  *   Revision History
  *   ================
  *
  */
package org.interpss.pssl.simu.net;

import com.interpss.CoreObjectFactory;
import com.interpss.core.net.Area;
import com.interpss.core.net.Network;
import com.interpss.core.net.NetworkedElement;
import com.interpss.core.net.Owner;
import com.interpss.core.net.Zone;

/**
 * base class for defining InterPSS Network PSSL (DSL) for creating network
 * and associated Bus/Branch objects
 * 
 * @author mzhou
 *
 * @param <TObj> for defining Bus or Branch class
 * @param <TNet> for defining Network class
 * @param <TBaseDSL> for defining base DSL class
 */
class BaseNetDSL<TObj extends NetworkedElement,       // for Bus or Branch class 
                    TNet extends Network<?,?>,                // for Network class
                    TBaseDSL> {                          // for base DSL class
	protected TNet net = null;
	private TObj obj = null;
		
	/**
	 * default constructor
	 */
	public BaseNetDSL() {
	}

	/**
	 * constructor
	 * 
	 * @param net
	 */
	public BaseNetDSL(TNet net) {
		this.net = net;
	}
	
	/**
	 * get the network object
	 * 
	 * @return
	 */
	TNet getNet() { return this.net; }
		
	/**
	 * get the status field
	 * 
	 * @param b
	 * @return
	 */
  	public TBaseDSL status(boolean b) {	return setStatus(b); }
	/**
	 * get the status field
	 * 
	 * @param b
	 * @return
	 */
	@SuppressWarnings(value="unchecked")
  	public TBaseDSL setStatus(boolean b) {	
		this.obj.setStatus(b); return (TBaseDSL)this; }
	
  	/**
  	 * get the name field 
  	 * @param s
  	 * @return
  	 */
  	public TBaseDSL name(String s) { return setName(s);}
  	/**
  	 * get the name field 
  	 * @param s
  	 * @return
  	 */
	@SuppressWarnings(value="unchecked")
  	public TBaseDSL setName(String s) { 
		this.obj.setName(s); return (TBaseDSL)this; }
	
  	/**
  	 * get the description field
  	 * 
  	 * @param s
  	 * @return
  	 */
  	public TBaseDSL description(String s) { return setDesc(s); }
  	/**
  	 * get the description field
  	 * 
  	 * @param s
  	 * @return
  	 */
	@SuppressWarnings(value="unchecked")
  	public TBaseDSL setDesc(String s) { 
		this.obj.setDesc(s); return (TBaseDSL)this; }
	
  	/**
  	 * get the area number fields
  	 * 
  	 * @param n
  	 * @return
  	 */
  	public TBaseDSL areaNumber(int n) { return setAreaNumber(n); }
  	/**
  	 * get the area number fields
  	 * 
  	 * @param n
  	 * @return
  	 */
	@SuppressWarnings(value="unchecked")
  	public TBaseDSL setAreaNumber(int n) { 
  	   	Area area = CoreObjectFactory.createArea(n, net);
  	   	this.obj.setArea(area); return (TBaseDSL)this; }
	
  	/**
  	 * get the zone number field
  	 * 
  	 * @param n
  	 * @return
  	 */
  	public TBaseDSL zoneNumber(int n) { return setZoneNumber(n); }
  	/**
  	 * get the zone number field
  	 * 
  	 * @param n
  	 * @return
  	 */
	@SuppressWarnings(value="unchecked")
  	public TBaseDSL setZoneNumber(int n) { 
  	   	Zone zone = CoreObjectFactory.createZone(n, net);
  	   	this.obj.setZone(zone); return (TBaseDSL)this; }
	
  	/**
  	 * get the ownerId field
  	 * 
  	 * @param id
  	 * @return
  	 */
  	public TBaseDSL ownerId(String id) { return setOwnerId(id); }
  	/**
  	 * get the ownerId field
  	 * 
  	 * @param id
  	 * @return
  	 */
	@SuppressWarnings(value="unchecked")
  	public TBaseDSL setOwnerId(String id) { 
  	   	Owner owner = CoreObjectFactory.createOwner(id, net);
  	   	this.obj.setOwner(owner); return (TBaseDSL)this; }
  
  	/**
  	 * get the Bus/Branch object
  	 * 
  	 * @return
  	 */
  	public TObj getObject() {return this.obj; }
  	/**
  	 * set the Bus/Branch object
  	 * 
  	 * @param obj
  	 */
	void setObject(TObj obj) { this.obj = obj; }
}

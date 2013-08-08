/*
  * @(#)FieldObjectFactory.java   
  *
  * Copyright (C) 2007 www.interpss.org
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
  * @Date 02/05/2007
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.dstab.control.cml;

import static com.interpss.dstab.controller.block.ICMLStaticBlock.StaticBlockType.NoLimit;

import java.lang.reflect.Field;
import java.util.StringTokenizer;

import org.interpss.dstab.control.cml.block.DelayControlBlock;
import org.interpss.dstab.control.cml.block.FilterControlBlock;
import org.interpss.dstab.control.cml.block.FilterNthOrderBlock;
import org.interpss.dstab.control.cml.block.GainBlock;
import org.interpss.dstab.control.cml.block.IntegrationControlBlock;
import org.interpss.dstab.control.cml.block.PIControlBlock;
import org.interpss.dstab.control.cml.block.TFunc2ndOrderBlock;
import org.interpss.dstab.control.cml.block.WashoutControlBlock;
import org.interpss.dstab.control.cml.func.FexFunction;
import org.interpss.dstab.control.cml.func.GainExpFunction;
import org.interpss.dstab.control.cml.func.GainFunction;
import org.interpss.dstab.control.cml.func.HighValueExpFunction;
import org.interpss.dstab.control.cml.func.HighValueFunction;
import org.interpss.dstab.control.cml.func.LookupTableFunction;
import org.interpss.dstab.control.cml.func.LowValueExpFunction;
import org.interpss.dstab.control.cml.func.LowValueFunction;
import org.interpss.dstab.control.cml.func.PowerFunction;
import org.interpss.dstab.control.cml.func.SeFunction;
import org.interpss.dstab.control.cml.func.SwitchFunction;
import org.interpss.dstab.control.cml.func.VthevFunction;
import org.interpss.numeric.datatype.Point;

import com.interpss.common.CommonObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.func.ILookupTable;
import com.interpss.common.util.StringUtil;
import com.interpss.dstab.controller.ICMLFieldObjectFactory;
import com.interpss.dstab.controller.annotate.AbstractAnnotateController;
import com.interpss.dstab.controller.annotate.util.CMLSymbolMapper;
import com.interpss.dstab.controller.block.ICMLControlBlock;
import com.interpss.dstab.controller.block.ICMLFunction;
import com.interpss.dstab.controller.block.ICMLFunctionExpression;
import com.interpss.dstab.controller.block.ICMLLookupTableFunction;
import com.interpss.dstab.controller.block.ICMLStaticBlock;
import com.interpss.dstab.controller.block.ICMLStaticBlock.StaticBlockType;
import com.interpss.dstab.datatype.ExpCalculator;

/**
 * Controller annotated field object factory to create objects based on the annotated info
 * 
 * @author mzhou
 *
 */
public class CMLFieldObjectFactory implements ICMLFieldObjectFactory {
	/**
	 * Create a control block object based on the annotation filed and parameters
	 * 
	 * @param controllor the parent controller object
	 * @param field the control block field
	 * @param parameters block parameters
	 * @return the created object
	 * @throws Exception
	 */
    public ICMLControlBlock createControlBlockField(AbstractAnnotateController controllor, Field field, String[] parameters) throws Exception {
    	if (field.getType() == DelayControlBlock.class) {
    		StaticBlockType type = CMLSymbolMapper.mapTypeStr2BlockType(parameters[0]);
    	    if (type == NoLimit) {
    	    	// format : {"type.NoLimit", "this.k", "this.t"},
    	    	double k = controllor.getDoubleField(StringUtil.getParameterName(parameters[1]));
    	    	double t = controllor.getDoubleField(StringUtil.getParameterName(parameters[2]));
    	    	return new DelayControlBlock(k, t);
    	    }
    	    else {
    	    	// format : {"type.Limit", "this.k", "this.t", "this.vmax", "this.vmin"},
    	    	double k = controllor.getDoubleField(StringUtil.getParameterName(parameters[1]));
    	    	double t = controllor.getDoubleField(StringUtil.getParameterName(parameters[2]));
    	    	String vmaxStr = parameters[3];
    	    	String vminStr = parameters[4];
    	    	if (ExpCalculator.isExpression(vmaxStr) && ExpCalculator.isExpression(vminStr)) {
        	    	return new DelayControlBlock(type, k, t, new ExpCalculator(vmaxStr), new ExpCalculator(vminStr));
    	    	}
    	    	else if (ExpCalculator.isExpression(vminStr)) {
    	    		double vmax = controllor.getDoubleField(StringUtil.getParameterName(parameters[3]));
        	    	return new DelayControlBlock(type, k, t, vmax, new ExpCalculator(vminStr));
    	    	}
    	    	else if (ExpCalculator.isExpression(vmaxStr)) {
        	    	double vmin = controllor.getDoubleField(StringUtil.getParameterName(parameters[4]));
        	    	return new DelayControlBlock(type, k, t, new ExpCalculator(vmaxStr), vmin);
    	    	}
    	    	else {
    	    		double vmax = controllor.getDoubleField(StringUtil.getParameterName(parameters[3]));
        	    	double vmin = controllor.getDoubleField(StringUtil.getParameterName(parameters[4]));
        	    	return new DelayControlBlock(type, k, t, vmax, vmin);
    	    	}
    	    }
    	}
    	else if (field.getType() == FilterControlBlock.class) {
    		StaticBlockType type = CMLSymbolMapper.mapTypeStr2BlockType(parameters[0]);
    	    if (type == NoLimit) {
    	    	// format : {"type.NoLimit", "this.k", "this.t1", "this.t2"},
    	    	double k = controllor.getDoubleField(StringUtil.getParameterName(parameters[1]));
    	    	double t1 = controllor.getDoubleField(StringUtil.getParameterName(parameters[2]));
    	    	double t2 = controllor.getDoubleField(StringUtil.getParameterName(parameters[3]));
    	    	return new FilterControlBlock(k, t1, t2);
    	    }
    	    else {
    	    	// format : {"type.Limit", "this.k", "this.t1", "this.t2", "this.vmax", "this.vmin"},
    	    	double k = controllor.getDoubleField(StringUtil.getParameterName(parameters[1]));
    	    	double t1 = controllor.getDoubleField(StringUtil.getParameterName(parameters[2]));
    	    	double t2 = controllor.getDoubleField(StringUtil.getParameterName(parameters[3]));
    	    	double vmax = controllor.getDoubleField(StringUtil.getParameterName(parameters[4]));
    	    	double vmin = controllor.getDoubleField(StringUtil.getParameterName(parameters[5]));
    	    	return new FilterControlBlock(type, k, t1, t2, vmax, vmin);
    	    }
    	}
    	else if (field.getType() == WashoutControlBlock.class) {
   	    	// format : {"this.k", "this.t"},
   	    	double k = controllor.getDoubleField(StringUtil.getParameterName(parameters[1]));
   	    	double t = controllor.getDoubleField(StringUtil.getParameterName(parameters[2]));
   	    	return new WashoutControlBlock(k, t);
    	}
    	else if (field.getType() == PIControlBlock.class) {
    		StaticBlockType type = CMLSymbolMapper.mapTypeStr2BlockType(parameters[0]);
    	    if (type == NoLimit) {
    	    	// format : {"type.NoLimit", "this.k", "this.t1", "this.t2"},
    	    	double kp = controllor.getDoubleField(StringUtil.getParameterName(parameters[1]));
    	    	double ki = controllor.getDoubleField(StringUtil.getParameterName(parameters[2]));
    	    	return new PIControlBlock(kp, ki);
    	    }
    	    else {
    	    	// format : {"type.Limit", "this.k", "this.t1", "this.t2", "this.vmax", "this.vmin"},
    	    	double kp = controllor.getDoubleField(StringUtil.getParameterName(parameters[1]));
    	    	double ki = controllor.getDoubleField(StringUtil.getParameterName(parameters[2]));
    	    	double max = controllor.getDoubleField(StringUtil.getParameterName(parameters[3]));
    	    	double min = controllor.getDoubleField(StringUtil.getParameterName(parameters[4]));
    	    	return new PIControlBlock(type, kp, ki, max, min);
    	    }
    	}
    	else if (field.getType() == IntegrationControlBlock.class) {
    		StaticBlockType type = CMLSymbolMapper.mapTypeStr2BlockType(parameters[0]);
    	    if (type == NoLimit) {
    	    	// format : {"type.NoLimit", "this.k"},
    	    	double k = controllor.getDoubleField(StringUtil.getParameterName(parameters[1]));
    	    	return new IntegrationControlBlock(k);
    	    }
    	    else {
    	    	// format : {"type.Limit", "this.k", "this.max", "this.min"},
    	    	double k = controllor.getDoubleField(StringUtil.getParameterName(parameters[1]));
    	    	double max = controllor.getDoubleField(StringUtil.getParameterName(parameters[2]));
    	    	double min = controllor.getDoubleField(StringUtil.getParameterName(parameters[3]));
    	    	return new IntegrationControlBlock(type, k, max, min);
    	    }
    	}
    	else if (field.getType() == TFunc2ndOrderBlock.class) {
	    	// format : {"type.NoLimit", "this.k", "this.a", "this.b"},
	    	// format : {"type.Limit", "this.k", "this.a", "this.b", "this.max", this.min"},
    		StaticBlockType type = CMLSymbolMapper.mapTypeStr2BlockType(parameters[0]);
	    	double k = controllor.getDoubleField(StringUtil.getParameterName(parameters[1]));
	    	double a = controllor.getDoubleField(StringUtil.getParameterName(parameters[2]));
	    	double b = controllor.getDoubleField(StringUtil.getParameterName(parameters[3]));
    	    if (type == NoLimit) 
	    		return new TFunc2ndOrderBlock(k, a, b);
	    	else {
		    	double max = controllor.getDoubleField(StringUtil.getParameterName(parameters[4]));
		    	double min = controllor.getDoubleField(StringUtil.getParameterName(parameters[5]));
	    		return new TFunc2ndOrderBlock(k, a, b, max, min);
	    	}
    	}
    	else if (field.getType() == FilterNthOrderBlock.class) {
	    	// format : {"this.t1", "this.t2", "this.m", "this.n"},
	    	double t1 = controllor.getDoubleField(StringUtil.getParameterName(parameters[0]));
	    	double t2 = controllor.getDoubleField(StringUtil.getParameterName(parameters[1]));
	    	int m = controllor.getIntField(StringUtil.getParameterName(parameters[2]));
	    	int n = controllor.getIntField(StringUtil.getParameterName(parameters[3]));
	    	return new 	FilterNthOrderBlock(t1, t2, m, n );
    	}
    	return null;
    }
    
	/**
	 * Create a static block object based on the annotation filed and parameters
	 * 
	 * @param controllor the parent controller object
	 * @param field the static block field
	 * @param parameters block parameters
	 * @return the created object
	 * @throws Exception
	 */
    public ICMLStaticBlock createStaticBlockField(AbstractAnnotateController controllor, Field field, String[] parameters) throws InterpssException {
    	if (field.getType() == GainBlock.class) {
    		StaticBlockType type = CMLSymbolMapper.mapTypeStr2BlockType(parameters[0]);
    	    if (type == NoLimit) {
    	    	// format : {"type.NoLimit", "this.k"},
    	    	double k = controllor.getDoubleField(StringUtil.getParameterName(parameters[1]));
    	    	return new GainBlock(k);
    	    }
    	    else {
    	    	// format : {"type.Limit", "this.k", "this.max", "this.min"},
    	    	double k = controllor.getDoubleField(StringUtil.getParameterName(parameters[1]));
    	    	double max = controllor.getDoubleField(StringUtil.getParameterName(parameters[2]));
    	    	double min = controllor.getDoubleField(StringUtil.getParameterName(parameters[3]));
    	    	return new GainBlock(type, k, max, min);
    	    }
    	}
    	return null;   
    }    

	/**
	 * Create a Function object based on the annotation filed and parameters
	 * 
	 * @param controllor the parent controller object
	 * @param field the Function field
	 * @param parameters block parameters
	 * @return the created object
	 * @throws Exception
	 */
    public ICMLFunction createFunctionField(AbstractAnnotateController controllor, Field field, String[] parameters) throws InterpssException {
    	if (field.getType() == SeFunction.class) {
	    	// format : {"this.e1", "this.se_e1", "this.e2", "this.se_e2"},
    	    double e1 = controllor.getDoubleField(StringUtil.getParameterName(parameters[0]));
    	    double se_e1 = controllor.getDoubleField(StringUtil.getParameterName(parameters[1]));
    	    double e2 = controllor.getDoubleField(StringUtil.getParameterName(parameters[2]));
    	    double se_e2 = controllor.getDoubleField(StringUtil.getParameterName(parameters[3]));
    	    return new SeFunction(e1, se_e1, e2, se_e2);
    	}
    	else if (field.getType() == GainFunction.class) {
	    	// format : {"this.k"},
    	    double k = controllor.getDoubleField(StringUtil.getParameterName(parameters[0]));
    	    return new GainFunction(k);
    	}
    	else if (field.getType() == SwitchFunction.class) {
	    	// format : {"this.e"},
    	    double e = controllor.getDoubleField(StringUtil.getParameterName(parameters[0]));
    	    return new SwitchFunction(e);
    	}
    	else if (field.getType() == PowerFunction.class) {
	    	// format : {"this.k"},
    		int k = controllor.getIntField(StringUtil.getParameterName(parameters[0]));
    		if ( k < 0)
    			throw new InterpssException("Power function, n has to be >= 0");
    	    return new PowerFunction(k);
    	}
    	else if (field.getType() == HighValueFunction.class) {
	    	// format : no parameter
    	    return new HighValueFunction();
    	}
    	else if (field.getType() == LowValueFunction.class) {
	    	// format : no parameter
    	    return new LowValueFunction();
    	}
    	else if (field.getType() == FexFunction.class) {
	    	// format : no parameter
    	    return new FexFunction();
    	}
    	else if (field.getType() == VthevFunction.class) {
	    	// format : {"this.kp", "this.ki"},
    	    double kp = controllor.getDoubleField(StringUtil.getParameterName(parameters[0]));
    	    double ki = controllor.getDoubleField(StringUtil.getParameterName(parameters[1]));
    	    return new VthevFunction(kp, ki);
    	}
    	return null;   
    }    

    /**
	 * Create a FunctionExpression object based on the annotation filed and parameters
	 * 
	 * @param controllor the parent controller object
	 * @param field the FunctionExpression field
	 * @param parameters block parameters
	 * @return the created object
	 * @throws Exception
	 */
    public ICMLFunctionExpression createFunctionExpressionField(AbstractAnnotateController controllor, Field field, String[] parameters) throws InterpssException {
    	if (field.getType() == LowValueExpFunction.class) {
	    	// format : no parameter
    	    return new LowValueExpFunction();
    	}
    	else if (field.getType() == HighValueExpFunction.class) {
	    	// format : no parameter
    	    return new HighValueExpFunction();
    	}
    	else if (field.getType() == GainExpFunction.class) {
	    	// format : {"this.k"},
    	    double k = controllor.getDoubleField(StringUtil.getParameterName(parameters[0]));
    	    return new GainExpFunction(k);
    	}
    	return null;   
    }    
    
    /**
	 * Create a FunctionExpression object based on the annotation filed and parameters
	 * 
	 * @param controllor the parent controller object
	 * @param field the FunctionExpression field
	 * @param lookupTableType block parameters
	 * @param dataPoints block parameters
	 * @return the created object
	 * @throws Exception 
	 */
    public ICMLLookupTableFunction createLookupTableFunctionField(AbstractAnnotateController controllor, Field field, 
    		                             ILookupTable.Type lookupTabletype, String[] dataPoints) throws InterpssException {
    	if (field.getType() == LookupTableFunction.class) {
    		ILookupTable table = CommonObjectFactory.createLookupTable(lookupTabletype);
    		// dataPoints format : {"1.0, 5.0", "2.0, 6.0", "3.0, 5.5"}
    		for (String str : dataPoints) {
    			StringTokenizer st = new StringTokenizer(str, ",");
    			double x = new Double(st.nextToken()).doubleValue();
    			double y = new Double(st.nextToken()).doubleValue();
    			Point point = new Point(x, y);
    			table.addPoint(point);
    		}
    		ICMLLookupTableFunction func = new LookupTableFunction(table);
    		return func;
    	}
    	return null;   
    }      
}

 /*
  * @(#)IeeeSteamTDSRGovernor.java
  * 
  * Generally referred to as IEEEG1. IEEE 1981 Type 1 Speed-Governing Model
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
  * @Date 09/15/2006
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.dstab.control.gov.ieee.steamTCDR;

import java.lang.reflect.Field;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnnotateGovernor;
import com.interpss.dstab.controller.cml.annotate.util.AnControllerInitializer;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.field.block.GainBlock;
import com.interpss.dstab.controller.cml.field.block.IntegrationControlBlock;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;
import org.interpss.dstab.control.util.AsymmetricDeadbandBlock;
import org.interpss.dstab.control.util.IntegrationStepAware;
import org.interpss.numeric.datatype.Unit.UnitType;

@AnController(
		   input="mach.speed - 1.0",
		   output="this.ratingScale*this.fvhp*this.chDelayBlock.y + this.ratingScale*this.fhp*this.rh1DelayBlock.y + this.ratingScale*this.fip*this.rh2DelayBlock.y + this.ratingScale*this.flp*this.coDelayBlock.y",
		   refPoint="this.gainBlock.u0 + this.filterBlock.y + this.intBlock.y",
		   display= {}		)
public class IeeeSteamTCDRGovernor extends AnnotateGovernor implements IntegrationStepAware {
   public double fvhp = 0.1, fhp = 0.1, fip = 0.3, flp = 0.5;
   public double ratingScale = 1.0, invRatingScale = 1.0;
   private double integrationStep;
   private double minimumTimeConstantMultiplier = 1.0;

   @Override
   public void configureIntegrationStep(double timeStepSec) {
       configureIntegrationStep(timeStepSec, 1.0);
   }

   public void configureIntegrationStep(double timeStepSec, double multiplier) {
       this.integrationStep = timeStepSec;
       this.minimumTimeConstantMultiplier = multiplier;
   }

    @AnControllerField(type=CMLFieldEnum.StaticBlock, input="mach.speed - 1.0",
            y0="0.0")
    public AsymmetricDeadbandBlock speedDeadbandBlock = new AsymmetricDeadbandBlock();

	public double k = 10.0, t1 = 0.5, t2 = 0.1;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.speedDeadbandBlock.y",
            parameter={"type.NoLimit", "this.k", "this.t2", "this.t1"},
            y0 = "this.refPoint - this.gainBlock.y - this.intBlock.y" )
    public FilterControlBlock filterBlock;
	
    public double k3 = 1.0 /* 1.0/t3 */, pup = 1.2, pdown = 0.0;
    @AnControllerField(
            type= CMLFieldEnum.StaticBlock,
            input="this.refPoint - this.filterBlock.y - this.intBlock.y",
            parameter={"type.Limit", "this.k3", "this.pup", "this.pdown"},
            y0="this.intBlock.u0"	)
    public GainBlock gainBlock;

    public double kint = 1.0, pmax = 10.0, pmin = 0.0;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.gainBlock.y",
            parameter={"type.Limit", "this.kint", "this.pmax", "this.pmin"},
            y0="this.coDelayBlock.u0"	)
    public IntegrationControlBlock intBlock;

    public double kch = 1.0, tch = 1.2;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.intBlock.y",
            parameter={"type.NoLimit", "this.kch", "this.tch"},
            y0="this.rh1DelayBlock.u0"	)
    public DelayControlBlock chDelayBlock;

    public double krh1 = 1.0, trh1 = 1.2;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.chDelayBlock.y",
            parameter={"type.NoLimit", "this.krh1", "this.trh1"},
            y0="this.rh2DelayBlock.u0"	)
    public DelayControlBlock rh1DelayBlock;

    public double krh2 = 1.0, trh2 = 1.2;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.rh1DelayBlock.y",
            parameter={"type.NoLimit", "this.krh2", "this.trh2"},
            y0="this.coDelayBlock.u0"	)
    public DelayControlBlock rh2DelayBlock;

    public double kco = 1.0, tco = 1.2, factor = 1.0 / (fvhp+fhp+fip+flp);
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.rh2DelayBlock.y",
            parameter={"type.NoLimit", "this.kco", "this.tco"},
            y0="this.factor*this.invRatingScale*mach.pm"	)
    public DelayControlBlock coDelayBlock;
 	
    // UI Editor panel
//    private static NBIeeeSteamTCDREditPanel _editPanel = new NBIeeeSteamTCDREditPanel();
    
    /**
     * Default Constructor
     *
     */
    public IeeeSteamTCDRGovernor() {
        this.setName("ieeeSteamTDSRGovernor");
        this.setCategory("IEEE");
    }
    
    /**
     * Constructor
     *
     * @param id excitor id
     * @param name excitor name
     */
    public IeeeSteamTCDRGovernor(String id, String name, String caty) {
        super(id, name, caty);
        // _data is defined in the parent class. However init it here is a MUST
        _data = new IeeeSteamTCDRGovernorData();
    }
    
    /**
     * Get the excitor data
     *
     * @return the data object
     */
    public IeeeSteamTCDRGovernorData getData() {
        return (IeeeSteamTCDRGovernorData)_data;
    }
    
    /**
     *  Init the controller states
     *
     *  @param msg the SessionMsg object
     */
    @Override
	public boolean initStates(BaseDStabBus<?,?> bus, Machine mach) {
        double machineMva = mach.getRating(UnitType.mVA, bus.getNetwork().getBaseKva());
        this.ratingScale = getData().getTrate() > 1.0e-9 && machineMva > 1.0e-9
                ? getData().getTrate() / machineMva : 1.0;
        this.invRatingScale = 1.0 / ratingScale;
        this.speedDeadbandBlock.setThresholds(getData().getDbH(), getData().getDbL());
        this.k = getData().getK();
        this.t1 = getData().getT1();
        this.t2 = getData().getT2();
        double t3 = correctedPositiveTimeConstant(getData().getT3());
        this.k3 = 1.0 / t3;
        this.fvhp = getData().getFvhp();
        this.fhp = getData().getFhp();
        this.fip = getData().getFip();
        this.flp = getData().getFlp();
        normalizeSingleMachineFractionsIfNeeded();
        double rawMax = Math.max(getData().getPmax(), getData().getPmin());
        double rawMin = Math.min(getData().getPmax(), getData().getPmin());
        double initialValve = mach.getPm() * invRatingScale
                / (fvhp + fhp + fip + flp);
        this.pmax = Math.max(rawMax, initialValve);
        this.pmin = Math.min(rawMin, initialValve);
        double rawOpen = getData().getPup();
        double rawClose = getData().getPdown();
        if (rawOpen < rawClose) {
            double swap = rawOpen; rawOpen = rawClose; rawClose = swap;
        }
        this.pup = rawOpen < 0.0 ? -rawOpen : rawOpen;
        this.pdown = rawClose > 0.0 ? -rawClose : rawClose;
        this.tch = correctedBypassTimeConstant(getData().getTch());
        this.trh1 = correctedBypassTimeConstant(getData().getTrh1());
        this.trh2 = correctedBypassTimeConstant(getData().getTrh2());
        this.tco = correctedBypassTimeConstant(getData().getTco());
	    this.factor = 1.0 / (this.fvhp+this.fhp+this.fip+this.flp);
        boolean initialized = super.initStates(bus, mach);
        if (initialized) bindCmlBlocks();
        return initialized;
    }

    private void bindCmlBlocks() {
        speedDeadbandBlock = (AsymmetricDeadbandBlock) AnControllerInitializer.getBlock(
                "speedDeadbandBlock", getFieldWrapperList());
        filterBlock = (FilterControlBlock) AnControllerInitializer.getBlock(
                "filterBlock", getFieldWrapperList());
        gainBlock = (GainBlock) AnControllerInitializer.getBlock(
                "gainBlock", getFieldWrapperList());
        intBlock = (IntegrationControlBlock) AnControllerInitializer.getBlock(
                "intBlock", getFieldWrapperList());
        chDelayBlock = (DelayControlBlock) AnControllerInitializer.getBlock(
                "chDelayBlock", getFieldWrapperList());
        rh1DelayBlock = (DelayControlBlock) AnControllerInitializer.getBlock(
                "rh1DelayBlock", getFieldWrapperList());
        rh2DelayBlock = (DelayControlBlock) AnControllerInitializer.getBlock(
                "rh2DelayBlock", getFieldWrapperList());
        coDelayBlock = (DelayControlBlock) AnControllerInitializer.getBlock(
                "coDelayBlock", getFieldWrapperList());
    }

    private double correctedBypassTimeConstant(double value) {
        double minimum = minimumTimeConstantMultiplier * integrationStep;
        if (value > 0.0 && value < 0.5 * minimum) return 0.0;
        if (value > 0.5 * minimum && value < minimum) return minimum;
        return value;
    }

    private double correctedPositiveTimeConstant(double value) {
        double minimum = minimumTimeConstantMultiplier * integrationStep;
        return value > 0.0 && value < minimum ? minimum : value;
    }

    private void normalizeSingleMachineFractionsIfNeeded() {
        double sum = fvhp + fhp + fip + flp;
        if (sum > 1.0) {
            fvhp /= sum;
            fhp /= sum;
            fip /= sum;
            flp /= sum;
        }
    }

    public double getValvePositionerTimeConstant() {
        return 1.0 / k3;
    }

    public double getSpeedSignal() {
        return speedDeadbandBlock.getY();
    }

    public double getGovernorSignal() {
        return filterBlock.getY();
    }

    public double getValveRate() {
        return gainBlock.getY();
    }

    public double getValvePosition() {
        return intBlock.getY();
    }

    public double getFirstStageOutput() {
        return chDelayBlock.getY();
    }

    public double getSecondStageOutput() {
        return rh1DelayBlock.getY();
    }

    public double getThirdStageOutput() {
        return rh2DelayBlock.getY();
    }

    public double getFourthStageOutput() {
        return coDelayBlock.getY();
    }

    /**
     * Get the editor panel for controller data editing
     *
     * @return the editor panel object
     */
//    @Override
//	public Object getEditPanel() {
//        _editPanel.init(this);
//        return _editPanel;
//    }
 
    @Override
	public AnController getAnController() {
    	return getClass().getAnnotation(AnController.class);  }
    @Override
	public Field getField(String fieldName) throws Exception {
    	return getClass().getField(fieldName);   }
    @Override
	public Object getFieldObject(Field field) throws Exception {
    	return field.get(this);    }

} // SimpleGovernor

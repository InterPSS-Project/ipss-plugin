 /*
  * @(#)NBIeeeSteamTDSREditPanel.java   
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

package org.interpss.dstab.control.gov.ieee.steamTDSR;

import java.util.Vector;

import org.interpss.dstab.control.base.EditHelper;
import org.interpss.ui.ICustomPluginEditor;

public class NBIeeeSteamTCDREditPanel extends javax.swing.JPanel implements ICustomPluginEditor {
	private static final long serialVersionUID = 1;

	// define data to be edited
	IeeeSteamTDSRGovernorData _data;

    /** Creates new form FaultLocDataPanel */
    public NBIeeeSteamTCDREditPanel() {
        initComponents();
        // init the field to the default values
        _data = new IeeeSteamTDSRGovernorData();
        setData2Editor("");
        
        // associate the editing fields with the verifier class defined at the end of this calss
  		DataVerifier verifier = new DataVerifier();
  	    kTextField.setInputVerifier(verifier);
  	    t1TextField.setInputVerifier(verifier);
  	    t2TextField.setInputVerifier(verifier);
  	    t3TextField.setInputVerifier(verifier);
  	    pmaxTextField.setInputVerifier(verifier);
  	    pminTextField.setInputVerifier(verifier);
  	    pupTextField.setInputVerifier(verifier);
  	    pdownTextField.setInputVerifier(verifier);
  	    tchTextField.setInputVerifier(verifier);
  	    trh1TextField.setInputVerifier(verifier);
  	    trh2TextField.setInputVerifier(verifier);
  	    tcoTextField.setInputVerifier(verifier);
  	    fvhpTextField.setInputVerifier(verifier);
  	    fhpTextField.setInputVerifier(verifier);
  	    fipTextField.setInputVerifier(verifier);
  	    flpTextField.setInputVerifier(verifier);
    }
    
    /**
     * Init the editor panel, which will be called from its parent editor
     */
	public void init(Object controller) {
		// init the data object from the bus object being edited
		_data = ((IeeeSteamTCDRGovernor)controller).getData();
	}
	
	/**
	*	Set controller data to the editor
	*
	* @return false if there is any problem
	*/
    public boolean setData2Editor(String desc) {
    	EditHelper.setDblTextFiled(kTextField, 	_data.getK(), "#0.00");
    	EditHelper.setDblTextFiled(t1TextField, 	_data.getT1(), "#0.000");
    	EditHelper.setDblTextFiled(t2TextField, 	_data.getT2(), "#0.000");
    	EditHelper.setDblTextFiled(t3TextField, 	_data.getT3(), "#0.000");
    	EditHelper.setDblTextFiled(pmaxTextField, 	_data.getPmax(), "#0.000");
    	EditHelper.setDblTextFiled(pminTextField, 	_data.getPmin(), "#0.000");
    	EditHelper.setDblTextFiled(pupTextField, 	_data.getPup(), "#0.000");
    	EditHelper.setDblTextFiled(pdownTextField, 	_data.getPdown(), "#0.000");
    	EditHelper.setDblTextFiled(tchTextField, 	_data.getTch(), "#0.000");
    	EditHelper.setDblTextFiled(trh1TextField, 	_data.getTrh1(), "#0.000");
    	EditHelper.setDblTextFiled(trh2TextField, 	_data.getTrh2(), "#0.000");
    	EditHelper.setDblTextFiled(tcoTextField, _data.getTco(), "#0.00");
    	EditHelper.setDblTextFiled(fvhpTextField, _data.getFvhp(), "#0.000");
    	EditHelper.setDblTextFiled(fhpTextField, 	_data.getFhp(), "#0.000");
    	EditHelper.setDblTextFiled(fipTextField, 	_data.getFip(), "#0.000");
    	EditHelper.setDblTextFiled(flpTextField, 	_data.getFlp(), "#0.000");

    	return true;
	}
    
	/**
	*	Save editor screen data to the controller data object
	*
	* @param errMsg error messages during the saving process.
	* @return false if there is any problem
	*/
    public boolean saveEditorData(Vector<String> errMsg) throws Exception {
    	errMsg.clear();
    	
    	EditHelper.saveDblTextField(_data, kTextField, "k", errMsg);
    	EditHelper.saveDblTextField(_data, t1TextField, "t1", errMsg);
    	EditHelper.saveDblTextField(_data, t2TextField, "t2", errMsg);
    	EditHelper.saveDblTextField(_data, t3TextField, "t3", errMsg);
    	EditHelper.saveDblTextField(_data, pmaxTextField, "pmax", errMsg);
    	EditHelper.saveDblTextField(_data, pminTextField, "pmin", errMsg);
    	EditHelper.saveDblTextField(_data, pupTextField, "pup", errMsg);
    	EditHelper.saveDblTextField(_data, pdownTextField, "pdown", errMsg);
    	EditHelper.saveDblTextField(_data, tchTextField, "tch", errMsg);
    	EditHelper.saveDblTextField(_data, trh1TextField, "trh1", errMsg);
    	EditHelper.saveDblTextField(_data, trh2TextField, "trh2", errMsg);
    	EditHelper.saveDblTextField(_data, tcoTextField, "tco", errMsg);
    	EditHelper.saveDblTextField(_data, fvhpTextField, "fvhp", errMsg);
    	EditHelper.saveDblTextField(_data, fhpTextField, "fhp", errMsg);
    	EditHelper.saveDblTextField(_data, fipTextField, "fip", errMsg);
    	EditHelper.saveDblTextField(_data, flpTextField, "flp", errMsg);

    	return errMsg.size() == 0;
	}
    
	/** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        kLabel = new javax.swing.JLabel();
        kTextField = new javax.swing.JTextField();
        t1Label = new javax.swing.JLabel();
        t1TextField = new javax.swing.JTextField();
        t2Label = new javax.swing.JLabel();
        t2TextField = new javax.swing.JTextField();
        t3Label = new javax.swing.JLabel();
        t3TextField = new javax.swing.JTextField();
        pmaxLabel = new javax.swing.JLabel();
        pmaxTextField = new javax.swing.JTextField();
        pminLabel = new javax.swing.JLabel();
        pminTextField = new javax.swing.JTextField();
        pupLabel = new javax.swing.JLabel();
        pupTextField = new javax.swing.JTextField();
        pdownLabel = new javax.swing.JLabel();
        pdownTextField = new javax.swing.JTextField();
        tchLabel = new javax.swing.JLabel();
        tchTextField = new javax.swing.JTextField();
        trh1Label = new javax.swing.JLabel();
        trh1TextField = new javax.swing.JTextField();
        trh2Label = new javax.swing.JLabel();
        trh2TextField = new javax.swing.JTextField();
        tcoLabel = new javax.swing.JLabel();
        tcoTextField = new javax.swing.JTextField();
        fvhpLabel = new javax.swing.JLabel();
        fvhpTextField = new javax.swing.JTextField();
        fhpLabel = new javax.swing.JLabel();
        fhpTextField = new javax.swing.JTextField();
        fipLabel = new javax.swing.JLabel();
        fipTextField = new javax.swing.JTextField();
        flpLabel = new javax.swing.JLabel();
        flpTextField = new javax.swing.JTextField();

        kLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        kLabel.setText("K(pu)");

        kTextField.setColumns(5);
        kTextField.setFont(new java.awt.Font("Dialog", 0, 12));
        kTextField.setText("0.0");

        t1Label.setFont(new java.awt.Font("Dialog", 0, 12));
        t1Label.setText("T1(s)");

        t1TextField.setColumns(5);
        t1TextField.setFont(new java.awt.Font("Dialog", 0, 12));
        t1TextField.setText("0.0");

        t2Label.setFont(new java.awt.Font("Dialog", 0, 12));
        t2Label.setText("T2(s)");

        t2TextField.setColumns(5);
        t2TextField.setFont(new java.awt.Font("Dialog", 0, 12));
        t2TextField.setText("0.0");

        t3Label.setFont(new java.awt.Font("Dialog", 0, 12));
        t3Label.setText("T3(s)");

        t3TextField.setColumns(5);
        t3TextField.setFont(new java.awt.Font("Dialog", 0, 12));
        t3TextField.setText("0.0");

        pmaxLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        pmaxLabel.setText("Pmax(pu)");

        pmaxTextField.setColumns(5);
        pmaxTextField.setFont(new java.awt.Font("Dialog", 0, 12));
        pmaxTextField.setText("0.0");

        pminLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        pminLabel.setText("Pmin(pu)");

        pminTextField.setColumns(5);
        pminTextField.setFont(new java.awt.Font("Dialog", 0, 12));
        pminTextField.setText("0.0");

        pupLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        pupLabel.setText("Pup(pu)");

        pupTextField.setColumns(5);
        pupTextField.setFont(new java.awt.Font("Dialog", 0, 12));
        pupTextField.setText("0.0");

        pdownLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        pdownLabel.setText("Pdown(pu)");

        pdownTextField.setColumns(5);
        pdownTextField.setFont(new java.awt.Font("Dialog", 0, 12));
        pdownTextField.setText("0.0");

        tchLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        tchLabel.setText("Tch(s)");

        tchTextField.setColumns(5);
        tchTextField.setFont(new java.awt.Font("Dialog", 0, 12));
        tchTextField.setText("0.0");

        trh1Label.setFont(new java.awt.Font("Dialog", 0, 12));
        trh1Label.setText("Trh1(s)");

        trh1TextField.setColumns(5);
        trh1TextField.setFont(new java.awt.Font("Dialog", 0, 12));
        trh1TextField.setText("0.0");

        trh2Label.setFont(new java.awt.Font("Dialog", 0, 12));
        trh2Label.setText("Trh2(s)");

        trh2TextField.setColumns(5);
        trh2TextField.setFont(new java.awt.Font("Dialog", 0, 12));
        trh2TextField.setText("0.0");

        tcoLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        tcoLabel.setText("Tco(s)");

        tcoTextField.setColumns(5);
        tcoTextField.setFont(new java.awt.Font("Dialog", 0, 12));
        tcoTextField.setText("0.0");

        fvhpLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        fvhpLabel.setText("Fvhp(pu)");

        fvhpTextField.setColumns(5);
        fvhpTextField.setFont(new java.awt.Font("Dialog", 0, 12));
        fvhpTextField.setText("0.0");

        fhpLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        fhpLabel.setText("Fhp(pu)");

        fhpTextField.setColumns(5);
        fhpTextField.setFont(new java.awt.Font("Dialog", 0, 12));
        fhpTextField.setText("0.0");

        fipLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        fipLabel.setText("Fip(pu)");

        fipTextField.setColumns(5);
        fipTextField.setFont(new java.awt.Font("Dialog", 0, 12));
        fipTextField.setText("0.0");

        flpLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        flpLabel.setText("Flp(pu)");

        flpTextField.setColumns(5);
        flpTextField.setFont(new java.awt.Font("Dialog", 0, 12));
        flpTextField.setText("0.0");

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(46, 46, 46)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(kLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 75, Short.MAX_VALUE)
                    .add(flpLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 75, Short.MAX_VALUE)
                    .add(fvhpLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 75, Short.MAX_VALUE)
                    .add(trh1Label, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 75, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, pupLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 75, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, t3Label, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 75, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING, false)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, flpTextField, 0, 0, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, fvhpTextField, 0, 0, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, trh1TextField, 0, 0, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, pupTextField, 0, 0, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, t3TextField, 0, 0, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, kTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 56, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(55, 55, 55)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(t1Label, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 67, Short.MAX_VALUE)
                    .add(layout.createSequentialGroup()
                        .add(2, 2, 2)
                        .add(fhpLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 65, Short.MAX_VALUE))
                    .add(trh2Label, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 67, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, pmaxLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 67, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, pdownLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 67, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(fhpTextField, 0, 0, Short.MAX_VALUE)
                    .add(trh2TextField, 0, 0, Short.MAX_VALUE)
                    .add(pdownTextField, 0, 0, Short.MAX_VALUE)
                    .add(layout.createSequentialGroup()
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(pmaxTextField, 0, 0, Short.MAX_VALUE))
                    .add(t1TextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 50, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(46, 46, 46)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(tcoLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 72, Short.MAX_VALUE)
                    .add(pminLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 72, Short.MAX_VALUE)
                    .add(t2Label, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 72, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, fipLabel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 71, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, tchLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 72, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(fipTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 46, Short.MAX_VALUE)
                    .add(tcoTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 46, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, t2TextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 46, Short.MAX_VALUE)
                    .add(pminTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 46, Short.MAX_VALUE)
                    .add(tchTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 46, Short.MAX_VALUE))
                .add(59, 59, 59))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(24, 24, 24)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                            .add(t2TextField)
                            .add(t2Label))
                        .add(13, 13, 13)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                            .add(pminTextField)
                            .add(pminLabel))
                        .add(12, 12, 12)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(tchTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(tchLabel))
                        .add(14, 14, 14)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(tcoTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(tcoLabel))
                        .add(15, 15, 15)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(fipLabel)
                            .add(fipTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                    .add(layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                .add(t1TextField)
                                .add(t1Label))
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                .add(kTextField)
                                .add(kLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                        .add(13, 13, 13)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(pmaxTextField)
                            .add(t3TextField)
                            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                                .add(t3Label, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .add(pmaxLabel)))
                        .add(12, 12, 12)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(pdownTextField)
                            .add(pupTextField)
                            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                                .add(pupLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .add(pdownLabel)))
                        .add(14, 14, 14)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                .add(trh2Label)
                                .add(trh2TextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                .add(trh1Label)
                                .add(trh1TextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                        .add(15, 15, 15)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(fvhpLabel)
                            .add(fvhpTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(fhpTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(fhpLabel))
                        .add(15, 15, 15)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(flpLabel)
                            .add(flpTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
                .add(25, 25, 25))
        );
    }// </editor-fold>//GEN-END:initComponents
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel fhpLabel;
    private javax.swing.JTextField fhpTextField;
    private javax.swing.JLabel fipLabel;
    private javax.swing.JTextField fipTextField;
    private javax.swing.JLabel flpLabel;
    private javax.swing.JTextField flpTextField;
    private javax.swing.JLabel fvhpLabel;
    private javax.swing.JTextField fvhpTextField;
    private javax.swing.JLabel kLabel;
    private javax.swing.JTextField kTextField;
    private javax.swing.JLabel pdownLabel;
    private javax.swing.JTextField pdownTextField;
    private javax.swing.JLabel pmaxLabel;
    private javax.swing.JTextField pmaxTextField;
    private javax.swing.JLabel pminLabel;
    private javax.swing.JTextField pminTextField;
    private javax.swing.JLabel pupLabel;
    private javax.swing.JTextField pupTextField;
    private javax.swing.JLabel t1Label;
    private javax.swing.JTextField t1TextField;
    private javax.swing.JLabel t2Label;
    private javax.swing.JTextField t2TextField;
    private javax.swing.JLabel t3Label;
    private javax.swing.JTextField t3TextField;
    private javax.swing.JLabel tchLabel;
    private javax.swing.JTextField tchTextField;
    private javax.swing.JLabel tcoLabel;
    private javax.swing.JTextField tcoTextField;
    private javax.swing.JLabel trh1Label;
    private javax.swing.JTextField trh1TextField;
    private javax.swing.JLabel trh2Label;
    private javax.swing.JTextField trh2TextField;
    // End of variables declaration//GEN-END:variables

    // define data validation rules
	class DataVerifier extends javax.swing.InputVerifier {
    	public boolean verify(javax.swing.JComponent input) {
			if (input == null)
				return false;
       		try {
       			// data field verification rules
    			if ( input == kTextField)
    				return EditHelper.checkDblDataRange(input, _data, "k");
    			if ( input == t1TextField)
    				return EditHelper.checkDblDataRange(input, _data, "t1");
    			if ( input == t2TextField)
    				return EditHelper.checkDblDataRange(input, _data, "t2");
    			if ( input == t3TextField)
    				return EditHelper.checkDblDataRange(input, _data, "t3");
    			if ( input == pmaxTextField)
    				return EditHelper.checkDblDataRange(input, _data, "pmax");
    			if ( input == pminTextField)
    				return EditHelper.checkDblDataRange(input, _data, "pmin");
    			if ( input == pupTextField)
    				return EditHelper.checkDblDataRange(input, _data, "pup");
    			if ( input == pdownTextField)
    				return EditHelper.checkDblDataRange(input, _data, "pdown");
    			if ( input == tchTextField)
    				return EditHelper.checkDblDataRange(input, _data, "tch");
    			if ( input == trh1TextField)
    				return EditHelper.checkIntDataRange(input, _data, "trh1");
    			if ( input == trh2TextField)
    				return EditHelper.checkIntDataRange(input, _data, "trh2");
    			if ( input == tcoTextField)
    				return EditHelper.checkDblDataRange(input, _data, "tco");
    			if ( input == fvhpTextField)
    				return EditHelper.checkDblDataRange(input, _data, "fvhp");
    			if ( input == fhpTextField)
    				return EditHelper.checkDblDataRange(input, _data, "fhp");
    			if ( input == fipTextField)
    				return EditHelper.checkDblDataRange(input, _data, "fip");
    			if ( input == flpTextField)
    				return EditHelper.checkDblDataRange(input, _data, "flp");
 	       	} catch (Exception e) {
 	    		return false;
 	       	}		
			return true;
        }
    }
}

 /*
  * @(#)NBIeee1992PSS2AEditPanel.java   
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

package org.interpss.dstab.control.pss.ieee.y1992.pss2a;

import java.util.Vector;

import org.interpss.dstab.control.base.EditHelper;
import org.interpss.ui.ICustomPluginEditor;

public class NBIeee1992PSS2AEditPanel extends javax.swing.JPanel implements ICustomPluginEditor {
	private static final long serialVersionUID = 1;

	// define data to be edited
	Ieee1992PSS2AStabilizerData _data;

    /** Creates new form FaultLocDataPanel */
    public NBIeee1992PSS2AEditPanel() {
        initComponents();
        // init the field to the default values
        _data = new Ieee1992PSS2AStabilizerData();
        setData2Editor("");
        
        // associate the editing fields with the verifier class defined at the end of this calss
  		DataVerifier verifier = new DataVerifier();
  	    ks1TextField.setInputVerifier(verifier);
  	    t1TextField.setInputVerifier(verifier);
  	    t2TextField.setInputVerifier(verifier);
  	    t3TextField.setInputVerifier(verifier);
  	    t4TextField.setInputVerifier(verifier);
  	    t6TextField.setInputVerifier(verifier);
  	    t7TextField.setInputVerifier(verifier);
  	    t8TextField.setInputVerifier(verifier);
  	    t9TextField.setInputVerifier(verifier);
  	    nTextField.setInputVerifier(verifier);
  	    mTextField.setInputVerifier(verifier);
  	    vstmaxTextField.setInputVerifier(verifier);
  	    vstminTextField.setInputVerifier(verifier);
  	    ks2TextField.setInputVerifier(verifier);
  	    ks3TextField.setInputVerifier(verifier);
  	    tw1TextField.setInputVerifier(verifier);
  	    tw2TextField.setInputVerifier(verifier);
  	    tw3TextField.setInputVerifier(verifier);
  	    tw4TextField.setInputVerifier(verifier);
    }
    
    /**
     * Init the editor panel, which will be called from its parent editor
     */
	public void init(Object controller) {
		// init the data object from the bus object being edited
		_data = ((Ieee1992PSS2AStabilizer)controller).getData();
	}
	
	/**
	*	Set controller data to the editor
	*
	* @return false if there is any problem
	*/
    public boolean setData2Editor(String desc) {
    	EditHelper.setDblTextFiled(ks1TextField, 	_data.getKs1(), "#0.00");
    	EditHelper.setDblTextFiled(t1TextField, 	_data.getT1(), "#0.000");
    	EditHelper.setDblTextFiled(t2TextField, 	_data.getT2(), "#0.000");
    	EditHelper.setDblTextFiled(t3TextField, 	_data.getT3(), "#0.000");
    	EditHelper.setDblTextFiled(t4TextField, 	_data.getT4(), "#0.000");
    	EditHelper.setDblTextFiled(t6TextField, 	_data.getT6(), "#0.000");
    	EditHelper.setDblTextFiled(t7TextField, 	_data.getT7(), "#0.000");
    	EditHelper.setDblTextFiled(t8TextField, 	_data.getT8(), "#0.000");
    	EditHelper.setDblTextFiled(t9TextField, 	_data.getT9(), "#0.000");
    	EditHelper.setIntTextFiled(nTextField, 		_data.getN());
    	EditHelper.setIntTextFiled(mTextField, 		_data.getM());
    	EditHelper.setDblTextFiled(vstmaxTextField, _data.getVstmax(), "#0.00");
    	EditHelper.setDblTextFiled(vstminTextField, _data.getVstmin(), "#0.000");
    	EditHelper.setDblTextFiled(ks2TextField, 	_data.getKs2(), "#0.000");
    	EditHelper.setDblTextFiled(ks3TextField, 	_data.getKs3(), "#0.000");
    	EditHelper.setDblTextFiled(tw1TextField, 	_data.getTw1(), "#0.000");
    	EditHelper.setDblTextFiled(tw2TextField, 	_data.getTw2(), "#0.000");
    	EditHelper.setDblTextFiled(tw3TextField, 	_data.getTw3(), "#0.00");
    	EditHelper.setDblTextFiled(tw4TextField, 	_data.getTw4(), "#0.000");

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
    	
    	EditHelper.saveDblTextField(_data, ks1TextField, "ks1", errMsg);
    	EditHelper.saveDblTextField(_data, t1TextField, "t1", errMsg);
    	EditHelper.saveDblTextField(_data, t2TextField, "t2", errMsg);
    	EditHelper.saveDblTextField(_data, t3TextField, "t3", errMsg);
    	EditHelper.saveDblTextField(_data, t4TextField, "t4", errMsg);
    	EditHelper.saveDblTextField(_data, t5TextField, "t5", errMsg);
    	EditHelper.saveDblTextField(_data, t6TextField, "t6", errMsg);
    	EditHelper.saveDblTextField(_data, t7TextField, "t7", errMsg);
    	EditHelper.saveDblTextField(_data, t8TextField, "t8", errMsg);
    	EditHelper.saveDblTextField(_data, t9TextField, "t9", errMsg);
    	EditHelper.saveIntTextField(_data, nTextField, "n", errMsg);
    	EditHelper.saveIntTextField(_data, mTextField, "m", errMsg);
    	EditHelper.saveDblTextField(_data, vstmaxTextField, "vstmax", errMsg);
    	EditHelper.saveDblTextField(_data, vstminTextField, "vstmin", errMsg);
    	EditHelper.saveDblTextField(_data, ks2TextField, "ks2", errMsg);
    	EditHelper.saveDblTextField(_data, ks3TextField, "ks3", errMsg);
    	EditHelper.saveDblTextField(_data, tw1TextField, "tw1", errMsg);
    	EditHelper.saveDblTextField(_data, tw2TextField, "tw2", errMsg);
    	EditHelper.saveDblTextField(_data, tw3TextField, "tw3", errMsg);
    	EditHelper.saveDblTextField(_data, tw4TextField, "tw4", errMsg);

    	return errMsg.size() == 0;
	}
    
	/** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        ks1Label = new javax.swing.JLabel();
        ks1TextField = new javax.swing.JTextField();
        t1Label = new javax.swing.JLabel();
        t1TextField = new javax.swing.JTextField();
        t2Label = new javax.swing.JLabel();
        t2TextField = new javax.swing.JTextField();
        t3Label = new javax.swing.JLabel();
        t3TextField = new javax.swing.JTextField();
        t4Label = new javax.swing.JLabel();
        t4TextField = new javax.swing.JTextField();
        t5Label = new javax.swing.JLabel();
        t5TextField = new javax.swing.JTextField();
        t6Label = new javax.swing.JLabel();
        t6TextField = new javax.swing.JTextField();
        t7Label = new javax.swing.JLabel();
        t7TextField = new javax.swing.JTextField();
        t8Label = new javax.swing.JLabel();
        t8TextField = new javax.swing.JTextField();
        t9Label = new javax.swing.JLabel();
        t9TextField = new javax.swing.JTextField();
        nLabel = new javax.swing.JLabel();
        nTextField = new javax.swing.JTextField();
        mLabel = new javax.swing.JLabel();
        mTextField = new javax.swing.JTextField();
        vstmaxLabel = new javax.swing.JLabel();
        vstmaxTextField = new javax.swing.JTextField();
        vstminLabel = new javax.swing.JLabel();
        vstminTextField = new javax.swing.JTextField();
        ks2Label = new javax.swing.JLabel();
        ks2TextField = new javax.swing.JTextField();
        ks3Label = new javax.swing.JLabel();
        ks3TextField = new javax.swing.JTextField();
        tw1Label = new javax.swing.JLabel();
        tw1TextField = new javax.swing.JTextField();
        tw2Label = new javax.swing.JLabel();
        tw2TextField = new javax.swing.JTextField();
        tw3Label = new javax.swing.JLabel();
        tw3TextField = new javax.swing.JTextField();
        tw4Label = new javax.swing.JLabel();
        tw4TextField = new javax.swing.JTextField();

        ks1Label.setFont(new java.awt.Font("Dialog", 0, 12));
        ks1Label.setText("Ks1(pu)");

        ks1TextField.setColumns(5);
        ks1TextField.setFont(new java.awt.Font("Dialog", 0, 12));
        ks1TextField.setText("0.0");

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

        t4Label.setFont(new java.awt.Font("Dialog", 0, 12));
        t4Label.setText("T4(s)");

        t4TextField.setColumns(5);
        t4TextField.setFont(new java.awt.Font("Dialog", 0, 12));
        t4TextField.setText("0.0");

        t5Label.setFont(new java.awt.Font("Dialog", 0, 12));
        t5Label.setText("T5(s)");
        t5Label.setEnabled(false);

        t5TextField.setColumns(5);
        t5TextField.setFont(new java.awt.Font("Dialog", 0, 12));
        t5TextField.setText("0.0");
        t5TextField.setEnabled(false);

        t6Label.setFont(new java.awt.Font("Dialog", 0, 12));
        t6Label.setText("T6(s)");

        t6TextField.setColumns(5);
        t6TextField.setFont(new java.awt.Font("Dialog", 0, 12));
        t6TextField.setText("0.0");

        t7Label.setFont(new java.awt.Font("Dialog", 0, 12));
        t7Label.setText("T7(s)");

        t7TextField.setColumns(5);
        t7TextField.setFont(new java.awt.Font("Dialog", 0, 12));
        t7TextField.setText("0.0");

        t8Label.setFont(new java.awt.Font("Dialog", 0, 12));
        t8Label.setText("T8(s)");

        t8TextField.setColumns(5);
        t8TextField.setFont(new java.awt.Font("Dialog", 0, 12));
        t8TextField.setText("0.0");

        t9Label.setFont(new java.awt.Font("Dialog", 0, 12));
        t9Label.setText("T9(s)");

        t9TextField.setColumns(5);
        t9TextField.setFont(new java.awt.Font("Dialog", 0, 12));
        t9TextField.setText("0.0");

        nLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        nLabel.setText("N");

        nTextField.setColumns(5);
        nTextField.setFont(new java.awt.Font("Dialog", 0, 12));
        nTextField.setText("0.0");

        mLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        mLabel.setText("M");

        mTextField.setColumns(5);
        mTextField.setFont(new java.awt.Font("Dialog", 0, 12));
        mTextField.setText("0.0");

        vstmaxLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        vstmaxLabel.setText("Vstmax(pu)");

        vstmaxTextField.setColumns(5);
        vstmaxTextField.setFont(new java.awt.Font("Dialog", 0, 12));
        vstmaxTextField.setText("0.0");

        vstminLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        vstminLabel.setText("Vstmin(pu)");

        vstminTextField.setColumns(5);
        vstminTextField.setFont(new java.awt.Font("Dialog", 0, 12));
        vstminTextField.setText("0.0");

        ks2Label.setFont(new java.awt.Font("Dialog", 0, 12));
        ks2Label.setText("Ks2(pu)");

        ks2TextField.setColumns(5);
        ks2TextField.setFont(new java.awt.Font("Dialog", 0, 12));
        ks2TextField.setText("0.0");

        ks3Label.setFont(new java.awt.Font("Dialog", 0, 12));
        ks3Label.setText("Ks3(pu)");

        ks3TextField.setColumns(5);
        ks3TextField.setFont(new java.awt.Font("Dialog", 0, 12));
        ks3TextField.setText("0.0");

        tw1Label.setFont(new java.awt.Font("Dialog", 0, 12));
        tw1Label.setText("Tw1(s)");

        tw1TextField.setColumns(5);
        tw1TextField.setFont(new java.awt.Font("Dialog", 0, 12));
        tw1TextField.setText("0.0");

        tw2Label.setFont(new java.awt.Font("Dialog", 0, 12));
        tw2Label.setText("Tw2(s)");

        tw2TextField.setColumns(5);
        tw2TextField.setFont(new java.awt.Font("Dialog", 0, 12));
        tw2TextField.setText("0.0");

        tw3Label.setFont(new java.awt.Font("Dialog", 0, 12));
        tw3Label.setText("Tw3(s)");

        tw3TextField.setColumns(5);
        tw3TextField.setFont(new java.awt.Font("Dialog", 0, 12));
        tw3TextField.setText("0.0");

        tw4Label.setFont(new java.awt.Font("Dialog", 0, 12));
        tw4Label.setText("Tw4(s)");

        tw4TextField.setColumns(5);
        tw4TextField.setFont(new java.awt.Font("Dialog", 0, 12));
        tw4TextField.setText("0.0");

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(49, 49, 49)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(ks1Label, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 75, Short.MAX_VALUE)
                    .add(tw3Label, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 75, Short.MAX_VALUE)
                    .add(ks3Label, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 75, Short.MAX_VALUE)
                    .add(vstmaxLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 75, Short.MAX_VALUE)
                    .add(t9Label, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 75, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, t6Label, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 75, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, t3Label, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 75, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING, false)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, tw3TextField, 0, 0, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, ks3TextField, 0, 0, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, vstmaxTextField, 0, 0, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, t9TextField, 0, 0, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, t6TextField, 0, 0, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, t3TextField, 0, 0, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, ks1TextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 56, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(55, 55, 55)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(t1Label, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 67, Short.MAX_VALUE)
                    .add(layout.createSequentialGroup()
                        .add(2, 2, 2)
                        .add(tw4Label, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 65, Short.MAX_VALUE))
                    .add(org.jdesktop.layout.GroupLayout.LEADING, tw1Label, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 67, Short.MAX_VALUE)
                    .add(layout.createSequentialGroup()
                        .add(2, 2, 2)
                        .add(vstminLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 65, Short.MAX_VALUE))
                    .add(nLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 67, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, t4Label, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 67, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, t7Label, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 67, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(tw4TextField, 0, 0, Short.MAX_VALUE)
                    .add(tw1TextField, 0, 0, Short.MAX_VALUE)
                    .add(vstminTextField, 0, 0, Short.MAX_VALUE)
                    .add(nTextField, 0, 0, Short.MAX_VALUE)
                    .add(t7TextField, 0, 0, Short.MAX_VALUE)
                    .add(layout.createSequentialGroup()
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(t4TextField, 0, 0, Short.MAX_VALUE))
                    .add(t1TextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 50, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(46, 46, 46)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(mLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 72, Short.MAX_VALUE)
                    .add(t5Label, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 72, Short.MAX_VALUE)
                    .add(t2Label, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 72, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, ks2Label, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 71, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, tw2Label, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 71, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, t8Label, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 72, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(tw2TextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 46, Short.MAX_VALUE)
                    .add(ks2TextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 46, Short.MAX_VALUE)
                    .add(mTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 46, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, t2TextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 46, Short.MAX_VALUE)
                    .add(t5TextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 46, Short.MAX_VALUE)
                    .add(t8TextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 46, Short.MAX_VALUE))
                .add(56, 56, 56))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(20, 20, 20)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                            .add(t2TextField)
                            .add(t2Label))
                        .add(13, 13, 13)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                            .add(t5TextField)
                            .add(t5Label))
                        .add(12, 12, 12)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(t8TextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(t8Label))
                        .add(14, 14, 14)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(mTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(mLabel))
                        .add(15, 15, 15)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(ks2Label)
                            .add(ks2TextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                        .add(15, 15, 15)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(tw2Label)
                            .add(tw2TextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                    .add(layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                .add(t1TextField)
                                .add(t1Label))
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                .add(ks1TextField)
                                .add(ks1Label, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                        .add(13, 13, 13)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(t4TextField)
                            .add(t3TextField)
                            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                                .add(t3Label, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .add(t4Label)))
                        .add(12, 12, 12)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(t7TextField)
                            .add(t6TextField)
                            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                                .add(t6Label, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .add(t7Label)))
                        .add(14, 14, 14)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                .add(nLabel)
                                .add(nTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                .add(t9Label)
                                .add(t9TextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                        .add(15, 15, 15)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(vstmaxLabel)
                            .add(vstmaxTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(vstminTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(vstminLabel))
                        .add(15, 15, 15)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(ks3Label)
                            .add(ks3TextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(tw1TextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(tw1Label))
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(layout.createSequentialGroup()
                                .add(15, 15, 15)
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                    .add(tw4TextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                    .add(tw4Label)))
                            .add(layout.createSequentialGroup()
                                .add(17, 17, 17)
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                    .add(tw3Label)
                                    .add(tw3TextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))))
                .add(20, 20, 20))
        );
    }// </editor-fold>//GEN-END:initComponents
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel ks1Label;
    private javax.swing.JTextField ks1TextField;
    private javax.swing.JLabel ks2Label;
    private javax.swing.JTextField ks2TextField;
    private javax.swing.JLabel ks3Label;
    private javax.swing.JTextField ks3TextField;
    private javax.swing.JLabel mLabel;
    private javax.swing.JTextField mTextField;
    private javax.swing.JLabel nLabel;
    private javax.swing.JTextField nTextField;
    private javax.swing.JLabel t1Label;
    private javax.swing.JTextField t1TextField;
    private javax.swing.JLabel t2Label;
    private javax.swing.JTextField t2TextField;
    private javax.swing.JLabel t3Label;
    private javax.swing.JTextField t3TextField;
    private javax.swing.JLabel t4Label;
    private javax.swing.JTextField t4TextField;
    private javax.swing.JLabel t5Label;
    private javax.swing.JTextField t5TextField;
    private javax.swing.JLabel t6Label;
    private javax.swing.JTextField t6TextField;
    private javax.swing.JLabel t7Label;
    private javax.swing.JTextField t7TextField;
    private javax.swing.JLabel t8Label;
    private javax.swing.JTextField t8TextField;
    private javax.swing.JLabel t9Label;
    private javax.swing.JTextField t9TextField;
    private javax.swing.JLabel tw1Label;
    private javax.swing.JTextField tw1TextField;
    private javax.swing.JLabel tw2Label;
    private javax.swing.JTextField tw2TextField;
    private javax.swing.JLabel tw3Label;
    private javax.swing.JTextField tw3TextField;
    private javax.swing.JLabel tw4Label;
    private javax.swing.JTextField tw4TextField;
    private javax.swing.JLabel vstmaxLabel;
    private javax.swing.JTextField vstmaxTextField;
    private javax.swing.JLabel vstminLabel;
    private javax.swing.JTextField vstminTextField;
    // End of variables declaration//GEN-END:variables

    // define data validation rules
	class DataVerifier extends javax.swing.InputVerifier {
    	public boolean verify(javax.swing.JComponent input) {
			if (input == null)
				return false;
       		try {
       			// data field verification rules
    			if ( input == ks1TextField)
    				return EditHelper.checkDblDataRange(input, _data, "ks1");
    			if ( input == t1TextField)
    				return EditHelper.checkDblDataRange(input, _data, "t1");
    			if ( input == t2TextField)
    				return EditHelper.checkDblDataRange(input, _data, "t2");
    			if ( input == t3TextField)
    				return EditHelper.checkDblDataRange(input, _data, "t3");
    			if ( input == t4TextField)
    				return EditHelper.checkDblDataRange(input, _data, "t4");
    			if ( input == t6TextField)
    				return EditHelper.checkDblDataRange(input, _data, "t6");
    			if ( input == t7TextField)
    				return EditHelper.checkDblDataRange(input, _data, "t7");
    			if ( input == t8TextField)
    				return EditHelper.checkDblDataRange(input, _data, "t8");
    			if ( input == t9TextField)
    				return EditHelper.checkDblDataRange(input, _data, "t9");
    			if ( input == nTextField)
    				return EditHelper.checkIntDataRange(input, _data, "n");
    			if ( input == mTextField)
    				return EditHelper.checkIntDataRange(input, _data, "m");
    			if ( input == vstmaxTextField)
    				return EditHelper.checkDblDataRange(input, _data, "vstmax");
    			if ( input == vstminTextField)
    				return EditHelper.checkDblDataRange(input, _data, "vstmin");
    			if ( input == ks2TextField)
    				return EditHelper.checkDblDataRange(input, _data, "ks2");
    			if ( input == ks3TextField)
    				return EditHelper.checkDblDataRange(input, _data, "ks3");
    			if ( input == tw1TextField)
    				return EditHelper.checkDblDataRange(input, _data, "tw1");
    			if ( input == tw2TextField)
    				return EditHelper.checkDblDataRange(input, _data, "tw2");
    			if ( input == tw3TextField)
    				return EditHelper.checkDblDataRange(input, _data, "tw3");
    			if ( input == tw4TextField)
    				return EditHelper.checkDblDataRange(input, _data, "tw4");
    			
 	       	} catch (Exception e) {
 	    		return false;
 	       	}		
			return true;
        }
    }
}

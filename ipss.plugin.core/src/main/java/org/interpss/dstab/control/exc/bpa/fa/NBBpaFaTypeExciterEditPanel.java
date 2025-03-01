/**
 * <copyright>
 * 	Copyright www.interpss.com 2011
 * </copyright>
 * 
 * A JPanel for editing the model's data. It implements the IControllerEditor interface, which will be called from
 * the parent editor dialog.
 * 
 * @author  Sherlock_Li
 */

package org.interpss.dstab.control.exc.bpa.fa;

import java.util.Vector;

import org.interpss.numeric.util.Number2String;
import org.interpss.ui.ICustomPluginEditor;
import org.interpss.ui.SwingInputVerifyUtil;

public class NBBpaFaTypeExciterEditPanel extends javax.swing.JPanel implements ICustomPluginEditor {
	private static final long serialVersionUID = 1;

	// define data to be edited
	BpaFaTypeExciterData _data;

    /** Creates new form FaultLocDataPanel */
    public NBBpaFaTypeExciterEditPanel() {
        initComponents();

        // associate the editing fields with the verifier class defined at the end of this calss
  		DataVerifier verifier = new DataVerifier();
  	    kaTextField.setInputVerifier(verifier);
  	    taTextField.setInputVerifier(verifier);
  	    tcTextField.setInputVerifier(verifier);
  	    tbTextField.setInputVerifier(verifier);
            vrmaxTextField.setInputVerifier(verifier);
            vrminTextField.setInputVerifier(verifier);
            keTextField.setInputVerifier(verifier);
            teTextField.setInputVerifier(verifier);
            se_e1TextField.setInputVerifier(verifier);
            se_e2TextField.setInputVerifier(verifier);
            efd1TextField.setInputVerifier(verifier);
    }
    
    /**
     * Init the editor panel, which will be called from its parent editor
     */
	public void init(Object controller) {
		// init the data object from the bus object being edited
		_data = ((BpaFaTypeExciter)controller).getData();
	}
	
	/**
	*	Set controller data to the editor
	*
	* @return false if there is any problem
	*/
    public boolean setData2Editor(String desc) {
  	    kaTextField.setText(Number2String.toStr(_data.getKa(), "#0.00"));
  	    taTextField.setText(Number2String.toStr(_data.getTa(), "#0.000"));
            tcTextField.setText(Number2String.toStr(_data.getTc(), "#0.000"));
            tbTextField.setText(Number2String.toStr(_data.getTb(), "#0.000"));
            vrmaxTextField.setText(Number2String.toStr(_data.getVrmax(), "#0.000"));
            vrminTextField.setText(Number2String.toStr(_data.getVrmin(), "#0.000"));
            keTextField.setText(Number2String.toStr(_data.getKe(), "#0.000"));
            teTextField.setText(Number2String.toStr(_data.getTe(), "#0.000"));
            se_e1TextField.setText(Number2String.toStr(_data.getSe_e1(), "#0.000"));
            se_e2TextField.setText(Number2String.toStr(_data.getSe_e2(), "#0.000"));
            efd1TextField.setText(Number2String.toStr(_data.getEfd1(), "#0.000"));
            kfTextField.setText(Number2String.toStr(_data.getKf(), "#0.000"));
  	    tfTextField.setText(Number2String.toStr(_data.getTf(), "#0.000"));
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
    	
	if (SwingInputVerifyUtil.within(this.kaTextField, 1.0, 1000.0, errMsg, 
			"Ka is out of the range [1.0, 1000.0]"))
		_data.setKa(SwingInputVerifyUtil.getDouble(kaTextField));

	if (SwingInputVerifyUtil.within(this.taTextField, 0.001, 10.0, errMsg,
			"Ta is out of the range [0.001, 10]"))
		_data.setTa(SwingInputVerifyUtil.getDouble(taTextField));

	if (SwingInputVerifyUtil.within(this.tcTextField, 0.001, 1000.0, errMsg,
			"Tc is out of the range [0.001, 1000.0]"))
		_data.setTc(SwingInputVerifyUtil.getDouble(tcTextField));

	if (SwingInputVerifyUtil.within(this.tbTextField, 0.001, 1000.0, errMsg,
			"Tb is out of the range [0.001, 1000.0]"))
		_data.setTb(SwingInputVerifyUtil.getDouble(tbTextField));

	if (SwingInputVerifyUtil.within(this.vrmaxTextField, 0.001, 100.0, errMsg,
			"Vrmax is out of the range [0.001, 100]"))
		_data.setVrmax(SwingInputVerifyUtil.getDouble(vrmaxTextField));

	if (SwingInputVerifyUtil.within(this.vrminTextField, -0.01, -100.0, errMsg,
			"Vrmin is out of the range [-0.01, -100.0]"))
		_data.setVrmin(SwingInputVerifyUtil.getDouble(vrminTextField));

	if (SwingInputVerifyUtil.within(this.keTextField, 0.001, 10.0, errMsg,
			"Ke is out of the range [0.001, 10]"))
		_data.setKe(SwingInputVerifyUtil.getDouble(keTextField));

	if (SwingInputVerifyUtil.within(this.teTextField, 0.001, 10.0, errMsg,
			"Te is out of the range [0.001, 10]"))
		_data.setTe(SwingInputVerifyUtil.getDouble(teTextField));

	if (SwingInputVerifyUtil.within(this.se_e1TextField, 0.001, 10.0, errMsg,
			"Se_e1 is out of the range [0.001, 10]"))
		_data.setSe_e1(SwingInputVerifyUtil.getDouble(se_e1TextField));

        if (SwingInputVerifyUtil.within(this.se_e2TextField, 0.001, 10.0, errMsg,
			"Se_e2 is out of the range [0.001, 10]"))
		_data.setSe_e2(SwingInputVerifyUtil.getDouble(se_e2TextField));

	if (SwingInputVerifyUtil.within(this.efd1TextField, 0.001, 100.0, errMsg,
			"Efd1 is out of the range [0.001, 100.0]"))
		_data.setEfd1(SwingInputVerifyUtil.getDouble(efd1TextField));

	if (SwingInputVerifyUtil.within(this.kfTextField, 0.1, 1000.0, errMsg,
			"Kf is out of the range [0.1, 1000.0]"))
		_data.setKf(SwingInputVerifyUtil.getDouble(kfTextField));

	if (SwingInputVerifyUtil.within(this.tfTextField, 0.001, 10.0, errMsg,
			"Tf is out of the range [0.001, 10]"))
		_data.setTf(SwingInputVerifyUtil.getDouble(tfTextField));
		
    	return errMsg.size() == 0;
	}
    
	/** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        kaLabel = new javax.swing.JLabel();
        kaTextField = new javax.swing.JTextField();
        taLabel = new javax.swing.JLabel();
        taTextField = new javax.swing.JTextField();
        tcLabel = new javax.swing.JLabel();
        tcTextField = new javax.swing.JTextField();
        tbLabel = new javax.swing.JLabel();
        tbTextField = new javax.swing.JTextField();
        vrmaxLabel = new javax.swing.JLabel();
        vrmaxTextField = new javax.swing.JTextField();
        vrminLabel = new javax.swing.JLabel();
        vrminTextField = new javax.swing.JTextField();
        keLabel = new javax.swing.JLabel();
        keTextField = new javax.swing.JTextField();
        teLabel = new javax.swing.JLabel();
        teTextField = new javax.swing.JTextField();
        se_e1Label = new javax.swing.JLabel();
        se_e1TextField = new javax.swing.JTextField();
        se_e2Label = new javax.swing.JLabel();
        se_e2TextField = new javax.swing.JTextField();
        efd1Label = new javax.swing.JLabel();
        efd1TextField = new javax.swing.JTextField();
        kfLabel = new javax.swing.JLabel();
        kfTextField = new javax.swing.JTextField();
        tfLabel = new javax.swing.JLabel();
        tfTextField = new javax.swing.JTextField();
        textareaScrollPane = new javax.swing.JScrollPane();
        pluginInfoTextArea = new javax.swing.JTextArea();

        kaLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        kaLabel.setText("Ka(pu)");

        kaTextField.setColumns(5);
        kaTextField.setFont(new java.awt.Font("Dialog", 0, 12));
        kaTextField.setText("40.0");

        taLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        taLabel.setText("Ta(s)");

        taTextField.setColumns(5);
        taTextField.setFont(new java.awt.Font("Dialog", 0, 12));
        taTextField.setText("0.05");

        tcLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        tcLabel.setText("Tc(s)");

        tcTextField.setColumns(5);
        tcTextField.setFont(new java.awt.Font("Dialog", 0, 12));
        tcTextField.setText("0.04");

        tbLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        tbLabel.setText("Tb(s)");

        tbTextField.setColumns(5);
        tbTextField.setFont(new java.awt.Font("Dialog", 0, 12));
        tbTextField.setText("0.02");

        vrmaxLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        vrmaxLabel.setText("Vrmax(pu)");

        vrmaxTextField.setColumns(5);
        vrmaxTextField.setFont(new java.awt.Font("Dialog", 0, 12));
        vrmaxTextField.setText("0.860");

        vrminLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        vrminLabel.setText("Vrmin(pu)");

        vrminTextField.setColumns(5);
        vrminTextField.setFont(new java.awt.Font("Dialog", 0, 12));
        vrminTextField.setText("5.5");

        keLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        keLabel.setText("Ke(pu)");

        keTextField.setColumns(5);
        keTextField.setFont(new java.awt.Font("Dialog", 0, 12));
        keTextField.setText("1.0");

        teLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        teLabel.setText("Te(s)");

        teTextField.setColumns(5);
        teTextField.setFont(new java.awt.Font("Dialog", 0, 12));
        teTextField.setText("2.0");

        se_e1Label.setFont(new java.awt.Font("Dialog", 0, 12));
        se_e1Label.setText("Se_e1(pu)");

        se_e1TextField.setColumns(5);
        se_e1TextField.setFont(new java.awt.Font("Dialog", 0, 12));
        se_e1TextField.setText("0.50");

        se_e2Label.setFont(new java.awt.Font("Dialog", 0, 12));
        se_e2Label.setText("Se_e2(pu)");

        se_e2TextField.setColumns(5);
        se_e2TextField.setFont(new java.awt.Font("Dialog", 0, 12));
        se_e2TextField.setText("0.03");

        efd1Label.setFont(new java.awt.Font("Dialog", 0, 12));
        efd1Label.setText("Efd1(pu)");

        efd1TextField.setColumns(5);
        efd1TextField.setFont(new java.awt.Font("Dialog", 0, 12));
        efd1TextField.setText("0.350");

        kfLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        kfLabel.setText("Kf(pu)");

        kfTextField.setColumns(5);
        kfTextField.setFont(new java.awt.Font("Dialog", 0, 12));
        kfTextField.setText("0.350");

        tfLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        tfLabel.setText("Tf(s)");

        tfTextField.setColumns(5);
        tfTextField.setFont(new java.awt.Font("Dialog", 0, 12));
        tfTextField.setText("0.350");

        pluginInfoTextArea.setColumns(20);
        pluginInfoTextArea.setEditable(false);
        pluginInfoTextArea.setFont(new java.awt.Font("Dialog", 0, 12));
        pluginInfoTextArea.setLineWrap(true);
        pluginInfoTextArea.setRows(3);
        pluginInfoTextArea.setText("Plugin impl description");
        pluginInfoTextArea.setWrapStyleWord(true);
        textareaScrollPane.setViewportView(pluginInfoTextArea);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .addContainerGap(99, Short.MAX_VALUE)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(kaLabel)
                            .add(tcLabel)
                            .add(vrmaxLabel)
                            .add(keLabel)
                            .add(efd1Label)
                            .add(tfLabel)
                            .add(se_e1Label))
                        .add(39, 39, 39)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(layout.createSequentialGroup()
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                    .add(kaTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                    .add(tcTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                    .add(vrmaxTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                    .add(keTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                    .add(se_e1TextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                    .add(efd1TextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                                .add(53, 53, 53)
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                                    .add(layout.createSequentialGroup()
                                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                            .add(taLabel)
                                            .add(tbLabel)
                                            .add(vrminLabel)
                                            .add(teLabel)
                                            .add(se_e2Label))
                                        .add(30, 30, 30)
                                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                            .add(org.jdesktop.layout.GroupLayout.TRAILING, vrminTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                            .add(org.jdesktop.layout.GroupLayout.TRAILING, taTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                            .add(org.jdesktop.layout.GroupLayout.TRAILING, tbTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                            .add(org.jdesktop.layout.GroupLayout.TRAILING, teTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                            .add(org.jdesktop.layout.GroupLayout.TRAILING, se_e2TextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                                    .add(layout.createSequentialGroup()
                                        .add(kfLabel)
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .add(kfTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
                            .add(tfTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, textareaScrollPane, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 368, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(98, 98, 98))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(112, 112, 112)
                        .add(teLabel))
                    .add(layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(layout.createSequentialGroup()
                                .addContainerGap()
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                    .add(layout.createSequentialGroup()
                                        .add(2, 2, 2)
                                        .add(kaLabel))
                                    .add(kaTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                    .add(layout.createSequentialGroup()
                                        .add(2, 2, 2)
                                        .add(taLabel))
                                    .add(taTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                    .add(layout.createSequentialGroup()
                                        .add(12, 12, 12)
                                        .add(tcLabel))
                                    .add(layout.createSequentialGroup()
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                            .add(tbTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                            .add(tbLabel)))
                                    .add(layout.createSequentialGroup()
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                                        .add(tcTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                    .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                        .add(vrmaxTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                        .add(vrmaxLabel))
                                    .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                        .add(vrminLabel)
                                        .add(vrminTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                                .add(10, 10, 10)
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                    .add(teTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                    .add(keTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                    .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                        .add(se_e1TextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                        .add(se_e1Label))
                                    .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                        .add(se_e2TextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                        .add(se_e2Label)))
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                    .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                        .add(efd1TextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                        .add(kfLabel)
                                        .add(efd1Label))
                                    .add(kfTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                            .add(layout.createSequentialGroup()
                                .add(115, 115, 115)
                                .add(keLabel)))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(tfLabel)
                            .add(tfTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 18, Short.MAX_VALUE)
                        .add(textareaScrollPane, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 77, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(32, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel efd1Label;
    private javax.swing.JTextField efd1TextField;
    private javax.swing.JLabel kaLabel;
    private javax.swing.JTextField kaTextField;
    private javax.swing.JLabel keLabel;
    private javax.swing.JTextField keTextField;
    private javax.swing.JLabel kfLabel;
    private javax.swing.JTextField kfTextField;
    private javax.swing.JTextArea pluginInfoTextArea;
    private javax.swing.JLabel se_e1Label;
    private javax.swing.JTextField se_e1TextField;
    private javax.swing.JLabel se_e2Label;
    private javax.swing.JTextField se_e2TextField;
    private javax.swing.JLabel taLabel;
    private javax.swing.JTextField taTextField;
    private javax.swing.JLabel tbLabel;
    private javax.swing.JTextField tbTextField;
    private javax.swing.JLabel tcLabel;
    private javax.swing.JTextField tcTextField;
    private javax.swing.JLabel teLabel;
    private javax.swing.JTextField teTextField;
    private javax.swing.JScrollPane textareaScrollPane;
    private javax.swing.JLabel tfLabel;
    private javax.swing.JTextField tfTextField;
    private javax.swing.JLabel vrmaxLabel;
    private javax.swing.JTextField vrmaxTextField;
    private javax.swing.JLabel vrminLabel;
    private javax.swing.JTextField vrminTextField;
    // End of variables declaration//GEN-END:variables

    // define data validation rules
	class DataVerifier extends javax.swing.InputVerifier {
    	@Override
		public boolean verify(javax.swing.JComponent input) {
			if (input == null)
				return false;
       		try {
       			// data field verification rules
    			if ( input == kaTextField ||
             		 input == taTextField ||
             		 input == tcTextField ||
             		 input == tbTextField ||
                         input == vrmaxTextField ||
                         input == vrminTextField ||
                         input == keTextField ||
                         input == teTextField ||
                         input == tfTextField ||
                         input == se_e1TextField ||
                         input == se_e2TextField ||
                         input == efd1TextField)
    	       		return SwingInputVerifyUtil.getDouble((javax.swing.JTextField)input) >= 0.0;
 	       	} catch (Exception e) {
 	    		return false;
 	       	}		
			return true;
        }
    }
}

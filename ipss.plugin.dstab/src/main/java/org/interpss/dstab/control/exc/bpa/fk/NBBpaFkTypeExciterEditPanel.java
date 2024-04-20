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

package org.interpss.dstab.control.exc.bpa.fk;

import java.util.Vector;

import org.interpss.numeric.util.Number2String;
import org.interpss.ui.ICustomPluginEditor;
import org.interpss.ui.SwingInputVerifyUtil;

public class NBBpaFkTypeExciterEditPanel extends javax.swing.JPanel implements ICustomPluginEditor {
	private static final long serialVersionUID = 1;

	// define data to be edited
	BpaFkTypeExciterData _data;

    /** Creates new form FaultLocDataPanel */
    public NBBpaFkTypeExciterEditPanel() {
        initComponents();

        // associate the editing fields with the verifier class defined at the end of this calss
  		DataVerifier verifier = new DataVerifier();
  	    kaTextField.setInputVerifier(verifier);
  	    taTextField.setInputVerifier(verifier);
  	    trTextField.setInputVerifier(verifier);
  	    ta1TextField.setInputVerifier(verifier);
            semaxTextField.setInputVerifier(verifier);
            efdmaxTextField.setInputVerifier(verifier);
            keTextField.setInputVerifier(verifier);
            teTextField.setInputVerifier(verifier);
            se_e2TextField.setInputVerifier(verifier);
            kfTextField.setInputVerifier(verifier);
            tfTextField.setInputVerifier(verifier);
    }
    
    /**
     * Init the editor panel, which will be called from its parent editor
     */
	public void init(Object controller) {
		// init the data object from the bus object being edited
/* TODO comment out for avoiding compiling errors  	    
		_data = ((FKExciterData)controller).getData();
*/		
	}
	
	/**
	*	Set controller data to the editor
	*
	* @return false if there is any problem
	*/
    public boolean setData2Editor(String desc) {
  	    kaTextField.setText(Number2String.toStr(_data.getKa(), "#0.00"));
  	    taTextField.setText(Number2String.toStr(_data.getTa(), "#0.000"));
/* TODO comment out for avoiding compiling errors  	    
            trTextField.setText(Number2String.toStr(_data.getTr(), "#0.000"));
            ta1TextField.setText(Number2String.toStr(_data.getTa1(), "#0.000"));
            semaxTextField.setText(Number2String.toStr(_data.getSemax(), "#0.000"));
            efdmaxTextField.setText(Number2String.toStr(_data.getEfdmax(), "#0.000"));
            keTextField.setText(Number2String.toStr(_data.getKe(), "#0.000"));
            teTextField.setText(Number2String.toStr(_data.getTe(), "#0.000"));
            se_e2TextField.setText(Number2String.toStr(_data.getSe_e2(), "#0.000"));
*/
            kfTextField.setText(Number2String.toStr(_data.getKf(), "#0.000"));
  	    trTextField.setText(Number2String.toStr(_data.getTf(), "#0.000"));
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

/* TODO comment out for avoiding compiling errors  	    
	
	if (SwingInputVerifyUtil.within(this.trTextField, 0.001, 10.0, errMsg,
			"Tr is out of the range [0.001, 10]"))
		_data.setTr(SwingInputVerifyUtil.getDouble(trTextField));

	if (SwingInputVerifyUtil.within(this.ta1TextField, 0.001, 10.0, errMsg,
			"Ta1 is out of the range [0.001, 10]"))
		_data.setTa1(SwingInputVerifyUtil.getDouble(ta1TextField));

	if (SwingInputVerifyUtil.within(this.semaxTextField, 0.001, 10.0, errMsg,
			"Semax is out of the range [0.001, 10]"))
		_data.setSemax(SwingInputVerifyUtil.getDouble(semaxTextField));

	if (SwingInputVerifyUtil.within(this.efdmaxTextField, 0.01, 100.0, errMsg,
			"Efdmax is out of the range [0.01, 100.0]"))
		_data.setEfdmax(SwingInputVerifyUtil.getDouble(efdmaxTextField));

	if (SwingInputVerifyUtil.within(this.keTextField, 0.001, 10.0, errMsg,
			"Ke is out of the range [0.001, 10]"))
		_data.setKe(SwingInputVerifyUtil.getDouble(keTextField));

	if (SwingInputVerifyUtil.within(this.teTextField, 0.001, 10.0, errMsg,
			"Te is out of the range [0.001, 10]"))
		_data.setTe(SwingInputVerifyUtil.getDouble(teTextField));

	if (SwingInputVerifyUtil.within(this.se_e2TextField, 0.001, 10.0, errMsg,
			"Se_e2 is out of the range [0.001, 10]"))
		_data.setSe_e2(SwingInputVerifyUtil.getDouble(se_e2TextField));
*/
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
        trLabel = new javax.swing.JLabel();
        trTextField = new javax.swing.JTextField();
        ta1Label = new javax.swing.JLabel();
        ta1TextField = new javax.swing.JTextField();
        semaxLabel = new javax.swing.JLabel();
        semaxTextField = new javax.swing.JTextField();
        keLabel = new javax.swing.JLabel();
        keTextField = new javax.swing.JTextField();
        teLabel = new javax.swing.JLabel();
        teTextField = new javax.swing.JTextField();
        se_e2Label = new javax.swing.JLabel();
        se_e2TextField = new javax.swing.JTextField();
        efdmaxLabel = new javax.swing.JLabel();
        efdmaxTextField = new javax.swing.JTextField();
        tfLabel = new javax.swing.JLabel();
        tfTextField = new javax.swing.JTextField();
        kfLabel = new javax.swing.JLabel();
        kfTextField = new javax.swing.JTextField();
        textareaScrollPane = new javax.swing.JScrollPane();
        pluginInfoTextArea = new javax.swing.JTextArea();

        kaLabel.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
        kaLabel.setText("Ka(pu)");

        kaTextField.setColumns(5);
        kaTextField.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
        kaTextField.setText("40.0");

        taLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        taLabel.setText("Ta(s)");

        taTextField.setColumns(5);
        taTextField.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
        taTextField.setText("0.05");

        trLabel.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
        trLabel.setText("Tr(s)");

        trTextField.setColumns(5);
        trTextField.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
        trTextField.setText("0.04");

        ta1Label.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
        ta1Label.setText("Ta1(s)");

        ta1TextField.setColumns(5);
        ta1TextField.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
        ta1TextField.setText("0.02");

        semaxLabel.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
        semaxLabel.setText("Semax(pu)");

        semaxTextField.setColumns(5);
        semaxTextField.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
        semaxTextField.setText("0.860");

        keLabel.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
        keLabel.setText("Ke(pu)");

        keTextField.setColumns(5);
        keTextField.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
        keTextField.setText("1.0");

        teLabel.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
        teLabel.setText("Te(s)");

        teTextField.setColumns(5);
        teTextField.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
        teTextField.setText("2.0");

        se_e2Label.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
        se_e2Label.setText("Se_e2(pu)");

        se_e2TextField.setColumns(5);
        se_e2TextField.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
        se_e2TextField.setText("0.50");

        efdmaxLabel.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
        efdmaxLabel.setText("Efdmax(pu)");

        efdmaxTextField.setColumns(5);
        efdmaxTextField.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
        efdmaxTextField.setText("5.5");

        tfLabel.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
        tfLabel.setText("Tf(s)");

        tfTextField.setColumns(5);
        tfTextField.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
        tfTextField.setText("0.350");

        kfLabel.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
        kfLabel.setText("Kf(pu)");

        kfTextField.setColumns(5);
        kfTextField.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
        kfTextField.setText("0.03");

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
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, textareaScrollPane, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 368, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(kaLabel)
                            .add(trLabel)
                            .add(semaxLabel)
                            .add(keLabel)
                            .add(se_e2Label)
                            .add(tfLabel))
                        .add(39, 39, 39)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(kaTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(trTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(semaxTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(keTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(se_e2TextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(tfTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                        .add(53, 53, 53)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(taLabel)
                            .add(ta1Label)
                            .add(efdmaxLabel)
                            .add(teLabel)
                            .add(kfLabel))
                        .add(30, 30, 30)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, efdmaxTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, taTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, ta1TextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, teTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, kfTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
                .add(98, 98, 98))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
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
                                .add(trLabel))
                            .add(layout.createSequentialGroup()
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                    .add(ta1TextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                    .add(ta1Label)))
                            .add(layout.createSequentialGroup()
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                                .add(trTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                .add(semaxTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                .add(semaxLabel))
                            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                .add(efdmaxLabel)
                                .add(efdmaxTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                        .add(10, 10, 10)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(teTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(keTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(layout.createSequentialGroup()
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                    .add(se_e2Label)
                                    .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                        .add(se_e2TextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                        .add(kfLabel)))
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                    .add(tfTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                    .add(tfLabel)))
                            .add(kfTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                        .add(46, 46, 46)
                        .add(textareaScrollPane, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 77, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(layout.createSequentialGroup()
                        .add(112, 112, 112)
                        .add(teLabel))
                    .add(layout.createSequentialGroup()
                        .add(115, 115, 115)
                        .add(keLabel)))
                .addContainerGap(38, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel efdmaxLabel;
    private javax.swing.JTextField efdmaxTextField;
    private javax.swing.JLabel kaLabel;
    private javax.swing.JTextField kaTextField;
    private javax.swing.JLabel keLabel;
    private javax.swing.JTextField keTextField;
    private javax.swing.JLabel kfLabel;
    private javax.swing.JTextField kfTextField;
    private javax.swing.JTextArea pluginInfoTextArea;
    private javax.swing.JLabel se_e2Label;
    private javax.swing.JTextField se_e2TextField;
    private javax.swing.JLabel semaxLabel;
    private javax.swing.JTextField semaxTextField;
    private javax.swing.JLabel ta1Label;
    private javax.swing.JTextField ta1TextField;
    private javax.swing.JLabel taLabel;
    private javax.swing.JTextField taTextField;
    private javax.swing.JLabel teLabel;
    private javax.swing.JTextField teTextField;
    private javax.swing.JScrollPane textareaScrollPane;
    private javax.swing.JLabel tfLabel;
    private javax.swing.JTextField tfTextField;
    private javax.swing.JLabel trLabel;
    private javax.swing.JTextField trTextField;
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
             		 input == keTextField ||
             		 input == trTextField ||
             		 input == ta1TextField ||
                         input == semaxTextField ||
                         input == efdmaxTextField ||
                         input == keTextField ||
                         input == se_e2TextField ||
                         input == tfTextField)
    	       		return SwingInputVerifyUtil.getDouble((javax.swing.JTextField)input) >= 0.0;
 	       	} catch (Exception e) {
 	    		return false;
 	       	}		
			return true;
        }
    }
}

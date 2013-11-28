/*
 * Copyright (c) 1995, 2008, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle or the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */ 

package org.interpss.QA.topology;

/*
 * TabbedPaneDemo.java requires one additional file:
 *   images/middle.gif.
 */

import javax.swing.JTabbedPane;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JFrame;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.interpss.common.util.IpssLogger;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.util.Hashtable;
import java.util.List;

public class MultiTopolgy extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7593688717309654683L;
	private static JTabbedPane tabbedPane =null;
	
	public MultiTopolgy(){
		
	}
	public MultiTopolgy(Hashtable<String,Component> compTable){
        super(new GridLayout(1, 1));
        
        tabbedPane = new JTabbedPane();
        //Add the tabbed pane to this panel.
        add(tabbedPane);
        
        //The following line enables to use scrolling tabs.
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        if(compTable!=null){
        	
        	if(!compTable.isEmpty()){
            
                for(String id:compTable.keySet()){
            	  
        	      JComponent panel =createPanel(compTable.get(id));
        	      tabbedPane.addTab(id, panel);

                }
        	}
        	else
        		IpssLogger.getLogger().severe("The input component table is Empty, " +
            			"JGraph component should be initialized first!");
        }
        else
        	IpssLogger.getLogger().severe("The input component table is null, " +
        			"JGraph component should be initialized first!");
        
	}  
       
    public JTabbedPane getTabbedPane(){
    	return tabbedPane;
    }
    
    public static JComponent createPanel(Component comp) {
        JPanel panel = new JPanel(false);
        panel.add(comp);
        panel.setLayout(new GridLayout(1, 1));
        return panel;
    }
    

}

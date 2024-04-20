/*
 * @(#)GridConstants.java   
 *
 * Copyright (C) 2006 www.interpss.com
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
 * @Date 04/15/2011
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.grid;


/**
 * All Grid computing constants
 */
public class GridConstants {
	/*
	 * 
	 */
	public static final String Token_RemoteMsg 			= "[RemoteMessage]";
	public static final String Token_DStabSimuMsg 		= Token_RemoteMsg	+ "DStabSimuAction:";
	public static final String Token_ProgressStatusMsg 	= Token_RemoteMsg + "ProgressStatus:";
	public static final String Token_RemoteNode 		= "Local Node";
	public static final String Token_MasterNode 		= "Master Grid Node";

	public static final String Token_ErrorMsg 		= Token_RemoteMsg + "ErrorMessage:";
	public static final String Token_WarnMsg 		= Token_RemoteMsg + "WarnMessage:";
	public static final String Token_StatusMsg 		= Token_RemoteMsg + "StatusMessage:";
	public static final String Token_InfoMsg 		= Token_RemoteMsg + "InfoMessage:";
	public static final String Token_DebugMsg 		= Token_RemoteMsg + "DebugMessage:";

	/*
	 * Task session attribute key 
	 */
	public static final String SeKey_CaseId 		= "GridRunCaseId";
	public static final String SeKey_MasterNodeId 	= "MasterNodeId";

	public static final String SeKey_RemoteNodeDebug 		= "RemoteNodeDebug";

	public static final String SeKey_RemoteJobCreation 		= "RemoteJobCreation";
	public static final String SeKey_BaseStudyCaseNetModel 	= "BaseStudyCaseNetworkModel";

	public static final String SeKey_AclfOpt_ReturnStudyCase = "AclfOptReturnStudyCase";
	public static final String SeKey_AclfOpt_CalculateViolation = "AclfOptCalculateViolation";
	public static final String SeKey_BusVoltageUpperLimitPU = "BusVoltageUpperLimitPU";
	public static final String SeKey_BusVoltageLowerLimitPU = "BusVoltageLowerLimitPU";

	public static final String SeKey_ApplyRuleBase 		= "ApplyAclfRuleBase";
	public static final String SeKey_RuleBaseXml 		= "AclfRuleBaseXml";
	
	/*
	 * remoteMsgTable key
	 */
	// Rqt - request from Master to remote
	// Rsp - response from remote to master
	// s - String, b - boolean, bAry - byte[]
	public static String MsgKEY_sRemoteNodeId 				= "RemoteNodeId";	
	public static String MsgKEY_sStudyCaseId 				= "StudyCaseId";	
	public static String MsgKEY_Rsp_bReturnStatus 			= "ReturnStatus";
	public static String MsgKEY_Rsp_sReturnMessage 			= "ReturnMessage";
//	public static String MsgKEY_Rqt_sStudyCaseNetModel 		= "StudyCaseNetworModel";	

	public static String MsgKEY_Rqt_sMessagDateType 		= "Msg_Type";	
	public static String MessagDateType_Xml 				= "Msg_Type_Xml";	
	public static String MessagDateType_EMF 				= "Msg_Type_EMF";	

	public static String MsgKEY_Rqt_sNodeCtxAction 			= "NodeCtxAction";	
	public static String NodeCtxAction_create 				= "NodeCtxActionCreate";	
	public static String NodeCtxAction_remove 				= "NodeCtxActionRemove";	
	public static String NodeCtxAction_store 				= "NodeCtxActionStore";	
	public static String NodeCtxAction_query 				= "NodeCtxActionQuery";	
	public static String MsgKEY_sNodeCtxData 				= "NodeCtxData";	
		// when ation = store, query the MsgKEY_sNodeCtxData field is used to carry data in the request
	
	public static String MsgKEY_Rqt_sChildNodection 		= "ChildNodection";	
	public static String ChildNodectionAction_cacheNet 		= "ChildNodectionCacheNet";	
	public static String ChildNodectionAction_runLF 		= "ChildNodectionRunLf";	

	public static String MsgKEY_Rqt_sSwingVUpdate 			= "SwingVUpdate";	
	public static String MsgKEY_Rsp_sBusPQUpdate 			= "BusPQUpdate";	

	public static String MsgKEY_Rqt_sStudyCaseModification 	= "StudyCaseModification";		
	
	public static String MsgKEY_Rqt_sDStabAlgorithm 		= "DStabAlgorithm";	
	
	public static int Const_ReturnAllStudyCases 	= 1;	
	public static int Const_ReturnDivergedCase 		= 2;	
	public static int Const_ReturnNoStudyCase 		= 3;		
}
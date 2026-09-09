/*
 * CIMConstants.java
 *
 * Copyright (C) 2025 www.interpss.org
 *
 * CIM namespace URIs and element type constants for CGMES parsing.
 */

package org.interpss.fadapter.cim;

/**
 * CIM namespace URIs and RDF constants used in CGMES RDF/XML files.
 */
public final class CIMConstants {

    private CIMConstants() {}

    // RDF namespace
    public static final String RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    public static final String RDF_TYPE = RDF_NS + "type";
    public static final String RDF_ABOUT = RDF_NS + "about";
    public static final String RDF_RESOURCE = RDF_NS + "resource";
    public static final String RDF_ID = RDF_NS + "ID";

    // CIM16 namespace (CGMES 2.4.x)
    public static final String CIM16_NS = "http://iec.ch/TC57/2013/CIM-schema-cim16#";

    // CIM100 namespace (CGMES 3.x)
    public static final String CIM100_NS = "http://iec.ch/TC57/CIM100#";

    // ENTSO-E extension namespace
    public static final String ENTSOE_NS = "http://entsoe.eu/CIM/SchemaExtension/3/1#";

    // Model Description namespace
    public static final String MD_NS = "http://iec.ch/TC57/61970-552/ModelDescription/1#";

    // --- CIM16 element class URIs ---
    public static final String CIM_SUBSTATION = CIM16_NS + "Substation";
    public static final String CIM_VOLTAGE_LEVEL = CIM16_NS + "VoltageLevel";
    public static final String CIM_TOPOLOGICAL_NODE = CIM16_NS + "TopologicalNode";
    public static final String CIM_CONNECTIVITY_NODE = CIM16_NS + "ConnectivityNode";
    public static final String CIM_AC_LINE_SEGMENT = CIM16_NS + "ACLineSegment";
    public static final String CIM_POWER_TRANSFORMER = CIM16_NS + "PowerTransformer";
    public static final String CIM_TRANSFORMER_END = CIM16_NS + "PowerTransformerEnd";
    public static final String CIM_ENERGY_CONSUMER = CIM16_NS + "EnergyConsumer";
    public static final String CIM_SYNCHRONOUS_MACHINE = CIM16_NS + "SynchronousMachine";
    public static final String CIM_GENERATING_UNIT = CIM16_NS + "GeneratingUnit";
    public static final String CIM_LINEAR_SHUNT_COMPENSATOR = CIM16_NS + "LinearShuntCompensator";
    public static final String CIM_TERMINAL = CIM16_NS + "Terminal";
    public static final String CIM_BASE_VOLTAGE = CIM16_NS + "BaseVoltage";
    public static final String CIM_OPERATIONAL_LIMIT = CIM16_NS + "OperationalLimit";
    public static final String CIM_CURRENT_LIMIT = CIM16_NS + "CurrentLimit";
    public static final String CIM_ACTIVE_POWER_LIMIT = CIM16_NS + "ActivePowerLimit";
    public static final String CIM_OPERATIONAL_LIMIT_SET = CIM16_NS + "OperationalLimitSet";
    public static final String CIM_OPERATIONAL_LIMIT_TYPE = CIM16_NS + "OperationalLimitType";
    public static final String CIM_TAP_CHANGER = CIM16_NS + "TapChanger";
    public static final String CIM_RATIO_TAP_CHANGER = CIM16_NS + "RatioTapChanger";
    public static final String CIM_PHASE_TAP_CHANGER = CIM16_NS + "PhaseTapChangerLinear";
    public static final String CIM_SWITCH = CIM16_NS + "Switch";
    public static final String CIM_BREAKER = CIM16_NS + "Breaker";
    public static final String CIM_DISCONNECTOR = CIM16_NS + "Disconnector";
    public static final String CIM_BUSBAR_SECTION = CIM16_NS + "BusbarSection";

    // --- CIM100 element class URIs ---
    public static final String CIM100_SUBSTATION = CIM100_NS + "Substation";
    public static final String CIM100_VOLTAGE_LEVEL = CIM100_NS + "VoltageLevel";
    public static final String CIM100_TOPOLOGICAL_NODE = CIM100_NS + "TopologicalNode";
    public static final String CIM100_CONNECTIVITY_NODE = CIM100_NS + "ConnectivityNode";
    public static final String CIM100_AC_LINE_SEGMENT = CIM100_NS + "ACLineSegment";
    public static final String CIM100_POWER_TRANSFORMER = CIM100_NS + "PowerTransformer";
    public static final String CIM100_TRANSFORMER_END = CIM100_NS + "PowerTransformerEnd";
    public static final String CIM100_ENERGY_CONSUMER = CIM100_NS + "EnergyConsumer";
    public static final String CIM100_SYNCHRONOUS_MACHINE = CIM100_NS + "SynchronousMachine";
    public static final String CIM100_GENERATING_UNIT = CIM100_NS + "GeneratingUnit";
    public static final String CIM100_LINEAR_SHUNT_COMPENSATOR = CIM100_NS + "LinearShuntCompensator";
    public static final String CIM100_TERMINAL = CIM100_NS + "Terminal";
    public static final String CIM100_BASE_VOLTAGE = CIM100_NS + "BaseVoltage";

    // --- Common property local names ---
    public static final String PROP_NAME = "IdentifiedObject.name";
    public static final String PROP_DESCRIPTION = "IdentifiedObject.description";
    public static final String PROP_SHORT_NAME = "IdentifiedObject.shortName"; // entsoe extension
    public static final String PROP_R = "ACLineSegment.r";
    public static final String PROP_X = "ACLineSegment.x";
    public static final String PROP_BCH = "ACLineSegment.bch";
    public static final String PROP_GCH = "ACLineSegment.gch";
    public static final String PROP_LENGTH = "Conductor.length";
    public static final String PROP_BASE_VOLTAGE = "ConductingEquipment.BaseVoltage";
    public static final String PROP_EQUIPMENT_CONTAINER = "Equipment.EquipmentContainer";
    public static final String PROP_NOMINAL_VOLTAGE = "VoltageLevel.nominalVoltage";
    public static final String PROP_MEMBER_OF = "VoltageLevel.MemberOf_Substation";
    public static final String PROP_TERMINAL_EQUIPMENT = "Terminal.ConductingEquipment";
    public static final String PROP_TERMINAL_TOPO_NODE = "Terminal.TopologicalNode";
    public static final String PROP_TERMINAL_CONN_NODE = "Terminal.ConnectivityNode";
    public static final String PROP_TRANSFORMER = "TransformerEnd.PowerTransformer";
    public static final String PROP_END_NUMBER = "TransformerEnd.endNumber";
    public static final String PROP_RATED_U = "TransformerEnd.ratedU";
    public static final String PROP_END_R = "TransformerEnd.r";
    public static final String PROP_END_X = "TransformerEnd.x";
    public static final String PROP_B = "TransformerEnd.b";
    public static final String PROP_G = "TransformerEnd.g";
    public static final String PROP_PHASE = "TransformerEnd.phaseAngleClock";
    public static final String PROP_RATED_S = "TransformerEnd.ratedS";
    public static final String PROP_CONNECTION_KIND = "TransformerEnd.connectionKind";
    public static final String PROP_GEN_UNIT = "RotatingMachine.GeneratingUnit";
    public static final String PROP_REGULATING = "RegulatingCondEq.RegulatingControl";
    public static final String PROP_SYNCH_MACHINE_TYPE = "SynchronousMachine.type";
    public static final String PROP_OPERATING_MODE = "SynchronousMachine.operatingMode";
    public static final String PROP_RATED_S_MACHINE = "SynchronousMachine.ratedS";
    public static final String PROP_P = "EnergyConsumer.p";
    public static final String PROP_Q = "EnergyConsumer.q";
    public static final String PROP_GEN_P = "GeneratingUnit.nominalP";
    public static final String PROP_GEN_MIN_P = "GeneratingUnit.minOperatingP";
    public static final String PROP_GEN_MAX_P = "GeneratingUnit.maxOperatingP";
    public static final String PROP_GEN_INITIAL_P = "GeneratingUnit.initialP";
    public static final String PROP_SHUNT_B_PER_SEC = "LinearShuntCompensator.bPerSection";
    public static final String PROP_SHUNT_G_PER_SEC = "LinearShuntCompensator.gPerSection";
    public static final String PROP_SHUNT_SECTIONS = "ShuntCompensator.maximumSections";
    public static final String PROP_SHUNT_NORMAL_SECTIONS = "ShuntCompensator.normalSections";
    public static final String PROP_SHUNT_B0 = "LinearShuntCompensator.b0PerSection";
    public static final String PROP_SHUNT_G0 = "LinearShuntCompensator.g0PerSection";
    public static final String PROP_SSH_P = "EnergyConsumer.pFixed";
    public static final String PROP_SSH_Q = "EnergyConsumer.qFixed";
    public static final String PROP_SSH_GEN_P = "GeneratingUnit.initialP";
    public static final String PROP_SSH_REGULATING = "RegulatingControl.enabled";
    public static final String PROP_SSH_TARGET_V = "RegulatingControl.targetValue";
    public static final String PROP_SSH_TARGET_Q = "RegulatingControl.targetValue";
    public static final String PROP_SSH_TARGET_P = "RegulatingControl.targetValue";
    public static final String PROP_SSH_SVNOMV = "TapChanger.normalStep";
    public static final String PROP_SSH_SVNEUTRAL = "TapChanger.neutralStep";
    public static final String PROP_SSH_SVSTEP = "TapChanger.step";
    public static final String PROP_SSH_SWITCH_OPEN = "Switch.normalOpen";
    public static final String PROP_SV_VOLTAGE_MAG = "SvVoltage.v";
    public static final String PROP_SV_VOLTAGE_ANG = "SvVoltage.angle";
    public static final String PROP_SV_VOLTAGE_TOPO_NODE = "SvVoltage.TopologicalNode";
    public static final String PROP_SV_POWER_FLOW = "SvPowerFlow.p";
    public static final String PROP_SV_POWER_FLOW_Q = "SvPowerFlow.q";
    public static final String PROP_SV_POWER_FLOW_TERM = "SvPowerFlow.Terminal";
    public static final String PROP_TAP_CHANGER_XFR_END = "TapChanger.TransformerEnd";
    public static final String PROP_LIMIT_VALUE = "CurrentLimit.value";
    public static final String PROP_LIMIT_ACTIVE_POWER = "ActivePowerLimit.value";
    public static final String PROP_LIMIT_OPERATIONAL_LIMIT = "OperationalLimit.OperationalLimitSet";
    public static final String PROP_LIMIT_SET_EQUIPMENT = "OperationalLimitSet.Equipment";
    public static final String PROP_LIMIT_TYPE = "OperationalLimit.OperationalLimitType";
}

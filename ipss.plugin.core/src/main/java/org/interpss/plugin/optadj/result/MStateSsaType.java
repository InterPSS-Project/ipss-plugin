package org.interpss.plugin.optadj.result;

public enum MStateSsaType {
	BaseNetwork("��������"),    		// �ڻ��������ϡ���δ��̬������Ȼ�����SSA
	NetOutage("���翪��"),      		// �ڻ��������ϸ��ӿ��ϡ���δ��̬������Ȼ�����SSA
	Net3WXfrOutage("������俪��");  	// �ڻ��������ϸ���������俪�ϡ���δ��̬������Ȼ�����SSA
	
	private String name;
	
	private MStateSsaType(String name) {	
		this.name = name;
	}
	
	public String getName() {
		return this.name;
	}

}

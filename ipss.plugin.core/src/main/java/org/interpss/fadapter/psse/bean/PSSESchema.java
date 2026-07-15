package org.interpss.fadapter.psse.bean;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

public class PSSESchema {
    private General general;
    private Network network;
    
    // Getters and Setters
    public General getGeneral() { return general; }
    public void setGeneral(General general) { this.general = general; }
    public Network getNetwork() { return network; }
    public void setNetwork(Network network) { this.network = network; }
	@Override
	public String toString() {
		Gson gson = new GsonBuilder()
		        .serializeSpecialFloatingPointValues()
		        .create();
    	return gson.toJson(this);
	}

public static class General {
    private String version;
    
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
	@Override
	public String toString() {
		return "General [version=" + version + "]";
	}
}

public static class Network {
    private CaseId caseid;
    private GeneralFields general;
    private Gauss gauss;
    private Newton newton;
    private Adjust adjust;
    private Tysl tysl;
    private Solver solver;
    private Rating rating;
    private Swdratnam swdratnam;
    private Bus bus;
    private Load load;
    private Fixshunt fixshunt;
    private Voltagedroop voltagedroop;
    private Generator generator;
    private Swdratset swdratset;
    private Acline acline;
    private Sysswd sysswd;
    private Transformer transformer;
    private Area area;
    private Twotermdc twotermdc;
    private Vscdc vscdc;
    private Impcor impcor;
    private Ntermdc ntermdc;
    private Ntermdcconv ntermdcconv;
    private Ntermdcbus ntermdcbus;
    private Ntermdclink ntermdclink;
    private Msline msline;
    private Zone zone;
    private Iatrans iatrans;
    private Owner owner;
    private Facts facts;
    private Swshunt swshunt;
    private Gne gne;
    private Indmach indmach;
    private Loadtype loadtype;
    @SerializedName("interface")
    private Interface interface_;
    private Itfelmt itfelmt;
    private Sub sub;
    private Subnode subnode;
    private Subswd subswd;
    private Subterm subterm;
    
    public CaseId getCaseid() { return caseid; }
    public void setCaseid(CaseId caseid) { this.caseid = caseid; }
    public GeneralFields getGeneral() { return general; }
    public void setGeneral(GeneralFields general) { this.general = general; }
    public Gauss getGauss() { return gauss; }
	public void setGauss(Gauss gauss) { this.gauss = gauss; }
	public Newton getNewton() { return newton; }
	public void setNewton(Newton newton) { this.newton = newton; }
	public Adjust getAdjust() { return adjust; }
	public void setAdjust(Adjust adjust) { this.adjust = adjust; }
	public Tysl getTysl() { return tysl; }
	public void setTysl(Tysl tysl) { this.tysl = tysl; }
	public Solver getSolver() { return solver; }
	public void setSolver(Solver solver) { this.solver = solver; }
	public Rating getRating() { return rating; }
	public void setRating(Rating rating) { this.rating = rating; }
	public Swdratnam getSwdratnam() { return swdratnam; }
	public void setSwdratnam(Swdratnam swdratnam) { this.swdratnam = swdratnam; }
	public Bus getBus() { return bus; }
	public void setBus(Bus bus) { this.bus = bus; }
	public Load getLoad() { return load; }
	public void setLoad(Load load) { this.load = load; }
	public Fixshunt getFixshunt() { return fixshunt; }
	public void setFixshunt(Fixshunt fixshunt) { this.fixshunt = fixshunt; }
	public Voltagedroop getVoltagedroop() { return voltagedroop; }
	public void setVoltagedroop(Voltagedroop voltagedroop) { this.voltagedroop = voltagedroop; }
	public Generator getGenerator() { return generator; }
	public void setGenerator(Generator generator) { this.generator = generator; }
	public Swdratset getSwdratset() { return swdratset; }
	public void setSwdratset(Swdratset swdratset) { this.swdratset = swdratset; }
	public Acline getAcline() { return acline; }
	public void setAcline(Acline acline) { this.acline = acline; }
	public Sysswd getSysswd() { return sysswd; }
	public void setSysswd(Sysswd sysswd) { this.sysswd = sysswd; }
	public Transformer getTransformer() { return transformer; }
	public void setTransformer(Transformer transformer) { this.transformer = transformer; }
	public Area getArea() { return area; }
	public void setArea(Area area) { this.area = area; }
	public Twotermdc getTwotermdc() { return twotermdc; }
	public void setTwotermdc(Twotermdc twotermdc) { this.twotermdc = twotermdc; }
	public Vscdc getVscdc() { return vscdc; }
	public void setVscdc(Vscdc vscdc) { this.vscdc = vscdc; }
	public Impcor getImpcor() { return impcor; }
	public void setImpcor(Impcor impcor) { this.impcor = impcor; }
	public Ntermdc getNtermdc() { return ntermdc; }
	public void setNtermdc(Ntermdc ntermdc) { this.ntermdc = ntermdc; }
	public Ntermdcconv getNtermdcconv() { return ntermdcconv; }
	public void setNtermdcconv(Ntermdcconv ntermdcconv) { this.ntermdcconv = ntermdcconv; }
	public Ntermdcbus getNtermdcbus() { return ntermdcbus; }
	public void setNtermdcbus(Ntermdcbus ntermdcbus) { this.ntermdcbus = ntermdcbus; }
	public Ntermdclink getNtermdclink() { return ntermdclink; }
	public void setNtermdclink(Ntermdclink ntermdclink) { this.ntermdclink = ntermdclink; }
	public Msline getMsline() { return msline; }
	public void setMsline(Msline msline) { this.msline = msline; }
	public Zone getZone() { return zone; }
	public void setZone(Zone zone) { this.zone = zone; }
	public Iatrans getIatrans() { return iatrans; }
	public void setIatrans(Iatrans iatrans) { this.iatrans = iatrans; }
	public Owner getOwner() { return owner; }
	public void setOwner(Owner owner) { this.owner = owner; }
	public Facts getFacts() { return facts; }
	public void setFacts(Facts facts) { this.facts = facts; }
	public Swshunt getSwshunt() { return swshunt; }
	public void setSwshunt(Swshunt swshunt) { this.swshunt = swshunt; }
	public Gne getGne() { return gne; }
	public void setGne(Gne gne) { this.gne = gne; }
	public Indmach getIndmach() { return indmach; }
	public void setIndmach(Indmach indmach) { this.indmach = indmach; }
	public Loadtype getLoadtype() { return loadtype; }
	public void setLoadtype(Loadtype loadtype) { this.loadtype = loadtype; }
	public Interface getInterface_() { return interface_; }
	public void setInterface_(Interface interface_) { this.interface_ = interface_; }
	public Itfelmt getItfelmt() { return itfelmt; }
	public void setItfelmt(Itfelmt itfelmt) { this.itfelmt = itfelmt; }
	public Sub getSub() { return sub; }
	public void setSub(Sub sub) { this.sub = sub; }
	public Subnode getSubnode() { return subnode; }
	public void setSubnode(Subnode subnode) { this.subnode = subnode; }
	public Subswd getSubswd() { return subswd; }
	public void setSubswd(Subswd subswd) { this.subswd = subswd; }
	public Subterm getSubterm() { return subterm; }
	public void setSubterm(Subterm subterm) { this.subterm = subterm; }
	@Override
	public String toString() {
		return "Network [caseid=" + caseid + ", general=" + general + ", gauss=" + gauss + ", newton=" + newton
				+ ", adjust=" + adjust + ", tysl=" + tysl + ", solver=" + solver + ", rating=" + rating + ", swdratnam="
				+ swdratnam + ", bus=" + bus + ", load=" + load + ", fixshunt=" + fixshunt + ", voltagedroop="
				+ voltagedroop + ", generator=" + generator + ", swdratset=" + swdratset + ", acline=" + acline
				+ ", sysswd=" + sysswd + ", transformer=" + transformer + ", area=" + area + ", twotermdc=" + twotermdc
				+ ", vscdc=" + vscdc + ", impcor=" + impcor + ", ntermdc=" + ntermdc + ", ntermdcconv=" + ntermdcconv
				+ ", ntermdcbus=" + ntermdcbus + ", ntermdclink=" + ntermdclink + ", msline=" + msline + ", zone="
				+ zone + ", iatrans=" + iatrans + ", owner=" + owner + ", facts=" + facts + ", swshunt=" + swshunt
				+ ", gne=" + gne + ", indmach=" + indmach + ", loadtype=" + loadtype + ", interface_=" + interface_
				+ ", itfelmt=" + itfelmt + ", sub=" + sub + ", subnode=" + subnode + ", subswd=" + subswd + ", subterm="
				+ subterm + "]";
	}
}

public static class CaseId extends Field_Data{}
public static class GeneralFields extends Field_Data{}
public static class Gauss extends Field_Data{}
public static class Newton extends Field_Data{}
public static class Adjust extends Field_Data{}
public static class Tysl extends Field_Data{}
public static class Solver extends Field_Data{}
public static class Rating extends Field_Data{}
public static class Swdratnam  extends Field_Data{}
public static class Bus extends Field_Data{}
public static class Load extends Field_Data{}
public static class Fixshunt extends Field_Data{}
public static class Voltagedroop extends Field_Data{}
public static class Generator extends Field_Data{}
public static class Swdratset extends Field_Data{}
public static class Acline extends Field_Data{}
public static class Sysswd extends Field_Data{}
public static class Transformer extends Field_Data{}
public static class Area extends Field_Data{}
public static class Twotermdc extends Field_Data{}
public static class Vscdc extends Field_Data{}
public static class Impcor extends Field_Data{}
public static class Ntermdc extends Field_Data{}
public static class Ntermdcconv extends Field_Data{}
public static class Ntermdcbus extends Field_Data{}
public static class Ntermdclink extends Field_Data{}
public static class Msline extends Field_Data{}
public static class Zone extends Field_Data{}
public static class Iatrans extends Field_Data{}
public static class Owner extends Field_Data{}
public static class Facts extends Field_Data{}
public static class Swshunt extends Field_Data{}
public static class Gne extends Field_Data{}
public static class Indmach extends Field_Data{}
public static class Loadtype extends Field_Data{}
public static class Interface extends Field_Data{}
public static class Itfelmt extends Field_Data{}
public static class Sub extends Field_Data{}
public static class Subnode extends Field_Data{}
public static class Subswd extends Field_Data{}
public static class Subterm extends Field_Data{}

public static class Field_Data {
    private List<String> fields;
    private List<Object> data;
    
    public List<String> getFields() { return fields; }
    public void setFields(List<String> fields) { this.fields = fields; }
    public List<Object> getData() { return data; }
    public void setData(List<Object> data) { this.data = data; }
    public String[] getFieldAry() { return this.fields.toArray(new String[] {}); }
	@Override
	public String toString() {
		Gson gson = new GsonBuilder()
		        .serializeSpecialFloatingPointValues()
		        .create();
    	return gson.toJson(this);
	}
}

}

package edu.duke.cs.osprey.energy;

import edu.duke.cs.osprey.confspace.RCTuple;
import edu.duke.cs.osprey.confspace.SimpleConfSpace;
import edu.duke.cs.osprey.energy.ResidueInteractions.Pair;

/** Residue interactions generator */
public class ResInterGen {
	
	public static interface IntraOffsetter {
		double makeOffset(int pos, int rc);
	}
	
	public static interface InterOffsetter {
		double makeOffset(int pos1, int rc1, int pos2, int rc2);
	}
	
	public static interface ShellOffsetter {
		double makeOffset(int pos, int rc, String shellResNum);
	}
	
	private final SimpleConfSpace confSpace;
	private final ResidueInteractions inters;
	
	public static ResInterGen of(SimpleConfSpace confSpace) {
		return new ResInterGen(confSpace);
	}
	
	private ResInterGen(SimpleConfSpace confSpace) {
		this.confSpace = confSpace;
		this.inters = new ResidueInteractions();
	}
	
	public ResidueInteractions make() {
		return inters;
	}
	
	public String getResNum(int pos) {
		return confSpace.positions.get(pos).resNum;
	}
	
	public ResInterGen addIntra(int pos) {
		return addIntra(pos, Pair.IdentityWeight, Pair.IdentityOffset);
	}
	
	public ResInterGen addIntra(int pos, double weight, double offset) {	
		inters.addSingle(getResNum(pos), weight, offset);
		return this;
	}
	
	public ResInterGen addIntras(RCTuple frag) {
		return addIntras(frag, Pair.IdentityWeight, (int pos, int rc) -> Pair.IdentityOffset);
	}
	
	public ResInterGen addIntras(RCTuple frag, double weight, IntraOffsetter offsetter) {
		for (int i=0; i<frag.size(); i++) {
			int pos = frag.pos.get(i);
			int rc = frag.RCs.get(i);
			inters.addSingle(getResNum(pos), weight, offsetter.makeOffset(pos, rc));
		}
		return this;
	}
	
	public ResInterGen addInter(int pos1, int pos2) {
		return addInter(pos1, pos2, Pair.IdentityWeight, Pair.IdentityOffset);
	}
	
	public ResInterGen addInter(int pos1, int pos2, double weight, double offset) {
		inters.addPair(getResNum(pos1), getResNum(pos2), weight, offset);
		return this;
	}
	
	public ResInterGen addInters(RCTuple frag) {
		return addInters(frag, Pair.IdentityWeight, (int pos1, int rc1, int pos2, int rc2) -> Pair.IdentityOffset);
	}
	
	public ResInterGen addInters(RCTuple frag, double weight, InterOffsetter offsetter) {
		for (int i=0; i<frag.size(); i++) {
			int pos1 = frag.pos.get(i);
			int rc1 = frag.RCs.get(i);
			String resNum1 = getResNum(pos1);
			for (int j=0; j<i; j++) {
				int pos2 = frag.pos.get(j);
				int rc2 = frag.RCs.get(j);
				String resNum2 = getResNum(pos2);
				inters.addPair(resNum1, resNum2, weight, offsetter.makeOffset(pos1, rc1, pos2, rc2));
			}
		}
		return this;
	}
	
	public ResInterGen addShell(int pos) {
		return addShell(pos, Pair.IdentityWeight, Pair.IdentityOffset);
	}
	
	public ResInterGen addShell(int pos, double weight, double offset) {
		String resNum = getResNum(pos);
		for (String resNumShell : confSpace.shellResNumbers) {
			inters.addPair(resNum, resNumShell, weight, offset);
		}
		return this;
	}
	
	public ResInterGen addShell(RCTuple frag) {
		return addShell(frag, Pair.IdentityWeight, (int pos, int rc, String shellResNum) -> Pair.IdentityOffset);
	}
	
	public ResInterGen addShell(RCTuple frag, double weight, ShellOffsetter offsetter) {
		for (int i=0; i<frag.size(); i++) {
			int pos = frag.pos.get(i);
			int rc = frag.RCs.get(i);
			String resNum = getResNum(pos);
			for (String shellResNum : confSpace.shellResNumbers) {
				inters.addPair(resNum, shellResNum, weight, offsetter.makeOffset(pos, rc, shellResNum));
			}
		}
		return this;
	}
}

package edu.duke.cs.osprey.multistatekstar;

public class KStarScoreUpperBound extends KStarScoreLowerBound {

	public KStarScoreUpperBound(MSKStarSettings settings) {
		super(settings);
	}
	
	@Override
	protected void compute(int state, int maxNumConfs) {
		super.compute(state, maxNumConfs);
		//bound state partition function is an upper bound, so check 
		//against state-specific constraints that are lower bounds
		if(state == partitionFunctions.length-1) {
			if(constrSatisfied)
				constrSatisfied = checkConstraints(state, true);
		}
	}

}

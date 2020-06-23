package edu.duke.cs.osprey.coffee.directors;

import edu.duke.cs.osprey.astar.conf.RCs;
import edu.duke.cs.osprey.coffee.Coffee;
import edu.duke.cs.osprey.coffee.NodeProcessor;
import edu.duke.cs.osprey.coffee.directions.Directions;
import edu.duke.cs.osprey.confspace.MultiStateConfSpace;
import edu.duke.cs.osprey.confspace.Sequence;
import edu.duke.cs.osprey.tools.MathTools.DoubleBounds;


public class SequenceDirector implements Coffee.Director {

	public final MultiStateConfSpace confSpace;
	public final Sequence seq;
	public final double gWidthMax;

	private final DoubleBounds[] freeEnergies;

	public SequenceDirector(MultiStateConfSpace confSpace, Sequence seq, double gWidthMax) {

		this.confSpace = confSpace;
		this.seq = seq;
		this.gWidthMax = gWidthMax;

		freeEnergies = new DoubleBounds[confSpace.states.size()];
	}

	@Override
	public void init(Directions directions, NodeProcessor processor) {

		// set the node trees for each state to just the specified sequence
		directions.setTrees(confSpace.states.stream()
			.map(state -> seq.makeRCs(state.confSpace))
			.toArray(RCs[]::new)
		);
	}

	@Override
	public void direct(Directions directions, NodeProcessor processor) {

		// process all the states to the desired precision
		for (var state : confSpace.states) {

			// calc the pfunc
			var pfunc = new PfuncDirector(confSpace, state, seq, gWidthMax, PfuncDirector.Timing.Efficient);
			freeEnergies[state.index] = pfunc.calc(directions, processor);

			// state complete, clear the nodes
			processor.nodedb.clear(state.index);
		}

		// all done, stop the computation
		directions.stop();
	}

	public DoubleBounds getFreeEnergy(MultiStateConfSpace.State state) {
		return freeEnergies[state.index];
	}
}

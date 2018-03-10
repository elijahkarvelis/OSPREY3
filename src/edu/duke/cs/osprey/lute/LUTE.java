package edu.duke.cs.osprey.lute;

import edu.duke.cs.osprey.confspace.Conf;
import edu.duke.cs.osprey.confspace.ConfDB;
import edu.duke.cs.osprey.confspace.RCTuple;
import edu.duke.cs.osprey.confspace.SimpleConfSpace;
import edu.duke.cs.osprey.ematrix.EnergyMatrix;
import edu.duke.cs.osprey.energy.ConfEnergyCalculator;
import edu.duke.cs.osprey.energy.EnergyCalculator;
import edu.duke.cs.osprey.pruning.PruningMatrix;
import edu.duke.cs.osprey.tools.Progress;
import edu.duke.cs.osprey.tools.Stopwatch;
import org.apache.commons.math3.linear.*;

import java.util.*;

import static edu.duke.cs.osprey.tools.Log.log;


public class LUTE {

	public static class LinearSystem {

		public final List<RCTuple> tuples;
		public final List<int[]> confs;
		public final RealMatrix A;
		public final RealVector b;

		public RealVector x = null;
		public RealVector residual = null;
		public Errors errors = null;

		public LinearSystem(Set<RCTuple> tuples, Map<RCTuple,Set<int[]>> samplesByTuple, Map<int[],Double> energies) {

			// linearize the tuples
			this.tuples = new ArrayList<>(tuples);

			// linearize the conformations
			Set<int[]> confsSet = new Conf.Set();
			for (Collection<int[]> samples : samplesByTuple.values()) {
				confsSet.addAll(samples);
			}
			confs = new ArrayList<>(confsSet);

			// build the linear model: Ax=b
			A = new OpenMapRealMatrix(confs.size(), this.tuples.size());
			b = new ArrayRealVector(confs.size());
			for (int row=0; row<confs.size(); row++) {
				int[] conf = confs.get(row);
				for (int col=0; col<this.tuples.size(); col++) {
					RCTuple tuple = this.tuples.get(col);
					if (Conf.containsTuple(conf, tuple)) {
						A.setEntry(row, col, 1);
					}
				}
				b.setEntry(row, energies.get(conf));
			}
		}

		public void fit() {

			// any linear least squares solver should work here
			// I generally like to use QR factorization, but it's a bit slow here
			// so let's use conjugate gradient iterative optimization instead

			//return new QRDecomposition(A).getSolver().solve(b);

			// TODO: CG is a bit slow too... need to optimize!
			// (Mark's code is really fast here, so we should be able to go fast too)

			// need square A though, so transform Ax=b to A^tAx = A^tb
			RealMatrix At = A.transpose();
			RealMatrix AtA = At.multiply(A);
			RealVector Atb = At.operate(b);
			setX(new ConjugateGradient(100000, 1e-6, false).solve((RealLinearOperator)AtA, Atb));
		}

		public void setX(RealVector x) {
			this.x = x;
			residual = A.operate(x).subtract(b);
			errors = new Errors(residual);
		}
	}

	public static class Errors {

		public final RealVector residual;
		public final double min;
		public final double max;
		public final double avg;
		public final double rms;

		public Errors(RealVector residual) {

			this.residual = residual;

			double sum = 0.0;
			double sumsq = 0.0;
			double min = Double.POSITIVE_INFINITY;
			double max = Double.NEGATIVE_INFINITY;

			int n = residual.getDimension();
			for (int row=0; row<n; row++) {

				double val = Math.abs(residual.getEntry(row));

				sum += val;
				sumsq += val*val;
				min = Math.min(min, val);
				max = Math.max(max, val);
			}

			this.min = min;
			this.max = max;
			this.avg = sum/n;
			this.rms = Math.sqrt(sumsq/n);
		}

		@Override
		public String toString() {
			return String.format("range [%8.4f,%8.4f]   avg %8.4f   rms %8.4f", min, max, avg, rms);
		}
	}

	public final SimpleConfSpace confSpace;

	private final Set<RCTuple> tuples = new LinkedHashSet<>();

	private LinearSystem trainingSystem = null;
	private LinearSystem testSystem = null;

	public LUTE(SimpleConfSpace confSpace) {
		this.confSpace = confSpace;
	}

	public void addUnprunedPairTuples(PruningMatrix pmat) {

		for (int pos1=0; pos1<pmat.getNumPos(); pos1++) {
			for (int rc1=0; rc1<pmat.getNumConfAtPos(pos1); rc1++) {

				// skip pruned singles
				if (pmat.isSinglePruned(pos1, rc1)) {
					continue;
				}

				for (int pos2=0; pos2<pos1; pos2++) {
					for (int rc2=0; rc2<pmat.getNumConfAtPos(pos2); rc2++) {

						// skip pruned singles
						if (pmat.isSinglePruned(pos2, rc2)) {
							continue;
						}

						// skip pruned tuples
						if (pmat.isPairPruned(pos1, rc1, pos2, rc2)) {
							continue;
						}

						// we found it! It's an unpruned pair!
						// NOTE: make the tuple in pos2, pos1 order so the positions are already sorted
						// (because pos2 < pos1 by definition)
						addTuple(new RCTuple(pos2, rc2, pos1, rc1));
					}
				}
			}
		}
	}

	public void addUnprunedTripleTuples(PruningMatrix pmat) {

		for (int pos1=0; pos1<pmat.getNumPos(); pos1++) {
			for (int rc1=0; rc1<pmat.getNumConfAtPos(pos1); rc1++) {

				// skip pruned singles
				if (pmat.isSinglePruned(pos1, rc1)) {
					continue;
				}

				for (int pos2=0; pos2<pos1; pos2++) {
					for (int rc2=0; rc2<pmat.getNumConfAtPos(pos2); rc2++) {

						// skip pruned singles
						if (pmat.isSinglePruned(pos2, rc2)) {
							continue;
						}

						// skip pruned pairs
						if (pmat.isPairPruned(pos1, rc1, pos2, rc2)) {
							continue;
						}

						for (int pos3=0; pos3<pos2; pos3++) {
							for (int rc3=0; rc3<pmat.getNumConfAtPos(pos3); rc3++) {

								// skip pruned singles
								if (pmat.isSinglePruned(pos3, rc3)) {
									continue;
								}

								// skip pruned pairs
								if (pmat.isPairPruned(pos1, rc1, pos3, rc3)) {
									continue;
								}
								if (pmat.isPairPruned(pos2, rc2, pos3, rc3)) {
									continue;
								}

								// we found it! It's an unpruned triple!
								// NOTE: make the tuple in pos3, pos2, pos1 order so the positions are already sorted
								// (because pos3 < pos2 < pos1 by definition)
								addTuple(new RCTuple(pos3, rc3, pos2, rc2, pos1, rc1));
							}
						}
					}
				}
			}
		}
	}

	public void addTuple(RCTuple tuple) {
		tuple.checkSortedPositions();
		tuples.add(tuple);
	}

	public Set<RCTuple> getTuples() {
		// don't allow callers to directly modify the tuple set
		// we need to maintain the ordering of tuple positions
		return Collections.unmodifiableSet(tuples);
	}

	public void fit(ConfEnergyCalculator confEcalc, ConfDB.ConfTable confTable, int minSamplesPerTuple, ConfSampler sampler) {

		// sample the training and test sets
		log("sampling confs for %d tuples...", tuples.size());
		Stopwatch samplingSw = new Stopwatch().start();
		Map<RCTuple,Set<int[]>> trainingSet = sampler.sampleConfsForTuples(tuples, minSamplesPerTuple);
		Map<RCTuple,Set<int[]>> testSet = sampler.sampleConfsForTuples(tuples, minSamplesPerTuple);
		log("done in %s", samplingSw.stop().getTime(2));

		// count all the samples
		Set<int[]> trainingSamples = new Conf.Set();
		for (Set<int[]> samples : trainingSet.values()) {
			trainingSamples.addAll(samples);
		}
		Set<int[]> testSamples = new Conf.Set();
		for (Set<int[]> samples : testSet.values()) {
			testSamples.addAll(samples);
		}
		Map<int[],Double> energies = new Conf.Map<>();
		for (Map<RCTuple,Set<int[]>> set : Arrays.asList(trainingSet, testSet)) {
			for (Set<int[]> samples : set.values()) {
				for (int[] conf : samples) {
					energies.put(conf, null);
				}
			}
		}
		log("sampled %d training confs, %d test confs, %d confs total (%.2f%% overlap)",
			trainingSamples.size(), testSamples.size(), energies.size(),
			100.0*(trainingSamples.size() + testSamples.size() - energies.size())/energies.size()
		);

		// calculate energies for all the samples
		Progress progress = new Progress(energies.size());
		log("calculating energies for %d samples...", progress.getTotalWork());
		for (Map.Entry<int[],Double> entry : energies.entrySet()) {
			int[] conf = entry.getKey();
			confEcalc.calcEnergyAsync(new RCTuple(conf), confTable, (energy) -> {
				entry.setValue(energy);
				progress.incrementProgress();
			});
		}
		confEcalc.tasks.waitForFinish();
		log("samples energy range: [%.4f,%.4f]",
			energies.values().stream().min(Comparator.comparingDouble(d -> d)).get(),
			energies.values().stream().max(Comparator.comparingDouble(d -> d)).get()
		);

		// fit the linear system to the training set
		Stopwatch trainingSw = new Stopwatch().start();
		trainingSystem = new LinearSystem(tuples, trainingSet, energies);
		log("fitting %d confs to %d tuples ...", energies.size(), this.tuples.size());
		trainingSystem.fit();
		log("done in %s", trainingSw.stop().getTime(2));
		log("training errors: %s", trainingSystem.errors);

		// analyze the test set errors
		testSystem = new LinearSystem(tuples, testSet, energies);
		testSystem.setX(trainingSystem.x);
		log("    test errors: %s", testSystem.errors);
	}

	public LinearSystem getTrainingSystem() {
		return trainingSystem;
	}

	public LinearSystem getTestSystem() {
		return testSystem;
	}

	public LUTEConfEnergyCalculator makeConfEnergyCalculator(EnergyCalculator ecalc) {
		return new LUTEConfEnergyCalculator(this, ecalc);
	}

	public EnergyMatrix makeEnergyMatrix() {

		EnergyMatrix emat = new EnergyMatrix(confSpace);

		// set constant and singles to 0
		emat.setConstTerm(0.0);
		for (SimpleConfSpace.Position pos : confSpace.positions) {
			for (SimpleConfSpace.ResidueConf rc : pos.resConfs) {
				emat.setOneBody(pos.index, rc.index, 0.0);
			}
		}

		// set pairs
		for (int i=0; i<trainingSystem.tuples.size(); i++) {
			RCTuple tuple = trainingSystem.tuples.get(i);
			if (tuple.size() == 2) {
				emat.setPairwise(
					tuple.pos.get(0),
					tuple.RCs.get(0),
					tuple.pos.get(1),
					tuple.RCs.get(1),
					trainingSystem.x.getEntry(i)
				);
			}
		}

		return emat;
	}
}

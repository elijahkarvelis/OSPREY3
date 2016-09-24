package edu.duke.cs.osprey.minimization;

import java.util.ArrayList;
import java.util.List;

import edu.duke.cs.osprey.confspace.ConfSearch.EnergiedConf;
import edu.duke.cs.osprey.confspace.ConfSearch.ScoredConf;
import edu.duke.cs.osprey.confspace.ConfSpace;
import edu.duke.cs.osprey.confspace.ParameterizedMoleculeCopy;
import edu.duke.cs.osprey.confspace.RCTuple;
import edu.duke.cs.osprey.energy.EnergyFunction;
import edu.duke.cs.osprey.parallelism.TaskExecutor;
import edu.duke.cs.osprey.structure.Molecule;
import edu.duke.cs.osprey.tools.Factory;
import edu.duke.cs.osprey.tools.ObjectPool;
import edu.duke.cs.osprey.tools.Progress;

public class ConfMinimizer {
	
	public double minimize(ParameterizedMoleculeCopy pmol, int[] conf, EnergyFunction efunc, ConfSpace confSpace) {
		RCTuple tuple = new RCTuple(conf);
		MoleculeModifierAndScorer mof = new MoleculeModifierAndScorer(efunc, confSpace, tuple, pmol);
		Minimizer minimizer = new CCDMinimizer(mof, true);
		minimizer.minimize();
		mof.cleanup();
		return efunc.getEnergy();
	}
	
	public EnergiedConf minimize(ParameterizedMoleculeCopy pmol, ScoredConf conf, EnergyFunction efunc, ConfSpace confSpace) {
		double energy = minimize(pmol, conf.getAssignments(), efunc, confSpace);
		return new EnergiedConf(conf, energy);
	}
	
	public List<EnergiedConf> minimize(ParameterizedMoleculeCopy pmol, List<ScoredConf> confs, EnergyFunction efunc, ConfSpace confSpace) {
		Progress progress = new Progress(confs.size());
		List<EnergiedConf> econfs = new ArrayList<>();
		for (ScoredConf conf : confs) {
			econfs.add(minimize(pmol, conf, efunc, confSpace));
			progress.incrementProgress();
		}
		return econfs;
	}
	
	public List<EnergiedConf> minimize(List<ScoredConf> confs, Factory<? extends EnergyFunction,Molecule> efuncs, ConfSpace confSpace) {
		return minimize(confs, efuncs, confSpace, new TaskExecutor());
	}
	
	public List<EnergiedConf> minimize(List<ScoredConf> confs, Factory<? extends EnergyFunction,Molecule> efuncs, ConfSpace confSpace, TaskExecutor tasks) {
		
		final Progress progress = new Progress(confs.size());
		
		List<EnergiedConf> econfs = new ArrayList<>(confs.size());
		for (int i=0; i<confs.size(); i++) {
			econfs.add(null);
		}
		
		Async async = new Async(efuncs, confSpace, tasks);
		async.setListener(new Async.Listener() {
			@Override
			public void onMinimized(EnergiedConf econf, Integer id) {
				econfs.set(id, econf);
				progress.incrementProgress();
			}
		});
		
		// minimize them all
		for (int i=0; i<confs.size(); i++) {
			async.minimizeAsync(confs.get(i), i);
		}
		async.waitForFinish();
		async.cleanup();
		
		return econfs;
	}
	
	public static class Async {
		
		private static class EfuncAndPMC {
			public ParameterizedMoleculeCopy pmol;
			public EnergyFunction efunc;
		}
		
		private class Task implements Runnable {
			
			public Integer id;
			public ScoredConf conf;
			public EnergiedConf minimizedConf;
			
			@Override
			public void run() {
				minimizedConf = minimizeSync(conf);
			}
		}
		
		public static interface Listener {
			void onMinimized(EnergiedConf econf, Integer id);
		}
	
		private ConfSpace confSpace;
		private TaskExecutor tasks;
		private Listener externalListener;
		private ConfMinimizer minimizer;
		private TaskExecutor.TaskListener taskListener;
		private ObjectPool<EfuncAndPMC> pool;
	
		public Async(Factory<? extends EnergyFunction,Molecule> efuncs, ConfSpace confSpace, TaskExecutor tasks) {
			
			this.confSpace = confSpace;
			this.tasks = tasks;
			
			externalListener = null;
			minimizer = new ConfMinimizer();
			
			// init the listener to collect all the minimized confs
			taskListener = new TaskExecutor.TaskListener() {
				@Override
				public void onFinished(Runnable taskBase) {
					
					Task task = (Task)taskBase;
					
					// chain listeners
					externalListener.onMinimized(task.minimizedConf, task.id);
				}
			};
			
			// make a pool for molecules and energy functions
			// to keep concurrent tasks from racing each other
			pool = new ObjectPool<>(new Factory<EfuncAndPMC,Void>() {
				@Override
				public EfuncAndPMC make(Void context) {
					
					EfuncAndPMC out = new EfuncAndPMC();
					out.pmol = new ParameterizedMoleculeCopy(confSpace);
					out.efunc = efuncs.make(out.pmol.getCopiedMolecule());
					return out;
				}
			});
		}
		
		public EnergiedConf minimizeSync(ScoredConf conf) {
			EfuncAndPMC em = pool.checkout();
			try {
				return minimizer.minimize(em.pmol, conf, em.efunc, confSpace);
			} finally {
				pool.release(em);
			}
		}
		
		public void setListener(Listener val) {
			externalListener = val;
		}
		
		public void minimizeAsync(ScoredConf conf) {
			minimizeAsync(conf, null);
		}
		
		public void minimizeAsync(ScoredConf conf, Integer id) {
			
			if (externalListener == null) {
				throw new IllegalStateException("call setListener() before minimizng asynchronously, this is a bug");
			}
			
			// submit the minimization task
			Task task = new Task();
			task.id = id;
			task.conf = conf;
			tasks.submit(task, taskListener);
		}
		
		public void waitForFinish() {
			tasks.waitForFinish();
		}
		
		public void cleanup() {
			
			// cleanup the energy functions if needed
			for (EfuncAndPMC em : pool) {
				if (em.efunc instanceof EnergyFunction.NeedsCleanup) {
					((EnergyFunction.NeedsCleanup)em.efunc).cleanup();
				}
			}
			pool.clear();
		}
	}
}

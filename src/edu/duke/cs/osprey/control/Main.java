/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.duke.cs.osprey.control;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import edu.duke.cs.osprey.energy.LigandResEnergies;
import edu.duke.cs.osprey.energy.MultiTermEnergyFunction;
import edu.duke.cs.osprey.gmec.GMECFinder;
import edu.duke.cs.osprey.gmec.SeqGMECFinder;
import edu.duke.cs.osprey.kstar.KSConfigFileParser;
import edu.duke.cs.osprey.minimization.CCDMinimizer;
import edu.duke.cs.osprey.parallelism.ThreadParallelism;
import edu.duke.cs.osprey.tools.FileTools;
import edu.duke.cs.osprey.tools.Stopwatch;

/**
 *
 * @author mhall44
 * Parse arguments and call high-level functions like DEE/A* and K*
   These will each be controlled by dedicated classes, unlike in the old KSParser
   to keep this class more concise, and to separate these functions for modularity purposes
 */

public class Main {
	
	public static final String Version = FileTools.readResource("/config/version").trim();
	
	public static enum Command {
		
		/**
		 * Prints the version and exits
		 */
		Version {
			@Override
			public void run(CommandArgs args) {
				System.out.println("OSPREY version: " + Main.Version);
			}
		},
		
		FindGMEC {
			@Override
			public void run(CommandArgs args) {
				ConfigFileParser cfp = args.loadConfig();
				GMECFinder gf = new GMECFinder();
				gf.init(cfp);
				gf.calcGMEC();
				args.cleanupConfig(cfp);
			}
		},
		
		FindSequences {
			@Override
			public void run(CommandArgs args) {
				ConfigFileParser cfp = args.loadConfig();
				GMECFinder gf = new GMECFinder();
				gf.init(cfp);
				gf.calcSequences();
				args.cleanupConfig(cfp);
			}
		},
		
		CalcKStar {
			@Override
			public void run(CommandArgs args) {
				ConfigFileParser cfp = args.loadConfig();
				KSConfigFileParser ksCfp = new KSConfigFileParser(cfp);
				KStarCalculator ksc = new KStarCalculator(ksCfp);
				ksc.calcKStarScores();
				args.cleanupConfig(cfp);
			}
		},
		
		DoCOMETS {
			@Override
			public void run(CommandArgs args) {
				ConfigFileParser cfp = args.loadConfig();
				COMETSDoer cd = new COMETSDoer(cfp);
				cd.calcBestSequences();
				args.cleanupConfig(cfp);
			}
		},
		
		CalcLigResE {
			@Override
			public void run(CommandArgs args) {
				ConfigFileParser cfp = args.loadConfig();
				LigandResEnergies lre = new LigandResEnergies(cfp.params);
				lre.printEnergies();
				args.cleanupConfig(cfp);
			}
		},
		
		CalcEnergy {
			@Override
			public void run(CommandArgs args) {
				ConfigFileParser cfp = args.loadConfig();
				new EnergyCalculator().run(cfp);
				args.cleanupConfig(cfp);
			}
		},
		
		ConfInfo {
			@Override
			public void run(CommandArgs args) {
				ConfigFileParser cfp = args.loadConfig();
				ConfInfo ci = new ConfInfo(cfp);
				ci.outputConfInfo();
				args.cleanupConfig(cfp);
			}
		},
		
		GpuInfo {
			@Override
			public void run(CommandArgs args) {
				// TODO: call gpu diagnostics
			}
		},

		FindSeqGMECs {
			@Override
			public void run(CommandArgs args) {
				ConfigFileParser cfp = args.loadConfig();
				new SeqGMECFinder(cfp).calcAllSeqGMECs();
				args.cleanupConfig(cfp);
			}
		};
		
		private static Map<String,Command> commands;
		
		static {
			commands = new HashMap<>();
			for (Command command : Command.values()) {
				commands.put(normalizeName(command.name()), command);
			}
		}
		
		public abstract void run(CommandArgs cargs);
		
		public static Command get(String name) {
			return commands.get(normalizeName(name));
		}
		
		public static String listNames() {
			StringJoiner joiner = new StringJoiner(", ");
			for (Command command : Command.values()) {
				joiner.add(command.name());
			}
			return joiner.toString();
		}
		
		public static String normalizeName(String name) {
			return name.toLowerCase();
		}
	}
	
	private static class CommandArgs {
		
		public final List<String> args;
		
		public CommandArgs() {
			args = new ArrayList<>();
		}
		
		public ConfigFileParser loadConfig() {
			
			// collect (and check) the config files
			List<File> configFiles = new ArrayList<>();
			boolean allExist = true;
			for (String path : args) {
				File configFile = new File(path);
				if (!configFile.exists()) {
					System.out.println("can't find config file: " + configFile.getAbsolutePath());
					allExist = false;
				}
				configFiles.add(configFile);
			}
			if (!allExist) {
				System.exit(1);
			}
			
			// load the config
			ConfigFileParser cfp = ConfigFileParser.makeFromFiles(configFiles);
			cfp.loadData();
			
			// init global state
			// TODO: get rid of global state
			EnvironmentVars.openSpecialWarningLogs(cfp);
			ThreadParallelism.setNumThreads(cfp.params.getInt("NumThreads"));
			MultiTermEnergyFunction.setNumThreads(ThreadParallelism.getNumThreads());
			CCDMinimizer.EConvTol = cfp.params.getDouble("CCDEConvTol");
			CCDMinimizer.numIter = cfp.params.getInt("CCDNumIter");
			
			return cfp;
		}
		
		public void cleanupConfig(ConfigFileParser cfp) {
			// TODO: get rid of global state
			EnvironmentVars.closeSpecialWarningLogs();
		}
	}

	public static void main(String[] args){
		
		// args expected to be "command config_file_1.cfg ..."
		
		// read the command name
		String commandName;
		try {
			commandName = args[0];
		}
		catch (Exception e) {
			System.out.println("OSPREY command needed. Try one of: " + Command.listNames());
			System.exit(1);
			return;
			// yeah, the return is redundant, but the compiler apparently doesn't know that
			// try commenting it out and see what happens =)
		}
		
		// lookup the command
		Command command = Command.get(commandName);
		if (command == null) {
			System.out.println("ERROR: OSPREY command unrecognized: " + commandName);
			System.exit(1);
			return;
		}
		
		// build the command args
		CommandArgs cargs = new CommandArgs();
		for (int i=1; i<args.length; i++) {
			cargs.args.add(args[i]);
		}
		
		// run the command (with timing)
		Stopwatch stopwatch = new Stopwatch().start();
		command.run(cargs);
		System.out.println("OSPREY finished, total execution time: " + stopwatch.stop().getTime() + ".");
	}
}

package edu.duke.cs.osprey.tools;

import java.util.concurrent.TimeUnit;

public class Stopwatch {

	private boolean isRunning;
	private long startTime;
	private long stopTime;
	
	public Stopwatch() {
		isRunning = false;
		startTime = -1;
	}
	
	public Stopwatch start() {
		assert (!isRunning);
		startTime = System.nanoTime();
		isRunning = true;
		return this;
	}
	
	public Stopwatch stop() {
		assert (isRunning);
		stopTime = System.nanoTime();
		isRunning = false;
		return this;
	}
	
	public boolean isRunning() {
		return isRunning;
	}
	
	public long getTimeNs() {
		if (isRunning) {
			return System.nanoTime() - startTime;
		} else {
			return stopTime - startTime;
		}
	}
	
	public double getTimeUs() {
		return TimeFormatter.getTimeUs(getTimeNs());
	}
	
	public double getTimeMs() {
		return TimeFormatter.getTimeMs(getTimeNs());
	}
	
	public double getTimeS() {
		return TimeFormatter.getTimeS(getTimeNs());
	}
	
	public double getTimeM() {
		return TimeFormatter.getTimeM(getTimeNs());
	}
	
	public double getTimeH() {
		return TimeFormatter.getTimeH(getTimeNs());
	}
	
	public String getTime() {
		return TimeFormatter.format(getTimeNs());
	}
	
	public String getTime(int decimals) {
		return TimeFormatter.format(getTimeNs(), decimals);
	}
	
	public String getTime(TimeUnit unit) {
		return TimeFormatter.format(getTimeNs(), unit);
	}
	
	public String getTime(TimeUnit unit, int decimals) {
		return TimeFormatter.format(getTimeNs(), unit, decimals);
	}
}

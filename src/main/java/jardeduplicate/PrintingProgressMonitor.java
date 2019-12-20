package jardeduplicate;

import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.lib.ProgressMonitor;

public class PrintingProgressMonitor implements ProgressMonitor {

	public PrintingProgressMonitor() {
	}

	private int counter = 0;
	private int destination;
	private int lastProzStep;
	private long startTime = 0;

	@Override
	public void beginTask(String arg0, int arg1) {
		System.out.print(arg0 + "(" + (arg1 == 0 ? "?" : Integer.toString(arg1)) + ")");
		destination = arg1;
		lastProzStep = 0;
		startTime = System.currentTimeMillis();
	}

	@Override
	public void endTask() {
		System.out.println("->" + counter);
		counter = 0;
	}

	@Override
	public boolean isCancelled() {
		return false;
	}

	@Override
	public void start(int arg0) {
		System.out.println("PrintingProgressMonitor.start(" + arg0 + ")");
	}

	@Override
	public void update(int arg0) {
		counter += arg0;
		if (destination != 0) {
			int prozStep = counter * 10 / destination;
			if (lastProzStep < prozStep) {
				long time = System.currentTimeMillis() - startTime;
				long endTime = time / prozStep * (10 - prozStep);
				if (endTime > 10000) {
					System.out
							.print(" " + (prozStep * 10) + "% (r: " + TimeUnit.MILLISECONDS.toSeconds(endTime) + "s)");
				} else if (destination >= 10) {
					System.out.print(" " + (prozStep * 10) + "%");
				}
				lastProzStep = prozStep;
			}
		}
	}
}

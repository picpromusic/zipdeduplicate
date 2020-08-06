package io.github.picpromusic.zipdeduplicate;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

public class FolderRestore extends TwoLevelRestore {

	public static void main(String[] args)
			throws IOException, ClassNotFoundException, InvalidRemoteException, TransportException, GitAPIException {
		Path workPath = Paths.get(args[0]);
		Git git;
		git = Git.open(workPath.toFile());

		ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		List<Future<?>> futures = new ArrayList<>();
		for (int i = 1; i < args.length; i += 3) {
			Branch branch = ZipDedupUtils.urlToBranchName(args[i + 0]);
			String additionalData = args[i + 1];
			String restoreTo = i + 2 < args.length ? args[i + 2] : null;
			futures.add((executor.submit(FolderRestore.restore(git, branch, additionalData, restoreTo))));
		}
		for (Future<?> future : futures) {
			try {
				future.get();
			} catch (ExecutionException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
				Thread.currentThread().interrupt();
			}
		}
		executor.shutdown();
	}

	private static Runnable restore(Git git, Branch branch, String additionalData, String restoreTo) {
		return () -> {
			Path path = Paths.get("./restore");
			FolderRestore folderRestore = new FolderRestore(git, branch, additionalData);
			if (restoreTo != null) {
				path = Paths.get(restoreTo);
				folderRestore.withReplaceTopLevel(".");
			}
			folderRestore.restoreTo(path);
		};
	}

	private String replaceTopLevel;

	protected void withReplaceTopLevel(String replaceTopLevel) {
		this.replaceTopLevel = replaceTopLevel;
	}

	@Override
	protected boolean deleteOthers() {
		return true;
	}

	@Override
	protected ContainerOutputStream createOuterMostContainer(Path path, String dest) {
		Path destPath = path.resolve(dest);
		if (replaceTopLevel != null) {
			Path p = Paths.get(dest);
			Path base = p.getName(0);
			p = base.relativize(p);
			base = Paths.get(replaceTopLevel);
			destPath = path.resolve(base.resolve(p));
		}
		return new FolderContainerOutputStream(destPath, this::reportCreatedPath);
	}

	@Override
	protected boolean isRestoreCompressed() {
		return false;
	}

	public FolderRestore(Git git, Branch branch, String additionalData) {
		super(git, branch, additionalData);
	}

}

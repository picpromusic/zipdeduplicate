package oss.zipdeduplicate;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;

public class FetchBeforeRestore {

	private static final String REFS_DATA_PREFIX = "refs/heads/data_";
	private static final String REFS_DESC_PREFIX = "refs/heads/desc_";
	private static final String ORIGIN = "origin";
	private Git git;

	public FetchBeforeRestore(Git git) {
		this.git = git;
	}

	public static void main(String[] args)
			throws IOException, InvalidRemoteException, TransportException, GitAPIException {
		long time = System.currentTimeMillis();
		Path workPath = Paths.get(args[0]);
		Git git = Git.open(workPath.toFile());
		String[] branches = new String[args.length - 1];
		for (int i = 1; i < args.length; i++) {
			branches[i-1] = ZipDedupUtils.urlToBranchName(args[i]);
		}
		new FetchBeforeRestore(git).fetch(branches);
		System.out.println(System.currentTimeMillis()-time);
	}

	protected void fetch(String... branchNames) throws InvalidRemoteException, TransportException, GitAPIException {
		List<RefSpec> refSpecs = new ArrayList<>(branchNames.length*2);
		for (String branchName : branchNames) {
			refSpecs.add(new RefSpec(REFS_DATA_PREFIX + branchName + ":" + REFS_DATA_PREFIX + branchName));
			refSpecs.add(new RefSpec(REFS_DESC_PREFIX + branchName + ":" + REFS_DESC_PREFIX + branchName));
		}

		FetchResult fetchResult = git.fetch().setRemote(ORIGIN).setRefSpecs(refSpecs)
				.setProgressMonitor(new PrintingProgressMonitor()).setForceUpdate(true).call();
		System.out.println(fetchResult.getMessages());
	}

}

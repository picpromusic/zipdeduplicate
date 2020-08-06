package io.github.picpromusic.zipdeduplicate;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.TrackingRefUpdate;

public class FetchBeforeRestore {

	private static final String ORIGIN = "origin";
	private Git git;
	private UpdateDataBranch updateDataBranch;

	public FetchBeforeRestore(Git git) {
		this.git = git;
		updateDataBranch = new UpdateDataBranch(git);
	}

	public static void main(String[] args)
			throws IOException, InvalidRemoteException, TransportException, GitAPIException {
		long time = System.currentTimeMillis();
		Path workPath = Paths.get(args[0]);
		Git git = Git.open(workPath.toFile());
		Branch[] branches = new Branch[args.length - 1];
		for (int i = 1; i < args.length; i++) {
			branches[i - 1] = ZipDedupUtils.urlToBranchName(args[i]);
		}
		new FetchBeforeRestore(git).fetch(branches);
		System.out.println(System.currentTimeMillis() - time);
	}

	protected void fetch(Branch... branchNames)
			throws InvalidRemoteException, TransportException, GitAPIException, IOException {

		List<RefSpec> refSpecs = new ArrayList<>(branchNames.length * 2);
		for (Branch branch : branchNames) {
			refSpecs.add(branch.getFetchDescRefSpec());
		}

		FetchResult fetchResult = git.fetch()//
				.setRemote(ORIGIN)//
				.setRefSpecs(refSpecs).setProgressMonitor(new TextProgressMonitor())//
				.call();
		System.out.println(fetchResult.getMessages());
		for (Branch branch : branchNames) {
			boolean upToDate = true;
			TrackingRefUpdate trackingRefUpdate = fetchResult.getTrackingRefUpdate(branch.getDescBranch());
			if (trackingRefUpdate != null) {
				System.out.println(trackingRefUpdate + "->" + trackingRefUpdate.getResult());
				if (trackingRefUpdate.getResult() == Result.REJECTED) {
					upToDate = false;
				}
			} else {
				ObjectId advertised = fetchResult.getAdvertisedRef(branch.getDescBranch()).getObjectId();
				ObjectId existing = git.getRepository().findRef(branch.getDescBranch()).getObjectId();
				upToDate = advertised.equals(existing);
			}
			if (!upToDate) {
				RefUpdate updateRef = git.getRepository().updateRef(branch.getDescBranchAsRef());
				updateRef.setNewObjectId(fetchResult.getAdvertisedRef(branch.getDescBranch()).getObjectId());
				updateRef.setForceUpdate(true);
				Result update = updateRef.update();
				System.out.println(update);
				updateDataBranch.execute(branch);
			}
		}

		refSpecs = new ArrayList<>(branchNames.length * 2);
		for (Branch branch : branchNames) {
			refSpecs.add(branch.getFetchDataRefSpec());
		}

		fetchResult = git.fetch()//
				.setRemote(ORIGIN)//
				.setRefSpecs(refSpecs).setProgressMonitor(new TextProgressMonitor())//
				.call();
		System.out.println(fetchResult.getMessages());
		for (Branch branch : branchNames) {
			TrackingRefUpdate trackingRefUpdate = fetchResult.getTrackingRefUpdate(branch.getDataBranch());
			if (trackingRefUpdate != null) {
				System.out.println(trackingRefUpdate + "->" + trackingRefUpdate.getResult());
			}
		}
	}

}

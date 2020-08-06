package io.github.picpromusic.zipdeduplicate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

public class UpdateDataBranch {

	private Repository repository;
	private Git git;

	public UpdateDataBranch(Git git) {
		this.git = git;
		this.repository = git.getRepository();
	}

	public void execute(Branch branch) throws IOException, NoHeadException, GitAPIException {
		List<Ref> dataRef = repository.getRefDatabase().getRefsByPrefix(branch.getDataBranchAsRef());
		Set<ObjectId> allKnownTreeIds = new HashSet<ObjectId>();
		Iterable<RevCommit> itRevCommit = git.log().add(dataRef.get(0).getObjectId()).call();
		for (RevCommit revCommit : itRevCommit) {
			allKnownTreeIds.add(revCommit.getTree().getId());
		}

		List<Ref> refsByPrefix = repository.getRefDatabase().getRefsByPrefix(branch.getDescBranchAsRef());
		Deque<ObjectId> reverseOrder = new LinkedList<ObjectId>();
		itRevCommit = git.log().add(refsByPrefix.get(0).getObjectId()).call();
		for (RevCommit revCommit : itRevCommit) {
			reverseOrder.addFirst(DescriptionUtil.extractDescription(repository, revCommit, null));
		}

		ObjectInserter newInserter = repository.newObjectInserter();
		Iterator<ObjectId> iteratorForDataBranch = reverseOrder.stream().distinct().iterator();
		ObjectId parent = null;
		PersonIdent person = ZipDedupUtils.getDataCommitPersonIdent();
		while (iteratorForDataBranch.hasNext()) {
			ObjectId nextTree = iteratorForDataBranch.next();
			if (allKnownTreeIds.contains(nextTree)) {
				CommitBuilder cb = new CommitBuilder();
				cb.setTreeId(nextTree);
				cb.setAuthor(person);
				cb.setCommitter(person);
				if (parent != null) {
					cb.setParentId(parent);
				}
				parent = newInserter.insert(cb);
			} else {
				break;
			}
		}

		RefUpdate updateRef = repository.updateRef(branch.getDataBranchAsRef());
		updateRef.setNewObjectId(parent);
		updateRef.setForceUpdate(true);
		Result update = updateRef.update();
		System.out.println(update);
	}

}

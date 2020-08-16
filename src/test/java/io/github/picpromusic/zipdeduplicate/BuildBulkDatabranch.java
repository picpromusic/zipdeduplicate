package io.github.picpromusic.zipdeduplicate;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;

public class BuildBulkDatabranch {

	public static void main(String[] args) throws IOException, NoHeadException, GitAPIException {
		Path workPath = Paths.get(args[0]);
		String branch = args[1];

		Git git = Git.open(workPath.toFile());
		Repository repository = git.getRepository();

		ThreadLocal<ObjectInserter> oneObjectInserterPerThread = ThreadLocal
				.withInitial(() -> repository.newObjectInserter());

		Branch revNames = new Branch(branch);

		List<Ref> refsByPrefix = repository.getRefDatabase().getRefsByPrefix(revNames.getDataBranchAsRef());
		refsByPrefix.forEach(System.out::println);
		ObjectId objectId = refsByPrefix.get(0).getObjectId();

		System.out.println();
		Deque<RevCommit> reverseOrder = new LinkedList<RevCommit>();
		Iterable<RevCommit> itRevCommit = git.log().add(objectId).call();
		itRevCommit.forEach(c -> {
			System.out.println(c.getId());
			System.out.println(c.getTree());
			reverseOrder.addFirst(c);
		});

		ObjectId parentCommit = null;
		ObjectId prevTree = null;
		PersonIdent person = ZipDedupUtils.getDataCommitPersonIdent();
		System.out.println();
		Set<ObjectId> allSeenIds = new HashSet<>();
		for (RevCommit revCommit : reverseOrder) {
			SortingTreeFormatter stf = new SortingTreeFormatter(oneObjectInserterPerThread::get);
//			if (prevTree != null) {
//				stf.append("prevTree", FileMode.TREE, prevTree);
//			}			
			System.out.println(revCommit.getTree());
			TreeWalk tw = new TreeWalk(repository);
			tw.setRecursive(true);
			tw.addTree(revCommit.getTree());
			while (tw.next()) {
				ObjectId oid = tw.getObjectId(0);
				if (allSeenIds.add(oid)) {
					stf.append(oid.getName(), FileMode.REGULAR_FILE, oid);
				}
//				System.out.println(oid.getName());
			}

			prevTree = stf.toObjectId();
			CommitBuilder cb = new CommitBuilder();
			cb.setTreeId(prevTree);
			cb.setAuthor(person);
			cb.setCommitter(person);
			if (parentCommit != null) {
				cb.setParentId(parentCommit);
			}
			parentCommit = oneObjectInserterPerThread.get().insert(cb);
		}

		RefUpdate updateRef = repository.updateRef("refs/heads/bulk-data");
		updateRef.setNewObjectId(parentCommit);
		updateRef.setForceUpdate(true);
		Result update = updateRef.update();
		System.out.println(update);
	}
}

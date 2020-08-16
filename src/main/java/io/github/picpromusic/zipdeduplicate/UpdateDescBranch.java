package io.github.picpromusic.zipdeduplicate;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;

public class UpdateDescBranch {

	private Git git;
	private Repository repository;
	private boolean debug;
	private Branch revNames;

	public UpdateDescBranch(Git git, Branch revNames, boolean debug) {
		this.git = git;
		this.repository = git.getRepository();
		this.revNames = revNames;
		this.debug = debug;
	}

	public void execute(Predicate<RevCommit> delete) throws NoHeadException, GitAPIException, IOException {
		List<Ref> refsByPrefix = repository.getRefDatabase().getRefsByPrefix(revNames.getDescBranchAsRef());
		refsByPrefix.forEach(System.out::println);
		ObjectId objectId = refsByPrefix.get(0).getObjectId();

		System.out.println();
		Deque<RevCommit> reverseOrder = new LinkedList<RevCommit>();
		Iterable<RevCommit> itRevCommit = git.log().add(objectId).call();
		itRevCommit.forEach(c -> {
			if (!delete.test(c)) {
				System.out.println(c.getId());
				System.out.println(c.getTree());
				reverseOrder.addFirst(c);
			}
		});

		ObjectInserter newInserter = repository.getObjectDatabase().newInserter();
		Iterator<RevCommit> listIterator = reverseOrder.iterator();
		AnyObjectId parent = null;
		while (listIterator.hasNext()) {
			RevCommit actual = listIterator.next();
			System.out.println(actual.getId());
			CommitBuilder cb = new CommitBuilder();
			cb.setTreeId(actual.getTree());
			cb.setTreeId(repairTreeAndInsertTreeData(actual.getTree()));
			cb.setAuthor(actual.getAuthorIdent());
			cb.setCommitter(actual.getCommitterIdent());
			cb.setMessage(actual.getFullMessage());
			if (parent != null) {
				cb.setParentId(parent);
			}
			parent = newInserter.insert(cb);
		}

		RefUpdate updateRef = repository.updateRef(revNames.getDescBranchAsRef() + (debug ? "2" : ""));
		updateRef.setNewObjectId(parent);
		updateRef.setForceUpdate(true);
		Result update = updateRef.update();
		System.out.println(update);

	}

	private ObjectId repairTreeAndInsertTreeData(RevTree tree)
			throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException, IOException {
		ObjectId desc = null;
		ObjectId treeData = null;
		TreeWalk tw = new TreeWalk(repository);
		tw.addTree(tree);
		tw.setRecursive(true);
		while (tw.next()) {
			if (tw.getPathString().equals("description")) {
				desc = tw.getObjectId(0);
			} else if (tw.getPathString().equals("treedata")) {
				treeData = tw.getObjectId(0);
			}
		}
		if (treeData != null) {
			return tree;
		}
		SortingTreeFormatter stf = new SortingTreeFormatter(repository::newObjectInserter);
		stf.append("description",FileMode.REGULAR_FILE,desc);
		ByteArrayInputStream bin = new ByteArrayInputStream(repository.open(desc).getBytes());
		BufferedReader bufr = new BufferedReader(new InputStreamReader(bin));
		ObjectId contentTreeId = ObjectId.fromString(bufr.readLine());

		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		PrintWriter pw = new PrintWriter(bout);
		TreeWalk tw2 = new TreeWalk(repository);
		tw2.setRecursive(true);
		tw2.addTree(contentTreeId);
		while (tw2.next()) {
			pw.println(tw2.getPathString());
			pw.println(tw2.getObjectId(0).getName());
		}
		pw.close();
		
		ObjectId insert = repository.newObjectInserter().insert(Constants.OBJ_BLOB, bout.toByteArray());

		stf.append("treedata", FileMode.REGULAR_FILE, insert);
		return stf.toObjectId();
	}

}

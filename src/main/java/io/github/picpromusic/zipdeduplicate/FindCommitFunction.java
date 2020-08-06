package io.github.picpromusic.zipdeduplicate;

import java.io.IOException;
import java.util.Iterator;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.revwalk.RevCommit;

public class FindCommitFunction {

	private Git git;

	public FindCommitFunction(Git git) {
		this.git = git;
	}

	public ObjectId findCommitWithTreeId(String ref,ObjectId treeId) {
		try {
			RefDatabase refDatabase = git.getRepository().getRefDatabase();
			Ref findRef = refDatabase.findRef(ref);

			if (findRef == null) {
				return null;
			}
			ObjectId objectId = findRef.getObjectId();

			Iterator<RevCommit> commitIter = git.log().add(objectId).call().iterator();
			ObjectId commitId = null;
			while (commitIter.hasNext() && commitId == null) {
				RevCommit next = commitIter.next();
				ObjectId id = next.getTree().getId();
				if (treeId.equals(id)) {
					return next.getId();
				}
			}
		} catch (GitAPIException | IOException e) {
		}
		return null;

	}

}

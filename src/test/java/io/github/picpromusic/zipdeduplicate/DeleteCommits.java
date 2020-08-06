package io.github.picpromusic.zipdeduplicate;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.filter.RevFilter;

public class DeleteCommits {
	public static void main(String[] args) throws IOException, GitAPIException {
		Path workPath = Paths.get(args[0]);
		String branch = args[1];

		Set<ObjectId> commitsToDelete = Arrays.asList(args).stream().skip(2).map(ObjectId::fromString)
				.collect(Collectors.toSet());

		Git git = Git.open(workPath.toFile());
		Repository repository = git.getRepository();

		Branch revNames = new Branch(branch);
		ObjectId keepExperiment = ObjectId.fromString("f9a799bae645d01447beb6eb641afc0ff3d368c2");

		UpdateDescBranch updateDesc = new UpdateDescBranch(git, revNames, false);
//		updateDesc.execute(c -> commitsToDelete.contains(c.getId()));
		updateDesc.execute(c -> !keepExperiment.equals(c));

		UpdateDataBranch updateData = new UpdateDataBranch(git);
		updateData.execute(new Branch(branch));
	}
}

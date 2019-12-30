package oss.zipdeduplicate;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Assert;

public class AbstractGitTest {

	protected void deleteRecursive(Path path) throws IOException {
		Files.walk(path)//
				.sorted(Comparator.reverseOrder())//
				.map(Path::toFile)//
				.forEach(File::delete);
	}

	protected void assertHasNoNext(TreeWalk tw)
			throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException, IOException {
		Assert.assertFalse(tw.next());
	}

	protected void assertHasNext(TreeWalk tw)
			throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException, IOException {
		Assert.assertTrue(tw.next());
	}

	protected List<String> readAll(Git g, ObjectId oid) throws MissingObjectException, IOException {
		ObjectStream os = g.getRepository().newObjectReader().open(oid).openStream();
		BufferedReader bufr = new BufferedReader(new InputStreamReader(os));
		List<String> allLines = new ArrayList<>();
		String line = bufr.readLine();
		while (line != null) {
			allLines.add(line);
			line = bufr.readLine();
		}
		return allLines;
	}

	protected TreeWalk createTreeWalk(Git git, ObjectId oid)
			throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException, IOException {
		git.getRepository().getObjectDatabase();
		TreeWalk tw = new TreeWalk(git.getRepository());
		tw.addTree(oid);
		return tw;
	}

}

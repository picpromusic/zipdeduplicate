package jardeduplicate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.DepthWalk.Commit;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;

import io.vavr.Tuple;
import io.vavr.Tuple2;

public class Restore {

	private Repository repo;

	public static void main(String[] args) throws IOException, ClassNotFoundException {
		Path workPath = Paths.get("demoGit");
		Git git;
		git = Git.open(workPath.toFile());

		Path path = Paths.get("./restore");

		new Restore(git.getRepository()).restoreTo(path);

	}

	public Restore(Repository repository) {
		this.repo = repository;
	}

	public void restoreTo(Path path) throws IOException, ClassNotFoundException {
		ObjectId commitId = repo.getRefDatabase().findRef("master").getObjectId();
		ObjectReader or = repo.newObjectReader();
		RevCommit commit = Commit.parse(or.open(commitId).getBytes());

		TreeMap<String, TreeSet<Tuple2<String, ObjectId>>> allZipPathes = new TreeMap<>();
		TreeMap<String, ZipOutputStream> allOpenZipOutputstreams = new TreeMap<>();
		ObjectId contentCommitId = null;

		TreeWalk walk = new TreeWalk(repo);
		walk.addTree(commit.getTree());
		while (walk.next()) {
			if (walk.getPathString().equals("description")) {
				ObjectInputStream oin = new ObjectInputStream(
						new ByteArrayInputStream(or.open(walk.getObjectId(0)).getBytes()));
				contentCommitId = ObjectId.fromString((String) oin.readObject());
				List<String[]> readObject2 = (List<String[]>) oin.readObject();
				readObject2.stream().map(a -> Arrays.stream(a).collect(Collectors.joining("")))
						.forEach(s -> allZipPathes.put(s, new TreeSet<Tuple2<String, ObjectId>>(onlyStringCompare())));
			}
		}
//		allZipPathes.keySet().forEach(System.out::println);

		commit = Commit.parse(or.open(contentCommitId).getBytes());
		walk = new TreeWalk(repo);
		walk.setRecursive(true);
		walk.addTree(commit.getTree());
		List<String> newZipContainerNames = null;
		String aktZipContainerName = null;
		ZipOutputStream zipOutputStream = null;
		while (walk.next()) {
			String pathString = walk.getPathString();
			newZipContainerNames = searchZipContainer(pathString, allZipPathes.navigableKeySet());
			String insertIntoContainer = pathString;
			boolean firstRun = true;
			int size = newZipContainerNames.size();
			if (size == 0) {
				try (OutputStream destStream = Files.newOutputStream(path.resolve(pathString))) {
					or.open(walk.getObjectId(0)).copyTo(destStream);
				}
			} else {
				ListIterator<String> listIterator = newZipContainerNames.listIterator(newZipContainerNames.size());
				while (listIterator.hasPrevious()) {
					String destZipContainer = listIterator.previous();
					zipOutputStream = allOpenZipOutputstreams.get(destZipContainer);
					if (zipOutputStream == null) {
						
					}
				}
			}
			aktZipContainerName = newZipContainerNames.isEmpty() ? null : newZipContainerNames.get(0);
		}
	}

	private Comparator<? super Tuple2<String, ObjectId>> onlyStringCompare() {
		return (t1, t2) -> t1._1.compareTo(t2._1);
	}

	private List<String> searchZipContainer(String pathString, NavigableSet<String> navigableSet) {
		List<String> allMatches = new ArrayList<>();

		String partString = pathString;
		NavigableSet<String> headSet = navigableSet.headSet(pathString, true);

		while (partString != null && !headSet.isEmpty()) {
			if (headSet.contains(partString)) {
				allMatches.add(partString);
			}
			int lastPos = partString.lastIndexOf("/");
			if (lastPos < 0) {
				partString = null;
			} else {
				partString = partString.substring(0, lastPos);
				headSet = headSet.headSet(partString, true);
			}
		}

		return allMatches;
	}
}

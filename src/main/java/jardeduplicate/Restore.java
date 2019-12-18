package jardeduplicate;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.DepthWalk.Commit;
import org.eclipse.jgit.revwalk.RevCommit;
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
		long start = System.currentTimeMillis();
		ObjectId commitId = repo.getRefDatabase().findRef("master").getObjectId();
		ObjectReader or = repo.newObjectReader();
		RevCommit commit = Commit.parse(or.open(commitId).getBytes());

		TreeMap<String, TreeSet<Tuple2<String, ObjectId>>> allZipPathes = new TreeMap<>();
		TreeMap<String, Tuple2<ZipOutputStream, ByteArrayOutputStream>> allOpenZipOutputstreams = new TreeMap<>();
		ObjectId contentCommitId = null;

		TreeWalk walk = new TreeWalk(repo);
		walk.addTree(commit.getTree());
		while (walk.next()) {
			if (walk.getPathString().equals("description")) {
				BufferedReader bufr = new BufferedReader(
						new InputStreamReader(or.open(walk.getObjectId(0)).openStream()));
				contentCommitId = ObjectId.fromString(bufr.readLine());
				String content = bufr.readLine();
				while (content != null) {
					allZipPathes.put(content, new TreeSet<Tuple2<String, ObjectId>>(onlyStringCompare()));
					content = bufr.readLine();
				}
			}
		}
//		allZipPathes.keySet().forEach(System.out::println);

		commit = Commit.parse(or.open(contentCommitId).getBytes());
		walk = new TreeWalk(repo);
		walk.setRecursive(true);
		walk.addTree(commit.getTree());
		List<String> newZipContainerNames = null;
		String lastPathString = null;
		ZipOutputStream zipOutputStream = null;
		while (walk.next()) {
			String pathString = walk.getPathString();
			closeAllDone(lastPathString, pathString, allZipPathes.navigableKeySet(), allOpenZipOutputstreams);
			newZipContainerNames = searchZipContainer(pathString, allZipPathes.navigableKeySet());

			int size = newZipContainerNames.size();
			if (size == 0) {
				try (OutputStream destStream = Files.newOutputStream(path.resolve(pathString))) {
					or.open(walk.getObjectId(0)).copyTo(destStream);
				}
			} else {
				ListIterator<String> listIterator = newZipContainerNames.listIterator(newZipContainerNames.size());
				ZipOutputStream outerZipStream = null;
				Path outerZipPath = null;
				while (listIterator.hasPrevious()) {
					String destZipContainer = listIterator.previous();
					Path actPath = Paths.get(destZipContainer);
					zipOutputStream = Optional.ofNullable(allOpenZipOutputstreams.get(destZipContainer)).map(Tuple2::_1)
							.orElse(null);
					if (zipOutputStream == null) {
						ByteArrayOutputStream bout = null;
						Path p = Paths.get(destZipContainer);
						if (p.getParent() == null) {
							zipOutputStream = new ZipOutputStream(new FileOutputStream(destZipContainer));
						} else {
							bout = new ByteArrayOutputStream();
							zipOutputStream = new ZipOutputStream(bout);
						}
						zipOutputStream.setMethod(ZipOutputStream.DEFLATED);
						zipOutputStream.setLevel(0);
						allOpenZipOutputstreams.put(destZipContainer, Tuple.of(zipOutputStream, bout));
					}
					outerZipStream = zipOutputStream;
					outerZipPath = actPath;
				}
				ZipEntry ze = new ZipEntry(outerZipPath.relativize(Paths.get(pathString)).toString());
				zipOutputStream.putNextEntry(ze);
				byte[] data = or.open(walk.getObjectId(0)).getBytes();
				zipOutputStream.write(data);
				zipOutputStream.closeEntry();
			}
			lastPathString = pathString;
		}
		closeAllDone(lastPathString, null, allZipPathes.navigableKeySet(), allOpenZipOutputstreams);

//		byte[] data = null;
//		while (!allOpenZipOutputstreams.isEmpty()) {
//			Entry<String, Tuple2<ZipOutputStream, ByteArrayOutputStream>> lastEntry = allOpenZipOutputstreams
//					.lastEntry();
//			System.out.println(lastEntry.getKey());
//			lastEntry.getValue()._1.closeEntry();
//			lastEntry.getValue()._1.close();
//			data = lastEntry.getValue()._2.toByteArray();
//			allOpenZipOutputstreams.remove(lastEntry.getKey());
//		}

//		System.out.println(System.currentTimeMillis() - start);
	}

	private void closeAllDone(String lastPathString, String pathString, NavigableSet<String> navigableKeySet,
			TreeMap<String, Tuple2<ZipOutputStream, ByteArrayOutputStream>> allOpenZipOutputstreams)
			throws IOException {
		if (lastPathString != null) {
			List<String> aktPathes = pathString != null ? searchZipContainer(pathString, navigableKeySet): Arrays.asList("");
			List<String> pathesToClose = searchZipContainer(lastPathString, navigableKeySet);
			if (!aktPathes.equals(pathesToClose)) {
				ListIterator<String> aktIt = aktPathes.listIterator(aktPathes.size());
				ListIterator<String> lastIt = pathesToClose.listIterator(pathesToClose.size());
				int stopIndex = -1;
				while (aktIt.hasPrevious() && lastIt.hasPrevious()) {
					String akt = aktIt.previous();
					String last = lastIt.previous();
					if (!akt.equals(last)) {
						stopIndex = lastIt.nextIndex();
						break;
					} else {
						if (!aktIt.hasPrevious() && lastIt.hasPrevious()) {
							stopIndex = lastIt.previousIndex();
						}
					}
				}
				ListIterator<String> closeIterator = pathesToClose.listIterator();
				byte[] data = null;
				String lastName = null;
				while (closeIterator.nextIndex() <= stopIndex+1 && closeIterator.hasNext()) {
					String name = closeIterator.next();
					Tuple2<ZipOutputStream, ByteArrayOutputStream> streams = allOpenZipOutputstreams.get(name);
					if (data != null) {
						String nameInZip = Paths.get(name).relativize(Paths.get(lastName)).toString();
						ZipEntry ze = new ZipEntry(nameInZip);
						streams._1.putNextEntry(ze);
						streams._1.write(data);
					}
					streams._1.closeEntry();
					if (closeIterator.nextIndex() <= stopIndex+1) {
						streams._1.close();
						data = Optional.ofNullable(streams._2).map(ByteArrayOutputStream::toByteArray).orElse(null);

						allOpenZipOutputstreams.remove(name);
						lastName = name;
					}
				}
			}
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

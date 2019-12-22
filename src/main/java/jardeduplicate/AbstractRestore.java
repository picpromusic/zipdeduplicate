package jardeduplicate;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.DepthWalk.Commit;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;

import io.vavr.Tuple;
import io.vavr.Tuple2;

public abstract class AbstractRestore {

	private Repository repo;
	private String branchName;
	private ObjectReader reader;
	private TreeMap<String, TreeSet<Tuple2<String, ObjectId>>> allZipPathes;
	private TreeMap<String, Tuple2<ContainerOutputStream, ByteArrayOutputStream>> allOpenOutputstreams;

	public AbstractRestore(Repository repository, String branch) {
		this.repo = repository;
		this.reader = this.repo.newObjectReader();
		this.branchName = branch;
		allZipPathes = new TreeMap<>();
		allOpenOutputstreams = new TreeMap<>();
	}

	public void restoreTo(Path path) throws IOException, ClassNotFoundException {
		long start = System.currentTimeMillis();
		ObjectId commitId = repo.getRefDatabase().findRef(branchName).getObjectId();
		RevCommit commit = readCommit(commitId);

		ObjectId contentCommitId = null;

		contentCommitId = DescriptionUtil.extractDescription(repo, commit, allZipPathes);

		commit = readCommit(contentCommitId);
		try (TreeWalk walk = new TreeWalk(repo)) {

			walk.setRecursive(true);
			walk.addTree(commit.getTree());
			String lastPathString = null;
			while (walk.next()) {
				String pathString = walk.getPathString();
//				System.out.println(pathString);
				ObjectId objectId = walk.getObjectId(0);

				closeAllDone(lastPathString, pathString);
				lastPathString = processElement(path, pathString, objectId);
			}
			closeAllDone(lastPathString, null);
		}

		System.out.println(System.currentTimeMillis() - start);
	}

	private RevCommit readCommit(ObjectId commitId) throws MissingObjectException, IOException {
		return Commit.parse(reader.open(commitId).getBytes());
	}

	private String processElement(Path path, String pathString, ObjectId objectId)
			throws MissingObjectException, IOException {
		List<String> newZipContainerNames;
		String lastPathString;
		ContainerOutputStream outputStream = null;
		newZipContainerNames = searchZipContainer(pathString);

		int size = newZipContainerNames.size();
		if (size == 0) {
			try (OutputStream destStream = Files.newOutputStream(path.resolve(pathString))) {
				reader.open(objectId).copyTo(destStream);
			}
		} else {
			ListIterator<String> listIterator = newZipContainerNames.listIterator(newZipContainerNames.size());
			Path outerZipPath = null;
			while (listIterator.hasPrevious()) {
				String destZipContainer = listIterator.previous();
				Path actPath = Paths.get(destZipContainer);
				outputStream = (ContainerOutputStream) allOpenOutputstreams
						.computeIfAbsent(destZipContainer, k -> openStreams(path, k))//
						._1();

				outerZipPath = actPath;
			}
			outputStream.putNextEntry(outerZipPath.relativize(Paths.get(pathString)).toString());
			byte[] data = reader.open(objectId).getBytes();
			outputStream.write(data);
			outputStream.closeEntry();
		}
		lastPathString = pathString;
		return lastPathString;
	}


	private void closeAllDone(String lastPathString, String pathString) throws IOException {
		if (lastPathString != null) {
			List<String> aktPathes = searchZipContainer(pathString);
			List<String> pathesToClose = searchZipContainer(lastPathString);
			if (!aktPathes.equals(pathesToClose)) {
				int stopIndex = calcStopIndex(aktPathes, pathesToClose);

				ListIterator<String> closeIterator = pathesToClose.listIterator();
				byte[] data = null;
				String lastName = null;
				while (closeIterator.nextIndex() <= stopIndex && closeIterator.hasNext()) {
					String name = closeIterator.next();
					Tuple2<ContainerOutputStream, ByteArrayOutputStream> streams = allOpenOutputstreams.get(name);
					if (data != null) {
						String nameInZip = Paths.get(name).relativize(Paths.get(lastName)).toString();
						streams._1.putNextEntry(nameInZip);
						streams._1.write(data);
						streams._1.closeEntry();
					}
					if (closeIterator.nextIndex() <= stopIndex) {
//						System.out.println("CLOSE:"+name );
						streams._1.close();
						data = Optional.ofNullable(streams._2).map(ByteArrayOutputStream::toByteArray).orElse(null);
						allOpenOutputstreams.remove(name);
						lastName = name;
					}
				}
			}
		}
	}

	private int calcStopIndex(List<String> aktPathes, List<String> pathesToClose) {
		ListIterator<String> aktIt = aktPathes.listIterator(aktPathes.size());
		ListIterator<String> lastIt = pathesToClose.listIterator(pathesToClose.size());
		int stopIndex = pathesToClose.size();
		while (aktIt.hasPrevious() && lastIt.hasPrevious()) {
			String akt = aktIt.previous();
			String last = lastIt.previous();
			if (!akt.equals(last)) {
//				stopIndex = lastIt.nextIndex() +2;
				break;
			} else {
				stopIndex--;
//				if (!aktIt.hasPrevious() && lastIt.hasPrevious()) {
//					stopIndex = lastIt.previousIndex() +2;
//				}
			}
		}
		return stopIndex;
	}

	private List<String> searchZipContainer(String pathString) {
		NavigableSet<String> navigableSet = allZipPathes.navigableKeySet();
		List<String> allMatches = new ArrayList<>();

		if (pathString != null && !pathString.isEmpty()) {
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
		}
		

		return allMatches;
	}

	protected abstract Tuple2<ContainerOutputStream, ByteArrayOutputStream> openStreams(Path path, String dest);
}
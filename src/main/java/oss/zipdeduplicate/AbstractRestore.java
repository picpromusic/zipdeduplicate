package oss.zipdeduplicate;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.DepthWalk.Commit;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.treewalk.TreeWalk;

import oss.zipdeduplicate.DescriptionUtil.PathAsStringAndObjectId;

public abstract class AbstractRestore {

	private static final String REFS_DATA_PREFIX = "refs/heads/data_";
	private static final String REFS_DESC_PREFIX = "refs/heads/desc_";
	private static final String ORIGIN = "origin";

	private Repository repo;
	private String branchName;
	private ObjectReader reader;
	private TreeMap<String, TreeSet<PathAsStringAndObjectId>> allZipPathes;
	private TreeMap<String, ContainerOutputStream> allOpenOutputstreams;
	private Set<Path> existingPathes;
	private Git git;
	private String additionalData;

	public AbstractRestore(Git git, String branch, String additionalData) {
		this.git = git;
		this.additionalData = additionalData;
		this.repo = git.getRepository();
		this.reader = this.repo.newObjectReader();
		this.branchName = branch;
		allZipPathes = new TreeMap<>();
		allOpenOutputstreams = new TreeMap<>();
	}

	protected void reportCreatedPath(Path p) {
		do {
			existingPathes.remove(p);
			p = p.getParent();
		} while (p != null);
	}

	public void restoreTo(Path path)
			throws IOException, ClassNotFoundException, InvalidRemoteException, TransportException, GitAPIException {
		long start = System.currentTimeMillis();
		rememberExistingFiles(path);
		fetch();
		ObjectId commitId = repo.getRefDatabase().findRef("desc_" + branchName).getObjectId();
		RevCommit commit = searchCommit(commitId);

		ObjectId contentCommitId = null;

		contentCommitId = DescriptionUtil.extractDescription(repo, commit, allZipPathes);

		commit = readCommit(contentCommitId);
		try (TreeWalk walk = new TreeWalk(repo)) {

			walk.setRecursive(true);
			walk.addTree(commit.getTree());
			String lastPathString = null;
			while (walk.next()) {
				String pathString = walk.getPathString();
				ObjectId objectId = walk.getObjectId(0);
				closeAllDone(lastPathString, pathString);
				lastPathString = processElement(path, pathString, objectId);
			}
			closeAllDone(lastPathString, null);
		}

		int retryCount = 10;
		while (retryCount > 0 && !deleteAllRemaingFiles()) {
			retryCount--;
			checkExistingPathes();
		}
		checkExistingPathes();

		if (!existingPathes.isEmpty()) {
			System.out.println("The following pathes couldn't be deleted:");
			existingPathes.forEach(System.out::println);
		}
		System.out.println();
		System.out.println(System.currentTimeMillis() - start);
	}

	private void checkExistingPathes() {
		existingPathes = existingPathes.stream().filter(Files::exists).collect(Collectors.toSet());
	}

	protected void fetch() throws InvalidRemoteException, TransportException, GitAPIException {
		String dataRef = REFS_DATA_PREFIX + branchName;
		RefSpec dataRefSpec = new RefSpec(dataRef + ":" + REFS_DATA_PREFIX + branchName);
		RefSpec descRefSpec = new RefSpec(REFS_DESC_PREFIX + branchName + ":" + REFS_DESC_PREFIX + branchName);

		FetchResult fetchResult = git.fetch().setRemote(ORIGIN).setRefSpecs(dataRefSpec, descRefSpec)
				.setProgressMonitor(new PrintingProgressMonitor()).setForceUpdate(true).call();
		System.out.println(fetchResult.getMessages());
	}

	private boolean deleteAllRemaingFiles() {
		return existingPathes.stream()//
//				.peek(System.out::println)
//				.peek(p -> System.out.println(p.getNameCount()))
				.filter(p -> p.getNameCount() > 2).filter(Files::exists)//
				.sorted((p1, p2) -> p2.toString().length() - p1.toString().length())//
//				.peek(System.out::println)
				.map(Path::toFile)//
				.map(File::delete)//
//				.peek(System.out::println)
				.filter(Boolean.FALSE::equals).count() == 0;
	}

	private void rememberExistingFiles(Path path) throws IOException {
		try (Stream<Path> walk = Files.walk(path)) {
			existingPathes = walk.sorted(Comparator.reverseOrder()).collect(Collectors.toSet());
		}
	}

	private RevCommit searchCommit(ObjectId objectId)
			throws MissingObjectException, IOException, NoHeadException, GitAPIException {
		Iterator<RevCommit> commitIter = git.log().add(objectId).call().iterator();
		ObjectId commitId = null;
		while (commitIter.hasNext() && commitId == null) {
			RevCommit next = commitIter.next();
			if (matchesAdditionalData(next.getFullMessage())) {
				commitId = next.getId();
			}
		}
		return readCommit(commitId);
	}

	private RevCommit readCommit(ObjectId commitId)
			throws MissingObjectException, IOException, NoHeadException, GitAPIException {
		return Commit.parse(reader.open(commitId).getBytes());
	}

	private boolean matchesAdditionalData(String fullMessage) {
		if (additionalData == null || additionalData.isEmpty()) {
			return true;
		}
		if (fullMessage == null || fullMessage.isEmpty() || !fullMessage.contains(":")) {
			return false;
		}
		String[] split = additionalData.split(":");
		String version = split[0];
		String basedOn = split[1];
		boolean latests = basedOn.equals("latests");
		split = Optional.ofNullable(fullMessage).map(s -> s.split(":")).orElseGet(() -> new String[] { "", "" });
		if (split[0].isEmpty()) {
			return false;
		}
		if (version.equals(split[0]) && (latests || basedOn.equals(split[1]))) {
			return true;
		}
		return false;
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
				outputStream = allOpenOutputstreams.computeIfAbsent(destZipContainer, k -> openStreams(path, k))//
				;

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
					ContainerOutputStream streams = allOpenOutputstreams.get(name);
					if (data != null) {
						String nameInZip = Paths.get(name).relativize(Paths.get(lastName)).toString();
						streams.putNextEntry(nameInZip);
						streams.write(data);
						streams.closeEntry();
					}
					if (closeIterator.nextIndex() <= stopIndex) {
						streams.close();
						data = streams.getWrittenTo().map(ByteArrayOutputStream.class::cast)
								.map(ByteArrayOutputStream::toByteArray).orElse(null);
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
				break;
			} else {
				stopIndex--;
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

	protected abstract ContainerOutputStream openStreams(Path path, String dest);
}

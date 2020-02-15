package oss.zipdeduplicate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.DepthWalk.Commit;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;

public class BulkInsert {

	private static final String ORIGIN = "origin";
	private static final String REFS_DATA_PREFIX = "refs/heads/data_";
	private static final String REFS_DESC_PREFIX = "refs/heads/desc_";
	private static final Integer NO_PARENT = 0;
	private static final Integer WITH_PARENT = 1;

	private Repository repo;
	private ThreadLocal<ObjectInserter> oneObjectInserterPerThread = ThreadLocal
			.withInitial(() -> repo.newObjectInserter());
	private ExecutorService poolForUnlimitedAmountOfJoins;
	private Predicate<String> additionalFilenameZipPredicate;
	private Git git;

	public BulkInsert(Git git) {
		this(git, (s) -> true);
	}

	public BulkInsert(Git git, Predicate<String> additionalFilenameZipPredicate) {
		this.git = git;
		this.repo = git.getRepository();
		this.additionalFilenameZipPredicate = additionalFilenameZipPredicate;
		this.poolForUnlimitedAmountOfJoins = Executors.newCachedThreadPool(BulkInsert::deamonThreads);
	}

	private static Thread deamonThreads(Runnable r) {
		Thread t = new Thread(r);
		t.setDaemon(true);
		return t;
	}

	public static void main(String[] args) throws GitAPIException, IOException {
		Path pathToInput = Paths.get(args[1]);
		String branchName = args[2].replaceFirst("http://", "").replace(':', '_');
		Path workPath = Paths.get(args[0]);
		String additionalData = args.length > 3 ? args[3] : null;

		try (Git git = openOrCreateGit(workPath)) {
			List<String> extensions = Arrays.asList(".jar", ".war", ".ear", ".zip");

			ZipInfoCollector zipInfoCollector = new DefaultZipInfoCollector();

			PersonIdent person = new PersonIdent("test", "test@zipdeduplicate.incubator");

			BulkInsert bInsert = new BulkInsert(git, onlyThisExtensions(extensions));
			ObjectId treeId = bInsert.doIt(pathToInput, zipInfoCollector);
			ObjectId commitId = bInsert.createOrFindCommit(branchName, person, treeId);
			zipInfoCollector.linkDataContainer(commitId);

			bInsert.ensureDescriptionCommitOnHeadOfBranch(branchName, zipInfoCollector, person, additionalData);

			Iterable<PushResult> call = git.push().setProgressMonitor(new PrintingProgressMonitor()).setPushAll()
					.call();
			call.forEach(pr -> {
				pr.getRemoteUpdates().forEach(u -> System.out.println(u.getRemoteName() + " " + u.getStatus()));
				Optional.ofNullable(pr.getMessages()).ifPresent(System.out::println);
			});
		}

	}

	private void ensureDescriptionCommitOnHeadOfBranch(String branchName, ZipInfoCollector zipInfoCollector,
			PersonIdent person, String additionalData) throws IOException, GitAPIException {
		byte[] rawDescriptionDataInLastCommit = getRawDescriptionDataInLastCommit(branchName);
		byte[] dumpInfo = zipInfoCollector.dumpInfo();
		boolean sameAdditionalData = Objects.equals(additionalData, getComment(branchName));

		if (!sameAdditionalData || !Arrays.equals(dumpInfo, rawDescriptionDataInLastCommit)) {
			ObjectInserter oi = repo.newObjectInserter();
			ObjectId treeId = oi.insert(Constants.OBJ_BLOB, dumpInfo);
			TreeFormatter treeFormatter = new TreeFormatter(1);
			treeFormatter.append("description", FileMode.REGULAR_FILE, treeId);
			treeId = oi.insert(treeFormatter);
			insertCommitOnBranch(treeId, REFS_DESC_PREFIX + branchName, person, additionalData);
		}
	}

	private ObjectId createOrFindCommit(String branchName, PersonIdent person, ObjectId treeId)
			throws IOException, GitAPIException {
		ObjectId commitId = fetchAndFindCommitWithTreeId(branchName, git, treeId);
		if (commitId == null) {
			commitId = insertCommitOnBranch(treeId, REFS_DATA_PREFIX + branchName, person, null);
		}
		return commitId;
	}

	private static Git openOrCreateGit(Path workPath) throws GitAPIException, IOException {
		Git git;
		if (!Files.exists(workPath)) {
			git = Git.init().setDirectory(workPath.toFile()).setBare(true).call();
		} else {
			git = Git.open(workPath.toFile());
		}
		return git;
	}

	private byte[] getRawDescriptionDataInLastCommit(String branchName) {
		try {
			Ref findRef = git.getRepository().getRefDatabase().findRef(REFS_DESC_PREFIX + branchName);
			if (findRef == null) {
				return null;
			}
			ObjectId objectId = findRef.getObjectId();
			RevCommit commit = Commit.parse(repo.newObjectReader().newReader().open(objectId).getBytes());
			return DescriptionUtil.extractDesciptionRawData(git.getRepository(), commit);
		} catch (IOException e) {
			return new byte[0];
		}
	}

	private String getComment(String branchName) {
		try {
			Ref findRef = git.getRepository().getRefDatabase().findRef(REFS_DESC_PREFIX + branchName);
			if (findRef == null) {
				return null;
			}
			ObjectId objectId = findRef.getObjectId();
			RevCommit commit = Commit.parse(repo.newObjectReader().newReader().open(objectId).getBytes());
			return Optional.ofNullable(commit.getFullMessage()).map(s -> s.isEmpty() ? null : s).orElse(null);
		} catch (IOException e) {
			return null;
		}
	}

	private static ObjectId fetchAndFindCommitWithTreeId(String branchName, Git git, ObjectId treeId) {
		try {
			String dataRef = REFS_DATA_PREFIX + branchName;
			RefSpec dataRefSpec = new RefSpec(dataRef + ":" + REFS_DATA_PREFIX + branchName);
			RefSpec descRefSpec = new RefSpec(REFS_DESC_PREFIX + branchName + ":" + REFS_DESC_PREFIX + branchName);

			FetchResult fetchResult = git.fetch().setRemote(ORIGIN).setRefSpecs(dataRefSpec, descRefSpec)
					.setProgressMonitor(new PrintingProgressMonitor()).setForceUpdate(true).call();
			System.out.println(fetchResult.getMessages());

			RefDatabase refDatabase = git.getRepository().getRefDatabase();
			Ref findRef = refDatabase.findRef(dataRef);

			ObjectId objectId = findRef.getObjectId();
			Iterator<RevCommit> commitIter = git.log().add(objectId).call().iterator();
			ObjectId commitId = null;
			while (commitIter.hasNext() && commitId == null) {
				RevCommit next = commitIter.next();
				ObjectId id = next.getTree().getId();
				if (treeId.equals(id)) {
					commitId = next.getId();
				}
			}
			return commitId;
		} catch (GitAPIException | IOException e) {
			return null;
		}
	}

	public static Predicate<String> onlyThisExtensions(List<String> extensions) {
		return s -> {
			String sl = s.toLowerCase();
			return extensions.stream().anyMatch(e -> sl.endsWith(e));
		};
	}

	public ObjectId doIt(Path pathToAnalyse, ZipInfoCollector zipInfoCollector) throws IOException, GitAPIException {

		Map<Path, List<PathWithObjectId>> allFilesGroupedByPathes;
		Map<String, AnyObjectId> rootElements = Collections.synchronizedMap(new TreeMap<>(gitNameComperator()));

		Stream<Path> filesToInsert;
		Path root;
		if (Files.isDirectory(pathToAnalyse)) {
			filesToInsert = Files.walk(pathToAnalyse);
			root = pathToAnalyse;
		} else {
			filesToInsert = Stream.of(pathToAnalyse);
			root = pathToAnalyse.getParent();
		}
		try (Stream<Path> walk = filesToInsert) {
			Stream<PathWithObjectId> filesWithObjectId = walk.collect(Collectors.toList()).stream()//
					.parallel()//
					.filter(Files::isReadable)//
					.filter(Files::isRegularFile)//
					.flatMap(p -> {
						Path relP = root.relativize(p);
						return singleFileToGit(zipInfoCollector, p, relP);
					});

			Map<Integer, List<PathWithObjectId>> splitted = filesWithObjectId
					.collect(Collectors.groupingBy(t -> t.p.getParent() == null ? NO_PARENT : WITH_PARENT));

			if (splitted.containsKey(NO_PARENT)) {
				rootElements.putAll(splitted.get(NO_PARENT).stream()//
						.collect(Collectors.toMap( //
								t -> t.p.toString(), //
								t -> t.id//
						)));
			}

			if (splitted.containsKey(WITH_PARENT)) {
				allFilesGroupedByPathes = splitted.get(WITH_PARENT).stream()//
						.collect(Collectors.groupingBy( //
								t -> t.p.getParent(), //
								Collectors.toList())//
						);
			} else {
				allFilesGroupedByPathes = Collections.emptyMap();
			}

			// Alle inhalte im Git enthalten.
			// Alle Files nach Parent sortieren.
		}

		Map<Path, Map<String, AnyObjectId>> filesGroupedByPathAndSortedByName = sortedMapOfPathesByLengthAndName();

		for (Entry<Path, List<PathWithObjectId>> entry : allFilesGroupedByPathes.entrySet()) {
			TreeMap<String, AnyObjectId> elements = new TreeMap<>(gitNameComperator());
			for (PathWithObjectId tup : entry.getValue()) {
				elements.put(tup.p.getFileName().toString(), tup.id);
			}
			filesGroupedByPathAndSortedByName.put(entry.getKey(), elements);
		}

		// Einfügen von
		Set<Path> allPathesContainingFiles = allFilesGroupedByPathes.keySet();
		allPathesContainingFiles.forEach(p -> {
			while (p.getParent() != null) {
				AnyObjectId prev = //
						filesGroupedByPathAndSortedByName
								.computeIfAbsent(p.getParent(), o -> new TreeMap<>(gitNameComperator()))
								.putIfAbsent(p.getFileName().toString() + "/", createNewTreeFormatter()//
				);
				p = p.getParent();
			}
		});

		for (Entry<Path, Map<String, AnyObjectId>> e : filesGroupedByPathAndSortedByName.entrySet()) {
			SortingTreeFormatter tf = buildTree(e.getKey(), e.getValue());
			Map<String, AnyObjectId> parentTree = Optional.ofNullable(e.getKey().getParent())//
					.map(filesGroupedByPathAndSortedByName::get)//
					.orElse(rootElements);

			parentTree.put(e.getKey().getFileName().toString() + "/", tf);
		}

		SortingTreeFormatter rootFormatter = buildTree(Paths.get(""), rootElements);
		if (Files.isDirectory(pathToAnalyse)) {
			rootElements = new TreeMap<String, AnyObjectId>();
			rootElements.put(pathToAnalyse.getFileName().toString() + "/", rootFormatter);
			rootFormatter = buildTree(Paths.get(""), rootElements);
			zipInfoCollector.prefix(pathToAnalyse.getFileName().toString() + "/");
			zipInfoCollector.newZipFile(Paths.get(""));
		}
		ObjectId rootDirId = rootFormatter.toObjectId();

		return rootDirId;
	}

	private Stream<PathWithObjectId> singleFileToGit(ZipInfoCollector zipInfoCollector, Path p, Path relP) {
		try (InputStream inp = Files.newInputStream(p)) {
			ZipInputStream zin = new ZipInputStream(inp);
			ZipEntry firstEntry = zin.getNextEntry();
			if (firstEntry != null && maybeAZip(relP)) {
				return singleZipFileToGit(zipInfoCollector, relP, zin, firstEntry).get();
			} else {
				try (ByteArrayOutputStream bout = copyToByteArrayOutput(Files.newInputStream(p))) {
					ObjectId oid = oneObjectInserterPerThread.get().insert(Constants.OBJ_BLOB, bout.toByteArray());
					return Stream.of(new PathWithObjectId(relP, oid));
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}

		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		} catch (ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	private Comparator<String> gitNameComperator() {
		return new Comparator<String>() {

			@Override
			public int compare(String o1, String o2) {
				return o1.compareTo(o2);
//				int minLength = Math.min(o1.length(), o2.length());
//				int comp = o1.substring(0, minLength).compareTo(o2.substring(0, minLength));
//				return comp != 0 ? comp : o2.length() - o1.length();
			}
		};
	}

	private ObjectId insertCommitOnBranch(ObjectId treeId, String refName, PersonIdent authorAndComitter,
			String comment) throws IOException {
		Ref a = repo.getRefDatabase().findRef(refName);

		CommitBuilder cb = new CommitBuilder();
		if (comment != null) {
			cb.setMessage(comment);
		}
		cb.setAuthor(authorAndComitter);
		cb.setCommitter(cb.getAuthor());
		if (a != null) {
			cb.setParentId(a.getObjectId());
		}

		cb.setTreeId(treeId);
		ObjectId commitId = repo.newObjectInserter().insert(cb);

		RefUpdate updateRef = repo.updateRef(refName);
		updateRef.setNewObjectId(commitId);
		Result result = updateRef.update();
		return commitId;
	}

	private SortingTreeFormatter buildTree(Path basePath, Map<String, AnyObjectId> content) throws IOException {
		SortingTreeFormatter tf = createNewTreeFormatter();

		for (Entry<String, AnyObjectId> e2 : content.entrySet()) {
			AnyObjectId fileOrTree = e2.getValue();
			ObjectId oid = fileOrTree.toObjectId();
			String name = e2.getKey();
			FileMode fm = FileMode.REGULAR_FILE;
			if (fileOrTree instanceof SortingTreeFormatter) {
				name = name.substring(0, name.length() - 1);
				fm = FileMode.TREE;
			}
			tf.append(name, fm, oid);
		}
		return tf;
	}

	private SortingTreeFormatter createNewTreeFormatter() {
		return new SortingTreeFormatter(oneObjectInserterPerThread::get);
	}

	private static <V> Map<Path, V> sortedMapOfPathesByLengthAndName() {
		return new TreeMap<>((a, b) -> {
			int len = b.toString().length() - a.toString().length();
			if (len != 0) {
				return len;
			} else {
				return a.toString().compareTo(b.toString());
			}
		});
	}

	private Future<Stream<PathWithObjectId>> singleZipFileToGit(ZipInfoCollector zipInfoCollector, Path pin,
			ZipInputStream zin, ZipEntry firstEntry) throws IOException {
		boolean entryProcessed = false;
		List<Future<Stream<PathWithObjectId>>> futures = new ArrayList<>();
		ZipEntry nextEntry = firstEntry;
		while (nextEntry != null) {
			if (!entryProcessed && zipInfoCollector != null) {
				zipInfoCollector.newZipFile(pin);
			}
			if (!nextEntry.isDirectory()) {
				ByteArrayOutputStream bout = copyToByteArrayOutput(zin);
				ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
				Path nameAsPath = Paths.get(nextEntry.getName());
				Path namedElement = pin.resolve(nameAsPath);
				ZipInputStream possibleInnerZipInputStream = new ZipInputStream(bin);
				ZipEntry possibleFirstElement = possibleInnerZipInputStream.getNextEntry();
				if (maybeAZip(nameAsPath) && possibleFirstElement != null) {
					futures.add(singleZipFileToGit( //
							zipInfoCollector, //
							namedElement, //
							possibleInnerZipInputStream, //
							possibleFirstElement//
					));
				} else {
					futures.add(CompletableFuture.supplyAsync(() -> {
						try {
							return Stream.of(new PathWithObjectId(//
									namedElement, //
									oneObjectInserterPerThread.get().insert(Constants.OBJ_BLOB, bout.toByteArray())//
							));
						} catch (IOException e) {
							throw new RuntimeException(e);
						}

					}));
				}
			}
			entryProcessed = true;
			nextEntry = zin.getNextEntry();
		}
		if (futures.isEmpty()) {
			return CompletableFuture.completedFuture(Stream.empty());
		} else {
			CompletableFuture<Stream<PathWithObjectId>> retFuture = new CompletableFuture<Stream<PathWithObjectId>>();
			CompletableFuture<Stream<PathWithObjectId>>[] allFutures = futures.toArray(new CompletableFuture[0]);
			CompletableFuture.allOf(allFutures).thenRunAsync(() -> {
				try {
					retFuture.complete(//
							Arrays.stream(allFutures)//
									.flatMap(BulkInsert::getResultWithoutCheckedExceptions)//
					);
				} catch (Throwable th) {
					retFuture.completeExceptionally(th);
				}
			}, poolForUnlimitedAmountOfJoins);
			return retFuture;
		}
	}

	private boolean maybeAZip(Path nameAsPath) {
		return additionalFilenameZipPredicate.test(nameAsPath.getFileName().toString());
	}

	private static <T> T getResultWithoutCheckedExceptions(Future<T> fut) {
		try {
			return fut.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		} catch (ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	private static ByteArrayOutputStream copyToByteArrayOutput(InputStream in) throws IOException {
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		byte[] buf = new byte[4096];
		int read = in.read(buf);
		while (read > 0) {
			bout.write(buf, 0, read);
			read = in.read(buf);
		}
		return bout;
	}

	private static class PathWithObjectId {
		private final Path p;
		private final ObjectId id;

		private PathWithObjectId(Path p, ObjectId id) {
			this.p = p;
			this.id = id;
		}

	}

}

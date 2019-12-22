package jardeduplicate;

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

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Either;

public class BulkInsert {

	private static final Integer NO_PARENT = 0;
	private static final Integer WITH_PARENT = 1;

	private Repository repo;
	private ThreadLocal<ObjectInserter> oi = ThreadLocal.withInitial(() -> repo.newObjectInserter());
	private ExecutorService poolForUnlimitedAmountOfJoins;
	private Predicate<String> additionalFilenameZipPredicate;

	public BulkInsert(Repository repo) {
		this(repo, (s) -> true);
	}

	public BulkInsert(Repository repo, Predicate<String> additionalFilenameZipPredicate) {
		this.repo = repo;
		this.additionalFilenameZipPredicate = additionalFilenameZipPredicate;
		this.poolForUnlimitedAmountOfJoins = Executors.newCachedThreadPool();
	}

	public static void main(String[] args) throws GitAPIException, IOException {
		Path pathToInput = Paths.get(args[1]);

		String branchName = args[2];

		Path workPath = Paths.get(args[0]);
		Git git;
		if (!Files.exists(workPath)) {
			git = Git.init().setDirectory(workPath.toFile()).setBare(true).call();
		} else {
			git = Git.open(workPath.toFile());
		}

		List<String> extensions = Arrays.asList(".jar", ".war", ".ear", ".zip");

		long startTime = System.currentTimeMillis();
//		ZipInfoCollector zipInfoCollector = new BaseZipInfoCollector();
		ZipInfoCollector zipInfoCollector = new DefaultZipInfoCollector();

		ObjectId treeId = new BulkInsert(git.getRepository(), onlyThisExtensions(extensions))//
				.doIt(pathToInput, zipInfoCollector);

		ObjectId commitId = fetchAndFindCommitWithTreeId(branchName, git, treeId);

		PersonIdent person = new PersonIdent("test", "test@zipdeduplicate.incubator");
		if (commitId == null) {
			Tuple2<Result, ObjectId> result = insertCommitOnBranch(git.getRepository(), treeId, "data", person);
			System.out.println((System.currentTimeMillis() - startTime) + " ms. UpdateRefResult:" + result._1
					+ " commit-id:" + result._2);
			commitId = result._2;
		}

		zipInfoCollector.linkDataContainer(commitId);

		byte[] rawDescriptionDataInLastCommit = getRawDescriptionDataInLastCommit(branchName, git);

		byte[] dumpInfo = zipInfoCollector.dumpInfo();
		System.out.println(dumpInfo.length + " bytes");

		if (!Arrays.equals(dumpInfo, rawDescriptionDataInLastCommit)) {
			ObjectInserter oi = git.getRepository().newObjectInserter();
			ObjectId id = oi.insert(Constants.OBJ_BLOB, dumpInfo);
			TreeFormatter treeFormatter = new TreeFormatter(1);
			treeFormatter.append("description", FileMode.REGULAR_FILE, id);
			id = oi.insert(treeFormatter);
			insertCommitOnBranch(git.getRepository(), id, branchName, person);
		}

		Iterable<PushResult> call = git.push()
				.setProgressMonitor(new PrintingProgressMonitor())
				.setPushAll().call();
		call.forEach(pr -> {
			pr.getRemoteUpdates().forEach(u -> System.out.println(u.getRemoteName() + " " + u.getStatus()));
			Optional.ofNullable(pr.getMessages()).ifPresent(System.out::println);
		});
		git.close();

	}

	private static byte[] getRawDescriptionDataInLastCommit(String branchName, Git git) {
		try {
			Ref findRef = git.getRepository().getRefDatabase().findRef("refs/heads/" + branchName);
			if (findRef == null) {
				return null;
			}
			ObjectId objectId = findRef.getObjectId();
			RevCommit commit = Commit
					.parse(git.getRepository().newObjectReader().newReader().open(objectId).getBytes());
			byte[] rawDescriptionDataInLastCommit = DescriptionUtil.extractDesciptionRawData(git.getRepository(),
					commit);
			return rawDescriptionDataInLastCommit;
		} catch (IOException e) {
			return null;
		}
	}

	private static ObjectId fetchAndFindCommitWithTreeId(String branchName, Git git, ObjectId treeId) {
		try {
			FetchResult fetchResult = git.fetch().setRemote("origin")
					.setRefSpecs(new RefSpec("refs/heads/data:refs/heads/data"),
							new RefSpec("refs/heads/" + branchName + ":refs/heads/" + branchName))
					.setProgressMonitor(new PrintingProgressMonitor()).setForceUpdate(true).call();
//			System.out.println(fetchResult.getMessages());
//			fetchResult.getAdvertisedRefs().forEach(System.out::println);
//			fetchResult.getTrackingRefUpdates().forEach(System.out::println);

			RefDatabase refDatabase = git.getRepository().getRefDatabase();
//			refDatabase.getRefs().forEach(System.out::println);

//			RefUpdate newUpdate = refDatabase.newUpdate("refs/heads/data", false);
//			newUpdate.setNewObjectId(refDatabase.findRef("refs/remotes/origin/data").getObjectId());
//			newUpdate.setForceUpdate(true);
//			Result update = newUpdate.update();

			Ref findRef = refDatabase.findRef("refs/heads/data");

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

		Map<Path, List<Tuple2<Path, ObjectId>>> allFilesGroupedByPathes;
		Map<String, Either<ObjectId, SortingTreeFormatter>> rootElements = Collections
				.synchronizedMap(new TreeMap<>(gitNameComperator()));

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
			Stream<Tuple2<Path, ObjectId>> filesWithObjectId = walk.collect(Collectors.toList()).stream()//
					.parallel()//
					.filter(Files::isReadable)//
					.filter(Files::isRegularFile)//
					.flatMap(p -> {
						Path relP = root.relativize(p);
						return singleFileToGit(zipInfoCollector.resolve(relP.getParent()), p, relP);
					});

			Map<Integer, List<Tuple2<Path, ObjectId>>> splitted = filesWithObjectId
					.collect(Collectors.groupingBy(t -> t._1().getParent() == null ? NO_PARENT : WITH_PARENT));

			if (splitted.containsKey(NO_PARENT)) {
				rootElements.putAll(splitted.get(NO_PARENT).stream()//
						.collect(Collectors.toMap( //
								t -> t._1.toString(), //
								t -> Either.left(t._2)//
						)));
			}

			if (splitted.containsKey(WITH_PARENT)) {
				allFilesGroupedByPathes = splitted.get(WITH_PARENT).stream()//
						.collect(Collectors.groupingBy( //
								t -> t._1.getParent(), //
								Collectors.toList())//
						);
			} else {
				allFilesGroupedByPathes = Collections.emptyMap();
			}

			// Alle inhalte im Git enthalten.
			// Alle Files nach Parent sortieren.
		}

		Map<Path, Map<String, Either<ObjectId, SortingTreeFormatter>>> filesGroupedByPathAndSortedByName = sortedMapOfPathesByLengthAndName();

		for (Entry<Path, List<Tuple2<Path, ObjectId>>> entry : allFilesGroupedByPathes.entrySet()) {
			// Either(ObjectId=File,TreeFormater=Verzeichnis)
			TreeMap<String, Either<ObjectId, SortingTreeFormatter>> elements = new TreeMap<>(gitNameComperator());
			for (Tuple2<Path, ObjectId> tup : entry.getValue()) {
				elements.put(tup._1.getFileName().toString(), Either.left(tup._2));
			}
			filesGroupedByPathAndSortedByName.put(entry.getKey(), elements);
		}

		// Einfügen von
		Set<Path> allPathesContainingFiles = allFilesGroupedByPathes.keySet();
		allPathesContainingFiles.forEach(p -> {
			while (p.getParent() != null) {
				Either<ObjectId, SortingTreeFormatter> prev = //
						filesGroupedByPathAndSortedByName
								.computeIfAbsent(p.getParent(), o -> new TreeMap<>(gitNameComperator()))
								.putIfAbsent(p.getFileName().toString() + "/", Either.right(createNewTreeFormatter())//
				);
				p = p.getParent();
			}
		});

		for (Entry<Path, Map<String, Either<ObjectId, SortingTreeFormatter>>> e : filesGroupedByPathAndSortedByName
				.entrySet()) {
			SortingTreeFormatter tf = buildTree(e.getKey(), e.getValue());
			Map<String, Either<ObjectId, SortingTreeFormatter>> parentTree = Optional.ofNullable(e.getKey().getParent())//
					.map(filesGroupedByPathAndSortedByName::get)//
					.orElse(rootElements);

			parentTree.put(e.getKey().getFileName().toString() + "/", Either.right(tf));
		}

		SortingTreeFormatter rootFormatter = buildTree(Paths.get(""), rootElements);
		if (Files.isDirectory(pathToAnalyse)) {
			rootElements = new TreeMap<String, Either<ObjectId, SortingTreeFormatter>>();
			rootElements.put(pathToAnalyse.getFileName().toString() + "/", Either.right(rootFormatter));
			rootFormatter = buildTree(Paths.get(""), rootElements);
			zipInfoCollector.prefix(pathToAnalyse.getFileName().toString() + "/");
			zipInfoCollector.newZipFile(Paths.get(""));
		}
		ObjectId rootDirId = rootFormatter.insert(oi.get());

		return rootDirId;
	}

	private Stream<? extends Tuple2<Path, ObjectId>> singleFileToGit(ZipInfoCollector zipInfoCollector, Path p,
			Path relP) {
		try (InputStream inp = Files.newInputStream(p)) {
			ZipInputStream zin = new ZipInputStream(inp);
			ZipEntry firstEntry = zin.getNextEntry();
			if (firstEntry != null && maybeAZip(relP)) {
				return singleZipFileToGit(zipInfoCollector, relP, zin, firstEntry).get();
			} else {
				try (ByteArrayOutputStream bout = copyToByteArrayOutput(Files.newInputStream(p))) {
					ObjectId oid = oi.get().insert(Constants.OBJ_BLOB, bout.toByteArray());
					return Stream.of(Tuple.of(relP, oid));
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

	private static Tuple2<Result, ObjectId> insertCommitOnBranch(Repository repo, ObjectId treeId, String branchName,
			PersonIdent authorAndComitter) throws IOException, GitAPIException {
		String refName = "refs/heads/" + branchName;
		Ref a = repo.getRefDatabase().findRef(refName);

		CommitBuilder cb = new CommitBuilder();
		cb.setAuthor(authorAndComitter);
		cb.setCommitter(cb.getAuthor());
		if (a != null) {
			cb.setParentId(a.getObjectId());
		}

		cb.setTreeId(treeId);
		ObjectId commitId = repo.newObjectInserter().insert(cb);
		RefUpdate updateRef = repo.updateRef(refName);

		updateRef.setNewObjectId(commitId);
		return Tuple.of(updateRef.update(), commitId);
	}

	private SortingTreeFormatter buildTree(Path basePath, Map<String, Either<ObjectId, SortingTreeFormatter>> content)
			throws IOException {
		SortingTreeFormatter tf = createNewTreeFormatter();

		for (Entry<String, Either<ObjectId, SortingTreeFormatter>> e2 : content.entrySet()) {
			Either<ObjectId, SortingTreeFormatter> fileOrTree = e2.getValue();
			String name = e2.getKey();
			if (fileOrTree.isLeft()) {
				Path path = basePath.resolve(name);
				tf.append(name, FileMode.REGULAR_FILE, fileOrTree.getLeft());
			} else {
				name = name.substring(0, name.length() - 1);
				Path path = basePath.resolve(name);
				tf.append(name, FileMode.TREE, fileOrTree.get().insert(oi.get()));
			}
		}
		return tf;
	}

	private SortingTreeFormatter createNewTreeFormatter() {
		return new SortingTreeFormatter();
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

	private Future<Stream<Tuple2<Path, ObjectId>>> singleZipFileToGit(ZipInfoCollector zipInfoCollector, Path pin,
			ZipInputStream zin, ZipEntry firstEntry) throws IOException {
		boolean entryProcessed = false;
		List<Future<Stream<Tuple2<Path, ObjectId>>>> futures = new ArrayList<>();
		ZipEntry nextEntry = firstEntry;
		ZipInfo newZipFile = null;
		while (nextEntry != null) {
			if (!entryProcessed && zipInfoCollector != null) {
				newZipFile = zipInfoCollector.newZipFile(pin);
				zipInfoCollector = zipInfoCollector.resolve(pin);
			}
			if (newZipFile != null) {
				newZipFile.newEntry(nextEntry);
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
							return Stream.of(Tuple.of(//
									namedElement, //
									oi.get().insert(Constants.OBJ_BLOB, bout.toByteArray())//
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
			CompletableFuture<Stream<Tuple2<Path, ObjectId>>> retFuture = new CompletableFuture<Stream<Tuple2<Path, ObjectId>>>();
			CompletableFuture<Stream<Tuple2<Path, ObjectId>>>[] allFutures = futures.toArray(new CompletableFuture[0]);
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

}

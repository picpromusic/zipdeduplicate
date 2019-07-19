package jardeduplicate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.bouncycastle.util.io.Streams;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.ietf.jgss.Oid;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Either;

public class BulkInsert {

	private Repository repo;
	private ThreadLocal<ObjectInserter> oi = ThreadLocal.withInitial(() -> repo.newObjectInserter());

	public BulkInsert(Repository repo) {
		this.repo = repo;
	}

	public static void main(String[] args) throws GitAPIException, IOException {
		Path workPath = Paths.get("demoGit");
		Git git;
		if (!Files.exists(workPath)) {
			git = Git.init().setDirectory(workPath.toFile()).setBare(true).call();
		} else {
			git = Git.open(workPath.toFile());
		}

		long startTime = System.currentTimeMillis();
//		ZipInfoCollector zipInfoCollector = new BaseZipInfoCollector();
		ZipInfoCollector zipInfoCollector = new DefaultZipInfoCollector();

		ObjectId treeId = new BulkInsert(git.getRepository()).doIt(Paths.get(args[0]), zipInfoCollector);
		PersonIdent person = new PersonIdent("test", "test@zipdeduplicate.incubator");
		Tuple2<Result, ObjectId> result = insertCommitOnBranch(git.getRepository(), treeId, "data", person);
		System.out.println((System.currentTimeMillis() - startTime) + " ms. UpdateRefResult:" + result._1
				+ " commit-id:" + result._2);

		zipInfoCollector.linkDataContainer(result._2);

		byte[] dumpInfo = zipInfoCollector.dumpInfo();
		System.out.println(dumpInfo.length + " bytes");
		ObjectInserter oi = git.getRepository().newObjectInserter();
		ObjectId id = oi.insert(Constants.OBJ_BLOB, dumpInfo);
		TreeFormatter treeFormatter = new TreeFormatter(1);
		treeFormatter.append("description", FileMode.REGULAR_FILE, id);
		id = oi.insert(treeFormatter);
		result = insertCommitOnBranch(git.getRepository(), id, "master", person);

		git.gc()//
//		.setAggressive(true)//
				.setProgressMonitor(progressMonitor(System.out)).call();
	}

	private static ProgressMonitor progressMonitor(PrintStream out) {
		return new PrintingProgressMonitor(out);
	}

	public ObjectId doIt(Path pathToAnalyse, ZipInfoCollector zipInfoCollector, Predicate<Path> useHashValueAsName)
			throws IOException, GitAPIException {
		Map<Path, List<Tuple2<Path, ObjectId>>> allFilesGroupedByPathes;
		Map<String, Either<ObjectId, TreeFormatter>> rootElements = Collections.synchronizedMap(new TreeMap<>());

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
//					.parallel()//
					.filter(Files::isReadable)//
					.filter(Files::isRegularFile)//
					.flatMap(p -> {
						Path relP = root.relativize(p);
						return singleFileToGit(zipInfoCollector.resolve(relP.getParent()), p, relP);
					});

			Path aktPath = Paths.get("./");
			allFilesGroupedByPathes = filesWithObjectId//
					.peek(t -> {
						if (t._1.getParent() == null) {
							rootElements.put(t._1.toString(), Either.left(t._2));
						}
					}) //
					.filter(t -> t._1.getParent() != null) //
					.collect(Collectors.groupingBy( //
							t -> t._1.getParent(), //
							Collectors.toList())//
					);

			// Alle inhalte im Git enthalten.
			// Alle Files nach Parent sortieren.
		}

		Map<Path, Map<String, Either<ObjectId, TreeFormatter>>> filesGroupedByPathAndSortedByName = sortedMapOfPathesByLengthAndName();

		for (Entry<Path, List<Tuple2<Path, ObjectId>>> entry : allFilesGroupedByPathes.entrySet()) {
			// Either(ObjectId=File,TreeFormater=Verzeichnis)
			TreeMap<String, Either<ObjectId, TreeFormatter>> elements = new TreeMap<>(gitNameComperator());
			for (Tuple2<Path, ObjectId> tup : entry.getValue()) {
				elements.put(tup._1.getFileName().toString(), Either.left(tup._2));
			}
			filesGroupedByPathAndSortedByName.put(entry.getKey(), elements);
		}

		// Einfügen von
		Set<Path> allPathesContainingFiles = allFilesGroupedByPathes.keySet();
		allPathesContainingFiles.forEach(p -> {
			while (p.getParent() != null) {
				Either<ObjectId, TreeFormatter> prev = //
						filesGroupedByPathAndSortedByName.computeIfAbsent(p.getParent(), o -> new TreeMap<>())
								.putIfAbsent(p.getFileName().toString() + "/", Either.right(new TreeFormatter())//
				);
				p = p.getParent();
			}
		});

		for (Entry<Path, Map<String, Either<ObjectId, TreeFormatter>>> e : filesGroupedByPathAndSortedByName
				.entrySet()) {
			TreeFormatter tf = buildTree(e.getKey(), e.getValue(), useHashValueAsName);
			Map<String, Either<ObjectId, TreeFormatter>> parentTree = Optional.ofNullable(e.getKey().getParent())//
					.map(filesGroupedByPathAndSortedByName::get)//
					.orElse(rootElements);

			parentTree.put(e.getKey().getFileName().toString() + "/", Either.right(tf));
		}

		TreeFormatter rootFormatter = buildTree(Paths.get(""), rootElements, useHashValueAsName);
		ObjectId rootDirId = oi.get().insert(rootFormatter);

		return rootDirId;
	}

	private Stream<? extends Tuple2<Path, ObjectId>> singleFileToGit(ZipInfoCollector zipInfoCollector, Path p,
			Path relP) {
		try (InputStream inp = Files.newInputStream(p)) {
			ZipInputStream zin = new ZipInputStream(inp);
			Spliterator<Tuple2<Path, ObjectId>> splitarator = tryWritingSingleZipFileToGit(zipInfoCollector, relP, zin)
					.spliterator();
			List<Tuple2<Path, ObjectId>> first = new ArrayList<>(1); // ValueHolder for first Object
			if (splitarator.tryAdvance(first::add)) { // has atleast one Object
				return Stream.concat(first.stream(), StreamSupport.stream(splitarator, true));
			} else {
				try (ByteArrayOutputStream bout = copyToByteArrayOutput(Files.newInputStream(p))) {
					ObjectId oid = oi.get().insert(Constants.OBJ_BLOB, bout.toByteArray());
					return Stream.of(Tuple.of(relP, oid));
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			return Stream.empty();
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

	private TreeFormatter buildTree(Path basePath, Map<String, Either<ObjectId, TreeFormatter>> content,
			Predicate<Path> useHashValueAsName) throws IOException {
		TreeFormatter tf = new TreeFormatter();

		for (Entry<String, Either<ObjectId, TreeFormatter>> e2 : content.entrySet()) {
			Either<ObjectId, TreeFormatter> fileOrTree = e2.getValue();
			String name = e2.getKey();
			if (fileOrTree.isLeft()) {
				Path path = basePath.resolve(name);
				name = useHashValueAsName.test(path) ? name : name; // TODO: Do clever trick to not put it a second time, and in right order
				tf.append(name, FileMode.REGULAR_FILE, fileOrTree.getLeft());
			} else {
				name = name.substring(0, name.length() - 1);
				Path path = basePath.resolve(name);
				name = useHashValueAsName.test(path) ? name : name; // TODO: Do clever trick to not put it a second time, and in right order
				tf.append(name, FileMode.TREE, oi.get().insert(fileOrTree.get()));
			}
		}
		return tf;
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

	private Stream<Tuple2<Path, ObjectId>> tryWritingSingleZipFileToGit(ZipInfoCollector zipInfoCollector, Path pin,
			ZipInputStream zin) throws IOException {
		boolean entryProcessed = false;
		List<Tuple2<Path, ObjectId>> res = new ArrayList<>();
		ZipEntry nextEntry = zin.getNextEntry();
		ZipInfo newZipFile = null;
		while (nextEntry != null) {
			if (!entryProcessed && zipInfoCollector != null) {
				newZipFile = zipInfoCollector.newZipFile(pin.getFileName());
			}
			if (newZipFile != null) {
				newZipFile.newEntry(nextEntry);
			}
			if (!nextEntry.isDirectory()) {
				ByteArrayOutputStream bout = copyToByteArrayOutput(zin);
				ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
				Path nameAsPath = Paths.get(nextEntry.getName());
				Path namedElement = pin.resolve(nameAsPath);
				long count = tryWritingSingleZipFileToGit(zipInfoCollector.resolve(nameAsPath.getParent()),
						namedElement, new ZipInputStream(bin)).peek(res::add).count();
				if (count == 0) {
					res.add(Tuple.of( //
							namedElement, oi.get().insert(Constants.OBJ_BLOB, bout.toByteArray())//
					));
				}
			}
			entryProcessed = true;
			nextEntry = zin.getNextEntry();
		}
		if (entryProcessed && zipInfoCollector != null) {
			newZipFile = null;
		}
		return res.stream();
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

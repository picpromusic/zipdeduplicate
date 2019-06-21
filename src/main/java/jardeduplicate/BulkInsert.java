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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
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
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Either;

public class BulkInsert {

	private ObjectInserter oi;

	public BulkInsert(ObjectInserter oi) {
		this.oi = oi;
	}

	public static void main(String[] args) throws GitAPIException, IOException {
		Path workPath = Paths.get("git");
		Git git;
		if (!Files.exists(workPath)) {
			git = Git.init().setDirectory(workPath.toFile()).setBare(true).call();
		} else {
			git = Git.open(workPath.toFile());
		}

		long startTime = System.currentTimeMillis();
		ObjectInserter oi = git.getRepository().newObjectInserter();
		ZipInfoCollector zipInfoCollector = null;
		ObjectId treeId = new BulkInsert(oi).doIt(Paths.get(args[0]), zipInfoCollector);
		PersonIdent person = new PersonIdent("test", "test@zipdeduplicate.incubator");
		Result result = insertCommitOnBranch(git.getRepository(), treeId, "master2", person);
		System.out.println(System.currentTimeMillis() + " ms. UpdateRefResult:" + result);

		git.gc().setAggressive(true).setProgressMonitor(progressMonitor(System.out)).call();
	}

	private static ProgressMonitor progressMonitor(PrintStream out) {
		return new PrintingProgressMonitor(out);
	}

	public ObjectId doIt(Path pathToAnalyse, ZipInfoCollector zipInfoCollector) throws IOException, GitAPIException {
		Map<Path, List<Tuple2<Path, ObjectId>>> allFilesGroupedByPathes;

		try (Stream<Path> walk = Files.walk(pathToAnalyse)) {

			Stream<Tuple2<Path, ObjectId>> filesWithObjectId = walk.parallel().filter(Files::isReadable).flatMap(p -> {
				Path relP = pathToAnalyse.relativize(p);
				try {
					InputStream inp = Files.newInputStream(p);
					try {
						ZipInputStream zin = new ZipInputStream(inp);
						return toGit(oi, zipInfoCollector, Tuple.of(relP, zin));
					} catch (IOException e) {
						ByteArrayOutputStream bout = copyToByteArrayOutput(inp);
						ObjectId oid = oi.insert(Constants.OBJ_BLOB, bout.toByteArray());
						return Stream.of(Tuple.of(relP, oid));
					}
				} catch (IOException e) {
					return Stream.empty();
				}
			});

			allFilesGroupedByPathes = filesWithObjectId
					.collect(Collectors.groupingBy(t -> t._1.getParent(), Collectors.toList()));

			// Alle inhalte im Git enthalten.
			// Alle Files nach Parent sortieren.
		}

		Map<Path, TreeMap<String, Either<ObjectId, TreeFormatter>>> filesGroupedByPathAndSortedByName = sortedMapOfPathesByLengthAndName();

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
								.putIfAbsent(p.getFileName().toString(), Either.right(new TreeFormatter())//
				);
				if (prev == null) {
//					System.out.println(p);
				}
				p = p.getParent();
			}
		});

		TreeMap<String, Either<ObjectId, TreeFormatter>> rootElements = new TreeMap<>();

		for (Entry<Path, TreeMap<String, Either<ObjectId, TreeFormatter>>> e : filesGroupedByPathAndSortedByName
				.entrySet()) {
			TreeFormatter tf = buildTree(e.getValue());
//			System.out.println(tf);
			TreeMap<String, Either<ObjectId, TreeFormatter>> parentTree = Optional.ofNullable(e.getKey().getParent())//
					.map(filesGroupedByPathAndSortedByName::get)//
					.orElse(rootElements);
			parentTree.put(e.getKey().getFileName().toString(), Either.right(tf));
//			System.out.println("\n\n:"+e.getKey()+"\n\t"+parentTree);
		}

		TreeFormatter rootFormatter = buildTree(rootElements);
		ObjectId rootDirId = oi.insert(rootFormatter);

		return rootDirId;
	}

	private Comparator<String> gitNameComperator() {
		return new Comparator<String>() {

			@Override
			public int compare(String o1, String o2) {
				int minLength = Math.min(o1.length(), o2.length());
				int comp = o1.substring(0,minLength).compareTo(o2.substring(0,minLength));
				return comp != 0 ? comp : o1.length() - o2.length();
			}
		};
	}

	private static Result insertCommitOnBranch(Repository repo, ObjectId treeId, String branchName,
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
		return updateRef.update();
	}

	private TreeFormatter buildTree(TreeMap<String, Either<ObjectId, TreeFormatter>> content) throws IOException {
		TreeFormatter tf = new TreeFormatter();
		for (Entry<String, Either<ObjectId, TreeFormatter>> e2 : content.entrySet()) {
			Either<ObjectId, TreeFormatter> fileOrTree = e2.getValue();
			if (fileOrTree.isLeft()) {
				tf.append(e2.getKey(), FileMode.REGULAR_FILE, fileOrTree.getLeft());
			} else {
				tf.append(e2.getKey(), FileMode.TREE, oi.insert(fileOrTree.getOrNull()));
			}
		}
		return tf;
	}

	private static <V> TreeMap<Path, V> sortedMapOfPathesByLengthAndName() {
		return new TreeMap<>((a, b) -> {
			int len = b.toString().length() - a.toString().length();
			if (len != 0) {
				return len;
			} else {
				return a.toString().compareTo(b.toString());
			}

		});
	}

	static Stream<Tuple2<Path, ObjectId>> toGit(ObjectInserter oi, ZipInfoCollector zipInfoCollector,
			Tuple2<Path, ZipInputStream> tup) throws IOException {
		if (zipInfoCollector != null) {
			zipInfoCollector.newZipFile(tup._1);
		}
		List<Tuple2<Path, ObjectId>> res = new ArrayList<>();
		Path pin = tup._1;
		ZipInputStream zin = tup._2;
		ZipEntry nextEntry = zin.getNextEntry();
		while (nextEntry != null) {
			if (zipInfoCollector != null) {
				zipInfoCollector.newEntry(nextEntry);
			}
			if (!nextEntry.isDirectory()) {
				ByteArrayOutputStream bout = copyToByteArrayOutput(zin);
				ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
				Path namedElement = pin.resolve(nextEntry.getName());
				long count = toGit(oi, zipInfoCollector, Tuple.of(namedElement, new ZipInputStream(bin))).peek(res::add)
						.count();
				if (count == 0) {
					res.add(Tuple.of( //
							namedElement, oi.insert(Constants.OBJ_BLOB, bout.toByteArray())//
					));
				}
			}
			nextEntry = zin.getNextEntry();
		}
		if (zipInfoCollector != null) {
			zipInfoCollector.endOfZipFile();
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

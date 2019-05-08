package jardeduplicate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
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
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;

import io.vavr.control.Try;
import io.vavr.Tuple;
import io.vavr.Tuple2;

public class Test {

	public static void main(String[] args) throws GitAPIException, IOException {
		Path workPath = Paths.get("git");
		Git git;
		if (!Files.exists(workPath)) {
			git = Git.init().setDirectory(workPath.toFile()).setBare(true).call();
		} else {
			git = Git.open(workPath.toFile());
		}

		long time = System.currentTimeMillis();
		Path repoPath = Paths.get(System.getProperty("user.home")).resolve(".m2").resolve("repository");
		Stream<Path> walk = Files.walk(repoPath);

		Stream<Tuple2<Path, ObjectId>> flatMap = walk//
//				.peek(System.out::println)
				.filter(Files::isReadable) //
				.map(p -> Try.of(() -> Tuple.of(p, Files.newInputStream(p)))) //
				.map(ty -> ty.mapTry(tup -> tup.map1(p -> repoPath.relativize(p)))) //
				.map(ty -> ty.mapTry(tup -> tup.map2(ZipInputStream::new))) //
				.map(ty -> ty.mapTry(tup -> toGit(git, tup))) //
				.flatMap(ty -> ty.getOrElseGet((x) -> Stream.empty()))//
		;

		Repository repo = git.getRepository();

		Map<Path, List<Tuple2<Path, ObjectId>>> collect = flatMap
				.collect(Collectors.groupingBy(t -> t._1.getParent(), Collectors.toList()));

		Map<Path, TreeFormatter> dirs = sortedMap();
		for (Entry<Path, List<Tuple2<Path, ObjectId>>> entry : collect.entrySet()) {
			TreeFormatter treeFormatter = new TreeFormatter();
			for (Tuple2<Path, ObjectId> tup : entry.getValue()) {
				treeFormatter.append(tup._1.getFileName().toString(), FileMode.REGULAR_FILE, tup._2);
			}
			dirs.put(entry.getKey(), treeFormatter);
		}
		collect.keySet().forEach(p -> {
			while (p != null) {
				dirs.computeIfAbsent(p, (o) -> new TreeFormatter());
				p = p.getParent();
			}
		});

		TreeFormatter rootDir = new TreeFormatter();

		Map<Path, ObjectId> treeIds = sortedMap();
		for (Entry<Path, TreeFormatter> entry : dirs.entrySet()) {
			ObjectId treeId = entry.getValue().insertTo(repo.newObjectInserter());
			Path path = entry.getKey();
			Optional.ofNullable(path.getParent())//
					.map(dirs::get)//
					.orElse(rootDir)//
					.append(path.getFileName().toString(), FileMode.TREE, treeId);
			treeIds.put(path, treeId);
		}

		for (Path path : treeIds.keySet()) {
//			System.out.println(path);
		}
		System.out.println(collect.size());
		
		ObjectId rootDirId = rootDir.insertTo(repo.newObjectInserter());
		CommitBuilder cb = new CommitBuilder();
		cb.setAuthor(new PersonIdent("test","test@email"));
		cb.setCommitter(cb.getAuthor());
		
		cb.setTreeId(rootDirId);
		ObjectId commitId = repo.newObjectInserter().insert(cb);
		RefUpdate updateRef = repo.updateRef("refs/heads/master");
		
		updateRef.setNewObjectId(commitId);
		updateRef.update();
	}

	private static <V> TreeMap<Path, V> sortedMap() {
		return new TreeMap<>((a, b) -> {
			int len = b.toString().length() - a.toString().length();
			if (len != 0) {
				return len;
			} else {
				return a.toString().compareTo(b.toString());
			}

		});
	}

	static Stream<Tuple2<Path, ObjectId>> toGit(Git g, Tuple2<Path, ZipInputStream> tup) throws IOException {
		List<Tuple2<Path, ObjectId>> res = new ArrayList<>();
		Path pin = tup._1;
		ZipInputStream zin = tup._2;
		ZipEntry nextEntry = zin.getNextEntry();
		while (nextEntry != null) {
			if (!nextEntry.isDirectory()) {
				ByteArrayOutputStream bout = new ByteArrayOutputStream();
				byte[] buf = new byte[4096];
				int read = zin.read(buf);
				do {
					bout.write(buf, 0, read);
					read = zin.read(buf);
				} while (read >= 0);
				ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());

				Path namedElement = pin.resolve(nextEntry.getName());

				long count = toGit(g, Tuple.of(namedElement, new ZipInputStream(bin))).peek(res::add).count();
				if (count == 0) {
					res.add(Tuple.of( //
							namedElement,
							g.getRepository().newObjectInserter().insert(Constants.OBJ_BLOB, bout.toByteArray())//
					));
				} else {
					System.out.println("WOW");
				}

			}
			nextEntry = zin.getNextEntry();
		}
		return res.stream();
	}

}

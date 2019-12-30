package oss.zipdeduplicate;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Comparator;
import java.util.SortedMap;
import java.util.TreeSet;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;

public class DescriptionUtil {

	private DescriptionUtil() {
	}

	public static ObjectId extractDescription(Repository repo, RevCommit commit,
			SortedMap<String, TreeSet<PathAsStringAndObjectId>> allZipPathes) throws IOException {

		ByteArrayInputStream bin = new ByteArrayInputStream(extractDesciptionRawData(repo, commit));
		BufferedReader bufr = new BufferedReader(new InputStreamReader(bin));
		ObjectId contentCommitId = ObjectId.fromString(bufr.readLine());
		String content = bufr.readLine();
		while (content != null) {
			if (content.endsWith("/")) {
				content = content.substring(0, content.length() - 1);
			}
			allZipPathes.put(content, new TreeSet<PathAsStringAndObjectId>(onlyPathCompare()));
			content = bufr.readLine();
		}
		return contentCommitId;
	}

	public static byte[] extractDesciptionRawData(Repository repo, RevCommit commit) throws IOException {

		try (ObjectReader or = repo.newObjectReader(); //
				TreeWalk walk = new TreeWalk(repo)//
		) {
			walk.addTree(commit.getTree());
			while (walk.next()) {
				if (walk.getPathString().equals("description")) {
					return or.open(walk.getObjectId(0)).getBytes();
				}
			}
			return new byte[0];
		}
	}

	private static Comparator<? super PathAsStringAndObjectId> onlyPathCompare() {
		return (t1, t2) -> t1.path.compareTo(t2.path);
	}

	public static class PathAsStringAndObjectId {
		private final String path;
		private final ObjectId id;

		public PathAsStringAndObjectId(String path, ObjectId id) {
			this.path = path;
			this.id = id;
		}
	}
}

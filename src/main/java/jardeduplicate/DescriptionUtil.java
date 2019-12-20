package jardeduplicate;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.TreeSet;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;

import io.vavr.Tuple2;

public class DescriptionUtil {

	public static ObjectId extractDescription(Repository repo, RevCommit commit,
			TreeMap<String, TreeSet<Tuple2<String, ObjectId>>> allZipPathes)
			throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException, IOException {

		ByteArrayInputStream bin = new ByteArrayInputStream(extractDesciptionRawData(repo, commit));
		BufferedReader bufr = new BufferedReader(new InputStreamReader(bin));
		ObjectId contentCommitId = ObjectId.fromString(bufr.readLine());
		String content = bufr.readLine();
		while (content != null) {
			if (content.endsWith("/")) {
				content = content.substring(0,content.length()-1);
			}
			allZipPathes.put(content, new TreeSet<Tuple2<String, ObjectId>>(onlyStringCompare()));
			content = bufr.readLine();
		}
		return contentCommitId;
	}

	public static byte[] extractDesciptionRawData(Repository repo, RevCommit commit)
			throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException, IOException {
		
		ObjectId contentCommitId = null;
		ObjectReader or = repo.newObjectReader();
		TreeWalk walk = new TreeWalk(repo);
		walk.addTree(commit.getTree());
		while (walk.next()) {
			if (walk.getPathString().equals("description")) {
				return or.open(walk.getObjectId(0)).getBytes();
			}
		}
		return null;
	}

	private static Comparator<? super Tuple2<String, ObjectId>> onlyStringCompare() {
		return (t1, t2) -> t1._1.compareTo(t2._1);
	}
}

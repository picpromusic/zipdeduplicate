package io.github.picpromusic.zipdeduplicate;

import org.eclipse.jgit.lib.PersonIdent;

public class ZipDedupUtils {

	public static Branch urlToBranchName(String url) {
		return new Branch(url.replaceFirst("http://", "").replace(':', '_'));
	}

	public static PersonIdent getDescCommitPersonIdent() {
		PersonIdent person = new PersonIdent("test", "test@zipdeduplicate.incubator");
//		PersonIdent person = new PersonIdent("desc", "test@zipdeduplicate.picpromusic.github.io");
		return person;
	}

	public static PersonIdent getDataCommitPersonIdent() {
		PersonIdent person = new PersonIdent("test", "test@zipdeduplicate.incubator", 0, 0);
//		PersonIdent person = new PersonIdent("data", "data@zipdeduplicate.picpromusic.github.io", 0, 0);
		return person;
	}

}

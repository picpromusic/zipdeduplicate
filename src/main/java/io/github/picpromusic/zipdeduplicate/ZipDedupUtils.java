package io.github.picpromusic.zipdeduplicate;

public class ZipDedupUtils {

	static String urlToBranchName(String url) {
		return url.replaceFirst("http://", "").replace(':', '_');
	}

}

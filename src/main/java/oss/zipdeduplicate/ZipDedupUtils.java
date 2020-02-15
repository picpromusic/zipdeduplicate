package oss.zipdeduplicate;

public class ZipDedupUtils {

	static String urlToBranchName(String url) {
		return url.replaceFirst("http://", "").replace(':', '_');
	}

}

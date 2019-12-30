package oss.zipdeduplicate;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.jgit.lib.ObjectId;

public class DefaultZipInfoCollector implements ZipInfoCollector {

	private String dataContainer;
	private List<String> allZipPathes;
	private String prefix;
	private static Charset UTF8 = Charset.forName("UTF-8");

	public DefaultZipInfoCollector() {
		this.allZipPathes = Collections.synchronizedList(new ArrayList<>());
	}

	@Override
	public void newZipFile(Path name) {
		this.allZipPathes.add(name.toString().replaceAll("\\\\", "/"));
	}

	@Override
	public byte[] dumpInfo() {
		StringBuilder sb = new StringBuilder();
		sb.append(dataContainer);
		sb.append("\n");
		SortedSet<String> allSortedPathes = new TreeSet<>();
		allSortedPathes.addAll(allZipPathes);
		for (String strings : allSortedPathes) {
			if (prefix != null) {
				sb.append(prefix);
			}
			sb.append(strings);
			sb.append("\n");
		}
		return sb.toString().getBytes(UTF8);
	}

	@Override
	public void linkDataContainer(ObjectId commitId) {
		this.dataContainer = ObjectId.toString(commitId);
	}

	List<String> getAllZipPathes() {
		return allZipPathes;
	}

	@Override
	public void prefix(String prefix) {
		this.prefix = prefix;
	}
}

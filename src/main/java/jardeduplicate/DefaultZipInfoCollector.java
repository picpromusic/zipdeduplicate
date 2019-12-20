package jardeduplicate;

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
	private Optional<DefaultZipInfoCollector> basis;
	private Path path;
	private String prefix;
	private static Charset UTF8 = Charset.forName("UTF-8");

	public DefaultZipInfoCollector() {
		this.allZipPathes = Collections.synchronizedList(new ArrayList<>());
		this.path = Paths.get("");
		this.basis = Optional.empty();
	}

	public DefaultZipInfoCollector(DefaultZipInfoCollector basis, Path path) {
		this.basis = Optional.of(basis);
		this.path = path;
	}

	@Override
	public ZipInfo newZipFile(Path name) {
		// Do not use map / orElseGet combination as in WithTimingZipInfoCollector.
		// Because ((DefaultZipInfoCollector)basis).newZipFile will also return null
		if (basis.isPresent()) {
			return basis.get().newZipFile(name);
		} else {
			this.allZipPathes.add(name.toString().replaceAll("\\\\", "/"));
			return null;
		}
	}

	@Override
	public byte[] dumpInfo() {
		return this.basis.map(ZipInfoCollector::dumpInfo).orElseGet(() -> {
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
		});
	}

	@Override
	public void linkDataContainer(ObjectId commitId) {
		basis.ifPresent(b -> b.linkDataContainer(commitId));
		this.dataContainer = ObjectId.toString(commitId);
	}

	@Override
	public ZipInfoCollector resolve(Path name) {
		if (name == null) {
			return this;
		}
		return new DefaultZipInfoCollector(basis.orElse(this), name);
	}

	List<String> getAllZipPathes() {
		return allZipPathes;
	}

	@Override
	public void prefix(String prefix) {
		this.prefix = prefix;
	}
}

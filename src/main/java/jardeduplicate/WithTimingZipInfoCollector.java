package jardeduplicate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.eclipse.jgit.lib.ObjectId;

public class WithTimingZipInfoCollector implements ZipInfoCollector {

	private String dataContainer;
	private Map<Path, ZipInfo> allZipInfos;
	private Optional<WithTimingZipInfoCollector> basis;
	private Path path;

	public WithTimingZipInfoCollector() {
		path = Paths.get(".");
		basis = Optional.empty();
		this.allZipInfos = Collections.synchronizedMap(new TreeMap<>((a, b) -> {
			int lenDiff = b.toString().length() - a.toString().length();
			return lenDiff != 0 ? lenDiff : a.compareTo(b);
		}));
	}

	public WithTimingZipInfoCollector(WithTimingZipInfoCollector basis, Path path) {
		this.basis = Optional.of(basis);
		this.path = path;
	}

	@Override
	public ZipInfo newZipFile(Path name) {
		return basis.map(b -> newZipFile(name)).orElseGet(() -> {
			ZipInfoWithTime zipInfo = new ZipInfoWithTime(path.resolve(name));
			this.allZipInfos.put(path, zipInfo);
			return zipInfo;
		});
	}

	@Override
	public byte[] dumpInfo() {
		return basis.map(ZipInfoCollector::dumpInfo).orElseGet(() -> {
			try {
				ByteArrayOutputStream bout = new ByteArrayOutputStream(64 * 1024 * 128);
				ObjectOutputStream oo = new ObjectOutputStream(bout);
				oo.writeObject(dataContainer);
				oo.writeObject(new ArrayList<>(allZipInfos.values()));
				oo.close();
				return bout.toByteArray();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Override
	public void linkDataContainer(ObjectId commitId) {
		basis.ifPresent(b -> b.linkDataContainer(commitId));
		dataContainer = ObjectId.toString(commitId);
	}

	@Override
	public ZipInfoCollector resolve(Path name) {
		if (name == null) {
			return this;
		}
		return new WithTimingZipInfoCollector(basis.orElse(this), path.resolve(name));
	}
}

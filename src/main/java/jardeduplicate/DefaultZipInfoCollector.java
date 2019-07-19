package jardeduplicate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import java.util.TreeMap;
import java.util.zip.ZipEntry;

import org.eclipse.jgit.lib.ObjectId;

public class DefaultZipInfoCollector implements ZipInfoCollector {

	private String dataContainer;
	private List<String[]> allZipPathes;
	private Optional<DefaultZipInfoCollector> basis;
	private Path path;

	public DefaultZipInfoCollector() {
		this.allZipPathes = Collections.synchronizedList(new ArrayList<>());
		this.path = Paths.get(".");
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
			return basis.map(b -> b.newZipFile(path.resolve(name))).orElse(null);
		} else {
			this.allZipPathes.add(StringDeduplicationHelper.splitPath(path.toString()));
			return null;
		}
	}

	@Override
	public byte[] dumpInfo() {
		return this.basis.map(ZipInfoCollector::dumpInfo).orElseGet(() -> {
			try {
				ByteArrayOutputStream bout = new ByteArrayOutputStream(64 * 1024 * 128);
				ObjectOutputStream oo = new ObjectOutputStream(bout);
				oo.writeObject(dataContainer);
				oo.writeObject(new ArrayList<>(allZipPathes));
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
		this.dataContainer = ObjectId.toString(commitId);
	}

	@Override
	public ZipInfoCollector resolve(Path name) {
		if (name == null) {
			return this;
		}
		return new DefaultZipInfoCollector(basis.orElse(this), this.path.resolve(name));
	}

}

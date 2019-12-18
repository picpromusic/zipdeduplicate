package jardeduplicate;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jgit.lib.ObjectId;

public class PrintingInfoCollector implements ZipInfoCollector {

	private Path path;

	public PrintingInfoCollector() {
		this.path = Paths.get(".");
	}

	protected PrintingInfoCollector(Path path) {
		this.path = path;
	}

	@Override
	public ZipInfo newZipFile(Path name) {
		System.out.println("[" + Thread.currentThread().getName() + "] " + path.resolve(name));
		return null;
	}

	@Override
	public byte[] dumpInfo() {
		throw new UnsupportedOperationException("JustPrinting");
	}

	@Override
	public void linkDataContainer(ObjectId commitId) {
		System.out.println("Linked to :" + commitId);
	}

	@Override
	public ZipInfoCollector resolve(Path path) {
		if (path == null) {
			return this;
		}
		return new PrintingInfoCollector(path.resolve(path));
	}

}

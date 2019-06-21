package jardeduplicate;

import java.nio.file.Path;
import java.util.zip.ZipEntry;

public interface ZipInfoCollector {
	void newZipFile(Path path);
	void newEntry(ZipEntry nextEntry);
	void endOfZipFile();
}

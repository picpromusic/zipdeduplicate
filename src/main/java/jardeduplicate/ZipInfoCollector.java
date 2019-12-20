package jardeduplicate;

import java.nio.file.Path;

import org.eclipse.jgit.lib.ObjectId;

public interface ZipInfoCollector {
	
	ZipInfoCollector resolve(Path path);
	
	ZipInfo newZipFile(Path name);

	byte[] dumpInfo();

	void linkDataContainer(ObjectId commitId);

	void prefix(String string);
}

package oss.zipdeduplicate;

import java.nio.file.Path;

import org.eclipse.jgit.lib.ObjectId;

public interface ZipInfoCollector {
	
	void newZipFile(Path name);

	byte[] dumpInfo();

	void linkDataContainer(ObjectId commitId);

	void prefix(String string);
}

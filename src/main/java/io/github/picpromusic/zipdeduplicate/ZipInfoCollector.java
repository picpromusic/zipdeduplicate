package io.github.picpromusic.zipdeduplicate;

import java.nio.file.Path;

import org.eclipse.jgit.lib.ObjectId;

public interface ZipInfoCollector {
	
	void newZipFile(Path name);

	byte[] dumpInfo();

//	void linkDataContainer(ObjectId treeId);

	void prefix(String string);
}

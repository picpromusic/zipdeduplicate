package io.github.picpromusic.zipdeduplicate;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;

public abstract class TwoLevelRestore extends AbstractRestore {

	public TwoLevelRestore(Git git, String branch, String additionalData) {
		super(git, branch, additionalData);
	}

	@Override
	protected ContainerOutputStream openStreams(Path path, String dest) {
		ByteArrayOutputStream bout = null;
		ContainerOutputStream zout;
		Path p = Paths.get(dest);
		if (p.getParent() == null) {
			zout = createOuterMostContainer(path, dest);
		} else {
			bout = new ByteArrayOutputStream();
			zout = createInnerContainer(bout);
		}
		return zout;
	}

	protected ZipContainerOutputStream createInnerContainer(ByteArrayOutputStream bout) {
		return new ZipContainerOutputStream(bout, isRestoreCompressed());
	}
	
	protected boolean isRestoreCompressed() {
		return true;
	}

	protected abstract ContainerOutputStream createOuterMostContainer(Path path, String dest);

}

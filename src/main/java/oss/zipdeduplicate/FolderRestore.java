package oss.zipdeduplicate;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;

public class FolderRestore extends TwoLevelRestore {

	public static void main(String[] args) throws IOException, ClassNotFoundException {
		Path workPath = Paths.get(args[0]);
		Git git;
		git = Git.open(workPath.toFile());
		Path path = Paths.get("./restore");
		new FolderRestore(git.getRepository(), args[1]).restoreTo(path);
	}

	@Override
	protected ContainerOutputStream createOuterMostContainer(Path path, String dest) {
		return new FolderContainerOutputStream(path.resolve(dest));
	}

	public FolderRestore(Repository repository, String branch) {
		super(repository, branch);
	}
	
}

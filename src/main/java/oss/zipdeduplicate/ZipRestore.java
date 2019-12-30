package oss.zipdeduplicate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;

public class ZipRestore extends TwoLevelRestore {

	public static void main(String[] args) throws IOException, ClassNotFoundException {
		Path workPath = Paths.get(args[0]);
		Git git;
		git = Git.open(workPath.toFile());

		Path path = Paths.get("./restore");

		new ZipRestore(git.getRepository(), args[1]).restoreTo(path);

	}

	public ZipRestore(Repository repository, String branch) {
		super(repository, branch);
	}

	@Override
	protected ContainerOutputStream createOuterMostContainer(Path path, String dest) {
		try {
		dest = dest.toLowerCase().endsWith(".zip") ? dest : dest + ".zip";
			return new ZipContainerOutputStream(Files.newOutputStream(path.resolve(dest)), false);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}

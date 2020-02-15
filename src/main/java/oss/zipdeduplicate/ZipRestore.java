package oss.zipdeduplicate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Repository;

public class ZipRestore extends TwoLevelRestore {

	public static void main(String[] args) throws IOException, ClassNotFoundException, InvalidRemoteException, TransportException, GitAPIException {
		Path workPath = Paths.get(args[0]);
		Git git;
		git = Git.open(workPath.toFile());

		Path path = Paths.get("./restore");

		new ZipRestore(git, args[1],args[2]).restoreTo(path);

	}

	public ZipRestore(Git git, String branch, String additionalData) {
		super(git, branch,additionalData);
	}

	@Override
	protected ContainerOutputStream createOuterMostContainer(Path path, String dest) {
		try {
			dest = dest.toLowerCase().endsWith(".zip") ? dest : dest + ".zip";
			Path zipPath = path.resolve(dest);
			reportCreatedPath(zipPath);
			return new ZipContainerOutputStream(Files.newOutputStream(zipPath), false);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}

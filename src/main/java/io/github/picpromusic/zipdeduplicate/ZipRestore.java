package io.github.picpromusic.zipdeduplicate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

public class ZipRestore extends TwoLevelRestore {

	public static void main(String[] args)
			throws IOException, ClassNotFoundException, InvalidRemoteException, TransportException, GitAPIException {
		List<String> arguments = new ArrayList(Arrays.asList(args));
//		while (arguments.get(0).startsWith("-")) {
//			String arg = arguments.remove(0);
//			if (arg.startsWith("-D")) {
//				String[] variable = arg.substring(2).split("=");
//				variables.put(variable[0], Arrays.stream(variable).skip(1).collect(Collectors.joining("=")));
//			}
//		}
		Path workPath = Paths.get(arguments.remove(0));
		Git git;
		git = Git.open(workPath.toFile());

		Path path = Paths.get("./restore/others");
		Files.createDirectories(path);
		String branch = arguments.remove(0);
		String additionalInfo = arguments.remove(0);
		new ZipRestore(git, new Branch(branch), additionalInfo).restoreTo(path);

	}

	public ZipRestore(Git git, Branch branch, String additionalData) {
		super(git, branch, additionalData);
	}

	@Override
	protected ContainerOutputStream createOuterMostContainer(Path path, String dest) {
		try {
			String lowerCase = dest.toLowerCase();
			boolean zip = lowerCase.endsWith(".zip") || lowerCase.endsWith(".ear") || lowerCase.endsWith(".war");
			dest = zip ? dest : dest + ".zip";
			Path zipPath = path.resolve(dest);
			reportCreatedPath(zipPath);
			return new ZipContainerOutputStream(Files.newOutputStream(zipPath), false);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected boolean isRestoreCompressed() {
		return false;
	}
}

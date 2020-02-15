package oss.zipdeduplicate;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Repository;

public class FolderRestore extends TwoLevelRestore {

	public static void main(String[] args) throws IOException, ClassNotFoundException, InvalidRemoteException, TransportException, GitAPIException {
		Path workPath = Paths.get(args[0]);
		Git git;
		git = Git.open(workPath.toFile());
		Path path = Paths.get("./restore");
		String branch = ZipDedupUtils.urlToBranchName(args[1]);
		FolderRestore folderRestore = new FolderRestore(git, branch,args[3]);
		if (args.length > 2) {
			folderRestore.withReplaceTopLevel(args[2]);
		}
		folderRestore.restoreTo(path);
	}

	private String replaceTopLevel;

	protected void withReplaceTopLevel(String replaceTopLevel) {
		this.replaceTopLevel=replaceTopLevel;
	}

	@Override
	protected ContainerOutputStream createOuterMostContainer(Path path, String dest) {
		Path destPath = path.resolve(dest);
		if (replaceTopLevel != null) {
			Path p = Paths.get(dest);
			Path base = p.getName(0);
			p = base.relativize(p);
			base = Paths.get(replaceTopLevel);
			destPath = path.resolve(base.resolve(p));
		}
		return new FolderContainerOutputStream(destPath,this::reportCreatedPath);
	}

	@Override
	protected boolean isRestoreCompressed() {
		return false;
	}
	
	public FolderRestore(Git git, String branch, String additionalData) {
		super(git, branch, additionalData);
	}
	
}

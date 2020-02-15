package oss.zipdeduplicate;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

public class FolderContainerOutputStream extends ContainerOutputStream{

	private Path destPath;
	private OutputStream entryOutputStream;
	private Consumer<Path> reportAllCreatedPathes;

	public FolderContainerOutputStream(Path folder,Consumer<Path> reportAllCreatedPathes) {
		this.reportAllCreatedPathes = reportAllCreatedPathes;
		String asString = folder.toString();
		if (asString.toLowerCase().endsWith(".zip")) {
			destPath = Paths.get(asString.substring(0,asString.length()-4));
		}else {
			destPath = folder;
		}
	}
	
	@Override
	public void putNextEntry(String path) throws IOException {
		Path destPathEntry = destPath.resolve(path);
		reportAllCreatedPathes.accept(destPathEntry);
		Files.createDirectories(destPathEntry.getParent());
		entryOutputStream = Files.newOutputStream(destPathEntry);
	}

	@Override
	public void closeEntry() throws IOException {
		entryOutputStream.close();
		entryOutputStream = null;
	}

	@Override
	public void write(int b) throws IOException {
		entryOutputStream.write(b);
	}
	
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		entryOutputStream.write(b, off, len);
	}

}

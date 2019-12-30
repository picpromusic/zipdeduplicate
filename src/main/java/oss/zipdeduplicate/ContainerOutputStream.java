package oss.zipdeduplicate;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;

public abstract class ContainerOutputStream extends OutputStream{

	public abstract void putNextEntry(String path) throws IOException;
	public abstract void closeEntry() throws IOException;
	
	public Optional<OutputStream> getWrittenTo() {
		return Optional.empty();
	}

}

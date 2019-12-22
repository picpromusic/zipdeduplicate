package jardeduplicate;

import java.io.IOException;
import java.io.OutputStream;

public abstract class ContainerOutputStream extends OutputStream{

	public abstract void putNextEntry(String path) throws IOException;
	public abstract void closeEntry() throws IOException;

}

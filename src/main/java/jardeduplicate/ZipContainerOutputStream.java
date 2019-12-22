package jardeduplicate;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipContainerOutputStream extends ContainerOutputStream {

	private ZipOutputStream wrapped;

	public ZipContainerOutputStream(OutputStream out, boolean compressed) {
		wrapped = new ZipOutputStream(out);
		if (!compressed) {
			wrapped.setMethod(ZipOutputStream.DEFLATED);
			wrapped.setLevel(0);
		}

	}

	@Override
	public void putNextEntry(String path) throws IOException {
		wrapped.putNextEntry(new ZipEntry(path));
	}

	@Override
	public void closeEntry() throws IOException {
		wrapped.closeEntry();

	}

	@Override
	public void write(int b) throws IOException {
		wrapped.write(b);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		wrapped.write(b, off, len);
	}

	@Override
	public void close() throws IOException {
		wrapped.close();
	}

}

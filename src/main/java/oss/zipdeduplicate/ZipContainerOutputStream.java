package oss.zipdeduplicate;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipContainerOutputStream extends ContainerOutputStream {

	private ZipOutputStream wrapped;
	private Optional<OutputStream> writtenTo;

	public ZipContainerOutputStream(OutputStream out, boolean compressed) {
		writtenTo = Optional.of(out);
		wrapped = new ZipOutputStream(out);
		if (!compressed) {
			wrapped.setMethod(ZipOutputStream.DEFLATED);
			wrapped.setLevel(0);
		}

	}
	
	@Override
	public Optional<OutputStream> getWrittenTo() {
		return writtenTo;
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

package oss.zipdeduplicate;

import java.nio.file.Path;

@FunctionalInterface
public interface RestoreFunction {
	void restoreTo(Path path);
}

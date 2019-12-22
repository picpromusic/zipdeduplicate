package jardeduplicate;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jgit.lib.Repository;

import io.vavr.Tuple;
import io.vavr.Tuple2;

public abstract class TwoLevelRestore extends AbstractRestore{

	public TwoLevelRestore(Repository repository, String branch) {
		super(repository, branch);
	}

	@Override
	protected Tuple2<ContainerOutputStream, ByteArrayOutputStream> openStreams(Path path, String dest) {
		ByteArrayOutputStream bout = null;
		ContainerOutputStream zout;
		Path p = Paths.get(dest);
		if (p.getParent() == null) {
			zout = createOuterMostContainer(path, dest);
		} else {
			bout = new ByteArrayOutputStream();
			zout = createInnerContainer(bout);
		}
		return Tuple.of(zout, bout);
	}

	protected ZipContainerOutputStream createInnerContainer(ByteArrayOutputStream bout) {
		return new ZipContainerOutputStream(bout,true);
	}
	
	protected abstract ContainerOutputStream createOuterMostContainer(Path path, String dest);


}

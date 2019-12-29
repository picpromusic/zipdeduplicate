package jardeduplicate;

import java.io.IOException;
import java.util.TreeMap;
import java.util.function.Supplier;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.TreeFormatter;

import io.vavr.Tuple;
import io.vavr.Tuple2;

public class SortingTreeFormatter extends AnyObjectId {

	private TreeMap<String, Tuple2<FileMode, ObjectId>> data;
	private Supplier<ObjectInserter> oiSupplier;

	public SortingTreeFormatter(Supplier<ObjectInserter> oiSupplier) {
		this.data = new TreeMap<>(this::gitNameSorting);
		this.oiSupplier = oiSupplier;
	}

	public void append(String name, FileMode fm, ObjectId id) {
		this.data.put(name, Tuple.of(fm, id));
	}

	public boolean contains(String name) {
		return this.data.containsKey(name);
	}

	private int gitNameSorting(String o1, String o2) {
		return o1.compareTo(o2);
//		int minLength = Math.min(o1.length(), o2.length());
//		int comp = o1.substring(0, minLength).compareTo(o2.substring(0, minLength));
//		return comp != 0 ? comp : o2.length() - o1.length();
	}

	@Override
	public ObjectId toObjectId() {
		try {
			TreeFormatter treeFormatter = new TreeFormatter(this.data.size());
			data.entrySet().forEach(e -> {
				treeFormatter.append(e.getKey(), e.getValue()._1(), e.getValue()._2());
			});
			return oiSupplier.get().insert(treeFormatter);
		} catch (IOException e1) {
			throw new RuntimeException(e1);
		}
	}
}

package oss.zipdeduplicate;

import java.io.IOException;
import java.util.TreeMap;
import java.util.function.Supplier;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.TreeFormatter;

public class SortingTreeFormatter extends AnyObjectId {

	private TreeMap<String, FileModeAndObjectId> data;
	private Supplier<ObjectInserter> oiSupplier;

	public SortingTreeFormatter(Supplier<ObjectInserter> oiSupplier) {
		this.data = new TreeMap<>(this::gitNameSorting);
		this.oiSupplier = oiSupplier;
	}

	public void append(String name, FileMode fm, ObjectId id) {
		this.data.put(name, new FileModeAndObjectId(fm, id));
	}

	public boolean contains(String name) {
		return this.data.containsKey(name);
	}

	private int gitNameSorting(String o1, String o2) {
		return o1.compareTo(o2);
	}

	@Override
	public ObjectId toObjectId() {
		try {
			TreeFormatter treeFormatter = new TreeFormatter(this.data.size());
			data.entrySet().forEach(e -> {
				treeFormatter.append(e.getKey(), e.getValue().fm, e.getValue().id);
			});
			return oiSupplier.get().insert(treeFormatter);
		} catch (IOException e1) {
			throw new RuntimeException(e1);
		}
	}

	public static class FileModeAndObjectId {
		private final FileMode fm;
		private final ObjectId id;

		public FileModeAndObjectId(FileMode fm, ObjectId id) {
			this.fm = fm;
			this.id = id;
		}
	}
}

package io.github.picpromusic.zipdeduplicate;

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
	protected Supplier<ObjectInserter> oiSupplier;

	public SortingTreeFormatter(Supplier<ObjectInserter> oiSupplier) {
		this.data = new TreeMap<>(this::gitNameSorting);
		this.oiSupplier = oiSupplier;
	}

	public void append(String name, FileMode fm, ObjectId id) {
		if (fm == FileMode.TREE && !name.endsWith("/")) {
			name = name + "/";
		}
		this.data.put(name, new FileModeAndObjectId(fm, id));
	}
	
	public void remove(String name,FileMode fm) {
		if (fm == FileMode.TREE && !name.endsWith("/")) {
			name = name + "/";
		}
		data.remove(name);
	}

	private int gitNameSorting(String o1, String o2) {
		int len1 = o1.length();
		int len2 = o2.length();
		int minLen = Math.min(len1, len2);
		int cmp = o1.substring(0, minLen).compareTo(o2.substring(0, minLen));
		if (cmp != 0)
			return cmp;
		if (len1 < len2)
	        return -1;
	    if (len1 > len2)
	        return 1;
		return 0;
	}

	@Override
	public ObjectId toObjectId() {
		try {
			TreeFormatter treeFormatter = new TreeFormatter(this.data.size());
			data.entrySet().forEach(e -> {
				FileMode fm = e.getValue().fm;
				String name = e.getKey();
				if (fm == fm.TREE && name.endsWith("/")) {
					name = name.substring(0,name.length()-1);
				}
				treeFormatter.append(name, fm, e.getValue().id);
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

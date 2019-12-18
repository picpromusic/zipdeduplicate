package jardeduplicate;

import java.io.IOException;
import java.util.TreeMap;

import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.TreeFormatter;

import io.vavr.Tuple;
import io.vavr.Tuple2;

public class SortingTreeFormatter {

	private TreeMap<String, Tuple2<FileMode, ObjectId>> data;

	public SortingTreeFormatter() {
		this.data = new TreeMap<>(this::gitNameSorting);
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

	public ObjectId insert(ObjectInserter oi) throws IOException {
		TreeFormatter treeFormatter = new TreeFormatter(this.data.size());
		data.entrySet().forEach(e -> {
			treeFormatter.append(e.getKey(), e.getValue()._1(), e.getValue()._2());
		});
		return oi.insert(treeFormatter);
	}
}

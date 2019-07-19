package jardeduplicate;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.zip.ZipEntry;

public class ZipInfoWithTime implements ZipInfo, Serializable {

	private transient Map<String, Info> mappedByName = new TreeMap();
	private List<Info> infos = new LinkedList<>();
	private String[] nameParts;

	public static class Info implements Serializable {
		private long time;
		private String comment;
		private String[] name;

		private Info(ZipEntry ze) {
			time = ze.getTime();
			comment = Optional.ofNullable(ze.getComment()).map(String::intern).orElse(null);
			StringDeduplicationHelper.splitPath(ze.getName());
		}
	}

	public ZipInfoWithTime(Path name) {
		this.nameParts = StringDeduplicationHelper.splitPath(name.toString());
	}

	@Override
	public void newEntry(ZipEntry nextEntry) {
		Info info = new Info(nextEntry);
		this.infos.add(info);
		this.mappedByName.put(nextEntry.getName(), info);
	}

}

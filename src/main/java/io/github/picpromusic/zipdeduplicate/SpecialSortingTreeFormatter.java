package io.github.picpromusic.zipdeduplicate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.function.Supplier;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.TreeFormatter;

import io.github.picpromusic.zipdeduplicate.SortingTreeFormatter.FileModeAndObjectId;

public class SpecialSortingTreeFormatter extends SortingTreeFormatter {

	private TreeMap<String, ObjectId> blobs;

	public SpecialSortingTreeFormatter(Supplier<ObjectInserter> oiSupplier) {
		super(oiSupplier);
	}

	@Override
	public void append(String name, FileMode fm, ObjectId id) {
		if (fm == FileMode.REGULAR_FILE) {
			blobs.put(name, id);
		} else {
			super.append("t" + name, fm, id);
		}
	}

	@Override
	public void remove(String name, FileMode fm) {
		if (fm == FileMode.REGULAR_FILE) {
			blobs.remove(name);
		} else {
			super.remove("t" + name, fm);
		}
	}

	@Override
	public ObjectId toObjectId() {
		ByteArrayOutputStream bout;
		ObjectOutputStream oos;
		ObjectId objectId;
		try {
			bout = new ByteArrayOutputStream();
			oos = new ObjectOutputStream(bout);
			for (Entry<String, ObjectId> entry : blobs.entrySet()) {
				oos.writeUTF(entry.getKey());
				entry.getValue().copyRawTo(oos);
			}
			oos.close();
			ObjectId filesId = oiSupplier.get().insert(Constants.OBJ_BLOB, bout.toByteArray());
			super.append("files", FileMode.REGULAR_FILE, filesId);
			objectId = super.toObjectId();
			super.remove("files", FileMode.REGULAR_FILE);
			return objectId;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return super.toObjectId();
	}

	public void setDesciption(ZipInfoCollector zipInfoCollector) throws IOException {
		ObjectId descriptionId = oiSupplier.get().insert(Constants.OBJ_BLOB, zipInfoCollector.dumpInfo());
		super.append("description", FileMode.REGULAR_FILE, descriptionId);
	}

}

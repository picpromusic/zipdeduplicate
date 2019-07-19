package jardeduplicate;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class StringDeduplicationHelper {

	private static char pathSeperator = File.separatorChar;
	private static char pathSeperatorToUse = '/';
	private static String pathSeperatorPattern = pathSeperator == '/' ? "/" : "\\\\";

	public static String[] splitPath(String path) {
		String[] temp = path.split(pathSeperatorPattern);
		List<String> tempList = new ArrayList<String>(temp.length);
		String pathSeperatorToAppend = Character.toString(pathSeperatorToUse); 
		for (int i = 0; i < temp.length; i++) {
			boolean lastElement = i == temp.length-1;
			if (lastElement) {
				pathSeperatorToAppend = "";
			}
			if (temp[i].length() > 10 && temp[i].contains(".")) {
				String[] split = temp[i].split("\\.");
				for (int j = 0; j < split.length - 1; j++) {
					tempList.add((split[j] + ".").intern());
				}
				if (split.length > 1) {
					tempList.add((split[split.length - 1] + pathSeperatorToAppend).intern());
				} else {
					tempList.add((temp[i] + pathSeperatorToAppend).intern());
				}
			}else {
				tempList.add(temp[i] + pathSeperatorToAppend);
			}
		}
		return tempList.toArray(new String[0]);
	}
}

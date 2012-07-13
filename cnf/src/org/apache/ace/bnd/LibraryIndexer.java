package org.apache.ace.bnd;

import java.io.File;
import java.io.FileWriter;

public class LibraryIndexer {
	public static void main(String[] args) throws Exception {
		File dir = new File("lib");
		StringBuffer repo = new StringBuffer();
		repo.append("<repository>\n");
		index(repo, dir, dir);
		repo.append("</repository>\n");
		FileWriter fw = null;
		try {
			fw = new FileWriter(new File(dir, "repository.xml"));
			fw.write(repo.toString());
		}
		finally {
			if (fw != null) {
				fw.close();
			}
		}
	}

	private static void index(StringBuffer repo, File baseDir, File dir) {
		int baseDirLength = baseDir.getAbsolutePath().length() + 1;
		for (File f : dir.listFiles()) {
			if (f.isDirectory()) {
				index(repo, baseDir, f);
			}
			else if (f.isFile() && f.getName().endsWith(".jar")) {
				String name = f.getName();
				int i = name.lastIndexOf('-');
				if (i != -1) {
					String bsn = name.substring(0, i);
					String suffix = name.substring(i + 1);
					String version;
					if (suffix.endsWith(".jar")) {
						version = suffix.substring(0, suffix.length() - 4);
					}
					else {
						version = suffix;
					}
					String uri = f.getAbsolutePath().substring(baseDirLength);
					repo.append(" <resource id='" + bsn + "/" + version + "' symbolicname='" + bsn + "' uri='" + uri + "' version='" + version + "'>" +
						"<capability name='bundle'>" +
						"<p n='manifestversion' v='2'/>" +
						"<p n='symbolicname' v='" + bsn + "'/>" +
						"<p n='version' t='version' v='" + version + "'/>" +
						"</capability>" +
						"</resource>\n"
					);
				}
			}
		}
	}
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ace.bnd;

import java.io.File;
import java.io.FileWriter;

public class LibraryIndexer {
	public static void main(String[] args) throws Exception {
		File dir = new File("lib");
		System.out.printf("Creating 'repository.xml' in %s ... ", dir.getName());
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
		System.out.println("done.");
	}

	private static void index(StringBuffer repo, File baseDir, File dir) {
		int baseDirLength = baseDir.getAbsolutePath().length() + 1;
		
		File[] files = dir.listFiles();
		if (files == null) {
			throw new RuntimeException("Failed to list files for " + dir);
		}

		for (File f : files) {
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

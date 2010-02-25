/*
 * $Id: Index.java 44 2007-07-13 20:49:41Z hargrave@us.ibm.com $
 *
 * Copyright (c) OSGi Alliance (2002, 2006, 2007). All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.osgi.impl.bundle.bindex;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.osgi.impl.bundle.obr.resource.BundleInfo;
import org.osgi.impl.bundle.obr.resource.RepositoryImpl;
import org.osgi.impl.bundle.obr.resource.ResourceImpl;
import org.osgi.impl.bundle.obr.resource.Tag;

/**
 * Iterate over a set of given bundles and convert them to resources. When -a is
 * specified, other resources than bundles will be included too. After
 * this, convert an local urls (file systems, JAR file) to relative URLs and
 * create a ZIP file with the complete content. This ZIP file can be used in an
 * OSGi Framework to map to an http service or it can be expanded on the web
 * server's file system.
 *
 * @version $Revision: 44 $
 */
public class Index {
	static String			repositoryFileName	= "repository.xml";
	static URL				licenseURL			= null;
	static boolean			quiet				= false;
	static String			name				= "Untitled";
    static boolean          all                 = false;
	static String			urlTemplate			= null;
	static File				rootFile			= new File("")
														.getAbsoluteFile();
	static RepositoryImpl	repository;
	static String			root;

	/**
	 * Main entry. See -help for options.
	 *
	 * @param args
	 * @throws Exception
	 */
	public static void main(String args[]) throws Exception {
		System.err.println("Bundle Indexer | v2.2");
		System.err.println("(c) 2007 OSGi, All Rights Reserved");

		Set resources = new HashSet();
		root = rootFile.toURL().toString();
		repository = new RepositoryImpl(rootFile.toURL());

		for (int i = 0; i < args.length; i++) {
            try {
				if (args[i].startsWith("-n")) {
                    name = args[++i];
                }
                else
					if (args[i].startsWith("-r")) {
						repositoryFileName = args[++i];
						repository = new RepositoryImpl(new File(
								repositoryFileName).getAbsoluteFile().toURL());
					}
					else
						if (args[i].startsWith("-q")) {
                            quiet = true;
                        }
                        else
							if (args[i].startsWith("-t")) {
                                urlTemplate = args[++i];
                            }
                            else
								if (args[i].startsWith("-l")) {
									licenseURL = new URL(new File("").toURL(),
											args[++i]);
								}
								else
									if (args[i].startsWith("-help")) {
										System.err
												.println("bindex [-t \"%s\" symbolic name \"%v\" version \"%f\" filename \"%p\" dirpath ] [ -r repository.(xml|zip) ] [-help] [-l file:license.html ] [-quiet] [-all] <jar file>*");
									}
									else
									    if (args[i].startsWith("-a")) {
                                            all = true;
                                        }
                                        else {
	    									recurse(resources, new File(args[i]));
		    							}
			}
			catch (Exception e) {
				System.err.println("Error in " + args[i] + " : " +
						e.getMessage());
				e.printStackTrace();
			}
        }

		List sorted = new ArrayList(resources);
		Collections.sort(sorted, new Comparator() {
			public int compare(Object r1, Object r2) {
				String s1 = getName((ResourceImpl) r1);
				String s2 = getName((ResourceImpl) r2);
				return s1.compareTo(s2);
			}
		});

		Tag tag = doIndex(sorted);
		if (repositoryFileName != null) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			PrintWriter pw = new PrintWriter(new OutputStreamWriter(out,
					"UTF-8"));

			pw.println("<?xml version='1.0' encoding='utf-8'?>");
			pw
					.println("<?xml-stylesheet type='text/xsl' href='http://www2.osgi.org/www/obr2html.xsl'?>");

			tag.print(0, pw);
			pw.close();
			byte buffer[] = out.toByteArray();
			String name = "repository.xml";
			FileOutputStream fout = new FileOutputStream(repositoryFileName);

			if (repositoryFileName.endsWith(".zip")) {
				ZipOutputStream zip = new ZipOutputStream(fout);
				CRC32 checksum = new CRC32();
				checksum.update(buffer);
				ZipEntry ze = new ZipEntry(name);
				ze.setSize(buffer.length);
				ze.setCrc(checksum.getValue());
				zip.putNextEntry(ze);
				zip.write(buffer, 0, buffer.length);
				zip.closeEntry();
				zip.close();
			}
			else {
				fout.write(buffer);
			}
			fout.close();
		}

		if (!quiet) {
			PrintWriter pw = new PrintWriter(new OutputStreamWriter(System.out));
			pw.println("<?xml version='1.0' encoding='utf-8'?>");
			pw
					.println("<?xml-stylesheet type='text/xsl' href='http://www2.osgi.org/www/obr2html.xsl'?>");
			tag.print(0, pw);
			pw.close();
		}
	}

	static String getName(ResourceImpl impl) {
		String s = impl.getSymbolicName();
		if (s != null) {
            return s;
        }
        else {
			return "no-symbolic-name";
		}
	}

	static void recurse(Set resources, File path) throws Exception {
		if (path.isDirectory()) {
			String list[] = path.list();
			for (int i = 0; i < list.length; i++) {
				recurse(resources, new File(path, list[i]));
			}
		}
		else {
		    if (path.getName().equals("repository.xml") || path.getName().equals(new File(repositoryFileName).getName())) {
		        // do not index our repository.xml, nor the file we are working on now.
		        return;
		    }
			if (path.getName().endsWith(".jar")) {
				BundleInfo info = new BundleInfo(repository, path);
				ResourceImpl resource = info.build();
				if (urlTemplate != null) {
					doTemplate(path, resource);
				}
                else {
                    resource.setURL(path.toURL());
                }

				resources.add(resource);
			}
			else {
			    // this is some other resource, we might want to include it.
			    if (all) {
			        resources.add(new ResourceImpl(repository, path.toURL()));
			    }
			}
		}
	}

	static void doTemplate(File path, ResourceImpl resource)
			throws MalformedURLException {
		String dir = path.getAbsoluteFile().getParentFile().getAbsoluteFile()
				.toURL().toString();
		if (dir.endsWith("/")) {
            dir = dir.substring(0, dir.length() - 1);
        }

		if (dir.startsWith(root)) {
            dir = dir.substring(root.length());
        }

		String url = urlTemplate.replaceAll("%v", "" + resource.getVersion());
		url = url.replaceAll("%s", resource.getSymbolicName());
		url = url.replaceAll("%f", path.getName());
		url = url.replaceAll("%p", dir);
		resource.setURL(new URL(url));
	}

	/**
	 * Create the repository index
	 *
	 * @param resources Set of resources
	 * @param collected The output zip file
	 * @throws IOException
	 */
	static Tag doIndex(Collection resources) throws IOException {
		Tag repository = new Tag("repository");
		repository.addAttribute("lastmodified", new Date());
		repository.addAttribute("name", name);

		for (Iterator i = resources.iterator(); i.hasNext();) {
			ResourceImpl resource = (ResourceImpl) i.next();
			repository.addContent(resource.toXML());
		}
		return repository;
	}

	/**
	 * Add the resource to the ZIP file, calculating the CRC etc.
	 *
	 * @param zip The output ZIP file
	 * @param name The name of the resource
	 * @param actual The contents stream
	 * @throws IOException
	 */
	static void addToZip(ZipOutputStream zip, String name, InputStream actual)
			throws IOException {
		byte buffer[];
		buffer = readAll(actual, 0);
		actual.close();
		CRC32 checksum = new CRC32();
		checksum.update(buffer);
		ZipEntry ze = new ZipEntry(name);
		ze.setSize(buffer.length);
		ze.setCrc(checksum.getValue());
		zip.putNextEntry(ze);
		zip.write(buffer, 0, buffer.length);
		zip.closeEntry();
	}

	/**
	 * Read a complete stream till EOF. This method will parse the input stream
	 * until a -1 is discovered.
	 *
	 * The method is recursive. It keeps on calling a higher level routine until
	 * EOF. Only then is the result buffer calculated.
	 */
	static byte[] readAll(InputStream in, int offset) throws IOException {
		byte temp[] = new byte[4096];
		byte result[];
		int size = in.read(temp, 0, temp.length);
		if (size <= 0) {
            return new byte[offset];
        }
		//
		// We have a positive result, copy it
		// to the right offset.
		//
		result = readAll(in, offset + size);
		System.arraycopy(temp, 0, result, offset, size);
		return result;
	}

}

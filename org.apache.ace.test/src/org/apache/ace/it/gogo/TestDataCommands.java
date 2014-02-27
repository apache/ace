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
package org.apache.ace.it.gogo;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.felix.service.command.Descriptor;
import org.osgi.framework.Constants;

/**
 * Provides GoGo shell commands to generate artifacts for testing purposes.
 */
public class TestDataCommands {

    public final static String SCOPE = "testdata";
    public final static String[] FUNCTIONS = new String[] { "gba", "gca" };

    @Descriptor("Generates a bundle artifact with version 1.0.0")
    public static String gba(@Descriptor("The bundle (symbolic) name") String bsn) throws Exception {
        return gba(bsn, bsn, "1.0.0");
    }

    @Descriptor("Generates a bundle artifact with a given version")
    public static String gba(@Descriptor("The bundle (symbolic) name") String bsn, @Descriptor("The bundle version") String version) throws Exception {
        return gba(bsn, bsn, version);
    }

    @Descriptor("Generates a bundle artifact with a given name and version")
    public static String gba(@Descriptor("The bundle name") String name, @Descriptor("The bundle symbolic name") String bsn, @Descriptor("The bundle version") String version) throws Exception {
        URL url = generateBundle(name, bsn, version);
        return url.toExternalForm();
    }

    @Descriptor("Generates a metatype configuration artifact")
    public static String gca(@Descriptor("The (placeholder) property names") String[] propertyNames) throws Exception {
        return gca(null, propertyNames);
    }

    @Descriptor("Generates a metatype configuration artifact")
    public static String gca(@Descriptor("The artifact name to use") String name, @Descriptor("The (placeholder) property names") String[] propertyNames) throws Exception {
        URL url = generateMetaTypeArtifact(name, propertyNames);
        return url.toExternalForm();
    }

    public static URL generateBundle(String name, String bsn, String version) throws IOException {
        return generateBundle(name, bsn, version, Collections.<String, String> emptyMap());
    }

    public static URL generateBundle(String name, String bsn, String version, Map<String, String> additionalHeaders) throws IOException {
        File dataFile = File.createTempFile("bundle", ".jar");
        dataFile.deleteOnExit();

        OutputStream bundleStream = null;
        try {
            OutputStream fileStream = new FileOutputStream(dataFile);
            bundleStream = new JarOutputStream(fileStream, getBundleManifest(name, bsn, version, additionalHeaders));
            bundleStream.flush();
            return dataFile.toURI().toURL();
        }
        finally {
            closeSilently(bundleStream);
        }
    }

    public static URL generateMetaTypeArtifact(String name, String[] propertyNames) throws Exception {
        if (propertyNames == null || propertyNames.length < 1) {
            throw new IllegalArgumentException("Need at least one property name!");
        }

        File dataFile;
        if (name == null) {
            dataFile = File.createTempFile("config", ".xml");
        }
        else {
            if (!name.endsWith(".xml")) {
                name = name.concat(".xml");
            }
            dataFile = new File(System.getProperty("java.io.tmpdir"), name);
        }
        dataFile.deleteOnExit();

        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        XMLStreamWriter writer = null;
        FileOutputStream fos = null;

        try {
            fos = new FileOutputStream(dataFile);
            writer = factory.createXMLStreamWriter(fos);

            String ns = "http://www.osgi.org/xmlns/metatype/v1.0.0";

            writer.writeStartDocument();
            writer.writeStartElement("MetaData");
            writer.writeAttribute("xmlns", ns);
            // OCD
            writer.writeStartElement("OCD");
            writer.writeAttribute("name", "ocd");
            writer.writeAttribute("id", "ocd");
            for (String propertyName : propertyNames) {
                writer.writeStartElement("AD");
                writer.writeAttribute("id", propertyName);
                writer.writeAttribute("type", "STRING");
                writer.writeAttribute("cardinality", "0");
                writer.writeEndElement();
            }
            writer.writeEndElement();
            // Designate
            writer.writeStartElement("Designate");
            writer.writeAttribute("pid", "pid" + System.nanoTime());
            writer.writeAttribute("bundle", "*");
            writer.writeStartElement("Object");
            writer.writeAttribute("ocdref", "ocd");
            for (String propertyName : propertyNames) {
                writer.writeStartElement("Attribute");
                writer.writeAttribute("adref", propertyName);
                writer.writeCData("${context." + propertyName + "}");
                writer.writeEndElement();
            }
            writer.writeEndElement();
            writer.writeEndElement();
            writer.writeEndDocument();

            return dataFile.toURI().toURL();
        }
        finally {
            closeSilently(writer);
            closeSilently(fos);
        }
    }

    private static void closeSilently(Closeable resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        }
        catch (IOException exception) {
            // Ignore...
        }
    }

    private static void closeSilently(XMLStreamWriter resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        }
        catch (XMLStreamException exception) {
            // Ignore...
        }
    }

    private static Manifest getBundleManifest(String name, String bsn, String version, Map<String, String> additionalHeaders) {
        Manifest manifest = new Manifest();

        Attributes mainAttrs = manifest.getMainAttributes();
        mainAttrs.putValue("Manifest-Version", "1");
        mainAttrs.putValue(Constants.BUNDLE_MANIFESTVERSION, "2");
        mainAttrs.putValue(Constants.BUNDLE_NAME, name);
        mainAttrs.putValue(Constants.BUNDLE_SYMBOLICNAME, bsn);
        mainAttrs.putValue(Constants.BUNDLE_VERSION, version.toString());
        for (Map.Entry<String, String> entry : additionalHeaders.entrySet()) {
            mainAttrs.putValue(entry.getKey(), entry.getValue());
        }
        return manifest;
    }
}

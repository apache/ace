package org.apache.ace.deployment.verifier.impl;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import junit.framework.AssertionFailedError;

import org.apache.ace.deployment.verifier.VerifierService;
import org.apache.ace.deployment.verifier.VerifierService.VerifyEnvironment;
import org.apache.ace.deployment.verifier.VerifierService.VerifyReporter;
import org.apache.felix.framework.util.FelixConstants;
import org.junit.Test;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.service.log.LogEntry;

public class VerifierTest {
	@Test
	public void testResolve() throws BundleException {
		VerifierService verifier = new VerifierServiceImpl();
		VerifyEnvironment env = verifier.createEnvironment(new HashMap<String, String>() {
			{
				put(Constants.FRAMEWORK_EXECUTIONENVIRONMENT, VerifierService.EE_1_6);
				put(Constants.FRAMEWORK_OS_NAME, "macosx");
				put(Constants.FRAMEWORK_OS_VERSION, "10.5");
			}
		}, new VerifyReporter() {
			
			public void reportWire(BundleRevision importer,
					BundleRequirement reqirement, BundleRevision exporter,
					BundleCapability capability) {
				System.out.println("WIRE: " + importer + " - " + reqirement + " - " + capability + " -> " + exporter);
			}
			
			public void reportLog(LogEntry logEntry) {
				System.out.println("Log(" + logEntry.getTime() + "): " + logEntry.getLevel() + " " + logEntry.getMessage());
				if (logEntry.getException() != null) {
					logEntry.getException().printStackTrace();
				}
			}
			
			public void reportException(Exception ex) {
				ex.printStackTrace();
			}
		});
		Set<BundleRevision> bundles = new HashSet<BundleRevision>();
		bundles.add(env.addBundle(0, new HashMap<String, String>(){
			{
				put(Constants.BUNDLE_MANIFESTVERSION, "2");
				put(Constants.BUNDLE_SYMBOLICNAME, FelixConstants.SYSTEM_BUNDLE_SYMBOLICNAME);
				put(Constants.EXPORT_PACKAGE, VerifierService.SYSTEM_PACKAGES + "," + VerifierService.JRE_1_6_PACKAGES);
			}
		}));
		bundles.add(env.addBundle(1, new HashMap<String, String>() {
			{
				put(Constants.BUNDLE_MANIFESTVERSION, "2");
				put(Constants.BUNDLE_SYMBOLICNAME, "org.test.foo");
				put(Constants.IMPORT_PACKAGE, "org.foo, org.osgi.framework");
			}
		}));
		bundles.add(env.addBundle(2, new HashMap<String, String>() {
			{
				put(Constants.BUNDLE_MANIFESTVERSION, "2");
				put(Constants.BUNDLE_SYMBOLICNAME, "org.test.foo2");
				put(Constants.EXPORT_PACKAGE, "org.foo" +
						"");
			}
		}));
		assertTrue(" Unable to resolve resolvable state.", env.verifyResolve(bundles, null, null));
	}
	
	@Test
	public void testResolveFail() throws BundleException {
		VerifierService verifier = new VerifierServiceImpl();
		VerifyEnvironment env = verifier.createEnvironment(new HashMap<String, String>(){
			{
				put(Constants.FRAMEWORK_EXECUTIONENVIRONMENT, VerifierService.EE_1_6);
				put(Constants.FRAMEWORK_OS_NAME, "macosx");
				put(Constants.FRAMEWORK_OS_VERSION, "10.5");
			}
		}, new VerifyReporter() {
			
			public void reportWire(BundleRevision importer,
					BundleRequirement reqirement, BundleRevision exporter,
					BundleCapability capability) {
				System.out.println("WIRE: " + importer + " - " + reqirement + " - " + capability + " -> " + exporter);
			}
			
			public void reportLog(LogEntry logEntry) {
				System.out.println("Log(" + logEntry.getTime() + "): " + logEntry.getLevel() + " " + logEntry.getMessage());
				if (logEntry.getException() != null) {
					logEntry.getException().printStackTrace();
				}
			}
			
			public void reportException(Exception ex) {
				ex.printStackTrace();
			}
		});
		Set<BundleRevision> bundles = new HashSet<BundleRevision>();
		bundles.add(env.addBundle(0, new HashMap<String, String>(){
			{
				put(Constants.BUNDLE_MANIFESTVERSION, "2");
				put(Constants.BUNDLE_SYMBOLICNAME, FelixConstants.SYSTEM_BUNDLE_SYMBOLICNAME);
				put(Constants.EXPORT_PACKAGE, VerifierService.SYSTEM_PACKAGES + "," + VerifierService.JRE_1_6_PACKAGES);
			}
		}));
		bundles.add(env.addBundle(1, new HashMap<String, String>() {
			{
				put(Constants.BUNDLE_MANIFESTVERSION, "2");
				put(Constants.BUNDLE_SYMBOLICNAME, "org.test.foo");
				put(Constants.IMPORT_PACKAGE, "org.foo");
			}
		}));
		bundles.add(env.addBundle(2, new HashMap<String, String>() {
			{
				put(Constants.BUNDLE_MANIFESTVERSION, "2");
				put(Constants.BUNDLE_SYMBOLICNAME, "org.test.foo2");
				put(Constants.EXPORT_PACKAGE, "org.foo2" +
						"");
			}
		}));
		assertFalse("Resolving unresolvable", env.verifyResolve(bundles, null, null));
	}
}

package org.example.tests;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.amdatu.mongo.MongoDBService;
import org.apache.ace.it.IntegrationTestBase;
import org.apache.ace.log.LogDescriptor;
import org.apache.ace.log.LogEvent;
import org.apache.ace.server.log.store.LogStore;
import org.apache.felix.dm.Component;
import org.osgi.service.log.LogService;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.MongoException;

public class MongoLogStoreTest extends IntegrationTestBase {
	private volatile LogStore m_logStore;
	private volatile MongoDBService m_mongodbService;

	@Override
	protected void before() throws Exception {
		configureFactory("org.amdatu.mongo", "dbName", "ace");
		configureFactory("org.apache.ace.server.log.store.factory", "name", "serverlog");
		super.before();
	}

	protected org.apache.felix.dm.Component[] getDependencies() {
		return new Component[] { createComponent().setImplementation(this)
				.add(createServiceDependency().setService(LogStore.class).setRequired(true))
				.add(createServiceDependency().setService(MongoDBService.class).setRequired(true)) };
	}

	public void testPutEvents() throws Exception {

		try {
			DBCollection collection = m_mongodbService.getDB().getCollection("serverlog");
			collection.remove(new BasicDBObject());
			TimeUnit.SECONDS.sleep(2);

			storeEvents();
			collection = m_mongodbService.getDB().getCollection("serverlog");
			assertEquals(5, collection.count());
		} catch (MongoException ex) {
			System.err.println("Mongodb not available on localhost, skipping test");
		}
	}

	public void testGetEvents() throws Exception {
		try {
			DBCollection collection = m_mongodbService.getDB().getCollection("serverlog");
			collection.remove(new BasicDBObject());
			TimeUnit.SECONDS.sleep(2);

			storeEvents();

			List<LogEvent> events = m_logStore.get(new LogDescriptor("mytarget1,1,0"));
			assertEquals(3, events.size());
		} catch (MongoException ex) {
			System.err.println("Mongodb not available on localhost, skipping test");
		}
	}

	public void testGetEventsWithRange() throws Exception {
		try {
			DBCollection collection = m_mongodbService.getDB().getCollection("serverlog");
			collection.remove(new BasicDBObject());
			TimeUnit.SECONDS.sleep(2);

			storeEvents();

			List<LogEvent> events = m_logStore.get(new LogDescriptor("mytarget1,1,2"));
			assertEquals(2, events.size());
		} catch (Exception ex) {
			System.err.println("Mongodb not available on localhost, skipping test");
		}
	}

	public void testGetDescriptorsSingleLogId() throws Exception {
		try {
			DBCollection collection = m_mongodbService.getDB().getCollection("serverlog");
			collection.remove(new BasicDBObject());
			TimeUnit.SECONDS.sleep(2);

			storeEvents();

			List<LogDescriptor> descriptors = m_logStore.getDescriptors();
			assertEquals(2, descriptors.size());
			assertEquals("mytarget1", descriptors.get(0).getTargetID());
			assertEquals(1, descriptors.get(0).getLogID());
			assertEquals(4, descriptors.get(0).getRangeSet().getHigh());
			assertEquals("mytarget2", descriptors.get(1).getTargetID());
			assertEquals(1, descriptors.get(1).getLogID());
			assertEquals(5, descriptors.get(1).getRangeSet().getHigh());
		} catch (MongoException ex) {
			System.err.println("Mongodb not available on localhost, skipping test");
		}

	}

	public void testGetDescriptorsMultipleLogIds() throws Exception {
		try {
			DBCollection collection = m_mongodbService.getDB().getCollection("serverlog");
			collection.remove(new BasicDBObject());
			TimeUnit.SECONDS.sleep(2);

			storeEvents();

			Properties props = new Properties();
			props.setProperty("myProperty", "myvalue");

			LogEvent event1 = new LogEvent("mytarget1", 2, 1, System.currentTimeMillis(), LogService.LOG_ERROR, props);
			LogEvent event2 = new LogEvent("mytarget1", 2, 2, System.currentTimeMillis(), LogService.LOG_ERROR, props);

			m_logStore.put(Arrays.asList(event1, event2));

			List<LogDescriptor> descriptors = m_logStore.getDescriptors();
			assertEquals(3, descriptors.size());
			assertEquals("mytarget1", descriptors.get(0).getTargetID());
			assertEquals(1, descriptors.get(0).getLogID());
			assertEquals(4, descriptors.get(0).getRangeSet().getHigh());

			assertEquals("mytarget1", descriptors.get(1).getTargetID());
			assertEquals(2, descriptors.get(1).getLogID());
			assertEquals(2, descriptors.get(1).getRangeSet().getHigh());

			assertEquals("mytarget2", descriptors.get(2).getTargetID());
			assertEquals(1, descriptors.get(2).getLogID());
			assertEquals(5, descriptors.get(2).getRangeSet().getHigh());
		} catch (MongoException ex) {
			System.err.println("Mongodb not available on localhost, skipping test");
		}
	}

	public void testGetDescriptorsForTarget() throws Exception {
		try {
			DBCollection collection = m_mongodbService.getDB().getCollection("serverlog");
			collection.remove(new BasicDBObject());
			TimeUnit.SECONDS.sleep(2);

			storeEvents();

			Properties props = new Properties();
			props.setProperty("myProperty", "myvalue");

			LogEvent event1 = new LogEvent("mytarget1", 2, 1, System.currentTimeMillis(), LogService.LOG_ERROR, props);
			LogEvent event2 = new LogEvent("mytarget1", 2, 2, System.currentTimeMillis(), LogService.LOG_ERROR, props);

			m_logStore.put(Arrays.asList(event1, event2));

			List<LogDescriptor> descriptors = m_logStore.getDescriptors("mytarget1");
			assertEquals(2, descriptors.size());
			assertEquals("mytarget1", descriptors.get(0).getTargetID());
			assertEquals(1, descriptors.get(0).getLogID());
			assertEquals(4, descriptors.get(0).getRangeSet().getHigh());

			assertEquals("mytarget1", descriptors.get(1).getTargetID());
			assertEquals(2, descriptors.get(1).getLogID());
			assertEquals(2, descriptors.get(1).getRangeSet().getHigh());
		} catch (MongoException ex) {
			System.err.println("Mongodb not available on localhost, skipping test");
		}
	}

	private void storeEvents() throws IOException {
		Properties props = new Properties();
		props.setProperty("myProperty", "myvalue");
		LogEvent event1 = new LogEvent("mytarget1", 1, 1, System.currentTimeMillis(), LogService.LOG_ERROR, props);
		LogEvent event2 = new LogEvent("mytarget1", 1, 2, System.currentTimeMillis(), LogService.LOG_ERROR, props);
		LogEvent event3 = new LogEvent("mytarget2", 1, 3, System.currentTimeMillis(), LogService.LOG_ERROR, props);
		LogEvent event4 = new LogEvent("mytarget2", 1, 5, System.currentTimeMillis(), LogService.LOG_ERROR, props);
		LogEvent event5 = new LogEvent("mytarget1", 1, 4, System.currentTimeMillis(), LogService.LOG_ERROR, props);

		m_logStore.put(Arrays.asList(event1, event2, event3, event4, event5));
	}

}

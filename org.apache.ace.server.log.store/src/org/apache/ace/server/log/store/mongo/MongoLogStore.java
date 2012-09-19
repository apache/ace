package org.apache.ace.server.log.store.mongo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.amdatu.mongo.MongoDBService;
import org.apache.ace.log.LogDescriptor;
import org.apache.ace.log.LogEvent;
import org.apache.ace.range.Range;
import org.apache.ace.range.SortedRangeSet;
import org.apache.ace.server.log.store.LogStore;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MapReduceCommand.OutputType;
import com.mongodb.MapReduceOutput;

public class MongoLogStore implements LogStore {
	private final String m_logname;
	private volatile MongoDBService m_mongoDBService;

	public MongoLogStore(String logname) {
		this.m_logname = logname;
	}

	@Override
	public List<LogEvent> get(LogDescriptor range) throws IOException {
		DBCollection collection = m_mongoDBService.getDB().getCollection(m_logname);
		long high = range.getRangeSet().getHigh();

		BasicDBObject filter = new BasicDBObject().append("targetId",
				range.getTargetID()).append("logId", range.getLogID());
		if (high > 0) {
			filter.append("id", new BasicDBObject("$lte", high));
		}

		DBCursor cursor = collection.find(filter);
		cursor.sort(new BasicDBObject("id", 1));

		List<LogEvent> logevents = new ArrayList<LogEvent>();
		while (cursor.hasNext()) {
			DBObject event = cursor.next();
			String targetId = (String) event.get("targetId");
			long logId = (Long) event.get("logId");
			long id = (Long) event.get("id");
			long time = (Long) event.get("time");
			int type = (Integer) event.get("type");
			Properties properties = new Properties();
			DBObject propertiesDbObject = (DBObject) event.get("properties");
			for (String key : propertiesDbObject.keySet()) {
				properties.put(key, propertiesDbObject.get(key));
			}

			logevents.add(new LogEvent(targetId, logId, id, time, type,
					properties));
		}

		return logevents;
	}

	@Override
	public LogDescriptor getDescriptor(String targetID, long logID)
			throws IOException {

		DBCollection collection = m_mongoDBService.getDB().getCollection(m_logname);

		BasicDBObject filter = new BasicDBObject().append("targetId", targetID)
				.append("logId", logID);

		DBCursor cursor = collection.find(filter);
		cursor.sort(new BasicDBObject("id", -1));

		long high = 1;
		if (cursor.hasNext()) {
			DBObject row = cursor.next();
			high = (Long) row.get("id");
			return new LogDescriptor(targetID, logID, new SortedRangeSet(
					new Range(1, high).toRepresentation()));
		} else {
			return new LogDescriptor(targetID, logID, SortedRangeSet.FULL_SET);
		}
	}

	@Override
	public void put(List<LogEvent> events) throws IOException {
		DBCollection collection = m_mongoDBService.getDB().getCollection(m_logname);

		for (LogEvent event : events) {
			DBObject dbObject = new BasicDBObject()
					.append("targetId", event.getTargetID())
					.append("logId", event.getLogID())
					.append("id", event.getID())
					.append("time", event.getTime())
					.append("type", event.getType())
					.append("properties", event.getProperties());

			collection.save(dbObject);
		}
	}

	@Override
	public List<LogDescriptor> getDescriptors(String targetID)
			throws IOException {
		
		DBCollection collection = m_mongoDBService.getDB().getCollection(m_logname);
		String m = "function() {emit(this.targetId,this.logId);}";
		String r = "function(k, vals) {var result = {target: k, logIds: []}; vals.forEach(function(value) { result.logIds.push(value)}); return result;}";
		DBObject filter = new BasicDBObject();
		if(targetID != null) {
			filter.put("targetId", targetID);
		}
		MapReduceOutput mapReduce = collection.mapReduce(m, r, null, OutputType.INLINE, filter);
		Iterator<DBObject> iterator = mapReduce.results().iterator();
		
		List<LogDescriptor> descriptors = new ArrayList<LogDescriptor>();
		while(iterator.hasNext()) {
			DBObject row = iterator.next();
			DBObject value = (DBObject)row.get("value");
			String targetId = (String)value.get("target");
			@SuppressWarnings("unchecked")
			List<Long> logIds = (List<Long>)value.get("logIds");
			Set<Long> logIdsFiltered = new HashSet<Long>();
			logIdsFiltered.addAll(logIds);
			
			for (long logId : logIdsFiltered) {
				descriptors.add(getDescriptor(targetId, logId));
			}
		}
		
		return descriptors;
	}

	@Override
	public List<LogDescriptor> getDescriptors() throws IOException {
		return getDescriptors(null);
	}

}

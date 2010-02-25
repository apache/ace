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
package org.apache.felix.deployment.rp.autoconf.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;

import org.apache.felix.metatype.Attribute;
import org.apache.felix.metatype.Designate;
import org.apache.felix.metatype.DesignateObject;
import org.apache.felix.metatype.MetaData;
import org.apache.felix.metatype.MetaDataReader;
import org.apache.felix.metatype.OCD;
import org.apache.felix.metatype.internal.LocalizedObjectClassDefinition;
import org.apache.felix.metatype.internal.l10n.BundleResources;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.deploymentadmin.spi.DeploymentSession;
import org.osgi.service.deploymentadmin.spi.ResourceProcessor;
import org.osgi.service.deploymentadmin.spi.ResourceProcessorException;
import org.osgi.service.log.LogService;
import org.osgi.service.metatype.AttributeDefinition;
import org.osgi.service.metatype.MetaTypeInformation;
import org.osgi.service.metatype.MetaTypeService;
import org.osgi.service.metatype.ObjectClassDefinition;
import org.xmlpull.v1.XmlPullParserException;

public class AutoConfResourceProcessor implements ResourceProcessor {

	private static final String LOCATION_PREFIX = "osgi-dp:";
	
	private volatile LogService m_log;					// injected by dependency manager
	private volatile ConfigurationAdmin m_configAdmin;	// injected by dependency manager
	private volatile MetaTypeService m_metaService;		// injected by dependency manager
	private volatile BundleContext m_bc;				// injected by dependency manager
	
	private DeploymentSession m_session = null;

	private Map m_currentDesignates = new HashMap();
	private Map m_currentProps = new HashMap();
	
	/**
	  * Called when the Deployment Admin starts a new operation on the given deployment package, 
	  * and the resource processor is associated a resource within the package. Only one 
	  * deployment package can be processed at a time.
	  * 
	  * @param session object that represents the current session to the resource processor
	  * @see DeploymentSession
	  */
   public void begin(DeploymentSession session) {
	   m_log.log(LogService.LOG_DEBUG, "Begin called" + ", rp instance:" + this);
	   m_session = session;
   }
 
   /**
    * Called when a resource is encountered in the deployment package for which this resource 
    * processor has been  selected to handle the processing of that resource.
    * 
    * @param name The name of the resource relative to the deployment package root directory. 
    * @param stream The stream for the resource. 
    * @throws ResourceProcessorException if the resource cannot be processed. Only 
    *         {@link ResourceProcessorException#CODE_RESOURCE_SHARING_VIOLATION} and 
    *         {@link ResourceProcessorException#CODE_OTHER_ERROR} error codes are allowed.
    */
   public void process(String name, InputStream stream) throws ResourceProcessorException {
	   m_log.log(LogService.LOG_DEBUG, "Process called for resource: " + name + ", rp instance:" + this);
	   if (m_session == null) {
		   throw new ResourceProcessorException(ResourceProcessorException.CODE_OTHER_ERROR, "Can not process resource without a Deployment Session" + ", rp instance:" + this);
	   }
	   MetaDataReader reader = new MetaDataReader();
	   try {
		   MetaData data = reader.parse(stream);
		   Map designates = data.getDesignates();
		   Map ocds = data.getObjectClassDefinitions();
		   Set keySet = designates.keySet();
		   Iterator it = keySet.iterator();
		   while (it.hasNext()) {
			   Designate designate = (Designate) designates.get(it.next());
			   if (designate.getFactoryPid() != null || "".equals(designate.getFactoryPid())) {
				   // TODO: support factory configurations
				   m_log.log(LogService.LOG_ERROR, "Factory configurations are not supported yet.");
				   continue;
			   }
			   
			   // determine bundle
			   String bundleLocation = designate.getBundleLocation();
			   Bundle bundle = null;
			   if (bundleLocation.startsWith(LOCATION_PREFIX)) {
				   bundle = m_session.getSourceDeploymentPackage().getBundle(bundleLocation.substring(LOCATION_PREFIX.length()));
			   }
			   if (bundle == null) {
				   // added to allow foreign bundles
				   Bundle[] bundles = m_bc.getBundles();
				   for (int i = 0; i < bundles.length; i++) {
					   String location = bundles[i].getLocation();
					   if (bundleLocation.equals(location)) {
						   bundle = bundles[i];
						   break;
					   }
				   }
				   // end of foreign bundle code
				   if (bundle == null) {
					   throw new ResourceProcessorException(ResourceProcessorException.CODE_RESOURCE_SHARING_VIOLATION);
				   }
			   }
			   
			   // find correct ocd
			   DesignateObject designateObject = designate.getObject();
			   String ocdRef = designateObject.getOcdRef();
			   OCD internalOcd = (OCD) ocds.get(ocdRef);
			   ObjectClassDefinition ocd = null;
			   if (internalOcd != null) {
				   // use local ocd
				   ocd = new LocalizedObjectClassDefinition(bundle, internalOcd, BundleResources.getResources(bundle, "", ""));
				   // TODO: getting a 'Resources' object like this is probably not a good idea
			   }
			   else {
				   // obtain ocd from metatypeservice
				   MetaTypeInformation mti = m_metaService.getMetaTypeInformation(bundle);
				   if (mti != null) {
					   try {
						   ocd = mti.getObjectClassDefinition(designate.getPid(), null);
					   }
					   catch (IllegalArgumentException iae) {
						   throw new ResourceProcessorException(ResourceProcessorException.CODE_OTHER_ERROR, "Unable to get Object Class Definition.", iae);
					   }
				   }
				   else {
					   throw new ResourceProcessorException(ResourceProcessorException.CODE_OTHER_ERROR, "Unable to get Object Class Definition.");
				   }
			   }
			   
			   // match attributes with attribute definitions from ocd
			   Dictionary newProps = new Properties();
			   AttributeDefinition[] ads = ocd.getAttributeDefinitions(ObjectClassDefinition.ALL);
			   List attributes = designateObject.getAttributes();
			   Iterator it2 = attributes.iterator();
			   while (it2.hasNext()) {
				   Attribute attribute = (Attribute) it2.next();
				   String adRef = attribute.getAdRef();
				   boolean found = false;
				   for(int i = 0; i < ads.length; i++) {
					   AttributeDefinition ad = ads[i];
					   if (adRef.equals(ad.getID())) {
						   Object value = getValue(attribute, ad);
						   m_log.log(LogService.LOG_DEBUG, "RP FOUND VALUE: adref=" + adRef + ", value=" + value);
						   if (value == null && !designate.isOptional()) {
							   throw new ResourceProcessorException(ResourceProcessorException.CODE_OTHER_ERROR, "Could not match attribute to it's definition adref=" + adRef);
						   }
						   newProps.put(adRef, value);
						   found = true;
						   break;
					   }
				   }
				   if (!found && !designate.isOptional()) {
					   throw new ResourceProcessorException(ResourceProcessorException.CODE_OTHER_ERROR, "Could not find attribute definition: adref=" + adRef);
				   }
			   }

			   m_currentDesignates.put(name, designate);
			   m_currentProps.put(name, newProps);
		   }
		} catch (IOException e) {
			throw new ResourceProcessorException(ResourceProcessorException.CODE_OTHER_ERROR, "Unable to process resource due to I/O problems.");
		} catch (XmlPullParserException e) {
			throw new ResourceProcessorException(ResourceProcessorException.CODE_OTHER_ERROR, "Supplied configuration is not conform the metatype xml specification.");
		}
    }
   
	/**
	  * Called when a resource, associated with a particular resource processor, had belonged to 
	  * an earlier version of a deployment package but is not present in the current version of 
	  * the deployment package.  This provides an opportunity for the processor to cleanup any 
	  * memory and persistent data being maintained for the particular resource.  
	  * This method will only be called during "update" deployment sessions.
	  * 
	  * @param resource the name of the resource to drop (it is the same as the value of the 
	  *        "Name" attribute in the deployment package's manifest)
	  * @throws ResourceProcessorException if the resource is not allowed to be dropped. Only the 
	  *         {@link ResourceProcessorException#CODE_OTHER_ERROR} error code is allowed
	  */
   public void dropped(String resource) throws ResourceProcessorException {
	   m_log.log(LogService.LOG_DEBUG, "Dropped called for resource: " + resource + ", rp instance:" + this);
	   if (m_session == null) {
		   throw new ResourceProcessorException(ResourceProcessorException.CODE_OTHER_ERROR, "Can not process resource without a Deployment Session" + ", rp instance:" + this);
	   }
   }
   
   /**
    * This method is called during an "uninstall" deployment session.
    * This method will be called on all resource processors that are associated with resources 
    * in the deployment package being uninstalled. This provides an opportunity for the processor 
    * to cleanup any memory and persistent data being maintained for the deployment package.
    * 
    * @throws ResourceProcessorException if all resources could not be dropped. Only the 
    *         {@link ResourceProcessorException#CODE_OTHER_ERROR} is allowed.
    */
   public void dropAllResources() throws ResourceProcessorException {
	   m_log.log(LogService.LOG_DEBUG, "DropAllResources called" + ", rp instance:" + this);
	   if (m_session == null) {
		   throw new ResourceProcessorException(ResourceProcessorException.CODE_OTHER_ERROR, "Can not process resource without a Deployment Session" + ", rp instance:" + this);
	   }
   }
 
   /**
    * This method is called on the Resource Processor immediately before calling the 
    * <code>commit</code> method. The Resource Processor has to check whether it is able 
    * to commit the operations since the last <code>begin</code> method call. If it determines 
    * that it is not able to commit the changes, it has to raise a 
    * <code>ResourceProcessorException</code> with the {@link ResourceProcessorException#CODE_PREPARE} 
    * error code.
    * 
    * @throws ResourceProcessorException if the resource processor is able to determine it is 
    *         not able to commit. Only the {@link ResourceProcessorException#CODE_PREPARE} error 
    *         code is allowed.
    */
   public void prepare() throws ResourceProcessorException {
	   m_log.log(LogService.LOG_DEBUG, "Prepare called" + ", rp instance:" + this);
	   if (m_session == null) {
		   throw new ResourceProcessorException(ResourceProcessorException.CODE_OTHER_ERROR, "Can not process resource without a Deployment Session" + ", rp instance:" + this);
	   }
   }
  
   /**
    * Called when the processing of the current deployment package is finished. 
    * This method is called if the processing of the current deployment package was successful, 
    * and the changes must be made permanent.
    */
   public synchronized void commit() {
	   m_log.log(LogService.LOG_DEBUG, "Commit called" + ", rp instance:" + this);
	   Set keySet = m_currentDesignates.keySet();
	   Iterator i = keySet.iterator();
	   while (i.hasNext()) {
		   String key = (String) i.next();
		   Designate designate = (Designate) m_currentDesignates.get(key);
		   Dictionary newProps = (Dictionary) m_currentProps.get(key);
		   if (newProps == null) {
			   m_log.log(LogService.LOG_DEBUG, "Internal error: no new properties found for resource (name=" + key + ")" + ", rp instance:" + this);
		   }
		   // update configuration
		   try {
			   Configuration configuration = m_configAdmin.getConfiguration(designate.getPid(), designate.getBundleLocation());
			   if (designate.isMerge()) {
				   Dictionary currentProps = configuration.getProperties();
				   if (currentProps != null) {
					   Enumeration keys = currentProps.keys();
					   while (keys.hasMoreElements()) {
						   Object propKey = keys.nextElement();
						   newProps.put(propKey, currentProps.get(propKey));
					   }
				   }
			   }
			   configuration.update(newProps);
		   }
		   catch (IOException ioe) {
			   m_log.log(LogService.LOG_DEBUG, "Could not commit resource (name=" + key + ")" + ", rp instance:" + this);
		   }
	   }
	   m_currentDesignates.clear();
	   m_currentProps.clear();
	   m_session = null;
   }
    
   /**
    * Called when the processing of the current deployment package is finished. 
    * This method is called if the processing of the current deployment package was unsuccessful, 
    * and the changes made during the processing of the deployment package should be removed.  
    */
   public void rollback() {
	   m_log.log(LogService.LOG_DEBUG, "Rollback called" + ", rp instance:" + this);
	   m_currentDesignates.clear();
	   m_currentProps.clear();
	   m_session = null;
   }
   
   /**
    * Processing of a resource passed to the resource processor may take long. 
    * The <code>cancel()</code> method notifies the resource processor that it should 
    * interrupt the processing of the current resource. This method is called by the 
    * <code>DeploymentAdmin</code> implementation after the
    * <code>DeploymentAdmin.cancel()</code> method is called.
    */
   public void cancel() {
	   m_log.log(LogService.LOG_DEBUG, "Cancel called" + ", rp instance:" + this);
	   m_currentDesignates.clear();
	   m_currentProps.clear();
	   m_session = null;
   }

   /**
    * Gets the values of the specified attribute, return value is based on the specified attribute definitions.
    * 
    * @param attribute Attribute containing the value(s)
    * @param ad Attribute definition
    * @return Object Object representing the value(s) of the attribute, or <code>null</code> if the attribute did not match it's definition. Object can be
    * a single object of one of the types specified as constants in <code>AttributeDefinition</code> or a <code>Vector</code> or an <code>array<code> with elements
    * of one of these types.
    */
   private Object getValue(Attribute attribute, AttributeDefinition ad) {
	   if (!attribute.getAdRef().equals(ad.getID())) {
		   // wrong attribute or definition
		   return null;
	   }
	   String[] content = attribute.getContent();
		
		// verify correct type of the value(s)
		int type = ad.getType();
		Object[] typedContent = new Object[content.length];
		try {
			for (int i = 0; i < content.length; i++) {
				String value = content[i];
				switch (type) {
				case AttributeDefinition.BOOLEAN:
					typedContent[i] = Boolean.valueOf(value);	
					break;
				case AttributeDefinition.BYTE:
					typedContent[i] = Byte.valueOf(value);
					break;
				case AttributeDefinition.CHARACTER:
					char[] charArray = value.toCharArray();
					if (charArray.length == 1) {
						typedContent[i] = Character.valueOf(charArray[0]);
					}
					else {
						return null;
					}
					break;
				case AttributeDefinition.DOUBLE:
					typedContent[i] = Double.valueOf(value);
					break;
				case AttributeDefinition.FLOAT:
					typedContent[i] = Float.valueOf(value);
					break;
				case AttributeDefinition.INTEGER:
					typedContent[i] = Integer.valueOf(value);
					break;
				case AttributeDefinition.LONG:
					typedContent[i] = Long.valueOf(value);
					break;
				case AttributeDefinition.SHORT:
					typedContent[i] = Short.valueOf(value);
					break;
				case AttributeDefinition.STRING:
					typedContent[i] = value;
					break;
				default:
					// unsupported type
					return null;
				}
			}
		}
		catch (NumberFormatException nfe) {
			return null;
		}
		
		// verify cardinality of value(s)
		int cardinality = ad.getCardinality();
		Object result = null;
		if (cardinality == 0) {
			if (typedContent.length == 1) {
				result = typedContent[0];
			}
			else {
				result = null;
			}
		}
		else if (cardinality == Integer.MIN_VALUE) {
			result = new Vector(Arrays.asList(typedContent));
		}
		else if (cardinality == Integer.MAX_VALUE) {
			result = typedContent;
		}
		else if (cardinality < 0) {
			if (typedContent.length == cardinality) {
				result = new Vector(Arrays.asList(typedContent));
			}
			else {
				result = null;
			}
		}
		else if (cardinality > 0) {
			if (typedContent.length == cardinality) {
				result = typedContent;
			}
			else {
				result = null;
			}
		}
		return result;
	}
}

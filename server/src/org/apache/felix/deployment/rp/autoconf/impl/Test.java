package org.apache.felix.deployment.rp.autoconf.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.felix.metatype.MetaData;
import org.apache.felix.metatype.MetaDataReader;
import org.xmlpull.v1.XmlPullParserException;

public class Test {

	public static void main(String[] args) throws Exception {
		   MetaDataReader reader = new MetaDataReader();
		   File file = new File("/Users/christianvanspaandonk/autoconf/sample.xml");
		   try {
			   MetaData data = reader.parse(new FileInputStream(file));
			   Map designates = data.getDesignates();
			   Map ocds = data.getObjectClassDefinitions();
			   Set keySet = designates.keySet();
			   Iterator it = keySet.iterator();
//			   while (it.hasNext()) {
//				   Designate designate = (Designate) designates.get(it.next());
//				   String bundleLocation = designate.getBundleLocation();
//				   Bundle bundle = null;
//				   if (bundleLocation.startsWith(LOCATION_PREFIX)) {
//					   bundle = m_session.getTargetDeploymentPackage().getBundle(bundleLocation.substring(LOCATION_PREFIX.length()));
//				   }
//				   if (bundle == null) {
//					   throw new ResourceProcessorException(ResourceProcessorException.CODE_RESOURCE_SHARING_VIOLATION);
//				   }
//				   DesignateObject designateObject = designate.getObject();
//				   String ocdRef = designateObject.getOcdRef();
//				   ObjectClassDefinition ocd = null;
//				   OCD internalOcd = (OCD) ocds.get(ocdRef);
//				   if (internalOcd != null) {
//					   // use supplied ocd
//					   ocd = new LocalizedObjectClassDefinition(bundle, internalOcd, null);
//				   }
//				   else {
//					   // obtain ocd from metatypeservice
//					   MetaTypeInformation mti = m_metaService.getMetaTypeInformation(bundle);
//					   ocd = mti.getObjectClassDefinition(ocdRef, null);
//				   }
//				   
//				   AttributeDefinition[] ads = ocd.getAttributeDefinitions(ObjectClassDefinition.ALL);
//				   List attributes = designateObject.getAttributes();
//				   Iterator it2 = attributes.iterator();
//				   while (it2.hasNext()) {
//					   Attribute attribute = (Attribute) it2.next();
//					   String adRef = attribute.getAdRef();
//					   for(int i = 0; i < ads.length; i++) {
//						   AttributeDefinition ad = ads[i];
//						   if (adRef.equals(ad.getID())) {
//							   
//							   //
//						   }
//					   }
//				   }
//			   }
			   System.out.println(data.getDesignates());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (XmlPullParserException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}
}

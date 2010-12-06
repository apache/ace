package org.apache.ace.client.repository.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.helper.PropertyResolver;
import org.apache.ace.client.repository.object.GatewayObject;



/**
 * 
 * Top-level property resolver, also able to return collections
 * of distributions, features and artifacts linked to this target
 * repository object.
 * 
 * 
 * @author dennisg
 *
 */
public class GatewayPropertyResolver extends RepoPropertyResolver {

		
        public GatewayPropertyResolver(GatewayObject go) {
        	super(go);
        }


        public Collection<PropertyResolver> getDistributions() {
        	List<PropertyResolver> list = new ArrayList<PropertyResolver>();
        	
        	List<RepositoryObject> distributions = (List<RepositoryObject>)getChildren();
        	
        	for (RepositoryObject repo : distributions) {
				list.add(new RepoPropertyResolver(repo));
			}
        	
        	return list;
        }
        
        public Collection<PropertyResolver> getFeatures() {
        	List<PropertyResolver> list = new ArrayList<PropertyResolver>();
        	
        	List<RepositoryObject> features = new ArrayList<RepositoryObject>();
        	
        	for (RepositoryObject repositoryObject : getChildren()) {
        		features.addAll(getChildren(repositoryObject));
			}
        	
        	for (RepositoryObject repo : features) {
				list.add(new RepoPropertyResolver(repo));
			}
        	return list;
        }
        
        public Collection<PropertyResolver> getArtifacts() {
        	List<PropertyResolver> list = new ArrayList<PropertyResolver>();
        	
        	List<RepositoryObject> artifacts = new ArrayList<RepositoryObject>();
        	
        	
        	List<RepositoryObject> features = new ArrayList<RepositoryObject>();
        	
        	for (RepositoryObject repositoryObject : getChildren()) {
        		features.addAll(getChildren(repositoryObject));
			}        	
        	
        	for (RepositoryObject repositoryObject : features) {
				artifacts.addAll(getChildren(repositoryObject));
			}
        	
        	
        	for (RepositoryObject repo : artifacts) {
				list.add(new RepoPropertyResolver(repo));
			}
        	return list;
        }

}

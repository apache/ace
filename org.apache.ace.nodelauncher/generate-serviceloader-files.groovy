# Licensed to the Apache Software Foundation (ASF) under the terms of ASLv2 (http://www.apache.org/licenses/LICENSE-2.0).

def libDir = new File("lib")
def serviceProviders = []

libDir.listFiles().each {
	def zipFile = new java.util.zip.ZipFile(it)
	def metaDataFile = zipFile.getEntry('META-INF/services/org.jclouds.apis.ApiMetadata')
	if(metaDataFile) {
		serviceProviders << zipFile.getInputStream(metaDataFile).text		
	}
}

def output = new File('resources/org.jclouds.apis.ApiMetadata')
serviceProviders.each {
	output << it
	output << '\n'
}
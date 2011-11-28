Apache ACE
==========

Apache ACE is a software distribution framework that allows you to centrally manage and distribute software components, configuration data and other artifacts to target systems. It is built using OSGi and can be deployed in different topologies. The target systems are usually also OSGi based, but don't have to be.

Go to the website for more information about ACE:
http://incubator.apache.org/ace/


Building from source
--------------------

Prerequisites

 - Java 6 SDK
 - Maven 2.2.1


Building the full source distribution

For convenience, when we do a full distribution of all the components that make up Apache ACE, we also release a component that contains all the sources. This helps you get started with the source code of the project more easily. You need an artifact called 'ace-release-full', which you unzip and then build like this:

mvn install


Building all artifacts at once

Another way to build the full project, is to download all the individual source components that make up Apache ACE, unzip each of them (they should all extract into their own folder) and then at the top level, build the project like this:

mvn -r install

Note: this does not work with Maven 3 (which does not have the -r option)


Building a single artifact

The final way to build the project, is by building a single source component. You can do that as long as you have the dependencies that are required by this project installed in either your local Maven cache, or in the repository at Apache. Getting all dependencies in your local Maven cache can be done by either downloading and installing them by hand (if you actually run Maven and a dependency is missing, it will explain you how to do that) or by doing a full source build once. Building the artifact is done like this:

mvn install


Getting Started
---------------

Detailed instructions on how to proceed once you've built the whole project, can be found on the Apache ACE website:

http://incubator.apache.org/ace/getting-started.html

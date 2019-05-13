## Developers Section
This section is intended for developers interested in contributing to the Biocode LIMS development.

## Requirements
* Java Development Kit 1.6+
* Gradle

### Geneious Plugin
Run the following command from the root directory to create the gplugin file:

    ./gradlew  createPlugin

Drag and drop the gplugin file from the build/distributoin directory into Geneious to install the plugin.

### LIMS Server (Under Development)
The LIMS Server is packaged as a distributable WAR file that can be deployed on most Java Web Servers.  (eg) Jetty 
or Apache Tomcat.

Run the following command from the **biocode-server** directory to create the war file:
    
    ./gradelw create-war

The war file will be created in the biocode-server directory.

## Development
The development of this project follows the [Gitflow branching strategy](https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow).  
All development is done in the develop branch and merged to master when complete.  Thus master only contains released code.

When switching branches it is always a good idea to run a build with

    ./gradlew  build-plugin

This will ensure any dependency changes for the new branch are applied and everything compiles.

### Modules
The project currently contains two modules:

* biocode-lims - The Geneious plugin
* biocode-server - The unreleased server

In most cases you will only need to make changes to the biocode-lims source code.


### Dependency Management
The plugin uses Apache Ivy for depenedency management and the repository includes everything that is required for its use.


### Use of an IDE
Many modern IDEs come with Apache Ivy integration.  If you are using such a feature, please note that the biocode-server 
depends on some Geneious core classes and this is not reflected in the Ivy configuration.  The main reason for this is
  that the required libraries are not provided standalone in any repositories.

In the gradle build, the complete Geneious runtime is downloaded and the required libraries are extracted from it.

## Contributing
Please contact support@mooreabiocode.org

## Biocode LIMS Server 
The Biocode LIMS server is an extension to the original LIMS that adds security, user management, access control and 
the ability to offload tasks from the Geneious client.  
 
The server needs to be deployed in a compatible Java Web Application server and acts as a middle man between the 
client and the LIMS database.  The client communicates with the server using a REST interface.  By providing this
interface rather than a proprietary one, there is the possibility of other future clients outside of the Geneious 
software package.
 
It is currently in active development and has not been released yet.

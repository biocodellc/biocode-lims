## Authors
Biomatters Ltd

Contact: support@mooreabiocode.org

## Summary
The Biocode LIMS is a Geneious plugin that was developed as part of the [Moorea Biocode Project](http://mooreabiocode.org).
It comprises everything you need to manage your lab and sequence analysis workflows.

### Biocode LIMS Server
The Biocode LIMS server is an extension to the original LIMS that adds security, user management, access control and 
 the ability to offload tasks from the Geneious client.  
 
 The server needs to be deployed in a compatible Java Web Application server and acts as a middle man between the 
 client and the LIMS database.  The client communicates with the server using a REST interface.  By providing this
 interface rather than a proprietary one, there is the possibility of other future clients outside of the Geneious 
 software package.
 
 It is currently in active development and has not been released yet.

## Installation
Download official releases from the [official website](a href="http://software.mooreabiocode.org/")

## Using the Source Code
### Building
Run the ant target create-plugin.  All dependencies will be retrieved from the internet using Apache Ivy.

### Building the Server
The create-war ant task will create a distributable WAR file that can be deployed on most Java Web Servers.  (eg) Jetty 
or Apache Tomcat.

### Use of an IDE
Many modern IDEs come with Apache Ivy integration.  If you are using such a feature, please note that the biocode-server 
depends on some Geneious core classes and this is not reflected in the Ivy configuration.  The main reason for this is
  that the required libraries are not provided standalone in any repositories.

In the ant build, the complete Geneious runtime is downloaded and the required libraries are extracted from it.

If  you do not wish to use the ant build, then you can obtain the core libraries from the Geneious Plugin Development Kit
or any installation of Geneious.


## More Information
See the official wiki at [http://software.mooreabiocode.org](http://software.mooreabiocode.org) for more information
including the user guide.

A great source of help can be found on the discussion forum at
[http://connect.barcodeoflife.net/group/lims](http://connect.barcodeoflife.net/group/lims) or you can email
support@mooreabiocode.org
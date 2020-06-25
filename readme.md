# Biocode LIMS
  
The Biocode LIMS is a Geneious plugin that comprises everything you need to manage your lab and sequence analysis workflows. For information on how to use the Biocode LIMS and to download the plugin visit our [Wiki Page](https://github.com/biocodellc/biocode-lims/wiki).

## Useful Information
* Official releases are available from our [releases page](https://github.com/biocodellc/biocode-lims/releases)
* Official [Wiki Page](https://github.com/biocodellc/biocode-lims/wiki) for more information
including the user guide.
* Support email support@mooreabiocode.org
* Information for developers on contributing to the Biocode LIMS plugin is on our [development page](https://github.com/biocodellc/biocode-lims/blob/develop/development.md)
 

## Developer Information
The Biocode LIMS plugin is built and developed using IntelliJ and gradle.  Currently, we develop against the geneiousPublicAPIVersion = 11.1.5 and use Java 8.
There are a couple of issues preventing compiling using Java 11 and hence being able to develop against GeneiousPrime:
 * openJFK libraries were removed in Java 11.  See issue: https://github.com/biocodellc/biocode-lims/issues/116
 * ClassLoader issues cropped up in Java 9 See issue: https://community.oracle.com/thread/4011800

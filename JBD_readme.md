# JBD Readme

Random notes about working in the LIMS development environmeent

# if we ever get class conflicts of the same class name, some steps to rectify:
NOTE: I believe this happens when i switch versions of geneious... doesn't seem to happen
if i stay on a single geneious version or with a single genious API.
there is probably a more elegant way to proceed here but this is what i've found helpful
  * clean the Data directory or provide an entirely new one
  * rm -fr /Library/Application\ Support/Geneious/plugins/com.biomatters.plugins.biocode.BiocodePlugin/
  * rm -fr ~/.gradle/caches
  * may have to clean gradle like ./gradlew clean

# Quickly create a plugin, without running time-consuming Tests... Only for development
# only run from the command line
```
./gradlew quickCreatePlugin
 ```

There may be errors related to running gradle from the command-line, in which case we want to invoke sdk-specific versions of gradle and/or java:

```
sdk use java  11.0.17-amzn
sdk use gradle 5.6.4
```
 

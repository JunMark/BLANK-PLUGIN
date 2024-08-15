# plugin for XNAT
Replace all 'BLANK' by a name of target datatype, like 'EEG','NIRS','TCD','TMS'

Notice that the some varieble are case-sensitive. So 'BLANK' means all letter are upper case, eg 'NIRS'. 
 'Blank' means the first letter is upper case and the rest ones are lower case, eg 'Nirs'


Build and Package Plugin:
• Package the plugin into a JAR file using Gradle: ‘.\gradlew jar’. 
Tips: everytime you make any change to schema or java class, use ‘.\gradlew jar’ to generate an updated JAR file. 
• The JAR file will be located at ./build/libs/

Deploy Plugin to XNAT: 
• Copy the generated JAR file to the XNAT plugins directory. 
• Restart XNAT instance to load the new plugin. 
• Verify that the plugin is successfully loaded in the XNAT admin interface.



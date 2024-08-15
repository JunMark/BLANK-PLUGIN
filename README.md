# plugin for XNAT
Travel all files and Replace all 'BLANK' by a name of target datatype, like 'EEG','NIRS','TCD','TMS'

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

Set QuickSearch 
In Xnat Web, go to Administer > Site Admininistration in the top navigation and click on the Site Setup tab. Change the setting below:
1. Site Login Landing: This is the relative path (e.g. /screens/QuickSearch_update.vm) to the Velocity template page that you want users to land on upon logging in.
2. Site Home: This is the relative path (e.g. /screens/QuickSearch_update.vm) to the Velocity template page that you want users to land on when they click the XNAT logo in the menu bar.

You can find .vm file at: BLANKPluginXNAT-main\src\main\resources\META-INF\resources\templates\screens\QuickSearch_update.vm
 Open it and change BLANK to the target Datatype

 # If download and package the plugin into a JAR file directly, you will get a new data type called 'BLANK'

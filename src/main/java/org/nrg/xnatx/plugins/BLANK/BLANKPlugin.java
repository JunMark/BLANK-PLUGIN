package org.nrg.xnatx.plugins.BLANK;

import lombok.extern.slf4j.Slf4j;
import org.nrg.framework.annotations.XnatDataModel;
import org.nrg.framework.annotations.XnatPlugin;
import org.nrg.xdat.om.BlankBlanksessiondata;            // replace Blank by the datatype. First letter uses upper case and the rest of letters use lower case, eg 'Nirs'
import org.nrg.xdat.om.BlankBlankscandata;               // the same as above.
import org.nrg.xnat.restlet.actions.importer.ImporterHandlerPackages;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@XnatPlugin(value = "BLANKPlugin", name = "BLANK Plugin",
            logConfigurationFile = "BLANK-logback.xml",
            dataModels = {
                          @XnatDataModel(value = BlankBlanksessiondata.SCHEMA_ELEMENT_NAME, // First letter uses upper case and the rest of letters use lower case, eg 'Nirs'
                                         singular = "BLANK Session",
                                         plural = "BLANK Sessions",
                                         code = "BLANK"),
                          @XnatDataModel(value = BlankBlankscandata.SCHEMA_ELEMENT_NAME,
                                         singular = "BLANK Scan",
                                         plural = "BLANK Scans",
                                         code = "BLANKScan")
                          })
@ComponentScan({"org.nrg.xnatx.plugins.BLANK.bli.helpers",
                "org.nrg.xnatx.plugins.BLANK.bli.helpers.impl"
})
@Slf4j
public class BLANKPlugin {

    @Autowired
    public BLANKPlugin() { }

    @Bean
    public ImporterHandlerPackages pixiImporterHandlerPackages() {
        return new ImporterHandlerPackages("org.nrg.xnatx.plugins.BLANK.importer");
    }

}

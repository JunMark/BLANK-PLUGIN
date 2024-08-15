package org.nrg.xnatx.plugins.BLANK.sessionBuilder;

import lombok.extern.slf4j.Slf4j;
import org.nrg.session.SessionBuilder;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.bean.BlankBlankscandataBean;
import org.nrg.xdat.bean.BlankBlanksessiondataBean;
import org.nrg.xdat.bean.XnatImagesessiondataBean;
import org.nrg.xdat.model.XnatImagescandataI;
import org.nrg.xdat.om.BlankBlanksessiondata;
import org.nrg.xnat.helpers.prearchive.PrearcUtils;

import java.io.File;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class BLANKSessionBuilder extends SessionBuilder {

    private final File sessionDir;

    public BLANKSessionBuilder(final File sessionDir, final Writer fileWriter) {
        super(sessionDir, sessionDir.getPath(), fileWriter);
        this.sessionDir = sessionDir;
    }

    @Override
    public String getSessionInfo() {
        return "(undetermined)";
    }

    @Override
    public XnatImagesessiondataBean call() throws Exception {
        // Get proj/subj/sess/... parameters
        Map<String, String> parameters = getParameters();
        String project = parameters.getOrDefault(PrearcUtils.PARAM_PROJECT, null);
        String subject = parameters.getOrDefault(PrearcUtils.PARAM_SUBJECT_ID, "");
        String label = parameters.getOrDefault(PrearcUtils.PARAM_LABEL, null);

        log.debug("Building BLI session for Project: {} Subject: {} Session: {}", project, subject, label);

        // Initialize the session and populate
        BlankBlanksessiondataBean BLANKSession = new BlankBlanksessiondataBean();

        BLANKSession.setPrearchivepath(sessionDir.getPath());
        BLANKSession.setProject(project);
        BLANKSession.setSubjectId(subject);
        BLANKSession.setLabel(label);


        // Build scans
        Path scanDir = sessionDir.toPath().resolve("SCANS");
        List<Path> scans = Files.list(scanDir).filter(Files::isDirectory).collect(Collectors.toList());

        for (Path scan : scans) {
            final BLANKScanBuilder BLANKScanBuilder = new BLANKScanBuilder(scan);
            BLANKSession.addScans_scan(BLANKScanBuilder.call());
        }

//         Set session date
//        Optional<Date> sessionDate = BLANKSession.getScans_scan().stream()
//                .map(XnatImagescandataI::getStartDate)
//                .map(d -> (Date) d)
//                .distinct()
//                .sorted()
//                .findFirst();
//        sessionDate.ifPresent(BLANKSession::setDate);

        // Set operator
//        Optional<String> operator = BLANKSession.getScans_scan().stream()
//                .map(XnatImagescandataI::getOperator)
//                .distinct()
//                .findFirst();
//        operator.ifPresent(BLANKSession::setOperator);

        return BLANKSession;
    }
}

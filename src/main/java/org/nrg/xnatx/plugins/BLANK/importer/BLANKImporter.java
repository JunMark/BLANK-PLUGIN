package org.nrg.xnatx.plugins.BLANK.importer;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.nrg.action.ClientException;
import org.nrg.action.ServerException;
import org.nrg.framework.constants.PrearchiveCode;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.bean.CatCatalogBean;
import org.nrg.xdat.bean.XnatResourcecatalogBean;
import org.nrg.xdat.om.ArcProject;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatResourcecatalog;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.event.persist.PersistentWorkflowI;
import org.nrg.xft.event.persist.PersistentWorkflowUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xft.utils.ResourceFile;
import org.nrg.xft.utils.SaveItemHelper;
import org.nrg.xft.utils.fileExtraction.Format;
import org.nrg.xnat.eventservice.services.SubscriptionDeliveryEntityService;
import org.nrg.xnat.helpers.ZipEntryFileWriterWrapper;
import org.nrg.xnat.helpers.prearchive.PrearcDatabase;
import org.nrg.xnat.helpers.prearchive.PrearcUtils;
import org.nrg.xnat.helpers.prearchive.SessionData;
import org.nrg.xnat.helpers.uri.URIManager;
import org.nrg.xnat.helpers.uri.UriParserUtils;
import org.nrg.xnat.restlet.actions.importer.ImporterHandler;
import org.nrg.xnat.restlet.actions.importer.ImporterHandlerA;
import org.nrg.xnat.restlet.util.FileWriterWrapperI;
import org.nrg.xnat.services.archive.CatalogService;
import org.nrg.xnat.services.messaging.prearchive.PrearchiveOperationRequest;
import org.nrg.xnat.turbine.utils.ArcSpecManager;
import org.nrg.xnat.utils.WorkflowUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

//import static jdk.tools.jlink.internal.ImageFileCreator.splitPath;
import static org.nrg.xnat.archive.Operation.Rebuild;
import static org.nrg.xnat.eventservice.entities.TimedEventStatusEntity.Status.ACTION_COMPLETE;
import static org.nrg.xnat.eventservice.entities.TimedEventStatusEntity.Status.ACTION_FAILED;

/**
 * An import handler for BLANK sessions.
 *
 * Supports ZIP uploads of a BLANK session. Each directory in the folder is assumed to be a single session.
 *
 */
@ImporterHandler(handler = "BLANK")
@Slf4j
public class BLANKImporter extends ImporterHandlerA {
    private final InputStream in;
    private final UserI user;
    private final Map<String, Object> params;
    private final Format format;
    private final Date uploadDate;
    private final Set<String> uris;
    private final Set<Path> timestampDirectories;
    private final Set<SessionData> sessions;
    private static final String UNKNOWN_SESSION_LABEL = "BLANK_zip_upload";
    private CatalogService catalogService;

    private SubscriptionDeliveryEntityService subscriptionDeliveryEntityService;

    public BLANKImporter(final Object listenerControl,
                       final UserI user,
                       final FileWriterWrapperI fw,
                       final Map<String, Object> params) throws IOException {
        super(listenerControl, user);
        this.user = user;
        this.params = params;
        this.in = fw.getInputStream();
        this.format = Format.getFormat(fw.getName());
        this.uploadDate = new Date();
        this.uris = Sets.newLinkedHashSet();
        this.timestampDirectories = Sets.newLinkedHashSet();
        this.sessions = Sets.newLinkedHashSet();
        this.subscriptionDeliveryEntityService = XDAT.getContextService().getBean(SubscriptionDeliveryEntityService.class);
    }

    public static String[] splitPath(String pathString) {
        Path path = Paths.get(pathString);
        return StreamSupport.stream(path.spliterator(), false).map(Path::toString)
                .toArray(String[]::new);
    }

    @Override
    public List<String> call() throws ClientException, ServerException {
        // Project ID is required
        if (!params.containsKey(URIManager.PROJECT_ID)) {
            ClientException e = new ClientException("PROJECT_ID is a required parameter for BLANK session uploads.");
            log.error("Project ID required for BLANK session uploads", e);
            throw e;
        }

        String projectId = (String) params.get(URIManager.PROJECT_ID);

        // Only accepting ZIP format
        if (format == Format.ZIP) {
            try (final ZipInputStream zis = new ZipInputStream(in)) {
                log.info("Zip file received by BLANK importer.");

                //read the zip file data
                ZipEntry zipEntry = zis.getNextEntry();
                while (null != zipEntry) {
//                    File newFile = newFile(destDir, zipEntry);
                    if (zipEntry != null && zipEntry.isDirectory() && zipEntry.getName().contains("sub-")){
                        if (zipEntry != null && !zipEntry.isDirectory() && zipEntry.getName().contains("params")) {
                            String[] paths = splitPath(zipEntry.getName());
                            String subject = paths[paths.length-2];
                            log.error("params file!!" + zipEntry.getName());
                            log.error("Create subject" + subject);
                            log.error("upload Params file" + zipEntry.getName());
                            //If subject doesnt already exist create a new subject
                            XnatSubjectdata lookForSubject = XnatSubjectdata.GetSubjectByProjectIdentifier(projectId, subject, user,false);
                            if (lookForSubject != null) {
                                createSubject(URIManager.PROJECT_ID, subject);
                                lookForSubject = XnatSubjectdata.GetSubjectByProjectIdentifier(projectId, subject, user,false);
                            }
                            else{
                                log.error("Adding file to subject data");
                                String subjectURI = UriParserUtils.getArchiveUri(lookForSubject);

                                XnatResourcecatalog resourcecatalog = null;
                                try {
                                    resourcecatalog = catalogService.insertResourceCatalog(user, subjectURI, null);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }

//                                resourcecatalog.resource
//                                String createdUri = UriParserUtils.getArchiveUri(resourcecatalog);
//                                if (StringUtils.isBlank(createdUri)) {
//                                    createdUri = subjectURI + "/resources/" + resourcecatalog.getLabel();
//                                }
//                                File resourceCatalogXml = new File(scanDir.toFile(), "resource_catalog.xml/subjectresource");
//                                XnatResourcecatalogBean resourceCatalog = new XnatResourcecatalogBean();
//
//
//                                resourceCatalog.setUri(Paths.get("SCANS", id, "params.txt").toString());
//                                resourceCatalog.setLabel("params.txt");
//                                resourceCatalog.setFormat("params");
//                                resourceCatalog.setContent("params");
//                                resourceCatalog.setDescription("params");
//
//                                CatCatalogBean catCatalogBean = new CatCatalogBean();
//
//                                Files.list(scanDir)
//                                        .map(this::createCatalogEntry)
//                                        .forEach(catCatalogBean::addEntries_entry);
//
//                                lookForSubject.addResources_resource(resourceCatalog);
//
//                                try (FileWriter resourceCatalogXmlWriter = new FileWriter(resourceCatalogXml)) {
//                                    catCatalogBean.toXML(resourceCatalogXmlWriter, true);
//                                } catch (IOException e) {
//                                    log.error("Unable to write scan catalog", e);
//                                }
                            }

                        }
                        if (zipEntry != null && zipEntry.isDirectory() && zipEntry.getName().contains("ses") && !zipEntry.getName().contains("__MACOSX")){
                            String[] paths = splitPath(zipEntry.getName());
                            String session = paths[paths.length-1];
                            String subject = paths[paths.length-2];
                            final String timestamp = PrearcUtils.makeTimestamp();
                            //after entering a session while the session is the same and the subject is the same create the corresponding folders needed in prearchive and copy contents
                            while (zipEntry != null && zipEntry.getName().contains(session) && zipEntry.getName().contains(subject)) {
                                if (zipEntry != null && zipEntry.isDirectory() && zipEntry.getName().contains("BLANK") && !zipEntry.getName().contains("__MACOSX")) {
                                    //It is a BLANK folder so get subject, session, and scan name details from path
                                    log.error("BLANK directory create scan and push files for dir" + zipEntry.getName());
                                    paths = splitPath(zipEntry.getName());
                                    String scanName = paths[paths.length - 2];
                                    session = paths[paths.length - 3];
                                    subject = paths[paths.length - 4];
                                    //Create subject session and prearchive folder to transfer data too
                                    XnatSubjectdata lookForSubject = XnatSubjectdata.GetSubjectByProjectIdentifier(projectId, subject, user, false);
                                    String subjectID = null;
                                    if (lookForSubject != null) {
                                        subjectID = createSubject(projectId, subject);
                                    }
                                    log.error(timestamp);
                                    Path prearchiveFolderPath = createPreArchiveFolder(projectId, subject, session, scanName, timestamp, Boolean.FALSE);
                                    createSession(projectId, subject, subjectID, session, scanName, timestamp, prearchiveFolderPath);

                                    //go next into the folder and get the scan files and write the data to the prearchive equavalent file
                                    zipEntry = zis.getNextEntry();
                                    //while you dont hit the next folder keep adding files to list of scan files
                                    while (zipEntry != null && !zipEntry.isDirectory()) {
                                        //if you see a params folder create the subject and transfer file to subject level file
                                        if (zipEntry.getName().contains("params") && zipEntry.isDirectory()) {
                                            log.error("params folder!!" + zipEntry.getName());
                                            log.error("Create subject" + subject);
                                            log.error("upload Params file" + zipEntry.getName());
                                            lookForSubject = XnatSubjectdata.GetSubjectByProjectIdentifier(projectId, subject, user,false);
                                            if (lookForSubject != null) {
                                                createSubject(URIManager.PROJECT_ID, subject);
                                            }
                                        }
                                        //otherwise it is scan data, start transfering the files
                                        else {
                                            log.error("pushing scan data to subject " + subject + "push data to scan " + scanName + "push data to session " + session);
                                            // Create file in the prearchive and write file details to the file in prearchive
                                            String fileName = zipEntry.getName();
                                            File f = new File(fileName);
                                            fileName = f.getName();
                                            Path finalPathForPrearchive = prearchiveFolderPath.resolve(fileName);

                                            if (Files.notExists(finalPathForPrearchive)) {
                                                timestampDirectories.add(prearchiveFolderPath); // Keep track of timestamp paths, will delete these folders in case of error
                                                log.error(String.valueOf(prearchiveFolderPath));
                                                Files.createDirectories(prearchiveFolderPath);
                                            }
                                            log.error(String.valueOf(finalPathForPrearchive));
                                            log.error(fileName);
                                            Files.createFile(finalPathForPrearchive);
                                            ZipEntryFileWriterWrapper zipEntryFileWriterWrapper = new ZipEntryFileWriterWrapper(zipEntry, zis);
                                            zipEntryFileWriterWrapper.write(finalPathForPrearchive.toFile());
                                        }
                                        zipEntry = zis.getNextEntry();
                                    }
                                }
                                if (zipEntry != null && zipEntry.isDirectory() && zipEntry.getName().contains("add")) {
                                    log.error("additional data directory create scan and push files" + zipEntry.getName());
                                    paths = splitPath(zipEntry.getName());
                                    String scanName = paths[paths.length - 2];
                                    session = paths[paths.length - 3];
                                    subject = paths[paths.length - 4];
                                    //Create subject session and prearchive folder to transfer data too
                                    XnatSubjectdata lookForSubject = XnatSubjectdata.GetSubjectByProjectIdentifier(projectId, subject, user, false);
                                    String subjectID = null;
                                    if (lookForSubject != null) {
                                        subjectID = createSubject(projectId, subject);
                                    }
                                    Path prearchiveFolderPath = createPreArchiveFolder(projectId, subject, session, scanName, timestamp, Boolean.TRUE);
                                    createSession(projectId, subject, subjectID, session, scanName, timestamp, prearchiveFolderPath);


                                    //go next into the folder and get the scan files and add it to the list
                                    zipEntry = zis.getNextEntry();
                                    //while you dont hit the next folder keep adding files to list of scan files
                                    while (zipEntry != null && !zipEntry.isDirectory()) {
                                        if (zipEntry.getName().contains("params")) {
                                            log.error("params file!!" + zipEntry.getName());
                                            log.error("Create subject" + subject);
                                            log.error("upload Params file" + zipEntry.getName());
                                        } else {
                                            log.error("push additional data to subject " + subject + "push data to scan " + scanName + "push data to session " + session);
                                            // Create file in the prearchive and write file details to the file in prearchive
                                            String fileName = zipEntry.getName();
                                            File f = new File(fileName);
                                            fileName = f.getName();

//                                    Path prearchiveFileWithSession = prearchiveTimestampPath.resolve(session);
//                                    Path prearchiveFileWithScansFolder = prearchiveFileWithSession.resolve("SCANS");
//                                    Path prearchiveFilewithScan = prearchiveFileWithScansFolder.resolve(scanName);
                                            Path finalPathForPrearchive = prearchiveFolderPath.resolve(fileName);

                                            if (Files.notExists(finalPathForPrearchive)) {
                                                timestampDirectories.add(prearchiveFolderPath); // Keep track of timestamp paths, will delete these folders in case of error
                                                Files.createDirectories(prearchiveFolderPath);
                                            }

                                            Files.createFile(finalPathForPrearchive);
                                            ZipEntryFileWriterWrapper zipEntryFileWriterWrapper = new ZipEntryFileWriterWrapper(zipEntry, zis);
                                            zipEntryFileWriterWrapper.write(finalPathForPrearchive.toFile());
                                        }
                                        zipEntry = zis.getNextEntry();
                                    }
                                }
                                zipEntry = zis.getNextEntry();
                            }
                        }
                    }

                    zipEntry = zis.getNextEntry();
                }

                zis.closeEntry();
                } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            ClientException e = new ClientException("Unsupported format " + format);
            log.error("Unsupported format: {}", format);
            throw e;
        }

        // Send a build request after all sessions have been imported
        sessions.forEach(this::sendSessionBuildRequest);

        return Lists.newArrayList(uris);
    }


    private String createSubject(String projectId, String subjectLabel) {
        final String subjectId;
        try {
            subjectId = XnatSubjectdata.CreateNewID();
        } catch (Exception e) {
            log.error("Unable to create subjectID for project " + projectId, e);
            return "ERROR";
        }

        log.info("Creating subject in project {} with ID {}", projectId, subjectId);
        final XnatSubjectdata subject = new XnatSubjectdata(user);
        subject.setId(subjectId);
        subject.setProject(projectId);
        subject.setLabel(subjectLabel);

        final PersistentWorkflowI workflow;
        final EventMetaI eventMeta;

        try {
            workflow = PersistentWorkflowUtils.buildOpenWorkflow(user, XnatSubjectdata.SCHEMA_ELEMENT_NAME, subjectLabel, projectId, EventUtils.newEventInstance(EventUtils.CATEGORY.DATA, EventUtils.TYPE.PROCESS, "Auto-created for project", "Created to support archiving image session", "Creating new subject " + subjectLabel));
            assert workflow != null;
            workflow.setStepDescription("Creating");
            eventMeta = workflow.buildEvent();
        } catch (Exception e) {
            log.error("Unable to create subject for project " + projectId, e);
            return "ERROR";
        }
        try {
            SaveItemHelper.authorizedSave(subject, user, false, false, eventMeta);
//            XDAT.triggerXftItemEvent(subject, CREATE);
            workflow.setStepDescription(PersistentWorkflowUtils.COMPLETE);
            WorkflowUtils.complete(workflow, eventMeta);
            log.info("Successfully create subject {} with ID {} in project {}", subjectLabel, subject.getId(), projectId);
        } catch (Exception e) {
            workflow.setStepDescription(PersistentWorkflowUtils.FAILED);
            try {
                WorkflowUtils.fail(workflow, eventMeta);
            } catch (Exception ex) {
                log.error("Unable to fail workflow for project " + projectId, ex);
            }
            log.error("Unable to create subject for project " + projectId, e);
        }
        return subjectId;
    }

    private Path createPreArchiveFolder(String projectId, String subject,String sessionLabel, String scanLabel, String atimestamp, Boolean addFolder) throws IOException {
        // Create prearchive timestamp
        log.error(atimestamp.toString());
        Path prearchiveTimestampPath = Paths.get(ArcSpecManager.GetInstance().getGlobalPrearchivePath(), projectId, atimestamp.toString());
        String sessionFolderName = subject.trim() + "_".trim() + sessionLabel.trim();
        Path sessionFolder = Paths.get(prearchiveTimestampPath.toString(), sessionFolderName);
        Path prearchiveScanFolderPath = Paths.get(sessionFolder.toString(), "SCANS", scanLabel);
        // mkdir if it doesnt exist
        if (addFolder){
            prearchiveScanFolderPath = Paths.get(prearchiveScanFolderPath.toString(), "Additional Data");
        }
        if (Files.notExists(prearchiveScanFolderPath)) {
            Files.createDirectories(prearchiveScanFolderPath);
        }
        return prearchiveScanFolderPath;
    }

    private void createSession(String projectId, String subjectlabel ,String subjectId, String sessionLabel, String scanLabel, String timestamp, Path prearchiveTimestampPath) throws ServerException {
        SessionData session = new SessionData();
        String sessionFolderName = subjectlabel.trim() + "_".trim() + sessionLabel.trim();
        session.setFolderName(sessionFolderName);
        //        session.setName(sessionLabel);
        session.setName(sessionFolderName);
        session.setProject(projectId);
        session.setUploadDate(uploadDate);
        session.setTimestamp(timestamp);
        session.setStatus(PrearcUtils.PrearcStatus.RECEIVING);
        session.setLastBuiltDate(Calendar.getInstance().getTime());
        session.setSubject(subjectlabel);
        session.setSource(params.get(URIManager.SOURCE));
        session.setPreventAnon(Boolean.valueOf((String) params.get(URIManager.PREVENT_ANON)));
        session.setPreventAutoCommit(Boolean.valueOf((String) params.get(URIManager.PREVENT_AUTO_COMMIT)));
        session.setAutoArchive(shouldAutoArchive(projectId));


        Optional<SessionData> matchingSession = sessions.stream().filter(s -> s.getProject().equals(session.getProject()) &&
                s.getFolderName().equals(session.getFolderName()) &&
                s.getName().equals(session.getName()) &&
                s.getSubject().equals(session.getSubject()) &&
                (!s.getName().equalsIgnoreCase(UNKNOWN_SESSION_LABEL) ||
                        !session.getName().equalsIgnoreCase(UNKNOWN_SESSION_LABEL))).findAny();

        Path sessionFolder = Paths.get(prearchiveTimestampPath.toString(), sessionFolderName);
//        Path scanFolder = Paths.get(sessionFolder.toString(), "SCANS", scanLabel);

        if (matchingSession.isPresent()) {
//            scanFolder = Paths.get(matchingSession.get().getUrl(), "SCANS", scanLabel);
            log.error("Session exists");
            // No need for PrearcDatabase.addSession(), session should have already been added
        } else {
            session.setUrl(sessionFolder.toString());
            try {
                PrearcDatabase.addSession(session);
                log.error("Added session to prearchive database. Project: {} Subject: {} Session: {} Scan: {}", projectId, subjectId, sessionLabel, scanLabel);
            } catch (Exception e) {
                log.error("Unable to add BLANK session", e);
                throw new ServerException(e);
            }
        }
//        session.setUrl(sessionFolder.toString());
//        try {
//            PrearcDatabase.addSession(session);
//            log.error("Added session to prearchive database. Project: {} Subject: {} Session: {} Scan: {}", projectId, subjectId, sessionLabel, scanLabel);
//        } catch (Exception e) {
//            log.error("Unable to add BLANK session", e);
//            throw new ServerException(e);
//        }
        sessions.add(session);
        uris.add(sessionFolder.toString());
    }

    private PrearchiveCode shouldAutoArchive(final String projectId) {
        if (params.containsKey("dest")) {
            if (params.get("dest").equals("/prearchive")) {
                return PrearchiveCode.Manual;
            } else if (params.get("dest").equals("/archive")) {
                return PrearchiveCode.AutoArchive;
            }
        }

        if (null == projectId) {
            return null;
        }

        XnatProjectdata project = XnatProjectdata.getXnatProjectdatasById(projectId, user, false);

        if (project == null) {
            return null;
        }

        ArcProject arcProject = project.getArcSpecification();
        if (arcProject == null) {
            log.warn("Tried to get the arc project from project {}, but got null in return. Returning null for the " +
                             "prearchive code, but it's probably not good that the arc project wasn't found.", project.getId());
            return null;
        }

        return PrearchiveCode.code(arcProject.getPrearchiveCode());
    }

    private void sendSessionBuildRequest(SessionData sessionData) {
        try {
            final File sessionDir = PrearcUtils.getPrearcSessionDir(user, sessionData.getProject(), sessionData.getTimestamp(), sessionData.getFolderName(), false);
            XDAT.sendJmsRequest(new PrearchiveOperationRequest(user, Rebuild, sessionData, sessionDir));
        } catch (Exception e) {
            log.info("Unable to request session build. Sitewide prearchive settings will be used instead.");
        }
    }

    public void setSubscriptionDeliveryEntityService(final SubscriptionDeliveryEntityService subscriptionDeliveryEntityService){
        this.subscriptionDeliveryEntityService = subscriptionDeliveryEntityService;
    }
}

package com.uploadpoc.core.workflows;

import com.adobe.granite.asset.api.AssetManager;
import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import java.util.Calendar;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = WorkflowProcess.class,
        property = {"process.label=Move Asset Based On Metadata"})
public class MoveAssetBasedOnMetadataProcess implements WorkflowProcess {
    private static final Logger LOG =
            LoggerFactory.getLogger(MoveAssetBasedOnMetadataProcess.class);

    private static final String ROOT_FOLDER = "/content/dam/cartology_2026";
    private static final String METADATA_NODE = "jcr:content/metadata";
    private static final String CHANNEL = "channel";
    private static final String BOOKING_ID = "bookingId";
    private static final String START_DATE = "startDate";

    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession,
                        MetaDataMap metaDataMap) throws WorkflowException {
        String assetPath = workItem.getWorkflowData().getPayload().toString();

        LOG.info("==================================================");
        LOG.info("Move Asset Workflow Started");
        LOG.info("Asset Path : {}", assetPath);
        LOG.info("==================================================");

        try {
            ResourceResolver resolver =
                    workflowSession.adaptTo(ResourceResolver.class);

            if (resolver == null) {
                throw new WorkflowException("Unable to obtain ResourceResolver");
            }

            Resource assetResource = resolver.getResource(assetPath);

            if (assetResource == null) {
                LOG.error("Asset not found : {}", assetPath);
                return;
            }

            Resource metadataResource = assetResource.getChild(METADATA_NODE);

            if (metadataResource == null) {
                LOG.error("Metadata node not found for asset : {}", assetPath);
                return;
            }

            ValueMap metadata = metadataResource.getValueMap();
            String channel = metadata.get(CHANNEL, String.class);
            String bookingId = metadata.get(BOOKING_ID, String.class);
            Calendar startDate = metadata.get(START_DATE, Calendar.class);

            LOG.debug("Metadata Values");
            LOG.debug("Channel   : {}", channel);
            LOG.debug("BookingId : {}", bookingId);
            LOG.debug("StartDate : {}", startDate);

            if (isBlank(channel) || isBlank(bookingId) || startDate == null) {
                LOG.error("Mandatory metadata missing. "
                                + "channel={}, bookingId={}, startDate={}",
                        channel, bookingId, startDate);

                return;
            }

            int year = startDate.get(Calendar.YEAR);
            int month = startDate.get(Calendar.MONTH) + 1;
            int day = startDate.get(Calendar.DAY_OF_MONTH);

            String targetFolder = String.format("%s/%s/%d/%02d/%02d/%s", ROOT_FOLDER,
                    sanitize(channel), year, month, day, sanitize(bookingId));

            LOG.info("Resolved Target Folder : {}", targetFolder);

            createDamFolderHierarchy(resolver, targetFolder);
            String assetName = assetPath.substring(assetPath.lastIndexOf("/") + 1);
            String destinationPath = targetFolder + "/" + assetName;

            LOG.info("Source Path      : {}", assetPath);
            LOG.info("Destination Path : {}", destinationPath);

            if (resolver.getResource(destinationPath) != null) {
                LOG.warn("Asset already exists at destination : {}", destinationPath);

                return;
            }

            AssetManager assetManager = resolver.adaptTo(AssetManager.class);

            if (assetManager == null) {
                throw new WorkflowException("Unable to adapt AssetManager");
            }

            LOG.debug("Moving asset...");

            assetManager.moveAsset(assetPath, destinationPath);
            resolver.commit();

            LOG.info("======================================");
            LOG.info("Asset moved successfully");
            LOG.info("Source      : {}", assetPath);
            LOG.info("Destination : {}", destinationPath);
            LOG.info("======================================");

        } catch (Exception e) {
            LOG.error("Error while processing asset {}", assetPath, e);
            throw new WorkflowException("Asset move workflow failed", e);
        }
    }

    /**
     * Creates DAM folder hierarchy if missing.
     */
    private void createDamFolderHierarchy(
            ResourceResolver resolver, String targetFolderPath)
            throws RepositoryException, PersistenceException {
        Session session = resolver.adaptTo(Session.class);

        if (session == null) {
            throw new RepositoryException("Unable to obtain JCR Session");
        }

        String relativePath = targetFolderPath.replace(ROOT_FOLDER, "");
        String[] folders = relativePath.split("/");
        String currentPath = ROOT_FOLDER;

        for (String folderName : folders) {
            if (folderName == null || folderName.trim().isEmpty()) {
                continue;
            }

            currentPath += "/" + folderName;
            Resource existingFolder = resolver.getResource(currentPath);

            if (existingFolder != null) {
                LOG.debug("Folder already exists : {}", currentPath);
                continue;
            }

            LOG.debug("Creating folder : {}", currentPath);

            String parentPath =
                    currentPath.substring(0, currentPath.lastIndexOf('/'));
            Resource parentResource = resolver.getResource(parentPath);

            if (parentResource == null) {
                throw new RepositoryException(
                        "Parent folder does not exist : " + parentPath);
            }

            Node parentNode = parentResource.adaptTo(Node.class);

            if (parentNode == null) {
                throw new RepositoryException(
                        "Unable to adapt parent resource to node : " + parentPath);
            }

            Node folderNode = parentNode.addNode(folderName, "sling:Folder");
            Node contentNode = folderNode.addNode("jcr:content", "nt:unstructured");
            contentNode.setProperty("jcr:title", folderName);
            contentNode.setProperty("dam:folderThumbnailPath", "");

            LOG.debug("Created folder : {}", currentPath);
        }

        session.save();
    }

    private String sanitize(String value) {
        return value.trim().replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
package com.uploadpoc.core.cartology.workflow;

import com.adobe.granite.asset.api.AssetManager;
import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.uploadpoc.core.cartology.model.ValidationResult;
import com.uploadpoc.core.cartology.validator.CartologyFilenameValidator;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AEM DAM workflow process step that validates the asset filename against
 * configured Cartology naming rules.
 * <p>
 * <b>On success:</b> Sets {@code cartology:validationStatus = VALID} on the
 * asset metadata and the workflow continues.
 * <p>
 * <b>On failure:</b> Sets {@code cartology:validationStatus = INVALID},
 * stores the error in {@code cartology:validationError}, and updates the
 * asset's {@code dc:title} with the error message so it is visible in the
 * DAM UI. The workflow step continues (does <em>not</em> throw
 * {@code WorkflowException}).
 */
@Component(service = WorkflowProcess.class,
        property = {"process.label=Cartology Filename Validation"})
public class CartologyFilenameValidationProcess implements WorkflowProcess {

    private static final Logger LOG =
            LoggerFactory.getLogger(CartologyFilenameValidationProcess.class);

    private static final String METADATA_NODE = "jcr:content/metadata";
    private static final String PROP_VALIDATION_STATUS = "cartology:validationStatus";
    private static final String PROP_VALIDATION_ERROR = "cartology:validationError";
    private static final String PROP_DC_TITLE = "dc:title";
    private static final String DESTINATION_ROOT =
            "/content/dam/woolworths-mrm/cartology/templates";

    @Reference
    private CartologyFilenameValidator filenameValidator;

    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession,
                        MetaDataMap metaDataMap) throws WorkflowException {

        String assetPath = workItem.getWorkflowData().getPayload().toString();

        LOG.info("Cartology filename validation started for: {}", assetPath);

        try {
            ResourceResolver resolver = workflowSession.adaptTo(ResourceResolver.class);
            if (resolver == null) {
                throw new WorkflowException("Unable to obtain ResourceResolver");
            }

            // Extract filename from path
            String filename = assetPath.substring(assetPath.lastIndexOf('/') + 1);

            // Validate
            ValidationResult result = filenameValidator.validate(filename);
            LOG.info("Cartology filename validation result: source={}, result={}",
                    assetPath, result);

            // Get metadata resource
            Resource assetResource = resolver.getResource(assetPath);
            if (assetResource == null) {
                LOG.error("Asset not found: {}", assetPath);
                return;
            }

            Resource metadataResource = assetResource.getChild(METADATA_NODE);
            if (metadataResource == null) {
                LOG.error("Metadata node not found for asset: {}", assetPath);
                return;
            }

            ModifiableValueMap metadata = metadataResource.adaptTo(ModifiableValueMap.class);
            if (metadata == null) {
                LOG.error("Unable to get ModifiableValueMap for: {}", assetPath);
                return;
            }

            boolean valid = result.isValid();
            if (valid) {
                // --- VALID ---
                metadata.put(PROP_VALIDATION_STATUS, "VALID");
                metadata.remove(PROP_VALIDATION_ERROR);

                LOG.info("Cartology filename validation PASSED: asset={}, channel={}, "
                                + "campaignType={}, mediaFormat={}",
                        filename, result.getChannel(), result.getCampaignType(),
                        result.getMediaFormat());

                moveValidAsset(resolver, assetPath, filename, result);

            } else {
                // --- INVALID ---
                String errorMessage = "[INVALID] " + result.getErrorCode()
                        + ": " + result.getMessage();

                metadata.put(PROP_VALIDATION_STATUS, "INVALID");
                metadata.put(PROP_VALIDATION_ERROR, result.getMessage());

                // Update dc:title with error message for DAM UI visibility
                metadata.put(PROP_DC_TITLE, errorMessage);

                LOG.warn("Cartology filename validation FAILED: asset={}, errorCode={}, "
                                + "message={}",
                        filename, result.getErrorCode(), result.getMessage());
            }

            resolver.commit();

        } catch (PersistenceException e) {
            LOG.error("Failed to persist validation metadata for asset: {}", assetPath, e);
            throw new WorkflowException("Failed to persist validation metadata", e);
        } catch (WorkflowException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Unexpected error during filename validation for: {}", assetPath, e);
            throw new WorkflowException("Filename validation failed unexpectedly", e);
        }
    }

    private void moveValidAsset(ResourceResolver resolver, String assetPath,
                                String filename, ValidationResult result)
            throws WorkflowException, RepositoryException, PersistenceException {
        String channel = requirePathSegment(result.getChannel(), "channel");
        String mediaFormat = requirePathSegment(result.getMediaFormat(), "mediaFormat");
        String channelFolder = DESTINATION_ROOT + "/" + channel;
        String destinationFolder = channelFolder + "/" + mediaFormat;
        String destinationPath = destinationFolder + "/" + filename;

        LOG.info("Cartology asset move: source={}, destinationFolder={}, "
                        + "finalDestination={}",
                assetPath, destinationFolder, destinationPath);

        Resource existingDestination = resolver.getResource(destinationPath);
        if (existingDestination != null) {
            LOG.warn("Cartology asset move skipped because destination already exists: {}",
                    destinationPath);
            return;
        }

        createDamFolderHierarchy(resolver, channelFolder, destinationFolder);

        existingDestination = resolver.getResource(destinationPath);
        if (existingDestination != null) {
            LOG.warn("Cartology asset move skipped because destination was created concurrently: {}",
                    destinationPath);
            return;
        }

        AssetManager assetManager = resolver.adaptTo(AssetManager.class);
        if (assetManager == null) {
            throw new WorkflowException("Unable to adapt AssetManager for asset move: "
                    + assetPath);
        }

        assetManager.moveAsset(assetPath, destinationPath);
        LOG.info("Cartology asset moved: source={}, finalDestination={}",
                assetPath, destinationPath);
    }

    private void createDamFolderHierarchy(ResourceResolver resolver,
                                          String channelFolder,
                                          String destinationFolder)
            throws RepositoryException {
        Session session = resolver.adaptTo(Session.class);
        if (session == null) {
            throw new RepositoryException("Unable to obtain JCR Session for folder creation");
        }

        String[] folders = {
            channelFolder.substring(DESTINATION_ROOT.length() + 1),
            destinationFolder.substring(channelFolder.length() + 1)
        };
        String currentPath = DESTINATION_ROOT;

        if (resolver.getResource(currentPath) == null) {
            throw new RepositoryException("Destination root folder does not exist: "
                    + DESTINATION_ROOT);
        }

        for (String folderName : folders) {
            currentPath += "/" + folderName;
            Resource existingFolder = resolver.getResource(currentPath);
            if (existingFolder != null) {
                continue;
            }

            String parentPath = currentPath.substring(0, currentPath.lastIndexOf('/'));
            Resource parentResource = resolver.getResource(parentPath);
            if (parentResource == null) {
                throw new RepositoryException("Parent folder does not exist: " + parentPath);
            }

            Node parentNode = parentResource.adaptTo(Node.class);
            if (parentNode == null) {
                throw new RepositoryException("Unable to adapt parent folder to JCR Node: "
                        + parentPath);
            }

                Node folderNode = parentNode.addNode(folderName, "sling:Folder");
            Node contentNode = folderNode.addNode("jcr:content", "nt:unstructured");
            contentNode.setProperty("jcr:title", folderName);
            contentNode.setProperty("dam:folderThumbnailPath", "");
            LOG.info("Created Cartology destination folder: {}", currentPath);
        }
    }

    private String requirePathSegment(String value, String name) throws WorkflowException {
        if (isBlank(value) || value.contains("/") || value.contains("\\")
                || ".".equals(value) || "..".equals(value)) {
            throw new WorkflowException("Invalid Cartology " + name
                    + " returned by filename validation: '" + value + "'");
        }
        return value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

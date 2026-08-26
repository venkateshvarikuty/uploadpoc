package com.uploadpoc.core.cartology.workflow;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.uploadpoc.core.cartology.model.ValidationResult;
import com.uploadpoc.core.cartology.validator.CartologyFilenameValidator;

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

            if (result.isValid()) {
                // --- VALID ---
                metadata.put(PROP_VALIDATION_STATUS, "VALID");
                metadata.remove(PROP_VALIDATION_ERROR);

                LOG.info("Cartology filename validation PASSED: asset={}, channel={}, "
                                + "campaignType={}, mediaFormat={}",
                        filename, result.getChannel(), result.getCampaignType(),
                        result.getMediaFormat());

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
}

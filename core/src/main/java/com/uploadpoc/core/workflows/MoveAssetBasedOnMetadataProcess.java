package com.uploadpoc.core.workflows;

import com.adobe.granite.asset.api.AssetManager;
import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
        service = WorkflowProcess.class,
        property = {
                "process.label=Move Asset Based On Metadata"
        })
public class MoveAssetBasedOnMetadataProcess
        implements WorkflowProcess {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    MoveAssetBasedOnMetadataProcess.class);

    @Override
    public void execute(
            WorkItem workItem,
            WorkflowSession workflowSession,
            MetaDataMap metaDataMap)
            throws WorkflowException {

        String assetPath =
                workItem.getWorkflowData()
                        .getPayload()
                        .toString();

        LOG.info("=================================================");
        LOG.info("Move Asset Workflow Started");
        LOG.info("Asset Path : {}", assetPath);
        LOG.info("=================================================");

        try {

            ResourceResolver resolver =
                    workflowSession.adaptTo(ResourceResolver.class);

            if (resolver == null) {
                LOG.error("ResourceResolver is null");
                return;
            }

            Resource assetResource =
                    resolver.getResource(assetPath);

            if (assetResource == null) {
                LOG.error(
                        "Asset resource not found for path {}",
                        assetPath);
                return;
            }

            LOG.info("Asset resource found");

            Resource metadataResource =
                    assetResource.getChild(
                            "jcr:content/metadata");

            if (metadataResource == null) {
                LOG.error(
                        "Metadata node not found for asset {}",
                        assetPath);
                return;
            }

            LOG.info("Metadata node found");

            String assetType =
                    metadataResource.getValueMap()
                            .get("assetType", String.class);

            LOG.info(
                    "Metadata assetType value : {}",
                    assetType);

            if (assetType == null || assetType.isEmpty()) {
                LOG.warn(
                        "assetType metadata is empty for {}",
                        assetPath);
                return;
            }

            String targetFolder =
                    getTargetFolder(assetType);

            LOG.info(
                    "Resolved target folder : {}",
                    targetFolder);

            if (targetFolder == null) {

                LOG.warn(
                        "No target folder mapping found for assetType {}",
                        assetType);

                return;
            }

            String assetName =
                    assetPath.substring(
                            assetPath.lastIndexOf("/") + 1);

            LOG.info("Asset Name : {}", assetName);

            String destination =
                    targetFolder + "/" + assetName;

            LOG.info(
                    "Source Path      : {}",
                    assetPath);

            LOG.info(
                    "Destination Path : {}",
                    destination);

            Resource destinationResource =
                    resolver.getResource(targetFolder);

            if (destinationResource == null) {

                LOG.error(
                        "Target folder does not exist {}",
                        targetFolder);

                return;
            }

            AssetManager assetManager =
                    resolver.adaptTo(
                            AssetManager.class);

            if (assetManager == null) {

                LOG.error(
                        "AssetManager adaptation failed");

                return;
            }

            LOG.info(
                    "Initiating asset move operation");

            assetManager.moveAsset(
                    assetPath,
                    destination);

            resolver.commit();

            LOG.info(
                    "Asset moved successfully");

            LOG.info(
                    "Moved From : {}",
                    assetPath);

            LOG.info(
                    "Moved To   : {}",
                    destination);

            LOG.info(
                    "Move Asset Workflow Completed Successfully");

        } catch (Exception e) {

            LOG.error(
                    "Exception occurred while moving asset {}",
                    assetPath,
                    e);

            throw new WorkflowException(e);
        }
    }

    private String getTargetFolder(
            String assetType) {

        LOG.debug(
                "Resolving target folder for assetType {}",
                assetType);

        switch (assetType.toLowerCase()) {

            case "brand":
                return "/content/dam/brands";

            case "product":
                return "/content/dam/products";

            case "marketing":
                return "/content/dam/marketing";

            default:
                LOG.warn(
                        "Unsupported assetType {}",
                        assetType);
                return null;
        }
    }
}
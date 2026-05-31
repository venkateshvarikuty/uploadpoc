package com.uploadpoc.core.workflows;

import com.adobe.granite.asset.api.AssetManager;
import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

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

    private static final String[] ASSET_TYPES = {
            "brand",
            "product",
            "marketing"
    };

    @Activate
    protected void activate() {
        LOG.info(
                "MoveAssetBasedOnMetadataProcess activated successfully");
    }

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
        LOG.info("Payload Path : {}", assetPath);
        LOG.info("=================================================");

        try {

            ResourceResolver resolver =
                    workflowSession.adaptTo(
                            ResourceResolver.class);

            if (resolver == null) {
                LOG.error("ResourceResolver is null");
                return;
            }

            LOG.info("Successfully obtained ResourceResolver");

            Resource assetResource =
                    resolver.getResource(assetPath);

            if (assetResource == null) {

                LOG.error(
                        "Asset resource not found for path {}",
                        assetPath);

                return;
            }

            LOG.info(
                    "Asset resource found : {}",
                    assetResource.getPath());

            Resource metadataResource =
                    assetResource.getChild(
                            "jcr:content/metadata");

            if (metadataResource == null) {

                LOG.warn(
                        "Metadata node not found. Continuing with random asset type.");

            } else {

                LOG.info(
                        "Metadata resource found : {}",
                        metadataResource.getPath());
            }

            Random random = new Random();

            String assetType =
                    ASSET_TYPES[random.nextInt(
                            ASSET_TYPES.length)];

            LOG.info(
                    "Randomly selected assetType : {}",
                    assetType);

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

            Resource targetFolderResource =
                    resolver.getResource(targetFolder);

            if (targetFolderResource == null) {

                LOG.error(
                        "Target folder does not exist : {}",
                        targetFolder);

                return;
            }

            String assetName =
                    assetPath.substring(
                            assetPath.lastIndexOf("/") + 1);

            LOG.info(
                    "Asset name : {}",
                    assetName);

            String destinationPath =
                    targetFolder + "/" + assetName;

            LOG.info(
                    "Source Path      : {}",
                    assetPath);

            LOG.info(
                    "Destination Path : {}",
                    destinationPath);

            AssetManager assetManager =
                    resolver.adaptTo(
                            AssetManager.class);

            if (assetManager == null) {

                LOG.error(
                        "Unable to adapt ResourceResolver to AssetManager");

                return;
            }

            LOG.info(
                    "Initiating move operation");

            assetManager.moveAsset(
                    assetPath,
                    destinationPath);

            resolver.commit();

            LOG.info("====================================");
            LOG.info("Asset moved successfully");
            LOG.info("Moved From : {}", assetPath);
            LOG.info("Moved To   : {}", destinationPath);
            LOG.info("Workflow completed successfully");
            LOG.info("====================================");

        } catch (Exception e) {

            LOG.error(
                    "Exception occurred while processing asset {}",
                    assetPath,
                    e);

            throw new WorkflowException(
                    "Error while moving asset",
                    e);
        }
    }

    private String getTargetFolder(
            String assetType) {

        LOG.info(
                "Resolving target folder for assetType : {}",
                assetType);

        switch (assetType.toLowerCase()) {

            case "brand":
                return "/content/dam/uploadpoc/brands";

            case "product":
                return "/content/dam/uploadpoc/products";

            case "marketing":
                return "/content/dam/uploadpoc/marketing";

            default:

                LOG.warn(
                        "Unsupported assetType : {}",
                        assetType);

                return "/content/dam/uploadpoc/others";
        }
    }
}
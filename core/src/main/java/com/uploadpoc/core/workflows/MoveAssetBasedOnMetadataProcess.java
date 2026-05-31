package com.uploadpoc.core.workflows;

import com.adobe.granite.asset.api.AssetManager;
import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.adobe.granite.workflow.WorkflowSession;

import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import org.osgi.service.component.annotations.Component;

@Component(
        service = WorkflowProcess.class,
        property = {
                "process.label=Move Asset Based On Metadata"
        }
)
public class MoveAssetBasedOnMetadataProcess
        implements WorkflowProcess {

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

        ResourceResolver resolver =
                workflowSession.adaptTo(ResourceResolver.class);

        Resource assetResource =
                resolver.getResource(assetPath);

        if (assetResource == null) {
            return;
        }

        Resource metadataResource =
                assetResource.getChild(
                        "jcr:content/metadata");

        if (metadataResource == null) {
            return;
        }

        String assetType =
                metadataResource.getValueMap()
                        .get("assetType", "");

        String targetFolder =
                getTargetFolder(assetType);

        if (targetFolder == null) {
            return;
        }

        String assetName =
                assetPath.substring(
                        assetPath.lastIndexOf("/") + 1);

        String destination =
                targetFolder + "/" + assetName;

        AssetManager assetManager =
                resolver.adaptTo(AssetManager.class);

        assetManager.moveAsset(
                assetPath,
                destination);

        try {
            resolver.commit();
        } catch (PersistenceException e) {
            throw new RuntimeException(e);
        }
    }

    private String getTargetFolder(
            String assetType) {

        switch (assetType) {

            case "brand":
                return "/content/dam/brands";

            case "product":
                return "/content/dam/products";

            case "marketing":
                return "/content/dam/marketing";

            default:
                return "/content/dam/wknd/en/site/workflow-upload";
        }
    }
}
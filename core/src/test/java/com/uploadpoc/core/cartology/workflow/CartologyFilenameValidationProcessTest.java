package com.uploadpoc.core.cartology.workflow;

import com.adobe.granite.asset.api.AssetManager;
import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowData;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.uploadpoc.core.cartology.model.ValidationErrorCode;
import com.uploadpoc.core.cartology.model.ValidationResult;
import com.uploadpoc.core.cartology.validator.CartologyFilenameValidator;

import javax.jcr.Node;
import javax.jcr.Session;

import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartologyFilenameValidationProcessTest {

    private static final String SOURCE = "/content/dam/inbox/Fresh-Mag_Shelf-Wobbler.pdf";
    private static final String POS_SOURCE = "/content/dam/inbox/POS_Special_Shelf-Wobbler.pdf";
    private static final String ROOT =
            "/content/dam/woolworths-mrm/cartology/templates";
    private static final String CHANNEL = ROOT + "/Fresh-Mag";
    private static final String MEDIA_FORMAT = CHANNEL + "/Shelf Wobbler";
    private static final String DESTINATION = MEDIA_FORMAT + "/Fresh-Mag_Shelf-Wobbler.pdf";
    private static final String POS_CHANNEL = ROOT + "/POS";
    private static final String POS_MEDIA_FORMAT = POS_CHANNEL + "/Shelf Wobbler";
    private static final String POS_DESTINATION = POS_MEDIA_FORMAT + "/POS_Special_Shelf-Wobbler.pdf";

    @Mock
    private CartologyFilenameValidator filenameValidator;

    @Mock
    private WorkflowSession workflowSession;

    @Mock
    private WorkItem workItem;

    @Mock
    private WorkflowData workflowData;

    @Mock
    private MetaDataMap workflowMetadata;

    @Mock
    private ResourceResolver resolver;

    @Mock
    private Resource assetResource;

    @Mock
    private Resource metadataResource;

    @Mock
    private ModifiableValueMap assetMetadata;

    @Mock
    private Resource rootResource;

    @Mock
    private Resource channelResource;

    @Mock
    private Node rootNode;

    @Mock
    private Node channelNode;

    @Mock
    private Node folderNode;

    @Mock
    private Node contentNode;

    @Mock
    private Session session;

    @Mock
    private AssetManager assetManager;

    @InjectMocks
    private CartologyFilenameValidationProcess process;

    @BeforeEach
    void setUp() {
        when(workItem.getWorkflowData()).thenReturn(workflowData);
        when(workflowData.getPayload()).thenReturn(SOURCE);
        when(workflowSession.adaptTo(ResourceResolver.class)).thenReturn(resolver);
        lenient().when(resolver.getResource(SOURCE)).thenReturn(assetResource);
        when(assetResource.getChild("jcr:content/metadata")).thenReturn(metadataResource);
        when(metadataResource.adaptTo(ModifiableValueMap.class)).thenReturn(assetMetadata);
    }

    @Test
    void validFilenameCreatesFoldersAndMovesAsset() throws Exception {
        prepareValidResult();
        when(resolver.getResource(ROOT)).thenReturn(rootResource);
        when(resolver.getResource(CHANNEL)).thenReturn(null, channelResource);
        when(resolver.getResource(MEDIA_FORMAT)).thenReturn(null);
        when(resolver.getResource(DESTINATION)).thenReturn(null);
        when(resolver.adaptTo(Session.class)).thenReturn(session);
        when(rootResource.adaptTo(Node.class)).thenReturn(rootNode);
        when(channelResource.adaptTo(Node.class)).thenReturn(channelNode);
        when(rootNode.addNode("Fresh-Mag", "sling:Folder")).thenReturn(folderNode);
        when(folderNode.addNode("jcr:content", "nt:unstructured")).thenReturn(contentNode);
        when(channelNode.addNode("Shelf Wobbler", "sling:Folder")).thenReturn(folderNode);
        when(resolver.adaptTo(AssetManager.class)).thenReturn(assetManager);

        process.execute(workItem, workflowSession, workflowMetadata);

        verify(rootNode).addNode("Fresh-Mag", "sling:Folder");
        verify(channelNode).addNode("Shelf Wobbler", "sling:Folder");
        verify(assetManager).moveAsset(SOURCE, DESTINATION);
    }

    @Test
    void validFilenameReusesExistingFolders() throws Exception {
        prepareValidResult();
        when(resolver.getResource(ROOT)).thenReturn(rootResource);
        when(resolver.getResource(CHANNEL)).thenReturn(channelResource);
        when(resolver.getResource(MEDIA_FORMAT)).thenReturn(channelResource);
        when(resolver.getResource(DESTINATION)).thenReturn(null);
        when(resolver.adaptTo(Session.class)).thenReturn(session);
        when(resolver.adaptTo(AssetManager.class)).thenReturn(assetManager);

        process.execute(workItem, workflowSession, workflowMetadata);

        verify(assetManager).moveAsset(SOURCE, DESTINATION);
        verify(rootResource, never()).adaptTo(Node.class);
        verify(channelResource, never()).adaptTo(Node.class);
    }

    @Test
    void validPosFilenameMovesWithoutCampaignTypeFolder() throws Exception {
        when(workflowData.getPayload()).thenReturn(POS_SOURCE);
        when(filenameValidator.validate("POS_Special_Shelf-Wobbler.pdf"))
                .thenReturn(ValidationResult.success(
                        new com.uploadpoc.core.cartology.model.ParsedFilename(
                            "POS", "Special", "Shelf Wobbler", null, "pdf")));
        when(resolver.getResource(POS_SOURCE)).thenReturn(assetResource);
        when(resolver.getResource(ROOT)).thenReturn(rootResource);
        when(resolver.getResource(POS_CHANNEL)).thenReturn(null, channelResource);
        when(resolver.getResource(POS_MEDIA_FORMAT)).thenReturn(null);
        when(resolver.getResource(POS_DESTINATION)).thenReturn(null);
        when(resolver.adaptTo(Session.class)).thenReturn(session);
        when(rootResource.adaptTo(Node.class)).thenReturn(rootNode);
        when(channelResource.adaptTo(Node.class)).thenReturn(channelNode);
        when(rootNode.addNode("POS", "sling:Folder")).thenReturn(folderNode);
        when(folderNode.addNode("jcr:content", "nt:unstructured")).thenReturn(contentNode);
        when(channelNode.addNode("Shelf Wobbler", "sling:Folder")).thenReturn(folderNode);
        when(resolver.adaptTo(AssetManager.class)).thenReturn(assetManager);

        process.execute(workItem, workflowSession, workflowMetadata);

        verify(rootNode).addNode("POS", "sling:Folder");
        verify(channelNode).addNode("Shelf Wobbler", "sling:Folder");
        verify(assetManager).moveAsset(POS_SOURCE, POS_DESTINATION);
    }

    @Test
    void invalidFilenameDoesNotMoveAsset() throws Exception {
        when(filenameValidator.validate("Fresh-Mag_Shelf-Wobbler.pdf"))
                .thenReturn(ValidationResult.failure(ValidationErrorCode.INVALID_FILENAME_FORMAT,
                        "Invalid filename"));

        process.execute(workItem, workflowSession, workflowMetadata);

        verify(assetMetadata).put("cartology:validationStatus", "INVALID");
        verify(assetMetadata).put("cartology:validationError", "Invalid filename");
        verify(resolver, never()).adaptTo(AssetManager.class);
    }

    @Test
    void missingChannelFailsWithoutMovingAsset() {
        when(filenameValidator.validate("Fresh-Mag_Shelf-Wobbler.pdf"))
                .thenReturn(ValidationResult.success(
                        new com.uploadpoc.core.cartology.model.ParsedFilename(
                            null, null, "Shelf Wobbler", "Fresh-Mag", "pdf")));

        assertThrows(WorkflowException.class,
                () -> process.execute(workItem, workflowSession, workflowMetadata));

        verify(resolver, never()).adaptTo(AssetManager.class);
    }

    @Test
    void existingDestinationAssetIsNotOverwritten() throws Exception {
        prepareValidResult();
        when(resolver.getResource(DESTINATION)).thenReturn(assetResource);

        process.execute(workItem, workflowSession, workflowMetadata);

        verify(resolver, never()).adaptTo(AssetManager.class);
        verify(assetMetadata).put("cartology:validationStatus", "VALID");
    }

    private void prepareValidResult() {
        when(filenameValidator.validate("Fresh-Mag_Shelf-Wobbler.pdf"))
                .thenReturn(ValidationResult.success(
                        new com.uploadpoc.core.cartology.model.ParsedFilename(
                            "Fresh-Mag", null, "Shelf Wobbler", "Fresh-Mag", "pdf")));
    }
}
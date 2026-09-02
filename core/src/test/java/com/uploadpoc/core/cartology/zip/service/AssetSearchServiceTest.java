package com.uploadpoc.core.cartology.zip.service;

import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.Rendition;
import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;
import com.uploadpoc.core.cartology.zip.config.CartologyAssetZipConfig;
import com.uploadpoc.core.cartology.zip.model.AssetZipRequest;
import com.uploadpoc.core.cartology.zip.service.AssetSearchService.MaxAssetsExceededException;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AssetSearchService}.
 */
@ExtendWith(MockitoExtension.class)
class AssetSearchServiceTest {

    @Mock
    private QueryBuilder queryBuilder;

    @Mock
    private ResourceResolverFactory resolverFactory;

    @Mock
    private ResourceResolver resourceResolver;

    @Mock
    private Session session;

    @Mock
    private Query query;

    @Mock
    private SearchResult searchResult;

    @InjectMocks
    private AssetSearchService assetSearchService;

    @BeforeEach
    void setUp() throws LoginException {
        CartologyAssetZipConfig config = mock(CartologyAssetZipConfig.class);
        when(config.damRootPath()).thenReturn("/content/dam/woolworths-mrm/cartology");
        when(config.channelProperty()).thenReturn("jcr:content/metadata/cartology:channel");
        when(config.campaignTypeProperty()).thenReturn("jcr:content/metadata/cartology:campaignType");
        when(config.mediaFormatProperty()).thenReturn("jcr:content/metadata/cartology:mediaFormat");
        when(config.assetReaderSubService()).thenReturn("cartology-asset-zip-reader");
        when(config.maxAssetsPerRequest()).thenReturn(500);
        assetSearchService.activate(config);

        when(resolverFactory.getServiceResourceResolver(anyMap())).thenReturn(resourceResolver);
        when(resourceResolver.adaptTo(Session.class)).thenReturn(session);
        when(queryBuilder.createQuery(any(PredicateGroup.class), any(Session.class)))
                .thenReturn(query);
        when(query.getResult()).thenReturn(searchResult);
    }

    @Test
    void findAssets_allFiltersSupplied() throws Exception {
        AssetZipRequest request = new AssetZipRequest("Retail Media", "Display", "Banner");

        Hit hit = mockHit("/content/dam/woolworths-mrm/cartology/au/banner.jpg");
        when(searchResult.getHits()).thenReturn(Collections.singletonList(hit));
        mockValidAsset("/content/dam/woolworths-mrm/cartology/au/banner.jpg");

        List<String> results = assetSearchService.findAssets(request);

        assertEquals(1, results.size());
        assertEquals("/content/dam/woolworths-mrm/cartology/au/banner.jpg", results.get(0));
    }

    @Test
    void findAssets_optionalCampaignTypeOmitted() throws Exception {
        AssetZipRequest request = new AssetZipRequest("Retail Media", null, "Banner");

        Hit hit = mockHit("/content/dam/woolworths-mrm/cartology/au/banner.jpg");
        when(searchResult.getHits()).thenReturn(Collections.singletonList(hit));
        mockValidAsset("/content/dam/woolworths-mrm/cartology/au/banner.jpg");

        List<String> results = assetSearchService.findAssets(request);

        assertEquals(1, results.size());
    }

    @Test
    void findAssets_noMatchingAssets() throws Exception {
        AssetZipRequest request = new AssetZipRequest("Retail Media", null, "Banner");

        when(searchResult.getHits()).thenReturn(Collections.emptyList());

        List<String> results = assetSearchService.findAssets(request);

        assertTrue(results.isEmpty());
    }

    @Test
    void findAssets_exceedsMaxLimit_throwsMaxAssetsExceededException() throws Exception {
        CartologyAssetZipConfig config = mock(CartologyAssetZipConfig.class);
        when(config.damRootPath()).thenReturn("/content/dam/woolworths-mrm/cartology");
        when(config.channelProperty()).thenReturn("jcr:content/metadata/cartology:channel");
        when(config.campaignTypeProperty()).thenReturn("jcr:content/metadata/cartology:campaignType");
        when(config.mediaFormatProperty()).thenReturn("jcr:content/metadata/cartology:mediaFormat");
        when(config.assetReaderSubService()).thenReturn("cartology-asset-zip-reader");
        when(config.maxAssetsPerRequest()).thenReturn(1);
        assetSearchService.activate(config);

        AssetZipRequest request = new AssetZipRequest("Retail Media", null, "Banner");

        Hit hit1 = mockHit("/content/dam/woolworths-mrm/cartology/au/banner1.jpg");
        Hit hit2 = mockHit("/content/dam/woolworths-mrm/cartology/au/banner2.jpg");
        when(searchResult.getHits()).thenReturn(Arrays.asList(hit1, hit2));

        assertThrows(MaxAssetsExceededException.class, () -> assetSearchService.findAssets(request));
    }

    @Test
    void findAssets_invalidAssetsExcluded() throws Exception {
        AssetZipRequest request = new AssetZipRequest("Retail Media", null, "Banner");

        Hit validHit = mockHit("/content/dam/woolworths-mrm/cartology/valid.jpg");
        Hit invalidHit = mockHit("/content/dam/woolworths-mrm/cartology/no-rendition.jpg");
        when(searchResult.getHits()).thenReturn(Arrays.asList(validHit, invalidHit));

        // Valid asset
        mockValidAsset("/content/dam/woolworths-mrm/cartology/valid.jpg");

        // Invalid asset — no original rendition
        Resource invalidResource = mock(Resource.class);
        Asset invalidAsset = mock(Asset.class);
        when(resourceResolver.getResource("/content/dam/woolworths-mrm/cartology/no-rendition.jpg"))
                .thenReturn(invalidResource);
        when(invalidResource.adaptTo(Asset.class)).thenReturn(invalidAsset);
        when(invalidAsset.getOriginal()).thenReturn(null);

        List<String> results = assetSearchService.findAssets(request);

        assertEquals(1, results.size());
        assertEquals("/content/dam/woolworths-mrm/cartology/valid.jpg", results.get(0));
    }

    @Test
    void findAssets_loginException_returnsEmptyList() throws Exception {
        when(resolverFactory.getServiceResourceResolver(anyMap()))
                .thenThrow(new LoginException("Service user not found"));

        AssetZipRequest request = new AssetZipRequest("Retail Media", null, "Banner");
        List<String> results = assetSearchService.findAssets(request);

        assertTrue(results.isEmpty());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Hit mockHit(String path) throws RepositoryException {
        Hit hit = mock(Hit.class);
        when(hit.getPath()).thenReturn(path);
        return hit;
    }

    private void mockValidAsset(String path) {
        Resource resource = mock(Resource.class);
        Asset asset = mock(Asset.class);
        Rendition original = mock(Rendition.class);
        when(resourceResolver.getResource(path)).thenReturn(resource);
        when(resource.adaptTo(Asset.class)).thenReturn(asset);
        when(asset.getOriginal()).thenReturn(original);
    }
}

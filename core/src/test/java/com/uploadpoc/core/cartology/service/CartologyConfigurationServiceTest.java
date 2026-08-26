package com.uploadpoc.core.cartology.service;

import com.uploadpoc.core.cartology.cache.CartologyConfigurationCache;
import com.uploadpoc.core.cartology.model.NamingRuleMapping;
import com.uploadpoc.core.cartology.repository.CartologyRepositoryService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CartologyConfigurationService}.
 */
@ExtendWith(MockitoExtension.class)
class CartologyConfigurationServiceTest {

    @Mock
    private CartologyConfigurationCache cache;

    @Mock
    private CartologyRepositoryService repositoryService;

    @InjectMocks
    private CartologyConfigurationService service;

    @Test
    void isValidMapping_delegatesToCache() {
        when(cache.isValidMapping("POS", "Special", "A3 Bin Card")).thenReturn(true);
        assertTrue(service.isValidMapping("POS", "Special", "A3 Bin Card"));
        verify(cache).isValidMapping("POS", "Special", "A3 Bin Card");
    }

    @Test
    void isValidMapping_nonExistent() {
        when(cache.isValidMapping("POS", "Seasonal", "A3 Bin Card")).thenReturn(false);
        assertFalse(service.isValidMapping("POS", "Seasonal", "A3 Bin Card"));
    }

    @Test
    void isChannelConfigured_delegatesToCache() {
        when(cache.isChannelConfigured("POS")).thenReturn(true);
        assertTrue(service.isChannelConfigured("POS"));
    }

    @Test
    void channelHasCampaignType_delegatesToCache() {
        when(cache.channelHasCampaignType("POS")).thenReturn(true);
        assertTrue(service.channelHasCampaignType("POS"));

        when(cache.channelHasCampaignType("Fresh Mag")).thenReturn(false);
        assertFalse(service.channelHasCampaignType("Fresh Mag"));
    }

    @Test
    void isCampaignTypeConfigured_delegatesToCache() {
        when(cache.isCampaignTypeConfigured("POS", "Special")).thenReturn(true);
        assertTrue(service.isCampaignTypeConfigured("POS", "Special"));
    }

    @Test
    void getMediaFormats_delegatesToCache() {
        Set<String> expected = new HashSet<>(Arrays.asList("A3-Bin-Card", "Shelf-Wobbler"));
        when(cache.getMediaFormats("POS", "Special")).thenReturn(expected);

        Set<String> result = service.getMediaFormats("POS", "Special");
        assertEquals(2, result.size());
        assertTrue(result.contains("A3-Bin-Card"));
    }

    @Test
    void getMediaFormats_empty() {
        when(cache.getMediaFormats("Online", null)).thenReturn(Collections.emptySet());
        assertTrue(service.getMediaFormats("Online", null).isEmpty());
    }

    @Test
    void getAllMappings_delegatesToRepository() {
        when(repositoryService.getAllMappings()).thenReturn(Arrays.asList(
                new NamingRuleMapping("POS", "Special", "A3 Bin Card")
        ));
        assertEquals(1, service.getAllMappings().size());
    }

    @Test
    void isCacheLoaded_delegatesToCache() {
        when(cache.isLoaded()).thenReturn(true);
        assertTrue(service.isCacheLoaded());
    }
}

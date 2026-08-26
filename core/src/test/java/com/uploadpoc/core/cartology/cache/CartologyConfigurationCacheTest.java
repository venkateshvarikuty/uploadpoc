package com.uploadpoc.core.cartology.cache;

import com.uploadpoc.core.cartology.model.NamingRuleMapping;
import com.uploadpoc.core.cartology.normalizer.CartologyNameNormalizer;
import com.uploadpoc.core.cartology.repository.CartologyRepositoryService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CartologyConfigurationCache}.
 */
@ExtendWith(MockitoExtension.class)
class CartologyConfigurationCacheTest {

    @Mock
    private CartologyRepositoryService repositoryService;

    @Spy
    private CartologyNameNormalizer nameNormalizer = new CartologyNameNormalizer();

    @InjectMocks
    private CartologyConfigurationCache cache;

    @BeforeEach
    void setUp() {
        when(repositoryService.getAllMappings()).thenReturn(Arrays.asList(
                new NamingRuleMapping("POS", "Special", "A3 Bin Card"),
                new NamingRuleMapping("POS", "Special", "Shelf Wobbler"),
                new NamingRuleMapping("POS", "Special", "Digital Screen"),
                new NamingRuleMapping("POS", "Everyday", "A3 Bin Card"),
                new NamingRuleMapping("Off Network", "Special", "A3 Bin Card"),
                new NamingRuleMapping("Off Network", "Seasonal", "Shelf Wobbler"),
                new NamingRuleMapping("Fresh Mag", null, "Full Page"),
                new NamingRuleMapping("Fresh Mag", null, "Half Page"),
                new NamingRuleMapping("Fresh Mag", null, "Fresh Mag")
        ));
        cache.activate();
    }

    @Test
    void isValidMapping_existingMapping() {
        assertTrue(cache.isValidMapping("POS", "Special", "A3 Bin Card"));
    }

    @Test
    void isValidMapping_existingMappingNormalisedInput() {
        // Input already normalised
        assertTrue(cache.isValidMapping("POS", "Special", "A3-Bin-Card"));
    }

    @Test
    void isValidMapping_nonExistent() {
        assertFalse(cache.isValidMapping("POS", "Seasonal", "A3 Bin Card"));
    }

    @Test
    void isValidMapping_channelWithoutCampaignType() {
        assertTrue(cache.isValidMapping("Fresh Mag", null, "Full Page"));
        assertTrue(cache.isValidMapping("Fresh-Mag", null, "Half-Page"));
    }

    @Test
    void isChannelConfigured_known() {
        assertTrue(cache.isChannelConfigured("POS"));
        assertTrue(cache.isChannelConfigured("Off Network"));
        assertTrue(cache.isChannelConfigured("Fresh Mag"));
    }

    @Test
    void isChannelConfigured_unknown() {
        assertFalse(cache.isChannelConfigured("Online"));
    }

    @Test
    void channelHasCampaignType_withCampaignType() {
        assertTrue(cache.channelHasCampaignType("POS"));
        assertTrue(cache.channelHasCampaignType("Off Network"));
    }

    @Test
    void channelHasCampaignType_withoutCampaignType() {
        assertFalse(cache.channelHasCampaignType("Fresh Mag"));
    }

    @Test
    void isCampaignTypeConfigured_known() {
        assertTrue(cache.isCampaignTypeConfigured("POS", "Special"));
        assertTrue(cache.isCampaignTypeConfigured("POS", "Everyday"));
    }

    @Test
    void isCampaignTypeConfigured_unknown() {
        assertFalse(cache.isCampaignTypeConfigured("POS", "Seasonal"));
    }

    @Test
    void getMediaFormats_withCampaignType() {
        Set<String> formats = cache.getMediaFormats("POS", "Special");
        assertEquals(3, formats.size());
        assertTrue(formats.contains("A3-Bin-Card"));
        assertTrue(formats.contains("Shelf-Wobbler"));
        assertTrue(formats.contains("Digital-Screen"));
    }

    @Test
    void getMediaFormats_withoutCampaignType() {
        Set<String> formats = cache.getMediaFormats("Fresh Mag", null);
        assertEquals(3, formats.size());
        assertTrue(formats.contains("Full-Page"));
        assertTrue(formats.contains("Half-Page"));
        assertTrue(formats.contains("Fresh-Mag"));
    }

    @Test
    void getMediaFormats_nonExistent() {
        Set<String> formats = cache.getMediaFormats("Online", null);
        assertTrue(formats.isEmpty());
    }

    @Test
    void getAllChannels() {
        Set<String> channels = cache.getAllChannels();
        assertEquals(3, channels.size());
        assertTrue(channels.contains("POS"));
        assertTrue(channels.contains("Off-Network"));
        assertTrue(channels.contains("Fresh-Mag"));
    }

    @Test
    void refresh_updatesCache() {
        // Initially POS/Seasonal doesn't exist
        assertFalse(cache.isValidMapping("POS", "Seasonal", "A3 Bin Card"));

        // Simulate 3rd party adding a new mapping
        when(repositoryService.getAllMappings()).thenReturn(Arrays.asList(
                new NamingRuleMapping("POS", "Special", "A3 Bin Card"),
                new NamingRuleMapping("POS", "Seasonal", "A3 Bin Card")
        ));
        cache.refresh();

        assertTrue(cache.isValidMapping("POS", "Seasonal", "A3 Bin Card"));
    }

    @Test
    void emptyMappings() {
        when(repositoryService.getAllMappings()).thenReturn(Collections.emptyList());
        cache.refresh();

        assertFalse(cache.isChannelConfigured("POS"));
        assertTrue(cache.getAllChannels().isEmpty());
    }
}

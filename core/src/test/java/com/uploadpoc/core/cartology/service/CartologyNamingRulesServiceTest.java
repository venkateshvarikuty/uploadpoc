package com.uploadpoc.core.cartology.service;

import com.uploadpoc.core.cartology.cache.CartologyConfigurationCache;
import com.uploadpoc.core.cartology.model.NamingRuleMapping;
import com.uploadpoc.core.cartology.normalizer.CartologyNameNormalizer;
import com.uploadpoc.core.cartology.repository.CartologyRepositoryService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.jcr.RepositoryException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CartologyNamingRulesService}.
 */
@ExtendWith(MockitoExtension.class)
class CartologyNamingRulesServiceTest {

    @Mock
    private CartologyRepositoryService repositoryService;

    @Mock
    private CartologyConfigurationCache configurationCache;

    @Spy
    private CartologyNameNormalizer nameNormalizer = new CartologyNameNormalizer();

    @InjectMocks
    private CartologyNamingRulesService service;

    /* ---------- createOrUpdateMapping ---------- */

    @Test
    void createOrUpdate_withCampaignType() throws Exception {
        service.createOrUpdateMapping("POS", "Special", "A3 Bin Card");

        verify(repositoryService).createMapping("POS", "Special", "A3-Bin-Card");
        verify(configurationCache).refresh();
    }

    @Test
    void createOrUpdate_withoutCampaignType() throws Exception {
        service.createOrUpdateMapping("Fresh Mag", null, "Full Page");

        verify(repositoryService).createMapping("Fresh-Mag", null, "Full-Page");
        verify(configurationCache).refresh();
    }

    @Test
    void createOrUpdate_emptyCampaignTypeTreatedAsNull() throws Exception {
        service.createOrUpdateMapping("Fresh Mag", "", "Half Page");

        verify(repositoryService).createMapping("Fresh-Mag", null, "Half-Page");
        verify(configurationCache).refresh();
    }

    @Test
    void createOrUpdate_normalises() throws Exception {
        service.createOrUpdateMapping("Off Network", "Seasonal", "Shelf Wobbler");

        verify(repositoryService).createMapping("Off-Network", "Seasonal", "Shelf-Wobbler");
    }

    @Test
    void createOrUpdate_idempotent() throws Exception {
        // Calling twice should not throw
        service.createOrUpdateMapping("POS", "Special", "A3 Bin Card");
        service.createOrUpdateMapping("POS", "Special", "A3 Bin Card");

        verify(repositoryService, times(2)).createMapping("POS", "Special", "A3-Bin-Card");
    }

    @Test
    void createOrUpdate_missingChannel() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createOrUpdateMapping(null, "Special", "A3 Bin Card"));
        assertThrows(IllegalArgumentException.class,
                () -> service.createOrUpdateMapping("", "Special", "A3 Bin Card"));
    }

    @Test
    void createOrUpdate_missingMediaFormat() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createOrUpdateMapping("POS", "Special", null));
        assertThrows(IllegalArgumentException.class,
                () -> service.createOrUpdateMapping("POS", "Special", ""));
    }

    /* ---------- deleteMapping ---------- */

    @Test
    void delete_existing() throws Exception {
        when(repositoryService.deleteMapping("POS", "Special", "A3-Bin-Card")).thenReturn(true);

        boolean result = service.deleteMapping("POS", "Special", "A3 Bin Card");

        assertTrue(result);
        verify(configurationCache).refresh();
    }

    @Test
    void delete_nonExistent() throws Exception {
        when(repositoryService.deleteMapping("POS", "Seasonal", "A3-Bin-Card")).thenReturn(false);

        boolean result = service.deleteMapping("POS", "Seasonal", "A3 Bin Card");

        assertFalse(result);
        verify(configurationCache).refresh();
    }

    @Test
    void delete_missingChannel() {
        assertThrows(IllegalArgumentException.class,
                () -> service.deleteMapping(null, "Special", "A3 Bin Card"));
    }

    @Test
    void delete_missingMediaFormat() {
        assertThrows(IllegalArgumentException.class,
                () -> service.deleteMapping("POS", "Special", null));
    }

    /* ---------- getAllMappings ---------- */

    @Test
    void getAllMappings_delegatesToRepository() {
        when(repositoryService.getAllMappings()).thenReturn(Arrays.asList(
                new NamingRuleMapping("POS", "Special", "A3 Bin Card")
        ));

        assertEquals(1, service.getAllMappings().size());
        verify(repositoryService).getAllMappings();
    }
}

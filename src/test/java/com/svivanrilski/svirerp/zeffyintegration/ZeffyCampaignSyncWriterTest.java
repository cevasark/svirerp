package com.svivanrilski.svirerp.zeffyintegration;

import com.svivanrilski.svirerp.finance.Account;
import com.svivanrilski.svirerp.finance.Fund;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ZeffyCampaignSyncWriterTest {

    private final Map<String, ZeffyCampaign> stored = new HashMap<>();
    private ZeffyCampaignSyncWriter writer;

    @BeforeEach
    void setUp() {
        ZeffyCampaignRepository repository = mock(ZeffyCampaignRepository.class);
        when(repository.findByZeffyCampaignId(any())).thenAnswer(invocation ->
                Optional.ofNullable(stored.get(invocation.getArgument(0))));
        when(repository.save(any())).thenAnswer(invocation -> {
            ZeffyCampaign campaign = invocation.getArgument(0);
            stored.put(campaign.getZeffyCampaignId(), campaign);
            return campaign;
        });
        writer = new ZeffyCampaignSyncWriter(repository);
    }

    @Test
    void repeatedSyncIsIdempotentAndRenamePreservesPolicy() {
        ZeffyApiModels.Campaign original = campaign("campaign-1", "Original title");
        ZeffyCampaignSyncWriter.Result first = writer.apply(java.util.List.of(original));
        ZeffyCampaign local = stored.get("campaign-1");
        local.setMappingConfirmed(true);
        local.setProcessingAction("APPLY");
        local.setFund(Fund.builder().fundName("General").fundCode("GEN").build());
        local.setCategoryAccount(Account.builder().accountNumber("4010").accountName("Donation").build());

        ZeffyCampaignSyncWriter.Result second = writer.apply(java.util.List.of(original));
        ZeffyCampaignSyncWriter.Result renamed = writer.apply(
                java.util.List.of(campaign("campaign-1", "Renamed campaign")));

        assertThat(first.inserted()).isEqualTo(1);
        assertThat(second.updated()).isZero();
        assertThat(second.ignored()).isEqualTo(1);
        assertThat(renamed.updated()).isEqualTo(1);
        assertThat(local.getTitle()).isEqualTo("Renamed campaign");
        assertThat(local.getMappingConfirmed()).isTrue();
        assertThat(local.getFund().getFundCode()).isEqualTo("GEN");
        assertThat(local.getCategoryAccount().getAccountNumber()).isEqualTo("4010");
    }

    private ZeffyApiModels.Campaign campaign(String id, String title) {
        return new ZeffyApiModels.Campaign(id, 1D, 2D, null, "donation_form", "donation",
                "active", title, null, "en", null, "USD", false);
    }
}

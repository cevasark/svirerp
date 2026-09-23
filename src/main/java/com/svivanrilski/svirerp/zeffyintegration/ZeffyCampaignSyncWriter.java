package com.svivanrilski.svirerp.zeffyintegration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ZeffyCampaignSyncWriter {

    private final ZeffyCampaignRepository repository;

    public record Result(int fetched, int inserted, int updated, int ignored, int failed) {
    }

    @Transactional
    public Result apply(List<ZeffyApiModels.Campaign> fetchedCampaigns) {
        Map<String, ZeffyApiModels.Campaign> unique = new LinkedHashMap<>();
        int failed = 0;
        int ignored = 0;
        for (ZeffyApiModels.Campaign remote : fetchedCampaigns) {
            if (remote == null || isBlank(remote.id()) || isBlank(remote.title()) || isBlank(remote.type())) {
                failed++;
                continue;
            }
            if (unique.put(remote.id(), remote) != null) ignored++;
        }

        int inserted = 0;
        int updated = 0;
        OffsetDateTime syncedAt = OffsetDateTime.now(ZoneOffset.UTC);

        for (ZeffyApiModels.Campaign remote : unique.values()) {
            ZeffyCampaign local = repository.findByZeffyCampaignId(remote.id()).orElse(null);
            boolean created = local == null;
            if (created) local = ZeffyCampaign.builder().zeffyCampaignId(remote.id()).build();

            OffsetDateTime remoteCreated = fromUnixSeconds(remote.created());
            OffsetDateTime remoteUpdated = fromUnixSeconds(remote.updated());
            OffsetDateTime remoteDeleted = fromUnixSeconds(remote.deletedAt());
            boolean archived = Boolean.TRUE.equals(remote.archived());
            boolean changed = created
                    || !Objects.equals(local.getTitle(), remote.title())
                    || !Objects.equals(local.getCampaignType(), remote.type())
                    || !Objects.equals(local.getCategory(), remote.category())
                    || !Objects.equals(local.getStatus(), remote.status())
                    || !Objects.equals(local.getDescription(), remote.description())
                    || !Objects.equals(local.getLocale(), remote.locale())
                    || !Objects.equals(local.getPublicUrl(), remote.url())
                    || !Objects.equals(local.getCurrency(), remote.currency())
                    || !Objects.equals(local.getIsArchived(), archived)
                    || !Objects.equals(local.getZeffyCreatedAt(), remoteCreated)
                    || !Objects.equals(local.getZeffyUpdatedAt(), remoteUpdated)
                    || !Objects.equals(local.getZeffyDeletedAt(), remoteDeleted);

            local.setTitle(remote.title());
            local.setCampaignType(remote.type());
            local.setCategory(remote.category());
            local.setStatus(remote.status());
            local.setDescription(remote.description());
            local.setLocale(remote.locale());
            local.setPublicUrl(remote.url());
            local.setCurrency(remote.currency());
            local.setIsArchived(archived);
            local.setZeffyCreatedAt(remoteCreated);
            local.setZeffyUpdatedAt(remoteUpdated);
            local.setZeffyDeletedAt(remoteDeleted);
            local.setLastSyncedAt(syncedAt);
            repository.save(local);

            if (created) inserted++;
            else if (changed) updated++;
            else ignored++;
        }
        return new Result(fetchedCampaigns.size(), inserted, updated, ignored, failed);
    }

    private OffsetDateTime fromUnixSeconds(Double value) {
        if (value == null) return null;
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(Math.round(value * 1000D)), ZoneOffset.UTC);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

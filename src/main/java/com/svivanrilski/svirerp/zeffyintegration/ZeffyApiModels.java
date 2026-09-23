package com.svivanrilski.svirerp.zeffyintegration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

final class ZeffyApiModels {

    private ZeffyApiModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CampaignPage(
            List<Campaign> data,
            @JsonProperty("has_more") boolean hasMore,
            @JsonProperty("next_cursor") JsonNode nextCursor) {

        String cursorText() {
            return nextCursor != null && nextCursor.isTextual() ? nextCursor.textValue() : null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Campaign(
            String id,
            Double created,
            Double updated,
            @JsonProperty("deleted_at") Double deletedAt,
            String type,
            String category,
            String status,
            String title,
            String description,
            String locale,
            String url,
            String currency,
            @JsonProperty("is_archived") Boolean archived) {
    }
}

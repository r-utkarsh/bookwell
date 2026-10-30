package com.bookwell.provider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProviderMapper {

    private ProviderMapper() {
    }

    public static ProviderDtos.ServiceDto toService(ServiceOffering service) {
        return new ProviderDtos.ServiceDto(
                service.getId(), service.getName(), service.getDescription(),
                service.getDurationMinutes(), service.getPrice(), service.getCurrency(),
                service.isActive(), service.getSortOrder());
    }

    public static ProviderDtos.ProviderCard toCard(ProviderProfile provider, long serviceCount,
                                                   BigDecimal startingPrice) {
        return new ProviderDtos.ProviderCard(
                provider.getId(), provider.getDisplayName(), provider.getBusinessName(),
                provider.getCategory(), provider.getDescription(), provider.getCity(),
                provider.getAddress(), provider.getPhone(), provider.getTimeZone(),
                provider.getCurrency(), provider.isFeatured(), provider.getStatus(),
                serviceCount, startingPrice);
    }

    public static ProviderDtos.ProviderDashboardProfile toDashboardProfile(ProviderProfile provider) {
        return new ProviderDtos.ProviderDashboardProfile(
                provider.getId(), provider.getUser().getId(), provider.getUser().getFullName(),
                provider.getUser().getEmail(), provider.getDisplayName(), provider.getBusinessName(),
                provider.getCategory(), provider.getDescription(), provider.getCity(),
                provider.getAddress(), provider.getPhone(), provider.getTimeZone(),
                provider.getCurrency(), provider.getStatus(), provider.isFeatured(),
                provider.getCreatedAt());
    }

    public static Map<UUID, Object[]> indexServiceSummaries(List<Object[]> rows) {
        return rows.stream().collect(java.util.stream.Collectors.toMap(
                row -> (UUID) row[0],
                row -> row));
    }
}

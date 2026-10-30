package com.bookwell.provider;

import com.bookwell.common.api.PageResponse;
import com.bookwell.common.exception.AppExceptions;
import com.bookwell.schedule.WeeklyScheduleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProviderCatalogService {

    private final ProviderProfileRepository providerRepository;
    private final ServiceOfferingRepository serviceRepository;
    private final WeeklyScheduleRepository weeklyScheduleRepository;

    public ProviderCatalogService(ProviderProfileRepository providerRepository,
                                  ServiceOfferingRepository serviceRepository,
                                  WeeklyScheduleRepository weeklyScheduleRepository) {
        this.providerRepository = providerRepository;
        this.serviceRepository = serviceRepository;
        this.weeklyScheduleRepository = weeklyScheduleRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProviderDtos.ProviderCard> search(int page, int size, String query, String category) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 48);
        String q = query == null ? "" : query.trim();
        String cat = category == null ? "" : category.trim();
        Page<ProviderProfile> providers = providerRepository.searchPublic(
                ProviderStatus.APPROVED, q, cat,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("featured"), Sort.Order.asc("displayName"))));
        Map<UUID, ServiceSummary> summaries = serviceSummaries(providers.getContent().stream()
                .map(ProviderProfile::getId).toList());
        return PageResponse.from(providers, provider -> {
            ServiceSummary summary = summaries.getOrDefault(provider.getId(), ServiceSummary.EMPTY);
            return ProviderMapper.toCard(provider, summary.count(), summary.minPrice());
        });
    }

    @Transactional(readOnly = true)
    public List<String> categories() {
        return providerRepository.findPublicCategories();
    }

    @Transactional(readOnly = true)
    public ProviderDtos.ProviderDetail get(UUID providerId) {
        ProviderProfile provider = providerRepository.findDetailedById(providerId)
                .filter(p -> p.getStatus() == ProviderStatus.APPROVED)
                .orElseThrow(() -> AppExceptions.notFound("Provider not found."));
        List<ServiceOffering> services = serviceRepository
                .findByProviderIdAndActiveTrueOrderBySortOrderAscNameAsc(providerId);
        BigDecimal startingPrice = services.stream()
                .map(ServiceOffering::getPrice)
                .min(BigDecimal::compareTo)
                .orElse(null);
        ProviderDtos.ProviderCard card = ProviderMapper.toCard(provider, services.size(), startingPrice);
        List<ProviderDtos.ServiceDto> serviceDtos = services.stream().map(ProviderMapper::toService).toList();
        List<ProviderDtos.WeeklyHourDto> weekly = weeklyScheduleRepository
                .findByProviderIdOrderByDayOfWeekAscStartTimeAsc(providerId).stream()
                .map(s -> new ProviderDtos.WeeklyHourDto(s.getId(), s.getDayOfWeek(),
                        s.getStartTime(), s.getEndTime()))
                .toList();
        return new ProviderDtos.ProviderDetail(card, serviceDtos, weekly);
    }

    private Map<UUID, ServiceSummary> serviceSummaries(List<UUID> providerIds) {
        if (providerIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ServiceSummary> summaries = new HashMap<>();
        for (Object[] row : providerRepository.summarizeActiveServices(providerIds)) {
            summaries.put((UUID) row[0], new ServiceSummary(((Number) row[1]).longValue(), (BigDecimal) row[2]));
        }
        return summaries;
    }

    private record ServiceSummary(long count, BigDecimal minPrice) {
        static final ServiceSummary EMPTY = new ServiceSummary(0, null);
    }
}

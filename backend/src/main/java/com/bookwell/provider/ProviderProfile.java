package com.bookwell.provider;

import com.bookwell.common.persistence.AuditedEntity;
import com.bookwell.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "provider_profiles")
@Getter
@Setter
@NoArgsConstructor
public class ProviderProfile extends AuditedEntity {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "display_name", nullable = false, length = 140)
    private String displayName;

    @Column(name = "business_name", nullable = false, length = 160)
    private String businessName;

    @Column(nullable = false, length = 60)
    private String category;

    @Column(nullable = false, columnDefinition = "text")
    private String description = "";

    @Column(nullable = false, length = 100)
    private String city = "";

    @Column(nullable = false, length = 240)
    private String address = "";

    @Column(length = 30)
    private String phone;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProviderStatus status = ProviderStatus.PENDING;

    @Column(nullable = false)
    private boolean featured = false;

    public static ProviderProfile create(User user, String displayName, String businessName, String category,
                                         String city, String timeZone, String currency) {
        ProviderProfile profile = new ProviderProfile();
        profile.id = UUID.randomUUID();
        profile.user = user;
        profile.displayName = displayName;
        profile.businessName = businessName;
        profile.category = category;
        profile.city = city;
        profile.timeZone = timeZone;
        profile.currency = currency;
        profile.status = ProviderStatus.PENDING;
        profile.description = "";
        profile.address = "";
        return profile;
    }
}

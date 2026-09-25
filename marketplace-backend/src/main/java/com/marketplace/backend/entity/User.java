package com.marketplace.backend.entity;

import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends AbstractEntity<UUID> {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider authProvider;

    @Column(nullable = false)
    private boolean enabled;

    @Column(length = 512)
    private String refreshToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserType userType;

    @Column(unique = true)
    private UUID paypalUserId;

    @Column(unique = true)
    private UUID misaTaxpayerId;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles;
}

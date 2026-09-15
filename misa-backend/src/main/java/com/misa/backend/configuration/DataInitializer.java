package com.misa.backend.configuration;

import com.misa.backend.constants.PointsConfig;
import com.misa.backend.dto.response.admin.BoxQrCodeResponse;
import com.misa.backend.entity.AuthProvider;
import com.misa.backend.entity.LoyaltyAccount;
import com.misa.backend.entity.Role;
import com.misa.backend.entity.RewardOption;
import com.misa.backend.entity.RewardType;
import com.misa.backend.entity.User;
import com.misa.backend.repository.BoxQrCodeRepository;
import com.misa.backend.repository.LoyaltyAccountRepository;
import com.misa.backend.repository.RewardOptionRepository;
import com.misa.backend.repository.RoleRepository;
import com.misa.backend.repository.UserRepository;
import com.misa.backend.service.BoxQrCodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Configuration
@Slf4j(topic = "INIT-APPLICATION")
public class DataInitializer {

    private static final String DEMO_EMAIL = "nguyenhuutrong111@gmail.com";
    private static final String DEMO_PASSWORD = "123456789";
    private static final String ADMIN_EMAIL = "admin@gmail.com";
    private static final String ADMIN_PASSWORD = "admin123456";

    @Bean
    public ApplicationRunner initData(RoleRepository roleRepository,
                                      RewardOptionRepository rewardOptionRepository,
                                      UserRepository userRepository,
                                      LoyaltyAccountRepository loyaltyAccountRepository,
                                      BoxQrCodeRepository boxQrCodeRepository,
                                      BoxQrCodeService boxQrCodeService,
                                      PasswordEncoder passwordEncoder) {
        return args -> {
            if (roleRepository.count() == 0) {
                roleRepository.saveAll(List.of(
                        createRole("ROLE_USER"),
                        createRole("ROLE_ADMIN")
                ));
                log.info("Initial roles inserted");
            }

        };
    }

    private Role createRole(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }

    private RewardOption createReward(RewardType type, int points, String title,
                                      String description, String codePrefix) {
        RewardOption option = new RewardOption();
        option.setType(type);
        option.setPoints(points);
        option.setTitle(title);
        option.setDescription(description);
        option.setCodePrefix(codePrefix);
        option.setActive(true);
        return option;
    }
}
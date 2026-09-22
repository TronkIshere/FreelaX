package com.misa.backend.configuration;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "misa")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MisaProperties {

    Organization organization = new Organization();
    Signing signing = new Signing();
    Tax tax = new Tax();

    @Getter
    @Setter
    public static class Organization {
        String id;
        String name;
        String taxCode;
        String address;
        String phone;
        String taxAuthority;
    }

    @Getter
    @Setter
    public static class Signing {
        String certificateSerial;
    }

    @Getter
    @Setter
    public static class Tax {
        double withholdingRate;
    }
}

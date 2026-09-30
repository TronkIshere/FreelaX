package com.misa.backend.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "certificate-pdf")
public class CertificatePdfProperties {
    private String payerName = "CÔNG TY CỔ PHẦN FREELAX";
    private String payerTaxCode = "";
    private String payerAddress = "";
    private String payerPhone = "";
    private String title = "CHỨNG TỪ KHẤU TRỪ THUẾ THU NHẬP CÁ NHÂN";
    private String incomeType = "Thu nhập từ tiền lương, tiền công";
    private String lookupUrl = "";
    private String fontRegular = "fonts/DejaVuSans.ttf";
    private String fontBold = "fonts/DejaVuSans-Bold.ttf";
    private String fontItalic = "fonts/DejaVuSans-Oblique.ttf";
}
package com.misa.backend.configuration;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.lowagie.text.pdf.BaseFont;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class CertificatePdfResourcesTest {
    @Test
    void defaultFontsArePackagedAndLoadable() throws Exception {
        CertificatePdfProperties properties = new CertificatePdfProperties();
        for (String path : List.of(
                properties.getFontRegular(),
                properties.getFontBold(),
                properties.getFontItalic())) {
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, () -> "Missing PDF font: " + path);
                byte[] bytes = stream.readAllBytes();
                assertNotNull(BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null));
            }
        }
    }
}

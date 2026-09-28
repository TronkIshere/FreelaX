package com.misa.backend.service.pdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.misa.backend.configuration.CertificatePdfProperties;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

@Component
public class CertificatePdfRenderer {

    private static final Locale VI_LOCALE = Locale.forLanguageTag("vi-VN");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String EMPTY = "—";

    private static final Color TEXT = new Color(33, 33, 33);
    private static final Color MUTED = new Color(105, 105, 105);
    private static final Color LINE = new Color(190, 190, 190);
    private static final Color HEADER_BG = new Color(242, 242, 242);
    private static final Color ACCENT = new Color(160, 28, 45);
    private static final Color SIGNED = new Color(22, 120, 60);

    private static final Map<Status, String> STATUS_LABELS = new EnumMap<>(Status.class);
    private static final Map<Status, String> WATERMARKS = new EnumMap<>(Status.class);

    static {
        STATUS_LABELS.put(Status.DRAFT, "Bản nháp - chưa phát hành");
        STATUS_LABELS.put(Status.SIGNED, "Đã ký số");
        STATUS_LABELS.put(Status.SUBMITTING, "Đã phát hành - đang gửi cơ quan thuế");
        STATUS_LABELS.put(Status.SUBMITTED, "Đã gửi cơ quan thuế");
        STATUS_LABELS.put(Status.ACCEPTED, "Cơ quan thuế đã tiếp nhận");
        STATUS_LABELS.put(Status.REJECTED, "Cơ quan thuế từ chối");
        STATUS_LABELS.put(Status.CORRECTION_REQUIRED, "Cần điều chỉnh");
        STATUS_LABELS.put(Status.REPLACED, "Đã bị thay thế");
        STATUS_LABELS.put(Status.CANCELLED, "Đã hủy");

        WATERMARKS.put(Status.DRAFT, "BẢN NHÁP");
        WATERMARKS.put(Status.CORRECTION_REQUIRED, "CẦN ĐIỀU CHỈNH");
        WATERMARKS.put(Status.REPLACED, "ĐÃ BỊ THAY THẾ");
        WATERMARKS.put(Status.CANCELLED, "ĐÃ HỦY");
        WATERMARKS.put(Status.REJECTED, "BỊ TỪ CHỐI");
    }

    private final CertificatePdfProperties properties;
    private volatile Fonts fonts;

    public CertificatePdfRenderer(CertificatePdfProperties properties) {
        this.properties = properties;
    }

    public byte[] render(CertificatePdfData data) {
        Fonts f = fonts();
        Status status = Status.from(data.status());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 30, 44);

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new PageDecorator(WATERMARKS.get(status), data.lookupCode(), f));

            document.addTitle(properties.getTitle() + " " + value(data.symbol()) + value(data.certificateNumber()));
            document.addAuthor(value(data.payerName()));
            document.addSubject("Mẫu số " + value(data.formNumber()));
            document.addCreator("MISA backend");
            document.open();

            document.add(header(data, f));
            document.add(new Chunk(new LineSeparator(0.8f, 100f, ACCENT, Element.ALIGN_CENTER, -4)));
            document.add(title(data, status, f));

            document.add(section("I. THÔNG TIN TỔ CHỨC TRẢ THU NHẬP", f));
            document.add(keyValues(f,
                    "Tên tổ chức trả thu nhập", data.payerName(),
                    "Mã số thuế", data.payerTaxCode(),
                    "Địa chỉ", data.payerAddress(),
                    "Điện thoại", data.payerPhone()));

            document.add(section("II. THÔNG TIN NGƯỜI NỘP THUẾ", f));
            document.add(keyValues(f,
                    "Họ và tên", data.taxpayerFullName(),
                    "Mã số thuế", data.taxpayerTaxCode(),
                    "Số CCCD/Hộ chiếu", data.taxpayerIdentityNumber(),
                    "Quốc tịch", data.taxpayerNationality(),
                    "Địa chỉ", data.taxpayerAddress(),
                    "Điện thoại", data.taxpayerPhone()));

            document.add(section("III. THÔNG TIN THUẾ THU NHẬP CÁ NHÂN KHẤU TRỪ", f));
            document.add(keyValues(f,
                    "Khoản thu nhập", data.incomeType(),
                    "Thời điểm trả thu nhập", paymentPeriod(data.paymentDate())));
            document.add(incomeTable(data, f));
            document.add(amountInWords("Tổng thu nhập chịu thuế bằng chữ: ", data.taxableIncome(), f));
            document.add(amountInWords("Số thuế đã khấu trừ bằng chữ: ", data.taxWithheld(), f));

            document.add(section("IV. THÔNG TIN THANH TOÁN", f));
            document.add(keyValues(f,
                    "Mã thanh toán", data.platformPayoutId(),
                    "Ngày thanh toán", data.paymentDate() != null ? data.paymentDate().format(DATE) : null,
                    "Số tiền nguyên tệ", data.sourceAmount() != null ? formatDecimal(data.sourceAmount()) : null,
                    "Tỷ giá quy đổi", data.exchangeRate() != null ? formatDecimal(data.exchangeRate()) + " VND" : null,
                    "Mã giao dịch", transactionLabel(data),
                    "Diễn giải", data.description()));

            if (status == Status.CANCELLED) {
                document.add(notice("Chứng từ đã bị hủy"
                        + (data.cancelledAt() != null ? " ngày " + data.cancelledAt().format(DATE_TIME) : "")
                        + (hasText(data.cancelReason()) ? ". Lý do: " + data.cancelReason() : "") + ".", f));
            }

            document.add(footer(data, status, f));
            document.close();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Khong tao duoc PDF chung tu", e);
        }

        return out.toByteArray();
    }

    private PdfPTable header(CertificatePdfData data, Fonts f) {
        PdfPTable table = new PdfPTable(new float[]{58, 42});
        table.setWidthPercentage(100);

        PdfPCell left = borderless();
        left.addElement(new Paragraph(value(data.payerName()), new Font(f.bold, 11.5f, Font.NORMAL, ACCENT)));
        left.addElement(line("Mã số thuế: ", data.payerTaxCode(), f, 9));
        table.addCell(left);

        PdfPCell right = borderless();
        right.addElement(rightLine("Mẫu số: ", data.formNumber(), f));
        right.addElement(rightLine("Ký hiệu: ", data.symbol(), f));
        right.addElement(rightLine("Số: ", data.certificateNumber(), f));
        table.addCell(right);

        table.setSpacingAfter(2);
        return table;
    }

    private Paragraph title(CertificatePdfData data, Status status, Fonts f) {
        Paragraph block = new Paragraph();
        block.setAlignment(Element.ALIGN_CENTER);
        block.setSpacingBefore(6);
        block.setSpacingAfter(2);

        Paragraph heading = new Paragraph(properties.getTitle(), new Font(f.bold, 15, Font.NORMAL, ACCENT));
        heading.setAlignment(Element.ALIGN_CENTER);
        block.add(heading);

        Paragraph subtitle = new Paragraph("(Chứng từ khấu trừ thuế điện tử)", new Font(f.italic, 9.5f, Font.NORMAL, MUTED));
        subtitle.setAlignment(Element.ALIGN_CENTER);
        block.add(subtitle);

        LocalDateTime date = data.issuedAt() != null ? data.issuedAt() : data.createdAt();
        Paragraph meta = new Paragraph();
        meta.setAlignment(Element.ALIGN_CENTER);
        meta.setSpacingBefore(4);
        if (date != null) {
            meta.add(new Chunk("Ngày " + date.format(DATE) + "   |   ", new Font(f.regular, 9.5f, Font.NORMAL, TEXT)));
        }
        meta.add(new Chunk("Trạng thái: ", new Font(f.regular, 9.5f, Font.NORMAL, MUTED)));
        meta.add(new Chunk(STATUS_LABELS.getOrDefault(status, value(data.status())),
                new Font(f.bold, 9.5f, Font.NORMAL, status == Status.DRAFT || status == Status.CANCELLED ? ACCENT : TEXT)));
        block.add(meta);

        return block;
    }

    private Paragraph section(String text, Fonts f) {
        Paragraph paragraph = new Paragraph(text, new Font(f.bold, 10.5f, Font.NORMAL, TEXT));
        paragraph.setSpacingBefore(7);
        paragraph.setSpacingAfter(2);
        return paragraph;
    }

    private PdfPTable keyValues(Fonts f, String... pairs) {
        PdfPTable table = new PdfPTable(new float[]{30, 70});
        table.setWidthPercentage(100);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            PdfPCell label = borderless(new Phrase(pairs[i], new Font(f.regular, 9.5f, Font.NORMAL, MUTED)));
            PdfPCell content = borderless(new Phrase(value(pairs[i + 1]), new Font(f.regular, 10, Font.NORMAL, TEXT)));
            label.setPaddingBottom(1.5f);
            content.setPaddingBottom(1.5f);
            table.addCell(label);
            table.addCell(content);
        }
        return table;
    }

    private PdfPTable incomeTable(CertificatePdfData data, Fonts f) {
        BigDecimal taxable = nz(data.taxableIncome());
        BigDecimal insurance = nz(data.mandatoryInsurance());
        BigDecimal charity = nz(data.charityContribution());
        BigDecimal assessable = taxable.subtract(insurance).subtract(charity).max(BigDecimal.ZERO);
        String currency = hasText(data.currency()) ? data.currency() : "VND";

        PdfPTable table = new PdfPTable(new float[]{8, 62, 30});
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);

        table.addCell(headerCell("STT", f, Element.ALIGN_CENTER));
        table.addCell(headerCell("Chỉ tiêu", f, Element.ALIGN_LEFT));
        table.addCell(headerCell("Số tiền (" + currency + ")", f, Element.ALIGN_RIGHT));

        incomeRow(table, "1", "Tổng thu nhập chịu thuế phải khấu trừ", taxable, f, false);
        incomeRow(table, "2", "Các khoản đóng bảo hiểm bắt buộc", insurance, f, false);
        incomeRow(table, "3", "Khoản đóng góp từ thiện, nhân đạo, khuyến học", charity, f, false);
        incomeRow(table, "4", "Tổng thu nhập tính thuế", assessable, f, false);
        incomeRow(table, "5", "Số thuế thu nhập cá nhân đã khấu trừ", nz(data.taxWithheld()), f, true);

        return table;
    }

    private void incomeRow(PdfPTable table, String index, String label, BigDecimal amount, Fonts f, boolean emphasis) {
        BaseFont font = emphasis ? f.bold : f.regular;
        table.addCell(bordered(new Phrase(index, new Font(f.regular, 9.5f, Font.NORMAL, TEXT)), Element.ALIGN_CENTER));
        table.addCell(bordered(new Phrase(label, new Font(font, 9.5f, Font.NORMAL, TEXT)), Element.ALIGN_LEFT));
        table.addCell(bordered(new Phrase(formatMoney(amount), new Font(font, 10, Font.NORMAL, emphasis ? ACCENT : TEXT)),
                Element.ALIGN_RIGHT));
    }

    private Paragraph amountInWords(String label, BigDecimal amount, Fonts f) {
        Paragraph paragraph = new Paragraph();
        paragraph.setSpacingBefore(2);
        paragraph.add(new Chunk(label, new Font(f.regular, 9.5f, Font.NORMAL, MUTED)));
        paragraph.add(new Chunk(VietnameseNumberWords.toVnd(nz(amount)), new Font(f.italic, 9.5f, Font.NORMAL, TEXT)));
        return paragraph;
    }

    private Paragraph notice(String text, Fonts f) {
        Paragraph paragraph = new Paragraph(text, new Font(f.bold, 10, Font.NORMAL, ACCENT));
        paragraph.setSpacingBefore(10);
        return paragraph;
    }

    private PdfPTable footer(CertificatePdfData data, Status status, Fonts f) throws IOException, DocumentException {
        PdfPTable table = new PdfPTable(new float[]{45, 55});
        table.setWidthPercentage(100);
        table.setSpacingBefore(10);
        table.setKeepTogether(true);

        PdfPCell lookup = borderless();
        if (hasText(data.lookupCode())) {
            Image qr = qrImage(lookupTarget(data.lookupCode()));
            if (qr != null) {
                qr.scaleAbsolute(76, 76);
                lookup.addElement(qr);
            }
            lookup.addElement(line("Mã tra cứu: ", data.lookupCode(), f, 9));
            if (hasText(properties.getLookupUrl())) {
                lookup.addElement(new Paragraph("Tra cứu tại: " + lookupTarget(data.lookupCode()),
                        new Font(f.regular, 8, Font.NORMAL, MUTED)));
            }
        }
        table.addCell(lookup);

        PdfPCell signature = borderless();
        LocalDateTime date = data.issuedAt() != null ? data.issuedAt() : data.createdAt();
        if (date != null) {
            Paragraph dateLine = new Paragraph("Ngày " + two(date.getDayOfMonth()) + " tháng "
                    + two(date.getMonthValue()) + " năm " + date.getYear(), new Font(f.italic, 9.5f, Font.NORMAL, TEXT));
            dateLine.setAlignment(Element.ALIGN_CENTER);
            signature.addElement(dateLine);
        }
        Paragraph role = new Paragraph("ĐẠI DIỆN TỔ CHỨC TRẢ THU NHẬP", new Font(f.bold, 10, Font.NORMAL, TEXT));
        role.setAlignment(Element.ALIGN_CENTER);
        signature.addElement(role);
        signature.addElement(signatureBox(data, status, f));
        table.addCell(signature);

        return table;
    }

    private PdfPTable signatureBox(CertificatePdfData data, Status status, Fonts f) {
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(88);
        box.setSpacingBefore(6);

        PdfPCell cell = new PdfPCell();
        cell.setPadding(7);
        boolean signed = data.issuedAt() != null && hasText(data.digitalCertificateSerial());
        cell.setBorderColor(signed ? SIGNED : LINE);
        cell.setBorderWidth(signed ? 1f : 0.6f);

        if (signed) {
            cell.addElement(new Paragraph("Chứng từ đã được ký điện tử", new Font(f.bold, 9.5f, Font.NORMAL, SIGNED)));
            cell.addElement(new Paragraph("Ký bởi: " + value(data.payerName()), new Font(f.regular, 8.5f, Font.NORMAL, TEXT)));
            cell.addElement(new Paragraph("Ký ngày: " + data.issuedAt().format(DATE_TIME), new Font(f.regular, 8.5f, Font.NORMAL, TEXT)));
            cell.addElement(new Paragraph("Serial chứng thư số: " + data.digitalCertificateSerial(),
                    new Font(f.regular, 8.5f, Font.NORMAL, TEXT)));
        } else {
            Paragraph unsigned = new Paragraph("(Chứng từ chưa được ký số)", new Font(f.italic, 9, Font.NORMAL, MUTED));
            unsigned.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(unsigned);
        }

        if (hasText(data.taxAuthorityReference())) {
            cell.addElement(new Paragraph("Mã tiếp nhận của cơ quan thuế: " + data.taxAuthorityReference(),
                    new Font(f.regular, 8.5f, Font.NORMAL, TEXT)));
        }
        if (data.submittedAt() != null && status != Status.DRAFT) {
            cell.addElement(new Paragraph("Ngày gửi cơ quan thuế: " + data.submittedAt().format(DATE_TIME),
                    new Font(f.regular, 8.5f, Font.NORMAL, TEXT)));
        }

        box.addCell(cell);
        return box;
    }

    private Image qrImage(String content) throws IOException, DocumentException {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 240, 240,
                    Map.of(EncodeHintType.MARGIN, 1, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.CHARACTER_SET, "UTF-8"));
            BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < matrix.getWidth(); x++) {
                for (int y = 0; y < matrix.getHeight(); y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }
            return Image.getInstance(image, null);
        } catch (WriterException e) {
            return null;
        }
    }

    private String lookupTarget(String lookupCode) {
        String url = properties.getLookupUrl();
        if (!hasText(url)) {
            return lookupCode;
        }
        return url.contains("{code}") ? url.replace("{code}", lookupCode) : url + lookupCode;
    }

    private Paragraph line(String label, String content, Fonts f, float size) {
        Paragraph paragraph = new Paragraph();
        paragraph.add(new Chunk(label, new Font(f.regular, size, Font.NORMAL, MUTED)));
        paragraph.add(new Chunk(value(content), new Font(f.regular, size, Font.NORMAL, TEXT)));
        return paragraph;
    }

    private Paragraph rightLine(String label, String content, Fonts f) {
        Paragraph paragraph = new Paragraph();
        paragraph.setAlignment(Element.ALIGN_RIGHT);
        paragraph.add(new Chunk(label, new Font(f.regular, 9.5f, Font.NORMAL, MUTED)));
        paragraph.add(new Chunk(value(content), new Font(f.bold, 10, Font.NORMAL, TEXT)));
        return paragraph;
    }

    private PdfPCell headerCell(String text, Fonts f, int alignment) {
        PdfPCell cell = bordered(new Phrase(text, new Font(f.bold, 9.5f, Font.NORMAL, TEXT)), alignment);
        cell.setBackgroundColor(HEADER_BG);
        return cell;
    }

    private PdfPCell bordered(Phrase phrase, int alignment) {
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorderColor(LINE);
        cell.setBorderWidth(0.6f);
        cell.setPadding(3.5f);
        cell.setPaddingBottom(4.5f);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private PdfPCell borderless() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        return cell;
    }

    private PdfPCell borderless(Phrase phrase) {
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingLeft(0);
        cell.setPaddingTop(1);
        return cell;
    }

    private String transactionLabel(CertificatePdfData data) {
        if (!hasText(data.transactionHash())) {
            return null;
        }
        return hasText(data.blockchain())
                ? data.transactionHash() + " (" + data.blockchain() + ")"
                : data.transactionHash();
    }

    private String paymentPeriod(LocalDate date) {
        return date == null ? null : "Tháng " + two(date.getMonthValue()) + " năm " + date.getYear();
    }

    private String formatMoney(BigDecimal amount) {
        DecimalFormat format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(VI_LOCALE));
        return format.format(nz(amount).setScale(0, RoundingMode.HALF_UP));
    }

    private String formatDecimal(BigDecimal amount) {
        DecimalFormat format = new DecimalFormat("#,##0.######", DecimalFormatSymbols.getInstance(VI_LOCALE));
        return format.format(amount);
    }

    private String two(int number) {
        return number < 10 ? "0" + number : String.valueOf(number);
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String value(String text) {
        return hasText(text) ? text : EMPTY;
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private Fonts fonts() {
        Fonts current = fonts;
        if (current == null) {
            synchronized (this) {
                current = fonts;
                if (current == null) {
                    current = new Fonts(
                            loadFont(properties.getFontRegular()),
                            loadFont(properties.getFontBold()),
                            loadFont(properties.getFontItalic()));
                    fonts = current;
                }
            }
        }
        return current;
    }

    private BaseFont loadFont(String classpathLocation) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(classpathLocation)) {
            if (in == null) {
                throw new IllegalStateException("Khong tim thay font " + classpathLocation + " trong classpath");
            }
            byte[] bytes = in.readAllBytes();
            return BaseFont.createFont(classpathLocation, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null);
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("Khong nap duoc font " + classpathLocation, e);
        }
    }

    private record Fonts(BaseFont regular, BaseFont bold, BaseFont italic) {
    }

    private enum Status {
        DRAFT, SIGNED, SUBMITTING, SUBMITTED, ACCEPTED, REJECTED, CORRECTION_REQUIRED, REPLACED, CANCELLED, UNKNOWN;

        static Status from(String value) {
            if (value == null) {
                return UNKNOWN;
            }
            try {
                return Status.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return UNKNOWN;
            }
        }
    }

    private static final class PageDecorator extends PdfPageEventHelper {

        private final String watermark;
        private final String lookupCode;
        private final Fonts fonts;

        private PageDecorator(String watermark, String lookupCode, Fonts fonts) {
            this.watermark = watermark;
            this.lookupCode = lookupCode;
            this.fonts = fonts;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Rectangle page = document.getPageSize();

            if (watermark != null) {
                PdfContentByte under = writer.getDirectContentUnder();
                PdfGState state = new PdfGState();
                state.setFillOpacity(0.10f);
                under.saveState();
                under.setGState(state);
                under.beginText();
                under.setColorFill(ACCENT);
                under.setFontAndSize(fonts.bold(), 78);
                under.showTextAligned(Element.ALIGN_CENTER, watermark, page.getWidth() / 2, page.getHeight() / 2, 45);
                under.endText();
                under.restoreState();
            }

            PdfContentByte over = writer.getDirectContent();
            over.saveState();
            over.setColorStroke(LINE);
            over.setLineWidth(0.5f);
            over.moveTo(document.left(), document.bottom() - 14);
            over.lineTo(document.right(), document.bottom() - 14);
            over.stroke();
            over.beginText();
            over.setColorFill(MUTED);
            over.setFontAndSize(fonts.regular(), 7.5f);
            String left = "Chứng từ điện tử" + (lookupCode != null && !lookupCode.isBlank() ? " - Mã tra cứu: " + lookupCode : "");
            over.showTextAligned(Element.ALIGN_LEFT, left, document.left(), document.bottom() - 26, 0);
            over.showTextAligned(Element.ALIGN_RIGHT, "Trang " + writer.getPageNumber(), document.right(), document.bottom() - 26, 0);
            over.endText();
            over.restoreState();
        }
    }
}
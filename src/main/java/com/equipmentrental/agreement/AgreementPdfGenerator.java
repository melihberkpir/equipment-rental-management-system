package com.equipmentrental.agreement;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

@Component
class AgreementPdfGenerator {

    /** Unicode font bundled with OpenPDF, embedded so names such as "Şükrü" render correctly. */
    private static final String FONT_RESOURCE = "font-fallback/LiberationSans-Regular.ttf";
    private static final String FONT_NAME = "LiberationSans-Regular.ttf";
    private static final float MARGIN = 54;

    private final byte[] fontBytes;

    AgreementPdfGenerator() {
        try (InputStream font = Document.class.getClassLoader().getResourceAsStream(FONT_RESOURCE)) {
            if (font == null) {
                throw new IllegalStateException("PDF font is missing: " + FONT_RESOURCE);
            }
            this.fontBytes = font.readAllBytes();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("PDF font could not be read", ex);
        }
    }

    byte[] generate(AgreementData data) {
        BaseFont baseFont = createBaseFont();
        Font title = new Font(baseFont, 20);
        Font heading = new Font(baseFont, 12.5f);
        Font body = new Font(baseFont, 10);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, MARGIN, MARGIN, MARGIN, MARGIN);
        PdfWriter.getInstance(document, output);
        document.addTitle("Rental Agreement " + data.reservationId());
        document.open();

        Paragraph titleParagraph = new Paragraph("Equipment Rental Agreement", title);
        titleParagraph.setAlignment(Element.ALIGN_CENTER);
        titleParagraph.setSpacingAfter(4);
        document.add(titleParagraph);

        Paragraph reference = new Paragraph(
                "Agreement No: " + data.reservationId() + "   |   Issue Date: " + data.issueDate(), body);
        reference.setAlignment(Element.ALIGN_CENTER);
        reference.setSpacingAfter(10);
        document.add(reference);

        addSection(document, "Customer", heading, body, new String[][] {
                {"Full name", data.customerName()},
                {"Email", data.customerEmail()}
        });
        addSection(document, "Equipment", heading, body, new String[][] {
                {"Name", data.equipmentName()},
                {"Category", data.categoryName()},
                {"Daily rate", data.dailyRate().toPlainString()}
        });
        addSection(document, "Rental", heading, body, new String[][] {
                {"Start date", data.startDate().toString()},
                {"Return date", data.endDate().toString()},
                {"Rental days", Long.toString(data.rentalDays())},
                {"Total cost", data.totalCost().toPlainString()},
                {"Reservation status", data.status().name()}
        });

        addHeading(document, "Terms", heading);
        String[] terms = {
                "1. The rental runs from the start date until the return date. The return date is not charged.",
                "2. The total cost is the daily rate multiplied by the number of rental days.",
                "3. The customer must use the equipment with care and return it in the condition received.",
                "4. The customer may cancel the reservation through the system before the start date."
        };
        for (String term : terms) {
            Paragraph paragraph = new Paragraph(term, body);
            paragraph.setSpacingAfter(3);
            document.add(paragraph);
        }

        document.add(signatures(body));
        document.close();
        return output.toByteArray();
    }

    private BaseFont createBaseFont() {
        try {
            return BaseFont.createFont(FONT_NAME, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, false, fontBytes, null);
        } catch (Exception ex) {
            throw new IllegalStateException("PDF font could not be loaded", ex);
        }
    }

    private static void addSection(Document document, String name, Font heading, Font body, String[][] rows) {
        addHeading(document, name, heading);
        PdfPTable table = new PdfPTable(new float[] {35, 65});
        table.setWidthPercentage(100);
        for (String[] row : rows) {
            table.addCell(cell(row[0], body));
            table.addCell(cell(row[1], body));
        }
        document.add(table);
    }

    private static void addHeading(Document document, String text, Font font) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setSpacingBefore(12);
        paragraph.setSpacingAfter(5);
        document.add(paragraph);
    }

    private static PdfPCell cell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderWidth(0.5f);
        return cell;
    }

    private static PdfPTable signatures(Font font) {
        PdfPTable table = new PdfPTable(new float[] {45, 10, 45});
        table.setWidthPercentage(100);
        table.setSpacingBefore(48);
        table.addCell(signatureCell("Customer signature", font, Rectangle.TOP));
        table.addCell(signatureCell("", font, Rectangle.NO_BORDER));
        table.addCell(signatureCell("Authorized representative", font, Rectangle.TOP));
        return table;
    }

    private static PdfPCell signatureCell(String text, Font font, int border) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(border);
        cell.setBorderWidth(0.5f);
        cell.setPaddingTop(5);
        return cell;
    }
}

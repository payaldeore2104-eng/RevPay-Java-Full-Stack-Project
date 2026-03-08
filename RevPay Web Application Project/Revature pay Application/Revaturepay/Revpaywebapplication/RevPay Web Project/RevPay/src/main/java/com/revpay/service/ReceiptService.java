package com.revpay.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;

import com.revpay.util.CurrencyUtil;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

@Service
public class ReceiptService {

        /**
         * Generates a styled PDF receipt for a completed transaction.
         *
         * @param tx JDBC Map row from the transactions query (uppercase keys from
         *           Oracle)
         * @return ByteArrayInputStream of the generated PDF
         */
        public ByteArrayInputStream generateReceipt(Map<String, Object> tx) throws Exception {

                Document document = new Document(PageSize.A5);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                PdfWriter writer = PdfWriter.getInstance(document, out);
                document.open();

                // ── Color palette ──────────────────────────────────────────────────
                Color brandBlue = new Color(0, 86, 179);
                Color darkText = new Color(26, 26, 46);
                Color mutedGrey = new Color(100, 100, 120);
                Color successGreen = new Color(25, 135, 84);
                Color lightBg = new Color(240, 242, 245);

                // ── Fonts ──────────────────────────────────────────────────────────
                Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, brandBlue);
                Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, darkText);
                Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, mutedGrey);
                Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 11, darkText);
                Font footerFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, mutedGrey);

                // ── Header – Brand Name ────────────────────────────────────────────
                Paragraph brand = new Paragraph("RevPay", brandFont);
                brand.setAlignment(Element.ALIGN_CENTER);
                brand.setSpacingAfter(2);
                document.add(brand);

                Paragraph tagline = new Paragraph("Payment Receipt", titleFont);
                tagline.setAlignment(Element.ALIGN_CENTER);
                tagline.setSpacingAfter(16);
                document.add(tagline);

                // ── Horizontal divider ─────────────────────────────────────────────
                PdfContentByte cb = writer.getDirectContent();
                cb.setColorStroke(brandBlue);
                cb.setLineWidth(1.5f);
                float left = document.left();
                float right = document.right();
                float yLine = writer.getVerticalPosition(false) - 4;
                cb.moveTo(left, yLine);
                cb.lineTo(right, yLine);
                cb.stroke();
                document.add(new Paragraph(" "));

                // ── Helper: extract value safely ───────────────────────────────────
                // Oracle JDBC returns column names in UPPERCASE
                String txRefId = safeVal(tx, "TRANSACTION_ID", "N/A");
                String senderName = safeVal(tx, "SENDER_NAME", "N/A");
                String receiverName = safeVal(tx, "RECEIVER_NAME", "N/A");
                String amount = safeVal(tx, "AMOUNT", "0.00");
                String createdAt = safeVal(tx, "CREATED_AT", "N/A");
                String status = safeVal(tx, "STATUS", "N/A");
                String description = safeVal(tx, "DESCRIPTION", "-");
                String txType = safeVal(tx, "TRANSACTION_TYPE", "TRANSFER");

                // ── Two-column receipt table ───────────────────────────────────────
                PdfPTable table = new PdfPTable(2);
                table.setWidthPercentage(100);
                table.setWidths(new float[] { 40f, 60f });
                table.setSpacingBefore(10);
                table.setSpacingAfter(16);

                // Helper inner class style method
                addRow(table, "Transaction ID", txRefId, labelFont, valueFont, lightBg, Color.WHITE);
                addRow(table, "Type", txType, labelFont, valueFont, Color.WHITE, lightBg);
                addRow(table, "Sender", senderName, labelFont, valueFont, lightBg, Color.WHITE);
                addRow(table, "Receiver", receiverName, labelFont, valueFont, Color.WHITE, lightBg);
                addRow(table, "Amount",
                                CurrencyUtil.format(Double.parseDouble(amount.replace("\u20B9", "").trim()))
                                                .replace("\u20B9", "INR "),
                                labelFont,
                                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, brandBlue),
                                lightBg, Color.WHITE);
                addRow(table, "Date & Time", createdAt, labelFont, valueFont, Color.WHITE, lightBg);
                addRow(table, "Description", description, labelFont, valueFont, lightBg, Color.WHITE);
                document.add(table);

                // ── Status badge ───────────────────────────────────────────────────
                boolean isSuccess = "COMPLETED".equalsIgnoreCase(status);
                Font sBadgeFont = isSuccess
                                ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE)
                                : FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, darkText);
                Color bgColor = isSuccess ? successGreen : new Color(255, 193, 7);

                PdfPTable statusTable = new PdfPTable(1);
                statusTable.setWidthPercentage(50);
                statusTable.setHorizontalAlignment(Element.ALIGN_CENTER);
                PdfPCell statusCell = new PdfPCell(new Phrase((isSuccess ? "✓ " : "⚠ ") + status, sBadgeFont));
                statusCell.setBackgroundColor(bgColor);
                statusCell.setPadding(10);
                statusCell.setBorder(Rectangle.NO_BORDER);
                statusCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                statusTable.addCell(statusCell);
                document.add(statusTable);

                // ── Footer ─────────────────────────────────────────────────────────
                document.add(new Paragraph(" "));
                document.add(new Paragraph(" "));
                cb.setColorStroke(new Color(200, 200, 210));
                cb.setLineWidth(0.5f);
                float yFoot = writer.getVerticalPosition(false) - 4;
                cb.moveTo(left, yFoot);
                cb.lineTo(right, yFoot);
                cb.stroke();
                document.add(new Paragraph(" "));

                Paragraph footer = new Paragraph(
                                "This is an auto-generated receipt. For queries contact support@revpay.com\n" +
                                                "RevPay | Secure Digital Payments Platform",
                                footerFont);
                footer.setAlignment(Element.ALIGN_CENTER);
                document.add(footer);

                document.close();
                return new ByteArrayInputStream(out.toByteArray());
        }

        // ── Helper: add a two-cell row ─────────────────────────────────────────
        private void addRow(PdfPTable table, String label, String value,
                        Font labelFont, Font valueFont,
                        Color labelBg, Color valueBg) {
                PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
                labelCell.setBackgroundColor(labelBg);
                labelCell.setPadding(8);
                labelCell.setBorder(Rectangle.NO_BORDER);
                table.addCell(labelCell);

                PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
                valueCell.setBackgroundColor(valueBg);
                valueCell.setPadding(8);
                valueCell.setBorder(Rectangle.NO_BORDER);
                table.addCell(valueCell);
        }

        // ── Helper: safely get string from JDBC Map ────────────────────────────
        private String safeVal(Map<String, Object> map, String key, String defaultVal) {
                Object v = map.get(key);
                return (v != null && !v.toString().isBlank()) ? v.toString() : defaultVal;
        }
}

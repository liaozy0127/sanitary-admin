package com.sanitary.admin.util;

import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class PdfGenerator {

    // WQY ZenHei font path installed by Alpine apk
    private static final String[] FONT_CANDIDATES = {
        "/usr/share/fonts/wqy-zenhei/wqy-zenhei.ttc",
        "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
        "/usr/share/fonts/wqy-zenhei.ttc"
    };

    public static void render(String html, OutputStream out) throws Exception {
        ITextRenderer renderer = new ITextRenderer();

        // Register Chinese font if available
        for (String path : FONT_CANDIDATES) {
            if (Files.exists(Path.of(path))) {
                renderer.getFontResolver().addFont(path, true);
                break;
            }
        }

        renderer.setDocumentFromString(html);
        renderer.layout();
        renderer.createPDF(out);
    }
}

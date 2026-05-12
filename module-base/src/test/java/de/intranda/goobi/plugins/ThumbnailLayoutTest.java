package de.intranda.goobi.plugins;

/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 *
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * Headless-browser regression test for the thumbnail strip layout in
 * {@code ImageQAPlugin.xhtml}. Asserts the inner image area fills the wrapper
 * exactly and the canvas fills the image area within sub-pixel tolerance.
 *
 * Skipped when no headless-capable {@code chromium} binary is on the path.
 */
public class ThumbnailLayoutTest {

    private static final int THUMBNAIL_SIZE = 200;

    /** Captures the EL expression in the inline style, e.g. "thumbnailSize +2" or "thumbnailSize". */
    private static final Pattern WRAPPER_WIDTH_PATTERN = Pattern.compile(
            "--thumbnail-width:\\s*#\\{AktuelleSchritteForm\\.myPlugin\\.([^}]+)\\}px");

    private static final Pattern RESULT_PATTERN =
            Pattern.compile("data-test-result=\"([^\"]+)\"");

    @Test
    public void thumbnailInnerAreaShouldFillOuterWrapper() throws Exception {
        String chromium = findChromium();
        Assume.assumeNotNull("Skipping: no chromium binary found", chromium);

        Path xhtml = Path.of("..", "module-gui", "src", "main", "webapp", "resources",
                "uii", "ImageQAPlugin.xhtml").toAbsolutePath().normalize();
        Path testHtml = Path.of("..", "module-gui", "src", "test", "resources",
                "uii", "thumbnail-layout-test.html").toAbsolutePath().normalize();
        Assert.assertTrue("Production xhtml missing: " + xhtml, Files.exists(xhtml));
        Assert.assertTrue("Test HTML missing: " + testHtml, Files.exists(testHtml));

        int wrapperWidth = evaluateWrapperWidthFromXhtml(xhtml, THUMBNAIL_SIZE);

        String url = testHtml.toUri().toString()
                + "?thumbnailSize=" + THUMBNAIL_SIZE
                + "&wrapperWidth=" + wrapperWidth;

        ProcessBuilder pb = new ProcessBuilder(
                chromium,
                "--headless",
                "--disable-gpu",
                "--no-sandbox",
                "--virtual-time-budget=2000",
                "--dump-dom",
                url);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String dom = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            Assert.fail("chromium did not finish within 30s");
        }

        Matcher m = RESULT_PATTERN.matcher(dom);
        Assert.assertTrue("Could not find data-test-result attribute in rendered DOM:\n" + dom, m.find());
        String result = m.group(1);
        Assert.assertEquals("Thumbnail wrapper width (" + wrapperWidth + "px, derived from "
                + xhtml.getFileName() + ") does not match the inner image-area width set "
                + "by ImageQAPlugin.js. This shows up to users as a cropped right border on "
                + "every thumbnail.",
                "PASS", result);
    }

    /**
     * Extracts the wrapper-width EL expression from the xhtml inline style and evaluates it for
     * the given thumbnailSize. Supports {@code thumbnailSize} and
     * {@code thumbnailSize + N} / {@code thumbnailSize +N} forms.
     */
    private static int evaluateWrapperWidthFromXhtml(Path xhtml, int thumbnailSize) throws Exception {
        String content = Files.readString(xhtml, StandardCharsets.UTF_8);
        Matcher m = WRAPPER_WIDTH_PATTERN.matcher(content);
        Assert.assertTrue("Could not find --thumbnail-width inline style in " + xhtml, m.find());
        String expr = m.group(1).trim();

        if ("thumbnailSize".equals(expr)) {
            return thumbnailSize;
        }
        Matcher offsetMatcher = Pattern.compile("thumbnailSize\\s*\\+\\s*(\\d+)").matcher(expr);
        if (offsetMatcher.matches()) {
            return thumbnailSize + Integer.parseInt(offsetMatcher.group(1));
        }
        throw new AssertionError("Unsupported wrapper-width expression: " + expr);
    }

    private static String findChromium() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String candidate : new String[] { "chromium", "chromium-browser", "google-chrome", "chrome" }) {
                for (String dir : pathEnv.split(File.pathSeparator)) {
                    File f = new File(dir, candidate);
                    if (f.canExecute()) {
                        return f.getAbsolutePath();
                    }
                }
            }
        }
        for (String macPath : new String[] {
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                "/Applications/Chromium.app/Contents/MacOS/Chromium" }) {
            File f = new File(macPath);
            if (f.canExecute()) {
                return f.getAbsolutePath();
            }
        }
        return null;
    }
}

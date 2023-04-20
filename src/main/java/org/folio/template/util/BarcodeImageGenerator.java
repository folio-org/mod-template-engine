package org.folio.template.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.krysalis.barcode4j.impl.code128.Code128Bean;
import org.krysalis.barcode4j.output.bitmap.BitmapCanvasProvider;
import org.krysalis.barcode4j.tools.UnitConv;

import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Base64;
import java.util.function.IntSupplier;

public class BarcodeImageGenerator {
  private static final Logger LOG = LogManager.getLogger("mod-template-engine");
  private static final String MIME_TYPE_PNG = "image/png";
  private static final int DPI = 160;

  static {
    checkFonts();
  }

  private BarcodeImageGenerator() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  static void checkFonts() {
    checkFonts(BarcodeImageGenerator::getFontCount);
  }

  @SuppressWarnings("java:S1181")  // suppress "Catch Exception instead of Error"
  static void checkFonts(IntSupplier fontCount) {
    // https://blog.adoptopenjdk.net/2021/01/prerequisites-for-font-support-in-adoptopenjdk/
    // https://issues.folio.org/browse/MODTEMPENG-57
    try {
      if (fontCount.getAsInt() == 0) {
        throw new IllegalStateException("Number of fonts is 0");
      }
    } catch (Exception | Error other) {
      // Error might be UnsatisfiedLinkError: "Error loading shared library libfreetype.so.6"
      var e = new IllegalStateException("No font found. Did you run 'apk add --no-cache fontconfig ttf-dejavu'?", other);
      LOG.fatal(e.getMessage(), e);
      throw e;
    }
  }

  private static int getFontCount() {
    return GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames().length;
  }

  private static void generateBarcodeImage(String barcode, OutputStream out) throws IOException {
    // Folio uses barcodes of type "Code 128"
    LOG.debug("generateBarcodeImage:: Generating barcode Image with barcode : {}", barcode);
    Code128Bean bean = new Code128Bean();
    bean.setModuleWidth(UnitConv.in2mm(2.8f / DPI));
    bean.doQuietZone(false);

    BitmapCanvasProvider canvas = new BitmapCanvasProvider(
        out, MIME_TYPE_PNG, DPI, BufferedImage.TYPE_BYTE_BINARY, false, 0);

    bean.generateBarcode(canvas, barcode);
    canvas.finish();
  }

  private static byte[] generateBarcodeImageBytes(String barcode) {
    LOG.debug("generateBarcodeImageBytes:: Generating barcode Image Bytes with barcode : {}", barcode);
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()){
      generateBarcodeImage(barcode, out);
      return out.toByteArray();
    }
    catch (IOException ex) {
      LOG.warn("Image generation for barcode {} failed", barcode,ex);
      return new byte[0];
    }
  }

  public static String generateBase64Image(String barcode) {
    LOG.debug("generateBase64Image:: Generating base64 image for barcode: {}", barcode);
    if (StringUtils.isBlank(barcode)) {
      return StringUtils.EMPTY;
    }

    return Base64.getEncoder()
        .encodeToString(generateBarcodeImageBytes(barcode));
  }

}

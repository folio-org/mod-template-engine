package org.folio.template.util;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.krysalis.barcode4j.impl.code128.Code128Bean;
import org.krysalis.barcode4j.output.bitmap.BitmapCanvasProvider;
import org.krysalis.barcode4j.tools.UnitConv;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Base64;

public class BarcodeImageGenerator {
  private static final Logger LOG = LoggerFactory.getLogger("mod-template-engine");
  private static final String MIME_TYPE_PNG = "image/png";

  private BarcodeImageGenerator() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  private static void generateBarcodeImage(String barcode, OutputStream out) throws IOException {
    final int dpi = 160;
    // Folio uses barcodes of type "Code 128"
    Code128Bean bean = new Code128Bean();
    bean.setModuleWidth(UnitConv.in2mm(2.8f / dpi));
    bean.doQuietZone(false);

    BitmapCanvasProvider canvas = new BitmapCanvasProvider(
        out, MIME_TYPE_PNG, dpi, BufferedImage.TYPE_BYTE_BINARY, false, 0);

    bean.generateBarcode(canvas, barcode);
    canvas.finish();
  }

  private static byte[] generateBarcodeImageBytes(String barcode) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()){
      generateBarcodeImage(barcode, out);
      return out.toByteArray();
    }
    catch (IOException ex) {
      LOG.error("Image generation for barcode {} failed", barcode);
      return new byte[0];
    }
  }

  public static String generateBase64Image(String barcode) {
    if (StringUtils.isBlank(barcode)) {
      return StringUtils.EMPTY;
    }

    return Base64.getEncoder()
        .encodeToString(generateBarcodeImageBytes(barcode));
  }

}

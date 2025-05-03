import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Standalone implementation of 2-D Feed Backward Predictive Coding (DPCM) in one main method.
 * All processing—from loading the image to saving results—is done in main, with explanatory comments and logs.
 */
public class Main {
    public static void main(String[] args) {
        // Check command-line arguments
        if (args.length < 4) {
            System.out.println("Usage: java Main <inputImage> <outputImage> <predictorType> <quantLevels>");
            System.out.println("  predictorType: order1 | order2 | adaptive");
            System.out.println("  quantLevels: number of uniform quantization levels (e.g., 8, 16, 32)");
            return;
        }

        String inputPath = args[0];
        String outputPath = args[1];
        String predictorType = args[2].toLowerCase();
        int levels = Integer.parseInt(args[3]);

        System.out.printf("[INFO] Loading image from '%s'...%n", inputPath);
        BufferedImage original;
        try {
            original = ImageIO.read(new File(inputPath));
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to load image: " + e.getMessage());
            return;
        }

        // Convert to grayscale if needed
        System.out.println("[INFO] Converting to grayscale (if not already)...");
        BufferedImage gray = new BufferedImage(
                original.getWidth(), original.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = gray.getGraphics();
        g.drawImage(original, 0, 0, null);
        g.dispose();

        int width = gray.getWidth();
        int height = gray.getHeight();

        // Step 1: read pixels into 2D array
        System.out.println("[INFO] Reading pixel data into 2D array...");
        int[][] pixels = new int[height][width];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                pixels[i][j] = gray.getRaster().getSample(j, i, 0);
            }
        }

        // Prepare quantizer parameters
        int step = (int) Math.ceil(256.0 / levels);
        System.out.printf("[INFO] Quantization: %d levels, step size = %d%n", levels, step);

        // Step 2: Encode residuals
        System.out.println("[INFO] Encoding residuals with predictor '" + predictorType + "'...");
        int[][] qResiduals = new int[height][width];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                // Predict based on selected predictor
                int predicted;
                int left = (j > 0) ? pixels[i][j - 1] : 0;
                int top = (i > 0) ? pixels[i - 1][j] : 0;
                int topLeft = (i > 0 && j > 0) ? pixels[i - 1][j - 1] : 0;
                switch (predictorType) {
                    case "order1":
                        predicted = left;
                        break;
                    case "order2":
                        predicted = left + top - topLeft;
                        break;
                    case "adaptive":
                        predicted = (left + top) / 2;
                        break;
                    default:
                        System.err.println("[ERROR] Unknown predictor type: " + predictorType);
                        return;
                }
                int error = pixels[i][j] - predicted;
                // Quantize error
                int q = (int) Math.round(error / (double) step);
                qResiduals[i][j] = q;

                // Log first few values
                if (i < 1 && j < 3) {
                    System.out.printf("[DEBUG] pix(%d,%d)=%d pred=%d err=%d q=%d%n",
                            i, j, pixels[i][j], predicted, error, q);
                }
            }
        }

        // Step 3: Decode to reconstruct
        System.out.println("[INFO] Reconstructing image from quantized residuals...");
        int[][] recon = new int[height][width];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int left = (j > 0) ? recon[i][j - 1] : 0;
                int top = (i > 0) ? recon[i - 1][j] : 0;
                int topLeft = (i > 0 && j > 0) ? recon[i - 1][j - 1] : 0;
                int predicted;
                switch (predictorType) {
                    case "order1":
                        predicted = left;
                        break;
                    case "order2":
                        predicted = left + top - topLeft;
                        break;
                    default: // adaptive
                        predicted = (left + top) / 2;
                        break;
                }
                // De-quantize and add prediction
                int deq = qResiduals[i][j] * step;
                int val = predicted + deq;
                // Clamp to valid grayscale range
                recon[i][j] = Math.max(0, Math.min(255, val));
            }
        }

        // Step 4: Evaluate MSE and compression ratio
        System.out.println("[INFO] Computing MSE and compression ratio...");
        double sumsq = 0;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                double diff = pixels[i][j] - recon[i][j];
                sumsq += diff * diff;
            }
        }
        double mse = sumsq / (width * height);
        double originalSize = width * height;                // bytes (1 byte per pixel)
        double encodedSize = width * height * Integer.BYTES; // 4 bytes per residual
        double cr = originalSize / encodedSize;

        System.out.printf("[RESULT] MSE = %.2f%n", mse);
        System.out.printf("[RESULT] Compression Ratio = %.4f%n", cr);

        // Step 5: Save reconstructed image
        System.out.println("[INFO] Saving reconstructed image to '" + outputPath + "'...");
        BufferedImage outImg = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                outImg.getRaster().setSample(j, i, 0, recon[i][j]);
            }
        }
        try {
            ImageIO.write(outImg, "png", new File(outputPath));
            System.out.println("[INFO] Done.");
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to save image: " + e.getMessage());
        }
    }
}

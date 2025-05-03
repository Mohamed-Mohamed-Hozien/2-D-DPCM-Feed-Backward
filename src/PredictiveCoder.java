public class PredictiveCoder {
    private final String predictorType;
    private final int quantizationLevels;

    public PredictiveCoder(String predictorType, int quantizationLevels) {
        this.predictorType = predictorType;
        this.quantizationLevels = quantizationLevels;
        if (!("Order-1".equals(predictorType) || "Order-2".equals(predictorType) || "Adaptive".equals(predictorType))) {
            throw new IllegalArgumentException("Invalid predictor type. Must be Order-1, Order-2, or Adaptive.");
        }
    }

    // Perform predictive encoding
    public int[][] encode(int[][] pixelArray) {
        if (pixelArray == null || pixelArray.length == 0 || pixelArray[0].length == 0) {
            throw new IllegalArgumentException("Input array must be non-empty.");
        }
        int height = pixelArray.length;
        int width = pixelArray[0].length;
        int[][] residuals = new int[height][width];

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int predicted = predict(i, j, pixelArray);
                residuals[i][j] = pixelArray[i][j] - predicted;
                residuals[i][j] = quantize(residuals[i][j]); // Quantize the residual
            }
        }
        return residuals;
    }

    // Perform reconstruction (decoding)
    public int[][] decode(int[][] quantizedResiduals) {
        if (quantizedResiduals == null || quantizedResiduals.length == 0 || quantizedResiduals[0].length == 0) {
            throw new IllegalArgumentException("Input array must be non-empty.");
        }
        int height = quantizedResiduals.length;
        int width = quantizedResiduals[0].length;
        int[][] reconstructed = new int[height][width];

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int predicted = predict(i, j, reconstructed);
                reconstructed[i][j] = predicted + quantizedResiduals[i][j];
            }
        }
        return reconstructed;
    }

    // Predict pixel value based on predictor type
    private int predict(int i, int j, int[][] pixelArray) {
        if (i < 0 || j < 0) return 0; // Edge case

        switch (predictorType) {
            case "Order-1":
                return (j > 0) ? pixelArray[i][j - 1] : 0;
            case "Order-2":
                return (j > 0 && i > 0) ? pixelArray[i][j - 1] +
                        pixelArray[i - 1][j] - pixelArray[i - 1][j - 1] : 0;
            case "Adaptive":
                int left = (j > 0) ? pixelArray[i][j - 1] : 0;
                int above = (i > 0) ? pixelArray[i - 1][j] : 0;
                int topLeft = (i > 0 && j > 0) ? pixelArray[i - 1][j - 1] : 0;
                return left + above - topLeft;
                
//                return 0; // Placeholder
            default:
                throw new IllegalArgumentException("Unknown predictor type");
        }
    }

    // Quantize residuals to the specified number of levels
    private int quantize(int value) {
        int stepSize = 256 / quantizationLevels;
        if (quantizationLevels <= 0) {
            throw new IllegalArgumentException("Quantization levels must be greater than 0.");
        }

        return Math.round((float) value / stepSize) * stepSize;
    }

    // Calculate Mean Squared Error
    public double calculateMSE(int[][] original, int[][] reconstructed) {
        if (original == null || reconstructed == null || original.length != reconstructed.length || original[0].length != reconstructed[0].length) {
            throw new IllegalArgumentException("Input arrays must be the same size.");
        }
        int height = original.length;
        int width = original[0].length;
        double mse = 0;

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int diff = original[i][j] - reconstructed[i][j];
                mse += (double) diff * diff;
            }
        }
        return mse / (height * width);
    }

    // Calculate compression ratio
    public double calculateCompressionRatio(int[][] original, int[][] encoded) {
        int originalSize = original.length * original[0].length * 8; // 8 bits per pixel
        int encodedSize = encoded.length * encoded[0].length * Integer.SIZE; // Residuals stored as integers

        return (double) originalSize / encodedSize;
    }
}
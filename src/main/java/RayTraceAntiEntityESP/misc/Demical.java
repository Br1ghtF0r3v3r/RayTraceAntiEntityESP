package RayTraceAntiEntityESP.misc;

public class Demical {

    public static String MaxDecimal(double value, int maxDecimals) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }

        double multiplier = Math.pow(10, maxDecimals);
        double rounded = Math.round(value * multiplier) / multiplier;
        String result = String.valueOf(rounded);

        return result.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

}

package ua.edu.university.util;

public class LandAnalysisService {
    public static final double NGO_PER_SQM = 450.25;

    public static double calculateUahPrice(double areaSqM) {
        return areaSqM * NGO_PER_SQM;
    }

    public static double calculateUsdPrice(double uahPrice, double rate) {
        return uahPrice / rate;
    }
}

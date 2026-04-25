package ua.edu.university.util;

/**
 * Розраховує оціночну вартість земельної ділянки.
 * Базова ставка НГО (грн/м²) береться з {@code application.properties}
 * (ключ {@code land.price.ngo_per_sqm}); конвертація в USD використовує
 * поточний курс НБУ/ПриватБанку.
 */
public class LandAnalysisService {

    public static double calculateUahPrice(double areaSqM) {
        double ngoPerSqm = ConfigManager.getDoubleProperty("land.price.ngo_per_sqm");
        return areaSqM * ngoPerSqm;
    }

    public static double calculateUsdPrice(double uahPrice, double rate) {
        return uahPrice / rate;
    }
}

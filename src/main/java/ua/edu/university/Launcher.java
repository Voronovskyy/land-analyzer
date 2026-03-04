package ua.edu.university;

/**
 * Клас-обгортка для безпечного запуску JavaFX додатку.
 * Використовується для обходу обмежень модульної системи (JPMS), яка була введена в Java 11.
 * Оскільки наш проект використовує Maven, але запускається локально без module-info.java,
 * цей клас дозволяє JVM правильно підтягнути всі графічні бібліотеки JavaFX
 * перед ініціалізацією класу Application.
 */
public class Launcher {
    public static void main(String[] args) {
        Main.main(args);
    }
}
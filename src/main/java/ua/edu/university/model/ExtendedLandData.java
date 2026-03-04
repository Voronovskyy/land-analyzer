package ua.edu.university.model;

import com.google.gson.JsonObject;

/**
 * Контейнер для розширених геофізичних та інфраструктурних даних.
 */
public class ExtendedLandData {
    public JsonObject climate;      // Опади, температури
    public String dayLength;        // Світловий день
    public JsonObject infrastructure; // Мережі (Overpass)
    public JsonObject regional;     // Країна/Регіон
}
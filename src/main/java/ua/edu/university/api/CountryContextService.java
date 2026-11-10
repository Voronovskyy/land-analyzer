package ua.edu.university.api;

import com.google.gson.JsonObject;

/**
 * Сервіс для отримання загальнодержавних та регіональних даних.
 * <p>
 * Раніше звертався до restcountries.com/v3.1, але це джерело виведено з
 * експлуатації: воно віддає 301 на legacy-файл, а той — відповідь
 * {@code {"success":false,"errors":[{"message":"This API version has been
 * deprecated"}]}}. Через це регіональний контекст завжди був null і рядок
 * «Юрисдикція» зникав зі звіту.
 * <p>
 * Мережевий виклик тут і не потрібен: з усієї відповіді у звіт ішов лише
 * рядок «Україна» — {@code population} і {@code timezone} не читав жоден
 * споживач. Тобто запит додавав ще одну точку відмови у фазу збору даних,
 * не додаючи жодних даних.
 */
public class CountryContextService {

    public JsonObject getRegionalContext() {
        JsonObject result = new JsonObject();
        result.addProperty("country", "Україна");
        return result;
    }
}

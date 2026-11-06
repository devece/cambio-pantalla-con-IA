/*
 * Restricciones vehiculares de Latinoamérica: implementación de referencia (Java).
 * Licencia MIT. Los datos (data/restricciones.json) son CC0.
 *
 * Pensada para Android antiguo (a partir de 4.1): Java 7, sin lambdas, sin java.time, solo org.json (que Android ya trae).
 * No trae los datos adentro: se los pasas como JSONObject, así puedes actualizar el archivo sin tocar el código.
 *
 *   JSONObject datos = new JSONObject(texto del restricciones.json);
 *   JSONObject ciudad = Restricciones.cityAt(datos, lat, lon, "CO");     // null si no estás en una ciudad con regla
 *   if (ciudad != null) {
 *       Restricciones.Evaluacion e = Restricciones.evaluate(ciudad, null, 7, Calendar.getInstance());
 *       if (e.restricted && e.inHours) { ... avisar ... }
 *   }
 *
 * Las fechas son la hora local del dispositivo.
 */
import java.util.Calendar;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class Restricciones {

    /** Mismos campos que la versión de JavaScript (ver README). */
    public static final class Evaluacion {
        public boolean expired, notStarted, inSeason, restricted, inHours, remaining;
    }

    private static final String[] SEMANA = {"dom", "lun", "mar", "mie", "jue", "vie", "sab"};

    private Restricciones() { }

    /** Ciudad de ese país en la que cae el punto (zonas aproximadas), o null. */
    public static JSONObject cityAt(JSONObject datos, double lat, double lon, String pais) {
        JSONArray ciudades = datos.optJSONArray("cities");
        if (ciudades == null) return null;
        for (int i = 0; i < ciudades.length(); i++) {
            JSONObject c = ciudades.optJSONObject(i);
            if (c != null && pais.equals(c.optString("country")) && inZone(c, lat, lon)) return c;
        }
        return null;
    }

    public static boolean inZone(JSONObject ciudad, double lat, double lon) {
        if (Double.isNaN(lat) || Double.isNaN(lon) || Double.isInfinite(lat) || Double.isInfinite(lon)) return false;
        JSONArray poly = ciudad.optJSONArray("zone");
        if (poly == null) return false;
        boolean dentro = false;
        for (int i = 0, j = poly.length() - 1; i < poly.length(); j = i++) {
            double yi = poly.optJSONArray(i).optDouble(0), xi = poly.optJSONArray(i).optDouble(1);
            double yj = poly.optJSONArray(j).optDouble(0), xj = poly.optJSONArray(j).optDouble(1);
            if ((yi > lat) != (yj > lat) && lon < ((xj - xi) * (lat - yi)) / (yj - yi) + xi) dentro = !dentro;
        }
        return dentro;
    }

    /**
     * Qué le toca a esa placa en esa fecha y hora.
     * @param claseVehiculo solo Chile: "sello", "sin" o "nuevo"; null en las demás ciudades.
     * @param digito último dígito de la placa (0-9).
     */
    public static Evaluacion evaluate(JSONObject ciudad, String claseVehiculo, int digito, Calendar cuando) {
        Evaluacion e = new Evaluacion();
        int minutos = cuando.get(Calendar.HOUR_OF_DAY) * 60 + cuando.get(Calendar.MINUTE);
        String hoy = iso(cuando);
        String validTo = textoONulo(ciudad, "validTo");
        String validFrom = textoONulo(ciudad, "validFrom");
        e.expired = validTo != null && hoy.compareTo(validTo) > 0;
        e.notStarted = validFrom != null && hoy.compareTo(validFrom) < 0;
        JSONArray meses = ciudad.optJSONArray("months");
        e.inSeason = meses == null || contiene(meses, cuando.get(Calendar.MONTH) + 1);
        String dia = SEMANA[cuando.get(Calendar.DAY_OF_WEEK) - 1];
        boolean diaQueRige = contieneTexto(ciudad.optJSONArray("weekdays"), dia);

        boolean porDigito = false, prohibido = false;
        JSONObject regla = ciudad.optJSONObject("rule");
        String tipo = regla == null ? "" : regla.optString("kind");
        if ("parity".equals(tipo)) {
            porDigito = contiene(regla.optJSONArray(cuando.get(Calendar.DAY_OF_MONTH) % 2 == 1 ? "odd" : "even"), digito);
        } else if ("weekday".equals(tipo)) {
            porDigito = contiene(regla.optJSONObject("days").optJSONArray(dia), digito);
        } else if ("classes".equals(tipo)) {
            JSONObject clase = clase(regla.optJSONArray("classes"), claseVehiculo);
            if (clase != null && !clase.optBoolean("exempt", false)) {
                JSONObject dias = clase.optJSONObject("days");
                porDigito = dias != null && contiene(dias.optJSONArray(dia), digito);
                JSONObject veda = clase.optJSONObject("alwaysBanned");
                prohibido = veda != null && contieneTexto(veda.optJSONArray("weekdays"), dia);
            }
        }
        boolean activa = !e.expired && !e.notStarted && e.inSeason && diaQueRige;
        e.restricted = activa && (porDigito || prohibido);

        JSONArray horas = ciudad.optJSONArray("hours");
        for (int i = 0; horas != null && i < horas.length(); i++) {
            int a = horas.optJSONArray(i).optInt(0), b = horas.optJSONArray(i).optInt(1);
            if (minutos >= a && minutos < b) e.inHours = true;
            if (minutos < b) e.remaining = true;
        }
        return e;
    }

    private static JSONObject clase(JSONArray clases, String id) {
        if (clases == null || clases.length() == 0) return null;
        for (int i = 0; i < clases.length(); i++) {
            JSONObject c = clases.optJSONObject(i);
            if (c != null && c.optString("id").equals(id)) return c;
        }
        return clases.optJSONObject(0); // sin clase indicada: la primera, como la versión de JavaScript
    }

    private static boolean contiene(JSONArray lista, int n) {
        for (int i = 0; lista != null && i < lista.length(); i++) if (lista.optInt(i, -1) == n) return true;
        return false;
    }

    private static boolean contieneTexto(JSONArray lista, String s) {
        for (int i = 0; lista != null && i < lista.length(); i++) if (s.equals(lista.optString(i))) return true;
        return false;
    }

    private static String textoONulo(JSONObject o, String clave) {
        if (o.isNull(clave)) return null;
        String s = o.optString(clave, "");
        return s.length() == 0 ? null : s;
    }

    private static String iso(Calendar c) {
        return String.format(java.util.Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }
}

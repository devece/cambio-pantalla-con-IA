import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Calendar;
import org.json.JSONArray;
import org.json.JSONObject;

/** Ejecuta pruebas/casos.json contra la implementación en Java.  Uso: ver README (sección «Probar»). */
public class PruebaCasos {
    static String leer(String ruta) throws Exception {
        InputStream in = new FileInputStream(ruta);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
        in.close();
        return new String(out.toByteArray(), "UTF-8");
    }

    static JSONObject ciudadPorId(JSONObject datos, String id) throws Exception {
        JSONArray cs = datos.getJSONArray("cities");
        for (int i = 0; i < cs.length(); i++) if (cs.getJSONObject(i).getString("id").equals(id)) return cs.getJSONObject(i);
        return null;
    }

    public static void main(String[] args) throws Exception {
        JSONObject datos = new JSONObject(leer(args[0]));
        JSONObject casos = new JSONObject(leer(args[1]));
        String[] campos = {"expired", "notStarted", "inSeason", "restricted", "inHours", "remaining"};
        int ok = 0, mal = 0;
        JSONArray reglas = casos.getJSONArray("reglas");
        for (int i = 0; i < reglas.length(); i++) {
            JSONObject c = reglas.getJSONObject(i);
            String f = c.getString("fecha"); // AAAA-MM-DDTHH:MM, hora local
            Calendar cal = Calendar.getInstance();
            cal.clear();
            cal.set(Integer.parseInt(f.substring(0, 4)), Integer.parseInt(f.substring(5, 7)) - 1, Integer.parseInt(f.substring(8, 10)),
                    Integer.parseInt(f.substring(11, 13)), Integer.parseInt(f.substring(14, 16)));
            Restricciones.Evaluacion e = Restricciones.evaluate(ciudadPorId(datos, c.getString("ciudad")), c.optString("clase", null), c.getInt("digito"), cal);
            boolean[] dado = {e.expired, e.notStarted, e.inSeason, e.restricted, e.inHours, e.remaining};
            StringBuilder dif = new StringBuilder();
            for (int k = 0; k < campos.length; k++) {
                boolean esperado = c.getJSONObject("esperado").getBoolean(campos[k]);
                if (dado[k] != esperado) dif.append(campos[k]).append('=').append(dado[k]).append(" (esperado ").append(esperado).append(") ");
            }
            if (dif.length() > 0) { mal++; System.out.println("FALLA  " + c.getString("nombre") + ": " + dif); } else ok++;
        }
        JSONArray geo = casos.getJSONArray("geo");
        for (int i = 0; i < geo.length(); i++) {
            JSONObject g = geo.getJSONObject(i);
            JSONObject ciudad = Restricciones.cityAt(datos, g.getDouble("lat"), g.getDouble("lon"), g.getString("pais"));
            String dado = ciudad == null ? null : ciudad.getString("id");
            String esperado = g.isNull("ciudad") ? null : g.getString("ciudad");
            if (dado == null ? esperado != null : !dado.equals(esperado)) { mal++; System.out.println("FALLA  zona " + g + ": dio " + dado); } else ok++;
        }
        System.out.println("Java: " + ok + " bien, " + mal + " mal");
        System.exit(mal == 0 ? 0 : 1);
    }
}

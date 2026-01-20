import com.github.signaflo.math.operations.DoubleFunctions;
import com.github.signaflo.timeseries.TimeSeries;
import com.github.signaflo.timeseries.forecast.Forecast;
import com.github.signaflo.timeseries.model.arima.Arima;
import com.github.signaflo.timeseries.model.arima.ArimaOrder;
import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciitable.CWC_LongestWord;
import de.vandermeer.asciithemes.a7.A7_Grids;
import org.json.JSONObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

//
//* @author couedrao on 25/11/2019.

//* @project gctrl

//

//


//* 1)Collects the details from the managed resources e g topology Collects the details from the managed resources e.g.  topology information, metrics (e.g. offered capacity and throughput), configuration property settings and so on.


//* 2)The monitor function aggregates,correlates and filters these details until it determines a symptom that needs to be analyzed.


//*


@SuppressWarnings({"SynchronizeOnNonFinalField"})
class Monitor {
    private static List<String> symptom;
    private static final int period = 2000;
    private static double i = 0;
    public String gw_current_SYMP = "N/A";

    void start() {
        Main.logger(this.getClass().getSimpleName(), "Start monitoring of " + Knowledge.gw);
        symptom = Main.shared_knowledge.get_symptoms();
        Main.shared_knowledge.create_lat_tab();
        data_collector(); //in bg
        symptom_generator();
    }

    //Symptom Generator  (can be modified)
    private void symptom_generator() {
        while (Main.run) {
            try {
                Thread.sleep(period * 5);
                ResultSet rs = Main.shared_knowledge.select_from_tab();
                //print_nice_rs(rs);
                double[] prediction = predict_next_lat(rs);
                boolean isOk = true;

                for (int j = 0; j < Knowledge.horizon; j++) {
                    // Prediction c'est le debit entrant
                    if (prediction[j] > ((Knowledge.replicas - 1) * Knowledge.GATEWAY_MAXIMUM_OUTPUT_DEBIT) + Knowledge.GATEWAY_MAXIMUM_OUTPUT_DEBIT * Knowledge.gw_debit_upgrade_threshold) {
                        Main.logger(this.getClass().getSimpleName(), " Symptom --> To Analyse : " + symptom.get(3));
                        update_symptom(symptom.get(3));
                        isOk = false;
                        break;
                    } else if (prediction[j] < (Knowledge.replicas - 2) * Knowledge.GATEWAY_MAXIMUM_OUTPUT_DEBIT + Knowledge.GATEWAY_MAXIMUM_OUTPUT_DEBIT * Knowledge.gw_debit_downgrade_threshold) {
                        Main.logger(this.getClass().getSimpleName(), " Symptom --> To Analyse : " + symptom.get(1));
                        update_symptom(symptom.get(1));
                        isOk = false;
                        break;
                    }
                }

                if (isOk) {
                    Main.logger(this.getClass().getSimpleName(), "Symptom --> To Analyse : " + symptom.get(2));
                    update_symptom(symptom.get(2));
                }

            } catch (SQLException | InterruptedException e) {
                e.printStackTrace();
                update_symptom("N/A");
            }
        }
    }

    private void data_collector() {
        new Thread(() -> {
            Main.logger(this.getClass().getSimpleName(), "Filling db with data every " + period + " ms");
            while (Main.run)
                try {
                    Thread.sleep(period);
                    double data = get_data();

                    /*
                    try {
                        double[] history = Main.shared_knowledge.get_history(100);

                        Main.logger(this.getClass().getSimpleName(), "\nHistory Chart:\n" + drawLineChart(history, 10));
                    } catch (SQLException e) {
                        Main.logger(this.getClass().getSimpleName(), "Failed to draw history chart");
                    }
*/

                    Main.logger(this.getClass().getSimpleName(), "Collected Data : " + data + " req/s");

                    Main.shared_knowledge.insert_in_tab(new Timestamp(new Date().getTime()), data);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

        }

        ).start();
    }


    public static String drawLineChart(double[] values, int height) {
        double max = Double.NEGATIVE_INFINITY;
        double min = Double.POSITIVE_INFINITY;

        for (double v : values) {
            max = Math.max(max, v);
            min = Math.min(min, v);
        }

        // Évite division par zéro si toutes les valeurs sont identiques
        if (max == min) {
            max += 1;
            min -= 1;
        }

        char[][] grid = new char[height][values.length];

        // Remplissage avec des espaces
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < values.length; x++) {
                grid[y][x] = ' ';
            }
        }

        // Placement des points
        for (int x = 0; x < values.length; x++) {
            int y = (int) Math.round(
                    (values[x] - min) / (max - min) * (height - 1)
            );
            grid[height - 1 - y][x] = '*';
        }

        StringBuilder sb = new StringBuilder();

        // Construction de l'affichage
        for (int y = 0; y < height; y++) {
            double label = max - (max - min) * y / (height - 1);
            sb.append(String.format("%6.2f |", label));

            for (int x = 0; x < values.length; x++) {
                sb.append(grid[y][x]);
            }
            sb.append('\n');
        }

        // Axe X
        sb.append("        +");
        sb.append("-".repeat(values.length));
        sb.append('\n');

        sb.append("          ");
        for (int i = 0; i < values.length; i++) {
            sb.append(i % 10);
        }
        sb.append('\n');

        return sb.toString();
    }

    private double get_data() {
    try {
        String requestQL =
                "sum(rate(istio_requests_total{destination_workload=\"sdci-gwi\"}[1m]))" ;

        String requestEncodedQuery = URLEncoder.encode(requestQL, StandardCharsets.UTF_8);

        URI requestURI = URI.create(
            "http://localhost:9090/api/v1/query?query=" + requestEncodedQuery
        );

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest requestRequest = HttpRequest.newBuilder()
                .uri(requestURI)
                .GET()
                .build();

        HttpResponse<String> requestResponse =
                client.send(requestRequest, HttpResponse.BodyHandlers.ofString());

        JSONObject requestJSON = new JSONObject(requestResponse.body());

        double request_value = requestJSON
                .getJSONObject("data")
                .getJSONArray("result")
                .getJSONObject(0)
                .getJSONArray("value")
                .getDouble(1);

        return request_value / 2;

    } catch (Exception e) {
        e.printStackTrace();
        return -1; // fallback si Prometheus ne répond pas
    }
}


    private double get_fake_data() {
        //return new Random().nextInt();
        return i += 2.5;
    }

    //ARIMA-based Forecasting
    private double[] predict_next_lat(ResultSet rs) throws SQLException {
        rs.first();
        double[] history = new double[Knowledge.moving_wind];
        double[] p = new double[Knowledge.horizon];
        int j = Knowledge.moving_wind - 1;
        while (rs.next()) {
            history[j] = Double.parseDouble(rs.getString("latency"));
            j--;
        }
        TimeSeries timeSeries = TimeSeries.from(DoubleFunctions.arrayFrom(history));
        ArimaOrder modelOrder = ArimaOrder.order(0, 1, 1, 0, 1, 1);
        //ArimaOrder modelOrder = ArimaOrder.order(0, 0, 0, 1, 1, 1);
        Arima model = Arima.model(timeSeries, modelOrder);
        Forecast forecast = model.forecast(Knowledge.moving_wind);

        StringBuilder sb = new StringBuilder();
        sb.append("ARIMA Model Summary: [");
        for (int k = 0; k < Knowledge.horizon; k++) {
            p[k] = forecast.pointEstimates().at(k);
            sb.append(p[k]);

            if (k < Knowledge.horizon - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");

        Main.logger(this.getClass().getSimpleName(), sb.toString());

        return p;
    }

    private void print_nice_rs(ResultSet rs) throws SQLException {
        rs.first();
        AsciiTable at = new AsciiTable();
        at.addRule();
        at.addRow("Timestamp", "Latency_in_" + Knowledge.gw);
        at.addRule();
        while (rs.next()) {
            at.addRow(rs.getTimestamp("id").getTime(), rs.getString("latency"));
            at.addRule();
        }
        at.getContext().setGrid(A7_Grids.minusBarPlusEquals());
        at.getRenderer().setCWC(new CWC_LongestWord());
        System.out.println(this.getClass().getSimpleName() + " : ");
        System.out.println(at.render());

    }

    private void update_symptom(String symptom) {

        synchronized (gw_current_SYMP) {
            gw_current_SYMP.notify();
            gw_current_SYMP = symptom;

        }
    }


}
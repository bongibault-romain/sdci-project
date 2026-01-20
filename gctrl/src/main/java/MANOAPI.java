import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.V1Scale;
import io.kubernetes.client.util.Config;
import org.h2.util.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.Map.Entry;

/**
 * @author couedrao on 27/11/2019.
 * @project gctrl
 */
class MANOAPI {

    String deploy_gw(Map<String, String> vnfinfos) {
        String ip = "192.168.0." + (new Random().nextInt(253) + 1);
        Main.logger(this.getClass().getSimpleName(), "Deploying VNF ...");

        //printing
        for (Entry<String, String> e : vnfinfos.entrySet()) {
            Main.logger(this.getClass().getSimpleName(), "\t" + e.getKey() + " : " + e.getValue());
        }
        //TODO
        //gateway deployment
        Main.logger(this.getClass().getSimpleName(), "Deployed GW IP : " + ip);
        

        return ip;
    }

    List<String> deploy_multi_gws_and_lb(List<Map<String, String>> vnfsinfos) {
        List<String> ips = new ArrayList<>();
        //TODO

        for (Map<String, String> vnfsinfo : vnfsinfos) {
            ips.add(deploy_gw(vnfsinfo));
        }

        return ips;
    }

    void pod_scaling(String name, int number) throws IOException, InterruptedException {
        String query = "localhost:/apis/apps/v1/namespaces/default/deployment/" + name + "/scale";

        ApiClient client = Config.defaultClient();
        Configuration.setDefaultApiClient(client);
        AppsV1Api api = new AppsV1Api();

        try {
            V1Scale scale = api.readNamespacedDeploymentScale(name, "default").execute();
            Objects.requireNonNull(scale.getSpec()).setReplicas(number);
            V1Scale updatedScale = api.replaceNamespacedDeploymentScale(name, "default", scale).execute();
            System.out.println("Message : " + updatedScale.getSpec().getReplicas());
        } catch (ApiException e) {
            System.err.println(e.getMessage());
        }
    }

    static int get_pod_replicas(String name) throws IOException, InterruptedException {
        int replicas = 0;

        ApiClient client = Config.defaultClient();
        Configuration.setDefaultApiClient(client);
        AppsV1Api api = new AppsV1Api();

        try {
            V1Scale scale = api.readNamespacedDeploymentScale(name, "default").execute();
            replicas = Objects.requireNonNull(scale.getSpec()).getReplicas();
        } catch (ApiException e) {
            System.err.println(e.getMessage());
        }

        return replicas;
    }

}

package co.cask.coopr.shell.command;

import co.cask.common.cli.Arguments;
import co.cask.common.cli.Command;
import co.cask.coopr.client.ClusterClient;
import co.cask.coopr.http.request.ClusterOperationRequest;
import co.cask.coopr.shell.util.CliUtil;

import java.io.PrintStream;
import javax.inject.Inject;

/**
 * Stops service on cluster.
 */
public class StopServiceOnClusterCommand implements Command {

  private static final String CLUSTER_ID_KEY = "cluster-id";
  private static final String SERVICE_ID_KEY = "service-id";
  private static final String PROVIDER_FIELDS_KEY = "provider-fields";

  private final ClusterClient clusterClient;

  @Inject
  public StopServiceOnClusterCommand(ClusterClient clusterClient) {
    this.clusterClient = clusterClient;
  }

  @Override
  public void execute(Arguments arguments, PrintStream printStream) throws Exception {
    String clusterId = CliUtil.checkArgument(arguments.get(CLUSTER_ID_KEY));
    String serviceId = CliUtil.checkArgument(arguments.get(SERVICE_ID_KEY));
    ClusterOperationRequest clusterOperationRequest = CliUtil.getObjectFromJson(arguments, PROVIDER_FIELDS_KEY,
                                                                                ClusterOperationRequest.class);
    if (clusterOperationRequest == null) {
      clusterClient.stopServiceOnCluster(clusterId, serviceId);
    } else {
      clusterClient.stopServiceOnCluster(clusterId, serviceId, clusterOperationRequest);
    }
  }

  @Override
  public String getPattern() {
    return "stop service <service-id> on cluster <cluster-id>[ with provider fields <provider-fields>]";
  }

  @Override
  public String getDescription() {
    return "Stops service on cluster";
  }
}

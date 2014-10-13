package co.cask.coopr.shell.command;

import co.cask.common.cli.Arguments;
import co.cask.common.cli.Command;
import co.cask.coopr.client.ClusterClient;
import co.cask.coopr.http.request.ClusterOperationRequest;
import co.cask.coopr.shell.util.CliUtil;

import java.io.PrintStream;
import javax.inject.Inject;

/**
 * Deletes a cluster.
 */
public class DeleteClusterCommand implements Command {

  private static final String CLUSTER_ID_KEY = "cluster-id";
  private static final String PROVIDER_FIELDS_KEY = "provider-fields";

  private final ClusterClient clusterClient;

  @Inject
  public DeleteClusterCommand(ClusterClient clusterClient) {
    this.clusterClient = clusterClient;
  }

  @Override
  public void execute(Arguments arguments, PrintStream printStream) throws Exception {
    String id = CliUtil.checkArgument(arguments.get(CLUSTER_ID_KEY));
    ClusterOperationRequest clusterOperationRequest = CliUtil.getObjectFromJson(arguments, PROVIDER_FIELDS_KEY,
                                                                                ClusterOperationRequest.class);
    if (clusterOperationRequest == null) {
      clusterClient.deleteCluster(id);
    } else {
      clusterClient.deleteCluster(id, clusterOperationRequest);
    }
  }

  @Override
  public String getPattern() {
    return "delete cluster <cluster-id>[ with provider fields <provider-fields>]";
  }

  @Override
  public String getDescription() {
    return "Deletes a cluster";
  }
}

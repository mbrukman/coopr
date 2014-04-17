/*
 * Copyright 2012-2014, Continuuity, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.continuuity.loom.layout;

import com.continuuity.loom.admin.Constraints;
import com.continuuity.loom.admin.Service;
import com.continuuity.loom.admin.ServiceConstraint;
import com.continuuity.loom.cluster.Cluster;
import com.continuuity.loom.cluster.Node;
import com.continuuity.loom.layout.change.AddServiceChangeIterator;
import com.continuuity.loom.layout.change.ClusterLayoutChange;
import com.continuuity.loom.layout.change.ClusterLayoutTracker;
import com.continuuity.loom.store.ClusterStore;
import com.continuuity.loom.store.EntityStore;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;

/**
 * Class that takes in an existing cluster and a request to update the cluster in some way, whether its by adding
 * services, removing services, adding nodes, or removing nodes.
 */
public class ClusterLayoutUpdater {
  private final ClusterStore clusterStore;
  private final EntityStore entityStore;

  @Inject
  ClusterLayoutUpdater(ClusterStore clusterStore, EntityStore entityStore) {
    this.clusterStore = clusterStore;
    this.entityStore = entityStore;
  }

  public ClusterLayoutTracker addServicesToCluster(String clusterId, Set<String> servicesToAdd) throws Exception {
    Cluster cluster = clusterStore.getCluster(clusterId);
    if (cluster == null) {
      throw new IllegalArgumentException("cluster " + clusterId + " does not exist.");
    }
    Set<Node> clusterNodes = clusterStore.getClusterNodes(clusterId);
    if (clusterNodes == null || clusterNodes.isEmpty()) {
      throw new IllegalArgumentException("cluster " + clusterId + " has no nodes.");
    }
    validateServicesToAdd(cluster, servicesToAdd);

    Constraints clusterConstraints = cluster.getClusterTemplate().getConstraints();
    ClusterLayout clusterLayout = ClusterLayout.fromNodes(clusterNodes, clusterConstraints);

    // heuristic: try and add services in order of lowest max count allowed.
    Set<String> servicesToAddCopy = Sets.newHashSet(servicesToAdd);
    SortedSet<Map.Entry<String, ServiceConstraint>> sortedConstraints = Sets.newTreeSet(new ServiceMaxComparator());
    sortedConstraints.addAll(clusterConstraints.getServiceConstraints().entrySet());
    Queue<String> sortedServices = Lists.newLinkedList();
    for (Map.Entry<String, ServiceConstraint> entry : sortedConstraints) {
      if (servicesToAddCopy.contains(entry.getKey())) {
        sortedServices.add(entry.getKey());
        servicesToAddCopy.remove(entry.getKey());
      }
    }
    // any service without a constraint has no limit on the number of nodes it can be placed on, so add them to the end
    sortedServices.addAll(servicesToAddCopy);

    ClusterLayoutTracker tracker = new ClusterLayoutTracker(clusterLayout);
    return canAddServicesToCluster(tracker, sortedServices) ? tracker : null;
  }

  private void validateServicesToAdd(Cluster cluster, Set<String> servicesToAdd) throws Exception {
    Preconditions.checkArgument(servicesToAdd != null && !servicesToAdd.isEmpty(),
                                "At least one service to add must be specified.");

    // check compatibility
    Set<String> compatibleServices = cluster.getClusterTemplate().getCompatibilities().getServices();
    Set<String> incompatibleServices = Sets.difference(servicesToAdd, compatibleServices);
    if (!incompatibleServices.isEmpty()) {
      String incompatibleStr = Joiner.on(',').join(incompatibleServices);
      throw new IllegalArgumentException(incompatibleStr + " are incompatible with the cluster");
    }

    // check dependencies
    boolean dependenciesSatisfied = true;
    StringBuilder errMsg = new StringBuilder();
    Set<String> existingClusterServices = cluster.getServices();
    for (String serviceName : servicesToAdd) {
      Service service = entityStore.getService(serviceName);
      if (service == null) {
        throw new IllegalArgumentException(serviceName + " does not exist");
      }
      for (String serviceDependency : service.getDependsOn()) {
        if (!existingClusterServices.contains(serviceDependency) && !servicesToAdd.contains(serviceDependency)) {
          dependenciesSatisfied = false;
          errMsg.append(serviceName);
          errMsg.append(" requires ");
          errMsg.append(serviceDependency);
          errMsg.append(", which is not on the cluster or in the list of services to add.");
        }
      }
    }
    if (!dependenciesSatisfied) {
      throw new IllegalArgumentException(errMsg.toString());
    }
  }

  private boolean canAddServicesToCluster(ClusterLayoutTracker tracker, Queue<String> servicesToAdd) {
    if (servicesToAdd.isEmpty()) {
      return true;
    }

    String service = servicesToAdd.remove();
    ClusterLayout currentLayout = tracker.getCurrentLayout();
    // find valid moves, where a move is adding some number of the first service in the queue to nodes in the cluster
    Iterator<ClusterLayoutChange> changes = new AddServiceChangeIterator(currentLayout, service);

    while (changes.hasNext()) {
      // expand the cluster
      ClusterLayoutChange change = changes.next();
      if (tracker.addChangeIfValid(change)) {
        // though the change was applied, the layout may not satisfy all constraints
        ClusterLayout nextLayout = tracker.getCurrentLayout();
        if (!nextLayout.isValid()) {
          // if constraints were not all satisfied, remove the last change and keep searching
          tracker.removeLastChange();
          continue;
        }

        // successfully added the service. See if we can add the rest of the services.
        if (canAddServicesToCluster(tracker, servicesToAdd)) {
          return true;
        } else {
          // we were not able to add the rest of the services. Move on to the next change for this service.
          tracker.removeLastChange();
        }
      }
    }
    return false;
  }

  /**
   * Comparator to sort services constraints so that services are sorted first by their max count (lower max count means
   * the constraint is lower), sorted next by their min count (higher min count means the constraint is lower), and
   * finally by the service name if all else is equal.
   */
  private class ServiceMaxComparator implements Comparator<Map.Entry<String, ServiceConstraint>> {

    @Override
    public int compare(Map.Entry<String, ServiceConstraint> entry1, Map.Entry<String, ServiceConstraint> entry2) {
      ServiceConstraint constraint1 = entry1.getValue();
      ServiceConstraint constraint2 = entry2.getValue();
      if (constraint1 == null && constraint2 != null) {
        return 1;
      } else if (constraint1 != null && constraint2 == null) {
        return -1;
      } else if (constraint1 != null) {
        int compare = ((Integer) constraint1.getMaxCount()).compareTo(constraint2.getMaxCount());
        if (compare != 0) {
          return compare;
        }
        compare = 0 - ((Integer) constraint1.getMinCount()).compareTo(constraint2.getMinCount());
        if (compare != 0) {
          return compare;
        }
      }
      return entry1.getKey().compareTo(entry2.getKey());
    }
  }
}
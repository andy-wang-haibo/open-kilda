/* Copyright 2019 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.wfm.topology.flowhs.fsm.reroute.actions;

import static java.lang.String.format;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.persistence.FetchStrategy;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.history.model.FlowDumpData;
import org.openkilda.wfm.share.history.model.FlowDumpData.DumpType;
import org.openkilda.wfm.share.mappers.HistoryMapper;
import org.openkilda.wfm.topology.flow.model.FlowPathPair;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.BaseFlowPathRemovalAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class RevertResourceAllocationAction extends
        BaseFlowPathRemovalAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {
    private final FlowResourcesManager resourcesManager;

    public RevertResourceAllocationAction(PersistenceManager persistenceManager,
                                          FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        this.resourcesManager = resourcesManager;
    }

    @Override
    protected void perform(State from, State to, Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        persistenceManager.getTransactionManager().doInTransaction(() -> {
            Flow flow = getFlow(stateMachine.getFlowId(), FetchStrategy.ALL_RELATIONS);

            FlowResources newPrimaryResources = stateMachine.getNewPrimaryResources();
            if (newPrimaryResources != null) {
                saveHistory(stateMachine, flow, newPrimaryResources);
                resourcesManager.deallocatePathResources(newPrimaryResources);
            }

            FlowResources newProtectedResources = stateMachine.getNewProtectedResources();
            if (newProtectedResources != null) {
                saveHistory(stateMachine, flow, newProtectedResources);
                resourcesManager.deallocatePathResources(newProtectedResources);
            }

            stateMachine.getRejectedResources().forEach(flowResources -> {
                saveHistory(stateMachine, flow, flowResources);
                resourcesManager.deallocatePathResources(flowResources);
            });

            FlowPath newPrimaryForward = null;
            FlowPath newPrimaryReverse = null;
            if (stateMachine.getNewPrimaryForwardPath() != null
                    && stateMachine.getNewPrimaryReversePath() != null) {
                newPrimaryForward = getFlowPath(stateMachine.getNewPrimaryForwardPath());
                newPrimaryReverse = getFlowPath(stateMachine.getNewPrimaryReversePath());
            }

            FlowPath newProtectedForward = null;
            FlowPath newProtectedReverse = null;
            if (stateMachine.getNewProtectedForwardPath() != null
                    && stateMachine.getNewProtectedReversePath() != null) {
                newProtectedForward = getFlowPath(stateMachine.getNewProtectedForwardPath());
                newProtectedReverse = getFlowPath(stateMachine.getNewProtectedReversePath());
            }

            List<FlowPath> flowPaths = Lists.newArrayList(newPrimaryForward, newPrimaryReverse,
                    newProtectedForward, newProtectedReverse);
            List<FlowPath> rejectedFlowPaths = stateMachine.getRejectedPaths().stream()
                    .map(this::getFlowPath)
                    .collect(Collectors.toList());
            flowPaths.addAll(rejectedFlowPaths);

            flowPathRepository.lockInvolvedSwitches(
                    flowPaths.stream().filter(Objects::nonNull).toArray(FlowPath[]::new));

            if (newPrimaryForward != null && newPrimaryReverse != null) {
                log.debug("Removing the new primary paths {} / {}", newPrimaryForward, newPrimaryReverse);
                FlowPathPair pathsToDelete = FlowPathPair.builder()
                        .forward(newPrimaryForward).reverse(newPrimaryReverse).build();
                deleteFlowPaths(pathsToDelete);

                saveRemovalActionWithDumpToHistory(stateMachine, flow, pathsToDelete);
            }

            if (newProtectedForward != null && newProtectedReverse != null) {
                log.debug("Removing the new protected paths {} / {}", newProtectedForward, newProtectedReverse);
                FlowPathPair pathsToDelete = FlowPathPair.builder()
                        .forward(newProtectedForward).reverse(newProtectedReverse).build();
                deleteFlowPaths(pathsToDelete);

                saveRemovalActionWithDumpToHistory(stateMachine, flow, pathsToDelete);
            }

            rejectedFlowPaths.forEach(flowPath -> {
                log.debug("Removing the rejected path {}", flowPath);
                deleteFlowPath(flowPath);

                saveRemovalActionWithDumpToHistory(stateMachine, flow, flowPath);
            });
        });

        stateMachine.setNewPrimaryResources(null);
        stateMachine.setNewPrimaryForwardPath(null);
        stateMachine.setNewPrimaryReversePath(null);
        stateMachine.setNewProtectedResources(null);
        stateMachine.setNewProtectedForwardPath(null);
        stateMachine.setNewProtectedReversePath(null);
    }

    private void saveHistory(FlowRerouteFsm stateMachine, Flow flow, FlowResources resources) {
        FlowDumpData flowDumpData = HistoryMapper.INSTANCE.map(flow, resources, DumpType.STATE_BEFORE);
        stateMachine.saveActionWithDumpToHistory("Flow resources were deallocated",
                format("The flow resources for %s / %s were deallocated",
                        resources.getForward().getPathId(), resources.getReverse().getPathId()), flowDumpData);
    }
}

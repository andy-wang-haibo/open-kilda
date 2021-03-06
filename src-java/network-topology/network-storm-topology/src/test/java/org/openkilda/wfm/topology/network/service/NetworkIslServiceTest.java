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

package org.openkilda.wfm.topology.network.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.openkilda.config.provider.ConfigurationProvider;
import org.openkilda.messaging.command.reroute.RerouteAffectedFlows;
import org.openkilda.messaging.info.discovery.RemoveIslDefaultRulesResult;
import org.openkilda.messaging.info.event.IslStatusUpdateNotification;
import org.openkilda.model.Isl;
import org.openkilda.model.IslDownReason;
import org.openkilda.model.IslStatus;
import org.openkilda.model.LinkProps;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.EmbeddedNeo4jDatabase;
import org.openkilda.persistence.Neo4jConfig;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionCallbackWithoutResult;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.LinkPropsRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.spi.PersistenceProvider;
import org.openkilda.stubs.ManualClock;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.share.model.IslReference;
import org.openkilda.wfm.topology.network.NetworkTopologyDashboardLogger;
import org.openkilda.wfm.topology.network.model.BfdStatusUpdate;
import org.openkilda.wfm.topology.network.model.IslDataHolder;
import org.openkilda.wfm.topology.network.model.NetworkOptions;
import org.openkilda.wfm.topology.network.model.RoundTripStatus;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.neo4j.driver.v1.exceptions.TransientException;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@RunWith(MockitoJUnitRunner.class)
public class NetworkIslServiceTest {
    private static EmbeddedNeo4jDatabase dbTestServer;

    @ClassRule
    public static TemporaryFolder fsData = new TemporaryFolder();

    private final ManualClock clock = new ManualClock();

    private final StubIslStorage islStorage = new StubIslStorage();

    private final Endpoint endpointAlpha1 = Endpoint.of(new SwitchId(1), 1);
    private final Endpoint endpointBeta2 = Endpoint.of(new SwitchId(2), 2);
    private final Map<SwitchId, Switch> allocatedSwitches = new HashMap<>();

    private final NetworkOptions options = NetworkOptions.builder()
            .dbRepeatMaxDurationSeconds(30)
            .discoveryTimeout(TimeUnit.SECONDS.toNanos(3))
            .build();

    @Mock
    private NetworkTopologyDashboardLogger dashboardLogger;

    @Mock
    private IIslCarrier carrier;

    @Mock
    private PersistenceManager persistenceManager;

    @Mock
    private TransactionManager transactionManager;

    @Mock
    private RepositoryFactory repositoryFactory;

    @Mock
    private SwitchRepository switchRepository;
    @Mock
    private SwitchPropertiesRepository switchPropertiesRepository;
    @Mock
    private IslRepository islRepository;
    @Mock
    private LinkPropsRepository linkPropsRepository;
    @Mock
    private FlowPathRepository flowPathRepository;
    @Mock
    private FeatureTogglesRepository featureTogglesRepository;

    private NetworkIslService service;

    @Before
    public void setUp() {
        when(persistenceManager.getTransactionManager()).thenReturn(transactionManager);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);

        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);
        when(repositoryFactory.createIslRepository()).thenReturn(islRepository);
        when(repositoryFactory.createLinkPropsRepository()).thenReturn(linkPropsRepository);
        when(repositoryFactory.createFlowPathRepository()).thenReturn(flowPathRepository);
        when(repositoryFactory.createFeatureTogglesRepository()).thenReturn(featureTogglesRepository);
        when(repositoryFactory.createSwitchPropertiesRepository()).thenReturn(switchPropertiesRepository);

        when(featureTogglesRepository.find()).thenReturn(Optional.empty());

        when(transactionManager.makeRetryPolicyBlank())
                .thenReturn(new RetryPolicy().withMaxRetries(2));
        doAnswer(invocation -> {
            TransactionCallbackWithoutResult<?> tr = invocation.getArgument(0);
            tr.doInTransaction();
            return null;
        }).when(transactionManager).doInTransaction(Mockito.any(TransactionCallbackWithoutResult.class));
        doAnswer(invocation -> {
            RetryPolicy retryPolicy = invocation.getArgument(0);
            TransactionCallbackWithoutResult<?> tr = invocation.getArgument(1);
            Failsafe.with(retryPolicy)
                    .run(tr::doInTransaction);
            return null;
        }).when(transactionManager)
                .doInTransaction(Mockito.any(RetryPolicy.class), Mockito.any(TransactionCallbackWithoutResult.class));

        NetworkTopologyDashboardLogger.Builder dashboardLoggerBuilder = mock(
                NetworkTopologyDashboardLogger.Builder.class);
        when(dashboardLoggerBuilder.build(any())).thenReturn(dashboardLogger);
        service = new NetworkIslService(carrier, persistenceManager, options, dashboardLoggerBuilder, clock);
    }

    private void setupIslStorageStub() {
        doAnswer(invocation -> {
            Endpoint ingress = Endpoint.of(invocation.getArgument(0), invocation.getArgument(1));
            Endpoint egress = Endpoint.of(invocation.getArgument(2), invocation.getArgument(3));
            return islStorage.lookup(ingress, egress)
                    .map(entry -> entry.toBuilder().build());  // ugly attempt to clone object
        }).when(islRepository).findByEndpoints(any(SwitchId.class), anyInt(), any(SwitchId.class), anyInt());
        doAnswer(invocation -> {
            Isl payload = invocation.getArgument(0);
            islStorage.save(payload);
            return null;
        }).when(islRepository).createOrUpdate(any(Isl.class));
    }

    @Test
    @Ignore("incomplete")
    public void initialUp() {
        dbTestServer = new EmbeddedNeo4jDatabase(fsData.getRoot());
        persistenceManager = PersistenceProvider.getInstance().createPersistenceManager(
                new ConfigurationProvider() { //NOSONAR
                    @SuppressWarnings("unchecked")
                    @Override
                    public <T> T getConfiguration(Class<T> configurationType) {
                        if (configurationType.equals(Neo4jConfig.class)) {
                            return (T) new Neo4jConfig() {
                                @Override
                                public String getUri() {
                                    return dbTestServer.getConnectionUri();
                                }

                                @Override
                                public String getLogin() {
                                    return "";
                                }

                                @Override
                                public String getPassword() {
                                    return "";
                                }

                                @Override
                                public int getConnectionPoolSize() {
                                    return 50;
                                }

                                @Override
                                public String getIndexesAuto() {
                                    return "update";
                                }
                            };
                        } else {
                            throw new UnsupportedOperationException("Unsupported configurationType "
                                                                            + configurationType);
                        }
                    }
                });
        emulateEmptyPersistentDb();

        SwitchRepository switchRepository = persistenceManager.getRepositoryFactory()
                .createSwitchRepository();
        persistenceManager.getTransactionManager().doInTransaction(() -> {
            Switch swA = Switch.builder()
                            .switchId(endpointAlpha1.getDatapath())
                            .description("alpha")
                            .build();
            switchRepository.createOrUpdate(swA);
            switchPropertiesRepository.createOrUpdate(SwitchProperties.builder()
                    .multiTable(false)
                    .supportedTransitEncapsulation(SwitchProperties.DEFAULT_FLOW_ENCAPSULATION_TYPES)
                    .switchObj(swA).build());
            Switch swB = Switch.builder()
                            .switchId(endpointBeta2.getDatapath())
                            .description("alpha")
                            .build();
            switchRepository.createOrUpdate(swB);
            switchPropertiesRepository.createOrUpdate(SwitchProperties.builder()
                    .multiTable(false)
                    .supportedTransitEncapsulation(SwitchProperties.DEFAULT_FLOW_ENCAPSULATION_TYPES)
                    .switchObj(swB).build());
        });

        IslReference ref = new IslReference(endpointAlpha1, endpointBeta2);
        IslDataHolder islData = new IslDataHolder(1000, 1000, 1000);
        service = new NetworkIslService(carrier, persistenceManager, options);
        service.islUp(ref.getSource(), ref, islData);

        System.out.println(mockingDetails(carrier).printInvocations());
        System.out.println(mockingDetails(islRepository).printInvocations());
    }

    @Test
    @Ignore("become invalid due to change initialisation logic")
    public void initialMoveEvent() {
        emulateEmptyPersistentDb();

        IslReference ref = new IslReference(endpointAlpha1, endpointBeta2);
        service.islMove(ref.getSource(), ref);

        // System.out.println(mockingDetails(carrier).printInvocations());
        verify(carrier, times(2)).triggerReroute(any(RerouteAffectedFlows.class));

        // System.out.println(mockingDetails(islRepository).printInvocations());
        verify(islRepository).createOrUpdate(argThat(
                link ->
                        link.getSrcSwitch().getSwitchId().equals(endpointAlpha1.getDatapath())
                                && link.getSrcPort() == endpointAlpha1.getPortNumber()
                                && link.getDestSwitch().getSwitchId().equals(endpointBeta2.getDatapath())
                                && link.getDestPort() == endpointBeta2.getPortNumber()
                                && link.getActualStatus() == IslStatus.INACTIVE
                                && link.getStatus() == IslStatus.INACTIVE));
        verify(islRepository).createOrUpdate(argThat(
                link ->
                        link.getSrcSwitch().getSwitchId().equals(endpointBeta2.getDatapath())
                                && link.getSrcPort() == endpointBeta2.getPortNumber()
                                && link.getDestSwitch().getSwitchId().equals(endpointAlpha1.getDatapath())
                                && link.getDestPort() == endpointAlpha1.getPortNumber()
                                && link.getActualStatus() == IslStatus.INACTIVE
                                && link.getStatus() == IslStatus.INACTIVE));

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void setIslUnstableTimeOnPortDown() {
        setupIslStorageStub();
        IslReference reference = prepareActiveIsl();

        Instant updateTime = clock.adjust(Duration.ofSeconds(1));

        // isl fail by PORT DOWN
        service.islDown(endpointAlpha1, reference, IslDownReason.PORT_DOWN);

        // ensure we have marked ISL as unstable
        Isl forward = lookupIsl(reference.getSource(), reference.getDest());
        Assert.assertEquals(updateTime, forward.getTimeUnstable());

        Isl reverse = lookupIsl(reference.getDest(), reference.getSource());
        Assert.assertEquals(updateTime, reverse.getTimeUnstable());
    }

    @Test
    public void deleteWhenActive() {
        setupIslStorageStub();
        prepareAndPerformDelete(IslStatus.ACTIVE, false);

        // ISL can't be delete in ACTIVE state
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void deleteWhenActiveMultiTable() {
        setupIslStorageStub();
        prepareAndPerformDelete(IslStatus.ACTIVE, true);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void deleteWhenInactive() {
        setupIslStorageStub();
        prepareAndPerformDelete(IslStatus.INACTIVE, false);
        verifyDelete();
    }

    @Test
    public void deleteWhenInactiveMultiTable() {
        setupIslStorageStub();
        prepareAndPerformDelete(IslStatus.INACTIVE, true);
        verifyDelete();
    }

    @Test
    public void deleteWhenMoved() {
        setupIslStorageStub();
        prepareAndPerformDelete(IslStatus.MOVED, false);
        verifyDelete();
    }

    @Test
    public void repeatOnTransientDbErrors() {
        mockPersistenceIsl(endpointAlpha1, endpointBeta2, null);
        mockPersistenceIsl(endpointBeta2, endpointAlpha1, null);

        mockPersistenceLinkProps(endpointAlpha1, endpointBeta2, null);
        mockPersistenceLinkProps(endpointBeta2, endpointAlpha1, null);

        mockPersistenceBandwidthAllocation(endpointAlpha1, endpointBeta2, 0);
        mockPersistenceBandwidthAllocation(endpointBeta2, endpointAlpha1, 0);

        doThrow(new TransientException("unit-test", "force createOrUpdate to fail"))
                .when(islRepository)
                .createOrUpdate(argThat(
                        link -> endpointAlpha1.getDatapath().equals(link.getSrcSwitch().getSwitchId())
                                && Objects.equals(endpointAlpha1.getPortNumber(), link.getSrcPort())));

        IslReference reference = new IslReference(endpointAlpha1, endpointBeta2);
        service.islUp(endpointAlpha1, reference, new IslDataHolder(100, 1, 100));

        verify(islRepository, atLeast(2))
                .createOrUpdate(argThat(
                        link -> endpointAlpha1.getDatapath().equals(link.getSrcSwitch().getSwitchId())
                                && Objects.equals(endpointAlpha1.getPortNumber(), link.getSrcPort())));
    }

    private void prepareAndPerformDelete(IslStatus initialStatus, boolean multiTable) {
        Isl islAlphaBeta = makeIsl(endpointAlpha1, endpointBeta2, multiTable)
                .actualStatus(initialStatus)
                .status(initialStatus)
                .build();
        Isl islBetaAlpha = makeIsl(endpointBeta2, endpointAlpha1, multiTable)
                .actualStatus(initialStatus)
                .status(initialStatus)
                .build();

        // prepare
        islStorage.save(islAlphaBeta);
        islStorage.save(islBetaAlpha);

        mockPersistenceLinkProps(endpointAlpha1, endpointBeta2, null);
        mockPersistenceLinkProps(endpointBeta2, endpointAlpha1, null);

        mockPersistenceBandwidthAllocation(endpointAlpha1, endpointBeta2, 0);
        mockPersistenceBandwidthAllocation(endpointBeta2, endpointAlpha1, 0);

        IslReference reference = new IslReference(endpointAlpha1, endpointBeta2);
        service.islSetupFromHistory(endpointAlpha1, reference, islAlphaBeta);

        reset(carrier);

        // remove
        service.remove(reference);

        if (multiTable && (initialStatus == IslStatus.INACTIVE || initialStatus == IslStatus.MOVED)) {
            service.islDefaultRuleDeleted(reference, new RemoveIslDefaultRulesResult(endpointAlpha1.getDatapath(),
                    endpointAlpha1.getPortNumber(), endpointBeta2.getDatapath(), endpointBeta2.getPortNumber(),
                    true));
            service.islDefaultRuleDeleted(reference, new RemoveIslDefaultRulesResult(
                    endpointBeta2.getDatapath(), endpointBeta2.getPortNumber(),
                    endpointAlpha1.getDatapath(), endpointAlpha1.getPortNumber(),
                    true));
            verify(carrier).islDefaultRulesDelete(endpointAlpha1, endpointBeta2);
            verify(carrier).islDefaultRulesDelete(endpointBeta2, endpointAlpha1);
        }
    }

    private void verifyDelete() {
        verify(carrier).bfdDisableRequest(endpointAlpha1);
        verify(carrier).bfdDisableRequest(endpointBeta2);

        verify(carrier).auxiliaryPollModeUpdateRequest(endpointAlpha1, false);
        verify(carrier).auxiliaryPollModeUpdateRequest(endpointBeta2, false);

        IslReference reference = new IslReference(endpointAlpha1, endpointBeta2);
        verify(carrier).islRemovedNotification(endpointAlpha1, reference);
        verify(carrier).islRemovedNotification(endpointBeta2, reference);

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void considerLinkPropsDataOnHistory() {
        setupIslStorageStub();

        Isl islAlphaBeta = makeIsl(endpointAlpha1, endpointBeta2, false)
                .maxBandwidth(100L)
                .build();
        Isl islBetaAlpha = makeIsl(endpointBeta2, endpointAlpha1, false)
                .maxBandwidth(100L)
                .build();

        islStorage.save(islAlphaBeta);
        islStorage.save(islBetaAlpha);

        mockPersistenceLinkProps(endpointAlpha1, endpointBeta2,
                                 makeLinkProps(endpointAlpha1, endpointBeta2)
                                         .maxBandwidth(50L)
                                         .build());
        mockPersistenceLinkProps(endpointBeta2, endpointAlpha1, null);

        mockPersistenceBandwidthAllocation(endpointAlpha1, endpointBeta2, 0L);
        mockPersistenceBandwidthAllocation(endpointBeta2, endpointAlpha1, 0L);

        IslReference reference = new IslReference(endpointAlpha1, endpointBeta2);
        service.islSetupFromHistory(endpointAlpha1, reference, islAlphaBeta);

        // need to change at least one ISL field to force DB sync
        service.islUp(endpointAlpha1, reference, new IslDataHolder(200L, 200L, 200L));

        verifyIslBandwidthUpdate(50L, 100L);
    }

    @Test
    public void considerLinkPropsDataOnCreate() {
        setupIslStorageStub();

        mockPersistenceLinkProps(endpointAlpha1, endpointBeta2,
                                 makeLinkProps(endpointAlpha1, endpointBeta2)
                                         .maxBandwidth(50L)
                                         .build());
        mockPersistenceLinkProps(endpointBeta2, endpointAlpha1, null);

        mockPersistenceBandwidthAllocation(endpointAlpha1, endpointBeta2, 0L);
        mockPersistenceBandwidthAllocation(endpointBeta2, endpointAlpha1, 0L);

        IslReference reference = new IslReference(endpointAlpha1, endpointBeta2);
        service.islUp(endpointAlpha1, reference, new IslDataHolder(200L, 200L, 200L));

        service.islUp(endpointBeta2, reference, new IslDataHolder(200L, 200L, 200L));

        verifyIslBandwidthUpdate(50L, 200L);
    }

    @Test
    public void considerLinkPropsDataOnRediscovery() {
        setupIslStorageStub();

        final Isl islAlphaBeta = makeIsl(endpointAlpha1, endpointBeta2, false)
                .defaultMaxBandwidth(300L)
                .availableBandwidth(300L)
                .build();
        final Isl islBetaAlpha = makeIsl(endpointBeta2, endpointAlpha1, false)
                .defaultMaxBandwidth(300L)
                .availableBandwidth(300L)
                .build();

        // initial discovery
        mockPersistenceLinkProps(endpointAlpha1, endpointBeta2, null);
        mockPersistenceLinkProps(endpointBeta2, endpointAlpha1, null);

        IslReference reference = new IslReference(endpointAlpha1, endpointBeta2);
        service.islUp(endpointAlpha1, reference, new IslDataHolder(300L, 300L, 300L));
        service.islUp(endpointBeta2, reference, new IslDataHolder(300L, 300L, 300L));

        verifyIslBandwidthUpdate(300L, 300L);

        // fail
        service.islDown(endpointAlpha1, reference, IslDownReason.POLL_TIMEOUT);
        service.islDown(endpointBeta2, reference, IslDownReason.POLL_TIMEOUT);

        reset(linkPropsRepository);

        // rediscovery
        mockPersistenceLinkProps(endpointAlpha1, endpointBeta2,
                                 makeLinkProps(endpointAlpha1, endpointBeta2)
                                         .maxBandwidth(50L)
                                         .build());
        mockPersistenceLinkProps(endpointBeta2, endpointAlpha1, null);

        service.islUp(endpointAlpha1, reference, new IslDataHolder(300L, 300L, 300L));
        service.islUp(endpointBeta2, reference, new IslDataHolder(300L, 300L, 300L));

        verifyIslBandwidthUpdate(50L, 300L);
    }

    @Test
    public void considerRoundTripDiscovery() {
        setupIslStorageStub();

        final IslReference reference = prepareActiveIsl();

        final Duration expirationTime = Duration.ofNanos(options.getDiscoveryTimeout());
        final Duration halfExpirationTime = Duration.ofNanos(options.getDiscoveryTimeout() / 2);

        Instant lastSeen = clock.instant();
        Instant now = clock.adjust(halfExpirationTime);
        service.roundTripStatusNotification(reference, new RoundTripStatus(reference.getSource(), lastSeen, now));

        service.islDown(reference.getSource(), reference, IslDownReason.POLL_TIMEOUT);
        service.islDown(reference.getDest(), reference, IslDownReason.POLL_TIMEOUT);

        verify(dashboardLogger, times(0)).onIslDown(reference);

        // postpone
        lastSeen = now;
        now = clock.adjust(halfExpirationTime);
        service.roundTripStatusNotification(reference, new RoundTripStatus(reference.getSource(), lastSeen, now));

        verify(dashboardLogger, times(0)).onIslDown(reference);

        // expire
        now = clock.adjust(expirationTime);
        service.roundTripStatusNotification(reference, new RoundTripStatus(reference.getSource(), lastSeen, now));
        verify(dashboardLogger).onIslDown(reference);
    }

    @Test
    public void considerRecoveryAfterRoundTrip() {
        setupIslStorageStub();

        final IslReference reference = prepareActiveIsl();

        final Duration expirationTime = Duration.ofNanos(options.getDiscoveryTimeout());
        final Duration halfExpirationTime = Duration.ofNanos(options.getDiscoveryTimeout() / 2);

        Instant lastSeen = clock.instant();
        Instant now = clock.adjust(halfExpirationTime);
        service.roundTripStatusNotification(reference, new RoundTripStatus(reference.getSource(), lastSeen, now));

        service.islDown(reference.getSource(), reference, IslDownReason.POLL_TIMEOUT);
        service.islDown(reference.getDest(), reference, IslDownReason.POLL_TIMEOUT);
        verify(dashboardLogger, times(0)).onIslDown(reference);

        // postpone
        lastSeen = now;
        now = clock.adjust(halfExpirationTime);
        service.roundTripStatusNotification(reference, new RoundTripStatus(reference.getSource(), lastSeen, now));

        verify(dashboardLogger, times(0)).onIslDown(reference);

        service.islUp(reference.getSource(), reference, new IslDataHolder(200L, 200L, 200L));
        service.islUp(reference.getDest(), reference, new IslDataHolder(200L, 200L, 200L));

        now = clock.adjust(expirationTime);
        service.roundTripStatusNotification(reference, new RoundTripStatus(reference.getSource(), lastSeen, now));
        // must not fail, because of successful discovery notifications
        verify(dashboardLogger, times(0)).onIslDown(reference);
    }

    @Test
    public void ignoreStaleRoundTripStatus() {
        setupIslStorageStub();

        final IslReference reference = prepareActiveIsl();
        final Duration expirationTime = Duration.ofNanos(options.getDiscoveryTimeout());

        Instant lastSeen = clock.instant();
        clock.adjust(expirationTime);
        Instant now = clock.adjust(expirationTime); // 2 expiration time have passed i.e. round trip have been expired
        service.roundTripStatusNotification(reference, new RoundTripStatus(reference.getSource(), lastSeen, now));

        // should fail on first poll fail event
        service.islDown(reference.getSource(), reference, IslDownReason.POLL_TIMEOUT);
        verify(dashboardLogger).onIslDown(reference);
    }

    @Test
    public void movedOverrideRoundTripState() {
        setupIslStorageStub();

        final IslReference reference = prepareActiveIsl();

        // inject round-trip status
        Instant lastSeen = clock.instant();
        service.roundTripStatusNotification(
                reference, new RoundTripStatus(reference.getDest(), lastSeen, lastSeen));

        Optional<Isl> forward = islStorage.lookup(reference.getSource(), reference.getDest());
        Assert.assertTrue(forward.isPresent());
        Assert.assertNotEquals(IslStatus.ACTIVE, forward.get().getRoundTripStatus());
        Assert.assertEquals(IslStatus.ACTIVE, forward.get().getStatus());
        Optional<Isl> reverse = islStorage.lookup(reference.getDest(), reference.getSource());
        Assert.assertTrue(reverse.isPresent());
        Assert.assertEquals(IslStatus.ACTIVE, reverse.get().getRoundTripStatus());
        Assert.assertEquals(IslStatus.ACTIVE, reverse.get().getStatus());

        service.islMove(reference.getSource(), reference);

        verify(dashboardLogger).onIslMoved(reference);
        forward = islStorage.lookup(reference.getSource(), reference.getDest());
        Assert.assertTrue(forward.isPresent());
        Assert.assertEquals(IslStatus.MOVED, forward.get().getActualStatus());
        Assert.assertEquals(IslStatus.MOVED, forward.get().getStatus());
        reverse = islStorage.lookup(reference.getDest(), reference.getSource());
        Assert.assertTrue(reverse.isPresent());
        Assert.assertNotEquals(IslStatus.MOVED, reverse.get().getActualStatus());
        Assert.assertEquals(IslStatus.MOVED, reverse.get().getStatus());

        verify(carrier).islStatusUpdateNotification(any(IslStatusUpdateNotification.class));
        verify(carrier).triggerReroute(any(RerouteAffectedFlows.class));
    }

    @Test
    public void considerBfdEventOnlyWhenBfdEnabledForBothEndpoints() {
        setupIslStorageStub();

        Isl islAlphaBeta = makeIsl(endpointAlpha1, endpointBeta2, false)
                .enableBfd(true)
                .build();
        Isl islBetaAlpha = makeIsl(endpointBeta2, endpointAlpha1, false)
                .enableBfd(true)
                .build();

        islStorage.save(islAlphaBeta);
        islStorage.save(islBetaAlpha);

        IslReference reference = new IslReference(endpointAlpha1, endpointBeta2);

        // system start
        service.islSetupFromHistory(endpointAlpha1, reference, islAlphaBeta);

        // BFD setup requests
        verify(carrier).bfdEnableRequest(eq(reference.getSource()), eq(reference));
        verify(carrier).bfdEnableRequest(eq(reference.getDest()), eq(reference));

        reset(dashboardLogger);

        // one BFD session is removed
        service.bfdStatusUpdate(endpointAlpha1, reference, BfdStatusUpdate.DOWN);

        // one BFD session is reinstalled
        service.bfdStatusUpdate(endpointAlpha1, reference, BfdStatusUpdate.UP);
        service.bfdStatusUpdate(endpointAlpha1, reference, BfdStatusUpdate.DOWN);

        // second BFD session is reinstalled
        service.bfdStatusUpdate(endpointBeta2, reference, BfdStatusUpdate.UP);
        verify(carrier).auxiliaryPollModeUpdateRequest(eq(reference.getSource()), eq(true));
        verify(carrier).auxiliaryPollModeUpdateRequest(eq(reference.getDest()), eq(true));
        service.bfdStatusUpdate(endpointAlpha1, reference, BfdStatusUpdate.UP);

        verify(dashboardLogger, never()).onIslDown(eq(reference));

        // only now BFD events must be able to control ISL state
        service.bfdStatusUpdate(endpointAlpha1, reference, BfdStatusUpdate.DOWN);
        verify(dashboardLogger, never()).onIslDown(eq(reference));  // opposite session still UP
        service.bfdStatusUpdate(endpointBeta2, reference, BfdStatusUpdate.DOWN);

        verify(dashboardLogger).onIslDown(eq(reference));  // both BFD session are down
        reset(dashboardLogger);

        service.bfdStatusUpdate(endpointAlpha1, reference, BfdStatusUpdate.UP);
        verify(dashboardLogger).onIslUp(eq(reference));  // one BFD session enough to raise ISL UP
        verifyNoMoreInteractions(dashboardLogger);
        reset(dashboardLogger);

        service.bfdStatusUpdate(endpointAlpha1, reference, BfdStatusUpdate.DOWN);
        verify(dashboardLogger).onIslDown(eq(reference));  // both BFD session are down (again)
    }

    @Test
    public void bfdUpOverriderPollDown() {
        setupIslStorageStub();

        IslReference reference = prepareBfdEnabledIsl();

        reset(dashboardLogger);
        service.islDown(reference.getSource(), reference, IslDownReason.POLL_TIMEOUT);
        verifyNoMoreInteractions(dashboardLogger);

        service.islUp(reference.getSource(), reference, new IslDataHolder(100, 100, 100));
        verifyNoMoreInteractions(dashboardLogger);

        service.bfdStatusUpdate(reference.getSource(), reference, BfdStatusUpdate.DOWN);
        service.bfdStatusUpdate(reference.getDest(), reference, BfdStatusUpdate.DOWN);

        // from poll point of view ISL is UP but BFD must force status to DOWN
        verify(dashboardLogger).onIslDown(eq(reference));
    }

    @Test
    public void portDownOverriderBfdUp() {
        setupIslStorageStub();

        IslReference reference = prepareBfdEnabledIsl();

        reset(dashboardLogger);
        service.islDown(reference.getSource(), reference, IslDownReason.PORT_DOWN);
        verify(dashboardLogger).onIslDown(reference);
    }

    private IslReference prepareBfdEnabledIsl() {
        IslReference reference = prepareActiveIsl();

        service.bfdStatusUpdate(reference.getSource(), reference, BfdStatusUpdate.UP);
        service.bfdStatusUpdate(reference.getDest(), reference, BfdStatusUpdate.UP);

        return reference;
    }

    private IslReference prepareActiveIsl() {
        // prepare data
        final Isl islAlphaBeta = makeIsl(endpointAlpha1, endpointBeta2, false).build();
        final Isl islBetaAlpha = makeIsl(endpointBeta2, endpointAlpha1, false).build();

        // setup alpha -> beta half
        IslReference reference = new IslReference(endpointAlpha1, endpointBeta2);
        service.islUp(endpointAlpha1, reference, new IslDataHolder(islAlphaBeta));
        verifyNoMoreInteractions(dashboardLogger);

        // setup beta -> alpha half
        service.islUp(endpointBeta2, reference, new IslDataHolder(islBetaAlpha));

        verify(dashboardLogger).onIslUp(reference);
        reset(dashboardLogger);

        return reference;
    }

    private IslReference prepareActiveIslOld() {
        // prepare data
        final Isl islAlphaBeta = makeIsl(endpointAlpha1, endpointBeta2, false).build();
        final Isl islBetaAlpha = makeIsl(endpointBeta2, endpointAlpha1, false).build();

        mockPersistenceIsl(endpointAlpha1, endpointBeta2, null);
        mockPersistenceIsl(endpointBeta2, endpointAlpha1, null);

        // setup alpha -> beta half
        IslReference reference = new IslReference(endpointAlpha1, endpointBeta2);
        service.islUp(endpointAlpha1, reference, new IslDataHolder(islAlphaBeta));
        verifyNoMoreInteractions(dashboardLogger);

        // setup beta -> alpha half
        reset(islRepository);
        when(islRepository.findByEndpoints(endpointAlpha1.getDatapath(), endpointAlpha1.getPortNumber(),
                                           endpointBeta2.getDatapath(), endpointBeta2.getPortNumber()))
                .thenReturn(Optional.of(islAlphaBeta.toBuilder().build()));
        when(islRepository.findByEndpoints(endpointBeta2.getDatapath(), endpointBeta2.getPortNumber(),
                                           endpointAlpha1.getDatapath(), endpointAlpha1.getPortNumber()))
                .thenReturn(Optional.of(islBetaAlpha.toBuilder().build()));
        service.islUp(endpointBeta2, reference, new IslDataHolder(islBetaAlpha));

        verify(dashboardLogger).onIslUp(reference);
        reset(dashboardLogger);

        return reference;
    }

    private void verifyIslBandwidthUpdate(long expectedForward, long expectedReverse) {
        Isl forward = lookupIsl(endpointAlpha1, endpointBeta2);
        Assert.assertEquals(expectedForward, forward.getMaxBandwidth());
        Assert.assertEquals(expectedForward, forward.getAvailableBandwidth());

        Isl reverse = lookupIsl(endpointBeta2, endpointAlpha1);
        Assert.assertEquals(expectedReverse, reverse.getMaxBandwidth());
        Assert.assertEquals(expectedReverse, reverse.getAvailableBandwidth());
    }

    private void emulateEmptyPersistentDb() {
        mockPersistenceIsl(endpointAlpha1, endpointBeta2, null);
        mockPersistenceLinkProps(endpointAlpha1, endpointBeta2, null);
        mockPersistenceBandwidthAllocation(endpointAlpha1, endpointBeta2, 0L);
    }

    private void mockPersistenceIsl(Endpoint source, Endpoint dest, Isl link) {
        when(islRepository.findByEndpoints(source.getDatapath(), source.getPortNumber(),
                                           dest.getDatapath(), dest.getPortNumber()))
                .thenReturn(Optional.ofNullable(link));
    }

    private void mockPersistenceLinkProps(Endpoint source, Endpoint dest, LinkProps props) {
        List<LinkProps> response = Collections.emptyList();
        if (props != null) {
            response = Collections.singletonList(props);
        }
        when(linkPropsRepository.findByEndpoints(source.getDatapath(), source.getPortNumber(),
                                                 dest.getDatapath(), dest.getPortNumber()))
                .thenReturn(response);
    }

    private void mockPersistenceBandwidthAllocation(Endpoint source, Endpoint dest, long allocation) {
        when(flowPathRepository.getUsedBandwidthBetweenEndpoints(source.getDatapath(), source.getPortNumber(),
                                                                    dest.getDatapath(), dest.getPortNumber()))
                .thenReturn(allocation);
    }

    private Isl.IslBuilder makeIsl(Endpoint source, Endpoint dest, boolean multiTable) {
        Switch sourceSwitch = lookupSwitchCreateMockIfAbsent(source.getDatapath(), multiTable);
        Switch destSwitch = lookupSwitchCreateMockIfAbsent(dest.getDatapath(), multiTable);
        return Isl.builder()
                .srcSwitch(sourceSwitch).srcPort(source.getPortNumber())
                .destSwitch(destSwitch).destPort(dest.getPortNumber())
                .status(IslStatus.ACTIVE)
                .actualStatus(IslStatus.ACTIVE)
                .latency(10)
                .speed(100)
                .availableBandwidth(100)
                .maxBandwidth(100)
                .defaultMaxBandwidth(100);
    }

    private LinkProps.LinkPropsBuilder makeLinkProps(Endpoint source, Endpoint dest) {
        return LinkProps.builder()
                .srcSwitchId(source.getDatapath()).srcPort(source.getPortNumber())
                .dstSwitchId(dest.getDatapath()).dstPort(dest.getPortNumber());
    }

    private Switch lookupSwitchCreateMockIfAbsent(SwitchId datapath, boolean multiTable) {
        Switch entry = allocatedSwitches.get(datapath);
        if (entry == null) {
            entry = Switch.builder()
                    .switchId(datapath)
                    .status(SwitchStatus.ACTIVE)
                    .description("autogenerated switch mock")
                    .build();
            SwitchProperties switchProperties = SwitchProperties.builder()
                    .supportedTransitEncapsulation(SwitchProperties.DEFAULT_FLOW_ENCAPSULATION_TYPES)
                    .multiTable(multiTable)
                    .build();
            allocatedSwitches.put(datapath, entry);

            when(switchRepository.findById(datapath)).thenReturn(Optional.of(entry));
            when(switchPropertiesRepository.findBySwitchId(datapath)).thenReturn(Optional.of(switchProperties));
        }

        return entry;
    }

    private Isl lookupIsl(Endpoint ingress, Endpoint egress) {
        return islStorage.lookup(ingress, egress)
                .orElseThrow(() -> new AssertionError(
                        String.format(
                                "The ISL object between %s and %s endpoints is missing",
                                ingress, egress)));
    }

    @Getter
    @AllArgsConstructor
    @EqualsAndHashCode
    private static class StubIslKey {
        private final Endpoint ingress;
        private final Endpoint egress;
    }

    private static class StubIslStorage {
        private final Map<StubIslKey, Isl> storage = new HashMap<>();

        public Optional<Isl> lookup(Isl reference) {
            return lookup(makeIngressEndpoint(reference), makeEgressEndpoint(reference));
        }

        public Optional<Isl> lookup(Endpoint ingress, Endpoint egress) {
            StubIslKey key = new StubIslKey(ingress, egress);
            return Optional.ofNullable(storage.get(key));
        }

        public void save(Isl payload) {
            StubIslKey key = new StubIslKey(makeIngressEndpoint(payload), makeEgressEndpoint(payload));
            storage.put(key, payload);
        }

        private static Endpoint makeIngressEndpoint(Isl entity) {
            return Endpoint.of(entity.getSrcSwitch().getSwitchId(), entity.getSrcPort());
        }

        private static Endpoint makeEgressEndpoint(Isl entity) {
            return Endpoint.of(entity.getDestSwitch().getSwitchId(), entity.getDestPort());
        }
    }
}

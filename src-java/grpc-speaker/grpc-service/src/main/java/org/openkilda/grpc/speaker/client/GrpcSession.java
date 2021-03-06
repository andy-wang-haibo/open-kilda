/* Copyright 2020 Telstra Open Source
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

package org.openkilda.grpc.speaker.client;

import static org.openkilda.grpc.speaker.client.GrpcOperation.DELETE_CONFIG_REMOTE_LOG_SERVER;
import static org.openkilda.grpc.speaker.client.GrpcOperation.DELETE_LOGICAL_PORT;
import static org.openkilda.grpc.speaker.client.GrpcOperation.DUMP_LOGICAL_PORTS;
import static org.openkilda.grpc.speaker.client.GrpcOperation.GET_PACKET_IN_OUT_STATS;
import static org.openkilda.grpc.speaker.client.GrpcOperation.LOGIN;
import static org.openkilda.grpc.speaker.client.GrpcOperation.SET_CONFIG_LICENSE;
import static org.openkilda.grpc.speaker.client.GrpcOperation.SET_CONFIG_REMOTE_LOG_SERVER;
import static org.openkilda.grpc.speaker.client.GrpcOperation.SET_LOGICAL_PORT;
import static org.openkilda.grpc.speaker.client.GrpcOperation.SET_LOG_MESSAGES_STATUS;
import static org.openkilda.grpc.speaker.client.GrpcOperation.SET_LOG_OF_ERRORS_STATUS;
import static org.openkilda.grpc.speaker.client.GrpcOperation.SET_PORT_CONFIG;
import static org.openkilda.grpc.speaker.client.GrpcOperation.SHOW_CONFIG_LOGICAL_PORT;
import static org.openkilda.grpc.speaker.client.GrpcOperation.SHOW_CONFIG_REMOTE_LOG_SERVER;
import static org.openkilda.grpc.speaker.client.GrpcOperation.SHOW_SWITCH_STATUS;

import org.openkilda.grpc.speaker.exception.GrpcRequestFailureException;
import org.openkilda.grpc.speaker.model.ErrorCode;
import org.openkilda.grpc.speaker.model.LicenseDto;
import org.openkilda.grpc.speaker.model.LogMessagesDto;
import org.openkilda.grpc.speaker.model.LogOferrorsDto;
import org.openkilda.grpc.speaker.model.LogicalPortDto;
import org.openkilda.grpc.speaker.model.PortConfigDto;
import org.openkilda.grpc.speaker.model.RemoteLogServerDto;

import com.google.common.net.InetAddresses;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.noviflow.AuthenticateUser;
import io.grpc.noviflow.CliReply;
import io.grpc.noviflow.License;
import io.grpc.noviflow.LogMessages;
import io.grpc.noviflow.LogOferrors;
import io.grpc.noviflow.LogicalPort;
import io.grpc.noviflow.NoviFlowGrpcGrpc;
import io.grpc.noviflow.OnOff;
import io.grpc.noviflow.PacketInOutStats;
import io.grpc.noviflow.PortConfig;
import io.grpc.noviflow.PortMode;
import io.grpc.noviflow.PortPause;
import io.grpc.noviflow.PortSpeed;
import io.grpc.noviflow.RemoteLogServer;
import io.grpc.noviflow.ShowPacketInOutStats;
import io.grpc.noviflow.ShowRemoteLogServer;
import io.grpc.noviflow.StatusSwitch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * The GRPC client session.
 */
@Slf4j
public class GrpcSession {
    private static final int PORT = 50051;

    @Value("${grpc.speaker.session.termination.timeout}")
    private int sessionTerminationTimeout;

    private ManagedChannel channel;
    private NoviFlowGrpcGrpc.NoviFlowGrpcStub stub;
    private String address;

    public GrpcSession(String address) {
        if (!InetAddresses.isInetAddress(address) && !InetAddresses.isUriInetAddress(address)) {
            throw new GrpcRequestFailureException(ErrorCode.ERRNO_23.getCode(), ErrorCode.ERRNO_23.getMessage());
        }
        this.address = address;
        this.channel = ManagedChannelBuilder.forAddress(address, PORT)
                .usePlaintext()
                .build();
        this.stub = NoviFlowGrpcGrpc.newStub(channel);
    }

    /**
     * Initiates async session shutdown.
     */
    public void shutdown() {
        log.debug("Perform messaging channel shutdown for switch {}", address);
        channel.shutdown();
        try {
            channel.awaitTermination(sessionTerminationTimeout, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error(String.format("Unable to shutdown GRPC session with switch '%s'", address), e);
        }
    }

    /**
     * Performs switch login request.
     *
     * @param user the user.
     * @param pass the password.
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<List<CliReply>> login(String user, String pass) {
        Objects.requireNonNull(user, "User name must not be null");
        Objects.requireNonNull(pass, "Password must not be null");

        AuthenticateUser authUser = AuthenticateUser.newBuilder()
                .setUsername(user)
                .setPassword(pass)
                .build();

        log.debug("Performs auth user request to switch {} with user {}", address, user);

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>(address, LOGIN);

        stub.setLoginDetails(authUser, observer);
        return observer.future;
    }

    /**
     * Performs switch status request.
     *
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<Optional<StatusSwitch>> showSwitchStatus() {
        GrpcResponseObserver<StatusSwitch> observer = new GrpcResponseObserver<>(address, SHOW_SWITCH_STATUS);

        log.info("Getting switch status for switch {}", address);

        stub.showStatusSwitch(StatusSwitch.newBuilder().build(), observer);
        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    /**
     * Performs set logical port config switch request.
     *
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<List<CliReply>> setLogicalPort(LogicalPortDto port) {
        Objects.requireNonNull(port.getLogicalPortNumber(), "Logical port number must not be null");
        Objects.requireNonNull(port.getPortNumbers(), "Port number must not be null");

        LogicalPort request = LogicalPort.newBuilder()
                .addAllPortno(port.getPortNumbers())
                .setLogicalportno(port.getLogicalPortNumber())
                .build();

        log.info("About to create logical port: {}", request);

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>(address, SET_LOGICAL_PORT);

        stub.setConfigLogicalPort(request, observer);
        return observer.future;
    }

    /**
     * Performs show switch logical port request.
     *
     * @param port the {@link LogicalPortDto} instance.
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<Optional<LogicalPort>> showConfigLogicalPort(Integer port) {
        Objects.requireNonNull(port, "Port must not be null");
        LogicalPort request = LogicalPort.newBuilder()
                .setLogicalportno(port)
                .build();

        log.info("Reading logical port {} from the switch: {}", port, address);

        GrpcResponseObserver<LogicalPort> observer = new GrpcResponseObserver<>(address, SHOW_CONFIG_LOGICAL_PORT);
        stub.showConfigLogicalPort(request, observer);

        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    /**
     * Performs dump switch logical port request.
     *
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<List<LogicalPort>> dumpLogicalPorts() {
        LogicalPort request = LogicalPort.newBuilder().build();

        log.info("Getting all logical ports for switch: {}", address);

        GrpcResponseObserver<LogicalPort> observer = new GrpcResponseObserver<>(address, DUMP_LOGICAL_PORTS);
        stub.showConfigLogicalPort(request, observer);

        return observer.future;
    }

    /**
     * Deletes a logical port.
     *
     * @param port a logical port number.
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<Optional<CliReply>> deleteLogicalPort(Integer port) {
        Objects.requireNonNull(port, "Port must not be null");

        LogicalPort logicalPort = LogicalPort.newBuilder()
                .setLogicalportno(port)
                .build();

        log.info("Deleting logical port for switch {}", address);

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>(address, DELETE_LOGICAL_PORT);
        stub.delConfigLogicalPort(logicalPort, observer);

        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    /**
     * Set log messages status on switch.
     *
     * @param logMessagesDto a log messages configuration.
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<Optional<CliReply>> setLogMessagesStatus(LogMessagesDto logMessagesDto) {
        LogMessages logMessages = LogMessages.newBuilder()
                .setStatus(OnOff.forNumber(logMessagesDto.getState().getNumber()))
                .build();

        log.info("Change enabling status of log messages for switch {}", address);

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>(address, SET_LOG_MESSAGES_STATUS);
        stub.setLogMessages(logMessages, observer);

        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    /**
     * Set log oferrors status on a switch.
     *
     * @param logOferrorsDto log oferrors data.
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<Optional<CliReply>> setLogOferrorsStatus(LogOferrorsDto logOferrorsDto) {
        LogOferrors logOferrors = LogOferrors.newBuilder()
                .setStatus(OnOff.forNumber(logOferrorsDto.getState().getNumber()))
                .build();

        log.info("Change enabling status of log OF errors for switch {}", address);

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>(address, SET_LOG_OF_ERRORS_STATUS);
        stub.setLogOferrors(logOferrors, observer);

        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    /**
     * Shows config of remote log server.
     *
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<Optional<RemoteLogServer>> showConfigRemoteLogServer() {
        ShowRemoteLogServer showRemoteLogServer = ShowRemoteLogServer.newBuilder().build();

        log.info("Get remote log server for switch {}", address);

        GrpcResponseObserver<RemoteLogServer> observer = new GrpcResponseObserver<>(
                address, SHOW_CONFIG_REMOTE_LOG_SERVER);
        stub.showConfigRemoteLogServer(showRemoteLogServer, observer);
        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    /**
     * Sets a config of remote log server on a switch.
     *
     * @param remoteServer remote log server data.
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<Optional<CliReply>> setConfigRemoteLogServer(RemoteLogServerDto remoteServer) {
        Objects.requireNonNull(remoteServer.getIpAddress(), "IP Address must not be null");
        Objects.requireNonNull(remoteServer.getPort(), "Port must not be null");

        RemoteLogServer logServer = RemoteLogServer.newBuilder()
                .setIpaddr(remoteServer.getIpAddress())
                .setPort(remoteServer.getPort())
                .build();

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>(address, SET_CONFIG_REMOTE_LOG_SERVER);
        log.info("Set remote log server for switch {}", address);
        stub.setConfigRemoteLogServer(logServer, observer);

        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    /**
     * Deletes a remote log server configuration.
     *
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<Optional<CliReply>> deleteConfigRemoteLogServer() {
        RemoteLogServer logServer = RemoteLogServer.newBuilder().build();

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>(address, DELETE_CONFIG_REMOTE_LOG_SERVER);
        log.info("Delete remote log server for switch {}", address);
        stub.delConfigRemoteLogServer(logServer, observer);

        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    /**
     * Sets a port configuration.
     *
     * @param portNumber a port number.
     * @param config a port configuration data.
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<Optional<CliReply>> setPortConfig(Integer portNumber, PortConfigDto config) {
        PortConfig.Builder builder = PortConfig.newBuilder()
                .setPortno(portNumber);
        if (config.getQueueId() != null) {
            builder.setQueueid(config.getQueueId());
        }
        if (config.getMinRate() != null) {
            builder.setMinrate(config.getMinRate());
        }
        if (config.getMaxRate() != null) {
            builder.setMaxrate(config.getMaxRate());
        }
        if (config.getWeight() != null) {
            builder.setWeight(config.getWeight());
        }
        if (config.getNativeVId() != null) {
            builder.setNativevid(config.getNativeVId());
        }
        if (config.getSpeed() != null) {
            builder.setSpeed(PortSpeed.forNumber(config.getSpeed().getNumber()));
        }
        if (config.getMode() != null) {
            builder.setMode(PortMode.forNumber(config.getMode().getNumber()));
        }
        if (config.getPause() != null) {
            builder.setPause(PortPause.forNumber(config.getPause().getNumber()));
        }
        if (config.getAutoneg() != null) {
            builder.setAutoneg(OnOff.forNumber(config.getAutoneg().getNumber()));
        }
        if (config.getNoPacketIn() != null) {
            builder.setNopacketin(OnOff.forNumber(config.getNoPacketIn().getNumber()));
        }
        if (config.getPortDown() != null) {
            builder.setPortdown(OnOff.forNumber(config.getPortDown().getNumber()));
        }
        if (config.getTrunk() != null) {
            builder.setTrunk(OnOff.forNumber(config.getTrunk().getNumber()));
        }

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>(address, SET_PORT_CONFIG);
        log.info("Set port {} configuration for switch {}", portNumber, address);

        stub.setConfigPort(builder.build(), observer);

        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    /**
     * Sets a license configuration.
     *
     * @param licenseDto a license data.
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<Optional<CliReply>> setConfigLicense(LicenseDto licenseDto) {
        License.Builder licenseBuilder = License.newBuilder();
        if (licenseDto.getLicenseFileName() != null) {
            licenseBuilder.setLicensefile(licenseDto.getLicenseFileName());
        }
        if (licenseDto.getLicenseData() != null) {
            licenseBuilder.setLicensedata(licenseDto.getLicenseData());
        }

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>(address, SET_CONFIG_LICENSE);
        log.info("Set license for switch {}", address);

        stub.setConfigLicense(licenseBuilder.build(), observer);

        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    /**
     * Performs packet in out stats request.
     *
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<Optional<PacketInOutStats>> getPacketInOutStats() {
        GrpcResponseObserver<PacketInOutStats> observer = new GrpcResponseObserver<>(address, GET_PACKET_IN_OUT_STATS);
        log.info("Getting packet in out stats for switch {}", address);
        stub.showStatsPacketInOut(ShowPacketInOutStats.newBuilder().build(), observer);
        return observer.future.thenApply(responses -> responses.stream().findFirst());
    }
}

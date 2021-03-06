package org.openkilda.functionaltests.spec.grpc

import org.openkilda.functionaltests.extension.failfast.Tidy

import spock.lang.Unroll

class GrpcCommonSpec extends GrpcBaseSpecification {

    @Tidy
    @Unroll
    def "Able to get switch status on the #switches.switchId switch"() {
        when: "Get switch status"
        def response = grpc.getSwitchStatus(switches.address)

        then: "Response is not null and needed fields are returned"
        response.serialNumber
        response.uptime
        response.kernel
        response.memUsage != null
        response.ssdUsage != null
        response.ethLinks
        response.builds
        response.cpuPercentage != null

        where:
        switches << getNoviflowSwitches()
    }

    @Tidy
    @Unroll
    def "Able to get switch packet in out stats"() {
        when: "Get switch packet in out stats"
        def response = grpc.getPacketInOutStats(switches.address)

        then: "Response is not null and needed fields are returned"
        with(response) {
            [packetInTotalPackets, packetInTotalPacketsDataplane, packetInNoMatchPackets, packetInApplyActionPackets,
            packetInInvalidTtlPackets, packetInActionSetPackets, packetInGroupPackets, packetInPacketOutPackets,
            packetOutTotalPacketsHost, packetOutTotalPacketsDataplane, packetOutEth0InterfaceUp, replyStatus].every {
                it != null
            }
        }

        where:
        switches << getNoviflowSwitches()
    }
}

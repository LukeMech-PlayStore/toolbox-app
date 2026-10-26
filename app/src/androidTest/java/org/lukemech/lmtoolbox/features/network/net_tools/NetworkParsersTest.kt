package org.lukemech.lmtoolbox.features.network.net_tools

import java.net.Inet4Address
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the network tools read and work out, on the device (its org.json, as the app's): ping's lines (Android's
 * iputils, IPv4 and IPv6, a router's errors), traceroute's answers, iperf3's JSON stream and a server's text, port
 * ranges, subnets, hosts as typed, what a LAN device seems to be. No network needed.
 */
class NetworkParsersTest {
    @Test
    fun pingLines() {
        assertEquals(PingLine.Header("example.com", "93.184.215.14"), PingParser.parse("PING example.com (93.184.215.14) 56(84) bytes of data."))
        assertEquals(
            PingLine.Reply("142.250.186.206", 1, 117, 12.3, 64),
            PingParser.parse("64 bytes from 142.250.186.206: icmp_seq=1 ttl=117 time=12.3 ms")
        )
        // Its name with the address in brackets; an IPv6 address's colons
        assertEquals("142.250.186.206", (PingParser.parse("64 bytes from waw07s05-in-f14.1e100.net (142.250.186.206): icmp_seq=2 ttl=117 time=11.9 ms") as PingLine.Reply).from)
        assertEquals(
            PingLine.Reply("2a00:1450:401b:80e::200e", 3, 57, 13.4, 64),
            PingParser.parse("64 bytes from 2a00:1450:401b:80e::200e: icmp_seq=3 ttl=57 time=13.4 ms")
        )
        assertEquals(PingLine.Error("10.0.0.1", 1, "Time to live exceeded"), PingParser.parse("From 10.0.0.1 icmp_seq=1 Time to live exceeded"))
        assertEquals(PingLine.Error("192.168.1.1", 4, "Destination Host Unreachable"), PingParser.parse("From _gateway (192.168.1.1) icmp_seq=4 Destination Host Unreachable"))
        assertEquals(PingLine.Summary(4, 3, 0, 25.0), PingParser.parse("4 packets transmitted, 3 received, 25% packet loss, time 3004ms"))
        assertEquals(PingLine.Summary(4, 0, 4, 100.0), PingParser.parse("4 packets transmitted, 0 received, +4 errors, 100% packet loss, time 3003ms"))
        assertEquals(PingLine.Rtt(11.981, 12.349, 12.876), PingParser.parse("rtt min/avg/max/mdev = 11.981/12.349/12.876/0.345 ms"))
        assertEquals(PingLine.Failure("unknown host nowhere.invalid"), PingParser.parse("ping: unknown host nowhere.invalid"))
        assertEquals(PingLine.Failure("Name or service not known"), PingParser.parse("ping: nowhere.invalid: Name or service not known"))
        assertEquals(PingLine.Failure("Network is unreachable"), PingParser.parse("connect: Network is unreachable"))
        assertNull(PingParser.parse(""))
        assertNull(PingParser.parse("--- example.com ping statistics ---"))
    }

    @Test
    fun pingResults() {
        val lines = listOf(
            "PING 1.1.1.1 (1.1.1.1) 56(84) bytes of data.",
            "64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=10.0 ms",
            // 2 lost: shown once 3 is back
            "64 bytes from 1.1.1.1: icmp_seq=3 ttl=57 time=30.0 ms",
            "64 bytes from 1.1.1.1: icmp_seq=3 ttl=57 time=31.0 ms (DUP!)",
            "4 packets transmitted, 2 received, +1 duplicates, 50% packet loss, time 3004ms",
        )
        val r = lines.mapNotNull(PingParser::parse).fold(PingResult(), PingResult::add)
        assertEquals("1.1.1.1", r.address)
        assertEquals(listOf(1, 2, 3, 4), r.rows.map { it.seq })
        assertEquals(listOf(true, false, true, false), r.rows.map { it.isReply })
        assertEquals(4, r.sent)
        assertEquals(2, r.received)
        assertEquals(50.0, r.lossPercent!!, 0.001)
        assertEquals(10.0, r.min!!, 0.001)
        assertEquals(20.0, r.avg!!, 0.001)
        assertEquals(30.0, r.max!!, 0.001)
        // A late reply takes its lost row's place
        assertTrue(r.withRow(PingRow(2, 15.0)).rows.first { it.seq == 2 }.isReply)
    }

    @Test
    fun tracerouteAnswers() {
        assertEquals("10.0.0.1" to false, Traceroute.answer(listOf("PING 1.1.1.1 (1.1.1.1) 56(84) bytes of data.", "From 10.0.0.1 icmp_seq=1 Time to live exceeded")))
        assertEquals("1.1.1.1" to true, Traceroute.answer(listOf("64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=10.0 ms")))
        assertEquals(null to false, Traceroute.answer(listOf("1 packets transmitted, 0 received, 100% packet loss, time 0ms")))
    }

    @Test
    fun iperfJsonStream() {
        val lines = listOf(
            """{"event":"start","data":{"connected":[{"socket":5,"local_host":"192.168.1.20","local_port":51234,"remote_host":"192.168.1.10","remote_port":5201}],"version":"iperf 3.22"}}""",
            """{"event":"interval","data":{"streams":[],"sum":{"start":0,"end":1.0001,"seconds":1.0001,"bytes":11796480,"bits_per_second":94363285.4,"retransmits":0,"omitted":false,"sender":true}}}""",
            """{"event":"interval","data":{"streams":[],"sum":{"start":1.0001,"end":2.0003,"seconds":1.0002,"bytes":11665408,"bits_per_second":93304512.1,"retransmits":2,"omitted":false,"sender":true}}}""",
            """{"event":"end","data":{"sum_sent":{"start":0,"end":10,"seconds":10,"bytes":117964800,"bits_per_second":94371840,"retransmits":12,"sender":true},"sum_received":{"start":0,"end":10.04,"seconds":10.04,"bytes":117440512,"bits_per_second":93578100,"sender":true}}}""",
        )
        val events = lines.mapNotNull(IperfParser::json)
        assertEquals(IperfEvent.Connected("192.168.1.10"), events[0])
        assertEquals(94363285.4, (events[1] as IperfEvent.Interval).bitsPerSecond, 0.1)
        val state = events.fold(IperfState(), IperfState::add)
        assertEquals(2, state.intervals.size)
        assertEquals(93304512.1, state.now!!, 0.1)
        val summary = state.summary!!
        assertEquals(94371840.0, summary.sentBitsPerSecond!!, 0.1)
        assertEquals(93578100.0, summary.receivedBitsPerSecond!!, 0.1)
        assertEquals(12, summary.retransmits)

        // UDP's loss and jitter
        val udp = IperfParser.json(
            """{"event":"end","data":{"sum":{"start":0,"end":10,"seconds":10,"bytes":1310720,"bits_per_second":1048576,"jitter_ms":0.042,"lost_packets":3,"packets":906,"lost_percent":0.331}}}"""
        ) as IperfEvent.Summary
        assertEquals(0.042, udp.jitterMs!!, 0.0001)
        assertEquals(0.331, udp.lostPercent!!, 0.0001)
        assertEquals(1048576.0, udp.sentBitsPerSecond!!, 0.1)

        assertEquals(
            IperfEvent.Error("unable to connect to server - server may not be running: Connection refused"),
            IperfParser.json("""{"event":"error","data":"error - unable to connect to server - server may not be running: Connection refused"}""")
        )
        assertNull(IperfParser.json("iperf3: not json"))
    }

    @Test
    fun iperfServerText() {
        val text = IperfParser.Text()
        val lines = listOf(
            "-----------------------------------------------------------",
            "Server listening on 5201 (test #1)",
            "-----------------------------------------------------------",
            "Accepted connection from 192.168.1.20, port 53190",
            "[  5] local 192.168.1.10 port 5201 connected to 192.168.1.20 port 53192",
            "[  7] local 192.168.1.10 port 5201 connected to 192.168.1.20 port 53194",
            "[ ID] Interval           Transfer     Bitrate",
            "[  5]   0.00-1.00   sec  5.45 MBytes  45.7 Mbits/sec",
            "[  7]   0.00-1.00   sec  5.45 MBytes  45.7 Mbits/sec",
            "[SUM]   0.00-1.00   sec  10.9 MBytes  91.4 Mbits/sec",
            "- - - - - - - - - - - - - - - - - - - - - - - - -",
            "[SUM]   0.00-10.04  sec   111 MBytes  92.6 Mbits/sec                  receiver",
        )
        val events = lines.mapNotNull(text::parse)
        assertEquals(IperfEvent.Listening(5201), events[0])
        assertEquals(IperfEvent.Connected("192.168.1.20"), events[1])
        // Two streams: their sum alone
        assertEquals(4, events.size)
        assertEquals(91.4e6, (events[2] as IperfEvent.Interval).bitsPerSecond, 1.0)
        assertEquals(92.6e6, (events[3] as IperfEvent.Summary).receivedBitsPerSecond!!, 1.0)
        val state = events.fold(IperfState(), IperfState::add)
        assertEquals("192.168.1.20", state.peer)
        assertEquals(5201, state.listening)

        // One stream: its own lines (no [SUM]); UDP's jitter and loss after its rate
        val one = IperfParser.Text()
        one.parse("Accepted connection from 10.0.0.2, port 40000")
        one.parse("[  5] local 10.0.0.1 port 5201 connected to 10.0.0.2 port 40001")
        val udp = one.parse("[  5]   1.00-2.00   sec  1.19 MBytes  10.0 Mbits/sec  0.020 ms  0/863 (0%)") as IperfEvent.Interval
        assertEquals(10.0e6, udp.bitsPerSecond, 1.0)
        assertEquals(2.0, udp.end, 0.0)
    }

    @Test
    fun portRanges() {
        assertEquals(listOf(22, 80, 443), PortRanges.parse("443, 22,80"))
        assertEquals((8000..8003).toList() + 25565, PortRanges.parse("8000-8003 25565"))
        assertEquals(listOf(1, 2, 3), PortRanges.parse("1-3, 2"))
        assertNull(PortRanges.parse(""))
        assertNull(PortRanges.parse("0"))
        assertNull(PortRanges.parse("70000"))
        assertNull(PortRanges.parse("100-10"))
        assertNull(PortRanges.parse("1-2-3"))
        assertEquals("SSH", Services.name(22))
    }

    @Test
    fun subnets() {
        fun ip(s: String) = Subnet.toInt(InetAddress.getByName(s) as Inet4Address)
        val home = Subnet.of(ip("192.168.1.23"), 24)
        assertEquals("192.168.1.0/24", home.toString())
        assertEquals(254, home.hosts().size)
        assertEquals("192.168.1.1", Subnet.text(home.hosts().first()))
        assertEquals("192.168.1.254", Subnet.text(home.hosts().last()))
        assertEquals("10.0.4.0/22", Subnet.of(ip("10.0.5.9"), 22).toString())
        // Too large to knock at every address: the phone's /24
        assertEquals("10.20.30.0/24", Subnet.of(ip("10.20.30.40"), 8).toString())
        assertEquals(0, Subnet.of(ip("10.0.0.1"), 32).hosts().size)
    }

    @Test
    fun hostsAsTyped() {
        assertEquals("example.com", Hosts.clean(" https://example.com/path "))
        assertEquals("example.com", Hosts.clean("example.com:8080"))
        assertEquals("fe80::1", Hosts.clean("[fe80::1]:5201"))
        assertEquals("2a00:1450::200e", Hosts.clean("2a00:1450::200e"))
        assertTrue(Hosts.isLocal(InetAddress.getByName("192.168.0.10")))
        assertTrue(!Hosts.isLocal(InetAddress.getByName("1.1.1.1")))
    }

    @Test
    fun lanDeviceKinds() {
        assertEquals(LanScan.Kind.Router, LanScan.Device("192.168.1.1", isGateway = true).kind)
        assertEquals(LanScan.Kind.Printer, LanScan.Device("192.168.1.5", ports = listOf(80, 631)).kind)
        assertEquals(LanScan.Kind.Cast, LanScan.Device("192.168.1.6", services = setOf("_googlecast._tcp")).kind)
        assertEquals(LanScan.Kind.Apple, LanScan.Device("192.168.1.7", ports = listOf(62078)).kind)
        assertEquals(LanScan.Kind.Phone, LanScan.Device("192.168.1.8", name = "Pixel-8.lan").kind)
        assertEquals(LanScan.Kind.Computer, LanScan.Device("192.168.1.9", ports = listOf(445)).kind)
        assertNull(LanScan.Device("192.168.1.10").kind)
    }
}

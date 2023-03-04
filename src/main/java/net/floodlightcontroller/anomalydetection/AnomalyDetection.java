//package net.floodlightcontroller.anomalydetection;
//
//import java.util.Collection;
//import java.util.Map;
//
//import org.projectfloodlight.openflow.protocol.OFMessage;
//import org.projectfloodlight.openflow.protocol.OFType;
//import org.projectfloodlight.openflow.types.EthType;
//import org.projectfloodlight.openflow.types.IPv4Address;
//import org.projectfloodlight.openflow.types.IpProtocol;
//import org.projectfloodlight.openflow.types.MacAddress;
//import org.projectfloodlight.openflow.types.TransportPort;
//import org.projectfloodlight.openflow.types.VlanVid;
//
//import net.floodlightcontroller.core.FloodlightContext;
//import net.floodlightcontroller.core.IOFMessageListener;
//import net.floodlightcontroller.core.IOFSwitch;
//import net.floodlightcontroller.core.module.FloodlightModuleContext;
//import net.floodlightcontroller.core.module.FloodlightModuleException;
//import net.floodlightcontroller.core.module.IFloodlightModule;
//import net.floodlightcontroller.core.module.IFloodlightService;
//
//import net.floodlightcontroller.core.IFloodlightProviderService;
//
//import java.io.BufferedWriter;
//import java.io.File;
//import java.io.FileWriter;
//import java.io.IOException;
//import java.io.PrintWriter;
//import java.util.ArrayList;
//import java.util.concurrent.ConcurrentSkipListSet;
//import java.util.Set;
//
//import net.floodlightcontroller.packet.ARP;
//import net.floodlightcontroller.packet.Ethernet;
//import net.floodlightcontroller.packet.IPv4;
//import net.floodlightcontroller.packet.TCP;
//import net.floodlightcontroller.packet.UDP;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//public class AnomalyDetection implements IOFMessageListener, IFloodlightModule {
//
//	protected IFloodlightProviderService floodlightProvider;
//	protected Set<Long> macAddresses;
//	protected static Logger logger;
//	
//	@Override
//	public String getName() {
//	    return AnomalyDetection.class.getSimpleName();
//	}
//
//	@Override
//	public boolean isCallbackOrderingPrereq(OFType type, String name) {
//		// TODO Auto-generated method stub
//		return false;
//	}
//
//	@Override
//	public boolean isCallbackOrderingPostreq(OFType type, String name) {
//		// TODO Auto-generated method stub
//		return false;
//	}
//
//	@Override
//	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
//		// TODO Auto-generated method stub
//		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
//		l.add(IFloodlightProviderService.class);
//		return l;	
//	}
//
//	@Override
//	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
//	    floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
//	    macAddresses = new ConcurrentSkipListSet<Long>();
//	    logger = LoggerFactory.getLogger(AnomalyDetection.class);
//	}
//
//	@Override
//	public void startUp(FloodlightModuleContext context) {
//	    floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
//	}
//
//	@Override
//	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
////		switch (msg.getType()) {
////		case PACKET_IN:
////	        /* Retrieve the deserialized packet in message */
////	        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
////	 
////	        /* Various getters and setters are exposed in Ethernet */
////	        MacAddress srcMac = eth.getSourceMACAddress();
////	        VlanVid vlanId = VlanVid.ofVlan(eth.getVlanID());
////	 
////	        /* 
////	         * Check the ethertype of the Ethernet frame and retrieve the appropriate payload.
////	         * Note the shallow equality check. EthType caches and reuses instances for valid types.
////	         */
////	        if (eth.getEtherType() == EthType.IPv4) {
////	            /* We got an IPv4 packet; get the payload from Ethernet */
////	            IPv4 ipv4 = (IPv4) eth.getPayload();
////	             
////	            /* Various getters and setters are exposed in IPv4 */
////	            byte[] ipOptions = ipv4.getOptions();
////	            IPv4Address dstIp = ipv4.getDestinationAddress();
////	             
////	            /* 
////	             * Check the IP protocol version of the IPv4 packet's payload.
////	             */
////	            if (ipv4.getProtocol() == IpProtocol.TCP) {
////	                /* We got a TCP packet; get the payload from IPv4 */
////	                TCP tcp = (TCP) ipv4.getPayload();
////	  
////	                /* Various getters and setters are exposed in TCP */
////	                TransportPort srcPort = tcp.getSourcePort();
////	                TransportPort dstPort = tcp.getDestinationPort();
////	                short flags = tcp.getFlags();
////	                 
////	                /* Your logic here! */
////	                /* Count number of packet in */
////	                	                                            
////	            } else if (ipv4.getProtocol() == IpProtocol.UDP) {
////	                /* We got a UDP packet; get the payload from IPv4 */
////	                UDP udp = (UDP) ipv4.getPayload();
////	  
////	                /* Various getters and setters are exposed in UDP */
////	                TransportPort srcPort = udp.getSourcePort();
////	                TransportPort dstPort = udp.getDestinationPort();
////	                 
////	                /* Your logic here! */
////	            }
////	 
////	        } else if (eth.getEtherType() == EthType.ARP) {
////	            /* We got an ARP packet; get the payload from Ethernet */
////	            ARP arp = (ARP) eth.getPayload();
////	 
////	            /* Various getters and setters are exposed in ARP */
////	            boolean gratuitous = arp.isGratuitous();
////	            
////	            /* Count number of packet in */
//////                packetInNow ++; 
////	 
////	        } else {
////	            /* Unhandled ethertype */
////	        }
////	        break;
////		case ANOMALY_DETECTION:
//		if (msg.getType() == OFType.ANOMALY_DETECTION)
//		{
//			/* Retrieve the deserialized packet in message */
//			logger.info("Anomaly Detection");
//	        Ethernet eth2 = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
//	 
//	        /* Various getters and setters are exposed in Ethernet */
//	        MacAddress srcMac2 = eth2.getSourceMACAddress();
//	        VlanVid vlanId2 = VlanVid.ofVlan(eth2.getVlanID());
//	 
//	        /* 
//	         * Check the ethertype of the Ethernet frame and retrieve the appropriate payload.
//	         * Note the shallow equality check. EthType caches and reuses instances for valid types.
//	         */
//	        if (eth2.getEtherType() == EthType.IPv4) {
//	            /* We got an IPv4 packet; get the payload from Ethernet */
//	            IPv4 ipv4 = (IPv4) eth2.getPayload();
//	             
//	            /* Various getters and setters are exposed in IPv4 */
//	            byte[] ipOptions = ipv4.getOptions();
//	            IPv4Address dstIp = ipv4.getDestinationAddress();
//	             
//	            /* 
//	             * Check the IP protocol version of the IPv4 packet's payload.
//	             */
//	            if (ipv4.getProtocol() == IpProtocol.TCP) {
//	                /* We got a TCP packet; get the payload from IPv4 */
//	                TCP tcp = (TCP) ipv4.getPayload();
//	                logger.info(tcp.getPayload() + "");
//	                File file = new File("/home/iot_team/Cuong_1sw_loop/6sw_test/result/payload.txt\n");
//	                try {
//						FileWriter fw = new FileWriter(file, true);
//						BufferedWriter bw = new BufferedWriter(fw);
//						PrintWriter pw = new PrintWriter(bw);
//						pw.println(tcp.getPayload());
//						pw.close();
//					} catch (IOException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
//	                /* Various getters and setters are exposed in TCP */
//	                TransportPort srcPort = tcp.getSourcePort();
//	                TransportPort dstPort = tcp.getDestinationPort();
//	                short flags = tcp.getFlags();
//	                 
//	                /* Your logic here! */
//	                /* Count number of packet in */
//	                	                                            
//	            } else if (ipv4.getProtocol() == IpProtocol.UDP) {
//	                /* We got a UDP packet; get the payload from IPv4 */
//	                UDP udp = (UDP) ipv4.getPayload();
//	  
//	                /* Various getters and setters are exposed in UDP */
//	                TransportPort srcPort = udp.getSourcePort();
//	                TransportPort dstPort = udp.getDestinationPort();
//	                 
//	                /* Your logic here! */
//	            }
//	 
//	        } else if (eth2.getEtherType() == EthType.ARP) {
//	            /* We got an ARP packet; get the payload from Ethernet */
//	            ARP arp = (ARP) eth2.getPayload();
//	 
//	            /* Various getters and setters are exposed in ARP */
//	            boolean gratuitous = arp.isGratuitous();
//	            
//	            /* Count number of packet in */
////                packetInNow ++; 
//	 
//	        } else {
//	            /* Unhandled ethertype */
//	        }
//		}
////			 /* Retrieve the deserialized packet in message */
////			logger.info("Anomaly Detection");
////	        Ethernet eth2 = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
////	 
////	        /* Various getters and setters are exposed in Ethernet */
////	        MacAddress srcMac2 = eth2.getSourceMACAddress();
////	        VlanVid vlanId2 = VlanVid.ofVlan(eth2.getVlanID());
////	 
////	        /* 
////	         * Check the ethertype of the Ethernet frame and retrieve the appropriate payload.
////	         * Note the shallow equality check. EthType caches and reuses instances for valid types.
////	         */
////	        if (eth2.getEtherType() == EthType.IPv4) {
////	            /* We got an IPv4 packet; get the payload from Ethernet */
////	            IPv4 ipv4 = (IPv4) eth2.getPayload();
////	             
////	            /* Various getters and setters are exposed in IPv4 */
////	            byte[] ipOptions = ipv4.getOptions();
////	            IPv4Address dstIp = ipv4.getDestinationAddress();
////	             
////	            /* 
////	             * Check the IP protocol version of the IPv4 packet's payload.
////	             */
////	            if (ipv4.getProtocol() == IpProtocol.TCP) {
////	                /* We got a TCP packet; get the payload from IPv4 */
////	                TCP tcp = (TCP) ipv4.getPayload();
////	                logger.info(tcp.getPayload() + "");
////	                File file = new File("/home/iot_team/Cuong_1sw_loop/6sw_test/result/payload.txt\n");
////	                try {
////						FileWriter fw = new FileWriter(file, true);
////						BufferedWriter bw = new BufferedWriter(fw);
////						PrintWriter pw = new PrintWriter(bw);
////						pw.println(tcp.getPayload());
////						pw.close();
////					} catch (IOException e) {
////						// TODO Auto-generated catch block
////						e.printStackTrace();
////					}
////	                /* Various getters and setters are exposed in TCP */
////	                TransportPort srcPort = tcp.getSourcePort();
////	                TransportPort dstPort = tcp.getDestinationPort();
////	                short flags = tcp.getFlags();
////	                 
////	                /* Your logic here! */
////	                /* Count number of packet in */
////	                	                                            
////	            } else if (ipv4.getProtocol() == IpProtocol.UDP) {
////	                /* We got a UDP packet; get the payload from IPv4 */
////	                UDP udp = (UDP) ipv4.getPayload();
////	  
////	                /* Various getters and setters are exposed in UDP */
////	                TransportPort srcPort = udp.getSourcePort();
////	                TransportPort dstPort = udp.getDestinationPort();
////	                 
////	                /* Your logic here! */
////	            }
////	 
////	        } else if (eth2.getEtherType() == EthType.ARP) {
////	            /* We got an ARP packet; get the payload from Ethernet */
////	            ARP arp = (ARP) eth2.getPayload();
////	 
////	            /* Various getters and setters are exposed in ARP */
////	            boolean gratuitous = arp.isGratuitous();
////	            
////	            /* Count number of packet in */
//////                packetInNow ++; 
////	 
////	        } else {
////	            /* Unhandled ethertype */
////	        }
////	        break;
////	    default:
////	        break;
////	    }
//        return Command.CONTINUE;
//    }
//
//}

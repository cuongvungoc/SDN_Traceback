package net.floodlightcontroller.mactracker;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.VlanVid;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

import net.floodlightcontroller.core.IFloodlightProviderService;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.Set;

import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;
//import net.floodlightcontroller.statistics.StatisticsCollector.PortStatsCollector;
import net.floodlightcontroller.threadpool.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.concurrent.TimeUnit.*;



public class MACTracker implements IOFMessageListener, IFloodlightModule {
	
	protected IFloodlightProviderService floodlightProvider;
	protected Set<Long> macAddresses;
	protected static Logger logger;
	public int packetInCounter = 0;
	public int packetInPre = 0;
	public int packetInNow = 0;
	private static IThreadPoolService threadPoolService;
	

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return MACTracker.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// TODO Auto-generated method stub
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		return l;	
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
	    macAddresses = new ConcurrentSkipListSet<Long>();
	    logger = LoggerFactory.getLogger(MACTracker.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		
		// Start new thread to count packet in
        ScheduledExecutorService PICounter = Executors.newScheduledThreadPool(1);
        Runnable piCounter = new Runnable() {
            public void run() { //			                }catch(IOException ioe){
//            	System.out.println("Exception occurred:");
//                ioe.printStackTrace();
//            }	
//		} else { /* initialize */
//			tentativePortStats.put(npt, SwitchPortBandwidth.of(npt.getNodeId(), npt.getPortId(), U64.ZERO, U64.ZERO, U64.ZERO, pse.getRxBytes(), pse.getTxBytes()));
////			log.info("TentaticePort stats Collector called !");
//		}
            	packetInCounter = packetInNow - packetInPre;
            	packetInPre = packetInNow;
            	
            	try {
                	File file = new File("/home/cuong/FIL/packeIn_counter.csv");
//            		File file = new File("/home/cuong/FIL/PIcounter.txt");
//                	File file = new File("home/cuong/FIL/packetIn_counter.log");
                	FileWriter fw = new FileWriter(file, true);
                	BufferedWriter bw = new BufferedWriter(fw);
                	PrintWriter pw = new PrintWriter(bw);
                	logger.info("Packet in counter is called !");
                	pw.println(packetInCounter);	                	
                	pw.close();
                }catch(IOException ioe){
                	System.out.println("Exception occurred:");
                    ioe.printStackTrace();
                }
            	
            }
        }; 
        ScheduledFuture<?> piCounterHandle =
        		PICounter.scheduleAtFixedRate(piCounter, 2, 2, SECONDS);
        PICounter.schedule(new Runnable() {
                    public void run() { piCounterHandle.cancel(true); }
                }, 2 * 60 * 60, SECONDS);
	}
	
	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		// TODO Auto-generated method stub
		switch (msg.getType()) {
		case PACKET_IN:
	        /* Retrieve the deserialized packet in message */
	        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
	 
	        /* Various getters and setters are exposed in Ethernet */
	        MacAddress srcMac = eth.getSourceMACAddress();
	        VlanVid vlanId = VlanVid.ofVlan(eth.getVlanID());
	 
	        /* 
	         * Check the ethertype of the Ethernet frame and retrieve the appropriate payload.
	         * Note the shallow equality check. EthType caches and reuses instances for valid types.
	         */
	        if (eth.getEtherType() == EthType.IPv4) {
	            /* We got an IPv4 packet; get the payload from Ethernet */
	            IPv4 ipv4 = (IPv4) eth.getPayload();
	             
	            /* Various getters and setters are exposed in IPv4 */
	            byte[] ipOptions = ipv4.getOptions();
	            IPv4Address dstIp = ipv4.getDestinationAddress();
	             
	            /* 
	             * Check the IP protocol version of the IPv4 packet's payload.
	             */
	            if (ipv4.getProtocol() == IpProtocol.TCP) {
	                /* We got a TCP packet; get the payload from IPv4 */
	                TCP tcp = (TCP) ipv4.getPayload();
	  
	                /* Various getters and setters are exposed in TCP */
	                TransportPort srcPort = tcp.getSourcePort();
	                TransportPort dstPort = tcp.getDestinationPort();
	                short flags = tcp.getFlags();
	                 
	                /* Your logic here! */
	                /* Count number of packet in */
	                packetInNow ++; 
//	                logger.info("TCP Source Port: {}, TCP Destination Port: {}",srcPort.toString(), dstPort.toString());
	                
	                /* Write source port and destination port to file */
//	                
//	                Map<String, String> stats = new HashMap<>()	;
//	                stats.put(srcPort.toString(), dstPort.toString());
////	                count ++;
//	                try {
//	                	File file = new File("/home/cuong/FIL/packeIn_counter.csv");
//	                	FileWriter fw = new FileWriter(file, true);
//	                	BufferedWriter bw = new BufferedWriter(fw);
//	                	PrintWriter pw = new PrintWriter(bw);
//	                	logger.info("Prtint Writer is called !");
////	                Add a new line to the file content
////	                	pw.println("");
//	                	pw.println(srcPort.toString() +"," + dstPort.toString() + "," + packetInCounter + "," + packetInNow);	                	
//	                	pw.close();
//	                }catch(IOException ioe){
//	                	System.out.println("Exception occurred:");
//	                    ioe.printStackTrace();
//	                }
	                	                
	                                
	            } else if (ipv4.getProtocol() == IpProtocol.UDP) {
	                /* We got a UDP packet; get the payload from IPv4 */
	                UDP udp = (UDP) ipv4.getPayload();
	  
	                /* Various getters and setters are exposed in UDP */
	                TransportPort srcPort = udp.getSourcePort();
	                TransportPort dstPort = udp.getDestinationPort();
	                 
	                /* Your logic here! */
	            }
	 
	        } else if (eth.getEtherType() == EthType.ARP) {
	            /* We got an ARP packet; get the payload from Ethernet */
	            ARP arp = (ARP) eth.getPayload();
	 
	            /* Various getters and setters are exposed in ARP */
	            boolean gratuitous = arp.isGratuitous();
	            
	            /* Count number of packet in */
//                packetInNow ++; 
	 
	        } else {
	            /* Unhandled ethertype */
	        }
	        break;
	    default:
	        break;
	    }
		return Command.CONTINUE;
//		Ethernet eth =
//                IFloodlightProviderService.bcStore.get(cntx,
//                                            IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
// 
//        Long sourceMACHash = eth.getSourceMACAddress().getLong();
//        if (!macAddresses.contains(sourceMACHash)) {
//            macAddresses.add(sourceMACHash);
//            logger.info("MAC Address: {} seen on switch: {}",
//                    eth.getSourceMACAddress().toString(),
//                    sw.getId().toString());
//        }
//        return Command.CONTINUE;
	}

}

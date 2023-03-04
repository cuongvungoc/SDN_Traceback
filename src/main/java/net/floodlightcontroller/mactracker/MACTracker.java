package net.floodlightcontroller.mactracker;

import java.util.Collection;

import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFAnomalyDetection;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.Set;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.concurrent.TimeUnit.*;

public class MACTracker implements IOFMessageListener, IFloodlightModule{
	protected IFloodlightProviderService floodlightProvider;
	protected Set<Long> macAddresses;
	protected static Logger logger;
	public static int packetInCounter = 0;
	public int packetInPre = 0;
	public int packetInNow = 0;
//	public int max_buffer = 10;
//	public double buffer[] = new double[10];
//	public int count = 0;
	
//	public double mean(double buffer[]) {
//		double sum = 0.0;
//		for (int i = 0; i < buffer.length; i++) {
//			sum += buffer[i];
//		}
//		double mean = sum / buffer.length;
//		return mean;
//	}
//	
//	public double sd(double buffer[]) {
//		double temp = 0.0;
//		for (int i = 0; i < buffer.length; i++) {
//			temp += (buffer[i] - mean(buffer)) * (buffer[i] - mean(buffer));
//		}
//		double sd = Math.sqrt(temp);
//		return sd;
//	}
//	
//	void esd(double buffer[], double loop) {
//		int alpha = 3;
//		double high_threshold = mean(buffer) + alpha * sd(buffer);
//		double low_threshold = mean(buffer) - alpha * sd(buffer);
//		if (loop > high_threshold || loop < low_threshold) {
//			logger.info(loop + " is Anomaly!");
//		}
//		else {
//			// Update buffer
//			for (int i = 0; i < buffer.length - 1; i++) {
//				buffer[i] = buffer[i + 1];
//				buffer[buffer.length - 1] = loop;
//			}
//		}
//	}
	
	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return MACTracker.class.getSimpleName();
//		return null;
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
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		// TODO Auto-generated method stub
		switch (msg.getType())
		{
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
				packetInNow ++; 
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

	                /* Count number of packet in */
//	                packetInNow ++; 
	                	                	                                
	            }
	 
	        } else {
	            /* Unhandled ethertype */
	        }
	        break;
		case ANOMALY_DETECTION:
			/* Retrieve the deserialized Anomaly Detection message */
			logger.info("Anomaly Detection");
			// 37 - 66
			String temp = msg.toString();
			String data = temp.substring(temp.length() - 32 , temp.length() - 2);
//			logger.info(msg.toString());
//			logger.info(data);
			/** 
			 * Data is string in ASCII such as: 48, 46, 53, 48, 48, 48, 48, 48
			 * Split string to:				  : 48	46	53	48	48	48	48	48
			 * Convert ASCII to char:		  :	 0   .   5	0	0	0	0	0
			 * Must convert to double: 		  : 0.500000
			 * Result:						  : 0.5
			 */
			String data_dec = "";
			String[] split_str = data.split(", ", -2);
			for (String s : split_str) {
				int num = Integer.parseInt(s);
				char c = (char) num;
				data_dec += c;
			}
			double loop = Double.parseDouble(data_dec);
//			logger.info(data_dec);
			logger.info(loop + "");
			
			// Call ESD
			// Call adaptive threshold algorithm
//			if (count >= 10) {
//				esd(buffer, loop);
//			}
//			else {
//				// Fill the buffer
//				buffer[count++] = loop;
//			}
//			
//			logger.info("Buffer: " + Arrays.toString(buffer));
			
			
			
			
//			try {
//            	File file = new File("/home/iot_team/Cuong_1sw_loop/6sw_test/result/anomaly.txt");
//            	FileWriter fw = new FileWriter(file, true);
//            	BufferedWriter bw = new BufferedWriter(fw);
//            	PrintWriter pw = new PrintWriter(bw);
//            	logger.info("Open anomaly file to write !");
//            	pw.println("Anomaly");
////            	pw.println("Anomaly, loop: " + tcp.getPayload());	                	
//            	pw.close();
//            }catch(IOException ioe){
//            	System.out.println("Exception occurred:");
//                ioe.printStackTrace();
//            }
	        			
	        break;
		default:
			break;
		
		}
		return Command.CONTINUE;
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
		
//		return null;
	    Collection<Class<? extends IFloodlightService>> l =
	            new ArrayList<Class<? extends IFloodlightService>>();
	        l.add(IFloodlightProviderService.class);
	        return l;
	}


	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
	    floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
	    macAddresses = new ConcurrentSkipListSet<Long>();
	    logger = LoggerFactory.getLogger(MACTracker.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		floodlightProvider.addOFMessageListener(OFType.ANOMALY_DETECTION, this);
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		// Start new thread to count packet in
		ScheduledExecutorService PICounter = Executors.newScheduledThreadPool(1);
		Runnable piCounter = new Runnable() {
			public void run() {
				packetInCounter = packetInNow - packetInPre;
				packetInPre = packetInNow;
				logger.info("Packet in counter is called !");

//				try {
//					File file = new File("/home/iot_team/Cuong_1sw_loop/packeIn_counter.csv");
//					FileWriter fw = new FileWriter(file, true);
//					BufferedWriter bw = new BufferedWriter(fw);
//					PrintWriter pw = new PrintWriter(bw);
//					logger.info("Packet in counter is called !");
//					pw.println(packetInCounter);	                	
//					pw.close();
//				}catch(IOException ioe){
//					System.out.println("Exception occurred:");
//					ioe.printStackTrace();
//				}
				
			}
		}; 
		ScheduledFuture<?> piCounterHandle =
				PICounter.scheduleAtFixedRate(piCounter, 2, 2, SECONDS);
		PICounter.schedule(new Runnable() {
			public void run() { piCounterHandle.cancel(true); }
		}, 5 * 60 * 60, SECONDS);
				
	}
	
}

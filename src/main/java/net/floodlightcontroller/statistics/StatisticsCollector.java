package net.floodlightcontroller.statistics;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortDescProp;
import org.projectfloodlight.openflow.protocol.OFPortDescPropEthernet;
import org.projectfloodlight.openflow.protocol.OFPortDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFPortStatsEntry;
import org.projectfloodlight.openflow.protocol.OFPortStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;
import org.projectfloodlight.openflow.protocol.OFStatsType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.ver13.OFMeterSerializerVer13;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.UnsignedLong;
import com.google.common.util.concurrent.ListenableFuture;
import com.kenai.jffi.Array;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.debugcounter.IDebugCounter;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugcounter.IDebugCounterService.MetaData;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.statistics.web.SwitchStatisticsWebRoutable;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.util.Pair;

public class StatisticsCollector implements IFloodlightModule, IStatisticsService {
	private static final Logger log = LoggerFactory.getLogger(StatisticsCollector.class);

	private static IOFSwitchService switchService;
	private static IThreadPoolService threadPoolService;
	private static IRestApiService restApiService;
	protected IDebugCounterService debugCounterService;
	private IDebugCounter counterPacketOut;

	private static boolean isEnabled = false;

	private static int portStatsInterval = 10; /* could be set by REST API, so not final */
	private static int flowStatsInterval = 11;

	private static ScheduledFuture<?> portStatsCollector;
	private static ScheduledFuture<?> flowStatsCollector;
	private static ScheduledFuture<?> portDescCollector;

	private static final long BITS_PER_BYTE = 8;
	private static final long MILLIS_PER_SEC = 1000;

	private static final String INTERVAL_PORT_STATS_STR = "collectionIntervalPortStatsSeconds";
	private static final String ENABLED_STR = "enable";

	private static final HashMap<NodePortTuple, SwitchPortBandwidth> portStats = new HashMap<>();
	private static final HashMap<NodePortTuple, SwitchPortBandwidth> tentativePortStats = new HashMap<>();

	private static final HashMap<Pair<Match,DatapathId>, FlowRuleStats> flowStats = new HashMap<>();
	
	private static final HashMap<NodePortTuple, PortDesc> portDesc = new HashMap<>();

	/* Anomaly Tree */
	public int  m = 7;						// number of BS
	public int k = 6;						// number of eigenvalues need to record
	public long[] C = new long[m];			// Current eigenvalue matrix (mx1)
	public long[][] F = new long[m][k];		// m x k; all normal eigenvalues vector
	public double[] alpha = new double[k];	// k x 1; weight vector
	public double[] E = new double[m];		// m x 1; average eigenvalue in jth BS vector
	public double sum = k * (k + 1) * 1.0 / 2;
	public int tree[] = new int[m]; 
	public double list_threshold[] = new double[m];
	long elapsedTime;
	//	ArrayList<Integer> tree = new ArrayList<Integer>(); // anomaly tree
	//	public int[] sigma = new int[m];	// m x 1 ; threshold vector
	//	public String logTemp;
	

	/* Calculate max of array*/
	public long max(long[] arr) {
		long max = arr[0];
		for(int i = 1; i < arr.length; i++) {
			if(arr[i] > max)
				max = arr[i];
		}
		return max;
	}
	
	/* Calculate average vector E = F * alpha */
	public void Average() {
		for(int i = 0; i < k; i++) {
			alpha[i] = (i + 1) / sum;
		}
		for(int r = 0; r < m; r++) {
			for(int c = 0; c < k; c ++) {
				E[r] += F[r][c] * alpha[c];
			}
		}		
	}
	
	/* Anomaly detect by threshold */
	public void AnomalyTree(long[] C, int m, int K) {
		boolean anomalyCheck = false;
		// For each Base Station
		for(int j = 0; j < m ; j ++) {
			
			/* Calculate threshold
			 * If eq1 = true => add to anomaly tree
			 * else update parameter
			 * */
			
			double threshold = (Math.abs(C[j] - E[j]) / (Math.sqrt(K - 1)));
			list_threshold[j] = threshold; 
			log.info("" + threshold);
			
			if(max(F[j]) + threshold < C[j]) {
				// Add BS to anomaly tree
				tree[j] = 1;
			}
			else {
				// Update vector S
				tree[j] = 0;
				for(int i = 0; i < K - 1; i++) {
					F[j][i] = F[j][i +1];
				}
				F[j][K - 1] = C[j];
			}
		}
		
		for(int i = 0; i < m; i++) {
			if(tree[i] != 0) {
				anomalyCheck = true;	
			}
		}
		
		if(anomalyCheck) {
			log.info("Anomaly Tree: " + Arrays.toString(tree));
		}
		else {
			log.info("Normal Traffic !");
		}
		/* File log max(Sj) + threshold */
		try {
        	File fileT = new File("/home/cuong/FIL/LogFile/threshold.csv");
        	FileWriter fwT = new FileWriter(fileT, true);
        	BufferedWriter bwT = new BufferedWriter(fwT);
        	PrintWriter pwT = new PrintWriter(bwT);
        	pwT.println(Arrays.toString(list_threshold));
        	pwT.close();
        }catch(IOException ioe){
        	System.out.println("Exception occurred:");
            ioe.printStackTrace();
        }
		/* File log max(Sj) + threshold */
	}	
	/* Anomaly Tree */

	/**
	 * Run periodically to collect all port statistics. This only collects
	 * bandwidth stats right now, but it could be expanded to record other
	 * information as well. The difference between the most recent and the
	 * current RX/TX bytes is used to determine the "elapsed" bytes. A 
	 * timestamp is saved each time stats results are saved to compute the
	 * bits per second over the elapsed time. There isn't a better way to
	 * compute the precise bandwidth unless the switch were to include a
	 * timestamp in the stats reply message, which would be nice but isn't
	 * likely to happen. It would be even better if the switch recorded 
	 * bandwidth and reported bandwidth directly.
	 * 
	 * Stats are not reported unless at least two iterations have occurred
	 * for a single switch's reply. This must happen to compare the byte 
	 * counts and to get an elapsed time.
	 * 
	 * @author Ryan Izard, ryan.izard@bigswitch.com, rizard@g.clemson.edu
	 *
	 */
	protected class PortStatsCollector implements Runnable {

		@Override
		public void run() {
			log.info("Run Function call !");
			if (elapsedTime < 60) {
				for (long[] row: F)
				    Arrays.fill(row, 50000000);
			}
			for (int  i = 0; i < m; i++) {
				log.info("Vector F: " + Arrays.toString(F[i]));
			}
			
			elapsedTime += 2;
			log.info("" + elapsedTime);
			//long timestamp = System.currentTimeMillis() / 1000;
			//log.info("Time: " + timestamp);
			Map<DatapathId, List<OFStatsReply>> replies = getSwitchStatistics(switchService.getAllSwitchDpids(), OFStatsType.PORT);
			for (Entry<DatapathId, List<OFStatsReply>> e : replies.entrySet()) {
				//log.info("First Loop");
				for (OFStatsReply r : e.getValue()) {
					OFPortStatsReply psr = (OFPortStatsReply) r;
					//log.info("Second Loop");
					for (OFPortStatsEntry pse : psr.getEntries()) {
						//log.info("Third Loop");
						NodePortTuple npt = new NodePortTuple(e.getKey(), pse.getPortNo());
						SwitchPortBandwidth spb;
						if (portStats.containsKey(npt) || tentativePortStats.containsKey(npt)) {
							if (portStats.containsKey(npt)) { /* update */
								spb = portStats.get(npt);
							} else if (tentativePortStats.containsKey(npt)) { /* finish */
								spb = tentativePortStats.get(npt);
								tentativePortStats.remove(npt);
							} else {
								log.error("Inconsistent state between tentative and official port stats lists.");
								return;
							}

							/* Get counted bytes over the elapsed period. Check for counter overflow. */
							U64 rxBytesCounted;
							U64 txBytesCounted;
							if (spb.getPriorByteValueRx().compareTo(pse.getRxBytes()) > 0) { /* overflow */
								U64 upper = U64.NO_MASK.subtract(spb.getPriorByteValueRx());
								U64 lower = pse.getRxBytes();
								rxBytesCounted = upper.add(lower);
							} else {
								rxBytesCounted = pse.getRxBytes().subtract(spb.getPriorByteValueRx());
							}
							if (spb.getPriorByteValueTx().compareTo(pse.getTxBytes()) > 0) { /* overflow */
								U64 upper = U64.NO_MASK.subtract(spb.getPriorByteValueTx());
								U64 lower = pse.getTxBytes();
								txBytesCounted = upper.add(lower);
							} else {
								txBytesCounted = pse.getTxBytes().subtract(spb.getPriorByteValueTx());
							}
							long speed = getSpeed(npt);
							double timeDifSec = ((System.nanoTime() - spb.getStartTime_ns()) * 1.0 / 1000000) / MILLIS_PER_SEC;
							//elapsedTime += timeDifSec;
							
							portStats.put(npt, SwitchPortBandwidth.of(npt.getNodeId(), npt.getPortId(), 
									U64.ofRaw(speed),
									U64.ofRaw(Math.round((rxBytesCounted.getValue() * BITS_PER_BYTE) / timeDifSec)),
									U64.ofRaw(Math.round((txBytesCounted.getValue() * BITS_PER_BYTE) / timeDifSec)),
									pse.getRxBytes(), pse.getTxBytes())
									);
							
							// Cuong
	             		                	
			                try {
			                	File file = new File("/home/cuong/FIL/port1_stats.csv");
			                	File file2 = new File("/home/cuong/FIL/port2_stats.csv");
			                	File file3 = new File("/home/cuong/FIL/port3_stats.csv");
			                	File file4 = new File("/home/cuong/FIL/port4_stats.csv");
			                	File file5 = new File("/home/cuong/FIL/port5_stats.csv");
			                	File file6 = new File("/home/cuong/FIL/port6_stats.csv");
			                	File file7 = new File("/home/cuong/FIL/port7_stats.csv");
			                	
			                	FileWriter fw = new FileWriter(file, true);
			                	BufferedWriter bw = new BufferedWriter(fw);
			                	PrintWriter pw = new PrintWriter(bw);
			                	
			                	FileWriter fw2 = new FileWriter(file2, true);
			                	BufferedWriter bw2 = new BufferedWriter(fw2);
			                	PrintWriter pw2 = new PrintWriter(bw2);
			                	
			                	FileWriter fw3 = new FileWriter(file3, true);
			                	BufferedWriter bw3 = new BufferedWriter(fw3);
			                	PrintWriter pw3 = new PrintWriter(bw3);
			                	
			                	FileWriter fw4 = new FileWriter(file4, true);
			                	BufferedWriter bw4 = new BufferedWriter(fw4);
			                	PrintWriter pw4 = new PrintWriter(bw4);
			                	
			                	FileWriter fw5 = new FileWriter(file5, true);
			                	BufferedWriter bw5 = new BufferedWriter(fw5);
			                	PrintWriter pw5 = new PrintWriter(bw5);
			                	
			                	FileWriter fw6 = new FileWriter(file6, true);
			                	BufferedWriter bw6 = new BufferedWriter(fw6);
			                	PrintWriter pw6 = new PrintWriter(bw6);
			                	
			                	FileWriter fw7 = new FileWriter(file7, true);
			                	BufferedWriter bw7 = new BufferedWriter(fw7);
			                	PrintWriter pw7 = new PrintWriter(bw7);
//			                	log.info("Throughput counter is called !");
//			                	tmp += (Math.round((rxBytesCounted.getValue()) / timeDifSec));
//			                	log.info("NPT: " + npt);
//			                	log.info(npt + "," + Math.round((rxBytesCounted.getValue()) / timeDifSec)); // new commnetn
//			                	pw.println(npt + "," + Math.round((rxBytesCounted.getValue()) / timeDifSec));
//			                	pw.close();

			                	if(npt.getNodeId().getLong() == 1) {
			                		C[0] += Math.round((rxBytesCounted.getValue()) / timeDifSec);
//			                		pw.println(npt.getPortId() + "," + Math.round((rxBytesCounted.getValue()) / timeDifSec));
			                		pw.println(npt.getNodeId() + "," + C[0]);
			                		pw.close();
			                	}
			                	if(npt.getNodeId().getLong() == 2) {
			                		C[1] += Math.round((rxBytesCounted.getValue()) / timeDifSec);
			                		pw2.println(npt.getNodeId() + "," + C[1]);
				                	pw2.close();
			                	}
			                	if(npt.getNodeId().getLong() == 3) {
			                		C[2] += Math.round((rxBytesCounted.getValue()) / timeDifSec);
			                		pw3.println(npt.getNodeId() + "," + C[2]);
				                	pw3.close();
			                	}
			                	if(npt.getNodeId().getLong() == 4) {
			                		C[3] += Math.round((rxBytesCounted.getValue()) / timeDifSec);
			                		pw4.println(npt.getNodeId() + "," + C[3]);
				                	pw4.close();
			                	}
			                	if(npt.getNodeId().getLong() == 5) {
			                		C[4] += Math.round((rxBytesCounted.getValue()) / timeDifSec);
			                		pw5.println(npt.getNodeId() + "," + C[4]);
				                	pw5.close();
			                	}
			                	if(npt.getNodeId().getLong() == 6) {
			                		C[5] += Math.round((rxBytesCounted.getValue()) / timeDifSec);
			                		pw6.println(npt.getNodeId() + "," + C[5]);
				                	pw6.close();
			                	}
			                	if(npt.getNodeId().getLong() == 7) {
			                		C[6] += Math.round((rxBytesCounted.getValue()) / timeDifSec);
			                		pw7.println(npt.getNodeId() + "," + C[6]);
				                	pw7.close();
			                	}
			                }catch(IOException ioe){
			                	System.out.println("Exception occurred:");
			                    ioe.printStackTrace();
			                }	
						} else { /* initialize */
							tentativePortStats.put(npt, SwitchPortBandwidth.of(npt.getNodeId(), npt.getPortId(), U64.ZERO, U64.ZERO, U64.ZERO, pse.getRxBytes(), pse.getTxBytes()));
//							log.info("TentaticePort stats Collector called !");
						}
					}
				}
			}
			if(elapsedTime > 60) {
				/* Call anomaly tree algorithm */
				log.info("Vector C: " + Arrays.toString(C));
				log.info("Vector E: " + Arrays.toString(E));
//				log.info("Vector alpha: " + Arrays.toString(alpha));
				
				try {
	            	File cur = new File("/home/cuong/FIL/LogFile/current.csv");
	            	FileWriter fwCur = new FileWriter(cur, true);
	            	BufferedWriter bwCur = new BufferedWriter(fwCur);
	            	PrintWriter pwCur = new PrintWriter(bwCur);
	            	pwCur.println("Vector C" + "," + elapsedTime + "," + Arrays.toString(C));	
	            	pwCur.println("Vector E" + "," + elapsedTime + "," + Arrays.toString(E));	
	            	pwCur.close();
	            }catch(IOException ioe){
	            	System.out.println("Exception occurred:");
	                ioe.printStackTrace();
	            }
				
				Average();
				AnomalyTree(C, m, k);
				Arrays.fill(C, 0);				
			}
		}

		protected long getSpeed(NodePortTuple npt) {
			IOFSwitch sw = switchService.getSwitch(npt.getNodeId());
			long speed = 0;

			if(sw == null) return speed; /* could have disconnected; we'll assume zero-speed then */
			if(sw.getPort(npt.getPortId()) == null) return speed;

			/* getCurrSpeed() should handle different OpenFlow Version */
			OFVersion detectedVersion = sw.getOFFactory().getVersion();
			switch(detectedVersion){
			case OF_10:
				log.debug("Port speed statistics not supported in OpenFlow 1.0");
				break;

			case OF_11:
			case OF_12:
			case OF_13:
				speed = sw.getPort(npt.getPortId()).getCurrSpeed();
				break;

			case OF_14:
			case OF_15:
				for(OFPortDescProp p : sw.getPort(npt.getPortId()).getProperties()){
					if( p.getType() == 0 ){ /* OpenFlow 1.4 and OpenFlow 1.5 will return zero */
						speed = ((OFPortDescPropEthernet) p).getCurrSpeed();
					}
				}
				break;

			default:
				break;
			}

			return speed;

		}

	}

	/**
	 * Run periodically to collect all flow statistics from every switch.
	 */
	protected class FlowStatsCollector implements Runnable {
		@Override
		public void run() {
			flowStats.clear(); // to clear expired flows
			Map<DatapathId, List<OFStatsReply>> replies = getSwitchStatistics(switchService.getAllSwitchDpids(), OFStatsType.FLOW);
			for (Entry<DatapathId, List<OFStatsReply>> e : replies.entrySet()) {
				IOFSwitch sw = switchService.getSwitch(e.getKey());
				for (OFStatsReply r : e.getValue()) {
					OFFlowStatsReply psr = (OFFlowStatsReply) r;
					for (OFFlowStatsEntry pse : psr.getEntries()) {
						if(sw.getOFFactory().getVersion().compareTo(OFVersion.OF_15) == 0){
							log.warn("Flow Stats not supported in OpenFlow 1.5.");

						} else {
							Pair<Match, DatapathId> pair = new Pair<>(pse.getMatch(), e.getKey());
							flowStats.put(pair,FlowRuleStats.of(
									e.getKey(),
									pse.getByteCount(),
									pse.getPacketCount(),
									pse.getPriority(),
									pse.getHardTimeout(),
									pse.getIdleTimeout(),
									pse.getDurationSec()));
						}
					}
				}
			}
		}
	}

	
	/**
	 *  Run periodically to collect port description from every switch and port, so it is possible to know its state and configuration.
	 * Used in Load balancer to determine if a port is enabled.
	 */
	private class PortDescCollector implements Runnable {
		@Override
		public void run() {
			Map<DatapathId, List<OFStatsReply>> replies = getSwitchStatistics(switchService.getAllSwitchDpids(), OFStatsType.PORT_DESC);
			for (Entry<DatapathId, List<OFStatsReply>> e : replies.entrySet()) {
				for (OFStatsReply r : e.getValue()) {
					OFPortDescStatsReply psr = (OFPortDescStatsReply) r;	
					for (OFPortDesc pse : psr.getEntries()) {
						NodePortTuple npt = new NodePortTuple(e.getKey(), pse.getPortNo());
						portDesc.put(npt,PortDesc.of(e.getKey(),
								pse.getPortNo(),
								pse.getName(),
								pse.getState(),
								pse.getConfig(),
								pse.isEnabled()));						
					}
				}
			}
		}
	}


	/**
	 * Single thread for collecting switch statistics and
	 * containing the reply.
	 * 
	 * @author Ryan Izard, ryan.izard@bigswitch.com, rizard@g.clemson.edu
	 *
	 */
	private class GetStatisticsThread extends Thread {
		private List<OFStatsReply> statsReply;
		private final DatapathId switchId;
		private final OFStatsType statType;

		public GetStatisticsThread(DatapathId switchId, OFStatsType statType) {
			this.switchId = switchId;
			this.statType = statType;
			this.statsReply = null;
		}

		public List<OFStatsReply> getStatisticsReply() {
			return statsReply;
		}

		public DatapathId getSwitchId() {
			return switchId;
		}

		@Override
		public void run() {
			statsReply = getSwitchStatistics(switchId, statType);
		}
	}

	/*
	 * IFloodlightModule implementation
	 */

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<>();
		l.add(IStatisticsService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m =
				new HashMap<>();
		m.put(IStatisticsService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<>();
		l.add(IOFSwitchService.class);
		l.add(IThreadPoolService.class);
		l.add(IRestApiService.class);
		l.add(IDebugCounterService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		switchService = context.getServiceImpl(IOFSwitchService.class);
		threadPoolService = context.getServiceImpl(IThreadPoolService.class);
		restApiService = context.getServiceImpl(IRestApiService.class);
		debugCounterService = context.getServiceImpl(IDebugCounterService.class);

		Map<String, String> config = context.getConfigParams(this);
		if (config.containsKey(ENABLED_STR)) {
			try {
				isEnabled = Boolean.parseBoolean(config.get(ENABLED_STR).trim());
			} catch (Exception e) {
				log.error("Could not parse '{}'. Using default of {}", ENABLED_STR, isEnabled);
			}
		}
		log.info("Statistics collection {}", isEnabled ? "enabled" : "disabled");

		if (config.containsKey(INTERVAL_PORT_STATS_STR)) {
			try {
				portStatsInterval = Integer.parseInt(config.get(INTERVAL_PORT_STATS_STR).trim());
			} catch (Exception e) {
				log.error("Could not parse '{}'. Using default of {}", INTERVAL_PORT_STATS_STR, portStatsInterval);
			}
		}
		log.info("Port statistics collection interval set to {}s", portStatsInterval);
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		restApiService.addRestletRoutable(new SwitchStatisticsWebRoutable());
		debugCounterService.registerModule("statistics");
		if (isEnabled) {
			startStatisticsCollection();
		}
		
		counterPacketOut = debugCounterService.registerCounter("statistics", "packet-outs-written", "Packet outs written by the StatisticsCollector", MetaData.WARN);
	}

	/*
	 * IStatisticsService implementation
	 */

	@Override
	public String setPortStatsPeriod(int period) {
		portStatsInterval = period;
		return "{\"status\" : \"Port period changed to " + period + "\"}";
	}
	
	@Override
	public String setFlowStatsPeriod(int period) {
		flowStatsInterval = period;
		return "{\"status\" : \"Flow period changed to " + period + "\"}";
	}
	
	
	@Override
	public Map<NodePortTuple, PortDesc> getPortDesc() {
		return Collections.unmodifiableMap(portDesc);
	}
	
	@Override
	public PortDesc getPortDesc(DatapathId dpid, OFPort port) {
		return portDesc.get(new NodePortTuple(dpid,port));
	}
	
	
	@Override
	public Map<Pair<Match, DatapathId>, FlowRuleStats> getFlowStats(){		 
		return Collections.unmodifiableMap(flowStats);
	}

	@Override
	public Set<FlowRuleStats> getFlowStats(DatapathId dpid){
		Set<FlowRuleStats> frs = new HashSet<>();
		for(Pair<Match,DatapathId> pair: flowStats.keySet()){
			if(pair.getValue().equals(dpid))
				frs.add(flowStats.get(pair));
		}
		return frs;
	}

	@Override
	public SwitchPortBandwidth getBandwidthConsumption(DatapathId dpid, OFPort p) {
		return portStats.get(new NodePortTuple(dpid, p));
	}

	@Override
	public Map<NodePortTuple, SwitchPortBandwidth> getBandwidthConsumption() {
		return Collections.unmodifiableMap(portStats);
	}

	@Override
	public synchronized void collectStatistics(boolean collect) {
		if (collect && !isEnabled) {
			startStatisticsCollection();
			isEnabled = true;
		} else if (!collect && isEnabled) {
			stopStatisticsCollection();
			isEnabled = false;
		} 
		/* otherwise, state is not changing; no-op */
	}

	@Override
	public boolean isStatisticsCollectionEnabled() {
		return isEnabled;
	}

	/*
	 * Helper functions
	 */

	/**
	 * Start all stats threads.
	 */
	private void startStatisticsCollection() {
		portStatsCollector = threadPoolService.getScheduledExecutor().scheduleAtFixedRate(new PortStatsCollector(), portStatsInterval, portStatsInterval, TimeUnit.SECONDS);
		tentativePortStats.clear(); /* must clear out, otherwise might have huge BW result if present and wait a long time before re-enabling stats */
		flowStatsCollector = threadPoolService.getScheduledExecutor().scheduleAtFixedRate(new FlowStatsCollector(), flowStatsInterval, flowStatsInterval, TimeUnit.SECONDS);
		portDescCollector = threadPoolService.getScheduledExecutor().scheduleAtFixedRate(new PortDescCollector(), portStatsInterval, portStatsInterval, TimeUnit.SECONDS);
		log.warn("Statistics collection thread(s) started");
//		log.info("Flow Stats Collector:", getBandwidthConsumption());  // Fixed
	}

	/**
	 * Stop all stats threads.
	 */
	private void stopStatisticsCollection() {
		if (!portStatsCollector.cancel(false) || !flowStatsCollector.cancel(false) || !portDescCollector.cancel(false)) {
			log.error("Could not cancel port/flow stats threads");
		} else {
			log.warn("Statistics collection thread(s) stopped");
		}
	}

	/**
	 * Retrieve the statistics from all switches in parallel.
	 * @param dpids
	 * @param statsType
	 * @return
	 */
	private Map<DatapathId, List<OFStatsReply>> getSwitchStatistics(Set<DatapathId> dpids, OFStatsType statsType) {
		HashMap<DatapathId, List<OFStatsReply>> model = new HashMap<>();

		List<GetStatisticsThread> activeThreads = new ArrayList<>(dpids.size());
		List<GetStatisticsThread> pendingRemovalThreads = new ArrayList<>();
		GetStatisticsThread t;
		for (DatapathId d : dpids) {
			t = new GetStatisticsThread(d, statsType);
			activeThreads.add(t);
			t.start();
		}

		/* Join all the threads after the timeout. Set a hard timeout
		 * of 12 seconds for the threads to finish. If the thread has not
		 * finished the switch has not replied yet and therefore we won't
		 * add the switch's stats to the reply.
		 */
		for (int iSleepCycles = 0; iSleepCycles < portStatsInterval; iSleepCycles++) {
			for (GetStatisticsThread curThread : activeThreads) {
				if (curThread.getState() == State.TERMINATED) {
					model.put(curThread.getSwitchId(), curThread.getStatisticsReply());
					pendingRemovalThreads.add(curThread);
				}
			}

			/* remove the threads that have completed the queries to the switches */
			for (GetStatisticsThread curThread : pendingRemovalThreads) {
				activeThreads.remove(curThread);
			}

			/* clear the list so we don't try to double remove them */
			pendingRemovalThreads.clear();

			/* if we are done finish early */
			if (activeThreads.isEmpty()) {
				break;
			}

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				log.error("Interrupted while waiting for statistics", e);
			}
		}

		return model;
	}

	/**
	 * Get statistics from a switch.
	 * @param switchId
	 * @param statsType
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected List<OFStatsReply> getSwitchStatistics(DatapathId switchId, OFStatsType statsType) {
		IOFSwitch sw = switchService.getSwitch(switchId);
		ListenableFuture<?> future;
		List<OFStatsReply> values = null;
		Match match;
		if (sw != null) {
			OFStatsRequest<?> req = null;
			switch (statsType) {
			case FLOW:
				match = sw.getOFFactory().buildMatch().build();
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_11) >= 0) {
					req = sw.getOFFactory().buildFlowStatsRequest()
							.setMatch(match)
							.setOutPort(OFPort.ANY)
							.setOutGroup(OFGroup.ANY)
							.setTableId(TableId.ALL)
							.build();
				} else{
					req = sw.getOFFactory().buildFlowStatsRequest()
							.setMatch(match)
							.setOutPort(OFPort.ANY)
							.setTableId(TableId.ALL)
							.build();
				}
				break;
			case AGGREGATE:
				match = sw.getOFFactory().buildMatch().build();
				req = sw.getOFFactory().buildAggregateStatsRequest()
						.setMatch(match)
						.setOutPort(OFPort.ANY)
						.setTableId(TableId.ALL)
						.build();
				break;
			case PORT:
				req = sw.getOFFactory().buildPortStatsRequest()
				.setPortNo(OFPort.ANY)
				.build();
				break;
			case QUEUE:
				req = sw.getOFFactory().buildQueueStatsRequest()
				.setPortNo(OFPort.ANY)
				.setQueueId(UnsignedLong.MAX_VALUE.longValue())
				.build();
				break;
			case DESC:
				req = sw.getOFFactory().buildDescStatsRequest()
				.build();
				break;
			case GROUP:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
					req = sw.getOFFactory().buildGroupStatsRequest()				
							.build();
				}
				break;

			case METER:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
					req = sw.getOFFactory().buildMeterStatsRequest()
							.setMeterId(OFMeterSerializerVer13.ALL_VAL)
							.build();
				}
				break;

			case GROUP_DESC:			
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
					req = sw.getOFFactory().buildGroupDescStatsRequest()			
							.build();
				}
				break;

			case GROUP_FEATURES:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
					req = sw.getOFFactory().buildGroupFeaturesStatsRequest()
							.build();
				}
				break;

			case METER_CONFIG:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
					req = sw.getOFFactory().buildMeterConfigStatsRequest()
							.build();
				}
				break;

			case METER_FEATURES:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
					req = sw.getOFFactory().buildMeterFeaturesStatsRequest()
							.build();
				}
				break;

			case TABLE:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
					req = sw.getOFFactory().buildTableStatsRequest()
							.build();
				}
				break;

			case TABLE_FEATURES:	
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
					req = sw.getOFFactory().buildTableFeaturesStatsRequest()
							.build();		
				}
				break;
			case PORT_DESC:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
					req = sw.getOFFactory().buildPortDescStatsRequest()
							.build();
				}
				break;
			case EXPERIMENTER:		
			default:
				log.error("Stats Request Type {} not implemented yet", statsType.name());
				break;
			}

			try {
				if (req != null) {
					future = sw.writeStatsRequest(req); 
					values = (List<OFStatsReply>) future.get(portStatsInterval*1000 / 2, TimeUnit.MILLISECONDS);

				}
			} catch (Exception e) {
				log.error("Failure retrieving statistics from switch {}. {}", sw, e);
			}
		}
		return values;
	}
}
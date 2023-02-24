package net.floodlightcontroller.anomalytree;

import java.lang.Math;
import java.util.ArrayList;
import java.util.PriorityQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnomalyTree {
	private static final Logger log = LoggerFactory.getLogger(AnomalyTree.class);
	public int  m = 7;					// number of BS
	public int k = 6;					// number of eigenvalues need to record
	public long[] C = new long[m];		// m x 1; current eigenvalues vector
//	public long[] S = new long[k];		// 1 x k; last k sample time eigenvalues vector	
	public long[][] F = new long[m][k];	// m x k; all normal eigenvalues vector
	public int[] alpha = new int[k];	// k x 1; weight vector
	public int[] E = new int[m];		// m x 1; average eigenvalue in jth BS vector
	public int[] sigma = new int[m];	// m x 1 ; threshold vector
	public int sum = k * (k + 1) / 2;
	ArrayList<Integer> tree = new ArrayList<Integer>(); // anomaly tree
	public String logTemp;
	
	// Constructor
	//public AnomalyTree() {}
	
	public AnomalyTree() {
		// TODO Auto-generated constructor stub
	}
	
	public AnomalyTree(long[] C, int m, int K) {
		this.m = m;
		this.k = K;
		this.C = C;
//		Average();
//		MainProcess();
	}
	
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
			alpha[i] = 1 / sum;
		}
		for(int r = 0; r < m; r++) {
			for(int c = 0; c < k; c ++) {
				E[r] += F[r][c] * alpha[c];
			}
		}		
	}
	
	/* Anomaly detect by threshold */
	public void MainProcess() {
		// For each Base Station
		for(int j = 0; j < m ; j ++) {
			
			/* Calculate threshold
			 * If eq1 = true => add to anomaly tree
			 * else update parameter
			 * */
			
			int threshold = (int) ((C[j] - E[j]) / (Math.sqrt(k - 1)));
			if(max(F[j]) + threshold < C[j]) {
				// Add BS to anomaly tree
				tree.add(j);
			}
			else {
				// Update parameter
				for(int i = 0; i < k - 1; i++) {
					F[j][i] = F[j][i +1];
				}
				F[j][k - 1] = C[j];
//				for(int i = 0; i < k; i++) {
//					F[j][i] = S[i];					
//				}
			}
		}
		if(tree.size() == 0) {
			log.info("Normal Traffic !");
		}
		else {
			for(int i=0; i<tree.size(); i++) {
				logTemp += tree.get(i).toString();
			}
			log.info("Anomaly Tree: ", logTemp);
		}
	}
}

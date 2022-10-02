package net.floodlightcontroller.anomalytree;

import java.lang.Math;
import java.util.ArrayList;

public class AnomalyTree {
	
	public int  m = 7;					// number of BS
	public int k = 6;					// number of eigenvalues need to record
	public int[] C = new int[m];		// m x 1; current eigenvalues vector
	public int[] S = new int[k];		// 1 x k; last k sample time eigenvalues vector	
	public int[][] F = new int[m][k];	// m x k; all normal eigenvalues vector
	public int[] alpha = new int[k];	// k x 1; weight vector
	public int[] E = new int[m];		// m x 1; average eigenvalue in jth BS vector
	public int[] sigma = new int[m];	// m x 1 ; threshold vector
	public int sum = k * (k + 1) / 2;
	ArrayList<Integer> tree = new ArrayList<Integer>(); // anomaly tree
	
	// Constructor
	//public AnomalyTree() {}
	
	public AnomalyTree(int[] C, int m, int K) {
		Average();
		MainProcess();
	}
	
	/* Calculate max of array*/
	public int max(int[] arr) {
		int max = arr[0];
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
			if(max(S) + threshold < C[j]) {
				// Add BS to anomaly tree
				tree.add(j);
			}
			else {
				// Update parameter
				for(int i = 0; i < k - 1; i++) {
					S[i] = S[i +1];
				}
				S[k - 1] = C[j];
				for(int i = 0; i < k; i++) {
					F[j][i] = S[i];					
				}
			}
		}
	}
}

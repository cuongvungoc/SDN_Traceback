package net.floodlightcontroller.loop;

import java.lang.Math;

class neighbor{
	public double distance;
	public int ind_list;
}

class data{
	public static final int DATANUM = 300;
	public int id;
	public double traffic_norm;
	public double n_flow_norm;
	public neighbor neigh[] = new neighbor[DATANUM];
	public double PLOF;
	public double LoOP;
}

public class loop {
	public static final int DATANUM = 300;
	public static final int K = 6;
	public static final int lambda = 3;
	
	// Compute error function
	public static double erf(double z) {
        double t = 1.0 / (1.0 + 0.5 * Math.abs(z));

        // use Horner's method
        
        double ans = 1 - t * Math.exp( -z*z   -   1.26551223 +
                                            t * ( 1.00002368 +
                                            t * ( 0.37409196 + 
                                            t * ( 0.09678418 + 
                                            t * (-0.18628806 + 
                                            t * ( 0.27886807 + 
                                            t * (-1.13520398 + 
                                            t * ( 1.48851587 + 
                                            t * (-0.82215223 + 
                                            t * ( 0.17087277))))))))));
        if (z >= 0) return  ans;
        else        return -ans;
    }
	
	
	// Compute the distance
	public double distance(data a, data b) {
		return Math.sqrt(Math.pow(a.n_flow_norm - b.n_flow_norm, 2) + Math.pow(a.traffic_norm - b.traffic_norm, 2) );
	}
	
	// Swap 
	public void swap(neighbor a, neighbor b) {
		neighbor temp;
		temp = a;
		a = b;
		b = temp;
	}
	
	// Buble sort
	public void bubble_sort(data p, int n, int k) {
		for(int i = 0; i < k; i++) {
			for(int j = i + 1; j < n;j++) {
				if(p.neigh[i].distance > p.neigh[j].distance) {
					swap(p.neigh[i], p.neigh[j]);
				}
			}
		}
	}
	
	// Make Nearest Neighbor train, if run in real time, parse it by true
	public void make_NN_train(data p, data arr[], int n, boolean realtime) {
		for(int i=0; i<n; i++)
			arr[i].id = i;
		
		if(realtime) {
			for(int j=0; j<n; j++) {
				p.neigh[j].ind_list = j;
				p.neigh[j].distance = distance(p,arr[j]);
			}
		}else {
			for(int i=0; i<n; i++) {
				int k=0;
				for(int j=0; j<n; j++) {
					if(i!=j) {
						arr[i].neigh[k].ind_list = j;
						arr[i].neigh[k].distance = distance(arr[i], arr[j]);
					}
				}
			}
		}
	}
	
	
	public double stan_dis(data a) {
		bubble_sort(a, DATANUM, K);
		double sum = 0;
		for(int i = 0; i < K; i++) {
			sum += Math.pow(a.neigh[i].distance, 2);
		}
		double st_dis = 0;
		st_dis = Math.sqrt((sum / K));
		return st_dis;
	}
	
	public double p_dis(data a) {
		double pdist;
		pdist = lambda * stan_dis(a);
		return pdist;
	}
	
	public double ev_pdist(data arr[], data a) {
		double ev = 0;
		double sum = 0;
		for(int i=0; i<K; i++) {
			sum += a.neigh[i].distance;
		}
		for(int i=0; i<K; i++) {
			ev += (a.neigh[i].distance/sum) * p_dis(arr[a.neigh[i].ind_list]);
		}
		return ev;
	}
	
	// Compute PLOF
	public double PLOF(data p, data arr[]) {
		double plof = 0;
		double p_d = p_dis(p);
		double p_d1 = ev_pdist(arr, p);
		plof += (p_d / p_d1) - 1;
		return plof;
	}
	
	// Compute nPLOF
	public double nPLOF(data arr[], data p) {
		bubble_sort(p, DATANUM, K);
		double nplof = 0;
		for(int i=0; i<K; i++) {
			bubble_sort(arr[p.neigh[i].ind_list], DATANUM, K);
		}
		p.PLOF = PLOF(p, arr);
		nplof = Math.sqrt(Math.pow(p.PLOF, 2));
		nplof = lambda * Math.sqrt(nplof);
		return nplof;
	}
	
	public double max(double a, double b) {
		return a >= b?a:b;
	}
	
	// Compute LoOP
	public double LoOP(data p, data arr[]) {
		double loop;
		double ERF;
		double t1 = nPLOF(arr, p)*Math.sqrt(2);
		ERF = erf(p.PLOF / t1);
		loop = max(0, ERF);
		return loop;
	}
}

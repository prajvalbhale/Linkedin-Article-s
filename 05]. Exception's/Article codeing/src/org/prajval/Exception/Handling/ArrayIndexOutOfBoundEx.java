package org.prajval.Exception.Handling;

public class ArrayIndexOutOfBoundEx {
	
	public static void main(String[] args) {
		int[] prajval = new int[5];
		
		prajval[0]= 11;
		prajval[1]= 12;
		prajval[2]= 13;
		prajval[3]= 14;
		prajval[4]= 15;//Array Limit is Over Here
		prajval[5]= 16;//We add more than Array Limit
		
	}

}

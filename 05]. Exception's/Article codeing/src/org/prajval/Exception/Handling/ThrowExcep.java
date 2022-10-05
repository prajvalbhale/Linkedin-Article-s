package org.prajval.Exception.Handling;

public class ThrowExcep {
	
	public static void main(String[] args) {
		try 
		{
			throw new ArithmeticException();
		} 
		catch (ArithmeticException e) 
			{		
				e.printStackTrace();		
			}
	}

}

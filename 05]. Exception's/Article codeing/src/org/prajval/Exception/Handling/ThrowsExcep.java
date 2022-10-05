package org.prajval.Exception.Handling;
import java.io.BufferedWriter;
import java.io.FileWriter;
public class ThrowsExcep {	
	public static void forakila() throws Exception{
		BufferedWriter bw = new BufferedWriter(
				new FileWriter("janu.txt"));
		bw.write("Hello");
		bw.close();
	}
	public static void main(String[] args) throws Exception{
		try {
			forakila();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
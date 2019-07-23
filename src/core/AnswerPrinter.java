package core;

import java.io.*;

public class AnswerPrinter {
	
	static void printAnswer(String answer) {
		try {
			PrintStream ps = new PrintStream(
				new FileOutputStream(new File("result.txt")));
			ps.println(answer);
			ps.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

}

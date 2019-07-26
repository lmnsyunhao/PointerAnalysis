package core;

public class Test {
	public static final String CODE_PATH = "E:\\code\\eclipse\\project\\code";
	public static void main(String[] args) {
		AnalyzerEntrance.main(new String[]{
			CODE_PATH,
			"test.Hello" //Accurate
//			"test.FieldSensitivity" //Accurate
		});
	}
}

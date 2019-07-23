package core;

import java.io.File;

import soot.PackManager;
import soot.Transform;

public class AnalyzerEntrance {
	public static void main(String[] args) {
		String classpath = args[0] + File.pathSeparator
				+ args[0] + File.separator + "rt.jar" + File.pathSeparator
				+ args[0] + File.separator + "jce.jar";
		System.out.println(classpath);
		
		PackManager.v().getPack("wjtp").add(new Transform("wjtp.mypta", new WholeProgramTransformer()));
		soot.Main.main(new String[] {
			"-w",
//			"-p", "cg.spark", "enabled:true",
			"-p", "wjtp.mypta", "enabled:true",
			"-soot-class-path", classpath,
			args[1]
		});
	}
}

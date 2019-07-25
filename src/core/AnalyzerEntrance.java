package core;

import java.io.File;
import java.util.*;

import soot.*;
import soot.toolkits.graph.ExceptionalUnitGraph;

class WholeProgramTransformer extends SceneTransformer {
	@Override
	protected void internalTransform(String arg0, Map<String, String> arg1) {
		try {
			SootMethod sm = Scene.v().getMainMethod();
			Stack<String> stk = new Stack<String>();
			stk.push(sm.toString());
			Analysis pa = new Analysis(new ExceptionalUnitGraph(sm.retrieveActiveBody()), new HashMap<String, Set<String>>(), sm.toString(), stk);
			stk.pop();
			
			String res = pa.getResult();
			System.out.print(res);
			AnswerPrinter.printAnswer(res);
		}
		catch(Exception e) {
//			e.printStackTrace();
			System.out.println("Cannot Do Analysis");
		}
	}
}

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

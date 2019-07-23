package core;

import java.util.*;

import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.util.queue.QueueReader;

public class WholeProgramTransformer extends SceneTransformer {
	
	@Override
	protected void internalTransform(String arg0, Map<String, String> arg1) {
		SootMethod sm = Scene.v().getMainMethod();
		Analysis pa = new Analysis(new ExceptionalUnitGraph(sm.retrieveActiveBody()), new HashMap<String, Set<String>>(), sm.toString());
		for(Map.Entry<String, String> entry : pa.queries.entrySet()) {
			System.out.print(entry.getKey() + " : ");
			for(String item : pa.result.get(entry.getValue())){
				System.out.print(item + " ");
			}
			System.out.print("\n");
		}
	}

}

package core;

import java.util.*;

import soot.*;
import soot.jimple.*;
import soot.toolkits.graph.*;
import soot.toolkits.scalar.ForwardFlowAnalysis;

public class Analysis extends ForwardFlowAnalysis {
	int allocId;
	String methodName;
	Map<String, String> queries, paraMap;
	Map<String, Set<String>> result, initset;
	
	public static void print(Map<String, Set<String>> mp) {
		System.out.println("========================START=========================");
		for(Map.Entry<String, Set<String>> entry : mp.entrySet()) {
			System.out.print(entry.getKey() + " : ");
			for (String s : entry.getValue()) {
				System.out.print(s + ", ");
			}
			System.out.print("\n");
		}
		System.out.println("=========================END==========================");
	}
	
	public static void printpara(Map<String, String> mp) {
		System.out.println("========================START=========================");
		for(Map.Entry<String, String> entry : mp.entrySet()) {
			System.out.println(entry.getKey() + " <=> " + entry.getValue());
		}
		System.out.println("=========================END==========================");
	}
	
	public Analysis(DirectedGraph g, Map<String, Set<String>> init, String method){
		super(g);
		
		allocId = 0;
		methodName = method;
		paraMap = new HashMap<String, String>();
		queries = new HashMap<String, String>();
		result = new HashMap<String, Set<String>>();
		initset = init;
		
		doAnalysis();
	}
	
	protected Object newInitialFlow(){
		return new HashMap<String, Set<String>>();
	}
	
	protected Object entryInitialFlow(){
		Map<String, Set<String>> ret = new HashMap<String, Set<String>>();
		ret.putAll(initset);
		return ret;
	}
	
	protected void merge(Object src1, Object src2, Object dest) {
		Map<String, Set<String>> input1 = (Map<String, Set<String>>)src1;
		Map<String, Set<String>> input2 = (Map<String, Set<String>>)src2;
		Map<String, Set<String>> output = (Map<String, Set<String>>)dest;
		output.clear();
		output.putAll(input1);
		for(Map.Entry<String, Set<String>> entry : input2.entrySet()) {
			if(output.containsKey(entry.getKey())) {
				output.get(entry.getKey()).addAll(entry.getValue());
				continue;
			}
			output.put(entry.getKey(), entry.getValue());
		}
	}
	
	protected void copy(Object src, Object dest) {
		Map<String, Set<String>> input = (Map<String, Set<String>>)src;
		Map<String, Set<String>> output = (Map<String, Set<String>>)dest;
		output.clear();
		output.putAll(input);
	}
	
	protected void flowThrough(Object src, Object unit, Object dest) {
		Map<String, Set<String>> input = (Map<String, Set<String>>)src;
		Map<String, Set<String>> output = (Map<String, Set<String>>)dest;
		copy(input, output);
		
		Unit u = (Unit)unit;
		if(u instanceof InvokeStmt) {
			InvokeExpr ie = ((InvokeStmt) u).getInvokeExpr();
			if (ie.getMethod().toString().equals("<benchmark.internal.Benchmark: void alloc(int)>")) {
				allocId = ((IntConstant)ie.getArgs().get(0)).value;
			}
			else if (ie.getMethod().toString().equals("<benchmark.internal.Benchmark: void test(int,java.lang.Object)>")) {
				int id = ((IntConstant)ie.getArgs().get(0)).value;
				Value v = ie.getArgs().get(1);
				queries.put(Integer.toString(id), methodName + v.toString());
			}
			else {
				SootMethod sm = ie.getMethod();
				Map<String, Set<String>> init = new HashMap<String, Set<String>>();
				for(int i = 0; i < ie.getArgCount(); i++) {
					String initkey = sm.toString() + "@parameter" + Integer.toString(i);
					Set<String> initval = new HashSet<String>();
					initval.addAll(output.get(methodName + ie.getArgs().get(i).toString()));
					init.put(initkey, initval);
				}
				for(Map.Entry<String, Set<String>> entry : output.entrySet()) {
					if(entry.getKey().startsWith("#")) {
						init.put(entry.getKey(), entry.getValue());
					}
				}
				if(ie instanceof SpecialInvokeExpr) {
					String thisobjkey = sm.toString() + "@this";
					Set<String> thisobjval = new HashSet<String>();
//					if(sm.toString().contains("<benchmark.objects.A: void <init>(benchmark.objects.B)>")) {
//						System.out.println(methodName + ie.getUseBoxes());
//						for (String s : output.get(methodName + ie.getUseBoxes().get(0).getValue().toString())) {
//							System.out.print(s + ", ");
//						}
//						System.out.print("END\n");
//					}
					thisobjval.addAll(output.get(methodName + ((SpecialInvokeExpr)ie).getBase().toString()));
					init.put(thisobjkey, thisobjval);
				}
				
				Analysis pa = new Analysis(new ExceptionalUnitGraph(sm.retrieveActiveBody()), init, sm.toString());
				for(int i = 0; i < ie.getArgCount(); i++) {
					String parakey = sm.toString() + "@parameter" + Integer.toString(i);
					String paraval = pa.paraMap.get(parakey);
					output.get(methodName + ie.getArgs().get(i).toString()).addAll(pa.result.get(paraval));
				}
				if(ie instanceof SpecialInvokeExpr) {
					output.get(methodName + ((SpecialInvokeExpr)ie).getBase().toString())
						  .addAll(pa.result.get(pa.paraMap.get(sm.toString() + "@this")));
				}
				if(methodName.contains("main"))
					print(pa.result);
//				printpara(pa.paraMap);
				output.putAll(pa.result);
				queries.putAll(pa.queries);
			}
		}
		else if(u instanceof DefinitionStmt) {
			Set<String> rightSet = new HashSet<String>();
			Value left = ((DefinitionStmt)u).getLeftOp();
			Value right = ((DefinitionStmt)u).getRightOp();
			
			if (right instanceof ParameterRef) {
				String pidx = Integer.toString(((ParameterRef)right).getIndex());
				String val = methodName + left.toString();
				paraMap.put(methodName + "@parameter" + pidx, val);
				if(output.containsKey(methodName + "@parameter" + pidx))
					rightSet.addAll(output.get(methodName + "@parameter" + pidx));
			}
			else if (right instanceof ThisRef) {
				paraMap.put(methodName + "@this", methodName + left.toString());
				if(output.containsKey(methodName + "@this"))
					rightSet.addAll(output.get(methodName + "@this"));
			}
			else if (right instanceof NewExpr) {
				rightSet.add(Integer.toString(allocId));
			}
			else if (right instanceof Local) {
				rightSet.addAll(output.get(methodName + right.toString()));
			}
			else if (right instanceof InstanceFieldRef) {
				String base = ((InstanceFieldRef)right).getBase().toString();
				String field = ((InstanceFieldRef)right).getField().toString();
				for(String entry : output.get(methodName + base)) {
					if(output.containsKey("#" + entry + "." + field)) {
						rightSet.addAll(output.get("#" + entry + "." + field));
					}
				}
			}
			
			if(left instanceof Local) {
				if(!output.containsKey(methodName + left.toString())) {
					output.put(methodName + left.toString(), new HashSet<String>());
				}
				output.get(methodName + left.toString()).addAll(rightSet);
			}
			else if(left instanceof InstanceFieldRef) {
				String base = ((InstanceFieldRef)left).getBase().toString();
				String field = ((InstanceFieldRef)left).getField().toString();
				for(String entry : output.get(methodName + base)) {
					if(!output.containsKey("#" + entry + "." + field)) {
						output.put("#" + entry + "." + field, new HashSet<String>());
					}
					output.get("#" + entry + "." + field).addAll(rightSet);
				}
			}
		}
		result = output;
	}
}

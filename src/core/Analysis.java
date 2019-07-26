package core;

import java.util.*;

import soot.*;
import soot.jimple.*;
import soot.toolkits.graph.*;
import soot.toolkits.scalar.ForwardFlowAnalysis;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class Analysis extends ForwardFlowAnalysis {
	int allocId;
	String methodName; //Current function name
	Map<String, String> queries; //Store Benchmark.test in current function
	Map<String, String> paraMap; //Map the parameters to the local variable
	Map<String, Set<String>> result; //Store the result of analysis
	Map<String, Set<String>> initset; //Pass the parameter results, heap references results to the called function  
	Stack<String> funcstk; //Store function call trace, avoid recursive function call
	
	public Analysis(DirectedGraph g, Map<String, Set<String>> init, String method, Stack<String> stk){
		super(g);
		
		allocId = 0;
		methodName = method;
		paraMap = new HashMap<String, String>();
		queries = new HashMap<String, String>();
		result = new HashMap<String, Set<String>>();
		funcstk = stk;
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

	protected void copy(Object src, Object dest) {
		Map<String, Set<String>> input = (Map<String, Set<String>>)src;
		Map<String, Set<String>> output = (Map<String, Set<String>>)dest;
		output.clear();
		output.putAll(input);
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
	
	//Deal with all InvokeStmt Or InvokeExpr
	private Set<String> functionCall(InvokeExpr ie, Map<String, Set<String>> output) throws Exception {
		Set<String> ret = new HashSet<String>();
		//Get alloc location
		if (ie.getMethod().toString().equals("<benchmark.internal.Benchmark: void alloc(int)>")) {
			allocId = ((IntConstant)ie.getArgs().get(0)).value;
		}
		//Store queries
		else if (ie.getMethod().toString().equals("<benchmark.internal.Benchmark: void test(int,java.lang.Object)>")) {
			int id = ((IntConstant)ie.getArgs().get(0)).value;
			Value v = ie.getArgs().get(1);
			queries.put(Integer.toString(id), methodName + "." + v.toString());
		}
		//Deal with InvokeExpr
		else {
			SootMethod sm = ie.getMethod();
			if(!funcstk.contains(sm.toString()) && !sm.getDeclaringClass().isJavaLibraryClass()) {
				//Make init set which will be passed to the called function
				Map<String, Set<String>> init = new HashMap<String, Set<String>>();
				//Put parameter result into init set
				for(int i = 0; i < ie.getArgCount(); i++) {
					if(ie.getArgs().get(i) instanceof Local) {
						String initkey = sm.toString() + ".@." + Integer.toString(i);
						Set<String> initval = new HashSet<String>();
						initval.addAll(output.get(methodName + "." + ie.getArgs().get(i).toString()));
						init.put(initkey, initval);						
					}
				}
				//Put this parameter result into init set
				if(ie instanceof InstanceInvokeExpr) {
					String thisobjkey = sm.toString() + ".@.this";
					Set<String> thisobjval = new HashSet<String>();
					thisobjval.addAll(output.get(methodName + "." + ((InstanceInvokeExpr)ie).getBase().toString()));
					init.put(thisobjkey, thisobjval);
				}
				//Put heap references result into init set
				for(Map.Entry<String, Set<String>> entry : output.entrySet()) {
					if(entry.getKey().startsWith("#.")) {
						init.put(entry.getKey(), entry.getValue());
					}
				}
				
				//Push into stack and begin a new Analysis
				funcstk.push(sm.toString());
				Analysis pa = new Analysis(new ExceptionalUnitGraph(sm.retrieveActiveBody()), init, sm.toString(), funcstk);
				funcstk.pop();
				
				//Fetch return value 
				if(pa.result.containsKey(sm.toString() + ".@.return")) {
					ret.addAll(pa.result.get(sm.toString() + ".@.return"));
				}
				//Merge all queries into current queries
				queries.putAll(pa.queries);
				//Merge some useful result into current result
				for (Map.Entry<String, Set<String>> entry : pa.result.entrySet()) {
					if(entry.getKey().startsWith("#.") || queries.containsValue(entry.getKey()) || entry.getKey().contains(".@.return")) {
						if(output.containsKey(entry.getKey())) {
							output.get(entry.getKey()).addAll(entry.getValue());
						}
						else {
							output.put(entry.getKey(), entry.getValue());
						}
					}
				}
				//Merge parameter result into current result
				for(int i = 0; i < ie.getArgCount(); i++) {
					if(ie.getArgs().get(i) instanceof Local) {
						String parakey = sm.toString() + ".@." + Integer.toString(i);
						String paraval = pa.paraMap.get(parakey);
						output.get(methodName + "." + ie.getArgs().get(i).toString()).addAll(pa.result.get(paraval));						
					}
				}
				//Merge this parameter result into current result
				if(ie instanceof InstanceInvokeExpr) {
					output.get(methodName + "." + ((InstanceInvokeExpr)ie).getBase().toString())
						  .addAll(pa.result.get(pa.paraMap.get(sm.toString() + ".@.this")));
				}
			}
		}
		return ret;
	}
	
	protected void flowThrough(Object src, Object unit, Object dest) {
		try {
			Map<String, Set<String>> input = (Map<String, Set<String>>)src;
			Map<String, Set<String>> output = (Map<String, Set<String>>)dest;
			copy(input, output);
			
			Unit u = (Unit)unit;
			if(u instanceof InvokeStmt) {
				InvokeExpr ie = ((InvokeStmt) u).getInvokeExpr();
				functionCall(ie, output);
			}
			else if(u instanceof DefinitionStmt) {
				Value left = ((DefinitionStmt)u).getLeftOp();
				Value right = ((DefinitionStmt)u).getRightOp();
				Set<String> rightSet = new HashSet<String>();
				
				//Deal with right operator
				if (right instanceof ParameterRef) {
					String pidx = Integer.toString(((ParameterRef)right).getIndex());
					paraMap.put(methodName + ".@." + pidx, methodName + "." + left.toString());
					if(output.containsKey(methodName + ".@." + pidx))
						rightSet.addAll(output.get(methodName + ".@." + pidx));
				}
				else if (right instanceof ThisRef) {
					paraMap.put(methodName + ".@.this", methodName + "." + left.toString());
					if(output.containsKey(methodName + ".@.this"))
						rightSet.addAll(output.get(methodName + ".@.this"));
				}
				else if (right instanceof NewExpr) {
					rightSet.add(Integer.toString(allocId));
				}
				else if (right instanceof Local) {
					rightSet.addAll(output.get(methodName + "." + right.toString()));
				}
				else if (right instanceof InstanceFieldRef) {
					String base = ((InstanceFieldRef)right).getBase().toString();
					String field = ((InstanceFieldRef)right).getField().toString();
					for(String entry : output.get(methodName + "." + base)) {
						if(output.containsKey("#." + entry + "." + field)) {
							rightSet.addAll(output.get("#." + entry + "." + field));
						}
					}
				}
				else if(right instanceof InvokeExpr) {
					rightSet = functionCall((InvokeExpr)right, output);
				}
				
				//Deal with left operator
				if(left instanceof Local) {
					if(!output.containsKey(methodName + "." + left.toString())) {
						output.put(methodName + "." + left.toString(), new HashSet<String>());
					}
					output.get(methodName + "." + left.toString()).addAll(rightSet);
				}
				else if(left instanceof InstanceFieldRef) {
					String base = ((InstanceFieldRef)left).getBase().toString();
					String field = ((InstanceFieldRef)left).getField().toString();
					for(String entry : output.get(methodName + "." + base)) {
						if(!output.containsKey("#." + entry + "." + field)) {
							output.put("#." + entry + "." + field, new HashSet<String>());
						}
						output.get("#." + entry + "." + field).addAll(rightSet);
					}
				}
			}
			else if(u instanceof ReturnStmt) {
				if(((ReturnStmt)u).getOp() instanceof Local) {
					Set<String> retval = output.get(methodName + "." + ((ReturnStmt)u).getOp().toString());
					if(!output.containsKey(methodName + ".@.return")) {
						output.put(methodName + ".@.return", new HashSet<String>());
					}
					output.get(methodName + ".@.return").addAll(retval);	
				}
			}
			Map<String, Set<String>> temp = new HashMap<String, Set<String>>();
			merge(result, output, temp);
			result = temp;
		}
		catch(Exception e) {
//			e.printStackTrace();
//			System.out.println("Cannot Do Analysis");
		}
	}
	
	public String getResult() throws Exception {
		String ret = "";
		Set<String> keyset = queries.keySet();
		List<Integer> keylist = new ArrayList<Integer>();
		for(String s : keyset) 
			keylist.add(new Integer(s));
		Collections.sort(keylist);
		
		for (int i = 0; i < queries.size(); i++) {
			ret = ret + keylist.get(i).toString() + ":";
			List<Integer> list = new ArrayList<Integer>();
			if(result.containsKey(queries.get(keylist.get(i).toString()))) {
				for (String s : result.get(queries.get(keylist.get(i).toString()))) 
					list.add(new Integer(s));
				Collections.sort(list);
				for (int j = 0; j < list.size(); j++) {
					if(list.get(j).equals(new Integer(0))) continue;
					ret = ret + " " + list.get(j).toString();
				}
			}
			ret += "\n";
		}
		return ret;
	}
	
	public void printer1(Map<String, String> mp) {
		System.out.println("========================START=========================");
		for(Map.Entry<String, String> entry : mp.entrySet())
			System.out.println(entry.getKey() + " => " + entry.getValue());
		System.out.println("=========================END==========================");
	}
	
	public void printer2(Map<String, Set<String>> mp) {
		System.out.println("========================START=========================");
		for(Map.Entry<String, Set<String>> entry : mp.entrySet()) {
			System.out.print(entry.getKey() + " : ");
			int cnt = 0;
			for (String s : entry.getValue()) {
				if(cnt == 0) System.out.print(s);
				else System.out.print(", " + s);
				cnt++;
			}
			System.out.print("\n");
		}
		System.out.println("=========================END==========================");
	}
	
}

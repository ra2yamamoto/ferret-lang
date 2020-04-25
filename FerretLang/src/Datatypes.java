import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import tester.Tester;

enum Datatype {
	BOOLEAN, NUMBER, STRING, FUNCTION, LIST, MAP, OPERATOR, NIL, AST_NODE
}

class Namespace { // The core of variable definitions and scoping
	ArrayList<Map<String, IValue>> namespaces; // Namespaces are stored as a list of maps so that proper scopes can be retained
	
	Namespace (ArrayList<Map<String, IValue>> nses) {
		this.namespaces = nses;
	}
	
	Namespace () {
		this.namespaces = new ArrayList<Map<String, IValue>>();
		namespaces.add(new HashMap<String, IValue>());
	}
	
	IValue get (String key) { // iterate backwards here to look at local scope before larger
		
		for (int i = this.namespaces.size() - 1; i >= 0; i--) {
			Map<String, IValue> map = this.namespaces.get(i);
			
			if (map.containsKey(key)) {
				return map.get(key);
			}
		}
		
		return null;
	}
	
	IValue set (String key, IValue value) {
		boolean found = false;
		
		for (int i = 0; i < this.namespaces.size(); i++) {
			Map<String, IValue> map = this.namespaces.get(i);
			
			if (map.containsKey(key)) {
				map.put(key, value);
				found = true;
			}
		}
		
		if (!found) {
			namespaces.get(namespaces.size() - 1).put(key, value);
		}
		
		return this.get(key);
	}
	
	Namespace copyWith (Map<String, IValue> otherMap) { // this function is called when a new funcion/scope is introduced
		ArrayList<Map<String, IValue>> end = new ArrayList<>();
		end.addAll(namespaces);
		end.add(otherMap); // in order to ensure modifications to global variables (or variables outside the scope) still get modified (maybe closures?)
							// uses the fact that Java will just pass down the pointers to the maps in any new namespaces created, so the same object is reference during set/get operations
		return new Namespace(end);
	}
	
	static Namespace stdlib () { // the "standard library", essentially a manual namespace created to access "named functions". Should be passed into the first sequences eval
		Map<String, IValue> mappings = new HashMap<>();
		
		Utils utils = new Utils();
		utils.loadNamed(); // offloading the map creation/setting/getting to one object
		
		utils.funcs.forEach((s, v) -> { mappings.put(s, new NamedFunction(s, utils)); }); // put a corresponding string to each named function for each named function in the collection
		
		return new Namespace(Utils.list(mappings)); // return a new namespace
	}
}

interface IExpression { // Everything in the AST is an IExpression
	Namespace getNamespace(); // some of these methods are redundant or not necessary
	void setNamespace(Namespace ns);
	IValue eval(Namespace ns); // used to recursively evaluate the final tree
	String printOutput(); // similar to Java's toString method, used for 'print();' calls
}

interface IValue extends IExpression { // represents a value in the AST
	Datatype getType();
}

interface ICollection extends IValue { // Used to define maps/lists
	IValue get(IValue identifier, Namespace ns);
	IValue set(IValue entry, IValue location, Namespace ns);
}

class Nil implements IValue { // analogous to null

	public Namespace getNamespace() {
		return null;
	}
	
	public IValue eval(Namespace ns) {
		return this;
	}
	
	public void setNamespace (Namespace ns) {}
	
	public String printOutput () {
		return "nil";
	}

	public Datatype getType() {
		return Datatype.NIL;
	}
	
	public String toString () {
		return "nil";
	}
	
	public boolean equals (Object other) {
		if (other instanceof Nil) {
			return true;
		}
		
		return false;
	}
	
	public int hashCode () {
		return 0;
	}
	
}

// LITERALS

abstract class ALiteral implements IValue { // encompasses all literals supported by the language: Numbers, Booleans, and Strings
	Object value;                          // Basically a wrapper around Java's Types
	
	ALiteral (Object value, Namespace ns) {
		this.value = value;
	}
	
	ALiteral (Object value) {
		this.value = value;
	}

	public Namespace getNamespace() {
		return null;
	}
	
	public IValue eval(Namespace ns) {
		this.setNamespace(ns);
		return this;
	}
	
	public void setNamespace (Namespace ns) {}
	
	public String printOutput () {
		return value.toString();
	}
	
	public String toString () {
		return value.toString();
	}
	
}

class NumberLiteral extends ALiteral {
	NumberLiteral (Number value, Namespace ns) {
		super(value.doubleValue(), ns); // numbers are all converted to doubles for equality and simplicities sake
	}
	
	NumberLiteral (Number value) {
		super(value.doubleValue());
	}

	public Datatype getType () {
		return Datatype.NUMBER;
	}
}

class BooleanLiteral extends ALiteral {
	BooleanLiteral (boolean value, Namespace ns) {
		super(value, ns);
	}
	
	BooleanLiteral (boolean value) {
		super(value);
	}

	public Datatype getType () {
		return Datatype.BOOLEAN;
	}
}

class StringLiteral extends ALiteral {
	StringLiteral (String value, Namespace ns) {
		super(value, ns);
	}
	
	StringLiteral (String value) {
		super(value);
	}

	public Datatype getType () {
		return Datatype.STRING;
	}
	
	public String toString () {
		return value.toString();
	}
}

// References

class Reference implements IValue { // a variable reference, e.g. 'a;' -> 1, assuming a is a variable with a value of 1
	String key;
	Namespace ns;
	
	Reference (String key, Namespace ns) {
		this.key = key;
		this.ns = ns;
	}
	
	Reference (String key) {
		this.key = key;
		this.ns = new Namespace();
	}
	
	public IValue eval(Namespace ns) {
		this.setNamespace(ns);
		IValue result = this.ns.get(this.key);
		
		return result == null ? new Nil() : result.eval(this.ns);
	}
	
	public Namespace getNamespace() {
		return this.ns;
	}
	
	public Datatype getType() {
		return Datatype.FUNCTION; // it's not a function, but I can't delegate to the Object class
	}
	
	public void setNamespace (Namespace ns) {
		this.ns = ns;
	}
	
	public String printOutput () {
		IValue result = this.ns.get(this.key); // inefficient
		
		return result == null ? "nil" : result.printOutput();
	}
	
	public String toString () {
		return "(Ref " + this.key + ")";
	}
}

// COLLECTIONS

class ListValue implements ICollection { // represents a list
	
	ArrayList<IValue> value;
	Namespace ns;
	
	ListValue (ArrayList<IValue> value) {
		this.value = value;
		this.ns = null;
	}
	
	public Namespace getNamespace() {
		return this.ns;
	}

	public IValue eval(Namespace ns) {
		this.setNamespace(ns);
		return this;
	}
	
	public Datatype getType () {
		return Datatype.LIST;
	}
	
	public IValue get(IValue identifier, Namespace ns) {
		this.setNamespace(ns);
		
		double identifierDouble;
		IValue maybeIndex = identifier.eval(this.ns); // evaluate first, to get references and such
		
		if (!(maybeIndex instanceof NumberLiteral)) {
			throw new IllegalArgumentException("Get operation on List expected an index, given " + maybeIndex.getClass().getName());
		}
		
		identifierDouble = (double) ((NumberLiteral) maybeIndex).value;
		
		if (identifierDouble != Math.floor(identifierDouble)) {
			throw new IllegalArgumentException("Get operation on List expected an integer, given " + identifierDouble);
		}
		
		if (identifierDouble > this.value.size() - 1 || identifierDouble < (-this.value.size() + 1)) {
			System.out.println("Get operation on List: index out of bounds");
			return new Nil();
		}
		
		identifierDouble = identifierDouble < 0 ? this.value.size() + identifierDouble : identifierDouble;
		
		IValue end = new Nil();
		
		try { // just in case there is an indexoutofbounds exception
			end = value.get((int) identifierDouble);
			return end;
		} catch (Exception e) {
			
		}
		
		return end;
	}
	
	// most interactions with lists would occur using a ":" operator
	
	public IValue set(IValue entry, IValue location, Namespace ns) { // entry can be whatever, location has to be a NumberLiteral
		this.setNamespace(ns);
		
		// if set is given -1 or the same as the .size of the list, a new thing is added
		
		// evaluate both
		IValue evalEntry = entry.eval(this.ns);
		IValue evalLocation = location.eval(this.ns);
		
		if (!(evalLocation instanceof NumberLiteral)) {
			throw new IllegalArgumentException("Set operation on List given an illegal index");
		}
		
		double index = (double) ((NumberLiteral) evalLocation).value;
		
		if (index != Math.floor(index)) {
			throw new IllegalArgumentException("Set operation on List given a non-integer index.");
		}
		
		index = index < 0 ? this.value.size() + 1 + (int) index : index; // wraparound once
		
		if ((int) index > this.value.size() || (int) index < (-this.value.size())) {
			System.out.println("Array Index out of bounds in List set call.");
			return new Nil();
		}
		
			// if the index is negative, wraparound
		try {
			
			if (index == this.value.size()) {
				value.add(evalEntry);
			} else {
				value.set((int) index, evalEntry);
			}
			
		} catch (Exception e) {
			return new Nil(); // handle this error better TODO
		}
		
		return this; // or the entry?
	}
	
	public void setNamespace (Namespace ns) {
		this.ns = ns;
	}
	
	public String printOutput () {
		return "nil";
	}
	
	public String toString () {
		return this.value.toString();
	}
}

class MapValue implements ICollection { // represents a map
	
	Map<IValue, IValue> value;
	Namespace ns;
	
	MapValue (ArrayList<IValue> keys, ArrayList<IValue> values) {
		this.value = new HashMap<IValue, IValue>();
		this.ns = new Namespace();
		
		if (keys.size() != values.size()) {
			throw new IllegalArgumentException("Map constructed with an odd number of key/value pairs");
		}
		
		for (int i = 0; i < keys.size(); i++) {
			this.value.put(keys.get(i), values.get(i));
		}
	}

	public Namespace getNamespace() {
		return this.ns;
	}

	public IValue eval(Namespace ns) {
		this.setNamespace(ns);
		return this;
	}

	public Datatype getType () {
		return Datatype.MAP;
	}
	
	public void setNamespace (Namespace ns) {
		this.ns = ns;
	}
	
	public String printOutput () {
		return "map";
	}

	public IValue get(IValue identifier, Namespace ns) {
		this.setNamespace(ns);
		IValue evalKey = identifier.eval(this.ns); // for now, there's no restriction on what can be a key
		IValue getVal = this.value.get(evalKey);
		
		return getVal == null ? new Nil() : getVal;
	}

	public IValue set(IValue entry, IValue location, Namespace ns) {
		this.setNamespace(ns);
		IValue eEntry = entry.eval(this.ns);
		IValue eLocation = entry.eval(this.ns);
		
		this.value.put(eLocation, eEntry);
		
		return eEntry;
	}
}

class Function implements IValue { // represents a (first class) function

	Namespace ns;
	Sequence body;
	ArrayList<String> params;
	
	Function (ArrayList<String> exArgs, Sequence body, Namespace ns) {
		this.params = exArgs;
		this.body = body;
		this.ns = ns;
	}
	
	Function (ArrayList<String> exArgs, Sequence body) {
		this.params = exArgs;
		this.body = body;
	}
	
	public Namespace getNamespace() {
		return this.ns;
	}

	public IValue eval(Namespace ns) { // returns itself
		this.setNamespace(ns);
		return this;
	}
	
	public IValue call(ArrayList<IValue> args, Namespace ns) { // used by a FunctionCall to evaluate the contents of the Function
		this.setNamespace(ns);
		
		Map<String, IValue> argNS = new HashMap<>();
		
		if (args.size() < params.size()) {
			throw new IllegalArgumentException("Arity mismatch");
		}
		
		for (int i = 0; i < args.size(); i++) {
			argNS.put("@" + String.valueOf(i + 1), args.get(i)); // adds @1 ... @n to the namespace
			
			if (i < params.size()) {
				argNS.put(params.get(i), args.get(i)); // adds the expected argument keys to the namespace (e.g 'f (a b) > {};' would add 'a' and 'b' to the function scope)
			}
		}
		
		return this.body.eval(this.ns.copyWith(argNS));
	}

	public Datatype getType() {
		return null;
	}
	
	public void setNamespace (Namespace ns) {
		this.ns = ns;
	}
	
	public String printOutput () {
		return "function";
	}
	
	public String toString () {
		return "Func " + params.toString() + " -> " + body.toString();
	}
	
}

class NamedFunction extends Function { // represents core functions
	Utils utils = new Utils();
	
	String type;
	IFuncOperation operation;
	static boolean firstPrint = true;
	
	NamedFunction (String type, Namespace ns) {
		super(new ArrayList<String>(), new Sequence(new ArrayList<IExpression>(), ns), ns);
		
		utils.loadNamed();
		this.type = type;
		this.operation = utils.getFunc(type);
		
		if (this.operation == null) {
			throw new IllegalArgumentException("NamedFunction given a non-standard function");
		}
	}
	
	NamedFunction (String type) {
		super(new ArrayList<String>(), new Sequence(new ArrayList<IExpression>(), new Namespace()));
		
		utils.loadNamed();
		this.type = type;
		this.operation = utils.getFunc(type);
		
		if (this.operation == null) {
			throw new IllegalArgumentException("NamedFunction given a non-standard function");
		}
	}
	
	NamedFunction (String type, Utils utils) {
		super(new ArrayList<String>(), new Sequence(new ArrayList<IExpression>(), new Namespace()));
		
		this.type = type;
		this.operation = utils.getFunc(type);
		
		if (this.operation == null) {
			throw new IllegalArgumentException("NamedFunction given a non-standard function");
		}
	}
	
	public IValue call (ArrayList<IValue> args, Namespace ns) {
		this.setNamespace(ns);
		
		ArrayList<IValue> endArgs = new ArrayList<>();
		
		if (this.type.equals("print")) {
			StringBuilder end = new StringBuilder();
			args.stream().forEach(val -> end.append(val == null ? "nil" : val.toString()));
			System.out.println(end.toString());
			return new StringLiteral(end.toString());
		}
		
		for (int i = 0; i < args.size(); i++) { // all the args we get should be as reduced as possible, as we eval() them in the functionCall eval()
			endArgs.add(args.get(i));
		}
		
		return this.operation.apply(endArgs, this.ns);
	}
}

class Sequence implements IExpression { // represents a sequence of IExpressions to be called in order
	
	ArrayList<IExpression> body;
	Namespace ns;
	
	Sequence (ArrayList<IExpression> body, Namespace ns) {
		this.body = body;
		this.ns = ns;
	}

	public Namespace getNamespace() {
		return this.ns;
	}

	public IValue eval(Namespace ns) {
		IValue last = new Nil();
		this.setNamespace(ns);
		
		for (int i = 0; i < body.size(); i++) {
			if (i == body.size() - 1) {
				last = body.get(i).eval(this.ns);
			} else {
				body.get(i).eval(this.ns);
			}
		}
		
		return last;
	}
	
	static Sequence makeSequence (Namespace ns, IExpression... expr) {
		return new Sequence(new ArrayList<IExpression>(Arrays.asList(expr)), ns);
	}
	
	static Sequence makeSequence (IExpression... expr) {
		return new Sequence(new ArrayList<IExpression>(Arrays.asList(expr)), new Namespace());
	}
	
	public void setNamespace (Namespace ns) {
		this.ns = ns;
	}
	
	public String printOutput () {
		return "sequence";
	}
	
	public String toString () {
		return "Sequence: {" + this.body.toString() + "}";
	}
	
}

// AST ANodeS

abstract class ANode implements IValue {
	Namespace ns;
	
	ANode (Namespace ns) {
		this.ns = ns;
	}
	
	public Namespace getNamespace () {
		return this.ns;
	}
	
	public void setNamespace (Namespace ns) {
		this.ns = ns;
	}
	
	public String printOutput () {
		return "AST node";
	}
	
	public Datatype getType() {
		return Datatype.AST_NODE;
	}
}

class FunctionCall extends ANode { // have to have their own args stored, actuqlly, same with all the ANodes, args don't need to be in every IExpression

	IValue maybeFunc;
	ArrayList<IValue> args;
	
	FunctionCall (IValue func, ArrayList<IValue> args, Namespace ns) { // takes in an IValue that has to evaluate to a function
		super(ns);
		this.maybeFunc = func;
		this.args = args;
	}
	
	FunctionCall (IValue func, ArrayList<IValue> args) { // takes in an IValue that has to evaluate to a function
		super(new Namespace());
		this.maybeFunc = func;
		this.args = args;
	}
	
	public IValue eval(Namespace ns) {
		this.setNamespace(ns);
		
		IValue result = this.maybeFunc.eval(this.ns); // run here to work with namespaces // evaluates, to work with references
		
		if (!(result instanceof Function)) { // if the result of the evaluation isn't a function
			throw new IllegalArgumentException("Tried to call a function on something that isn't a function.");
		}
		
		ArrayList<IValue> finalArgs = new ArrayList<>(); // takes up space, worth doing in place?
		
		for (int i = 0; i < this.args.size(); i++) {
			finalArgs.add(this.args.get(i).eval(this.ns));
		}
		
		return ((Function) result).call(finalArgs, this.getNamespace());
	}
	
	public Datatype getType() {
		return Datatype.AST_NODE;
	}
	
	public String toString () {
		return "Call " + this.maybeFunc.toString() + " on " + args.toString();
	}
	
}

class Operation extends ANode { // TODO: finish & create type enforce method
	
	static Utils utils = new Utils(); // hopefully saves space
	
	IFuncOperation operation;
	ArrayList<IValue> operands;
	
	Operation (String type, ArrayList<IValue> operands) { // DON'
		super(new Namespace());
		utils.loadOps();
		this.operation = utils.getOp(type);
		this.operands = operands;
	}

	public IValue eval(Namespace ns) {
		this.setNamespace(ns);
		return this.operation.apply(operands, this.ns);
	}
	
	public Datatype getType() {
		return Datatype.AST_NODE;
	}
	
}

class Definition extends ANode { // analogous to a variable assignment, e.g. 'a 1;' -> defines the variable a with a value of 1

	String key;
	IValue value;
	
	Definition (String key, IValue value, Namespace ns) {
		super(ns);
		this.key = key;
		this.value = value;
	}
	
	Definition (String key, IValue value) {
		super(new Namespace());
		this.key = key;
		this.value = value;
	}
	
	public IValue eval(Namespace ns) {
		this.setNamespace(ns);
		this.ns.set(this.key, this.value.eval(this.ns));
		
		return this.ns.get(this.key);
	}

	public Datatype getType() {
		return Datatype.AST_NODE;
	}
	
	public String toString () {
		return "Def " + this.key +  " = " + this.value.toString();
	}
	
}

class Conditional extends ANode { // represents a conditional, ie an if statement // DONT USE

	ArrayList<IExpression> conditions;
	ArrayList<IExpression> thens;
	IExpression elseExpr;
	
	Conditional(IExpression condition, IExpression then, IExpression elseExpr) {
		super(new Namespace());
		
		this.conditions = Utils.list(condition);
		this.thens = Utils.list(then);
		this.elseExpr = elseExpr;
	}
	
	Conditional(ArrayList<IExpression> condition, ArrayList<IExpression> then, IExpression elseExpr) {
		super(new Namespace());
		
		if (condition.size() != then.size() || condition.size() < 1 || then.size() < 1) {
			throw new IllegalArgumentException("Conditional Given an illegal number of arguments");
		}
		
		this.conditions = condition;
		this.thens = then;
		this.elseExpr = elseExpr;
	}
	
	public IValue eval(Namespace ns) {
		this.setNamespace(ns);
		
		IValue evaledCond;
		
		for (int i = 0; i < this.conditions.size(); i++) {
			evaledCond = conditions.get(i).eval(this.ns);
			
			if (evaledCond instanceof Function) { // keeps syntax clean
				evaledCond = ((Function) evaledCond).call(Utils.list(), this.ns); // mutates for simplicity's sake // gross
			}
			
			if (!(evaledCond instanceof BooleanLiteral)) {
				throw new IllegalArgumentException("Condition in if statement returned a " + evaledCond.getClass().getName() + ", not a boolean.");
			}
			
			// The main calculation:
			
			if ((boolean) ((BooleanLiteral) evaledCond).value) { // lovely casting
				return thens.get(i).eval(this.ns);
			}
		}
		
		return elseExpr.eval(this.ns);
	}
	
}

@FunctionalInterface
interface IFuncOperation {
	IValue apply(ArrayList<IValue> args, Namespace ns);
};

class Utils {
	
	Map<String, IFuncOperation> funcs = new HashMap<>();
	ToDoubleFunction<IValue> literalToDouble = literal -> ((Number) ((ALiteral) literal).value).doubleValue();
	
	Map<String, IFuncOperation> ops = new HashMap<>();
	
	Utils () {}
	
	static <T> ArrayList<T> list (T ...items) {
		return new ArrayList<T>(Arrays.asList(items));
	}
	
	void loadNamed () {
		Namespace emptyNS = new Namespace();
		
		funcs.put("+", (l, ns) -> {
			if (l.stream().anyMatch(e -> e instanceof StringLiteral)) { // concat if any are strings
				StringBuilder end = new StringBuilder();
				l.stream().forEach(val -> end.append(val.toString()));
				return new StringLiteral(end.toString());
			}
			return new NumberLiteral(l.stream().collect(Collectors.summingDouble(e -> ((Number) ((ALiteral) e).value).doubleValue())));
		});
		funcs.put("-", (l, ns) -> new NumberLiteral(l.stream().mapToDouble(literalToDouble).reduce((a, b) -> a - b).getAsDouble()));
		funcs.put("*", (l, ns) -> new NumberLiteral(l.stream().mapToDouble(literalToDouble).reduce(1, (a, b) -> a * b)));
		funcs.put("^", (l, ns) -> new NumberLiteral(l.stream().mapToDouble(literalToDouble).reduce((a, b) -> Math.pow(a, b)).getAsDouble()));
		//funcs.put("/", l -> new NumberLiteral(((Number) l.get(0).value).doubleValue() / ((Number) l.get(1).value).doubleValue(), this.ns));
		funcs.put("/", (l, ns) -> new NumberLiteral(l.stream().mapToDouble(literalToDouble).reduce((a, b) -> a / b).getAsDouble()));
		
		funcs.put("<", (l, ns) -> new BooleanLiteral(((Number) ((ALiteral) l.get(0)).value).doubleValue() < ((Number) ((ALiteral) l.get(1)).value).doubleValue()));
		funcs.put(">", (l, ns) -> new BooleanLiteral(((Number) ((ALiteral) l.get(0)).value).doubleValue() > ((Number) ((ALiteral) l.get(1)).value).doubleValue()));
		funcs.put("<=", (l, ns) -> new BooleanLiteral(((Number) ((ALiteral) l.get(0)).value).doubleValue() <= ((Number) ((ALiteral) l.get(1)).value).doubleValue()));
		funcs.put(">=", (l, ns) -> new BooleanLiteral(((Number) ((ALiteral) l.get(0)).value).doubleValue() >= ((Number) ((ALiteral) l.get(1)).value).doubleValue()));
		funcs.put("=", (l, ns) -> new BooleanLiteral(l.stream().distinct().limit(2).count() <= 1));
		funcs.put("!=", (l, ns) -> new BooleanLiteral(!(l.stream().distinct().limit(2).count() <= 1)));

		funcs.put("and", (l, ns) -> new BooleanLiteral(l.stream().allMatch(e -> { return (Boolean) ((ALiteral) e).value; } )));
		funcs.put("or", (l, ns) -> new BooleanLiteral(l.stream().anyMatch(e -> { return (Boolean) ((ALiteral) e).value; } )));
		funcs.put("not", (l, ns) -> new BooleanLiteral(!((Boolean) ((ALiteral) l.get(0)).value)));
		funcs.put("!", (l, ns) -> new BooleanLiteral(!((Boolean) ((ALiteral) l.get(0)).value)));
		funcs.put("for", (l, ns) -> {
			ArrayList<IValue> ret = new ArrayList<>();
			
			ListValue list = (ListValue) l.get(0);
			Function func = (Function) l.get(1);
			
			for (int i = 0; i < list.value.size(); i++) {
				ret.add(func.call(Utils.list(list.value.get(i)), ns));
			}
			
			return new ListValue(ret);
		});
		funcs.put("len", (l, ns) -> new NumberLiteral( l.size() <= 0 ? 0 : ((ListValue) l.get(0)).value.size() ));
		funcs.put("print", (l, ns) -> l.get(0));
		
		// conditionals
		
		funcs.put("if", (l, ns) -> {
			//steps;
			//make sure the first parameter evals to boolean, if it's a function, evaluate it
			//evaluate either the second or third parameter, if it's a function, wrap it in a call
			
			//System.out.println(l);
			
			if (l.size() != 3) {
				throw new IllegalArgumentException("If statement given " + l.size() + " instead of 3 arguments");
			}
			
			IValue condition = l.get(0).eval(ns);
			IValue then = l.get(1);
			IValue els = l.get(2);
			
			if (condition instanceof Function) {
				condition = ((Function) condition).call(Utils.list(), ns);
			}
			
			if (!(condition instanceof BooleanLiteral)) {
				throw new IllegalArgumentException("If statement condition doesn't evaluate to a boolean");
			}
			
			// main thing
			
			if ((boolean) ((BooleanLiteral) condition).value) { // lovely casting
				
				if (then instanceof Function) {
					then = ((Function) then).call(Utils.list(), ns);
				}
				
				return then.eval(ns);
			} else {
				
				if (els instanceof Function) {
					els = ((Function) els).call(Utils.list(), ns);
				}
				
				return els.eval(ns);
			}
		});
		
		// lofuncs
		
	}
	
	void loadOps () {
		ops.put("...", (l, ns) -> {
				ArrayList<IValue> toRet = new ArrayList<>();
				int start = (int) ((double) ((NumberLiteral) l.get(0).eval(ns)).value);
				int end = (int) ((double) ((NumberLiteral) l.get(1).eval(ns)).value);
				
				if (start < end) {
					for (int i = start; i < end; i++) {
						toRet.add(new NumberLiteral(i));
					}
				} else {
					for (int i = start - 1; i >= end; i--) {
						toRet.add(new NumberLiteral(i));
					}
				}	
				
				return new ListValue(toRet);
			});
		
		ops.put(":", (l, ns) -> {
			return ((ICollection) l.get(0).eval(ns)).get(l.get(1).eval(ns), ns);
		});
		
		ops.put("<<", (l, ns) -> { // alternating
			ICollection list = (ICollection) l.get(l.size() - 1).eval(ns);
			
			for (int i = 0; i < (int) l.size() - 1; i += 2) {
					list.set(l.get(i+1), l.get(i), ns);
			}
			
			return list;
		});
	}
	
	IFuncOperation getFunc (String s) {
		return this.funcs.get(s);
	}
	
	IFuncOperation getOp (String s) {
		return this.ops.get(s);
	}
}

class ValueTests {
	//Every program is encased in a large Sequence
	//A statement
	Namespace namespace;
	Map<String, IValue> map;
	ArrayList<IValue> empty = new ArrayList<>();
	ArrayList<Map<String, IValue>> nsList;
	
	void initNS () {
		nsList = new ArrayList<>(Arrays.asList(new HashMap<String, IValue>()));
		namespace = new Namespace(nsList);
		map = new HashMap<String, IValue>();
	}
	
	void testSeqs (Tester t) {
		initNS();
		
		t.checkExpect(Sequence.makeSequence(namespace, new BooleanLiteral(true, namespace)).eval(namespace), new BooleanLiteral(true, namespace));
		t.checkExpect(Sequence.makeSequence(namespace, new StringLiteral("orange", namespace)).eval(namespace), new StringLiteral("orange", namespace));
		t.checkExpect(Sequence.makeSequence(namespace, new StringLiteral("orange", namespace), new NumberLiteral(21, namespace)).eval(namespace), new NumberLiteral(21, namespace));
	}
	
	void testDefs (Tester t) {
		initNS();
		BooleanLiteral tru = new BooleanLiteral(true, namespace);
		BooleanLiteral fal = new BooleanLiteral(false, namespace);
		
		t.checkExpect(Sequence.makeSequence(namespace, new Definition("orange", tru, namespace)).eval(namespace), tru);
		t.checkExpect(namespace.get("orange"), tru);
		t.checkExpect(Sequence.makeSequence(namespace, 
				new Definition("orange", tru, namespace),
				new Definition("orange", fal, namespace),
				new Definition("apple", fal, namespace)).eval(namespace), fal);
		t.checkExpect(namespace.get("orange"), fal);
		t.checkExpect(namespace.get("apple"), fal);
		t.checkExpect(Sequence.makeSequence(namespace, new Definition("apple", tru, namespace), fal, fal).eval(namespace), fal);
		t.checkExpect(namespace.get("apple"), tru);	
	}
	
	void testRefs (Tester t) {
		initNS();
		NumberLiteral num = new NumberLiteral(24601, namespace);
		
		t.checkExpect(Sequence.makeSequence(namespace, num).eval(namespace), num);
		t.checkExpect(Sequence.makeSequence(namespace, new Definition("number", num, namespace)).eval(namespace), num);
		t.checkExpect(Sequence.makeSequence(namespace, new Reference("number", namespace)).eval(namespace), num);
		
		t.checkExpect(Sequence.makeSequence(namespace, 
				new Definition("a", num, namespace), // a 24601;
				new Definition("b", // b a;
						new Reference("a", namespace), namespace), // b;
				new Reference("b", namespace)).eval(namespace), num); // -> 24601
	}
	
	void testFuncs (Tester t) {
		initNS();
		BooleanLiteral tru = new BooleanLiteral(true, namespace);
		NumberLiteral num1 = new NumberLiteral(24601, namespace);
		NumberLiteral num2 = new NumberLiteral(24602, namespace);
		
		ArrayList<Map<String, IValue>> m = new ArrayList<>(Arrays.asList(new HashMap<>()));
		
		m.get(0).put("@1", num1);
		m.get(0).put("@2", num2);
		m.get(0).put("a", num1);
		m.get(0).put("b", num2);
		
		Namespace expectedNS = new Namespace(m);
		
		Sequence body = Sequence.makeSequence(namespace, new Reference("@1", namespace));
		Function func = new Function(new ArrayList<String>(Arrays.asList("a", "b")), body, namespace);
		
		t.checkException(new IllegalArgumentException("Arity mismatch"), func, "call", new ArrayList<IValue>(Arrays.asList()), namespace);
		t.checkExpect(func.call(new ArrayList<IValue>(Arrays.asList(num1, num2)), namespace), num1);
		
		t.checkExpect(func.eval(namespace), func);
		
		//t.checkExpect(func.body.body.get(0).getNamespace(), expectedNS);
		t.checkExpect(func.body.body.get(0).getNamespace().get("@1"), num1);
		t.checkExpect(func.body.body.get(0).getNamespace().get("@2"), num2);
		
		t.checkExpect(func.body.body.get(0).getNamespace().get("@0"), null);
		t.checkExpect(func.body.body.get(0).getNamespace().get("@3"), null);

		t.checkExpect(func.body.body.get(0).getNamespace().get("a"), num1);
		t.checkExpect(func.body.body.get(0).getNamespace().get("b"), num2);
		
		t.checkExpect(func.ns.get("@1"), null);
		
		t.checkExpect(func.body.body.get(0).getNamespace().get("c"), null);
		
		// with function calls
		
		t.checkExpect(Sequence.makeSequence(namespace,
				new FunctionCall(func, new ArrayList<IValue>(Arrays.asList(num2, num1)), namespace)).eval(namespace), num2);
		
		t.checkExpect(Sequence.makeSequence(namespace, 
				new Definition("orange", num1, namespace), // orange 24601;
				new FunctionCall(new Function(
						new ArrayList<String>(),
						Sequence.makeSequence(namespace, new Definition("orange", num2, namespace)), // {orange 24602}()
						namespace), new ArrayList<IValue>(), namespace), new Reference("orange", namespace)).eval(namespace), num2); // orange; # -> 24602 // global variables can be mutated in local scopes
		
		t.checkExpect(Sequence.makeSequence(namespace, 
				new Definition("orange", num1, namespace), // orange 24601;
				new FunctionCall(new Function(
						new ArrayList<String>(),
						Sequence.makeSequence(namespace, new Definition("orange", num2, namespace), new Reference("@1", namespace)), // {orange 24602; @1}(true) # -> true
						namespace), new ArrayList<IValue>(Arrays.asList(tru)), namespace)).eval(namespace), tru);
		
		t.checkExpect(Sequence.makeSequence(namespace, 
				new Definition("orange", num1, namespace),
				new FunctionCall(new Function(
						new ArrayList<String>(),
						Sequence.makeSequence(namespace, new Definition("orange", num2, namespace), new Reference("@1", namespace)),
						namespace), new ArrayList<IValue>(Arrays.asList(tru)), namespace), new Reference("@1", namespace)).eval(namespace), new Nil()); // this test verifies that @1 (or any definitions created by the function) don't get pushed to the global namespace
	}
	
	void testHashMaps (Tester t) {
		Map<String, Number> m = new HashMap<String, Number>();
		Map<String, Number> k = new HashMap<String, Number>();
		
		m.put("a", 1);
		m.put("b", 2);
		
		k.putAll(m);
		k.put("c", 3);
		
		t.checkExpect(m.get("a"), 1);
		t.checkExpect(m.get("b"), 2);
		t.checkExpect(m.get("c"), null);
		
		t.checkExpect(k.get("a"), 1);
		t.checkExpect(k.get("b"), 2);
		t.checkExpect(k.get("c"), 3);
		
		k.put("a", 0);
		
		t.checkExpect(k.get("a"), 0);
		t.checkExpect(m.get("a"), 1);
		
		//
		
		Namespace ns1 = new Namespace(new ArrayList<>(Arrays.asList(new HashMap<>())));
		Map<String, IValue> hMap = new HashMap<String, IValue>();
		
		NumberLiteral n1 = new NumberLiteral(1, ns1);
		NumberLiteral n2 = new NumberLiteral(2, ns1);
		
		hMap.put("b", n2);
		
		ns1.set("a", n1);
		
		Namespace ns2 = ns1.copyWith(hMap);
		
		t.checkExpect(ns1.get("a"), n1);
		t.checkExpect(ns1.get("b"), null);
		
		t.checkExpect(ns2.get("a"), n1);
		t.checkExpect(ns2.get("b"), n2);
		
		ns2.set("a", n2);
		
		t.checkExpect(ns2.get("a"), n2);
		t.checkExpect(ns2.get("b"), n2);
		
		t.checkExpect(ns1.get("a"), n2);
	}
	
	void testNamedFuncs (Tester t) {
		initNS();
		BooleanLiteral tru = new BooleanLiteral(true, namespace);
		BooleanLiteral fal = new BooleanLiteral(false, namespace);
		NumberLiteral num1 = new NumberLiteral(1, namespace);
		NumberLiteral num2 = new NumberLiteral(2, namespace);
		NumberLiteral num3 = new NumberLiteral(3, namespace);
		
		NumberLiteral num6 = new NumberLiteral(6, namespace);
		
		t.checkExpect(Sequence.makeSequence(namespace, // make a new sequence
				new FunctionCall(new NamedFunction("+", namespace), // make a new function call on the named function '+'
						new ArrayList<IValue>(Arrays.asList(num1, num2)), // give it arguments num1 (1) and num2 (2)
						namespace)).eval(namespace), num3); // evaluate it
		
		t.checkExpect(Sequence.makeSequence(namespace, // make a new sequence
				new FunctionCall(new NamedFunction("+", namespace), // make a new function call on the named function '+'
						new ArrayList<IValue>(Arrays.asList(num1, num2, num2, num1)), // give it arguments num1 (1) and num2 (2)
						namespace)).eval(namespace), num6); // evaluate it
		
		t.checkExpect(Sequence.makeSequence(namespace, // make a new sequence
				new FunctionCall(new NamedFunction("-", namespace), // make a new function call on the named function '+'
						new ArrayList<IValue>(Arrays.asList(num1, num2, num2, num1)), // give it arguments num1 (1) and num2 (2)
						namespace)).eval(namespace), new NumberLiteral(-4, namespace)); // evaluate it
		
		t.checkExpect(Sequence.makeSequence(namespace, // make a new sequence
				new FunctionCall(new NamedFunction("*", namespace), // make a new function call on the named function '+'
						new ArrayList<IValue>(Arrays.asList(num3, num2)), // give it arguments num1 (1) and num2 (2)
						namespace)).eval(namespace), new NumberLiteral(6, namespace)); // evaluate it
		
		t.checkExpect(Sequence.makeSequence(namespace, // make a new sequence
				new FunctionCall(new NamedFunction("^", namespace), // make a new function call on the named function '+'
						new ArrayList<IValue>(Arrays.asList(num2, num2)), // give it arguments num1 (1) and num2 (2)
						namespace)).eval(namespace), new NumberLiteral(4, namespace)); // evaluate it
		
		t.checkExpect(Sequence.makeSequence(namespace, // make a new sequence
				new FunctionCall(new NamedFunction("/", namespace), // make a new function call on the named function '+'
						new ArrayList<IValue>(Arrays.asList(num6, num2)), // give it arguments num1 (1) and num2 (2)
						namespace)).eval(namespace), new NumberLiteral(3, namespace)); // evaluate it
		
		t.checkExpect(Sequence.makeSequence(namespace, // make a new sequence
				new FunctionCall(new NamedFunction("/", namespace), // make a new function call on the named function '+'
						new ArrayList<IValue>(Arrays.asList(num6, num2, num3)), // give it arguments num1 (1) and num2 (2)
						namespace)).eval(namespace), new NumberLiteral(1, namespace)); // evaluate it
		
		t.checkExpect(Sequence.makeSequence(namespace, // make a new sequence
				new FunctionCall(new NamedFunction("<", namespace), // make a new function call on the named function '+'
						new ArrayList<IValue>(Arrays.asList(num6, num2)), // give it arguments num1 (1) and num2 (2)
						namespace)).eval(namespace), new BooleanLiteral(false, namespace)); // evaluate it
		
		t.checkExpect(Sequence.makeSequence(namespace, // make a new sequence
				new FunctionCall(new NamedFunction(">", namespace), // make a new function call on the named function '+'
						new ArrayList<IValue>(Arrays.asList(num6, num2)), // give it arguments num1 (1) and num2 (2)
						namespace)).eval(namespace), new BooleanLiteral(true, namespace)); // evaluate it
		
		t.checkExpect(Sequence.makeSequence(namespace, // make a new sequence
				new FunctionCall(new NamedFunction(">=", namespace), // make a new function call on the named function '+'
						new ArrayList<IValue>(Arrays.asList(num2, num2)), // give it arguments num1 (1) and num2 (2)
						namespace)).eval(namespace), new BooleanLiteral(true, namespace)); // evaluate it
		
		t.checkExpect(Sequence.makeSequence(namespace, // make a new sequence
				new FunctionCall(new NamedFunction("=", namespace), // make a new function call on the named function '+'
						new ArrayList<IValue>(Arrays.asList(num2, num2)), // give it arguments num1 (1) and num2 (2)
						namespace)).eval(namespace), new BooleanLiteral(true, namespace)); // evaluate it
		
		t.checkExpect(Sequence.makeSequence(namespace, // make a new sequence
				new FunctionCall(new NamedFunction("!=", namespace), // make a new function call on the named function '+'
						new ArrayList<IValue>(Arrays.asList(num2, num2)), // give it arguments num1 (1) and num2 (2)
						namespace)).eval(namespace), new BooleanLiteral(false, namespace)); // evaluate it
		
		t.checkExpect(Sequence.makeSequence(namespace, // make a new sequence
				new FunctionCall(new NamedFunction("and", namespace), // make a new function call on the named function '+'
						new ArrayList<IValue>(Arrays.asList(tru, tru, fal, fal)), // give it arguments num1 (1) and num2 (2)
						namespace)).eval(namespace), new BooleanLiteral(false, namespace)); // evaluate it
		
		t.checkExpect(Sequence.makeSequence(namespace, // make a new sequence
				new FunctionCall(new NamedFunction("and", namespace), // make a new function call on the named function '+'
						new ArrayList<IValue>(Arrays.asList(tru, tru, tru, tru)), // give it arguments num1 (1) and num2 (2)
						namespace)).eval(namespace), new BooleanLiteral(true, namespace)); // evaluate it
		
		t.checkExpect(Sequence.makeSequence(namespace, // make a new sequence
				new FunctionCall(new NamedFunction("or", namespace), // make a new function call on the named function '+'
						new ArrayList<IValue>(Arrays.asList(fal, fal, fal, tru)), // give it arguments num1 (1) and num2 (2)
						namespace)).eval(namespace), new BooleanLiteral(true, namespace)); // evaluate it
		
		t.checkExpect(Sequence.makeSequence(namespace, // make a new sequence
				new FunctionCall(new NamedFunction("!", namespace), // make a new function call on the named function '+'
						new ArrayList<IValue>(Arrays.asList(fal)), // give it arguments num1 (1) and num2 (2)
						namespace)).eval(namespace), new BooleanLiteral(true, namespace)); // evaluate it
		
		t.checkExpect(new Double(3.0).equals(new Double(3)), true);
		
		initNS();
		
		Function f = new Function(new ArrayList<String>(Arrays.asList("a")), Sequence.makeSequence(namespace, // (a) > { # a function f with parameter a
				new Definition("a", new FunctionCall(new NamedFunction("^", namespace),
						new ArrayList<IValue>(Arrays.asList(new Reference("a", namespace), new NumberLiteral(2, namespace))), // a ^(a 2); # set a (locally) to a squared
						namespace), namespace),
				new FunctionCall(new NamedFunction("+", namespace),
						new ArrayList<IValue>(Arrays.asList(new Reference("a", namespace), new NumberLiteral(400, namespace))), // +(a 400); # return a + 400
						namespace)), namespace); // }
		
		t.checkExpect(Sequence.makeSequence(namespace, new FunctionCall(f,
				new ArrayList<IValue>(Arrays.asList(new NumberLiteral(4, namespace))),
				namespace)).eval(namespace), new NumberLiteral(416, namespace));
		
		Sequence.makeSequence(namespace, new FunctionCall(new NamedFunction("print", namespace),
				new ArrayList<IValue>(Arrays.asList(f, tru, fal, num1, num2, num6)), namespace)).eval(namespace);
		
		Definition starF = new Definition("f", f, namespace);
		
//		t.checkExpect(Sequence.makeSequence(namespace, starF, // f {function f}; # assigns f to the Function f
//				new FunctionCall(new Reference("orange", namespace), // f (4); # passes a reference to the function call
//						new ArrayList<IValue>(Arrays.asList(new NumberLiteral(4, namespace))),
//				namespace)).eval(namespace), new NumberLiteral(416, namespace));
		
//		t.checkExpect(Sequence.makeSequence(namespace, 
//				new Definition("func", 
//						new Function(new ArrayList<String>(), 
//								Sequence.makeSequence(namespace, new Reference("@1", namespace)), namespace),
//						namespace), 
//				new Definition("orange", new NumberLiteral(5, namespace), namespace), 
//				
//				new FunctionCall(new Reference("func", namespace), 
//				new ArrayList<IValue>(Arrays.asList(new NumberLiteral(5, namespace))), namespace)).eval(namespace), new NumberLiteral(5, namespace));
	
		t.checkExpect(Sequence.makeSequence(namespace, 
				new Definition("orange", new Function(Utils.list(), // orange { !(@1) };
						Sequence.makeSequence(namespace, new FunctionCall(new NamedFunction("!"), Utils.list(new Reference("@1")))))), // orange(true);
				new FunctionCall(new Reference("orange"), Utils.list(new BooleanLiteral(true)))).eval(namespace), new BooleanLiteral(false)); // # -> false;
	}
	
	void testCond (Tester t) {
		
		initNS();

		/**# recursive test
		 * r {
		 * 
		 * 	if ({>(@1 0)} { # if statements have to take in three IExpressions, the first must evaluate to a boolean
		 * 		print(@1);
		 * 		r( -(@1 1) );
		 * 	} {
		 * 		print(@1);
		 * 	})
		 * 
		 * }
		 * 
		 * r(5); # -> Ferret: 5 4 3 2 1 0
		 */
		
		t.checkExpect(Sequence.makeSequence(new BooleanLiteral(true)).eval(namespace), new BooleanLiteral(true)); // recursion test
		
		t.checkExpect(Sequence.makeSequence(new Conditional(new BooleanLiteral(true), new BooleanLiteral(true), new BooleanLiteral(false))).eval(namespace), new BooleanLiteral(true));
		t.checkExpect(Sequence.makeSequence(new Conditional(new BooleanLiteral(false), new BooleanLiteral(true), new BooleanLiteral(false))).eval(namespace), new BooleanLiteral(false));
		
		t.checkExpect(Sequence.makeSequence(new Definition("greaterThanTwice", 
				new Function(Utils.list("a", "b"), // greaterThanTwice (a b) > { # a function greaterThanTwice, which returns a boolean of if a is greater than 2 * b
						Sequence.makeSequence(new FunctionCall(new NamedFunction(">"), 
								Utils.list(new Reference("a"), 
										new FunctionCall(new NamedFunction("*"), // >(a *(b 2)); }
												Utils.list(new Reference("b"), new NumberLiteral(2)))))))),
				new FunctionCall(new Reference("greaterThanTwice"), Utils.list(new NumberLiteral(11), new NumberLiteral(5)))).eval(namespace), 
				new BooleanLiteral(true)); // greaterThanTwice(11 5); # -> true
		
		// recursion test
		
		Sequence recursiveProgram = Sequence.makeSequence(new Definition("recursive", new Function( // define a new function defined as "recursive", with expected parameters "a"
					Utils.list("a"), Sequence.makeSequence(
								new Conditional(new FunctionCall(new NamedFunction(">"), Utils.list(new Reference("a"), new NumberLiteral(0))), // inside the function, if a is greater than 0
											Sequence.makeSequence(new FunctionCall(new NamedFunction("print"), Utils.list(new Reference("a"))), // print a
													new FunctionCall(new Reference("recursive"), Utils.list( // and recur by making a function call to "recursive", if the namespace passes down properly, "recursive" should still be found
															new FunctionCall(new NamedFunction("-"), Utils.list(new Reference("a"), new NumberLiteral(1))))) // recur with -1 to a
													), new FunctionCall(new NamedFunction("print"), Utils.list(new Reference("a"))) // at the base case, return a reference to a
										)
							)
				)), new FunctionCall(new Reference("recursive"), Utils.list(new NumberLiteral(5)))); // call the function with args 5
		
		recursiveProgram.eval(namespace);
		
		t.checkExpect(Sequence.makeSequence(new Conditional( // else if example
				Utils.list(new BooleanLiteral(false), new BooleanLiteral(false), new BooleanLiteral(true)), 
				Utils.list(new StringLiteral("1st Option"),new StringLiteral("2nd Option"), new StringLiteral("3rd Option")), new StringLiteral("else case"))).eval(namespace),
				new StringLiteral("3rd Option"));
		
		t.checkExpect(Sequence.makeSequence(new Operation("...", Utils.list(new NumberLiteral(0), new NumberLiteral(6)))).eval(namespace), new ListValue(Utils.list(new NumberLiteral(0), new NumberLiteral(1), new NumberLiteral(2), new NumberLiteral(3), new NumberLiteral(4), new NumberLiteral(5))));
	}
}

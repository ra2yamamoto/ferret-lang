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

class Namespace {
	ArrayList<Map<String, IValue>> namespaces; // Namespaces are stored as a list of maps so that proper scopes can be retained
	
	Namespace (ArrayList<Map<String, IValue>> nses) {
		this.namespaces = nses;
	}
	
	Namespace () {
		this.namespaces = new ArrayList<Map<String, IValue>>();
	}
	
	IValue get (String key) { // iterate backwards here
		
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
	
	Namespace copyWith (Map<String, IValue> otherMap) {
		ArrayList<Map<String, IValue>> end = new ArrayList<>();
		end.addAll(namespaces);
		end.add(otherMap);
		
		return new Namespace(end);
	}
}

interface IExpression {
	Namespace getNamespace();
	void setNamespace(Namespace ns);
	IValue eval(Namespace ns);
	String printOutput();
}

interface IValue extends IExpression {
	Datatype getType();
}

interface ICollection extends IValue {
	IValue get(String identifier);
	IValue set(String identifier, IValue entry);
}

class Nil implements IValue {

	public Namespace getNamespace() {
		return null;
	}
	
	public IValue eval(Namespace ns) {
		return null;
	}
	
	public void setNamespace (Namespace ns) {}
	
	public String printOutput () {
		return "nil";
	}

	public Datatype getType() {
		return Datatype.NIL;
	}}

// LITERALS

abstract class ALiteral implements IValue {
	Object value;
	
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
	
}

class NumberLiteral extends ALiteral {
	NumberLiteral (Number value, Namespace ns) {
		super(value.doubleValue(), ns);
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
}

// COLLECTIONS

class ListValue implements ICollection {
	
	ArrayList<IValue> value;
	Namespace ns;
	
	ListValue (ArrayList<IValue> value, Namespace ns) {
		this.value = value;
		this.ns = ns;
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
	
	public IValue get(String identifier) {
		int index;
		
		try {
			
			index = Integer.valueOf(identifier);
			return value.get(index);
			
		} catch (Exception e) {}
		
		return new Nil();
	}
	
	public IValue set(String identifier, IValue entry) {
		int index;
		
		try {
			index = Integer.valueOf(identifier);
			
			if (index < value.size() && index > 0) {
				value.set(index, entry);
			} else {
				value.add(entry);
			}
		} catch (Exception e) {
			return new Nil(); // handle this error better TODO
		}
		
		return this;
	}
	
	public void setNamespace (Namespace ns) {
		this.ns = ns;
	}
	
	public String printOutput () {
		return "nil";
	}
}

class MapValue implements ICollection { // I'm trying to avoid conflicts with the actual Map class
	
	Map<String, IValue> value;
	Namespace ns;
	
	MapValue (Map<String, IValue> value, Namespace ns) {
		this.value = value;
		this.ns = ns;
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
	
	public IValue get(String identifier) {
		return value.containsKey(identifier) ? value.get(identifier) : new Nil(); // they're going to return a nil for now
	}
	
	public IValue set(String identifier, IValue entry) {
		value.put(identifier, entry);
		return this;
	}
	
	public void setNamespace (Namespace ns) {
		this.ns = ns;
	}
	
	public String printOutput () {
		return "nil";
	}
}

class Function implements IValue {

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

	public IValue eval(Namespace ns) {
		this.setNamespace(ns);
		return this;
	}
	
	public IValue call(ArrayList<IValue> args, Namespace ns) {
		this.setNamespace(ns);
		
		Map<String, IValue> argNS = new HashMap<>();
		
		if (args.size() < params.size()) {
			throw new IllegalArgumentException("Arity mismatch");
		}
		
		for (int i = 0; i < args.size(); i++) {
			argNS.put("@" + String.valueOf(i + 1), args.get(i)); // adds @1 ... @n to the namespace
			
			if (i < params.size()) {
				argNS.put(params.get(i), args.get(i)); // adds the expected arguments keys to the namespace
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
	
}

@FunctionalInterface
interface IFuncOperation {
	IValue apply(ArrayList<ALiteral> args);
};

class NamedFunction extends Function {
	Map<String, IFuncOperation> funcs = new HashMap<>();
	
	String type;
	IFuncOperation operation;
	static boolean firstPrint = true;
	
	NamedFunction (String type, Namespace ns) {
		super(new ArrayList<String>(), new Sequence(new ArrayList<IExpression>(), ns), ns);
		
		ToDoubleFunction<ALiteral> literalToDouble = literal -> ((Number) literal.value).doubleValue(); 
		
		funcs.put("+", l -> new NumberLiteral(l.stream().collect(Collectors.summingDouble(e -> ((Number) e.value).doubleValue())), this.ns));
		funcs.put("-", l -> new NumberLiteral(l.stream().mapToDouble(literalToDouble).reduce((a, b) -> a - b).getAsDouble(), this.ns));
		funcs.put("*", l -> new NumberLiteral(l.stream().mapToDouble(literalToDouble).reduce(1, (a, b) -> a * b), this.ns));
		funcs.put("^", l -> new NumberLiteral(l.stream().mapToDouble(literalToDouble).reduce((a, b) -> Math.pow(a, b)).getAsDouble(), this.ns));
		//funcs.put("/", l -> new NumberLiteral(((Number) l.get(0).value).doubleValue() / ((Number) l.get(1).value).doubleValue(), this.ns));
		funcs.put("/", l -> new NumberLiteral(l.stream().mapToDouble(literalToDouble).reduce((a, b) -> a / b).getAsDouble(), this.ns));
		
		
		funcs.put("<", l -> new BooleanLiteral(((Number) l.get(0).value).doubleValue() < ((Number) l.get(1).value).doubleValue(), this.ns));
		funcs.put(">", l -> new BooleanLiteral(((Number) l.get(0).value).doubleValue() > ((Number) l.get(1).value).doubleValue(), this.ns));
		funcs.put("<=", l -> new BooleanLiteral(((Number) l.get(0).value).doubleValue() <= ((Number) l.get(1).value).doubleValue(), this.ns));
		funcs.put(">=", l -> new BooleanLiteral(((Number) l.get(0).value).doubleValue() >= ((Number) l.get(1).value).doubleValue(), this.ns));
		funcs.put("=", l -> new BooleanLiteral(new Double(((Number) l.get(0).value).doubleValue()).equals(new Double(((Number) l.get(1).value).doubleValue())), this.ns));
		funcs.put("!=", l -> new BooleanLiteral(!(new Double(((Number) l.get(0).value).doubleValue()).equals(new Double(((Number) l.get(1).value).doubleValue()))), this.ns));

		funcs.put("and", l -> new BooleanLiteral(l.stream().allMatch(e -> (Boolean) e.value), this.ns));
		funcs.put("or", l -> new BooleanLiteral(l.stream().anyMatch(e -> (Boolean) e.value), this.ns));
		funcs.put("not", l -> new BooleanLiteral(!((Boolean) l.get(0).value), this.ns));
		funcs.put("!", l -> new BooleanLiteral(!((Boolean) l.get(0).value), this.ns));
		funcs.put("print", l -> new Nil());
		
		this.type = type;
		this.operation = funcs.get(type);
		
		if (this.operation == null) {
			throw new IllegalArgumentException("NamedFunction given a non-standard function");
		}
	}
	
	NamedFunction (String type) {
		super(new ArrayList<String>(), new Sequence(new ArrayList<IExpression>(), new Namespace()));
		
		ToDoubleFunction<ALiteral> literalToDouble = literal -> ((Number) literal.value).doubleValue(); 
		
		funcs.put("+", l -> new NumberLiteral(l.stream().collect(Collectors.summingDouble(e -> ((Number) e.value).doubleValue())), this.ns));
		funcs.put("-", l -> new NumberLiteral(l.stream().mapToDouble(literalToDouble).reduce((a, b) -> a - b).getAsDouble(), this.ns));
		funcs.put("*", l -> new NumberLiteral(l.stream().mapToDouble(literalToDouble).reduce(1, (a, b) -> a * b), this.ns));
		funcs.put("^", l -> new NumberLiteral(l.stream().mapToDouble(literalToDouble).reduce((a, b) -> Math.pow(a, b)).getAsDouble(), this.ns));
		//funcs.put("/", l -> new NumberLiteral(((Number) l.get(0).value).doubleValue() / ((Number) l.get(1).value).doubleValue(), this.ns));
		funcs.put("/", l -> new NumberLiteral(l.stream().mapToDouble(literalToDouble).reduce((a, b) -> a / b).getAsDouble(), this.ns));
		
		
		funcs.put("<", l -> new BooleanLiteral(((Number) l.get(0).value).doubleValue() < ((Number) l.get(1).value).doubleValue(), this.ns));
		funcs.put(">", l -> new BooleanLiteral(((Number) l.get(0).value).doubleValue() > ((Number) l.get(1).value).doubleValue(), this.ns));
		funcs.put("<=", l -> new BooleanLiteral(((Number) l.get(0).value).doubleValue() <= ((Number) l.get(1).value).doubleValue(), this.ns));
		funcs.put(">=", l -> new BooleanLiteral(((Number) l.get(0).value).doubleValue() >= ((Number) l.get(1).value).doubleValue(), this.ns));
		funcs.put("=", l -> new BooleanLiteral(new Double(((Number) l.get(0).value).doubleValue()).equals(new Double(((Number) l.get(1).value).doubleValue())), this.ns));
		funcs.put("!=", l -> new BooleanLiteral(!(new Double(((Number) l.get(0).value).doubleValue()).equals(new Double(((Number) l.get(1).value).doubleValue()))), this.ns));

		funcs.put("and", l -> new BooleanLiteral(l.stream().allMatch(e -> (Boolean) e.value), this.ns));
		funcs.put("or", l -> new BooleanLiteral(l.stream().anyMatch(e -> (Boolean) e.value), this.ns));
		funcs.put("not", l -> new BooleanLiteral(!((Boolean) l.get(0).value), this.ns));
		funcs.put("!", l -> new BooleanLiteral(!((Boolean) l.get(0).value), this.ns));
		funcs.put("print", l -> new Nil());
		
		this.type = type;
		this.operation = funcs.get(type);
		
		if (this.operation == null) {
			throw new IllegalArgumentException("NamedFunction given a non-standard function");
		}
	}
	
	public IValue call (ArrayList<IValue> args, Namespace ns) {
		this.setNamespace(ns);
		
		ArrayList<ALiteral> endArgs = new ArrayList<>();
		
		if (this.type.equals("print")) {
			StringBuilder end = new StringBuilder();
			
			args.stream().forEach(val -> end.append(val.printOutput() + "\n"));
			
			if (firstPrint) {
				System.out.println("Ferret output: \n\n" + end.toString());
				firstPrint = false;
			} else {
				System.out.println(end.toString());
			}
			
			return new StringLiteral(end.toString());
		}
		
		for (int i = 0; i < args.size(); i++) { // all the args we get should be as reduced as possible, as we eval() them in the functionCall eval()
			if (!(args.get(i) instanceof ALiteral)) { // if the IValue at the index i isn't a literal
				// ERROR TODO
			} else {
				endArgs.add((ALiteral) args.get(i));
			}
		}
		
		return this.operation.apply(endArgs);
	}
}

class Sequence implements IExpression { // CHANGE SEQUENCES TO SET THEIR OWN NAMESPACE ON EVAL AND GIVE ALL THE CHILDREN THE NAMESPACE
	
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
	
}

class Operation extends ANode {
	
	Operation () {
		super(new Namespace(new ArrayList<Map<String, IValue>>(Arrays.asList(new HashMap<String, IValue>()))));
	}

	@Override
	public IValue eval(Namespace ns) {
		this.setNamespace(ns);
		// TODO Auto-generated method stub
		return null;
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
	
}

class Conditional extends ANode {

	IExpression condition;
	IExpression then;
	IExpression elseExpr;
	
	Conditional(IExpression condition, IExpression then, IExpression elseExpr) {
		super(new Namespace());
		
		this.condition = condition;
		this.then = then;
		this.elseExpr = elseExpr;
		// TODO Auto-generated constructor stub
	}
	
	public IValue eval(Namespace ns) {
		this.setNamespace(ns);
		IValue evaledCond = condition.eval(this.ns);
		
		if (!(evaledCond instanceof BooleanLiteral)) {
			throw new IllegalArgumentException("Condition in if statement returned a " + evaledCond.getClass().getName() + ", not a boolean.");
		}
		
		// The main calculation:
		
		if ((boolean) ((BooleanLiteral) evaledCond).value) { // lovely casting
			return then.eval(this.ns);
		} else {
			return elseExpr.eval(this.ns);
		}
		
	}
	
}

// TODO: Function, then fill out the parser with all the different ast ANodes

class Utils {
	static <T> ArrayList<T> list (T ...items) {
		return new ArrayList<T>(Arrays.asList(items));
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
	}
}

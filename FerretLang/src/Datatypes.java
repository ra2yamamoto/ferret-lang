import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import tester.Tester;

enum Datatype {
	BOOLEAN, NUMBER, STRING, FUNCTION, LIST, MAP, OPERATOR, NIL, AST_NODE
}

class Namespace {
	ArrayList<Map<String, IValue>> namespaces; // Namespaces are stored as a list of maps so that proper scopes can be retained
	
	Namespace (ArrayList<Map<String, IValue>> nses) {
		this.namespaces = nses;
	}
	
	IValue get (String key) {
		
		for (int i = 0; i < this.namespaces.size(); i++) {
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

	public Datatype getType() {
		return Datatype.NIL;
	}}

// LITERALS

abstract class ALiteral implements IValue {
	Object value;
	Namespace ns;
	
	ALiteral (Object value, Namespace ns) {
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
	
	public void setNamespace (Namespace ns) {
		this.ns = ns;
	}
	
}

class NumberLiteral extends ALiteral {
	NumberLiteral (Number value, Namespace ns) {
		super(value, ns);
	}

	public Datatype getType () {
		return Datatype.NUMBER;
	}
}

class BooleanLiteral extends ALiteral {
	BooleanLiteral (boolean value, Namespace ns) {
		super(value, ns);
	}

	public Datatype getType () {
		return Datatype.BOOLEAN;
	}
}

class StringLiteral extends ALiteral {
	StringLiteral (String value, Namespace ns) {
		super(value, ns);
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
		
		return this.body.eval(this.ns.copyWith(argNS)); // TODO: the issue with this is that any modifications to global variable won't actually change it
	}

	public Datatype getType() {
		return null;
	}
	
	public void setNamespace (Namespace ns) {
		this.ns = ns;
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
	
	public void setNamespace (Namespace ns) {
		this.ns = ns;
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
}

class FunctionCall extends ANode { // have to have their own args stored, actuqlly, same with all the ANodes, args don't need to be in every IExpression

	Function function;
	ArrayList<IValue> args;
	
	FunctionCall (Function f, ArrayList<IValue> args, Namespace ns) {
		super(ns);
		this.function = f;
		this.args = args;
	}
	
	public IValue eval(Namespace ns) {
		this.setNamespace(ns);
		return function.call(this.args, this.getNamespace());
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
	
	public IValue eval(Namespace ns) {
		this.setNamespace(ns);
		this.ns.set(this.key, this.value);
		return this.ns.get(this.key);
	}

	public Datatype getType() {
		return Datatype.AST_NODE;
	}
	
}

// TODO: Function, then fill out the parser with all the different ast ANodes

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

}

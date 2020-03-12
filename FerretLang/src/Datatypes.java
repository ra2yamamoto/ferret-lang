import tester.Tester;

interface IExpression {
	Object eval(); // evaluates the expression
}

abstract class AValue implements IExpression {
	Object value;

	AValue (Object value) {
		this.value = value;
	}

	public Object eval() {
		return this.value;
	}
}

class NumberV extends AValue {
	NumberV (Number value) {
		super(value);
	}
}

class BoolV extends AValue {
	BoolV (boolean value) {
		super(value);
	}
}

class StringV extends AValue {
	StringV (String value) {
		super(value);
	}
}

class ValueTests {
	void testValues (Tester t) {
		NumberV n = new NumberV(30.91);
		t.checkExpect(n.eval(), 30.91);
		
		BoolV b = new BoolV(false);
		t.checkExpect(b.eval(), false);
		
		StringV s = new StringV("Hello World");
		t.checkExpect(s.eval(), "Hello World");
	}
}
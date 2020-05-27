import tester.Tester;

class TestHarness {
	// A test harness, only checks for equality of outcomes
	IExpression program;
	IValue expected;
	
	Tester t;
	Namespace ns;

	TestHarness (String source, IValue expected) {
		this.program = new Parser(Lexer.lex(source)).parse();
		this.expected = expected;
	}
	
	void runTests () {
		t = new Tester();
		
		this.ns = Namespace.stdlib();
		//System.out.println(this.program.eval(ns).equals(this.expected));
		t.checkExpect(this.program.eval(ns), this.expected);
	}
}

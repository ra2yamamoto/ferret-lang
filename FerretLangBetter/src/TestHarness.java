import tester.Tester;

class TestHarness {
	// A test harness, only checks for equality of outcomes
	IExpression program;
	IValue expected;
	
	Tester t = new Tester();
	Namespace ns;

	TestHarness (String source, IValue expected) {
		this.program = new Parser(Lexer.lex(source)).parse();
		this.expected = expected;
	}
	
	void runTests () {
		this.ns = new Namespace();
		t.checkExpect(this.program.eval(ns), this.expected);
	}
}

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import tester.Tester;

class Parser {
	ArrayList<AToken> input;
	int index = 0;
	boolean debugMode = false;
	
	Parser (ArrayList<AToken> input) {
		this.input = input;
	}
	
	IExpression parse () {
		ArrayList<IExpression> exprs = new ArrayList<>();
		
		while (!atEnd()) {
			exprs.add(expression());
			consume(new SeparatorT(";"));
			consume(new BreakT());
		}
		
		return new Sequence(exprs, new Namespace());
	}
	
	IExpression expression () {
		IValue left = operation();
		print("expr on " + left);
		
		if (current().value.equals("(") && !skip(till(new SeparatorT(")")) + 1).value.equals(">")) { // it's a call
			print("call detected on" + current());
			return call(left);
		} else if (current().value.equals(";")) {
			consume(new SeparatorT(";"));
			return left;
		} else {
			IExpression def = definition();
			return def;
		}
	}
	
	IExpression definition () { // the previous is the identifier
		IExpression def = new Definition((String) prev().getValue(), operation());
		expect(new SeparatorT(";")); // expect a semicolon after it
		return def;
	}
	
	IValue function () { // TODO: fill out
		//print("ffunc");
		
		print("FUNC");
		
		ArrayList<IExpression> bodyList = new ArrayList<IExpression>();
		ArrayList<String> params = new ArrayList<String>();
		
		if (check(new SeparatorT("{"))) {
			advance(); // move past {
			
			while (!check(new SeparatorT("}"))) {
				bodyList.add(expression());
			}
			
		} else if (check(new SeparatorT("("))) {
			
			params = parameters();
			expect(new OperatorT(">"));
			expect(new SeparatorT("{"));
			
			while (!check(new SeparatorT("}"))) {
				bodyList.add(expression());
			}
			
		}
		
		advance();
		
		print("current " + current());
		
		return new Function(params, new Sequence(bodyList, new Namespace()));
	}
	
	IValue call (IValue func) {
		int beginning = this.index;
		// after arguments, if the current is a > then go back to beginning, and call function
		ArrayList<IValue> args = arguments();
		
		if (check(new OperatorT(">"))) {
			this.index = beginning;
			return function();
		}
		
		IValue f = new FunctionCall(func, args);
		//print(f);
		return f;
	}
	
	IValue primary () {
		
		if (checkLitType(Type.BOOL)) {
			return new BooleanLiteral(advance().value.equals("true") ? true : false);
		} else if (checkLitType(Type.NUMBER)) {
			if (current().value.equals("-")) {
				advance();
				return call(new Reference("-"));
			}
			
			return new NumberLiteral((Number) Double.valueOf((String) advance().value));
		} else if (checkLitType(Type.STRING)) {
			return new StringLiteral((String) advance().value);
		} else if (checkLitType(Type.NIL)) {
			advance();
			return new Nil();
		} else {
			throw new ParsingError("Primary expected");
		}
	}
	
	ArrayList<String> parameters () { // should be starting at a (
		advance();
		ArrayList<String> end = new ArrayList<String>();
		
		print("param current " + current());
		
		while (!check(new SeparatorT(")"))) {
			if (!checkType(new IdentifierT(""))) { // if it's not a literal
				throw new ParsingError("Given non-Identifier types in parameter list.");
			}
			
			end.add((String) current().value);
			advance();
			
			if (check(new SeparatorT(";"))) {
				throw new ParsingError("Semicolon found in argument list");
			}
		}
		
		advance();
		return end;
	}
	
	ArrayList<IValue> arguments () { // should be starting at a ( TODO
		print("args called");
		
		advance(); // move from the first one
		ArrayList<IValue> end = new ArrayList<IValue>();
		
		while (!check(new SeparatorT(")"))) {
			end.add(operation());
			if (check(new SeparatorT(";"))) {
				throw new ParsingError("Semicolon found in argument list");
			}
		}
		
		advance(); // increment after )
		
		return end;
	}
	
	IValue operation () {
		return collectionInsert();
	}
	
	IValue collectionInsert () {
		IValue left = collectionAccess();
		
		if (check(new OperatorT("<"))) {
			ArrayList<IValue> values = new ArrayList<>();
			ArrayList<IValue> indeces = new ArrayList<>();
			print("AAAHAHAHAHAHHAAA");
			
			while (check(new OperatorT("<"))) {
				if (next().value.equals("<")) {
					indeces.add(new NumberLiteral(-1));
					advance();
					advance();
					print("chose at end, " + current());
				} else {
					advance();
					indeces.add(collectionAccess());
					print(current().toString());
					if (!check(new OperatorT("<"))) {
						throw new ParsingError("Expected closing '<' in list insert operation.");
					}
					advance();
					print("chose somewhere, " + current());
				}
				
				values.add(collectionAccess());
			}
			
			ArrayList<IValue> consList = new ArrayList<>();
			
			for (int i = 0; i < indeces.size(); i++) {
				consList.add(indeces.get(i));
				consList.add(values.get(i));
			}
			
			consList.add(left);
			return new Operation("<<", consList);
			
		}
		
		return left;
	}
	
	IValue collectionAccess () {
		IValue left = collectionCreate();
		if (check(new OperatorT(":"))) {
			advance();
			return new Operation(":", Utils.list(left, value()));
		}
		return left;
	}
	
	IValue collectionCreate () {
		//print(current());
		
		if (check(new SeparatorT("["))) {
			ArrayList<IValue> end = new ArrayList<IValue>();
			advance(); // move past [
			while (!check(new SeparatorT("]"))) {
				end.add(value());
			}
			advance(); // move out of ]
			return new ListValue(end);
		}
		
		IValue left = value();
		//print(left);
		
		if (current().value.equals("...")) {
			advance();
			return new Operation("...", Utils.list(left, value()));
		} else {
			return left;
		}
	}
	
	IValue value () { // forward only
		if (checkType(new LiteralT("", Type.NIL))) { // if its a literal, pass to primary
			return primary();
		} else if (check(new OperatorT(">"))) {
			advance();
			IValue c = call(new Reference(">"));
			return c;
		} else if (check(new OperatorT("<"))) {
			advance();
			if (check(new IdentifierT("="))) {
				advance();
				return call(new Reference("<="));
			}
			IValue c = call(new Reference("<"));
			return c;
		} else if (checkType(new IdentifierT(""))) { // if its an identifier, it could be
			int checkpoint = this.index;
			
			if (next().value.equals("(")) { // if its a function call
				advance();
				IValue c = call(new Reference((String) prev().getValue()));
				if (c instanceof FunctionCall) {
					return c;
				}
			}
			
			this.index = checkpoint;
			return new Reference((String) advance().getValue());
		} else if (check(new SeparatorT("(")) || check(new SeparatorT("{"))) { // it's a function
			IValue f = function();
			
			if (check(new SeparatorT("("))) { // if there's a call on it
				return call(f);
			}
			
			return f;
		} else { // PROBLEM
			//print("here");
			advance();
			return new Nil();
		}
	}
	
	AToken consume (AToken t) {
		while (!atEnd() && check(t)) {
			advance();
		}
		return current();
	}
	
	boolean acceptType (AToken expected) {
		if (current().getClass().equals(expected.getClass())) {
			advance();
			return true;
		}
		return false;
	}
	
	boolean accept (AToken expected) {
		if (current().value.equals(expected.value)) {
			advance();
			return true;
		}
		return false;
	}
	
	boolean checkType (AToken t) {
		if (current().getClass().equals(t.getClass())) {
			return true;
		}
		return false;
	}
	
	boolean checkLitType (Type t) {
		if (current().isLiteral() && ((LiteralT) current()).type == t) {
			return true;
		}
		return false;
	}
	
	boolean check (AToken t) {
		if (current().value.equals(t.value)) {
			return true;
		}
		return false;
	}
	
	boolean expect (AToken expected) {
		if (!atEnd() && accept(expected)) {
			return true;
		}
		throw new ParsingError("Expected token " + expected.toString());
	}
	
	int till (AToken t) {
		int i = 0;
		while (!skip(i).value.equals(t.value)) {
			i++;
		}
		return i;
	}
	
	AToken advance () {
		if (!atEnd()) index++;
		return prev();
	}
	
	AToken current () {
		return !atEnd() ? input.get(index) : prev();
	}
	
	AToken next () {
		return atEnd() ? input.get(index) : input.get(index + 1);
	}
	
	AToken skip (int i) {
		return atEnd() ? input.get(index) : input.get(index + i);
	}
	
	AToken prev () {
		return index == 0 ? input.get(index) : input.get(index - 1);
	}
	
	boolean atEnd () {
		return index > (input.size() - 1);
	}
	
	void print (String s) {
		if (debugMode) {
			System.out.println(s);
		}
	}

}

class ParsingError extends RuntimeException {
	private static final long serialVersionUID = 1L;
	ParsingError (String e) {
		super(e);
	}
	
	ParsingError (String e, AToken location) {
		super(e + " at " + location);
	}
};

class TestPair {
	String program;
	String output;
	IExpression ASTOutput;
	
	TestPair (String program, String output) {
		this.program = program;
		this.output = output;
	}
	
	TestPair (String program, IExpression output) {
		this.program = program;
		this.ASTOutput = output;
	}
	
	void runInterpretTest (Tester t) {
		t.checkExpect(TestPair.interpretString(this.program), TestPair.interpretString(this.output));
	}
	
	void runParseTest (Tester t) {
		t.checkExpect(new Parser(Lexer.lex(this.program)).parse(), this.output);
	}
	
	static IValue interpretString (String in) {
		return new Parser(Lexer.lex(in)).parse().eval(Namespace.stdlib());
	}
	
	static void runInterpretTestsFromList (ArrayList<TestPair> tests, Tester t) {
		tests.stream().forEach(test -> test.runInterpretTest(t));
	}
	static void runParseTestsFromList (ArrayList<TestPair> tests, Tester t) {
		tests.stream().forEach(test -> test.runInterpretTest(t));
	}
}

class TestParser {
	void testInterpret (Tester t) {
		TestPair.runInterpretTestsFromList(new ArrayList<TestPair>(Arrays.asList(
				new TestPair("a 5; a;", "5;"),
				new TestPair("square (a) > { *(a a); }; square(5);", "25;"),
				new TestPair("square { *(@1 @1); }; square(5);", "25;"),
				new TestPair("if (true 1 0);", "1;"),
				new TestPair("sum (n) > {" // recursion
						+ "    if (<(1 n) {"
						+ "        +(n sum(-(n 1)));"
						+ "    } {"
						+ "        1;"
						+ "    });"
						+ "}; sum(5);", "15;")//,
				//new TestPair("for (0...10 {+(1 @1);});", "[1 2 3 4 5 6 7 8 9 10];") // This works, but the namespaces are different
				)), t);
	}
	
	void testParse (Tester t) {
		
	}
	
	void testDefs(Tester t) {
//		ArrayList<AToken> list = Lexer.lex("a (orange potato) > { print(+(orange potato) \"bruh\"); };\n a(1 2);");
//		ArrayList<AToken> list = Lexer.lex("if ({true} {print(\"hello\")} print(\"goodbye\"));");
//		ArrayList<AToken> list = Lexer.lex("r {"
//				+ "if (<(0 @1) {"
//				+ "    print(@1); "
//				+ "    r(-(@1 1));"
//				+ "} {"
//				+ "    print(@1 \"done iterating\")});"
//				+ "};"
//				
//				+ "r(100);");
//		ArrayList<AToken> list = Lexer.lex("r {print(<(0 @1));}; r(-5219);");
//		ArrayList<AToken> list = Lexer.lex("r {print(<(0 -(@1 100)));}; r(-5219);");
//		ArrayList<AToken> list = Lexer.lex("r {print(@1);}; r(-2);");
//		ArrayList<AToken> list = Lexer.lex("print(\"thing: \" {+(@1 2)}(4) \"\nhey\")");
//		ArrayList<AToken> list = Lexer.lex(
//				"sum (n) > {"
//				+ "    if (<(1 n) {"
//				+ "        +(n sum(-(n 1)));"
//				+ "    } {"
//				+ "        1;"
//				+ "    })"
//				+ "};"
//				+ ""
//				+ "print(sum(5));");
//		ArrayList<AToken> list = Lexer.lex(
//				  "seq (start end fun) > {"
//				+ "    if (<=(end start) {"
//				+ "        print(fun(start));"
//				+ "    } {"
//				+ "        print(fun(start));"
//				+ "        seq(+(start 1) end fun);"
//				+ "    });"
//				+ "};"
//				+ ""
//				+ "seq(1 7 {*(@1 @1)});");
		ArrayList<AToken> list = Lexer.lex("[] << 3 << 4 <1< 5;");
//		ArrayList<AToken> list = Lexer.lex("a 1;");
		System.out.println(list);
		//ArrayList<AToken> list = Lexer.lex("");
		Parser parseTest = new Parser(list);
		Sequence program = (Sequence) parseTest.parse();
		System.out.println(program);
		System.out.println("=> " + program.eval(Namespace.stdlib()));
	}
}
/**
r {
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

/**CFG
 * 
 * 
 * 
 * 
 */
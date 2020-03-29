import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import tester.Tester;

enum Type {
	NUMBER, BOOL, STRING, NIL
}

abstract class AToken {
	Object value;
	
	AToken (Object value) {
		this.value = value;
	}
	
	Object getValue () {
		return this.value;
	};
	
	Object hasValue (Object other) {
		return this.value.equals(other);
	}
	
	boolean isIdentifier () {
		return false;
	}
	
	boolean isSeparator () {
		return false;
	}
	
	boolean isOperator () {
		return false;
	}
	
	boolean isLiteral () {
		return false;
	}
	
	boolean isBreak () {
		return false;
	}
}

class IdentifierT extends AToken { // function names can only be alphanumeric
	IdentifierT (String value) {
		super(value);
	}
	
	boolean isIdentifier () {
		return true;
	}
}

class SeparatorT extends AToken {

	SeparatorT (String value) {
		super(value);
	}
	
	boolean isSeparator () {
		return true;
	}
	
}

class OperatorT extends AToken {
	
	OperatorT (String value) {
		super(value);
	}
	
	boolean isOperator () {
		return true;
	}
	
}

class LiteralT extends AToken {
	Type type;
	
	LiteralT (Object value, Type type) {
		super(value);
		this.type = type;
	}
	
	@Override
	public String toString () {
		return(this.type.name() + " " + this.value);
	}
	
	boolean isLiteral () {
		return true;
	}
}

class BreakT extends AToken {
	BreakT () {
		super("\n");
	}
	
	boolean isBreak () {
		return true;
	}
}

class Lexer {

	static ArrayList<AToken> lex (String code) {
		/**
		 * Rules for lexing:
		 * FOR NOW:
		 * 	Iterate on a character level
		 * 	If find a special character, in the regexp???, lexicalize it
		 * 	If find a identifier, add it
		 * 
		 * for each character:
		 * 	if whitespace, increment i, (do nothing)
		 * 	if " run a method findString on the string to find the end of the string, and add it to a literalT of type LiteralType.STRING
		 * 	if a number, run a method to find the end of the number
		 * 	otherwise, match it to a bunch of regex
		 * */
		
		Map<String, String> opMap = new HashMap<String, String>(); // the full form of the operators to test for
		opMap.put(":", ":");
		opMap.put("<", "<<");
		opMap.put(">", ">>>");
		opMap.put(".", "...");
		
		ArrayList<AToken> result = new ArrayList<AToken>();
		
		for (int i = 0; i < code.length(); i++) {
			String current = String.valueOf(code.charAt(i));
			
			if (current.matches("\n")) {
				result.add(new BreakT());
				continue;
			}
			
			if (current.matches("\\s+")) { // if whitespace
				continue;
			}
			
			if (current.matches("\\{|\\[|\\(|\\}|\\]|\\)|;")) { // if a separator: { } [ ] ( ) ;
				result.add(new SeparatorT(current));
				continue;
			}
			
			//TODO: put operators before identifiers
			if (current.matches(":|<|>|\\.")) { // if an operator, ie \: << >>> ...
				int sequenceIndex = indexAtSequence(code, i, opMap.get(current));
				
				if (sequenceIndex >= 0) {
					result.add(new OperatorT(code.substring(i, sequenceIndex + 1)));
					i = sequenceIndex;
					continue;
				} else {
					// throw a parsing error???
				}
			}
			
			if (current.matches("[a-zA-Z|+|\\-|*|/|<|>|=|!|^|@]")) { // if a Identifier
				int nextIndex = indexUntilNotMatching(code, i, "[a-zA-Z0-9|+|\\-|*|/|<|>|=|!|^|@]");
				String toAdd = code.substring(i, nextIndex + 1);
				
				if (toAdd.equals(">")) { // kinda gross
					result.add(new OperatorT(">"));
				} else if (toAdd.equals("nil")) {
					result.add(new LiteralT(toAdd, Type.NIL));
				} else {
					result.add(toAdd.equals("true") || toAdd.equals("false") ? new LiteralT(toAdd, Type.BOOL) : new IdentifierT(toAdd)); // find where the identifier ends, essentially, when the next regex fails
				}
				
				// also check if the string is actually a boolean (means true and false can't be overridden)
				i = nextIndex; // I think this is how it's supposed to work, because the i++ will automatically advance it
				continue;
			}
			
			if (current.matches("[0-9]")) { // if a literal, ie if a number
				int nextIndex = indexUntilNotMatching(code, i, "\\d|\\."); // if it's a number of a period
				
				boolean found = false;
				
				for (int j = i; j < nextIndex; j++) { // check each "number" (either 0-9 or a period)
					if (String.valueOf(code.charAt(j)).equals(".")) { // if there's a period, check if it repeats three times
						if (indexAtSequence(code, j, "...") >= 0) { // if it does, it'll be an operator
							result.add(new LiteralT(code.substring(i, j), Type.NUMBER)); // add this number as a literal
							i = j - 1; // update the big index
							found = true;
							break; // break out of the loop
						}
					};
				}
				
				if (found) { // if there was a ... operator found, then don't try to add this "number", just evaluate what's next (ie a ... , which will be interpreted as an operator)
					continue;
				} else {
					result.add(new LiteralT(code.substring(i, nextIndex + 1), Type.NUMBER));
					i = nextIndex;
					continue;
				}
				
			}
			
			if (current.matches("\\\"|'")) { // if a literal, ie a string denoted by "" or ''
				int nextIndex = indexUntil(code, i + 1, current);
				
				result.add(new LiteralT(code.substring(i + 1, nextIndex), Type.STRING));
				i = nextIndex;
				continue;
			}
			
			if (current.matches("#")) {
				int nextIndex = indexUntil(code, i, "\n");
				
				result.add(new BreakT());
				i = nextIndex;
				continue;
			}
			
		}

		return result;

	}

	static int indexUntil (String input, int start, String toMatch) { // use for comments, find until \n // returns the index that is the same
		for (int i = start; i < input.length(); i++) {
			if (String.valueOf(input.charAt(i)).equals(toMatch)) {
				return i;
			};
		}
		
		return input.length() - 1;
	}
	
	static int indexUntilNotMatching (String input, int start, String matchingRegex) { // use for comments, find until \n // returns the last index for which it is matching
		for (int i = start; i < input.length(); i++) {
			if (!String.valueOf(input.charAt(i)).matches(matchingRegex)) {
				return i - 1;
			};
		}
		
		return input.length() - 1;
	}
	
	static int indexAtSequence (String input, int start, String sequenceToFind) {
		int index = 0;
		
		for (int i = 0; i < sequenceToFind.length(); i++) {
			if (input.charAt(start + i) != sequenceToFind.charAt(i)) {
				return -1;
			}
			
			index = i;
		}
		
		return start + index;
	}

}

class Examples {
	
	void testStrings (Tester t) {
		String seq = "oranges dogs and rabbits and cows";
		
		t.checkExpect(Lexer.indexAtSequence(seq, 8, "dogs"), 11);
		t.checkExpect(Lexer.indexUntilNotMatching(seq, 0, "[a-z]"), 6);
		t.checkExpect(Lexer.indexUntil(seq, 0, "s"), 6);
		t.checkExpect(Lexer.indexUntil("apples \"bananas\";", 8, "\""), 15);
		
		AToken apples = new IdentifierT("apples");
		AToken bananas = new LiteralT("bananas", Type.STRING);
		AToken semicolon = new SeparatorT(";");
		
		t.checkExpect(Lexer.lex("a").get(0), new IdentifierT("a"));
		t.checkExpect(Lexer.lex("apples").get(0), new IdentifierT("apples"));
		t.checkExpect(Lexer.lex("apples nil;"), new ArrayList<AToken>(Arrays.asList(apples, new LiteralT("nil", Type.NIL), semicolon)));
		t.checkExpect(Lexer.lex("apples bananas"), new ArrayList<AToken>(Arrays.asList(apples, new IdentifierT("bananas"))));
		t.checkExpect(Lexer.lex("apples 'bananas'"), new ArrayList<AToken>(Arrays.asList(apples, bananas)));
		t.checkExpect(Lexer.lex("apples \"bananas\""), new ArrayList<AToken>(Arrays.asList(apples, bananas)));
		t.checkExpect(Lexer.lex("apples \"bananas\";"), new ArrayList<AToken>(Arrays.asList(apples, bananas, semicolon)));
		t.checkExpect(Lexer.lex("apples 30.005;"), new ArrayList<AToken>(Arrays.asList(apples, new LiteralT("30.005", Type.NUMBER), semicolon)));
		t.checkExpect(Lexer.lex("apples true;"), new ArrayList<AToken>(Arrays.asList(apples, new LiteralT("true", Type.BOOL), semicolon)));
		t.checkExpect(Lexer.lex("apples truee;"), new ArrayList<AToken>(Arrays.asList(apples, new IdentifierT("truee"), semicolon)));
		t.checkExpect(Lexer.lex("apples truee;"), new ArrayList<AToken>(Arrays.asList(apples, new IdentifierT("truee"), semicolon)));
		t.checkExpect(Lexer.lex("apples 'bananas';\n1.00001;"), new ArrayList<AToken>(Arrays.asList(apples, bananas, semicolon, new BreakT(), new LiteralT("1.00001", Type.NUMBER), semicolon)));
		t.checkExpect(Lexer.lex("apples; # bruh moment\n2.4601;"), new ArrayList<AToken>(Arrays.asList(apples, semicolon, new BreakT(), new LiteralT("2.4601", Type.NUMBER), semicolon)));
		
		t.checkExpect(Lexer.lex("f (a) > {1}"), new ArrayList<AToken>(Arrays.asList(
				new IdentifierT("f"), new SeparatorT("("), new IdentifierT("a"), new SeparatorT(")"), new OperatorT(">")
				, new SeparatorT("{"), new LiteralT("1", Type.NUMBER), new SeparatorT("}"))));
		
		t.checkExpect(Lexer.lex(">>>"), new ArrayList<AToken>(Arrays.asList(new OperatorT(">>>"))));
		t.checkExpect(Lexer.lex("..."), new ArrayList<AToken>(Arrays.asList(new OperatorT("..."))));
		t.checkExpect(Lexer.lex("<<"), new ArrayList<AToken>(Arrays.asList(new OperatorT("<<"))));
		t.checkExpect(Lexer.lex(":"), new ArrayList<AToken>(Arrays.asList(new OperatorT(":"))));
		t.checkExpect(Lexer.lex("12:213"), new ArrayList<AToken>(Arrays.asList(new LiteralT("12", Type.NUMBER), new OperatorT(":"), new LiteralT("213", Type.NUMBER))));
		t.checkExpect(Lexer.lex("apples:21"), new ArrayList<AToken>(Arrays.asList(apples, new OperatorT(":"), new LiteralT("21", Type.NUMBER))));
		t.checkExpect(Lexer.lex("0...n"), new ArrayList<AToken>(Arrays.asList(new LiteralT("0", Type.NUMBER), new OperatorT("..."), new IdentifierT("n"))));
		t.checkExpect(Lexer.lex("0...10"), new ArrayList<AToken>(Arrays.asList(new LiteralT("0", Type.NUMBER), new OperatorT("..."), new LiteralT("10", Type.NUMBER))));
		
		t.checkExpect(Lexer.lex("[1 2 3] << orange"), new ArrayList<AToken>(
				Arrays.asList(new SeparatorT("["), new LiteralT("1", Type.NUMBER), new LiteralT("2", Type.NUMBER), new LiteralT("3", Type.NUMBER),
						new SeparatorT("]"), new OperatorT("<<"), new IdentifierT("orange"))));
		
		t.checkExpect(Lexer.lex("<(a);"), new ArrayList<AToken>(Arrays.asList(new IdentifierT("<"), new SeparatorT("("), new IdentifierT("a"), new SeparatorT(")"), semicolon)));
		t.checkExpect(Lexer.lex(">(a);"), new ArrayList<AToken>(Arrays.asList(new OperatorT(">"), new SeparatorT("("), new IdentifierT("a"), new SeparatorT(")"), semicolon)));
		
		t.checkExpect(Lexer.lex("@1 @2;"), new ArrayList<AToken>(Arrays.asList(new IdentifierT("@1"), new IdentifierT("@2"), semicolon)));
		
	}
}
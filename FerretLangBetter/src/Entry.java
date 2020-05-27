import java.util.ArrayList;
import java.util.stream.Collectors;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

// wiki: https://github.com/ra2yama/ferret-lang/wiki
// the repo also includes examples

public class Entry {
	public static void main(String[] args) throws IOException {
		
		System.out.println(args);
		
		if (args.length <= 0) {
			System.out.println("Opening REPL...");
			Entry.repl();
		} else if (args[0].equals("test")) {
			System.out.println("Starting Test Harness...\n");
			String code = "";
			
			try {
				BufferedReader reader = new BufferedReader(new FileReader(args[1]));
				code = reader.lines().collect(Collectors.joining("\n"));
				System.out.println("Found.\n");
				System.out.println(code);
				reader.close();
			} catch (Exception e) {
				System.out.println("Error loading file: " + e);
				System.out.println("Defaulting to REPL...");
				Entry.repl();
			}
			System.out.println("Testing...\n");
			
			TestHarness harness = new TestHarness(code, new Parser(Lexer.lex(args[2])).parse().eval(new Namespace())); // does this make sense to do?
			harness.runTests();
			
		} else {
			System.out.println("Trying to b file...\n");
			String code = "";
			
				try {
					BufferedReader reader = new BufferedReader(new FileReader(args[0]));
					code = reader.lines().collect(Collectors.joining("\n"));
					System.out.println("Found.\n");
					System.out.println(code);
					reader.close();
				} catch (Exception e) {
					System.out.println("Error loading file: " + e);
					System.out.println("Defaulting to REPL...");
					Entry.repl();
				}
				
				System.out.println("\nRunning program...\n"); // the main execution
				Parser p = new Parser(Lexer.lex(code));
				//System.out.println(Lexer.lex(code));
				IExpression program = p.parse();
				//System.out.println(program);
				System.out.println("=> " + program.eval(Namespace.stdlib()));
			}
	}
	
	public static void repl () throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		
		Parser p;
		Namespace global = Namespace.stdlib();
		
		boolean running = true;
		
        while (running) {
        		System.out.print(">>> ");
            String s = br.readLine();
            
            if (s.equals("quit") || s.equals("quit()") || s.equals("quit();") || s.equals("exit") || s.equals("exit()") || s.equals("exit();")) {
            		System.out.println("Leaving REPL...");
            		running = false;
            		break;
            }
            
            p = new Parser(Lexer.lex(s));
            
            try {
            		System.out.println("=> " + p.parse().eval(global));
            } catch (Exception e) {
            		System.out.println("ERROR " + e);
            }
        }
	}
}
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Entry {
	public static void main(String[] args) throws IOException {
		
		if (args.length <= 0) {
			System.out.println("Opening REPL...");
			Entry.repl();
		} else {
			System.out.println("Trying to access file...\n");
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
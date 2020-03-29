import java.util.ArrayList;

public class Entry {
	public static void main(String[] args) {
		FerretSample sample = new FerretSample();
		System.out.println(sample.getCode());
		
		ArrayList<AToken> lexed = Lexer.lex(sample.getCode());
		System.out.println(lexed);
		
		//ArrayList<IExpression> parsed = 
	}
}
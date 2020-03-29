import java.util.ArrayList;
import java.util.Map;

class Parser {
	ArrayList<AToken> input;
	int index;
	
	Parser (ArrayList<AToken> input) {
		this.input = input;
	}
	
	Object parse () {
		for ( ; index < input.size(); index++) { // is this smart?
			
		}
		
		return null;
	}
	
	AToken next () {
		return index == input.size() - 1 ? input.get(index) : input.get(index + 1);
	}
	
	AToken prev () {
		return index == 0 ? input.get(index) : input.get(index - 1);
	}

}

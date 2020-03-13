import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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

class Pointer implements IExpression { // TODO
  String key;
  Map<String, AValue> ns;

  Pointer (String key, Map<String, AValue> namespace) {
    this.key = key;
    this.ns = namespace;
  }
  
  public Object eval() {
    return null;
  }
  
}

class Function extends AValue {
  Map<String, AValue> parentNamespace;
  Map<String, AValue> localNamespace;
  Sequence body;
  
  Function (Sequence body, Map<String, AValue> namespace, ArrayList<AValue> parameters) {
    super(body);
    
    this.body = body;
    
    this.parentNamespace = namespace;
    this.localNamespace = new HashMap<String, AValue>();
  }
  
  public Object eval(ArrayList<AValue> params) {
    return body.eval(this.parentNamespace, this.localNamespace);
  }
}

class Sequence implements IExpression { //TODO
  
  ArrayList<IExpression> ListOfExpressions;
  
  Sequence (ArrayList<IExpression> LoE) {
    this.ListOfExpressions = LoE;
  }

  public Object eval(Map<String, AValue> parentNamespace, Map<String, AValue> localNamespace) {
    
    for (int i = 0; i < this.ListOfExpressions.size(); i++) {
      
      if (i == this.ListOfExpressions.size() - 1) {  
        return this.ListOfExpressions.get(i).eval();
      } else {
        this.ListOfExpressions.get(i).eval();
      }
      
    }
    
    return null;
  }

  public Object eval() {
    return null;
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

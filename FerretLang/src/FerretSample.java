class FerretSample {
	String code = String.join("\n", 
			"a 3;",
			"a;");
	
	String getCode () {
		return this.code;
	}
	
	int forSum (int[] l) {
		int x = 0;
		
        for (int i = 0; i < l.length; i++) {
        		x += l[i];
        }
        
        return x;
	}
		
}

/**
 * for-sum {
 * 	x 0;
 * 	for (@1 {x (+ x @1);});
 * 	x; 
 * }
 * 
 * print(for-sum([1 2 3 4 5]));
 * 
 * for-sum {@1 >>> +};
 * 
 * (defn for-sum [l] (reduce + l))
 * 
 * (defn for-sum [l]
 * (let [x (atom 0)]
 *   (doseq [e l] (swap! x #(+ % e))) @x))
 * 
 * 
 */
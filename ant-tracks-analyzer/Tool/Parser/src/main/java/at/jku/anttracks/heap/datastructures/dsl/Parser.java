package at.jku.anttracks.heap.datastructures.dsl;

import java.util.ArrayList;
import java.util.List;

import java.util.stream.Collectors;


public class Parser {
	public static final int _EOF = 0;
	public static final int _ident = 1;
	public static final int _wildcardIdent = 2;
	public static final int maxT = 26;

	static final boolean _T = true;
	static final boolean _x = false;
	static final int minErrDist = 2;

	public Token t;    // last recognized token
	public Token la;   // lookahead token
	int errDist = minErrDist;
	
	public Scanner scanner;
	public Errors errors;

	private final List<DSLDSPartDesc> descriptions = new ArrayList<>();

  public List<DSLDSPartDesc> getAllDataStructurePartDescriptions() { return descriptions; }
  public List<DSLDSPartDesc> getHeadDataStructurePartDescriptions() { return descriptions.stream().filter(dsp -> dsp.isHead()).collect(Collectors.toList()); }
  public List<DSLDSPartDesc> getInternalDataStructurePartDescriptions() { return descriptions.stream().filter(dsp -> dsp.isInternal()).collect(Collectors.toList()); }



	public Parser(Scanner scanner) {
		this.scanner = scanner;
		errors = new Errors();
	}

	void SynErr (int n) {
		if (errDist >= minErrDist) errors.SynErr(la.line, la.col, n);
		errDist = 0;
	}

	public void SemErr (String msg) {
		if (errDist >= minErrDist) errors.SemErr(t.line, t.col, msg);
		errDist = 0;
	}
	
	void Get () {
		for (;;) {
			t = la;
			la = scanner.Scan();
			if (la.kind <= maxT) {
				++errDist;
				break;
			}

			la = t;
		}
	}
	
	void Expect (int n) {
		if (la.kind==n) Get(); else { SynErr(n); }
	}
	
	boolean StartOf (int s) {
		return set[s][la.kind];
	}
	
	void ExpectWeak (int n, int follow) {
		if (la.kind == n) Get();
		else {
			SynErr(n);
			while (!StartOf(follow)) Get();
		}
	}
	
	boolean WeakSeparator (int n, int syFol, int repFol) {
		int kind = la.kind;
		if (kind == n) { Get(); return true; }
		else if (StartOf(repFol)) return false;
		else {
			SynErr(n);
			while (!(set[syFol][kind] || set[repFol][kind] || set[0][kind])) {
				Get();
				kind = la.kind;
			}
			return StartOf(syFol);
		}
	}
	
	void DataStructures() {
		while (StartOf(1)) {
			if (StartOf(2)) {
				Namespace(null);
			} else {
				DSLDSPartDesc partDesc = DataStructurePartDescription(null);
				descriptions.add(partDesc); 
			}
		}
	}

	void Namespace(String outerNamespace) {
		switch (la.kind) {
		case 3: {
			Get();
			break;
		}
		case 4: {
			Get();
			break;
		}
		case 5: {
			Get();
			break;
		}
		case 6: {
			Get();
			break;
		}
		case 7: {
			Get();
			break;
		}
		case 8: {
			Get();
			break;
		}
		default: SynErr(27); break;
		}
		String ns = Type();
		String namespace = (outerNamespace != null ? outerNamespace + "." : "") + ns; 
		Expect(9);
		while (StartOf(1)) {
			if (StartOf(2)) {
				Namespace(namespace);
			} else {
				DSLDSPartDesc partDesc = DataStructurePartDescription(namespace);
				descriptions.add(partDesc); 
			}
		}
		Expect(10);
	}

	DSLDSPartDesc DataStructurePartDescription(String namespace) {
		DSLDSPartDesc d;
		boolean isHead = false; 
		if (StartOf(3)) {
			switch (la.kind) {
			case 11: {
				Get();
				break;
			}
			case 12: {
				Get();
				break;
			}
			case 13: {
				Get();
				break;
			}
			case 14: {
				Get();
				break;
			}
			case 15: {
				Get();
				Expect(16);
				break;
			}
			case 17: {
				Get();
				break;
			}
			}
			isHead = true; 
		}
		String type = Type();
		type = namespace != null ? namespace + "." + type : type;
		if(type.contains("<") && !type.startsWith("java.lang.Class")) {
		 SemErr("Generics may only be used for java.lang.Class");
		}
		d = new DSLDSPartDesc(type, isHead);
		Expect(9);
		while (la.kind == 1 || la.kind == 2 || la.kind == 18) {
			boolean follow = true;
			String pointsTo = null;
			if (la.kind == 1 || la.kind == 2) {
				pointsTo = PointsToDescription();
				follow = true; 
			} else {
				Get();
				pointsTo = PointsToDescription();
				Expect(19);
				follow = false; 
			}
			d.addPointsToDescription(new DSLDSParsedReferenceInfo(pointsTo, follow));
			if(namespace != null && !pointsTo.startsWith("*")) {
			 d.addPointsToDescription(new DSLDSParsedReferenceInfo(namespace + "." + pointsTo, follow));
			}
			Expect(20);
		}
		Expect(10);
		return d;
	}

	String  Type() {
		String  name;
		name = ""; 
		Expect(1);
		name += t.val; 
		while (la.kind == 21) {
			Get();
			name += "."; 
			Expect(1);
			name += t.val; 
		}
		if (la.kind == 22) {
			Get();
			String generic = Type();
			name += "<" + generic + ">"; 
			Expect(23);
		}
		return name;
	}

	String  PointsToDescription() {
		String  name;
		name = ""; 
		if (la.kind == 2) {
			Get();
			name += t.val; 
		} else if (la.kind == 1) {
			Get();
			name += t.val; 
		} else SynErr(28);
		while (la.kind == 21) {
			Get();
			name += "."; 
			if (la.kind == 2) {
				Get();
				name += t.val; 
			} else if (la.kind == 1) {
				Get();
				name += t.val; 
			} else SynErr(29);
		}
		while (la.kind == 24) {
			Get();
			Expect(25);
			name += "[]"; 
		}
		return name;
	}



	public void Parse() {
		la = new Token();
		la.val = "";		
		Get();
		DataStructures();
		Expect(0);

	}

	private static final boolean[][] set = {
		{_T,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x},
		{_x,_T,_x,_T, _T,_T,_T,_T, _T,_x,_x,_T, _T,_T,_T,_T, _x,_T,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x},
		{_x,_x,_x,_T, _T,_T,_T,_T, _T,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x},
		{_x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_T, _T,_T,_T,_T, _x,_T,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x}

	};

	public static class Errors { // Changed by MW to allow access to error count from outside the parser
  	public int count = 0;                                    // number of errors detected
  	public java.io.PrintStream errorStream = System.out;     // error messages go to this stream
  	public String errMsgFormat = "-- line {0} col {1}: {2}"; // 0=line, 1=column, 2=text

  	protected void printMsg(int line, int column, String msg) {
  		StringBuffer b = new StringBuffer(errMsgFormat);
  		int pos = b.indexOf("{0}");
  		if (pos >= 0) { b.delete(pos, pos+3); b.insert(pos, line); }
  		pos = b.indexOf("{1}");
  		if (pos >= 0) { b.delete(pos, pos+3); b.insert(pos, column); }
  		pos = b.indexOf("{2}");
  		if (pos >= 0) b.replace(pos, pos+3, msg);
  		errorStream.println(b.toString());
  	}

  	public void SynErr (int line, int col, int n) {
  		String s;
  		switch (n) {
			case 0: s = "EOF expected"; break;
			case 1: s = "ident expected"; break;
			case 2: s = "wildcardIdent expected"; break;
			case 3: s = "\"package\" expected"; break;
			case 4: s = "\"namespace\" expected"; break;
			case 5: s = "\"Package\" expected"; break;
			case 6: s = "\"Namespace\" expected"; break;
			case 7: s = "\"ns\" expected"; break;
			case 8: s = "\"NS\" expected"; break;
			case 9: s = "\"{\" expected"; break;
			case 10: s = "\"}\" expected"; break;
			case 11: s = "\"DS\" expected"; break;
			case 12: s = "\"ds\" expected"; break;
			case 13: s = "\"Datastructure\" expected"; break;
			case 14: s = "\"DataStructure\" expected"; break;
			case 15: s = "\"datastructure\" expected"; break;
			case 16: s = "\"head\" expected"; break;
			case 17: s = "\"Head\" expected"; break;
			case 18: s = "\"(\" expected"; break;
			case 19: s = "\")\" expected"; break;
			case 20: s = "\";\" expected"; break;
			case 21: s = "\".\" expected"; break;
			case 22: s = "\"<\" expected"; break;
			case 23: s = "\">\" expected"; break;
			case 24: s = "\"[\" expected"; break;
			case 25: s = "\"]\" expected"; break;
			case 26: s = "??? expected"; break;
			case 27: s = "invalid Namespace"; break;
			case 28: s = "invalid PointsToDescription"; break;
			case 29: s = "invalid PointsToDescription"; break;
  			default: s = "error " + n; break;
  		}
  		printMsg(line, col, s);
  		count++;
  	}

  	public void SemErr (int line, int col, String s) {
  		printMsg(line, col, s);
  		count++;
  	}

  	public void SemErr (String s) {
  		errorStream.println(s);
  		count++;
  	}

  	public void Warning (int line, int col, String s) {
  		printMsg(line, col, s);
  	}

  	public void Warning (String s) {
  		errorStream.println(s);
  	}
  } // Errors

  public static class FatalError extends RuntimeException {
  	public static final long serialVersionUID = 1L;
  	public FatalError(String s) { super(s); }
  }
} // end Parser

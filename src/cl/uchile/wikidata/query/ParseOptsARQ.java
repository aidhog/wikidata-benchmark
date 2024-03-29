package cl.uchile.wikidata.query;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.TreeSet;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.Op1;
import org.apache.jena.sparql.algebra.op.Op2;
import org.apache.jena.sparql.algebra.op.OpBGP;
import org.apache.jena.sparql.algebra.op.OpLeftJoin;
import org.apache.jena.sparql.algebra.op.OpN;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.semanticweb.yars.nx.Node;

import cl.uchile.dcc.blabel.label.GraphColouring.HashCollisionException;
import cl.uchile.wikidata.query.ParseBGPsARQ.BGP;

public class ParseOptsARQ {
	static String INPUT_FOLDER = "data/queries/";
	
	static String[] INPUT = {
			INPUT_FOLDER+"I1_status500_Joined.tsv",
			INPUT_FOLDER+"I2_status500_Joined.tsv",
			INPUT_FOLDER+"I3_status500_Joined.tsv",
			INPUT_FOLDER+"I4_status500_Joined.tsv",
			INPUT_FOLDER+"I5_status500_Joined.tsv",
			INPUT_FOLDER+"I6_status500_Joined.tsv",
			INPUT_FOLDER+"I7_status500_Joined.tsv"
	};

	static String[] FILTERQ = {
			"COUNT",
			"UNION",
			"MINUS",
			"FILTER",
			"GROUP BY",
			"ORDER BY",
			"*",
			"+",
			"^",
			"DESCRIBE",
			"CONSTRUCT",
			"VALUES",
			"SERVICE",
			"REGEX",
			"ASK",
			"string",
			"> / <",
			"|",
			"search",
			"OFFSET",
			"CONCAT",
			"SUM",
			" SELECT",
			" AS ",
			"qualifier"
	};
	
	static String[] FILTERB = {
			"statement", 
			"rdf-schema#label", 
			"description", 
			"serviceParam",
			"queryHints",
			"qualifier",
			"core#altLabel",
			"rdf-schema#domain",
			"rdf-schema#range",
			"rdf-schema#subclassOf",
			"rdf-schema#subClassOf",
			"rdf-schema#subPropertyOf"
	};
	
	public static String OUTPUT = "opts-normalized.tsv";

	public static void main(String[] args) throws UnsupportedEncodingException, IOException, InterruptedException, HashCollisionException {

		int valid = 0;
		int invalid = 0;
		
		int optsfiltered = 0;
		int optskept = 0;
		
		PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(OUTPUT),StandardCharsets.UTF_8));
		TreeSet<String> optsSeen = new TreeSet<String>();
		TreeSet<String> optsSigsSeen = new TreeSet<String>();
		
		for(String input:INPUT) {
			BufferedReader br = new BufferedReader(new FileReader(input));

			String line = null;

			while((line=br.readLine())!=null) {
				String[] cols = line.trim().split("\t");
				if(cols.length==4) {
					String queryEString = cols[0];
					String queryString = URLDecoder.decode(queryEString,"UTF-8").replaceAll("\n", " ");

					try {
						Query query = QueryFactory.create(queryString);
						valid++;
						
						Op op = Algebra.compile(query);
						
						ArrayList<OpLeftJoin> opts = getOpts(op);
						
						TreeSet<String> opTypes = getOpTypes(op);
						
						for(OpLeftJoin opt: opts) {
							String sopt = opt.toString();
							boolean filter = false;
							if(optsSeen.add(opt.toString())) {
								for(String fb:FILTERB) {
									if(sopt.toString().contains(fb)) {
										filter = true;
									}
								}
							} else {
								filter = true;
							}
							
							Opt o = new Opt(opt);
							if(o.hasCartesianProducts() || !optsSigsSeen.add(o.getSignatureVarId())) {
								filter = true;
							}
							
							if(!filter) {
								pw.println(o.toString()+"\t"+queryString+"\t"+opTypes+"\t"+cols[2]);
								optskept ++;
							} else {
								optsfiltered ++;
							}
						}
					} catch(QueryParseException e) {
						invalid++;
					} catch(ExprEvalException e) {
						invalid++;
					}
					
					System.out.print(".");
					if((valid+invalid)%100==0) {
						System.out.println();
					}
				}
			}
			
			br.close();
		}
		
		pw.close();
		
		System.out.println();
		System.out.println("Parsed queries "+(valid+invalid));
		System.out.println("Valid queries "+valid);
		System.out.println("Invalid queries "+invalid);
		System.out.println("Total Optionals "+(optskept+optsfiltered));
		System.out.println("Kept Optionals "+optskept);
		System.out.println("Filtered Optionals "+optsfiltered);
	}
	
	public static ArrayList<OpLeftJoin> getOpts(Op op){
		ArrayList<OpLeftJoin> opts = new ArrayList<OpLeftJoin>();
		getOpts(op,opts);
		return opts;
	}
	
	public static void getOpts(Op op, ArrayList<OpLeftJoin> opts){
		if(op instanceof OpBGP) {
			return;
		} else if(op instanceof Op1) {
			getOpts(((Op1)op).getSubOp(),opts);
		} else if(op instanceof Op2) {
			if(op instanceof OpLeftJoin && checkChildrenOptOrBgp(op)) {
				opts.add((OpLeftJoin) op);
			} else {
				getOpts(((Op2)op).getLeft(),opts);
				getOpts(((Op2)op).getRight(),opts);
			}
		} else if(op instanceof OpN) {
			OpN opn = (OpN) op;
			for(Op sop:opn.getElements()) {
				getOpts(sop,opts);
			}
		}
	}
	
	public static boolean checkChildrenOptOrBgp(Op op){
		if(op instanceof OpBGP) {
			return true;
		} else if(op instanceof Op1) {
			return false;
		} else if(op instanceof Op2) {
			if(op instanceof OpLeftJoin) {
				return checkChildrenOptOrBgp(((Op2)op).getLeft()) && checkChildrenOptOrBgp(((Op2)op).getRight());
			}
		} else if(op instanceof OpN) {
			return false;
		}
		return false;
	}
	
	public static TreeSet<String> getOpTypes(Op op){
		TreeSet<String> opTypes = new TreeSet<String>();
		getOpTypes(op,opTypes);
		return opTypes;
	}
	
	public static void getOpTypes(Op op, TreeSet<String> opTypes){
		opTypes.add(op.getClass().getSimpleName());
		if(op instanceof Op1) {
			getOpTypes(((Op1)op).getSubOp(),opTypes);
		} else if(op instanceof Op2) {
			getOpTypes(((Op2)op).getLeft(),opTypes);
			getOpTypes(((Op2)op).getRight(),opTypes);
		} else if(op instanceof OpN) {
			OpN opn = (OpN) op;
			for(Op sop:opn.getElements()) {
				getOpTypes(sop,opTypes);
			}
		}
	}
	
	public static class Opt {
		OpLeftJoin opt;

		Opt oleft = null;
		Opt oright = null;
		
		BGP bleft = null;
		BGP bright = null;
		
		public Opt(OpLeftJoin opt) throws InterruptedException, HashCollisionException {
			this.opt = opt;
			if(opt.getLeft() instanceof OpBGP) {
				bleft = new BGP((OpBGP) opt.getLeft());
			} else if(opt.getLeft() instanceof OpLeftJoin) {
				oleft = new Opt((OpLeftJoin) opt.getLeft());
			}
			
			if(opt.getRight() instanceof OpBGP) {
				bright = new BGP((OpBGP) opt.getRight());
			} else if(opt.getRight() instanceof OpLeftJoin) {
				oright = new Opt((OpLeftJoin) opt.getRight());
			}
		}
		
		public TreeSet<Node> getVars() {
			TreeSet<Node> vars = new TreeSet<Node>();
			if(bleft!=null) {
				vars.addAll(bleft.getVars());
			} else if(oleft!=null) {
				vars.addAll(oleft.getVars());
			}
			
			if(bright!=null) {
				vars.addAll(bright.getVars());
			} else if(oright!=null) {
				vars.addAll(oright.getVars());
			}
			
			return vars;
		}
		
		public boolean isWellDesigned() {
			TreeSet<Node> outerVars = new TreeSet<Node>();
			return isWellDesigned(this, outerVars);
		}
		
		private static boolean isWellDesigned(Opt op, TreeSet<Node> outerVars) {
			TreeSet<Node> lvars = null;
			TreeSet<Node> rvars = null;
			
			if(op.bright!=null) {
				rvars = op.bright.getVars();
			} else {
				System.out.println("nested opt");
				rvars = op.oright.getVars();
			}
			
			if(op.bleft!=null) {
				lvars = op.bleft.getVars();
			} else {
				lvars = op.oleft.getVars();
			}
			
			for(Node v : rvars) {
				if(outerVars.contains(v)) {
					if(!lvars.contains(v)) {
						return false;
					}
				}
			}
			
			if(op.oleft!=null) {
				TreeSet<Node> newOuterVars = new TreeSet<Node>();
				newOuterVars.addAll(outerVars);
				newOuterVars.addAll(rvars);
				if(!isWellDesigned(op.oleft, newOuterVars)) {
					return false;
				}
			}
			
			if(op.oright!=null) {
				TreeSet<Node> newOuterVars = new TreeSet<Node>();
				newOuterVars.addAll(outerVars);
				newOuterVars.addAll(lvars);
				if(!isWellDesigned(op.oright, newOuterVars)) {
					return false;
				}
			}
			
			return true;
		}
		
		public boolean hasCartesianProducts() {
			TreeSet<Node> lvars = null;
			TreeSet<Node> rvars = null;
			
			if(bleft!=null) {
				if(bleft.clr.getPartitionCount()>1)
					return true;
				lvars = bleft.getVars();
			} else if(oleft!=null) {
				if(oleft.hasCartesianProducts())
					return true;
				lvars = oleft.getVars();
			}
			
			if(bright!=null) {
				if(bright.clr.getPartitionCount()>1)
					return true;
				rvars = bright.getVars();
			} else if(oright!=null) {
				if(oright.hasCartesianProducts())
					return true;
				rvars = oright.getVars();
			}
			
			boolean cartesian = true;
			for(Node left:lvars) {
				if(rvars.contains(left)) {
					cartesian = false;
				}
			}
			
			if(cartesian) {
				System.out.println(toString());
			}
			
			return cartesian;
		}
		
		public String getSignature() {
			String hash = "";
			
			if(bleft!=null) {
				hash+=bleft.getSignature();
			} else if(oleft!=null) {
				hash+=oleft.getSignature();
			}
			
			hash += "OPT";
			
			if(bright!=null) {
				hash+=bright.getSignature();
			} else if(oright!=null) {
				hash+=oright.getSignature();
			}
			
			return hash;
		}
		
		public String getSignatureVarId() {
			String hash = "";
			
			if(bleft!=null) {
				hash+=bleft.getSignatureVarId();
			} else if(oleft!=null) {
				hash+=oleft.getSignatureVarId();
			}
			
			hash += "OPT";
			
			if(bright!=null) {
				hash+=bright.getSignatureVarId();
			} else if(oright!=null) {
				hash+=oright.getSignatureVarId();
			}
			
			return hash;
		}
		
		public String toString() {
			String left = null;
			String right = null;
			if(bleft!=null) {
				left = bleft.toNonCanonicalisedString();
			} else if(oleft!=null) {
				left = oleft.toString();
			}
			
			if(bright!=null) {
				right = bright.toNonCanonicalisedString();
			} else if(oright!=null) {
				right = oright.toString();
			}
			
			return "{ "+left+" } OPTIONAL { "+right+" }";
		}
		
	}
	
}

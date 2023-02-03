package cl.uchile.wikidata.query;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;

import org.apache.jena.graph.Node_Literal;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.Transformer;
import org.apache.jena.sparql.algebra.op.Op1;
import org.apache.jena.sparql.algebra.op.Op2;
import org.apache.jena.sparql.algebra.op.OpBGP;
import org.apache.jena.sparql.algebra.op.OpN;
import org.apache.jena.sparql.expr.ExprEvalException;

import cl.uchile.dcc.blabel.label.GraphColouring.HashCollisionException;

public class ParseAndFindLiterals {
	static String INPUT_FOLDER = "C:\\Users\\aidhog\\Documents\\Research\\data\\wikidata_log\\";
	
	static String[] INPUT = {
			INPUT_FOLDER+"2017-06-12_2017-07-09_status_500.tsv.gz",
			INPUT_FOLDER+"2017-07-10_2017-08-06_status_500.tsv.gz",
			INPUT_FOLDER+"2017-08-07_2017-09-03_status_500.tsv.gz",
			INPUT_FOLDER+"2017-12-03_2017-12-30_status_500.tsv.gz",
			INPUT_FOLDER+"2018-01-01_2018-01-28_status_500.tsv.gz",
			INPUT_FOLDER+"2018-01-29_2018-02-25_status_500.tsv.gz",
			INPUT_FOLDER+"2018-02-26_2018-03-25_status_500.tsv.gz"
	};
	
	static String PROPERTIES = "data/num-props.txt";

	static String[] FILTERQ = {
//			"OPTIONAL",
//			"COUNT",
//			"UNION",
//			"MINUS",
//			"FILTER",
//			"GROUP BY",
//			"ORDER BY",
//			"*",
//			"+",
//			"^",
//			"DESCRIBE",
//			"CONSTRUCT",
//			"VALUES",
//			"SERVICE",
//			"REGEX",
//			"ASK",
//			"string",
//			"> / <",
//			"|",
//			"search",
//			"OFFSET",
//			"CONCAT",
//			"SUM",
//			" SELECT",
//			" AS ",
//			"qualifier"
	};
	
	static String[] FILTERB = {
//			"statement", 
//			"rdf-schema#label", 
//			"description", 
//			"serviceParam",
//			"queryHints",
//			"qualifier",
//			"core#altLabel",
//			"rdf-schema#domain",
//			"rdf-schema#range",
//			"rdf-schema#subclassOf",
//			"rdf-schema#subClassOf",
//			"rdf-schema#subPropertyOf"
	};
	
//	public static String OUTPUT = "num-queries-v2.tsv";

	public static void main(String[] args) throws UnsupportedEncodingException, IOException, InterruptedException, HashCollisionException {

		int valid = 0;
		int invalid = 0;
		
		int filtered = 0;
		
		int lit = 0;
		int nolitserv = 0;
		int nolit = 0;
		
		TreeSet<String> queriesSeen = new TreeSet<String>();
		
		for(String input:INPUT) {
			System.out.print("\nProcessing "+input+"\n");
			InputStream is = new FileInputStream(input);
			if(input.endsWith(".gz")) {
				is = new GZIPInputStream(is);
			}
			BufferedReader br = new BufferedReader(new InputStreamReader(is));

			String line = null;
			


			while((line=br.readLine())!=null) {
				String[] cols = line.trim().split("\t");
				if(cols.length==4) {
					String queryEString = cols[0];
					String queryString = URLDecoder.decode(queryEString,"UTF-8").replaceAll("\n", " ");

					if(queriesSeen.add(queryString)) {
						try {
							Query query = QueryFactory.create(queryString);
							valid++;
							
							Op op = Algebra.compile(query);
							
							TreeSet<String> lits = getLiterals(op);
							
							if(!lits.isEmpty()) {
								lit ++;
								
								System.out.println("lit\t"+queryString);
								
								if(queryString.toLowerCase().contains("service")) {
									try {
										Op opNoService = Transformer.transform(new RemoveServiceClauses(), op);
										
										opNoService = RemoveServiceClauses.transformTopLevel(opNoService);
										
										lits = getLiterals(opNoService);
										
										if(lits.isEmpty()) {
											nolitserv++;
//											System.out.println("\tnolitserv\t"+queryString);
										}
									} catch(Exception e) {
										System.err.println(queryString);
										throw e;
									}
								}
							} else {
//								System.out.println("nolit\t"+queryString);
								nolit ++;
							}
							
						} catch(QueryParseException e) {
							invalid++;
						} catch(ExprEvalException e) {
							invalid++;
						}
						
						
						if((valid+invalid)%100==0) {
							System.out.print(".");
							if((valid+invalid)%5000==0) {
								System.out.println();
							}
						}
					} else {
						filtered ++;
					}
				}
			}
			
			br.close();
			
			System.out.println(" done.");
		}
		
		System.out.println();
		System.out.println("Parsed queries "+(valid+invalid));
		System.out.println("Valid queries "+valid);
		System.out.println("Invalid queries "+invalid);
		System.out.println("Total literal queries "+lit);
		System.out.println("Total no literal queries "+nolit);
		System.out.println("Total literal queries only service "+nolitserv);
		System.out.println("Filtered queries "+filtered);
	}
	
	private static TreeSet<String> getLiterals(Op op) {
		TreeSet<String> literals = new TreeSet<String>();
		getLiterals(op,literals);
		return literals;
	}

	private static void getLiterals(Op op, TreeSet<String> literals) {
		if(op instanceof OpBGP) {
			OpBGP bgp = (OpBGP)op;
			List<Triple> tps = bgp.getPattern().getList();
			for(Triple tp:tps) {
//				System.out.println(tp.getPredicate().toString()+"\t"+properties.contains(tp.getPredicate().toString())+"\t"+tp.getObject().isVariable());
				if(tp.getObject() instanceof Node_Literal && tp.getObject().toString().substring(1).startsWith("string")) {
					literals.add(tp.getObject().toString());
				}
			}
		} else if(op instanceof Op1) {
			getLiterals(((Op1)op).getSubOp(),literals);
		} else if(op instanceof Op2) {
			getLiterals(((Op2)op).getLeft(),literals);
			getLiterals(((Op2)op).getRight(),literals);
		} else if(op instanceof OpN) {
			OpN opn = (OpN) op;
			for(Op sop:opn.getElements()) {
				getLiterals(sop,literals);
			}
		}
	}
}

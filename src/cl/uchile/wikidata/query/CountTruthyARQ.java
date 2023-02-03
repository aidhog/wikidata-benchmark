package cl.uchile.wikidata.query;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.expr.ExprEvalException;

import cl.uchile.dcc.blabel.label.GraphColouring.HashCollisionException;

public class CountTruthyARQ {
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
	
	static String[] FILTERB = {
			"statement", 
//			"rdf-schema#label", 
//			"description", 
//			"serviceParam",
//			"queryHints",
			"qualifier",
			"value",
			"reference"
//			"core#altLabel",
//			"rdf-schema#domain",
//			"rdf-schema#range",
//			"rdf-schema#subclassOf",
//			"rdf-schema#subClassOf",
//			"rdf-schema#subPropertyOf"
	};
	
//	public static String OUTPUT = "bgps-normalized.tsv";

	public static void main(String[] args) throws UnsupportedEncodingException, IOException, InterruptedException, HashCollisionException {

		int valid = 0;
		int invalid = 0;
		
		int filtered = 0;
		
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
						String norm = op.toString();
						
						for(String fb:FILTERB) {
							if(norm.toString().contains(fb)) {
								filtered ++;
								break;
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
		
		System.out.println();
		System.out.println("Parsed queries "+(valid+invalid));
		System.out.println("Valid queries "+valid);
		System.out.println("Invalid queries "+invalid);
		System.out.println("Valid but using qualifiers "+filtered);
	}
}

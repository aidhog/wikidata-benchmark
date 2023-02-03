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

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.OpLeftJoin;
import org.apache.jena.sparql.expr.ExprEvalException;

import cl.uchile.dcc.blabel.label.GraphColouring.HashCollisionException;
import cl.uchile.wikidata.query.ParseOptsARQ.Opt;

public class ClassifyOptsARQ {
	static String INPUT_FILE = "data/opts.txt";
	static String OUTPUT_FILE = "data/opts-c.txt";
	
//	static String INPUT_FILE = "data/test.txt";
//	static String OUTPUT_FILE = "data/test-c.txt";

	public static void main(String[] args) throws UnsupportedEncodingException, IOException, InterruptedException, HashCollisionException {

		PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(OUTPUT_FILE),StandardCharsets.UTF_8));

		BufferedReader br = new BufferedReader(new FileReader(INPUT_FILE));

		String line = null;
		
		int l = 0;

		while((line=br.readLine())!=null) {
			line = line.trim();
			l++;
			if(!line.isBlank()) {
				System.out.print(l+"\t");
				String queryEString = "SELECT * WHERE { "+line+ "}";
				String queryString = URLDecoder.decode(queryEString,"UTF-8").replaceAll("\n", " ");

				try {
					Query query = QueryFactory.create(queryString);

					Op op = Algebra.compile(query);

//					System.out.print(op);

					ArrayList<OpLeftJoin> opts = ParseOptsARQ.getOpts(op);

					if(opts.size()>1) {
						throw new IllegalArgumentException("Query with multiple OPs: "+line);
					}

					OpLeftJoin opt = opts.get(0);
					String wd = "W";

					Opt o = new Opt(opt);
					if(!o.isWellDesigned()) {
						wd = "NW";
					}
					
					String c = "C";
					if(o.hasCartesianProducts()) {
						c = "NC";
					}

//					System.out.println("->"+wd+" "+c);
					pw.println(line+"\t"+wd+"\t"+c);
				} catch(QueryParseException e) {
					br.close();
					pw.close();
					throw e;
				} catch(ExprEvalException e) {
					br.close();
					pw.close();
					throw e;
				}
			}
		}

		br.close();
		pw.close();
	}
}

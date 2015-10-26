package geoNLP;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.base.Joiner;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

public class GeoNLP {
	
	private static String readFile(String path, Charset encoding) throws IOException
	{
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return new String(encoded, encoding);
	}

	private static ArrayList<String> getSearchTerms(String keyword, String lang, String configFileName) {
		final ArrayList<String> searchTerms = new ArrayList<String>();
		try {
			JSONObject obj = new JSONObject(readFile(configFileName, StandardCharsets.UTF_8));
			obj = obj.getJSONObject("keywords");
			obj = obj.getJSONObject(keyword);
			final JSONArray keywords = obj.getJSONArray(lang);
			for (int i = 0; i < keywords.length(); i++) {
            	searchTerms.add(keywords.getString(i));
            }
		} catch (JSONException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return searchTerms;
	}
	
	private static String getSentenceText(CoreMap sentence) {
		final ArrayList<String> words = new ArrayList<String>();
	    for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
	        final String word = token.get(TextAnnotation.class).toLowerCase();
	        words.add(word);
	    }
	    return Joiner.on(" ").join(words);
	}
	
	private static Integer getDistance(String sentence, ArrayList<String> searchTerms, String location) {
		Integer minDist = Integer.MAX_VALUE;
		Integer locDist = sentence.indexOf(location);
		for (String term: searchTerms) {
			Integer termDist = sentence.indexOf(term);
			if ((termDist >= 0) && (locDist >= 0)) {
				Integer dist = Math.abs(locDist - termDist);
				if (dist < minDist) {
					minDist = dist;
				}
			}
		}
		return minDist;
	}

	private static String getLocation(List<CoreMap> sentences) {
	    final ArrayList<String> searchTerms = getSearchTerms("landslide", "en", "config.js");

	    // variables to maintain location with the minimum
	    // distance to one of the search terms
	    String minLoc = "";
	    Integer minDist = Integer.MAX_VALUE;

	    // analyze each sentence
	    for (CoreMap sentence: sentences) {
	    	String location = "";
		    final String sent = getSentenceText(sentence);
	    	// traverse the words in the current sentence
		    for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
				// this is the NER label of the token
				final String ne = token.get(NamedEntityTagAnnotation.class);
		        if (ne.equals("LOCATION")) {
			    	// this is the text of the token
			        final String word = token.get(TextAnnotation.class).toLowerCase();
			        // a single location can consist of multiple words
			        if (location.length() > 0)
			        	location += " ";
			        location += word;
		        } else {
		        	if (location.length() > 0) {
		        		Integer dist = getDistance(sent, searchTerms, location);
		        		if (dist < minDist) {
			        		minDist = dist;
			        		minLoc = location;
		        		}
		        		location = "";
		        	}
		        }
		    }
		    // this is a case when a location is the last word in the sentence
		    if (location.length() > 0) {
        		Integer dist = getDistance(sent, searchTerms, location);
        		if (dist < minDist) {
	        		minDist = dist;
	        		minLoc = location;
        		}
		    }
	    }
	    return minLoc;
	}
	
	private static void saveLocation(PrintWriter writer, String item_id, String location) {
    	try {
		    // update the element with location
    		writer.println(item_id+"\t"+location);
    	} catch (Exception e) {
	    	System.out.println("nlp failed to write locations");
    	}
	}

	private static void processElem(StanfordCoreNLP pipeline, String item_id, String text, PrintWriter writer) {
		// annotate text
	    final Annotation document = new Annotation(text);
	    pipeline.annotate(document);
	    // retrieve sentences
	    final List<CoreMap> sentences = document.get(SentencesAnnotation.class);
	    // collect location
	    final String location = getLocation(sentences);
	    // save location to an output file
	    saveLocation(writer, item_id, location);
	}

	private static void traverse(String fIn, String fOut, StanfordCoreNLP pipeline) {
		try (PrintWriter writer = new PrintWriter(fOut, "UTF-8")) {
			try (BufferedReader br = new BufferedReader(new FileReader(fIn))) {
				String line;
				while ((line = br.readLine()) != null) {
					// each line is a JSON formatted string
					final JSONObject obj = new JSONObject(line);
					final String stream_type = obj.getString("stream_type");
					String item_id = null;
					String text = null;
					if (stream_type.equals("Twitter")) {
						item_id = obj.getString("id_str");
						text = obj.getString("text");
					}
					processElem(pipeline, item_id, text, writer);
				}
			} catch (JSONException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		final long t0 = System.nanoTime();
		
		// set up NLP pipeline
		final Properties props = new Properties();
	    props.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse");
	    final StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

	    // traverse the files
	    traverse("month_analyze_2014_1.txt", "month_analyze_2014_1_nlp.txt", pipeline);

	    // print execution time
		final long duration = (System.nanoTime() - t0);
		System.out.println("milliseconds = " + Long.toString(duration / 1000 / 1000));
	}
}


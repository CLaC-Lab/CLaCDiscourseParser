package org.cleartk.corpus.conll2015;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.corpus.conll2015.type.ConllToken;
import org.cleartk.discourse.type.DiscourseArgument;
import org.cleartk.discourse.type.DiscourseConnective;
import org.cleartk.discourse.type.DiscourseRelation;
import org.cleartk.discourse.type.TokenList;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.json.JsonHierarchicalStreamDriver;

import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;

class ConllGoldTokenList{

	public ConllGoldTokenList(TokenList tokenList) {
		if (tokenList == null)
			return ;
		
		RawText = TokenListTools.getTokenListText(tokenList);
		List<int[]> spanLists = new ArrayList<>();
		List<int[]> tokenLists = new ArrayList<>();
		
		List<Token> tokens = TokenListTools.convertToTokens(tokenList);
		tokenLists = tokens.stream().map((t) -> {
			ConllToken token = (ConllToken)t;
			return new int[]{token.getBegin(), token.getEnd(), token.getDocumentOffset(), token.getSentenceOffset(), token.getOffsetInSentence()};
		}).collect(Collectors.toList());
		

		int start = -1;
		int end = -1;
		for (Token token: tokens){
			if (start == -1){
				start = token.getBegin();
				end = token.getEnd();
			} else if (token.getBegin() <= end + 1){
				end = token.getEnd();
			} else {
				spanLists.add(new int[]{start, end});
				start = token.getBegin();
				end = token.getEnd();
			}
		}
		spanLists.add(new int[]{start, end});
		
		
		TokenList = tokenLists.toArray(new int[tokenLists.size()][]);
		CharacterSpanList = spanLists.toArray(new int[spanLists.size()][]);
		
	}

	int[][] CharacterSpanList;
	String RawText;
	int[][] TokenList;
	
}

class ConllGoldDiscourseRelation{
	String DocID;
	String Type;
	String[] Sense = new String[1];
	String discourseConnectiveText;
	ConllGoldTokenList Arg1;
	ConllGoldTokenList Arg2;
	ConllGoldTokenList Connective;
	
}

public class ConllJSonGoldExporter extends JCasAnnotator_ImplBase{
	public static final String PARAM_JSON_OUT_FILE = "PARAM_JSON_OUT_FILE";
	public static final String JSON_OUT_FILE_DESCRIPTION = "Specify the json output file.";

	@ConfigurationParameter(
			name = PARAM_JSON_OUT_FILE,
			description = JSON_OUT_FILE_DESCRIPTION,
			mandatory = true)
	private String jsonOutFilePath;

	private PrintWriter jsonFile;

	private XStream xstream = new XStream(new JsonHierarchicalStreamDriver());
	public static AnalysisEngineDescription getDescription(File jsonOuFilePath) throws ResourceInitializationException {
		return AnalysisEngineFactory.createEngineDescription(
				ConllJSonGoldExporter.class,
				PARAM_JSON_OUT_FILE,
				jsonOuFilePath);
	}

	public void initialize(UimaContext context)
			throws ResourceInitializationException {
		super.initialize(context);
		
		xstream.setMode(XStream.NO_REFERENCES);
        xstream.alias("", ConllGoldDiscourseRelation.class);
		try {
			File directory = new File(jsonOutFilePath).getParentFile();
			if (!directory.exists())
				directory.mkdirs();
			jsonFile = new PrintWriter(jsonOutFilePath);
		} catch (FileNotFoundException e) {
			throw new ResourceInitializationException(e); 
		}
	}
	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		for (DiscourseRelation discourseRelation: JCasUtil.select(aJCas, DiscourseRelation.class)){
			String line = convertToJSon(discourseRelation, aJCas);
			jsonFile.println(line);
			jsonFile.flush();
		}
	}
	
	private String convertToJSon(DiscourseRelation discourseRelation, JCas aJCas) throws AnalysisEngineProcessException {
		ConllGoldDiscourseRelation conllDiscourseRelation = new ConllGoldDiscourseRelation();
		conllDiscourseRelation.DocID = Tools.getDocName(aJCas);

		DiscourseConnective discourseConnective = discourseRelation.getDiscourseConnective();
		
		conllDiscourseRelation.Connective = new ConllGoldTokenList(discourseConnective);

		DiscourseArgument arg1 = discourseRelation.getArguments(0);
		conllDiscourseRelation.Arg1 = new ConllGoldTokenList(arg1);
		DiscourseArgument arg2 = discourseRelation.getArguments(1);
		conllDiscourseRelation.Arg2 = new ConllGoldTokenList(arg2);

		conllDiscourseRelation.Sense[0] = discourseRelation.getSense();
		conllDiscourseRelation.Type = discourseRelation.getRelationType();

		String jsonVal = xstream.toXML(conllDiscourseRelation).replace("\n", "").replaceAll(" +", " ");
		jsonVal = jsonVal.substring("{\"\": ".length(), jsonVal.length() - 1);
		return jsonVal;
	}

	
	
	@Override
	public void collectionProcessComplete()
			throws AnalysisEngineProcessException {
		super.collectionProcessComplete();
		jsonFile.close();
		System.out.println("ConllJSONExporter.collectionProcessComplete()");
	}
	
}

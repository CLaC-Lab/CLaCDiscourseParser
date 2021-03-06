package ca.concordia.clac.discourse.parser.dc.disambiguation;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.ml.jar.JarClassifierBuilder;

import com.lexicalscope.jewel.cli.CliFactory;
import com.lexicalscope.jewel.cli.Option;

import ca.concordia.clac.uima.engines.XMLGenerator;
import de.tudarmstadt.ukp.dkpro.core.berkeleyparser.BerkeleyParser;
import de.tudarmstadt.ukp.dkpro.core.io.text.TextReader;
import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiWriter;
import de.tudarmstadt.ukp.dkpro.core.opennlp.OpenNlpPosTagger;
import de.tudarmstadt.ukp.dkpro.core.opennlp.OpenNlpSegmenter;

public class DiscourseConnectiveDisambiguator {
	public static URL DEFAULT_URL = null;
	public static String DEFAULT_BERKELEY_MODEL_FILE = "eng_sm5.gr";

	private URL model;
	private URL dcList;
	private URL discourseVsNonDiscoursePackage;
	private URL senseLabelerPackage;

	public DiscourseConnectiveDisambiguator() throws MalformedURLException {
		this(DEFAULT_URL, DEFAULT_BERKELEY_MODEL_FILE);
	}

	public DiscourseConnectiveDisambiguator(File packageDir) throws MalformedURLException {
		this(packageDir.toURI().toURL(), DEFAULT_BERKELEY_MODEL_FILE);
	}

	public DiscourseConnectiveDisambiguator(URL packageDir, String berkeleyModelFile) throws MalformedURLException {
		if (packageDir == null){
			model = ClassLoader.getSystemClassLoader().getResource("clacParser/model/" + berkeleyModelFile);
			dcList = ClassLoader.getSystemClassLoader().getResource("clacParser/model/" + DiscourseVsNonDiscourseClassifier.DC_HEAD_LIST_FILE);
			discourseVsNonDiscoursePackage =  ClassLoader.getSystemClassLoader().getResource("clacParser/model/" + DiscourseVsNonDiscourseClassifier.PACKAGE_DIR);
			senseLabelerPackage = ClassLoader.getSystemClassLoader().getResource("clacParser/model/" + DiscourseSenseLabeler.PACKAGE_DIR);

		} else {
			model = new URL(packageDir, berkeleyModelFile);
			dcList = new URL(packageDir, DiscourseVsNonDiscourseClassifier.DC_HEAD_LIST_FILE);
			discourseVsNonDiscoursePackage =  new URL(packageDir, DiscourseVsNonDiscourseClassifier.PACKAGE_DIR);
			senseLabelerPackage = new URL(packageDir, DiscourseSenseLabeler.PACKAGE_DIR);
		}
	}
	
	private AnalysisEngineDescription getTokenizer() throws ResourceInitializationException{
		return AnalysisEngineFactory.createEngineDescription(OpenNlpSegmenter.class);
	}
	
	private AnalysisEngineDescription getPosTagger() throws ResourceInitializationException{
		return AnalysisEngineFactory.createEngineDescription(OpenNlpPosTagger.class);
	}
	
	private AnalysisEngineDescription getSyntacticParser() throws ResourceInitializationException{
		return AnalysisEngineFactory.createEngineDescription(BerkeleyParser.class, 
				BerkeleyParser.PARAM_MODEL_LOCATION, 
				model.toString());
	}
	
	private AnalysisEngineDescription getWriter(File dir) throws ResourceInitializationException{
		return AnalysisEngineFactory.createEngineDescription(XmiWriter.class
				, XmiWriter.PARAM_TARGET_LOCATION, dir.getAbsolutePath()
				, XmiWriter.PARAM_OVERWRITE, true);
	}
	
	public void parse(File dir, File output) throws ResourceInitializationException, UIMAException, IOException, URISyntaxException{
		System.out.println("DiscourseConnectiveDisambiguator.parse(): parsing " + dir.getAbsolutePath() + "...");
		CollectionReaderDescription reader = CollectionReaderFactory.createReaderDescription(TextReader.class, 
				TextReader.PARAM_SOURCE_LOCATION, dir, 
				TextReader.PARAM_LANGUAGE, "en",
				TextReader.PARAM_PATTERNS, "*");
		
		SimplePipeline.runPipeline(reader,
				getTokenizer(), 
				getPosTagger(), 
				getSyntacticParser(),
				getParser(CAS.NAME_DEFAULT_SOFA), 
				getWriter(output),
				XMLGenerator.getDescription(output, "", true, "DiscourseConnective")
						);
	}
	
	public AnalysisEngineDescription getParser(String viewName) throws ResourceInitializationException, MalformedURLException, URISyntaxException{
		AggregateBuilder parser = new AggregateBuilder();
		parser.add(DiscourseVsNonDiscourseClassifier.getClassifierDescription(dcList, discourseVsNonDiscoursePackage), 
				CAS.NAME_DEFAULT_SOFA, viewName);
		parser.add(getSenseLabeler(), 
				CAS.NAME_DEFAULT_SOFA, viewName);
		
		return parser.createAggregateDescription();
	}
	
	public AnalysisEngineDescription getSenseLabeler() throws ResourceInitializationException, MalformedURLException{
		return DiscourseSenseLabeler.getClassifierDescription(senseLabelerPackage);
	}
	
	public void parseSubdirectory(File dir, File output) throws ResourceInitializationException, UIMAException, IOException, URISyntaxException{
		for (File d: dir.listFiles()){
			if (d.isDirectory())
				parse(d, new File(output, d.getName()));
		}
		parse(dir, output);
	}
	
	public void train(String configs) throws Exception{
		File[] packageDirs = new File[]{
				new File(discourseVsNonDiscoursePackage.getFile()), 
				new File(senseLabelerPackage.getFile())};
		
		for (File packageDir: packageDirs){			 
			JarClassifierBuilder.trainAndPackage(packageDir, configs);
		}
	}
	
	public interface Options{
		@Option(
				defaultToNull = true,
				shortName = "i",
				longName = "inputDataset", 
				description = "Specify the input directory")
		public String getInputDataset();
		
		@Option(
				shortName = "m",
				longName = "model", 
				description = "Specify the model directory")
		public String getModel();
		
		@Option(
				defaultToNull = true,
				shortName = "o",
				longName = "outputDir",
				description = "Specify the output directory to stores extracted texts")
		public String getOutputDir();

		@Option(
				defaultToNull = true,
				shortName = "c",
				longName = "The configuration for the classifier",
				description = "Specify the configuration for the classifier (e.g. Weka Classifier)")
		public String getConfig();

//		"weka.classifiers.trees.RandomForest", "-P 100 -I 100 -num-slots 10 -K 0 -M 1.0 -V 0.001 -S 1"		
	}
	public static void main(String[] args) throws Exception {
		Options options = CliFactory.parseArguments(Options.class, args);
		
		DiscourseConnectiveDisambiguator disambiguator = new DiscourseConnectiveDisambiguator(new File(options.getModel()));
		if (options.getInputDataset() == null){
			System.out.println("DiscourseConnectiveDisambiguator.main(): Start training...");
	
			disambiguator.train(options.getConfig());
			System.out.println("DiscourseConnectiveDisambiguator.main(): Done!");
		} else {
			System.out.println("DiscourseConnectiveDisambiguator.main(): Start parsing...");
			disambiguator.parse(new File(options.getInputDataset()), new File(options.getOutputDir()));
		}
	}
}

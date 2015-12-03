package org.parser.dc.disambiguation;

import java.io.File;
import java.io.IOException;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.ml.jar.JarClassifierBuilder;

import com.lexicalscope.jewel.cli.CliFactory;
import com.lexicalscope.jewel.cli.Option;

import de.tudarmstadt.ukp.dkpro.core.berkeleyparser.BerkeleyParser;
import de.tudarmstadt.ukp.dkpro.core.io.text.TextReader;
import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiWriter;
import de.tudarmstadt.ukp.dkpro.core.opennlp.OpenNlpPosTagger;
import de.tudarmstadt.ukp.dkpro.core.opennlp.OpenNlpSegmenter;

public class DiscourseConnectiveDisambiguator {

	private File model;
	private File dcList;
	private File discourseVsNonDiscoursePackage;
	private File senseLabelerPackage;
	
	public DiscourseConnectiveDisambiguator(File packageDir) {
		model = new File(packageDir, "eng_sm5.gr");
		dcList = new File(packageDir, DiscourseVsNonDiscourseClassifier.DC_HEAD_LIST_FILE);
		discourseVsNonDiscoursePackage = new File(packageDir, DiscourseVsNonDiscourseClassifier.PACKAGE_DIR);
		senseLabelerPackage = new File(packageDir, DiscourseSenseLabeler.PACKAGE_DIR);
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
				model.getAbsolutePath());
	}
	
	private AnalysisEngineDescription getWriter(File dir) throws ResourceInitializationException{
		return AnalysisEngineFactory.createEngineDescription(XmiWriter.class
				, XmiWriter.PARAM_TARGET_LOCATION, dir.getAbsolutePath());
	}
	
	public void parse(File dir, File output) throws ResourceInitializationException, UIMAException, IOException{
		CollectionReaderDescription reader = CollectionReaderFactory.createReaderDescription(TextReader.class, 
				TextReader.PARAM_SOURCE_LOCATION, dir, 
				TextReader.PARAM_LANGUAGE, "en",
				TextReader.PARAM_PATTERNS, "*.txt");
		
		SimplePipeline.runPipeline(reader,
				getTokenizer(), 
				getPosTagger(), 
				getSyntacticParser(),
				DiscourseVsNonDiscourseClassifier.getClassifierDescription(dcList, discourseVsNonDiscoursePackage),
				DiscourseSenseLabeler.getClassifierDescription(senseLabelerPackage), 
				getWriter(output)
						);
	}
	
	public void parseSubdirectory(File dir, File output) throws ResourceInitializationException, UIMAException, IOException{
		for (File d: dir.listFiles()){
			if (d.isDirectory())
				parse(d, new File(output, d.getName()));
		}
	}
	
	public void train() throws Exception{
		File[] packageDirs = new File[]{discourseVsNonDiscoursePackage, senseLabelerPackage};
		for (File packageDir: packageDirs){
			JarClassifierBuilder.trainAndPackage(packageDir, "weka.classifiers.trees.J48", "-C 0.25 -M 2");
		}
	}
	
	public interface Options{
		@Option(
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
				shortName = "o",
				longName = "outputDir",
				description = "Specify the output directory to stores extracted texts")
		public String getOutputDir();
	}
	public static void main(String[] args) throws ResourceInitializationException, UIMAException, IOException {
		Options options = CliFactory.parseArguments(Options.class, args);
		DiscourseConnectiveDisambiguator disambiguator = new DiscourseConnectiveDisambiguator(new File(options.getModel()));
		disambiguator.parseSubdirectory(new File(options.getInputDataset()), new File(options.getOutputDir()));
	}
}

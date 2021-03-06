package ca.concordia.clac.discourse.parser.dc.disambiguation;

import static ca.concordia.clac.ml.feature.FeatureExtractors.dummyFunc;
import static ca.concordia.clac.ml.feature.FeatureExtractors.flatMap;
import static ca.concordia.clac.ml.feature.FeatureExtractors.getText;
import static ca.concordia.clac.ml.feature.FeatureExtractors.makeFeature;
import static ca.concordia.clac.ml.feature.FeatureExtractors.multiMap;
import static ca.concordia.clac.ml.feature.TreeFeatureExtractor.getConstituentType;
import static ca.concordia.clac.ml.feature.TreeFeatureExtractor.getLeftSibling;
import static ca.concordia.clac.ml.feature.TreeFeatureExtractor.getParent;
import static ca.concordia.clac.ml.feature.TreeFeatureExtractor.getPathFromRoot;
import static ca.concordia.clac.ml.feature.TreeFeatureExtractor.getRightSibling;
import static ca.concordia.clac.ml.scop.ScopeFeatureExtractor.getLast;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.corpus.conll2015.ConllDatasetPath;
import org.cleartk.corpus.conll2015.ConllDatasetPath.DatasetMode;
import org.cleartk.corpus.conll2015.ConllDatasetPathFactory;
import org.cleartk.corpus.conll2015.ConllDiscourseGoldAnnotator;
import org.cleartk.corpus.conll2015.ConllSyntaxGoldAnnotator;
import org.cleartk.discourse.type.DiscourseConnective;
import org.cleartk.ml.Feature;
import org.cleartk.ml.weka.WekaStringOutcomeDataWriter;

import ca.concordia.clac.ml.classifier.ClassifierAlgorithmFactory;
import ca.concordia.clac.ml.classifier.InstanceExtractor;
import ca.concordia.clac.ml.classifier.StringClassifierLabeller;
import ca.concordia.clac.uima.engines.LookupInstanceExtractor;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.constituent.Constituent;
import de.tudarmstadt.ukp.dkpro.core.io.text.TextReader;

class CombineFeature<T> implements Function<T, List<Feature>>{
	private String[][] patterns;
	private Function<T, List<Feature>> featurExtractors;
	public CombineFeature(Function<T, List<Feature>> featurExtractors, String[][] patterns) {
		this.patterns = patterns;
		this.featurExtractors = featurExtractors;
	}

	@Override
	public List<Feature> apply(T t) {
		List<Feature> features = new ArrayList<>(featurExtractors.apply(t));
		Map<String, Feature> fNameToFeature = new HashMap<>();
		
		for (Feature f: features){
			fNameToFeature.put(f.getName(), f);
		}
		
		for (String[] pattern: patterns){
			String combositFeatureName = "";
			String combositFeatureValue = "";
			for (String fName: pattern){
				if (combositFeatureName.length() != 0){
					combositFeatureName += "-";
					combositFeatureValue += "-";
				}
				
				combositFeatureName += fName;
				Feature feature = fNameToFeature.get(fName);
				if (feature == null){
					combositFeatureValue += "null"; 
				} else
					combositFeatureValue += feature.getValue().toString();
			}
			features.add(new Feature(combositFeatureName, combositFeatureValue));
		}
		return features;
	}
	
}

public class DiscourseVsNonDiscourseClassifier implements ClassifierAlgorithmFactory<String, DiscourseConnective>{
	public static final String SELF_CAT = "selfCat";
	public static final String RIGHT_TEXT = "rightText";
	public static final String RIGHT_POS = "rightPOS";
	public static final String LEFT_TEXT = "leftText";
	public static final String LEFT_POS = "leftPOS";
	public static final String PACKAGE_DIR = "discourse-vs-nondiscourse/";
	public static final String DC_HEAD_LIST_FILE = "dcHeadList.txt";
	public static final String CONN_LStr = "CON-LStr";
	
	private LookupInstanceExtractor<DiscourseConnective> lookupInstanceExtractor = new LookupInstanceExtractor<>();

	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {
		ClassifierAlgorithmFactory.super.initialize(context);
		lookupInstanceExtractor.initialize(context);
	}

	@Override
	public InstanceExtractor<DiscourseConnective> getExtractor(JCas aJCas) {
		return lookupInstanceExtractor;
	}
	
	public static <T extends Annotation, R extends Annotation> Function<T, R> getLeft(Class<R> r){
		return (ann) -> {
			List<R> selectPreceding = JCasUtil.selectPreceding(r, ann, 1);
			if (selectPreceding.size() > 0)
				return selectPreceding.get(0);
			return null;
		};
	}

	public static <T extends Annotation, R extends Annotation> Function<T, R> getRight(Class<R> r){
		return (ann) -> {
			List<R> selectPreceding = JCasUtil.selectFollowing(r, ann, 1);
			if (selectPreceding.size() > 0)
				return selectPreceding.get(0);
			return null;
		};
	}

	public static Function<DiscourseConnective, List<Feature>> getDiscourseConnectiveFeatures(){
		Function<String, String> toLowerCase = (str) -> str != null ? str.toLowerCase() : null;
		Function<String, Boolean> isAllLowerCase = StringUtils::isAllLowerCase;
		
		Function<DiscourseConnective, List<Feature>> textFeatures = dummyFunc(DiscourseConnective.class).andThen(getText()).andThen(multiMap(
				toLowerCase.andThen(makeFeature(CONN_LStr)),
				isAllLowerCase.andThen((b) -> b.toString()).andThen(makeFeature("CON-POS"))
				));
		
		Function<DiscourseConnective, List<Feature>> contextFeature = dummyFunc(DiscourseConnective.class).andThen(
				multiMap(getLeft(Token.class).andThen(
							multiMap(
									getConstituentType().andThen(makeFeature(LEFT_POS)),
									getText().andThen(toLowerCase).andThen(makeFeature(LEFT_TEXT))
									)
							),
						getRight(Token.class).andThen(
							multiMap(
									getConstituentType().andThen(makeFeature(RIGHT_POS)),
									getText().andThen(toLowerCase).andThen(makeFeature(RIGHT_TEXT))
									)
							)
						)
				).andThen(flatMap(Feature.class));
		
		Function<DiscourseConnective, List<Feature>> allFeatuers = multiMap(textFeatures, contextFeature).andThen(flatMap(Feature.class));
		
		
		return allFeatuers;
			
	}

	@Override
	public List<Function<DiscourseConnective, List<Feature>>> getFeatureExtractor(JCas aJCas) {
		Function<DiscourseConnective, List<Feature>> pathFeatures = 
				getPathFromRoot(DiscourseConnective.class).andThen(
				getLast(Constituent.class).andThen(
				multiMap(
						getConstituentType().andThen(makeFeature(SELF_CAT)),
						getParent().andThen(getConstituentType()).andThen(makeFeature("selfCatParent")),
						getLeftSibling().andThen(getConstituentType()).andThen(makeFeature("selfCatLeftSibling")),
						getRightSibling().andThen(getConstituentType()).andThen(makeFeature("selfCatRightSibling"))
						)));
				
		Function<DiscourseConnective, List<Feature>> singleFeatures = multiMap(pathFeatures, getDiscourseConnectiveFeatures()).andThen(flatMap(Feature.class));
		
//		singleFeatures = new CombineFeature<>(singleFeatures, new String[][]{
//			{CONN_LStr, LEFT_TEXT}, 
//			{CONN_LStr, RIGHT_TEXT}, 
//			{SELF_CAT, LEFT_POS}, 
//			{SELF_CAT, RIGHT_POS}, 
//			
//		});
		
		return Arrays.asList(singleFeatures);
	}

	@Override
	public Function<DiscourseConnective, String> getLabelExtractor(JCas aJCas) {
		return (dc) -> {
			List<DiscourseConnective> selectCovered = JCasUtil.selectCovering(DiscourseConnective.class, dc);
			for (DiscourseConnective indexedDc: selectCovered){
				if (indexedDc.getBegin() == dc.getBegin() &&
						indexedDc.getEnd() == dc.getEnd()){
					return Boolean.toString(true);
				}
			}
			return Boolean.toString(false);
		};
	}

	@Override
	public BiConsumer<String, DiscourseConnective> getLabeller(JCas aJCas) {
		return (label, dc) ->{
			boolean toAdd = Boolean.parseBoolean(label);
			if (toAdd)
				dc.addToIndexes();
		};
	}


	public static AnalysisEngineDescription getWriterDescription(File dcList, File outputFld) throws ResourceInitializationException, MalformedURLException{
		return StringClassifierLabeller.getWriterDescription(
				DiscourseVsNonDiscourseClassifier.class,
//				MaxentStringOutcomeDataWriter.class,
				WekaStringOutcomeDataWriter.class, 
				outputFld, 
				LookupInstanceExtractor.PARAM_LOOKUP_FILE_URL, dcList.toURI().toURL().toString(),
				LookupInstanceExtractor.PARAM_ANNOTATION_FACTORY_CLASS_NAME, DiscourseAnnotationFactory.class.getName()
				);
	}

	public static AnalysisEngineDescription getClassifierDescription(URL dcList, URL packageDir) throws ResourceInitializationException, MalformedURLException, URISyntaxException{
		return StringClassifierLabeller.getClassifierDescription(
					DiscourseVsNonDiscourseClassifier.class, 
					new URL(packageDir, "model.jar"),
					LookupInstanceExtractor.PARAM_LOOKUP_FILE_URL, dcList.toURI().toURL().toString(),
					LookupInstanceExtractor.PARAM_ANNOTATION_FACTORY_CLASS_NAME, DiscourseAnnotationFactory.class.getName()
					);
	}


	public static void main(String[] args) throws Exception {
		ConllDatasetPath dataset = new ConllDatasetPathFactory().makeADataset2016(new File("../discourse.conll.dataset/data"), DatasetMode.train);

		CollectionReaderDescription reader = CollectionReaderFactory.createReaderDescription(TextReader.class, 
				TextReader.PARAM_SOURCE_LOCATION, dataset.getRawDirectory(), 
				TextReader.PARAM_LANGUAGE, "en",
				TextReader.PARAM_PATTERNS, "wsj_*");
		AnalysisEngineDescription conllSyntaxJsonReader = 
				ConllSyntaxGoldAnnotator.getDescription(dataset.getParsesJSonFile());

		AnalysisEngineDescription conllGoldJsonReader = 
				ConllDiscourseGoldAnnotator.getDescription(dataset.getRelationsJSonFile());

		File dcList = new File(new File("outputs/resources"), DC_HEAD_LIST_FILE);
		File featureFile = new File(new File("outputs/resources"), PACKAGE_DIR);
		if (featureFile.exists())
			FileUtils.deleteDirectory(featureFile);
		SimplePipeline.runPipeline(reader,
				conllSyntaxJsonReader, 
				conllGoldJsonReader, 
				getWriterDescription(dcList, featureFile)
				);


	}
}
